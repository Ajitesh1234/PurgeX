# PurgeX - Secure File Shredder for Android

PurgeX is an Android application designed to securely and permanently delete files and folders, making them unrecoverable. It overwrites data multiple times before deletion to ensure complete data destruction.

## Features

- **Securely Shred Single Files**: Select any individual file to make it permanently unrecoverable.
- **Securely Shred Folders**: Select a folder to securely delete all of its contents, including subfolders.
- **Wipe Storage Area**: A high-stakes feature to securely wipe all contents of a selected storage location (e.g., a specific folder on an SD card or internal storage). This action is irreversible and requires explicit confirmation.
- **Multi-Pass Overwriting**: Files are overwritten multiple times (currently 3 passes) with random data to prevent recovery by advanced file-recovery software.
- **Certificate of Destruction**: After a successful shredding operation, the app generates a digital certificate detailing the operation, which can also be saved as a PDF.
- **Real-time Progress**: A progress bar and status updates keep you informed during the shredding process.
- **Permission Handling**: The app helps guide the user to grant the necessary "All Files Access" permission on Android 11 (R) and above.

## How It Works

Standard file deletion in most operating systems only removes the pointer to the file, leaving the actual data on the storage medium until it's overwritten by new data. PurgeX prevents this by:

1.  **Overwriting**: It first overwrites the entire content of the file with random data. This is done multiple times (in passes).
2.  **Deletion**: After the file's content has been thoroughly scrambled, the app deletes the file from the filesystem.

This process ensures that even if someone tries to use forensic tools to recover the data, they will only find meaningless, random bytes, not the original information.

## How to Use

1.  **Grant Permissions**: On first use, the app will prompt you to grant "All Files Access" if you are on Android 11 or newer. This permission is necessary for the app to access and delete files outside of its own specific directory.
2.  **Choose an Option**:
    - **Select File**: To shred a single file.
    - **Select Folder**: To shred all contents of a specific folder.
    - **Wipe Storage Area**: To shred the entire contents of a broader storage location.
3.  **Confirm**: For folder and wipe operations, a clear confirmation dialog will appear, warning you that the action is irreversible. You must explicitly confirm to proceed.
4.  **Monitor Progress**: Watch the progress bar and status messages as PurgeX securely deletes your data.
5.  **View Certificate**: Once the operation is complete, a certificate of destruction will be displayed on the screen.

## ⚠️ Disclaimer

**Data deletion by PurgeX is PERMANENT AND IRREVERSIBLE.** Please be absolutely certain that you want to delete the selected files or folders before confirming the operation. The developers of this application are not responsible for any accidental data loss.

## Technologies Used

- **Language**: Kotlin
- **Asynchronous Operations**: Kotlin Coroutines for non-blocking file I/O.
- **UI**: Android Views with ViewBinding, Material Components for modern UI elements.
- **File System**: `DocumentFile` API for robust interaction with the Android Storage Access Framework (SAF).
