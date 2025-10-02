// app/src/main/java/com/example/secureshredder/MainActivity.kt

package com.example.secureshredder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Html // Added import for Html
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.example.secureshredder.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var shredder: Shredder // Will be initialized in onCreate
    private var isWipeStorageIntent: Boolean = false // Flag to check intent of folder picker

    // Launcher for picking a single file
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { fileUri ->
            startShreddingFile(fileUri)
        } ?: Snackbar.make(binding.root, "No file selected.", Snackbar.LENGTH_LONG).show()
    }

    // Launcher for picking a directory (used by both "Select Folder" and "Wipe Storage")
    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { folderUri ->
            if (isWipeStorageIntent) {
                // CRITICAL: Show a strong confirmation dialog before wiping a selected storage area
                val dialogFolderName = DocumentFile.fromTreeUri(applicationContext, folderUri)?.name ?: folderUri.lastPathSegment ?: "Selected Location"
                AlertDialog.Builder(this)
                    .setTitle("Confirm Wipe Storage Area")
                    .setMessage("You are about to securely wipe ALL files and subfolders within: \n'$dialogFolderName'. \n\nThis action is IRREVERSIBLE. Are you absolutely sure?")
                    .setPositiveButton("Yes, Wipe It") { dialog, _ ->
                        startShreddingFolder(folderUri, isWipeOperation = true)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        Snackbar.make(binding.root, "Wipe operation cancelled.", Snackbar.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show()
            } else {
                startShreddingFolder(folderUri, isWipeOperation = false)
            }
        } ?: run {
            val message = if (isWipeStorageIntent) "No storage area selected for wiping." else "No folder selected."
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }
        isWipeStorageIntent = false // Reset the flag
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        shredder = Shredder(applicationContext) // Initialize Shredder with context

        binding.btnSelectFile.setOnClickListener {
            isWipeStorageIntent = false // Ensure flag is reset
            checkPermissionsAndLaunchPicker {
                filePickerLauncher.launch(arrayOf("*/*"))
            }
        }

        binding.btnSelectFolder.setOnClickListener {
            isWipeStorageIntent = false
            checkPermissionsAndLaunchPicker {
                folderPickerLauncher.launch(null)
            }
        }

        binding.btnWipeStorageArea.setOnClickListener {
            isWipeStorageIntent = true // Set flag for wipe intent
            checkPermissionsAndLaunchPicker {
                folderPickerLauncher.launch(null) // Use the same folder picker
            }
        }
    }

    private fun checkPermissionsAndLaunchPicker(launchPicker: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            if (Environment.isExternalStorageManager()) {
                launchPicker()
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                Snackbar.make(binding.root, "Please grant All Files Access permission and try again.", Snackbar.LENGTH_LONG).show()
            }
        } else {
            launchPicker()
        }
    }

    private fun startShreddingFile(fileUri: Uri) {
        lifecycleScope.launch { 
            setUiInProgress(true)
            val passes = 3 

            shredder.shredFile(fileUri, passes,
                onProgress = { progress ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.progressBar.progress = progress
                        binding.tvStatus.text = getString(R.string.shredding_progress, progress)
                    }
                },
                onStatusUpdate = { status ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.tvStatus.text = status
                    }
                }
            )
            
            val fileName = DocumentFile.fromSingleUri(applicationContext, fileUri)?.name ?: fileUri.lastPathSegment ?: "Unknown File"
            val certificateResult = CertificateManager.generateCertificateAndAttemptSavePdf(applicationContext, fileName, passes)
            val certificateDisplayString = certificateResult.first

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                binding.tvCertificate.text = Html.fromHtml(certificateDisplayString, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                binding.tvCertificate.text = Html.fromHtml(certificateDisplayString)
            }
            
            setUiInProgress(false)
            Snackbar.make(binding.root, "Shredding process complete for file: $fileName", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun startShreddingFolder(folderUri: Uri, isWipeOperation: Boolean) {
        lifecycleScope.launch { 
            setUiInProgress(true)
            val passes = 3 

            shredder.shredDirectory(folderUri, passes,
                onProgress = { progress ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.progressBar.progress = progress
                        binding.tvStatus.text = getString(R.string.shredding_progress, progress)
                    }
                },
                onStatusUpdate = { status ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.tvStatus.text = status
                    }
                }
            )

            val folderName = DocumentFile.fromTreeUri(applicationContext, folderUri)?.name ?: folderUri.lastPathSegment ?: "Unknown Folder"
            // Ensure itemNameForCertificate is ONLY the folderName for the certificate
            val itemNameForCertificate = folderName 

            val certificateResult = CertificateManager.generateCertificateAndAttemptSavePdf(applicationContext, itemNameForCertificate, passes)
            val certificateDisplayString = certificateResult.first

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                binding.tvCertificate.text = Html.fromHtml(certificateDisplayString, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                binding.tvCertificate.text = Html.fromHtml(certificateDisplayString)
            }
            
            val snackbarMessage = if (isWipeOperation) "Wipe operation complete for: $folderName" else "Shredding process complete for folder: $folderName"
            setUiInProgress(false)
            Snackbar.make(binding.root, snackbarMessage, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setUiInProgress(inProgress: Boolean) {
        binding.progressBar.visibility = if (inProgress) View.VISIBLE else View.INVISIBLE
        binding.tvStatus.visibility = if (inProgress) View.VISIBLE else View.INVISIBLE
        binding.btnSelectFile.isEnabled = !inProgress
        binding.btnSelectFolder.isEnabled = !inProgress
        binding.btnWipeStorageArea.isEnabled = !inProgress 
        if (!inProgress) {
            binding.progressBar.progress = 0
        } else {
            binding.tvCertificate.text = "" 
        }
    }
}
