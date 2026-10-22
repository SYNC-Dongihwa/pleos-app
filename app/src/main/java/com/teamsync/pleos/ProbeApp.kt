package com.teamsync.pleos

import android.app.Application
import com.teamsync.pleos.log.AppLog

class ProbeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.i("App", "onCreate version=${BuildConfig.VERSION_NAME} sdk=${android.os.Build.VERSION.SDK_INT} device=${android.os.Build.DEVICE}")
    }
}
