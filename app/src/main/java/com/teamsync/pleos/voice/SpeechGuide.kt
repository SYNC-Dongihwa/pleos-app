package com.teamsync.pleos.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import com.teamsync.pleos.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * 튜토리얼 음성 안내. Pleos TextToSpeech SDK 온디바이스 모드 (앱 등록·네트워크 불필요).
 *
 * 실측 규칙 (CLAUDE.md §7-10): addEventListener는 initialize 성공 후 / 로캘은 Locale.KOREA / speak(text, isFinal=true).
 * Android 표준 TextToSpeech는 에뮬레이터에서 초기화 status=-1로 실패해 쓰지 않는다.
 * 초기화에 실패하면 [status]에 이유를 남기고 화면 안내만 한다 (대체 음성 없음).
 */
class SpeechGuide(context: Context) {

    sealed interface Status {
        data object Initializing : Status
        data object Ready : Status
        data class Failed(val reason: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Initializing)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** 초기화 전에 들어온 안내는 마지막 것만 기억 (새 안내가 이전 안내를 대체). */
    @Volatile private var pending: String? = null

    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)

    init {
        AppLog.i(TAG, "initialize(ON_DEVICE) call")
        try {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    AppLog.i(TAG, "initialize onSuccess")
                    tts.addEventListener(object : EventListener {
                        override fun onStart() = AppLog.d(TAG, "event onStart")
                        override fun onReady() = AppLog.d(TAG, "event onReady")
                        override fun onDone() = AppLog.d(TAG, "event onDone")
                        override fun onError(error: ErrorType) = AppLog.e(TAG, "event onError $error")
                        override fun onStop() = AppLog.d(TAG, "event onStop")
                        override fun onUpdatedRms(rms: Double) = Unit
                    })
                    runCatching { tts.setLocale(Locale.KOREA) }
                        .onFailure { AppLog.w(TAG, "setLocale(ko_KR) failed: ${it.message}") }
                    _status.value = Status.Ready
                    pending?.let { pending = null; speakNow(it, interrupt = false) }
                }

                override fun onFailure(error: ErrorType) {
                    AppLog.e(TAG, "initialize onFailure $error")
                    _status.value = Status.Failed("초기화 실패: $error")
                }
            })
        } catch (e: Exception) {
            AppLog.e(TAG, "initialize threw ${e.javaClass.name}: ${e.message}", e)
            _status.value = Status.Failed("초기화 예외: ${e.message}")
        }
    }

    /** [interrupt]=true면 말하던 안내를 멈추고 새 안내를 말한다 (튜토리얼은 항상 최신 안내가 중요). */
    fun speak(text: String, interrupt: Boolean = true) {
        when (_status.value) {
            Status.Ready -> speakNow(text, interrupt)
            Status.Initializing -> {
                pending = text
                AppLog.i(TAG, "queued until ready: '$text'")
            }
            is Status.Failed -> AppLog.w(TAG, "not speaking (TTS unavailable): '$text'")
        }
    }

    private fun speakNow(text: String, interrupt: Boolean) {
        try {
            if (interrupt) tts.stop()
            tts.speak(text, true)
            AppLog.i(TAG, "speak('$text')")
        } catch (e: Exception) {
            AppLog.e(TAG, "speak threw ${e.javaClass.name}: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "Speech"
    }
}
