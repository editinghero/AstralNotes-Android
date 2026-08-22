package com.astralquarks.notes.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.astralquarks.notes.BuildConfig
import com.astralquarks.notes.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

class GeminiManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("gemini_ai_prefs", Context.MODE_PRIVATE)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    var customApiKey: String
        get() = prefs.getString("custom_api_key", "") ?: ""
        set(value) = prefs.edit().putString("custom_api_key", value).apply()

    var customModelName: String
        get() = prefs.getString("custom_model_name", "gemini-3.7-flash") ?: "gemini-3.7-flash"
        set(value) = prefs.edit().putString("custom_model_name", value).apply()

    var isThinkingModeEnabled: Boolean
        get() = prefs.getBoolean("thinking_mode_enabled", false)
        set(value) = prefs.edit().putBoolean("thinking_mode_enabled", value).apply()

    var isSearchGroundingEnabled: Boolean
        get() = prefs.getBoolean("search_grounding_enabled", false)
        set(value) = prefs.edit().putBoolean("search_grounding_enabled", value).apply()

    fun getEffectiveApiKey(): String {
        return customApiKey.trim()
    }

    suspend fun generateContent(
        prompt: String,
        systemInstruction: String? = null,
        history: List<ChatMessage> = emptyList(),
        modelOverride: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                Exception("Gemini API key is required. Please add your API key in Settings or AI dialog.")
            )
        }

        var activeModel = modelOverride ?: customModelName.ifBlank { "gemini-3.7-flash" }

        // If thinking mode is explicitly enabled and model is default flash, allow thinking mode
        if (isThinkingModeEnabled && (activeModel == "gemini-3.7-flash" || activeModel == "gemini-3.1-pro-preview")) {
            activeModel = "gemini-3.1-pro-preview"
        }

        try {
            val jsonRequest = JSONObject()

            // System instruction
            if (!systemInstruction.isNullOrBlank()) {
                val sysParts = JSONArray().put(JSONObject().put("text", systemInstruction))
                jsonRequest.put("systemInstruction", JSONObject().put("parts", sysParts))
            }

            // Contents array
            val contentsArray = JSONArray()
            history.forEach { msg ->
                val role = if (msg.role == "model" || msg.role == "assistant") "model" else "user"
                val part = JSONObject().put("text", msg.content)
                val contentObj = JSONObject()
                    .put("role", role)
                    .put("parts", JSONArray().put(part))
                contentsArray.put(contentObj)
            }

            // Add current prompt
            val currentPart = JSONObject().put("text", prompt)
            val currentContent = JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(currentPart))
            contentsArray.put(currentContent)

            jsonRequest.put("contents", contentsArray)

            // Generation config
            val genConfig = JSONObject()
            if (isThinkingModeEnabled) {
                val thinkingConfig = JSONObject().put("thinkingLevel", "high")
                genConfig.put("thinkingConfig", thinkingConfig)
            } else {
                genConfig.put("temperature", 0.7)
            }
            jsonRequest.put("generationConfig", genConfig)

            // Google Search tool if grounding is enabled
            if (isSearchGroundingEnabled && activeModel.contains("gemini-3.5-flash")) {
                val toolsArray = JSONArray().put(JSONObject().put("googleSearch", JSONObject()))
                jsonRequest.put("tools", toolsArray)
            }

            val requestBody = jsonRequest.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$activeModel:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errorJson = JSONObject(responseBody)
                    errorJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }
                return@withContext Result.failure(Exception("Gemini API error: $errorMsg"))
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val textBuilder = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val text = part.optString("text", "")
                        textBuilder.append(text)
                    }
                    val resultText = textBuilder.toString()
                    return@withContext Result.success(resultText)
                }
            }

            Result.failure(Exception("No output received from Gemini model."))
        } catch (e: Exception) {
            Log.e("GeminiManager", "Error generating content", e)
            Result.failure(e)
        }
    }

    suspend fun summarizeNote(note: Note): Result<String> {
        val prompt = "Please provide a clear, concise executive summary in markdown bullet points for the following note:\n\n# ${note.title}\n${note.content}"
        return generateContent(prompt, systemInstruction = "You are a professional note-taking and summarization AI assistant. Return well-formatted Markdown.")
    }

    suspend fun generateActionChecklist(note: Note): Result<String> {
        val prompt = "Extract all actionable tasks, to-dos, and next steps from this note into markdown task checklist format `- [ ] Task item`:\n\n# ${note.title}\n${note.content}"
        return generateContent(prompt, systemInstruction = "You are an action item extraction specialist. Return pure markdown checklists (- [ ] task).")
    }

    suspend fun polishMarkdownNote(note: Note): Result<String> {
        val prompt = "Enhance, fix grammar, and upgrade the Markdown structure and formatting of this note while preserving its core facts and intent. Use headings, lists, tables, callouts, and clean markdown:\n\n# ${note.title}\n${note.content}"
        return generateContent(prompt, systemInstruction = "You are an expert Markdown editor and writer. Return the polished note in complete Markdown.")
    }

    suspend fun brainstormIdeas(note: Note): Result<String> {
        val prompt = "Brainstorm 5 creative ideas, follow-ups, and insightful questions expanding on this note:\n\n# ${note.title}\n${note.content}"
        return generateContent(prompt, systemInstruction = "You are a creative brainstorming assistant. Provide structured, inspirational Markdown recommendations.")
    }

    suspend fun chatWithNote(note: Note, userQuestion: String, chatHistory: List<ChatMessage>): Result<String> {
        val systemInstruction = """
            You are AstralNotes AI assistant answering questions and performing actions for the user's note.
            ---
            CURRENT NOTE TITLE: ${note.title}
            CURRENT NOTE CONTENT:
            ${note.content}
            ---
            CAPABILITIES & TOOLS:
            If the user asks you to modify, create, make a list, add items, or edit the note:
            1. If creating a brand new note/list, include at the end: :::action:create_note{"title": "Title here", "content": "Markdown content here"}:::
            2. If updating/replacing this current note or modifying it, include at the end: :::action:update_note{"title": "Updated Title", "content": "Updated full note markdown"}:::
            3. If appending or adding checklist items to the current note, include at the end: :::action:append_note{"content": "\n- [ ] Task 1\n- [ ] Task 2"}:::
            
            Always provide a polite explanation in clean Markdown alongside any action.
        """.trimIndent()

        return generateContent(
            prompt = userQuestion,
            systemInstruction = systemInstruction,
            history = chatHistory
        )
    }

    suspend fun askAcrossAllNotes(
        notes: List<Note>,
        userQuestion: String,
        chatHistory: List<ChatMessage> = emptyList(),
        explicitTargetNoteIds: Set<String> = emptySet()
    ): Result<String> {
        // Selective retrieval: never pass all notes into the context window at once
        val userTokens = userQuestion.lowercase().split("\\W+".toRegex()).filter { it.length > 2 }

        val retrievedNotes = if (explicitTargetNoteIds.isNotEmpty()) {
            // User explicitly called/focused specific notes
            notes.filter { explicitTargetNoteIds.contains(it.id) }
        } else {
            // Smart selective scoring: find at most 1-2 relevant notes
            val scoredNotes = notes.map { n ->
                var score = 0
                val titleLower = n.title.lowercase()
                val contentLower = n.content.lowercase()
                val tagsLower = n.tags.joinToString(" ").lowercase()

                for (token in userTokens) {
                    if (titleLower.contains(token)) score += 20
                    if (tagsLower.contains(token)) score += 15
                    if (contentLower.contains(token)) score += 5
                }
                n to score
            }.filter { it.second > 0 }.sortedByDescending { it.second }

            scoredNotes.take(2).map { it.first }
        }

        // Lightweight title/tag summary index (no raw note bodies)
        val catalogIndex = notes.take(25).joinToString("\n") { n ->
            val tagsStr = if (n.tags.isNotEmpty()) " [Tags: ${n.tags.joinToString()}]" else ""
            "• [Note: \"${n.title.ifBlank { "Untitled" }}\"]$tagsStr"
        }

        val retrievedDetails = if (retrievedNotes.isNotEmpty()) {
            retrievedNotes.joinToString("\n\n---\n") { n ->
                "NOTE TITLE: ${n.title}\nTAGS: ${n.tags.joinToString()}\nCONTENT:\n${n.content}"
            }
        } else {
            "(No specific note was loaded. Reference the Note Index if needed.)"
        }

        val systemInstruction = """
            You are AstralNotes AI assistant. You have access to user-selected notes and a high-level note index.
            
            === SPECIFICALLY LOADED NOTE CONTENT ===
            $retrievedDetails
            
            === NOTE INDEX (Titles only) ===
            $catalogIndex
            
            CAPABILITIES & TOOLS:
            If the user asks to create a new list or note, include at the end:
            :::action:create_note{"title": "Title", "content": "Markdown content"}:::
            
            Always provide crisp, helpful answers in Markdown.
        """.trimIndent()

        return generateContent(
            prompt = userQuestion,
            systemInstruction = systemInstruction,
            history = chatHistory
        )
    }
}
