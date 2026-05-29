package com.devson.nvplayer

import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ExampleUnitTest {
    @Test
    fun printButtonGroupKtMeasure() {
        val jarPath = "C:\\Users\\DEVENDRA\\.gradle\\caches\\modules-2\\files-2.1\\androidx.compose.material3\\material3-android\\1.4.0-alpha02\\26834eab2827c2f1288a99e64cf5e4cc986d9d97\\material3-android-1.4.0-alpha02-sources.jar"
        val file = File(jarPath)
        if (!file.exists()) {
            println("Jar not found at $jarPath")
            return
        }
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith("ButtonGroup.kt")) {
                    println("=== Entry: ${entry.name} ===")
                    zip.getInputStream(entry).bufferedReader().use { reader ->
                        reader.lineSequence().drop(235).take(150).forEachIndexed { index, line ->
                            println("${index + 236}: $line")
                        }
                    }
                }
            }
        }
    }
}