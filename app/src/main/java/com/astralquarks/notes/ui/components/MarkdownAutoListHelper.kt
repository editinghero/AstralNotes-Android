package com.astralquarks.notes.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Intelligent Markdown list auto-continuation and backspace cancellation helper.
 * Supports:
 * - Checklists: `- [ ] ` or `- [x] ` or `* [ ] ` or `+ [ ] `
 * - Bullet lists: `- `, `* `, `+ `
 * - Numbered lists: `1. `, `2. `, `10. ` etc.
 * - Blockquotes: `> `
 */
object MarkdownAutoListHelper {

    private val CHECKLIST_REGEX = Regex("^(\\s*)([\\-*+]\\s*\\[[ xX]\\]\\s+)(.*)$")
    private val BULLET_REGEX = Regex("^(\\s*)([\\-*+]\\s+)(.*)$")
    private val NUMBERED_REGEX = Regex("^(\\s*)(\\d+)(\\.\\s+)(.*)$")
    private val QUOTE_REGEX = Regex("^(\\s*>\\s+)(.*)$")

    /**
     * Handles text input changes to check if a newline was just inserted.
     * If so, automatically continues bullet, numbered, checklist, or quote prefixes.
     * If the user pressed enter on an EMPTY list item, it removes the prefix instead of making a new one.
     */
    fun handleTextChange(
        oldValue: TextFieldValue,
        newValue: TextFieldValue
    ): TextFieldValue {
        val oldText = oldValue.text
        val newText = newValue.text

        // Check if exactly one character was added and that character is a newline ('\n')
        if (newText.length == oldText.length + 1 && newValue.selection.collapsed) {
            val cursor = newValue.selection.start
            if (cursor > 0 && newText[cursor - 1] == '\n') {
                // Find previous line content before this newline
                val textBeforeNewline = newText.substring(0, cursor - 1)
                val lastNewlineIndex = textBeforeNewline.lastIndexOf('\n')
                val previousLine = if (lastNewlineIndex == -1) textBeforeNewline else textBeforeNewline.substring(lastNewlineIndex + 1)

                // 1. Checklist
                val checkMatch = CHECKLIST_REGEX.find(previousLine)
                if (checkMatch != null) {
                    val indent = checkMatch.groupValues[1]
                    val content = checkMatch.groupValues[3].trim()
                    if (content.isEmpty()) {
                        // Empty checklist item -> Clear it on enter!
                        val lineStart = if (lastNewlineIndex == -1) 0 else lastNewlineIndex + 1
                        val textBefore = newText.substring(0, lineStart)
                        val textAfter = newText.substring(cursor)
                        return TextFieldValue(textBefore + textAfter, TextRange(lineStart))
                    } else {
                        // Continue checklist
                        val prefix = "$indent- [ ] "
                        val textBefore = newText.substring(0, cursor)
                        val textAfter = newText.substring(cursor)
                        val updated = textBefore + prefix + textAfter
                        return TextFieldValue(updated, TextRange(cursor + prefix.length))
                    }
                }

                // 2. Bullet list (- or * or +)
                val bulletMatch = BULLET_REGEX.find(previousLine)
                if (bulletMatch != null) {
                    val indent = bulletMatch.groupValues[1]
                    val bulletChar = bulletMatch.groupValues[2].trim()
                    val content = bulletMatch.groupValues[3].trim()
                    if (content.isEmpty()) {
                        // Empty bullet -> Clear it!
                        val lineStart = if (lastNewlineIndex == -1) 0 else lastNewlineIndex + 1
                        val textBefore = newText.substring(0, lineStart)
                        val textAfter = newText.substring(cursor)
                        return TextFieldValue(textBefore + textAfter, TextRange(lineStart))
                    } else {
                        val prefix = "$indent$bulletChar "
                        val textBefore = newText.substring(0, cursor)
                        val textAfter = newText.substring(cursor)
                        val updated = textBefore + prefix + textAfter
                        return TextFieldValue(updated, TextRange(cursor + prefix.length))
                    }
                }

                // 3. Numbered list (1. , 2. ...)
                val numMatch = NUMBERED_REGEX.find(previousLine)
                if (numMatch != null) {
                    val indent = numMatch.groupValues[1]
                    val num = numMatch.groupValues[2].toIntOrNull() ?: 1
                    val content = numMatch.groupValues[4].trim()
                    if (content.isEmpty()) {
                        // Empty numbered item -> Clear it!
                        val lineStart = if (lastNewlineIndex == -1) 0 else lastNewlineIndex + 1
                        val textBefore = newText.substring(0, lineStart)
                        val textAfter = newText.substring(cursor)
                        return TextFieldValue(textBefore + textAfter, TextRange(lineStart))
                    } else {
                        val nextNum = num + 1
                        val prefix = "$indent$nextNum. "
                        val textBefore = newText.substring(0, cursor)
                        val textAfter = newText.substring(cursor)
                        val updated = textBefore + prefix + textAfter
                        return TextFieldValue(updated, TextRange(cursor + prefix.length))
                    }
                }

                // 4. Quote (> Quote)
                val quoteMatch = QUOTE_REGEX.find(previousLine)
                if (quoteMatch != null) {
                    val content = quoteMatch.groupValues[2].trim()
                    if (content.isEmpty()) {
                        val lineStart = if (lastNewlineIndex == -1) 0 else lastNewlineIndex + 1
                        val textBefore = newText.substring(0, lineStart)
                        val textAfter = newText.substring(cursor)
                        return TextFieldValue(textBefore + textAfter, TextRange(lineStart))
                    } else {
                        val prefix = "> "
                        val textBefore = newText.substring(0, cursor)
                        val textAfter = newText.substring(cursor)
                        val updated = textBefore + prefix + textAfter
                        return TextFieldValue(updated, TextRange(cursor + prefix.length))
                    }
                }
            }
        }

        return newValue
    }

    /**
     * Determines continuation for single inline line editor when user presses Enter
     */
    fun getNextLineContinuation(currentLine: String): String? {
        val checkMatch = CHECKLIST_REGEX.find(currentLine)
        if (checkMatch != null) {
            val indent = checkMatch.groupValues[1]
            return "$indent- [ ] "
        }
        val bulletMatch = BULLET_REGEX.find(currentLine)
        if (bulletMatch != null) {
            val indent = bulletMatch.groupValues[1]
            val bulletChar = bulletMatch.groupValues[2].trim()
            return "$indent$bulletChar "
        }
        val numMatch = NUMBERED_REGEX.find(currentLine)
        if (numMatch != null) {
            val indent = numMatch.groupValues[1]
            val num = numMatch.groupValues[2].toIntOrNull() ?: 1
            return "$indent${num + 1}. "
        }
        val quoteMatch = QUOTE_REGEX.find(currentLine)
        if (quoteMatch != null) {
            return "> "
        }
        return null
    }
}
