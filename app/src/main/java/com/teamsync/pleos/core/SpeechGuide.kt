package com.teamsync.pleos.core

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import com.teamsync.pleos.log.AppLog
import java.util.Locale

/**
 * 음성 안내 (화면을 볼 수 없는 순간용 — 예: 2차 사고 대피). Pleos TextToSpeech SDK 온디바이스 모드.
 *
 * 실측(2026-10-22): Android 표준 `android.speech.tts.TextToSpeech`는 엔진(ai.pleos.playground.caas)이 보이고
 * `pleos.car.permission.TTS_SERVICE`를 선언해도 초기화 status=-1로 실패 → Pleos SDK(문서상 "준비 중", Maven엔 3.3.1 공개)를 사용.
 * 에뮬레이터 실측: 모델 로드 0.4~0.9s(첫 호출), 문장 3.8s 분량 합성 0.29s, 요청→재생 시작 약 0.5s. 화자 seoyoon.
 */
class SpeechGuide(context: Context) {

    @Volatile private var ready = false
    private val pending = ArrayDeque<String>()

    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)

    init {
        AppLog.i(TAG, "initialize(ON_DEVICE) call")
        try {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    AppLog.i(TAG, "initialize onSuccess")
                    // 실측: initialize 전에 addEventListener를 부르면 SDK 내부 lateinit 미초기화로 크래시
                    tts.addEventListener(object : EventListener {
                        override fun onStart() = AppLog.i(TAG, "event onStart")
                        override fun onReady() = AppLog.i(TAG, "event onReady")
                        override fun onDone() = AppLog.i(TAG, "event onDone")
                        override fun onError(error: ErrorType) = AppLog.e(TAG, "event onError $error")
                        override fun onStop() = AppLog.i(TAG, "event onStop")
                        override fun onUpdatedRms(rms: Double) = Unit
                    })
                    // 실측: Locale.KOREAN("ko") → 엔진이 "unsupported locale: ko-"로 ON_DEVICE_ENGINE_ERROR. 국가 코드까지 필요
                    runCatching { tts.setLocale(Locale.KOREA) }
                        .onFailure { AppLog.w(TAG, "setLocale(ko_KR) failed: ${it.message}") }
                    ready = true
                    synchronized(pending) { while (pending.isNotEmpty()) speakNow(pending.removeFirst()) }
                }

                override fun onFailure(error: ErrorType) {
                    AppLog.e(TAG, "initialize onFailure $error")
                }
            })
        } catch (e: Exception) {
            AppLog.e(TAG, "initialize threw ${e.javaClass.name}: ${e.message}", e)
        }
    }

    fun speak(text: String) {
        if (!ready) {
            synchronized(pending) { pending.addLast(text) }
            AppLog.i(TAG, "queued until TTS ready: '$text'")
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        try {
            // 2번째 인자 = isFinal (스트리밍 TTS: 조각을 이어 보내고 마지막에 true). false면 엔진이 다음 조각을 기다리며 멈춤 (실측)
            tts.speak(text, true)
            AppLog.i(TAG, "speak('$text')")
        } catch (e: Exception) {
            AppLog.e(TAG, "speak threw ${e.javaClass.name}: ${e.message}", e)
        }
    }

    fun shutdown() {
        runCatching { tts.release() }
        AppLog.i(TAG, "release")
    }

    companion object {
        private const val TAG = "Speech"
    }
}
