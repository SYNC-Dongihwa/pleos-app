package com.teamsync.pleos.tutorial

import com.teamsync.pleos.vehicle.SignalChange
import com.teamsync.pleos.vehicle.SignalState
import com.teamsync.pleos.vehicle.Support
import com.teamsync.pleos.vehicle.VehicleConnection
import com.teamsync.pleos.vehicle.VehicleSignal
import com.teamsync.pleos.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 엔진 판정 규칙 검증. 단계는 테스트 전용 데이터(문자열 값)로 만든다 — 앱 코드 경로에는 없음. */
class TutorialEngineTest {

    private val light = toggleStep(
        id = "light", title = "전조등", signal = VehicleSignal.LOW_BEAM,
        isOn = { it == "ON" }, turnOn = "켜 보세요", turnOff = "꺼 보세요",
        restoreOn = "다시 켜 주세요", restoreOff = "다시 꺼 주세요", hint = "레버 힌트",
        valueLabel = { it?.toString() ?: "?" },
    )
    private val temp = TutorialStep(
        id = "temp", title = "온도", signal = VehicleSignal.DRIVER_TEMPERATURE,
        phases = listOf(
            Phase({ "1도 내려 보세요" }, "온도 힌트", Goals.decreasedBy(1f)),
            Phase({ "되돌려 주세요" }, "온도 힌트2", Goals.returnedToOriginal()),
        ),
        valueLabel = { it.toString() },
    )
    private val mode = TutorialStep(
        id = "mode", title = "주행 모드", signal = VehicleSignal.DRIVING_MODE,
        phases = listOf(Phase({ "바꿔 보세요" }, "모드 힌트", Goals.changedFromOriginal())),
        valueLabel = { it.toString() },
    )

    private lateinit var engine: TutorialEngine
    private var now = 1_000L
    private var vehicle = VehicleState(connection = VehicleConnection.Connected)

    @Before fun setUp() {
        engine = TutorialEngine(listOf(light, temp, mode))
        set(VehicleSignal.LOW_BEAM, "OFF")
        set(VehicleSignal.DRIVER_TEMPERATURE, 22.0f)
        vehicle = vehicle.copy(signals = vehicle.signals + (VehicleSignal.DRIVING_MODE to SignalState(support = Support.Unsupported("ERROR_NOT_SUPPORTED_PROPERTY"))))
        engine.onSnapshot(vehicle, now)
        engine.onGate(parked = true, reason = null, now)
    }

    private fun set(s: VehicleSignal, value: Any?) {
        vehicle = vehicle.copy(signals = vehicle.signals + (s to SignalState(support = Support.Supported(""), value = value, receiveCount = 1)))
    }

    private fun change(s: VehicleSignal, from: Any?, to: Any?, source: String = "live"): List<Effect> {
        now += 1_000
        set(s, to)
        engine.onSnapshot(vehicle, now)
        return engine.onChange(SignalChange(s, from, to, source, now), now)
    }

    private fun spoken(effects: List<Effect>) = effects.filterIsInstance<Effect.Speak>().map { it.text }

    @Test fun `처음 값이 꺼짐이면 켜기 안내로 시작`() {
        val fx = engine.start(fromBeginning = true, now)
        assertEquals(Mode.Running, engine.state.mode)
        assertEquals("켜 보세요", engine.state.instruction)
        assertTrue(spoken(fx).single().endsWith("켜 보세요"))
    }

    @Test fun `켜기와 되돌리기 전이를 모두 관찰해야 완료`() {
        engine.start(true, now)
        change(VehicleSignal.LOW_BEAM, "OFF", "ON")
        assertEquals(1, engine.state.phaseIndex)
        assertEquals("다시 꺼 주세요", engine.state.instruction)
        assertNull(engine.state.results["light"])
        change(VehicleSignal.LOW_BEAM, "ON", "OFF")
        assertEquals(StepResult(Outcome.COMPLETED, "OFF → ON, ON → OFF"), engine.state.results["light"])
        assertEquals("temp", engine.steps[engine.state.stepIndex].id)
    }

    @Test fun `get으로 온 변화는 완료 판정에 쓰지 않는다`() {
        engine.start(true, now)
        change(VehicleSignal.LOW_BEAM, "OFF", "ON", source = "get")
        assertEquals(0, engine.state.phaseIndex)
    }

    @Test fun `온도는 1도 이상 내려가야 다음 단계`() {
        engine.start(true, now)
        change(VehicleSignal.LOW_BEAM, "OFF", "ON")
        change(VehicleSignal.LOW_BEAM, "ON", "OFF")
        change(VehicleSignal.DRIVER_TEMPERATURE, 22.0f, 21.5f)
        assertEquals(0, engine.state.phaseIndex)
        change(VehicleSignal.DRIVER_TEMPERATURE, 21.5f, 21.0f)
        assertEquals(1, engine.state.phaseIndex)
        change(VehicleSignal.DRIVER_TEMPERATURE, 21.0f, 22.0f)
        assertEquals(Outcome.COMPLETED, engine.state.results["temp"]?.outcome)
    }

    @Test fun `미지원 단계는 사유와 함께 자동으로 넘어가고 끝나면 완료 화면`() {
        engine.start(true, now)
        engine.skip(now)
        engine.skip(now)
        assertEquals(Outcome.UNSUPPORTED, engine.state.results["mode"]?.outcome)
        assertEquals("ERROR_NOT_SUPPORTED_PROPERTY", engine.state.results["mode"]?.detail)
        assertEquals(Mode.Finished, engine.state.mode)
        assertFalse(engine.isMaster)
    }

