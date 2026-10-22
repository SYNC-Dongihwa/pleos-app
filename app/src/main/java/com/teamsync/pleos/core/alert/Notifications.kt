package com.teamsync.pleos.core.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.teamsync.pleos.MainActivity
import com.teamsync.pleos.R
import com.teamsync.pleos.log.AppLog
import com.teamsync.pleos.service.MonitorService

object Notifications {
    private const val TAG = "Notify"
    const val CHANNEL_MONITOR = "monitor"
    const val CHANNEL_ALERTS = "driving_alerts"
    const val ID_MONITOR = 1
    private const val ID_ALERT = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, "차량 상태 모니터링", NotificationManager.IMPORTANCE_LOW).apply {
                description = "차량·내비 상태를 상시 수신하는 동안 표시"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "주행 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "경고등·배터리 등 운전 중 1줄 알림"
            },
        )
    }

    fun monitor(context: Context): Notification {
        val stop = PendingIntent.getService(
            context, 0,
            Intent(context, MonitorService::class.java).setAction(MonitorService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_car)
            .setContentTitle("차량 상태 모니터링 중")
            .setContentText("경고등·배터리·내비 이벤트를 수신하고 있습니다")
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .addAction(0, "중지", stop)
            .build()
    }

    /** 앱이 화면에 없을 때 쓰는 1줄 heads-up. 같은 ID로 덮어써서 항상 최신 1건만 남긴다. */
    fun alert(context: Context, alert: DrivingAlert) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            AppLog.w(TAG, "notifications disabled (POST_NOTIFICATIONS 거부?) — '${alert.text}' not shown")
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_car)
            .setContentTitle(alert.text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setTimeoutAfter(alert.severity.displayMs)
            .setContentIntent(openApp(context))
            .build()
        try {
            manager.notify(ID_ALERT, notification)
            AppLog.i(TAG, "heads-up posted '${alert.text}'")
        } catch (e: SecurityException) {
            AppLog.e(TAG, "notify failed: ${e.message}", e)
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
