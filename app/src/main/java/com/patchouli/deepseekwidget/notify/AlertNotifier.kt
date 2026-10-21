package com.patchouli.deepseekwidget.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.patchouli.deepseekwidget.MainActivity
import com.patchouli.deepseekwidget.R
import com.patchouli.deepseekwidget.data.Prefs
import com.patchouli.deepseekwidget.util.Format
import java.math.BigDecimal

object AlertNotifier {

    private const val CHANNEL_ID = "balance_alerts"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Una sola notifica per attraversamento della soglia: quando il saldo
     * scende sotto la soglia si notifica, lo stato resta "sotto" finche' il
     * saldo non risale (o la soglia cambia e il saldo e' di nuovo sopra).
     */
    fun evaluate(context: Context, total: BigDecimal) {
        val prefs = Prefs(context)
        val threshold = prefs.getThreshold()
        if (total < threshold) {
            if (prefs.isBelowThreshold()) return
            if (!canNotify(context)) return
            ensureChannel(context)
            val posted = try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, build(context, total, threshold))
                true
            } catch (_: SecurityException) {
                false
            }
            if (posted) prefs.setBelowThreshold(true)
        } else {
            prefs.setBelowThreshold(false)
        }
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun build(context: Context, total: BigDecimal, threshold: BigDecimal) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(
                context.getString(
                    R.string.notification_text,
                    Format.money(total),
                    Format.money(threshold)
                )
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(
                        R.string.notification_text,
                        Format.money(total),
                        Format.money(threshold)
                    )
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
