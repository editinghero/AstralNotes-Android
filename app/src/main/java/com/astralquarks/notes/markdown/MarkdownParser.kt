package com.astralquarks.notes.markdown

sealed class MarkdownBlock {
    var startOffset: Int = 0
    var endOffset: Int = 0

    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Blockquote(val lines: List<String>, val alertType: AlertType? = null) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class NumberedList(val items: List<NumberedItem>) : MarkdownBlock()
    data class TaskList(val items: List<TaskItem>) : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
    data class ImageBlock(val alt: String, val url: String) : MarkdownBlock()
    data class Details(val summary: String, val content: String) : MarkdownBlock()
}

data class NumberedItem(
    val number: Int,
    val text: String
)

data class TaskItem(
    val checked: Boolean,
    val text: String,
    val rawLineIndex: Int = -1
)

enum class AlertType(val title: String) {
    NOTE("Note"),
    TIP("Tip"),
    IMPORTANT("Important"),
    WARNING("Warning"),
    CAUTION("Caution")
}

object MarkdownParser {

    fun parse(markdown: String): List<MarkdownBlock> {
        if (markdown.isBlank()) return emptyList()
        val lines = markdown.lines()
        val blocks = mutableListOf<MarkdownBlock>()

        // Pre-calculate line offsets
        val lineOffsets = IntArray(lines.size)
        var currentGlobalOffset = 0
        for (idx in lines.indices) {
            lineOffsets[idx] = currentGlobalOffset
            val originalNewlineLen = if (currentGlobalOffset + lines[idx].length < markdown.length && markdown[currentGlobalOffset + lines[idx].length] == '\r') 2 else 1
            currentGlobalOffset += lines[idx].length + originalNewlineLen
        }

        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            val blockStartOffset = lineOffsets[i]

            // Blank line
            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // Horizontal Rule (--- or *** or ___)
            if (trimmed.matches(Regex("^([\\-*_]\\s*){3,}$"))) {
                val block = MarkdownBlock.HorizontalRule
                block.startOffset = blockStartOffset
                block.endOffset = blockStartOffset + line.length
                blocks.add(block)
                i++
                continue
            }

            // Code Block (```)
            if (trimmed.startsWith("```")) {
                val language = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                var endOff = if (i < lines.size) lineOffsets[i] + lines[i].length else markdown.length
                if (i < lines.size && lines[i].trim().startsWith("```")) {
                    i++ // skip closing ```
                }
                val block = MarkdownBlock.CodeBlock(language, codeLines.joinToString("\n"))
                block.startOffset = blockStartOffset
                block.endOffset = endOff
                blocks.add(block)
                continue
            }

            // Standalone Image: ![alt](url)
            val imgMatch = Regex("^!\\[(.*?)\\]\\((.*?)\\)$").find(trimmed)
            if (imgMatch != null) {
                val alt = imgMatch.groupValues[1]
                val url = imgMatch.groupValues[2]
                val block = MarkdownBlock.ImageBlock(alt, url)
                block.startOffset = blockStartOffset
                block.endOffset = blockStartOffset + line.length
                blocks.add(block)
                i++
                continue
            }

            // Details (<details><summary>...</summary>...</details>)
            if (trimmed.startsWith("<details>", ignoreCase = true)) {
                val detailLines = mutableListOf<String>()
                var summaryText = "Details"
                val sameLineSummaryMatch = Regex("<summary>(.*?)</summary>", RegexOption.IGNORE_CASE).find(trimmed)
                if (sameLineSummaryMatch != null) {
                    summaryText = sameLineSummaryMatch.groupValues[1]
                }
                i++
                while (i < lines.size && !lines[i].trim().startsWith("</details>", ignoreCase = true)) {
                    val currentLine = lines[i].trim()
                    if (currentLine.startsWith("<summary>", ignoreCase = true)) {
                        val summaryMatch = Regex("<summary>(.*?)</summary>", RegexOption.IGNORE_CASE).find(currentLine)
                        if (summaryMatch != null) {
                            summaryText = summaryMatch.groupValues[1]
                        } else {
                            summaryText = currentLine.removePrefix("<summary>").removePrefix("<SUMMARY>").removeSuffix("</summary>").removeSuffix("</SUMMARY>")
                        }
                    } else if (currentLine != "</summary>" && currentLine != "</SUMMARY>") {
                        detailLines.add(lines[i])
                    }
                    i++
                }
                var endOff = if (i < lines.size) lineOffsets[i] + lines[i].length else markdown.length
                if (i < lines.size && lines[i].trim().startsWith("</details>", ignoreCase = true)) {
                    i++
                }
                val block = MarkdownBlock.Details(summaryText, detailLines.joinToString("\n").trim())
                block.startOffset = blockStartOffset
                block.endOffset = endOff
                blocks.add(block)
                continue
            }


            // Headings (# H1 to ###### H6)
            if (trimmed.startsWith("#")) {
                val hashCount = trimmed.takeWhile { it == '#' }.length
                if (hashCount in 1..6 && trimmed.length > hashCount && trimmed[hashCount] == ' ') {
                    val headingText = trimmed.substring(hashCount).trim()
                    val block = MarkdownBlock.Heading(hashCount, headingText)
                    block.startOffset = blockStartOffset
                    block.endOffset = blockStartOffset + line.length
                    blocks.add(block)
                    i++
                    continue
                }
            }

            // Blockquote & Callout alerts (> [!NOTE] or > quote)
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                var alertType: AlertType? = null

                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    var cleanLine = lines[i].trim().removePrefix(">").trim()
                    if (quoteLines.isEmpty()) {
                        val alertMatch = Regex("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\\]", RegexOption.IGNORE_CASE).find(cleanLine)
                        if (alertMatch != null) {
                            val typeStr = alertMatch.groupValues[1].uppercase()
                            alertType = try { AlertType.valueOf(typeStr) } catch (e: Exception) { null }
                            cleanLine = cleanLine.replace(alertMatch.value, "").trim()
                        }
                    }
                    if (cleanLine.isNotEmpty() || quoteLines.isNotEmpty()) {
                        quoteLines.add(cleanLine)
                    }
                    i++
                }
                val endOff = if (i > 0) lineOffsets[i - 1] + lines[i - 1].length else blockStartOffset
                val block = MarkdownBlock.Blockquote(quoteLines, alertType)
                block.startOffset = blockStartOffset
                block.endOffset = endOff
                blocks.add(block)
                continue
            }

            // Task list (- [ ] or - [x] or * [ ])
            if (trimmed.matches(Regex("^[\\-*+]\\s*\\[[ xX]\\](\\s.*)?$"))) {
                val taskItems = mutableListOf<TaskItem>()
                while (i < lines.size && lines[i].trim().matches(Regex("^[\\-*+]\\s*\\[[ xX]\\](\\s.*)?$"))) {
                    val currentLine = lines[i].trim()
                    val isChecked = currentLine.matches(Regex("^[\\-*+]\\s*\\[[xX]\\].*"))
                    val taskText = currentLine.replaceFirst(Regex("^[\\-*+]\\s*\\[[ xX]\\]\\s*"), "")
                    taskItems.add(TaskItem(checked = isChecked, text = taskText, rawLineIndex = i))
                    i++
                }
                val endOff = if (i > 0) lineOffsets[i - 1] + lines[i - 1].length else blockStartOffset
                val block = MarkdownBlock.TaskList(taskItems)
                block.startOffset = blockStartOffset
                block.endOffset = endOff
                blocks.add(block)
                continue
            }

            // Bullet list (- or * or +)
            if (trimmed.matches(Regex("^[\\-*+]\\s+.*"))) {
                val items = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().matches(Regex("^[\\-*+]\\s+.*")) && !lines[i].trim().matches(Regex("^[\\-*+]\\s*\\[[ xX]\\].*"))) {
                    val itemText = lines[i].trim().replaceFirst(Regex("^[\\-*+]\\s+"), "")
                    items.add(itemText)
                    i++
                }
                if (items.isNotEmpty()) {
                    val endOff = if (i > 0) lineOffsets[i - 1] + lines[i - 1].length else blockStartOffset
                    val block = MarkdownBlock.BulletList(items)
                    block.startOffset = blockStartOffset
                    block.endOffset = endOff
                    blocks.add(block)
                } else {
                    i++ // Safe advance
                }
                continue
            }

            // Numbered list (1. 2. etc) - Preserve explicit numbering
            val numberedMatch = Regex("^(\\d+)\\.\\s*(.*)$").find(trimmed)
            if (numberedMatch != null) {
                val items = mutableListOf<NumberedItem>()
                while (i < lines.size) {
                    val lineTrimmed = lines[i].trim()
                    val itemMatch = Regex("^(\\d+)\\.\\s*(.*)$").find(lineTrimmed)
                    if (itemMatch != null) {
                        val num = itemMatch.groupValues[1].toIntOrNull() ?: (items.size + 1)
                        val text = itemMatch.groupValues[2]
                        items.add(NumberedItem(number = num, text = text))
                        i++
                    } else {
                        break
                    }
                }
                if (items.isNotEmpty()) {
                    val endOff = if (i > 0) lineOffsets[i - 1] + lines[i - 1].length else blockStartOffset
                    val block = MarkdownBlock.NumberedList(items)
                    block.startOffset = blockStartOffset
                    block.endOffset = endOff
                    blocks.add(block)
                } else {
                    i++ // Safe advance
                }
                continue
            }

            // Table (| Col 1 | Col 2 |)
            if (trimmed.startsWith("|") && trimmed.endsWith("|") && i + 1 < lines.size && lines[i + 1].trim().matches(Regex("^\\|[\\s\\-:\\|]+\\|$"))) {
                val headerRow = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                i += 2 // skip header and delimiter (|---|---|)
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    val rowCells = lines[i].trim().split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    rows.add(rowCells)
                    i++
                }
                val endOff = if (i > 0) lineOffsets[i - 1] + lines[i - 1].length else blockStartOffset
                val block = MarkdownBlock.Table(headerRow, rows)
                block.startOffset = blockStartOffset
                block.endOffset = endOff
                blocks.add(block)
                continue
            }

            // Regular Paragraph
            val paragraphLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().isNotEmpty() &&
                !lines[i].trim().startsWith("#") &&
                !lines[i].trim().startsWith("```") &&
                !lines[i].trim().startsWith(">") &&
                !lines[i].trim().matches(Regex("^[\\-*+]\\s+.*")) &&
                !lines[i].trim().matches(Regex("^\\d+\\.\\s*.*")) &&
                !lines[i].trim().matches(Regex("^([\\-*_]\\s*){3,}$")) &&
                !(lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|"))
            ) {
                paragraphLines.add(lines[i])
                i++
            }
            if (paragraphLines.isNotEmpty()) {
                val endOff = if (i > 0) lineOffsets[i - 1] + lines[i - 1].length else blockStartOffset
                val block = MarkdownBlock.Paragraph(paragraphLines.joinToString("\n"))
                block.startOffset = blockStartOffset
                block.endOffset = endOff
                blocks.add(block)
            } else {
                i++
            }
        }

        return blocks
    }
