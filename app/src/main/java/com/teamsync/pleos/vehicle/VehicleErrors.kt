package com.teamsync.pleos.vehicle

import ai.pleos.playground.vehicle.constant.ErrorCode

/**
 * Vehicle SDK 2.3.2에는 문서에 나오는 public `VehicleException`이 없다.
 * 실제 예외 클래스는 난독화된 `ai.pleos.playground.vehicle.callback.exception.a`이고,
 * 인자 없는 메서드 `a(): ErrorCode`로 에러 코드를 준다 (AAR javap로 확인, CLAUDE.md §9).
 * 이름이 바뀌어도 동작하도록 "ErrorCode를 반환하는 무인자 메서드"를 찾아 호출한다.
 */
object VehicleErrors {
    fun errorCodeOf(e: Throwable): ErrorCode? {
        val getter = e.javaClass.methods.firstOrNull {
            it.parameterCount == 0 && it.returnType == ErrorCode::class.java
        } ?: return null
        return runCatching { getter.invoke(e) as? ErrorCode }.getOrNull()
    }

    fun describe(e: Throwable): String =
        "errorCode=${errorCodeOf(e)?.name ?: "-"} type=${e.javaClass.name} message=${e.message}"
}
