package com.teamsync.pleos.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [VehicleStateRepository.timedOut]: capability 시간 초과는 아직 '확인 중'인 신호만 미지원으로 바꾼다. */
class CapabilityTimeoutTest {

    private val reason = "capability 응답 없음 (5초)"
    private val s = VehicleSignal.RECIRCULATION

    private fun stateWith(support: Support) =
        VehicleState().let { it.copy(signals = it.signals + (s to it[s].copy(support = support))) }

    @Test fun `응답이 끝내 없으면 미지원으로 바꾼다`() {
        val next = VehicleStateRepository.timedOut(VehicleState(), s, reason)
        assertEquals(Support.Unsupported(reason), next!![s].support)
    }

    @Test fun `응답이 먼저 와서 지원됨이면 그대로 둔다 (10-27 내기 순환 경합)`() {
        assertNull(VehicleStateRepository.timedOut(stateWith(Support.Supported("accessType=READ_WRITE")), s, reason))
    }

    @Test fun `다른 사유로 이미 미지원이면 원래 사유를 유지한다`() {
        assertNull(VehicleStateRepository.timedOut(stateWith(Support.Unsupported("읽기 불가")), s, reason))
    }

    @Test fun `대상 신호만 바뀌고 다른 신호는 그대로`() {
        val next = VehicleStateRepository.timedOut(VehicleState(), s, reason)!!
        VehicleSignal.entries.filter { it != s }.forEach { assertEquals(it.name, Support.Checking, next[it].support) }
    }

    @Test fun `update 안에서 쓰면 그 사이 바뀐 지원됨을 덮어쓰지 않는다`() {
        val flow = MutableStateFlow(VehicleState())
        // 시간 초과가 확인하기 직전에 binder 스레드의 응답이 먼저 반영된 순서
        flow.update { stateWith(Support.Supported("accessType=READ_WRITE")) }
        flow.update { st -> VehicleStateRepository.timedOut(st, s, reason) ?: st }
        assertEquals(Support.Supported("accessType=READ_WRITE"), flow.value[s].support)
    }
}
