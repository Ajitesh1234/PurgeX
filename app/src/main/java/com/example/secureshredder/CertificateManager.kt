// app/src/main/java/com/example/secureshredder/CertificateManager.kt

package com.example.secureshredder

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.Color
import android.os.Environment
import android.text.Html
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object CertificateManager {

    private const val PDF_TAG = "CertificatePDF"

    fun generateCertificateAndAttemptSavePdf(
        context: Context,
        itemName: String,
        passes: Int
    ): Pair<String, File?> {
        val uuid = UUID.randomUUID().toString()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(Date())
        val statusValue = "Shredded Successfully"

        var savedPdfFile: File? = null
        var pdfSavePathForDisplay = "Not saved (PDF generation failed)"

        try {
            val documentsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (documentsFolder != null) {
                if (!documentsFolder.exists()) {
                    documentsFolder.mkdirs()
                }

                val sanitizedItemName = itemName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                val pdfFileName = "PurgeX_Certificate_${sanitizedItemName}_${System.currentTimeMillis()}.pdf"
                val file = File(documentsFolder, pdfFileName)

                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val titlePaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 16f
                    isFakeBoldText = true
                }
                val textPaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 10f
                }
                val labelPaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 10f
                    isFakeBoldText = true
                }

                var yPosition = 40f
                val lineHeight = 15f

                canvas.drawText("PurgeX Shredding Certificate", 40f, yPosition, titlePaint)
                yPosition += lineHeight * 2

                fun drawPdfLine(label: String, value: String) {
                    canvas.drawText(label, 40f, yPosition, labelPaint)
                    canvas.drawText(value, 200f, yPosition, textPaint)
                    yPosition += lineHeight
                }

                drawPdfLine("UUID:", uuid)
                drawPdfLine("Timestamp:", timestamp)
                drawPdfLine("Item Name:", itemName)
                drawPdfLine("Overwrite Pass:", passes.toString())
                drawPdfLine("Status:", statusValue)
                
                yPosition += lineHeight
                canvas.drawText("Built by team Spicy Jalebi for SIH 2025", 40f, yPosition, labelPaint)

                pdfDocument.finishPage(page)

                FileOutputStream(file).use { fos ->
                    pdfDocument.writeTo(fos)
                }
                pdfDocument.close()
                savedPdfFile = file
                pdfSavePathForDisplay = file.absolutePath
                Log.i(PDF_TAG, "Certificate PDF saved to: ${file.absolutePath}")
            } else {
                Log.e(PDF_TAG, "Documents folder is null.")
                pdfSavePathForDisplay = "Not saved (Documents folder inaccessible)"
            }
        } catch (e: IOException) {
            Log.e(PDF_TAG, "Error saving PDF certificate: ${e.message}", e)
            pdfSavePathForDisplay = "Not saved (IO Error)"
        } catch (e: Exception) {
            Log.e(PDF_TAG, "General error during PDF generation: ${e.message}", e)
            pdfSavePathForDisplay = "Not saved (Error)"
        }

        val displayCertificateString = buildDisplayCertificateString(
            uuid, timestamp, itemName, passes, statusValue, pdfSavePathForDisplay
        )

        return Pair(displayCertificateString, savedPdfFile)
    }

    private fun buildDisplayCertificateString(
        uuid: String,
        timestamp: String,
        itemName: String,
        passes: Int,
        status: String,
        actualPdfSavePath: String
    ): String {
         return """
            <b>PurgeX Shredding Certificate</b><br/>
            <b>UUID:</b> $uuid<br/>
            <b>Timestamp:</b> $timestamp<br/>
            <b>Item Name:</b> $itemName<br/>
            <b>Overwrite Pass:</b> $passes<br/>
            <b>Status:</b> $status<br/>
            <b>Certificate saved at:</b> $actualPdfSavePath<br/>
            <b>Built by team Spicy Jalebi for SIH 2025</b>
        """.trimIndent()
    }
}
