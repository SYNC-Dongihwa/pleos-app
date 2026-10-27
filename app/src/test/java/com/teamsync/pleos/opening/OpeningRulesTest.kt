package com.teamsync.pleos.opening

import com.teamsync.pleos.tutorial.Mode
import com.teamsync.pleos.tutorial.Outcome
import com.teamsync.pleos.tutorial.StepResult
import com.teamsync.pleos.tutorial.TutorialState
import com.teamsync.pleos.vehicle.SignalChange
import com.teamsync.pleos.vehicle.VehicleSignal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningRulesTest {
    private val now = 1_000_000L
    private fun door(from: Int?, to: Int, source: String = "live") = SignalChange(VehicleSignal.DRIVER_DOOR, from, to, source, now)
    private val idle = TutorialState()

    @Test fun `홈에서 처음 운전석 문이 열리면 재생`() {
        assertTrue(OpeningRules.shouldPlayOnDoor(door(0, 1), idle, visible = false, lastPlayedAt = 0))
    }

    @Test fun `기준값(get)이나 닫힘은 무시`() {
        assertFalse(OpeningRules.shouldPlayOnDoor(door(null, 1, "get"), idle, false, 0))
        assertFalse(OpeningRules.shouldPlayOnDoor(door(1, 0), idle, false, 0))
    }

    @Test fun `튜토리얼 진행 중이거나 이미 시작한 적 있으면 무시`() {
        assertFalse(OpeningRules.shouldPlayOnDoor(door(0, 1), idle.copy(mode = Mode.Running), false, 0))
        val started = idle.copy(results = mapOf("low_beam" to StepResult(Outcome.COMPLETED, "")))
        assertFalse(OpeningRules.shouldPlayOnDoor(door(0, 1), started, false, 0))
    }

    @Test fun `1분 안에 다시 열면 반복하지 않음`() {
        assertFalse(OpeningRules.shouldPlayOnDoor(door(0, 1), idle, false, lastPlayedAt = now - 30_000))
        assertTrue(OpeningRules.shouldPlayOnDoor(door(0, 1), idle, false, lastPlayedAt = now - 61_000))
    }
}
