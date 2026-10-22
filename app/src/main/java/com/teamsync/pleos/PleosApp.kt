package com.teamsync.pleos

import android.app.Application
import com.teamsync.pleos.core.CarSession
import com.teamsync.pleos.core.alert.Notifications
import com.teamsync.pleos.log.AppLog

class PleosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.i("App", "onCreate version=${BuildConfig.VERSION_NAME} sdk=${android.os.Build.VERSION.SDK_INT} device=${android.os.Build.DEVICE}")
        Notifications.createChannels(this)
        CarSession.init(this)
    }
}
