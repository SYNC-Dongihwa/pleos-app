package com.teamsync.pleos.core

import ai.pleos.playground.llm.LLM
import ai.pleos.playground.llm.constant.Mode
import ai.pleos.playground.llm.listener.ResultListener
import android.content.Context
import com.teamsync.pleos.log.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pleos LLM SDK 온디바이스 모드 (문서상 미공개, Maven에 2.1.3.2 공개 — 2026-10-22 확인).
 * 플랫폼의 `ai.pleos.playground.caas` LLMService가 Gleo AI 모델 제공자로부터 모델 파일을 받아 실행하는 구조로 보임.
 */
class OnDeviceLlm(context: Context) {

    private val llm = LLM(context.applicationContext, Mode.ON_DEVICE)
    @Volatile private var initialized = false

    fun initialize() {
        if (initialized) return
        AppLog.i(TAG, "initialize(ON_DEVICE) call")
        try {
            llm.initialize()
            initialized = true
            AppLog.i(TAG, "initialize returned")
        } catch (e: Exception) {
            AppLog.e(TAG, "initialize threw ${e.javaClass.name}: ${e.message}", e)
        }
    }

    /** 전체 응답을 모아서 돌려준다. 스트리밍 조각은 onResponse(text, isDone=false)로 올 수 있음. */
    suspend fun generate(prompt: String, timeoutMs: Long = TIMEOUT_MS): Result<String> {
        initialize()
        val result = CompletableDeferred<Result<String>>()
        val buffer = StringBuilder()
        val started = System.currentTimeMillis()
        AppLog.i(TAG, "generateContent prompt(${prompt.length}자)")
        try {
            llm.generateContent(prompt, object : ResultListener {
                override fun onResponse(text: String, isDone: Boolean) {
                    buffer.append(text)
                    AppLog.d(TAG, "onResponse chunk=${text.length}자 done=$isDone")
                    if (isDone) result.complete(Result.success(buffer.toString()))
                }

                override fun onError(message: String) {
                    AppLog.e(TAG, "onError $message")
                    result.complete(Result.failure(IllegalStateException(message)))
                }
            })
        } catch (e: Exception) {
            AppLog.e(TAG, "generateContent threw ${e.javaClass.name}: ${e.message}", e)
            return Result.failure(e)
        }
        val outcome = withTimeoutOrNull(timeoutMs) { result.await() }
            ?: Result.failure(IllegalStateException("응답 없음 (${timeoutMs}ms), 받은 조각 ${buffer.length}자"))
        val elapsed = System.currentTimeMillis() - started
        outcome.onSuccess { AppLog.i(TAG, "generate done ${elapsed}ms → ${it.take(200)}") }
            .onFailure { AppLog.e(TAG, "generate failed ${elapsed}ms: ${it.message}") }
        return outcome
    }

    fun release() {
        runCatching { llm.release() }
        initialized = false
        AppLog.i(TAG, "release")
    }

    companion object {
        private const val TAG = "LLM"
        private const val TIMEOUT_MS = 90_000L
    }
}