fun toggleChecklist(markdown: String, taskItem: TaskItem): String {
        val lines = markdown.lines().toMutableList()
        if (taskItem.rawLineIndex in 0 until lines.size) {
            val line = lines[taskItem.rawLineIndex]
            if (line.contains("[x]", ignoreCase = true) || line.contains("[ ]")) {
                val newLine = if (taskItem.checked) {
                    line.replaceFirst(Regex("\\[[xX]\\]"), "[ ]")
                } else {
                    line.replaceFirst(Regex("\\[ \\]"), "[x]")
                }
                lines[taskItem.rawLineIndex] = newLine
                return lines.joinToString("\n")
            }
        }

        // Fallback search by non-empty task text
        if (taskItem.text.isNotBlank()) {
            for (idx in lines.indices) {
                val line = lines[idx]
                if (line.contains(taskItem.text) && (line.contains("[x]", ignoreCase = true) || line.contains("[ ]"))) {
                    if (taskItem.checked && line.contains("[x]", ignoreCase = true)) {
                        lines[idx] = line.replaceFirst(Regex("\\[[xX]\\]"), "[ ]")
                        return lines.joinToString("\n")
                    } else if (!taskItem.checked && line.contains("[ ]")) {
                        lines[idx] = line.replaceFirst("[ ]", "[x]")
                        return lines.joinToString("\n")
                    }
                }
            }
        }
        return markdown
    }
}
