package com.example.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

object Downloader {
    private val client = OkHttpClient()

    private const val NUKKIT_URL = "https://github.com/PowerNukkitX/PowerNukkitX/releases/download/2.0.0/powernukkitx.jar"
    
    private val FALLBACK_URLS_21 = mapOf(
        "openjdk" to "https://packages.termux.dev/apt/termux-main/pool/main/o/openjdk-21/openjdk-21_21.0.11_aarch64.deb",
        "libandroid-shmem" to "https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb",
        "libandroid-spawn" to "https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-spawn/libandroid-spawn_0.3_aarch64.deb",
        "zlib" to "https://packages.termux.dev/apt/termux-main/pool/main/z/zlib/zlib_1.3.2_aarch64.deb",
        "libc++" to "https://packages.termux.dev/apt/termux-main/pool/main/libc/libc++/libc++_29_aarch64.deb"
    )

    private val FALLBACK_URLS_25 = mapOf(
        "openjdk" to "https://packages.termux.dev/apt/termux-main/pool/main/o/openjdk-25/openjdk-25_25.0.0_aarch64.deb",
        "libandroid-shmem" to "https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb",
        "libandroid-spawn" to "https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-spawn/libandroid-spawn_0.3_aarch64.deb",
        "zlib" to "https://packages.termux.dev/apt/termux-main/pool/main/z/zlib/zlib_1.3.2_aarch64.deb",
        "libc++" to "https://packages.termux.dev/apt/termux-main/pool/main/libc/libc++/libc++_29_aarch64.deb"
    )


    suspend fun downloadServerJar(url: String, destination: File, onProgress: (String) -> Unit): Boolean {
        var finalUrl = url
        val isNukkitMot = url == "GITHUB_API_NUKKIT_MOT"
        
        // Handle Nukkit-MOT dynamic resolution with fallback logic
        if (isNukkitMot) {
            onProgress("Connecting to Nukkit-MOT Jenkins CI...")
            val candidateUrls = resolveNukkitMotJarUrls(onProgress)
            if (candidateUrls.isEmpty()) {
                onProgress("ERROR: Could not resolve any Nukkit-MOT build artifacts.")
                return false
            }

            for (candidateUrl in candidateUrls) {
                onProgress("Attempting to download build: $candidateUrl")
                val success = downloadFile(candidateUrl, destination, onProgress, destination.name, isJar = true)
                
                if (success) {
                    if (validateNukkitMotJar(destination, onProgress)) {
                        onProgress("Nukkit-MOT setup successful.")
                        return true
                    } else {
                        onProgress("Validation failed for $candidateUrl. Trying next candidate...")
                    }
                }
            }
            onProgress("ERROR: All Nukkit-MOT build candidates failed validation.")
            return false
        }

        return downloadFile(finalUrl, destination, onProgress, destination.name, isJar = true)
    }

