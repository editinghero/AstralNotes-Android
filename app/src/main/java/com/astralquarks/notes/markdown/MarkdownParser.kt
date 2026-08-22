package com.astralquarks.notes.markdown

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Blockquote(val lines: List<String>, val alertType: AlertType? = null) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class NumberedList(val items: List<String>) : MarkdownBlock()
    data class TaskList(val items: List<TaskItem>) : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
    data class ImageBlock(val alt: String, val url: String) : MarkdownBlock()
    data class Details(val summary: String, val content: String) : MarkdownBlock()
}

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
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Blank line
            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // Horizontal Rule (---, ***, ___)
            if (trimmed.matches(Regex("^([\\-*_]\\s*){3,}$"))) {
                blocks.add(MarkdownBlock.HorizontalRule)
                i++
                continue
            }

            // Fenced Code Block (```lang ... ```)
            if (trimmed.startsWith("```")) {
                val language = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size && lines[i].trim().startsWith("```")) {
                    i++ // skip closing ```
                }
                blocks.add(MarkdownBlock.CodeBlock(language, codeLines.joinToString("\n")))
                continue
            }

            // Standalone Image: ![alt](url)
            val imgMatch = Regex("^!\\[(.*?)\\]\\((.*?)\\)$").find(trimmed)
            if (imgMatch != null) {
                val alt = imgMatch.groupValues[1]
                val url = imgMatch.groupValues[2]
                blocks.add(MarkdownBlock.ImageBlock(alt, url))
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
                if (i < lines.size && lines[i].trim().startsWith("</details>", ignoreCase = true)) {
                    i++
                }
                blocks.add(MarkdownBlock.Details(summaryText, detailLines.joinToString("\n").trim()))
                continue
            }


            // Headings (# H1 to ###### H6)
            if (trimmed.startsWith("#")) {
                val hashCount = trimmed.takeWhile { it == '#' }.length
                if (hashCount in 1..6 && trimmed.length > hashCount && trimmed[hashCount] == ' ') {
                    val headingText = trimmed.substring(hashCount).trim()
                    blocks.add(MarkdownBlock.Heading(hashCount, headingText))
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
                blocks.add(MarkdownBlock.Blockquote(quoteLines, alertType))
                continue
            }

            // Task list (- [ ] or - [x] or * [ ])
            if (trimmed.matches(Regex("^[\\-*+]\\s+\\[[ xX]\\]\\s+.*"))) {
                val taskItems = mutableListOf<TaskItem>()
                while (i < lines.size && lines[i].trim().matches(Regex("^[\\-*+]\\s+\\[[ xX]\\]\\s+.*"))) {
                    val currentLine = lines[i].trim()
                    val isChecked = currentLine.matches(Regex("^[\\-*+]\\s+\\[[xX]\\].*"))
                    val taskText = currentLine.replaceFirst(Regex("^[\\-*+]\\s+\\[[ xX]\\]\\s*"), "")
                    taskItems.add(TaskItem(checked = isChecked, text = taskText, rawLineIndex = i))
                    i++
                }
                blocks.add(MarkdownBlock.TaskList(taskItems))
                continue
            }

            // Bullet list (- or * or +)
            if (trimmed.matches(Regex("^[\\-*+]\\s+.*"))) {
                val items = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().matches(Regex("^[\\-*+]\\s+.*")) && !lines[i].trim().matches(Regex("^[\\-*+]\\s+\\[[ xX]\\].*"))) {
                    val itemText = lines[i].trim().replaceFirst(Regex("^[\\-*+]\\s+"), "")
                    items.add(itemText)
                    i++
                }
                blocks.add(MarkdownBlock.BulletList(items))
                continue
            }

            // Numbered list (1. 2. etc)
            if (trimmed.matches(Regex("^\\d+\\.\\s+.*"))) {
                val items = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().matches(Regex("^\\d+\\.\\s+.*"))) {
                    val itemText = lines[i].trim().replaceFirst(Regex("^\\d+\\.\\s+"), "")
                    items.add(itemText)
                    i++
                }
                blocks.add(MarkdownBlock.NumberedList(items))
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
                blocks.add(MarkdownBlock.Table(headerRow, rows))
                continue
            }

            // Regular Paragraph
            val paragraphLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().isNotEmpty() &&
                !lines[i].trim().startsWith("#") &&
                !lines[i].trim().startsWith("```") &&
                !lines[i].trim().startsWith(">") &&
                !lines[i].trim().matches(Regex("^[\\-*+]\\s+.*")) &&
                !lines[i].trim().matches(Regex("^\\d+\\.\\s+.*")) &&
                !lines[i].trim().matches(Regex("^([\\-*_]\\s*){3,}$")) &&
                !(lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|"))
            ) {
                paragraphLines.add(lines[i])
                i++
            }
            if (paragraphLines.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString("\n")))
            } else {
                i++
            }
        }

        return blocks
    }

    /**
     * Toggles a checkbox in the markdown text given task text and line index.
     */
    fun toggleChecklist(markdown: String, taskItem: TaskItem): String {
        val lines = markdown.lines().toMutableList()
        if (taskItem.rawLineIndex in 0 until lines.size) {
            val line = lines[taskItem.rawLineIndex]
            val newLine = if (taskItem.checked) {
                line.replaceFirst(Regex("\\[[xX]\\]"), "[ ]")
            } else {
                line.replaceFirst(Regex("\\[ \\]"), "[x]")
            }
            lines[taskItem.rawLineIndex] = newLine
            return lines.joinToString("\n")
        }

        // Fallback search by text match
        for (idx in lines.indices) {
            val line = lines[idx]
            if (line.contains(taskItem.text)) {
                if (taskItem.checked && line.contains("[x]", ignoreCase = true)) {
                    lines[idx] = line.replaceFirst(Regex("\\[[xX]\\]"), "[ ]")
                    return lines.joinToString("\n")
                } else if (!taskItem.checked && line.contains("[ ]")) {
                    lines[idx] = line.replaceFirst("[ ]", "[x]")
                    return lines.joinToString("\n")
                }
            }
        }
        return markdown
    }
}
