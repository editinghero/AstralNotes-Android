package com.astralquarks.notes.ai

import org.json.JSONObject

sealed class GeminiNoteAction {
    data class CreateNote(
        val title: String,
        val content: String,
        val tags: List<String> = emptyList()
    ) : GeminiNoteAction()

    data class UpdateNote(
        val title: String?,
        val content: String?,
        val tags: List<String>? = null
    ) : GeminiNoteAction()

    data class AppendToNote(
        val content: String
    ) : GeminiNoteAction()
}

data class ParsedAiMessage(
    val displayText: String,
    val action: GeminiNoteAction? = null
)

object GeminiActionParser {

    private val ACTION_REGEX = Regex(":::action:(\\w+)\\{([\\s\\S]*?)\\}:::", RegexOption.DOT_MATCHES_ALL)

    fun parse(rawResponse: String): ParsedAiMessage {
        val match = ACTION_REGEX.find(rawResponse) ?: return ParsedAiMessage(rawResponse, null)

        val actionType = match.groupValues[1]
        val jsonPayload = "{" + match.groupValues[2] + "}"
        val cleanDisplayText = rawResponse.replace(match.value, "").trim()

        val action: GeminiNoteAction? = try {
            val json = JSONObject(jsonPayload)
            when (actionType) {
                "create_note" -> {
                    val title = json.optString("title", "New Note")
                    val content = json.optString("content", "")
                    val tagsArray = json.optJSONArray("tags")
                    val tags = mutableListOf<String>()
                    if (tagsArray != null) {
                        for (i in 0 until tagsArray.length()) {
                            tags.add(tagsArray.getString(i))
                        }
                    }
                    GeminiNoteAction.CreateNote(title, content, tags)
                }
                "update_note" -> {
                    val title = if (json.has("title")) json.optString("title") else null
                    val content = if (json.has("content")) json.optString("content") else null
                    GeminiNoteAction.UpdateNote(title, content)
                }
                "append_note", "add_checklist" -> {
                    val content = json.optString("content", "")
                    GeminiNoteAction.AppendToNote(content)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }

        val finalDisplay = if (cleanDisplayText.isNotBlank()) cleanDisplayText else "I have prepared the requested note action for you."
        return ParsedAiMessage(finalDisplay, action)
    }
}
