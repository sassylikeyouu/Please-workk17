package com.example.server

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object JavaRuntimeManager {
    fun getJreDir(context: Context, version: Int = 21): File {
        return File(context.filesDir, "jre$version")
    }

    fun getJavaBin(context: Context, version: Int = 21): File {
        return File(getJreDir(context, version), "bin/java")
    }

    suspend fun isJavaReady(context: Context, version: Int = 21): Boolean {
        val jreDir = getJreDir(context, version)
        val javaBin = getJavaBin(context, version)
        val libjli = File(jreDir, "lib/libjli.so")
        val libjliAlt = File(jreDir, "lib/jli/libjli.so")
        val libjvm = File(jreDir, "lib/server/libjvm.so")
        
        return javaBin.exists() && javaBin.canExecute() && 
               (libjli.exists() || libjliAlt.exists()) && 
               libjvm.exists()
    }

    suspend fun ensureJavaReady(context: Context, version: Int = 21, onProgress: (String) -> Unit): Boolean {
        if (isJavaReady(context, version)) {
            return true
        }

        onProgress("Java runtime (v$version) not found or incomplete. Starting download...")
        val success = Downloader.downloadAndExtractJre(getJreDir(context, version), version, onProgress)
        if (success) {
            onProgress("Java runtime setup successful.")
        } else {
            onProgress("ERROR: Java runtime setup failed.")
        }
        return success
    }

    fun getLdLibraryPath(context: Context, version: Int = 21): String {
        val jreDir = getJreDir(context, version)
        return "${jreDir.absolutePath}/lib:${jreDir.absolutePath}/lib/server:${jreDir.absolutePath}/lib/jli:/system/lib64"
    }
}
