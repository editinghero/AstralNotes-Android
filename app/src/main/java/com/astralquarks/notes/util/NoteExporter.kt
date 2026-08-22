package com.astralquarks.notes.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.astralquarks.notes.model.Note
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NoteExporter {

    enum class ExportFormat {
        MARKDOWN,
        HTML,
        PDF
    }

    fun shareNote(context: Context, note: Note, format: ExportFormat) {
        try {
            when (format) {
                ExportFormat.MARKDOWN -> shareAsMarkdown(context, note)
                ExportFormat.HTML -> shareAsHtml(context, note)
                ExportFormat.PDF -> shareAsPdf(context, note)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share note: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getSanitizedFilename(title: String, extension: String): String {
        val clean = title.ifBlank { "Note" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(30)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${clean}_$timestamp.$extension"
    }

    private fun shareAsMarkdown(context: Context, note: Note) {
        val notesDir = File(context.cacheDir, "notes").apply { mkdirs() }
        val filename = getSanitizedFilename(note.title, "md")
        val file = File(notesDir, filename)

        val fullMd = buildString {
            if (note.title.isNotBlank()) {
                appendLine("# ${note.title}")
                appendLine()
            }
            if (note.tags.isNotEmpty()) {
                appendLine("Tags: ${note.tags.joinToString(" ") { "#$it" }}")
                appendLine()
            }
            appendLine(note.content)
        }

        FileOutputStream(file).use { it.write(fullMd.toByteArray()) }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "AstralNotes" })
            putExtra(Intent.EXTRA_TEXT, fullMd)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Markdown Note via"))
    }

    private fun shareAsHtml(context: Context, note: Note) {
        val notesDir = File(context.cacheDir, "notes").apply { mkdirs() }
        val filename = getSanitizedFilename(note.title, "html")
        val file = File(notesDir, filename)

        val dateFormatted = SimpleDateFormat("MMM d, yyyy - HH:mm", Locale.getDefault()).format(Date(note.updatedAt))

        // Basic Markdown to HTML conversion
        var bodyHtml = note.content
            .replace(Regex("&"), "&amp;")
            .replace(Regex("<"), "&lt;")
            .replace(Regex(">"), "&gt;")
            // Headings
            .replace(Regex("(?m)^### (.*?)$"), "<h3>$1</h3>")
            .replace(Regex("(?m)^## (.*?)$"), "<h2>$1</h2>")
            .replace(Regex("(?m)^# (.*?)$"), "<h1>$1</h1>")
            // Checklists
            .replace(Regex("(?m)^[\\-*+]\\s+\\[x\\]\\s*(.*?)$"), "<div class=\"check done\">&#9745; $1</div>")
            .replace(Regex("(?m)^[\\-*+]\\s+\\[ \\]\\s*(.*?)$"), "<div class=\"check\">&#9744; $1</div>")
            // Bold, Italic, Strikethrough, Highlight
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "<strong>$1</strong>")
            .replace(Regex("\\*(.*?)\\*"), "<em>$1</em>")
            .replace(Regex("~~(.*?)~~"), "<del>$1</del>")
            .replace(Regex("==(.*?)==|<mark>(.*?)</mark>"), "<mark>$1</mark>")
            // Images
            .replace(Regex("!\\[(.*?)\\]\\((https?://.*?)\\)"), "<img src=\"$2\" alt=\"$1\" style=\"max-width:100%; border-radius:12px; margin:12px 0;\" />")
            // Links
            .replace(Regex("\\[(.*?)\\]\\((https?://.*?)\\)"), "<a href=\"$2\" target=\"_blank\">$1</a>")
            // Line breaks
            .replace("\n", "<br/>")

        val htmlDoc = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${note.title.ifBlank { "AstralNotes" }}</title>
<style>
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    line-height: 1.6;
    color: #1d1b20;
    background-color: #fef7ff;
    padding: 24px;
    max-width: 680px;
    margin: 0 auto;
  }
  .card {
    background: #ffffff;
    border-radius: 20px;
    padding: 28px;
    box-shadow: 0 4px 16px rgba(0,0,0,0.06);
    border: 1px solid #e6e0e9;
  }
  h1 { font-size: 26px; color: #21005d; margin-top: 0; }
  h2 { font-size: 20px; color: #49454f; }
  h3 { font-size: 16px; color: #49454f; }
  .meta { font-size: 12px; color: #79747e; margin-bottom: 20px; border-bottom: 1px solid #e6e0e9; padding-bottom: 12px; }
  .tag { display: inline-block; background: #eaddff; color: #21005d; padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: bold; margin-right: 6px; margin-bottom: 6px; }
  .check { margin: 6px 0; font-size: 15px; }
  .check.done { color: #79747e; text-decoration: line-through; }
  mark { background: #ffe082; padding: 2px 4px; border-radius: 4px; }
  a { color: #6750a4; text-decoration: underline; }
  footer { margin-top: 24px; text-align: center; font-size: 12px; color: #79747e; }
</style>
</head>
<body>
<div class="card">
  <h1>${note.title.ifBlank { "Untitled Note" }}</h1>
  <div class="meta">
    <span>Last updated: $dateFormatted</span>
    ${if (note.tags.isNotEmpty()) "<div style='margin-top:8px;'>" + note.tags.joinToString("") { "<span class='tag'>#$it</span>" } + "</div>" else ""}
  </div>
  <div class="content">
    $bodyHtml
  </div>
</div>
<footer>Exported with AstralNotes</footer>
</body>
</html>
        """.trimIndent()

        FileOutputStream(file).use { it.write(htmlDoc.toByteArray()) }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "AstralNotes Document" })
            putExtra(Intent.EXTRA_TEXT, htmlDoc)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share HTML Note via"))
    }

    private fun shareAsPdf(context: Context, note: Note) {
        val notesDir = File(context.cacheDir, "notes").apply { mkdirs() }
        val filename = getSanitizedFilename(note.title, "pdf")
        val file = File(notesDir, filename)

        val pageWidth = 595 // Standard A4 points at 72dpi
        val pageHeight = 842
        val margin = 40f

        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        // Paints
        val titlePaint = Paint().apply {
            color = AndroidColor.rgb(33, 0, 93) // Primary
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val datePaint = Paint().apply {
            color = AndroidColor.rgb(121, 116, 126)
            textSize = 10f
            isAntiAlias = true
        }

        val headingPaint = Paint().apply {
            color = AndroidColor.rgb(49, 44, 52)
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = AndroidColor.rgb(29, 27, 32)
            textSize = 12f
            isAntiAlias = true
        }

        val tagPaint = Paint().apply {
            color = AndroidColor.rgb(103, 80, 164)
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val dividerPaint = Paint().apply {
            color = AndroidColor.rgb(230, 224, 233)
            strokeWidth = 1f
        }

        var currentY = margin + 20f

        // Draw Title
        val displayTitle = note.title.ifBlank { "Untitled Note" }
        canvas.drawText(displayTitle, margin, currentY, titlePaint)
        currentY += 20f

        // Draw Date & Tags
        val dateFormatted = SimpleDateFormat("MMM d, yyyy - HH:mm", Locale.getDefault()).format(Date(note.updatedAt))
        canvas.drawText("Updated: $dateFormatted", margin, currentY, datePaint)
        currentY += 14f

        if (note.tags.isNotEmpty()) {
            val tagsStr = note.tags.joinToString(" ") { "#$it" }
            canvas.drawText(tagsStr, margin, currentY, tagPaint)
            currentY += 16f
        }

        // Draw divider
        canvas.drawLine(margin, currentY, pageWidth - margin, currentY, dividerPaint)
        currentY += 20f

        val textWidth = (pageWidth - (margin * 2)).toInt()

        // Render content lines
        val lines = note.content.lines()
        for (rawLine in lines) {
            val isHeading = rawLine.startsWith("#")
            val isChecklist = rawLine.trim().matches(Regex("^[\\-*+]\\s+\\[[ xX]\\].*"))
            val cleanText = rawLine
                .replace(Regex("^#+\\s*"), "")
                .replace(Regex("!\\[.*?\\]\\(.*?\\)"), "[Image]")

            val activePaint = if (isHeading) headingPaint else bodyPaint
            val lineHeight = if (isHeading) 22f else 18f

            // Wrap text if line is long
            val words = cleanText.split(" ")
            var lineBuf = ""

            for (word in words) {
                val testLine = if (lineBuf.isEmpty()) word else "$lineBuf $word"
                val measuredWidth = activePaint.measureText(testLine)

                if (measuredWidth > textWidth) {
                    // Check if we need a new page
                    if (currentY + lineHeight > pageHeight - margin) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = margin + 20f
                    }

                    canvas.drawText(lineBuf, margin, currentY, activePaint)
                    currentY += lineHeight
                    lineBuf = word
                } else {
                    lineBuf = testLine
                }
            }

            if (lineBuf.isNotEmpty()) {
                if (currentY + lineHeight > pageHeight - margin) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = margin + 20f
                }
                canvas.drawText(lineBuf, margin, currentY, activePaint)
                currentY += lineHeight
            }
        }

        // Finish document
        pdfDocument.finishPage(page)
        FileOutputStream(file).use { pdfDocument.writeTo(it) }
        pdfDocument.close()

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "AstralNotes PDF" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF Note via"))
    }
}
