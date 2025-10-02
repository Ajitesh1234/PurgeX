// app/src/main/java/com/example/secureshredder/Shredder.kt

package com.example.secureshredder

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

class Shredder(private val context: Context) {

    private suspend fun shredSingleDocument(
        docUri: Uri,
        docName: String?,
        docSize: Long,
        totalSizeOverall: Long,
        baseProcessedBytesOverall: Long,
        passes: Int,
        onProgress: (Int) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): Boolean {
        if (docSize == 0L) {
            onStatusUpdate("Skipping empty file: ${docName ?: docUri.lastPathSegment}")
            try {
                DocumentsContract.deleteDocument(context.contentResolver, docUri)
                return true
            } catch (e: Exception) {
                onStatusUpdate("Could not delete empty file: ${docName ?: docUri.lastPathSegment} - ${e.message}")
                return false
            }
        }

        onStatusUpdate("Shredding: ${docName ?: docUri.lastPathSegment}")
        for (i in 1..passes) {
            onStatusUpdate("Overwriting ${docName ?: docUri.lastPathSegment} (Pass $i/$passes)")
            if (!overwriteFileInternal(docUri, docSize, totalSizeOverall, baseProcessedBytesOverall, onProgress) { SecureRandom().generateSeed(it) }) {
                onStatusUpdate("Failed to overwrite ${docName ?: docUri.lastPathSegment}")
                return false
            }
        }

        onStatusUpdate("Encrypting ${docName ?: docUri.lastPathSegment} (Final Pass)")
        if (!encryptFileInternal(docUri, docSize)) { 
            onStatusUpdate("Failed to encrypt ${docName ?: docUri.lastPathSegment}")
            return false
        }

        onStatusUpdate("Deleting ${docName ?: docUri.lastPathSegment}")
        return try {
            if (DocumentsContract.deleteDocument(context.contentResolver, docUri)) {
                true
            } else {
                onStatusUpdate("Failed to delete ${docName ?: docUri.lastPathSegment}")
                false
            }
        } catch (e: Exception) {
            onStatusUpdate("Error deleting ${docName ?: docUri.lastPathSegment}: ${e.message}")
            false
        }
    }

    suspend fun shredFile(
        fileUri: Uri,
        passes: Int = 3,
        onProgress: (Int) -> Unit,
        onStatusUpdate: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val documentFile = DocumentFile.fromSingleUri(context, fileUri)
        if (documentFile == null || !documentFile.exists() || !documentFile.isFile) {
            onStatusUpdate("Error: File not found or is not a file.")
            onProgress(100)
            return@withContext
        }

        val fileName = documentFile.name ?: fileUri.lastPathSegment
        val fileSize = documentFile.length()

        if (fileSize == 0L) {
            onStatusUpdate("Shredding empty file: $fileName")
             try {
                if (documentFile.delete()) {
                    onStatusUpdate("Empty file $fileName deleted.")
                } else {
                    onStatusUpdate("Could not delete empty file $fileName.")
                }
            } catch (e: Exception) {
                onStatusUpdate("Error deleting empty file $fileName: ${e.message}")
            }
            onProgress(100)
            return@withContext
        }

        shredSingleDocument(fileUri, fileName, fileSize, fileSize, 0, passes, onProgress, onStatusUpdate)
        onProgress(100)
        onStatusUpdate("Shredding Complete for $fileName")
    }

    suspend fun shredDirectory(
        treeUri: Uri,
        passes: Int = 3,
        onProgress: (Int) -> Unit,
        onStatusUpdate: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)

        if (rootDoc == null || !rootDoc.exists() || !rootDoc.isDirectory) {
            onStatusUpdate("Error: Directory not found or is not a directory.")
            onProgress(100)
            return@withContext
        }

        val filesToShred = mutableListOf<DocumentFile>()
        val queue = ArrayDeque<DocumentFile>()
        queue.add(rootDoc)

        onStatusUpdate("Scanning directory...")
        // This part can trigger the permission denial when listing contents of certain directories
        try {
            while (queue.isNotEmpty()) {
                val currentDir = queue.removeFirst()
                currentDir.listFiles().forEach { doc ->
                    if (doc.isDirectory) {
                        queue.add(doc)
                    } else if (doc.isFile) {
                        filesToShred.add(doc)
                    }
                }
            }
        } catch (e: SecurityException) {
            onStatusUpdate("Permission denied trying to list files in ${rootDoc.name}. Shredding only based on initial URI.")
            // If we can't list, we can't shred contents. 
            // Depending on desired behavior, could try to delete rootDoc if it's empty or treat as error.
            // For now, will proceed as if no files were found if listing fails.
            filesToShred.clear() // Ensure it's empty if listing failed
        }

        if (filesToShred.isEmpty()) {
            onStatusUpdate("No files found to shred in ${rootDoc.name}, or access was denied.")
             // Try to delete the root directory if it's truly empty and accessible
            if (rootDoc.listFiles().isEmpty()) { // This listFiles might also fail
                try {
                    if (rootDoc.delete()) {
                        onStatusUpdate("Empty root directory ${rootDoc.name} deleted.")
                    }
                } catch (e: Exception) {
                    // Log.w("Shredder", "Could not delete empty root directory ${rootDoc.name}", e)
                }
            }
            onProgress(100)
            return@withContext
        }