    private fun validateNukkitMotJar(file: File, onProgress: (String) -> Unit): Boolean {
        if (!file.exists()) {
            onProgress("ERROR: Downloaded file missing.")
            return false
        }
        val size = file.length()
        if (size < 1024 * 1024) { // 1 MB
            onProgress("ERROR: Jar is too small (${size / 1024} KB). Expected > 1MB.")
            file.delete()
            return false
        }
        
        // JAR format check (ZIP magic number)
        val isValid = try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(4)
                fis.read(header) == 4 && 
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && 
                header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
            }
        } catch (e: Exception) {
            false
        }

        if (!isValid) {
            onProgress("ERROR: Invalid JAR format detected.")
            file.delete()
            return false
        }
        
        onProgress("Validation successful: ${size / (1024 * 1024)} MB")
        return true
    }

    private suspend fun resolveNukkitMotJarUrls(onProgress: (String) -> Unit): List<String> {
        return withContext(Dispatchers.IO) {
            val urls = mutableListOf<String>()
            try {
                // Official Jenkins URL: https://motci.cn/job/Nukkit-MOT/
                // We'll target the java25 job which appears to be the current main branch for v1.21+
                val jobName = "java25" 
                val jenkinsBase = "https://motci.cn/job/Nukkit-MOT/job/$jobName"
                val apiUrl = "$jenkinsBase/api/json?tree=builds[number,result,artifacts[relativePath]]"
                
                onProgress("Fetching build list from motci.cn...")
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("Accept", "application/json")
                    .build()
                
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    onProgress("Jenkins API error: HTTP ${response.code}")
                    // Fallback to a guessed lastSuccessfulBuild path if API fails
                    urls.add("$jenkinsBase/lastSuccessfulBuild/artifact/target/Nukkit-MOT-SNAPSHOT.jar")
                    return@withContext urls
                }
                
                val body = response.body?.string() ?: return@withContext urls
                val json = org.json.JSONObject(body)
                val builds = json.getJSONArray("builds")
                
                var count = 0
                for (i in 0 until builds.length()) {
                    if (count >= 3) break // Try at most 3 builds
                    
                    val build = builds.getJSONObject(i)
                    val result = build.optString("result", "UNKNOWN")
                    val number = build.getInt("number")
                    
                    // Jenkins "result" is null while building, and "SUCCESS" when finished successfully
                    if (result == "SUCCESS") {
                        val artifacts = build.getJSONArray("artifacts")
                        for (j in 0 until artifacts.length()) {
                            val artifact = artifacts.getJSONObject(j)
                            val path = artifact.getString("relativePath")
                            if (path.endsWith("Nukkit-MOT-SNAPSHOT.jar") || path.contains("Nukkit-MOT")) {
                                urls.add("$jenkinsBase/$number/artifact/$path")
                                count++
                                break
                            }
                        }
                    }
                }
                
                // If no successful builds found in JSON, add the generic shortcut as absolute last resort
                if (urls.isEmpty()) {
                    urls.add("$jenkinsBase/lastSuccessfulBuild/artifact/target/Nukkit-MOT-SNAPSHOT.jar")
                }
                
            } catch (e: Exception) {
                onProgress("Error resolving Jenkins builds: ${e.message}")
                // Last resort fallback
                urls.add("https://motci.cn/job/Nukkit-MOT/job/java25/lastSuccessfulBuild/artifact/target/Nukkit-MOT-SNAPSHOT.jar")
            }
            urls
        }
    }

    private suspend fun resolveNukkitMotJarUrl(onProgress: (String) -> Unit): String? {
        // This is now replaced by resolveNukkitMotJarUrls for better fallback support
        return null
    }

    private suspend fun resolveUrls(version: Int, onProgress: (String) -> Unit): Map<String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                onProgress("Resolving current Java v$version package URLs from Termux...")
                val url = "https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-aarch64/Packages.gz"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    onProgress("Failed to fetch Termux package index: HTTP ${response.code}. Using fallbacks.")
                    return@withContext null
                }
                val body = response.body ?: return@withContext null
                val resolved = mutableMapOf<String, String>()
                val targetOpenJDK = "openjdk-$version"
                val deps = listOf("libandroid-shmem", "libandroid-spawn", "zlib", "libc++")
                
                java.util.zip.GZIPInputStream(body.byteStream()).bufferedReader().use { reader ->
                    var currentPackage = ""
                    var currentFilename = ""
                    var line = reader.readLine()
                    while (line != null) {
                        if (line.startsWith("Package:")) {
                            currentPackage = line.substringAfter("Package:").trim()
                        } else if (line.startsWith("Filename:")) {
                            currentFilename = line.substringAfter("Filename:").trim()
                        } else if (line.trim().isEmpty()) {
                            if (currentPackage == targetOpenJDK) {
                                resolved["openjdk"] = "https://packages.termux.dev/apt/termux-main/$currentFilename"
                            } else if (currentPackage in deps) {
                                resolved[currentPackage] = "https://packages.termux.dev/apt/termux-main/$currentFilename"
                            }
                            currentPackage = ""
                            currentFilename = ""
                        }
                        line = reader.readLine()
                    }
                    if (currentPackage == targetOpenJDK) {
                        resolved["openjdk"] = "https://packages.termux.dev/apt/termux-main/$currentFilename"
                    } else if (currentPackage in deps) {
                        resolved[currentPackage] = "https://packages.termux.dev/apt/termux-main/$currentFilename"
                    }
                }
                resolved
            } catch (e: Exception) {
                onProgress("URL resolution error: ${e.message}. Using fallbacks.")
                null
            }
        }
    }

    private fun extractAr(debFile: File, outputFile: File) {
        debFile.inputStream().buffered().use { input ->
            val magic = ByteArray(8)
            val readMagic = input.read(magic)
            if (readMagic != 8 || !magic.contentEquals("!<arch>\n".toByteArray(Charsets.US_ASCII))) {
                throw IOException("Not a valid ar archive")
            }
            val header = ByteArray(60)
            while (input.read(header) == 60) {
                val name = String(header, 0, 16, Charsets.US_ASCII).trim()
                val sizeStr = String(header, 48, 10, Charsets.US_ASCII).trim()
                val size = sizeStr.toLong()
                val paddedSize = if (size % 2L != 0L) size + 1 else size
                
                if (name.startsWith("data.tar.xz")) {
                    outputFile.outputStream().use { output ->
                        var remaining = size
                        val buffer = ByteArray(32 * 1024)
                        while (remaining > 0) {
                            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                            val read = input.read(buffer, 0, toRead)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            remaining -= read
                        }
                    }
                    return
                } else {
                    var remaining = paddedSize
                    val buffer = ByteArray(32 * 1024)
                    while (remaining > 0) {
                        val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read == -1) break
                        remaining -= read
                    }
                }
            }
        }
        throw IOException("data.tar.xz not found in deb archive")
    }

    private fun extractTarXz(tarXzFile: File, destDir: File) {
        val destPath = destDir.toPath().toAbsolutePath()
        FileInputStream(tarXzFile).buffered().use { fileIn ->
            XZCompressorInputStream(fileIn).use { xzIn ->
                TarArchiveInputStream(xzIn).use { tarIn ->
                    var entry = tarIn.nextTarEntry
                    while (entry != null) {
                        val entryPath = destPath.resolve(entry.name).normalize()
                        if (!entryPath.startsWith(destPath)) {
                            entry = tarIn.nextTarEntry
                            continue
                        }
                        
                        if (entry.isDirectory) {
                            Files.createDirectories(entryPath)
                        } else if (entry.isSymbolicLink) {
                            Files.createDirectories(entryPath.parent)
                            val targetPath = Paths.get(entry.linkName)
                            try {
                                Files.deleteIfExists(entryPath)
                                Files.createSymbolicLink(entryPath, targetPath)
                            } catch (e: Exception) {
                                // If symlink fails, we might have to ignore or handle differently
                                // In Android, symlinks are supported on /data partitions (ext4/f2fs)
                            }
                        } else if (entry.isCheckSumOK) {
                            Files.createDirectories(entryPath.parent)
                            Files.newOutputStream(entryPath).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var read: Int
                                while (tarIn.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                }
                            }
                            
                            // Set executable permission if mode indicates it
                            val mode = entry.mode
                            if ((mode and 0x49) != 0) { // Any execute bit (owner, group, or other)
                                entryPath.toFile().setExecutable(true, false)
                            }
                        }
                        entry = tarIn.nextTarEntry
                    }
                }
            }
        }
    }

    private fun validateElf(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(4)
                fis.read(header) == 4 && 
                header[0] == 0x7F.toByte() && header[1] == 'E'.toByte() && 
                header[2] == 'L'.toByte() && header[3] == 'F'.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadAndExtractJre(jreDir: File, version: Int, onProgress: (String) -> Unit): Boolean {
        for (attempt in 1..2) {
            onProgress("Downloading Full ARM64 OpenJDK $version Server Runtime... (Attempt $attempt/2)")
            
            if (jreDir.exists()) {
                jreDir.deleteRecursively()
            }
            jreDir.mkdirs()
            
            val fallbackSet = if (version == 25) FALLBACK_URLS_25 else FALLBACK_URLS_21
            val resolvedUrls = resolveUrls(version, onProgress) ?: fallbackSet
            val finalUrls = fallbackSet.toMutableMap().apply {
                resolvedUrls.forEach { (k, v) -> put(k, v) }
            }
            
            var success = true
            val pkgFiles = mutableMapOf<String, File>()
            
            for ((pkgName, url) in finalUrls) {
                val debFile = File(jreDir.parentFile, "${pkgName}_v${version}.deb")
                onProgress("Downloading package $pkgName...")
                if (!downloadFile(url, debFile, onProgress, pkgName)) {
                    success = false
                    break
                }
                pkgFiles[pkgName] = debFile
            }
            
            if (!success) {
                pkgFiles.values.forEach { it.delete() }
                continue
            }
            
            val extracted = withContext(Dispatchers.IO) {
                try {
                    val tempExtracted = File(jreDir, "temp_extracted")
                    tempExtracted.mkdirs()
                    
                    for ((pkgName, debFile) in pkgFiles) {
                        onProgress("Extracting package $pkgName...")
                        val tarXzFile = File(jreDir.parentFile, "${pkgName}_data_v${version}.tar.xz")
                        extractAr(debFile, tarXzFile)
                        extractTarXz(tarXzFile, tempExtracted)
                        tarXzFile.delete()
                    }
                    
                    onProgress("Promoting files to final location...")
                    val jdkSrcDir = File(tempExtracted, "data/data/com.termux/files/usr/lib/jvm/java-$version-openjdk")
                    if (jdkSrcDir.exists()) {
                        val jdkSrcPath = jdkSrcDir.toPath()
                        val jrePath = jreDir.toPath()
                        Files.walk(jdkSrcPath).use { stream ->
                            stream.forEach { source ->
                                if (source == jdkSrcPath) return@forEach
                                val relative = jdkSrcPath.relativize(source)
                                val destination = jrePath.resolve(relative)
                                if (Files.isDirectory(source)) {
                                    Files.createDirectories(destination)
                                } else {
                                    if (Files.isRegularFile(source) && Files.size(source) == 0L) return@forEach
                                    Files.createDirectories(destination.parent)
                                    Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
                                }
                            }
                        }
                    }
                    
                    val libSrcDir = File(tempExtracted, "data/data/com.termux/files/usr/lib")
                    val destLibDir = File(jreDir, "lib")
                    destLibDir.mkdirs()
                    if (libSrcDir.exists()) {
                        val libSrcPath = libSrcDir.toPath()
                        val destLibPath = destLibDir.toPath()
                        Files.walk(libSrcPath).use { stream ->
                            stream.forEach { source ->
                                if (source == libSrcPath) return@forEach
                                if (Files.isRegularFile(source) || Files.isSymbolicLink(source)) {
                                    if (Files.isRegularFile(source) && Files.size(source) == 0L) return@forEach
                                    val name = source.fileName.toString()
                                    if (name.endsWith(".so") || name.contains(".so.")) {
                                        val destination = destLibPath.resolve(name)
                                        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
                                    }
                                }
                            }
                        }
                    }
                    
                    tempExtracted.deleteRecursively()
                    pkgFiles.values.forEach { it.delete() }
                    
                    onProgress("Finalizing permissions...")
                    val binDir = File(jreDir, "bin")
                    binDir.listFiles()?.forEach { it.setExecutable(true, false) }
                    
                    // Final validation of critical files
                    val criticalFiles = listOf(
                        File(jreDir, "bin/java"),
                        File(jreDir, "lib/server/libjvm.so"),
                        File(jreDir, "lib/libz.so"),
                        File(jreDir, "lib/libjli.so")
                    )
                    
                    for (cf in criticalFiles) {
                        if (!cf.exists()) {
                            onProgress("Critical file missing: ${cf.name}")
                            throw IOException("Validation failed: ${cf.name} missing")
                        }
                        if (cf.isFile && cf.length() == 0L) {
                            onProgress("Critical file is empty: ${cf.name}")
                            throw IOException("Validation failed: ${cf.name} is empty")
                        }
                        if (cf.name.endsWith(".so") && !validateElf(cf)) {
                            // Note: some .so might be symlinks, validateElf might fail on symlinks if not handled
                            // But for libjvm.so it should be a real file.
                            if (Files.isRegularFile(cf.toPath()) && !validateElf(cf)) {
                                onProgress("Invalid ELF header: ${cf.name}")
                                throw IOException("Validation failed: ${cf.name} invalid ELF")
                            }
                        }
                    }
                    
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    onProgress("Extraction failed: ${e.message}")
                    false
                }
            }
            
            val javaBin = File(jreDir, "bin/java")
            val javacBin = File(jreDir, "bin/javac")
            
            if (extracted && javaBin.exists() && javacBin.exists()) {
                javaBin.setExecutable(true, false)
                javacBin.setExecutable(true, false)
                
                onProgress("Performing sanity check: java -version")
                val pb = ProcessBuilder(javaBin.absolutePath, "-version")
                val ldLibPath = "${jreDir.absolutePath}/lib:${jreDir.absolutePath}/lib/server:${jreDir.absolutePath}/lib/jli:/system/lib64"
                pb.environment()["LD_LIBRARY_PATH"] = ldLibPath
                pb.redirectErrorStream(true)
                try {
                    val process = pb.start()
                    val output = process.inputStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        onProgress("JDK Setup Complete (java -version success)")
                        return true
                    } else {
                        onProgress("java -version failed with exit code $exitCode:\n$output")
                    }
                } catch (e: Exception) {
                    onProgress("Sanity check crashed: ${e.message}")
                }
                
                onProgress("Validation failed after extraction. Retrying...")
                jreDir.deleteRecursively()
            } else {
                onProgress("Validation failed: Required JDK files missing.")
                jreDir.deleteRecursively()
            }
        }
        return false
    }


    

    private suspend fun downloadFile(url: String, destination: File, onProgress: (String) -> Unit, name: String, isJar: Boolean = false): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                onProgress("Connecting to download $name...")
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    onProgress("Download failed:\nHTTP ${response.code}")
                    if (destination.exists()) destination.delete()
                    return@withContext false
                }
                val body = response.body ?: return@withContext false
                val totalLength = body.contentLength()
                val inputStream = body.byteStream()
                destination.parentFile?.mkdirs()
                val outputStream = FileOutputStream(destination)
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var downloaded: Long = 0
                var lastUpdate = System.currentTimeMillis()
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 500) {
                        lastUpdate = now
                        if (totalLength > 0) {
                            val progress = (downloaded * 100 / totalLength).toInt()
                            onProgress("Downloading $name ($progress%)")
                        } else {
                            onProgress(String.format("Downloading $name... %.2f MB", downloaded / (1024.0 * 1024.0)))
                        }
                    }
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                onProgress("Download $name complete.")
                true
            } catch (e: Exception) {
                onProgress("Download error: ${e.message}")
                if (destination.exists()) destination.delete()
                false
            }
        }
    }
}
