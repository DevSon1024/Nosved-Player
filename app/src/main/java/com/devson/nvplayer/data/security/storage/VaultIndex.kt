package com.devson.nvplayer.data.security.storage

import com.devson.nvplayer.domain.model.VaultStorageMode

/**
 * Metadata entry stored in the persistent .vault_index file on user storage.
 * Ensures the app can fully reconstruct the Room database after uninstall/reinstall,
 * even when raw unencrypted files do not embed a custom header.
 */
data class VaultIndexEntry(
    val id: String,
    val vltFilename: String,
    val title: String,
    val originalExtension: String = "mp4",
    val originalUri: String = "",
    val fileSize: Long = 0L,
    val durationMs: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val storageMode: VaultStorageMode = VaultStorageMode.NONE,
    val formatVersion: Int = 2
)

data class VaultIndex(
    val version: Int = 1,
    val items: List<VaultIndexEntry> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Pure Kotlin serializer and parser for VaultIndex.
 * Avoids Android framework stub dependencies in JVM test environments.
 */
object VaultIndexJson {

    fun toJson(index: VaultIndex): String {
        fun escape(s: String): String =
            s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

        return buildString {
            append("{\n")
            append("  \"version\": ${index.version},\n")
            append("  \"lastUpdated\": ${index.lastUpdated},\n")
            append("  \"items\": [\n")
            index.items.forEachIndexed { idx, item ->
                append("    {\n")
                append("      \"id\": \"${escape(item.id)}\",\n")
                append("      \"vltFilename\": \"${escape(item.vltFilename)}\",\n")
                append("      \"title\": \"${escape(item.title)}\",\n")
                append("      \"originalExtension\": \"${escape(item.originalExtension)}\",\n")
                append("      \"originalUri\": \"${escape(item.originalUri)}\",\n")
                append("      \"fileSize\": ${item.fileSize},\n")
                append("      \"durationMs\": ${item.durationMs},\n")
                append("      \"dateAdded\": ${item.dateAdded},\n")
                append("      \"storageMode\": \"${item.storageMode.name}\",\n")
                append("      \"formatVersion\": ${item.formatVersion}\n")
                append("    }")
                if (idx < index.items.size - 1) append(",")
                append("\n")
            }
            append("  ]\n")
            append("}")
        }
    }

    fun fromJson(jsonText: String): VaultIndex? {
        return try {
            fun extractLong(text: String, key: String): Long? {
                val pattern = """"$key"\s*:\s*(-?\d+)""".toRegex()
                return pattern.find(text)?.groupValues?.get(1)?.toLongOrNull()
            }

            fun extractString(text: String, key: String): String? {
                val pattern = """"$key"\s*:\s*"((?:\\.|[^"\\])*)"""".toRegex()
                val match = pattern.find(text) ?: return null
                val raw = match.groupValues[1]
                return raw.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r")
            }

            val version = extractLong(jsonText, "version")?.toInt() ?: 1
            val lastUpdated = extractLong(jsonText, "lastUpdated") ?: System.currentTimeMillis()

            val items = mutableListOf<VaultIndexEntry>()
            val itemsBlockRegex = """"items"\s*:\s*\[(.*)\]""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val itemsMatch = itemsBlockRegex.find(jsonText)

            if (itemsMatch != null) {
                val itemsContent = itemsMatch.groupValues[1]
                val objectRegex = """\{([^}]+)\}""".toRegex()
                objectRegex.findAll(itemsContent).forEach { objMatch ->
                    val objText = objMatch.groupValues[1]
                    val id = extractString(objText, "id") ?: return@forEach
                    val vltFilename = extractString(objText, "vltFilename") ?: "$id.vlt"
                    val title = extractString(objText, "title") ?: id
                    val ext = extractString(objText, "originalExtension") ?: "mp4"
                    val uri = extractString(objText, "originalUri") ?: ""
                    val size = extractLong(objText, "fileSize") ?: 0L
                    val duration = extractLong(objText, "durationMs") ?: 0L
                    val dateAdded = extractLong(objText, "dateAdded") ?: System.currentTimeMillis()
                    val modeStr = extractString(objText, "storageMode") ?: "NONE"
                    val storageMode = try {
                        VaultStorageMode.valueOf(modeStr)
                    } catch (_: Exception) {
                        VaultStorageMode.NONE
                    }
                    val formatVersion = extractLong(objText, "formatVersion")?.toInt() ?: 2

                    items.add(
                        VaultIndexEntry(
                            id = id,
                            vltFilename = vltFilename,
                            title = title,
                            originalExtension = ext,
                            originalUri = uri,
                            fileSize = size,
                            durationMs = duration,
                            dateAdded = dateAdded,
                            storageMode = storageMode,
                            formatVersion = formatVersion
                        )
                    )
                }
            }

            VaultIndex(
                version = version,
                items = items,
                lastUpdated = lastUpdated
            )
        } catch (_: Exception) {
            null
        }
    }
}
