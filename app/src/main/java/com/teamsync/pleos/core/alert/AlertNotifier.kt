package com.teamsync.pleos.core.alert

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.teamsync.pleos.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 앱이 화면에 보이면 OneLineAlertBar가 보여주므로 생략, 안 보이면(내비가 전면 등) heads-up 알림으로. */
class AlertNotifier(private val context: Context, private val scope: CoroutineScope) {

    fun start() {
        scope.launch(Dispatchers.Main) {
            AlertCenter.posted.collect { alert ->
                val visible = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (visible) {
                    AppLog.d(TAG, "app visible → in-app bar only '${alert.text}'")
                } else {
                    Notifications.alert(context, alert)
                }
            }
        }
    }

    companion object {
        private const val TAG = "Notify"
    }
}
