package com.teamsync.pleos.core.alert

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.teamsync.pleos.core.CarSession
import com.teamsync.pleos.core.SpeechGuide
import com.teamsync.pleos.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 앱이 화면에 보이면 OneLineAlertBar가 보여주므로 생략, 안 보이면(내비가 전면 등) heads-up 알림으로.
 * WARN 이상은 화면을 볼 수 없는 경우(앱이 뒤에 있음, 또는 주행 중) 음성으로도 읽는다 —
 * 에뮬레이터에선 서드파티 앱의 주행 중 화면·heads-up이 모두 막혀 음성이 유일한 경로 (2026-10-22 실측).
 */
class AlertNotifier(private val context: Context, private val scope: CoroutineScope) {

    private var speech: SpeechGuide? = null

    fun start() {
        scope.launch(Dispatchers.Main) {
            AlertCenter.posted.collect { alert ->
                val visible = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                val driving = CarSession.ux.state.value.restricted
                if (visible) {
                    AppLog.d(TAG, "app visible → in-app bar only '${alert.text}'")
                } else {
                    Notifications.alert(context, alert)
                }
                if (alert.severity >= AlertSeverity.WARN && (!visible || driving)) {
                    AppLog.i(TAG, "speak (visible=$visible driving=$driving) '${alert.text}'")
                    val guide = speech ?: SpeechGuide(context).also { speech = it }
                    guide.speak(alert.text)
                }
            }
        }
    }

    fun stop() {
        speech?.shutdown()
        speech = null
    }

    companion object {
        private const val TAG = "Notify"
    }
}
