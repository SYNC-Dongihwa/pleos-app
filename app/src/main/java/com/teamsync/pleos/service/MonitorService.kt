package com.teamsync.pleos.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.teamsync.pleos.core.CarSession
import com.teamsync.pleos.core.alert.AlertNotifier
import com.teamsync.pleos.core.alert.AlertRules
import com.teamsync.pleos.core.alert.Notifications
import com.teamsync.pleos.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 상시 수신용 Foreground Service (type=specialUse).
 * 앱이 백그라운드로 가면 ~10초 뒤 cached-app freezer가 프로세스를 얼려 SDK 콜백이 끊긴다 (2026-10-22 실측).
 * FGS가 떠 있으면 프로세스가 전면 서비스 상태로 유지돼 Navigation 앱이 전면이어도 계속 받는다.
 *
 * Android 12+ 정책상 백그라운드에서 FGS를 새로 시작할 수 없으므로 [start]는 화면(Activity)에서 호출한다.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppLog.i(TAG, "onCreate")
        if (!enterForeground()) return
        CarSession.start()
        AlertRules(scope).start()
        AlertNotifier(this, scope).start()
        _running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AppLog.i(TAG, "stop requested")
            stopSelf()
            return START_NOT_STICKY
        }
        AppLog.i(TAG, "onStartCommand startId=$startId restart=${intent == null}")
        return START_STICKY
    }

    override fun onDestroy() {
        AppLog.i(TAG, "onDestroy")
        scope.cancel()
        CarSession.stop()
        _running.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun enterForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.ID_MONITOR, Notifications.monitor(this), type)
        AppLog.i(TAG, "startForeground ok (type=specialUse)")
        true
    } catch (e: Exception) {
        // ForegroundServiceStartNotAllowedException 등 → 상시 수신 불가를 그대로 기록하고 종료
        AppLog.e(TAG, "startForeground failed: ${e.javaClass.name}: ${e.message}", e)
        stopSelf()
        false
    }

    companion object {
        private const val TAG = "Service"
        const val ACTION_STOP = "com.teamsync.pleos.action.STOP_MONITOR"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            AppLog.i(TAG, "start requested")
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MonitorService::class.java).setAction(ACTION_STOP))
        }
    }
}
