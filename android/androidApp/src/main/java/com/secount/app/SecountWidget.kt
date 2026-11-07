package com.secount.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.secount.app.logic.nextVisibleCountdown
import com.secount.app.logic.parseEventArray
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Home-screen widget: shows the next visible countdown. Updates from the
 * current events.json snapshot after a change, when the app resumes, or when Android refreshes it.
 */
class SecountWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateOne(context, mgr, id)
    }

    companion object {
        fun requestRefresh(context: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, SecountWidget::class.java))
                // Let the framework route through onUpdate.
                val intent = Intent(context, SecountWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            } catch (ignored: Exception) {
            }
        }

        private fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
            try {
                val (title, sub) = readNextFromFile(context)
                val views = RemoteViews(context.packageName, R.layout.secount_widget)
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_sub, sub)
                try {
                    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (launch != null) {
                        val pi = PendingIntent.getActivity(
                            context, 0, launch,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_root, pi)
                    }
                } catch (ignored: Exception) {
                }
                mgr.updateAppWidget(id, views)
            } catch (ignored: Exception) {
            }
        }

        private fun readNextFromFile(context: Context): Pair<String, String> {
            return try {
                val f = java.io.File(context.filesDir, "events.json")
                if (!f.exists()) return "Secount" to "No countdowns yet"
                val events = parseEventArray(f.readText())
                    ?: return "Secount" to "Open the app"
                val today = LocalDate.now()
                val myId = context.getSharedPreferences("secount", Context.MODE_PRIVATE)
                    .getString("acct_id", "").orEmpty()
                val best = nextVisibleCountdown(events, myId, today)
                    ?: return "Secount" to "No countdowns yet"
                val days = ChronoUnit.DAYS.between(today, best.nextOccurrence(today))
                best.title to when (days) {
                    0L -> "Today! ♥"
                    1L -> "Tomorrow"
                    else -> "$days days left"
                }
            } catch (e: Exception) {
                "Secount" to "Open the app"
            }
        }
    }
}
