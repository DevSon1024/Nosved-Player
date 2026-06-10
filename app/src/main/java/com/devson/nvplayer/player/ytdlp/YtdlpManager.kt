package com.devson.nvplayer.player.ytdlp

import android.content.Context
import android.system.Os
import android.util.Log
import com.devson.nvplayer.data.repository.PlaybackSettings
import `is`.xyz.mpv.MPVLib
import java.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YtdlpManager {
    private const val TAG = "YtdlpManager"
    private const val YTDL_DIR = "ytdl"

    fun getYtdlDir(context: Context): File {
        return File(context.filesDir, YTDL_DIR).apply { if (!exists()) mkdirs() }
    }

    fun getExecutablePath(context: Context): String {
        return File(context.applicationInfo.nativeLibraryDir, "libytdl.so").absolutePath
    }

    suspend fun copyAssets(context: Context) = withContext(Dispatchers.IO) {
        val ytdlDir = getYtdlDir(context)

        // Clean up old potentially problematic scripts from multiple possible locations
        listOf("youtube-dl", "youtube-dl.sh").forEach { name ->
            File(context.filesDir, name).delete()
            File(ytdlDir, name).delete()
        }

        // Files to copy from assets/ytdl/ to filesDir/ytdl/
        val ytdlFiles = arrayOf("setup.py", "wrapper", "python313.zip")
        for (name in ytdlFiles) {
            copyAssetFile(context, "ytdl/$name", File(ytdlDir, name))
        }

        // cacert.pem goes to filesDir/
        copyAssetFile(context, "cacert.pem", File(context.filesDir, "cacert.pem"))

        // Set executable permission on wrapper (just in case it's used)
        File(ytdlDir, "wrapper").setExecutable(true)
    }

    private fun copyAssetFile(context: Context, assetPath: String, outFile: File): Boolean {
        return try {
            context.assets.open(assetPath).use { input ->
                val size = input.available().toLong()
                if (outFile.exists() && outFile.length() == size) {
                    Log.v(TAG, "Skipping copy: $assetPath (exists same size)")
                    return true
                }
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
                Log.d(TAG, "Copied asset: $assetPath")
                true
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset: $assetPath", e)
            false
        }
    }

    fun setupMpvOptions(context: Context, settings: PlaybackSettings) {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val ytdlBinaryPath = File(nativeLibDir, "libytdl.so").absolutePath
        val ytdlDir = getYtdlDir(context).absolutePath
        val ytDlpScriptPath = File(ytdlDir, "yt-dlp").absolutePath
        val pythonPath = File(nativeLibDir, "libpython.so").absolutePath

        // Set environment variables for the subprocesses started by libmpv
        try {
            Os.setenv("YTDL_PYTHON", pythonPath, true)
            Os.setenv("YTDL_SCRIPT", ytDlpScriptPath, true)
            Os.setenv("PYTHONHOME", ytdlDir, true)
            // Include both the zip and the directory itself in PYTHONPATH
            // Also include nativeLibDir for potential .so modules
            Os.setenv("PYTHONPATH", "$ytdlDir/python313.zip:$ytdlDir:$nativeLibDir", true)
            Os.setenv("SSL_CERT_FILE", File(context.filesDir, "cacert.pem").absolutePath, true)
            
            // Add nativeLibDir to PATH so scripts can find our bridge if they search PATH
            val currentPath = runCatching { Os.getenv("PATH") }.getOrNull()
            val newPath = if (currentPath.isNullOrBlank()) nativeLibDir else "$nativeLibDir:$currentPath"
            Os.setenv("PATH", newPath, true)

            // Set LD_LIBRARY_PATH for the subprocess to find libpython.so's dependencies
            val currentLd = runCatching { Os.getenv("LD_LIBRARY_PATH") }.getOrNull()
            val newLd = if (currentLd.isNullOrBlank()) nativeLibDir else "$nativeLibDir:$currentLd"
            Os.setenv("LD_LIBRARY_PATH", newLd, true)
            
            Log.d(TAG, "Environment variables set for ytdl bridge")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set environment variables", e)
        }

        // Check if yt-dlp actually exists. If not, log a warning.
        val ytDlpFile = File(ytdlDir, "yt-dlp")
        if (!ytDlpFile.exists()) {
            Log.w(TAG, "yt-dlp not found in ${ytDlpFile.absolutePath}. Subprocess will fail until installed.")
        }

        val resolvedOptions = YtdlpOptionsBuilder.build(
            YtdlpOptionSettings.fromSettings(settings),
        )
        val ua = settings.customUserAgent.ifBlank { YtdlpOptionsBuilder.DEFAULT_USER_AGENT }

        // Create script-opts/ytdl_hook.conf to ensure the script picks up our bridge
        // This is the most reliable way to override ytdl_hook options
        try {
            val scriptOptsDir = File(context.filesDir, "script-opts")
            if (!scriptOptsDir.exists()) scriptOptsDir.mkdirs()
            val ytdlConf = File(scriptOptsDir, "ytdl_hook.conf")
            val confContent = """
                path=$ytdlBinaryPath
                ytdl_path=$ytdlBinaryPath
                all_formats=yes
                all_subtitles=yes
            """.trimIndent()
            ytdlConf.writeText(confContent)
            Log.d(TAG, "Created ytdl_hook.conf at ${ytdlConf.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ytdl_hook.conf", e)
        }

        // Apply options to MPV core
        MPVLib.setOptionString("ytdl", "yes")
        MPVLib.setOptionString("ytdl-path", ytdlBinaryPath)
        
        val customYtdlFormat = settings.ytdlFormat
        val ytdlFormat = customYtdlFormat.ifBlank { resolvedOptions.format }
        if (ytdlFormat.isNotBlank()) {
            MPVLib.setOptionString("ytdl-format", ytdlFormat)
        }

        // Global User-Agent to avoid blocks at the network level
        MPVLib.setOptionString("user-agent", ua)

        Log.d(TAG, "Setting ytdl-format to: $ytdlFormat")
        Log.d(TAG, "Setting ytdl-raw-options to: ${resolvedOptions.rawOptions}")
        MPVLib.setOptionString("ytdl-raw-options", resolvedOptions.rawOptions)

        // Consolidate all script options into a single "script-opts" string
        val optsList = mutableListOf(
            "ytdl_hook-path=$ytdlBinaryPath",
            "ytdl_hook-ytdl_path=$ytdlBinaryPath",
            "ytdl_hook-all_formats=yes",
            "ytdl_hook-all_subtitles=yes",
            "ytdl_hook-user_agent=\"$ua\""
        )
        val scriptOptsValue = optsList.joinToString(",")
        Log.d(TAG, "Setting script-opts to: $scriptOptsValue")
        MPVLib.setOptionString("script-opts", scriptOptsValue)

        // Keep redundant script-opts-append calls for safety/compatibility with older/newer builds
        MPVLib.setOptionString("script-opts-append", "ytdl_hook-path=$ytdlBinaryPath")
        MPVLib.setOptionString("script-opts-append", "ytdl_hook-ytdl_path=$ytdlBinaryPath")
        MPVLib.setOptionString("script-opts-append", "ytdl_hook-all_formats=yes")
        MPVLib.setOptionString("script-opts-append", "ytdl_hook-all_subtitles=yes")
        MPVLib.setOptionString("script-opts-append", "ytdl_hook-user_agent=\"$ua\"")

        Log.d(TAG, "MPV ytdl options set. Binary: $ytdlBinaryPath")
    }

    suspend fun runInstall(context: Context, onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        copyAssets(context)
        
        val ytdlDir = getYtdlDir(context)
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val pythonBinary = getExecutablePath(context)
        val setupPy = File(ytdlDir, "setup.py").absolutePath

        // We use the bridge to run setup.py
        val command = mutableListOf(pythonBinary, setupPy, nativeLibDir)
        
        runPythonProcess("Installing yt-dlp...", command, context, onLog)
    }

    suspend fun runUpdate(context: Context, onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val ytdlDir = getYtdlDir(context)
        val pythonBinary = getExecutablePath(context)
        val ytDlp = File(ytdlDir, "yt-dlp").absolutePath

        val command = mutableListOf(pythonBinary, ytDlp, "--update")
        
        runPythonProcess("Updating yt-dlp...", command, context, onLog)
    }

    private fun runPythonProcess(title: String, command: List<String>, context: Context, onLog: (String) -> Unit): Boolean {
        onLog("$title\n")
        return try {
            val processBuilder = ProcessBuilder(command)
                .directory(getYtdlDir(context))
                .redirectErrorStream(true)
            
            val env = processBuilder.environment()
            val ytdlDir = getYtdlDir(context).absolutePath
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            
            // Clear YTDL_SCRIPT so the bridge doesn't try to wrap yt-dlp during setup/update
            env.remove("YTDL_SCRIPT")
            
            env["YTDL_PYTHON"] = File(nativeLibDir, "libpython.so").absolutePath
            env["PYTHONHOME"] = ytdlDir
            env["PYTHONPATH"] = "$ytdlDir/python313.zip"
            env["SSL_CERT_FILE"] = File(context.filesDir, "cacert.pem").absolutePath
            env["LD_LIBRARY_PATH"] = nativeLibDir
            
            val process = processBuilder.start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                onLog(line + "\n")
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            onLog("Error: ${e.message}\n")
            false
        }
    }

    suspend fun getInstalledVersion(context: Context): String? = withContext(Dispatchers.IO) {
        val ytdlDir = getYtdlDir(context)
        val ytDlp = File(ytdlDir, "yt-dlp")
        if (!ytDlp.exists()) return@withContext null
        
        val pythonBinary = getExecutablePath(context)
        val command = listOf(pythonBinary, ytDlp.absolutePath, "--version")
        try {
            val processBuilder = ProcessBuilder(command)
                .directory(ytdlDir)
                .redirectErrorStream(true)
            val env = processBuilder.environment()
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            env.remove("YTDL_SCRIPT")
            env["YTDL_PYTHON"] = File(nativeLibDir, "libpython.so").absolutePath
            env["PYTHONHOME"] = ytdlDir.absolutePath
            env["PYTHONPATH"] = "${ytdlDir.absolutePath}/python313.zip"
            env["SSL_CERT_FILE"] = File(context.filesDir, "cacert.pem").absolutePath
            env["LD_LIBRARY_PATH"] = nativeLibDir
            
            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()?.trim()
            process.waitFor()
            if (!output.isNullOrBlank() && output.matches(Regex("""\d{4}\.\d{2}\.\d{2}(\.\d+)*"""))) {
                output
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get installed yt-dlp version", e)
            null
        }
    }

    suspend fun getLatestReleaseTag(): String? = withContext(Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        try {
            val url = java.net.URL("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest")
            connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val regex = Regex(""""tag_name"\s*:\s*"([^"]+)"""")
                val match = regex.find(response)
                val rawTag = match?.groupValues?.get(1)
                // Remove prefix 'v' if any
                rawTag?.removePrefix("v")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch latest release tag", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