    @Test fun `20초 변화가 없으면 힌트를 한 번만`() {
        engine.start(true, now)
        assertTrue(spoken(engine.tick(now + 19_000)).isEmpty())
        assertEquals(listOf("레버 힌트"), spoken(engine.tick(now + 20_000)))
        assertTrue(spoken(engine.tick(now + 60_000)).isEmpty())
        assertEquals("레버 힌트", engine.state.hint)
    }

    @Test fun `목표에 못 미친 변화도 힌트 타이머를 다시 시작`() {
        engine.start(true, now)
        change(VehicleSignal.LOW_BEAM, "OFF", "ON")
        change(VehicleSignal.LOW_BEAM, "ON", "OFF") // 온도 단계 시작 (now 기준)
        val begun = now
        now = begun + 15_000
        engine.onChange(SignalChange(VehicleSignal.DRIVER_TEMPERATURE, 22.0f, 21.5f, "live", now), now) // 0.5도: 미충족
        assertEquals(0, engine.state.phaseIndex)
        assertTrue(spoken(engine.tick(begun + 25_000)).isEmpty()) // 시작 기준 25초지만 변화 후 10초
        assertEquals(listOf("온도 힌트"), spoken(engine.tick(now + 20_000)))
    }

    @Test fun `주행 전환 시 일시정지, 그동안 변화는 판정 안 함, 재개 시 안내 반복`() {
        engine.start(true, now)
        val paused = engine.onGate(parked = false, reason = "기어 D", now)
        assertEquals(Mode.Paused("기어 D"), engine.state.mode)
        assertTrue(spoken(paused).single().contains("잠시 멈출게요"))
        change(VehicleSignal.LOW_BEAM, "OFF", "ON")
        assertEquals(0, engine.state.phaseIndex)
        val resumed = engine.onGate(parked = true, reason = null, now)
        assertEquals(Mode.Running, engine.state.mode)
        assertTrue(spoken(resumed).single().contains("켜 보세요"))
        // 일시정지 중 이미 켜진 상태에서 재개 → 켜짐 기준으로 이어서 (같은 값 재수신은 무시)
        change(VehicleSignal.LOW_BEAM, "ON", "ON")
        assertEquals(0, engine.state.phaseIndex)
    }

    @Test fun `멈춘 동안 바뀐 값은 재개 시점의 새 기준이 된다`() {
        engine.start(true, now)
        change(VehicleSignal.LOW_BEAM, "OFF", "ON")
        change(VehicleSignal.LOW_BEAM, "ON", "OFF") // 온도 단계
        engine.onGate(parked = false, reason = "기어 D", now)
        change(VehicleSignal.DRIVER_TEMPERATURE, 22.0f, 21.0f) // 멈춘 동안 1도 내려감 → 판정 안 함
        engine.onGate(parked = true, reason = null, now)
        change(VehicleSignal.DRIVER_TEMPERATURE, 21.0f, 20.5f) // 재개 기준 21.0에서 0.5 → 미충족
        assertEquals(0, engine.state.phaseIndex)
        change(VehicleSignal.DRIVER_TEMPERATURE, 20.5f, 20.0f)
        assertEquals(1, engine.state.phaseIndex)
    }

    @Test fun `주제 조사 은는`() {
        assertEquals("전조등은", topic("전조등"))
        assertEquals("주행 모드는", topic("주행 모드"))
        assertEquals("앞 와이퍼는", topic("앞 와이퍼"))
    }

    @Test fun `건너뛰기는 건너뜀으로 기록`() {
        engine.start(true, now)
        engine.skip(now)
        assertEquals(Outcome.SKIPPED, engine.state.results["light"]?.outcome)
        assertEquals("temp", engine.steps[engine.state.stepIndex].id)
    }

    @Test fun `정차가 아니면 시작하지 않는다`() {
        engine.onGate(parked = false, reason = "기어 D", now)
        engine.start(true, now)
        assertEquals(Mode.Idle, engine.state.mode)
    }

    @Test fun `완료 화면에서 한 단계만 다시 하기`() {
        engine.start(true, now)
        engine.skip(now)
        engine.skip(now) // temp 건너뜀 → mode 미지원 → Finished
        assertEquals(Mode.Finished, engine.state.mode)
        engine.retry("light", now)
        assertTrue(engine.state.singleStep)
        change(VehicleSignal.LOW_BEAM, "OFF", "ON")
        change(VehicleSignal.LOW_BEAM, "ON", "OFF")
        assertEquals(Outcome.COMPLETED, engine.state.results["light"]?.outcome)
        assertEquals(Mode.Finished, engine.state.mode)
    }

    @Test fun `이어서 하기는 결과가 없는 단계부터`() {
        engine.restore(mapOf("light" to StepResult(Outcome.COMPLETED, "OFF → ON, ON → OFF")))
        engine.start(fromBeginning = false, now)
        assertEquals("temp", engine.steps[engine.state.stepIndex].id)
    }

    @Test fun `모두 완료하거나 미지원이면 마스터`() {
        engine.start(true, now)
        change(VehicleSignal.LOW_BEAM, "OFF", "ON")
        change(VehicleSignal.LOW_BEAM, "ON", "OFF")
        change(VehicleSignal.DRIVER_TEMPERATURE, 22.0f, 21.0f)
        change(VehicleSignal.DRIVER_TEMPERATURE, 21.0f, 22.0f)
        assertEquals(Mode.Finished, engine.state.mode)
        assertTrue(engine.isMaster)
    }
}
