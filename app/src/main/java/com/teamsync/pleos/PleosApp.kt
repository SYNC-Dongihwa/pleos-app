package com.teamsync.pleos

import android.app.Application
import com.teamsync.pleos.log.AppLog

class PleosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.i("App", "onCreate version=${BuildConfig.VERSION_NAME} sdk=${android.os.Build.VERSION.SDK_INT} device=${android.os.Build.DEVICE}")
        // Vehicle SDK 문서: initialize()는 애플리케이션 생성 시 호출
        AppGraph.init(this)
    }
}