        val totalSize = filesToShred.sumOf { it.length() }
        if (totalSize == 0L && filesToShred.isNotEmpty()) {
             onStatusUpdate("All files in ${rootDoc.name} are empty. Deleting them.")
            filesToShred.forEach { file ->
                file.delete()
            }
            deleteDirectoryRecursively(rootDoc)
            onProgress(100)
            return@withContext
        }

        var processedBytesOverall = 0L
        var allSuccessful = true

        for (file in filesToShred) {
            val success = shredSingleDocument(
                file.uri,
                file.name,
                file.length(),
                totalSize,
                processedBytesOverall,
                passes,
                onProgress,
                onStatusUpdate
            )
            if (success) {
                processedBytesOverall += file.length()
                if (totalSize > 0) {
                    onProgress((processedBytesOverall * 100 / totalSize).toInt())
                } else if (filesToShred.size == 1) {
                    onProgress(100)
                }
            } else {
                allSuccessful = false
            }
        }
        
        onStatusUpdate("Attempting to delete directory structure for ${rootDoc.name}...")
        deleteDirectoryRecursively(rootDoc)

        onProgress(100)
        if (allSuccessful) {
            onStatusUpdate("Shredding Complete for directory ${rootDoc.name}")
        } else {
            onStatusUpdate("Shredding for directory ${rootDoc.name} completed with some errors.")
        }
    }
    
    private fun deleteDirectoryRecursively(dir: DocumentFile) {
        // This recursive delete can also hit permission issues with listFiles()
        try {
            if (!dir.isDirectory) return
            dir.listFiles().forEach { file ->
                if (file.isDirectory) {
                    deleteDirectoryRecursively(file)
                }
                file.delete() 
            }
            dir.delete()
        } catch (e: SecurityException) {
            Log.w("Shredder", "Permission denied during recursive delete for ${dir.name}", e)
            // May not be able to delete everything if access is lost
        }
    }

    private fun overwriteFileInternal(
        uri: Uri,
        fileSize: Long,
        totalSizeOverall: Long,
        baseProcessedBytesOverall: Long,
        onProgress: (Int) -> Unit,
        dataGenerator: (Int) -> ByteArray
    ): Boolean {
        if (fileSize == 0L) return true

        val buffer = ByteArray(DEFAULT_BUFFER_SIZE) 
        try {
            context.contentResolver.openFileDescriptor(uri, "rwt")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { fos ->
                    var bytesWritten: Long = 0
                    while (bytesWritten < fileSize) {
                        val chunk = dataGenerator(buffer.size)
                        val toWrite = min(chunk.size.toLong(), fileSize - bytesWritten).toInt()
                        fos.write(chunk, 0, toWrite)
                        bytesWritten += toWrite

                        val currentTotalProcessed = baseProcessedBytesOverall + bytesWritten
                        if (totalSizeOverall > 0) {
                             onProgress((currentTotalProcessed * 100 / totalSizeOverall).toInt())
                        }
                    }
                    fos.fd.sync()
                }
            } ?: return false
        } catch (e: Exception) {
            Log.e("ShredderError", "Error in overwriteFileInternal for URI $uri", e)
            return false
        }
        return true
    }

    private fun encryptFileInternal(sourceUri: Uri, originalFileSize: Long): Boolean {
        if (originalFileSize == 0L) return true

        val key = SecureRandom().generateSeed(32) 
        val iv = SecureRandom().generateSeed(16)  
        val secretKeySpec = SecretKeySpec(key, "AES")
        val ivParameterSpec = IvParameterSpec(iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)

        val tempEncryptedFile = File(context.cacheDir, "temp_encrypted_${System.currentTimeMillis()}.dat")

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(tempEncryptedFile).use { tempFileOutStream ->
                    CipherOutputStream(tempFileOutStream, cipher).use { cipherOutStream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytesRead: Int = 0
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            cipherOutStream.write(buffer, 0, bytesRead)
                        }
                    }
                }
            } ?: run {
                Log.e("ShredderError", "Failed to open input stream for encryption: $sourceUri")
                tempEncryptedFile.delete()
                return false
            }

            context.contentResolver.openFileDescriptor(sourceUri, "wt")?.use { targetPfd ->
                FileInputStream(tempEncryptedFile).use { tempFileInStream ->
                    FileOutputStream(targetPfd.fileDescriptor).use { targetOutStream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytesRead: Int = 0
                        while (tempFileInStream.read(buffer).also { bytesRead = it } != -1) {
                            targetOutStream.write(buffer, 0, bytesRead)
                        }
                        targetOutStream.fd.sync()
                    }
                }
            } ?: run {
                Log.e("ShredderError", "Failed to open output stream for writing encrypted data to: $sourceUri")
                tempEncryptedFile.delete()
                return false
            }

        } catch (e: Exception) {
            Log.e("ShredderError", "Error during streaming encryption for URI $sourceUri", e)
            return false
        } finally {
            tempEncryptedFile.delete()
        }
        return true
    }
}

const val DEFAULT_BUFFER_SIZE = 8192
