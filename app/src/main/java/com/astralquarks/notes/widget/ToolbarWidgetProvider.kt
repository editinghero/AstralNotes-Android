package com.astralquarks.notes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.astralquarks.notes.MainActivity
import com.astralquarks.notes.R

class ToolbarWidgetProvider : AppWidgetProvider() {

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
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_notes_toolbar)

            // Pending intent for New Note
            val newNoteIntent = Intent(context, MainActivity::class.java).apply {
                action = QuickNoteWidgetProvider.ACTION_NEW_NOTE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val newNotePending = PendingIntent.getActivity(
                context, 201, newNoteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_new_note, newNotePending)

            // Pending intent for Checklist
            val checklistIntent = Intent(context, MainActivity::class.java).apply {
                action = QuickNoteWidgetProvider.ACTION_NEW_CHECKLIST
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val checklistPending = PendingIntent.getActivity(
                context, 202, checklistIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_new_checklist, checklistPending)

            // Pending intent for Image Note
            val imageIntent = Intent(context, MainActivity::class.java).apply {
                action = QuickNoteWidgetProvider.ACTION_NEW_IMAGE_NOTE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val imagePending = PendingIntent.getActivity(
                context, 203, imageIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_new_image, imagePending)

            // Pending intent for AI
            val aiIntent = Intent(context, MainActivity::class.java).apply {
                action = QuickNoteWidgetProvider.ACTION_OPEN_AI
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val aiPending = PendingIntent.getActivity(
                context, 204, aiIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_ai, aiPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
