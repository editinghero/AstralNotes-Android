package com.astralquarks.notes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.astralquarks.notes.MainActivity
import com.astralquarks.notes.R
import com.astralquarks.notes.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QuickNoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val ACTION_NEW_NOTE = "com.astralquarks.notes.action.NEW_NOTE"
        const val ACTION_NEW_CHECKLIST = "com.astralquarks.notes.action.NEW_CHECKLIST"
        const val ACTION_NEW_IMAGE_NOTE = "com.astralquarks.notes.action.NEW_IMAGE_NOTE"
        const val ACTION_OPEN_AI = "com.astralquarks.notes.action.OPEN_AI"
        const val EXTRA_NOTE_ID = "com.astralquarks.notes.extra.NOTE_ID"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, QuickNoteWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, QuickNoteWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_notes_expressive)

            // Pending intent for New Note
            val newNoteIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_NEW_NOTE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val newNotePending = PendingIntent.getActivity(
                context, 101, newNoteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_new_note, newNotePending)

            // Pending intent for Checklist
            val checklistIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_NEW_CHECKLIST
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val checklistPending = PendingIntent.getActivity(
                context, 102, checklistIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_new_checklist, checklistPending)

            // Pending intent for Image Note
            val imageIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_NEW_IMAGE_NOTE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val imagePending = PendingIntent.getActivity(
                context, 103, imageIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_new_image, imagePending)

            // Pending intent for AI
            val aiIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_AI
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val aiPending = PendingIntent.getActivity(
                context, 104, aiIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_ai, aiPending)

            // Asynchronously fetch the most recent note
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val noteDao = db.noteDao()
                    val allNotes = noteDao.getAllActiveNotesSync()
                    val latest = allNotes.firstOrNull()

                    if (latest != null) {
                        views.setTextViewText(R.id.widget_recent_title, latest.title.ifBlank { "Untitled Note" })
                        val snippet = latest.content
                            .replace(Regex("[#*`~>-]"), "")
                            .lines()
                            .firstOrNull { it.isNotBlank() } ?: "Empty note content"
                        views.setTextViewText(R.id.widget_recent_snippet, snippet)

                        val openNoteIntent = Intent(context, MainActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            putExtra(EXTRA_NOTE_ID, latest.id)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val openPending = PendingIntent.getActivity(
                            context, 105, openNoteIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_recent_note_container, openPending)
                    } else {
                        views.setTextViewText(R.id.widget_recent_title, "No notes yet")
                        views.setTextViewText(R.id.widget_recent_snippet, "Tap + Note to write your first note!")
                        views.setOnClickPendingIntent(R.id.widget_recent_note_container, newNotePending)
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
