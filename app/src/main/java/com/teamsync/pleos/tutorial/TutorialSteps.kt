package com.teamsync.pleos.tutorial

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperState
import com.teamsync.pleos.vehicle.VehicleSignal

/**
 * 튜토리얼 단계 목록 (순서 = 진행 순서). 비상등·사이드미러 접기는 SDK에 API가 없어 제외 (CLAUDE.md §3).
 * 위치 힌트는 일반적인 배치 기준이라 "차종마다 다를 수 있어요"를 붙인다.
 */
object TutorialSteps {

    val all: List<TutorialStep> = listOf(
        toggleStep(
            id = "low_beam", title = "전조등", signal = VehicleSignal.LOW_BEAM,
            isOn = { it == LightState.ON || it == LightState.DAYTIME_RUNNING },
            turnOn = "전조등을 켜 보세요.", turnOff = "전조등을 꺼 보세요.",
            restoreOn = "좋아요. 이제 전조등을 다시 켜서 원래대로 해 주세요.", restoreOff = "좋아요. 이제 전조등을 꺼서 원래대로 해 주세요.",
            hint = "운전대 왼쪽 레버 끝의 다이얼을 돌리면 전조등이 켜지고 꺼져요. 차종마다 다를 수 있어요.",
            valueLabel = ::lightLabel,
        ),
        toggleStep(
            id = "high_beam", title = "상향등", signal = VehicleSignal.HIGH_BEAM,
            isOn = { it == LightState.ON },
            turnOn = "상향등을 켜 보세요.", turnOff = "상향등을 꺼 보세요.",
            restoreOn = "좋아요. 상향등을 다시 켜 주세요.", restoreOff = "좋아요. 이제 상향등을 꺼 주세요.",
            hint = "운전대 왼쪽 레버를 앞쪽으로 밀면 상향등이 켜지고, 다시 당기면 꺼져요. 차종마다 다를 수 있어요.",
            valueLabel = ::lightLabel,
        ),
        TutorialStep(
            id = "turn_signal", title = "방향지시등", signal = VehicleSignal.TURN_SIGNAL,
            phases = listOf(
                Phase({ "왼쪽 방향지시등을 켜 보세요." }, "운전대 왼쪽 레버를 아래로 내리면 왼쪽 방향지시등이 켜져요.", Goals.becomes(TurnSignalValue.LEFT)),
                Phase({ "좋아요. 이번에는 오른쪽 방향지시등을 켜 보세요." }, "같은 레버를 위로 올리면 오른쪽이 켜져요.", Goals.becomes(TurnSignalValue.RIGHT)),
                Phase({ "잘했어요. 이제 방향지시등을 꺼 주세요." }, "레버를 가운데로 돌려 놓으면 꺼져요.", Goals.returnedToOriginal()),
            ),
            valueLabel = ::turnSignalLabel,
        ),
        toggleStep(
            id = "front_wiper", title = "앞 와이퍼", signal = VehicleSignal.FRONT_WIPER,
            isOn = { it == WiperState.ON },
            turnOn = "앞 와이퍼를 한 번 작동해 보세요. 마른 유리에서는 짧게만 움직여 주세요.", turnOff = "앞 와이퍼를 멈춰 보세요.",
            restoreOn = "좋아요. 와이퍼를 원래대로 다시 켜 주세요.", restoreOff = "좋아요. 이제 와이퍼를 꺼 주세요.",
            hint = "운전대 오른쪽 레버를 아래로 한 칸 내리면 와이퍼가 움직여요. 차종마다 다를 수 있어요.",
            restoreHint = "오른쪽 레버를 원래 위치로 올리면 멈춰요.",
            valueLabel = ::wiperLabel,
        ),
        TutorialStep(
            id = "driver_window", title = "운전석 창문", signal = VehicleSignal.DRIVER_WINDOW,
            phases = listOf(
                Phase({ "운전석 창문을 조금 내려 보세요." }, "운전석 문 손잡이 옆 창문 스위치를 아래로 누르세요.", Goals.movedBy(2)),
                Phase({ "좋아요. 이제 창문을 원래대로 다시 올려 주세요." }, "같은 스위치를 위로 당기면 올라가요.", Goals.returnedToOriginal()),
            ),
            valueLabel = { if (it == null) "확인 중" else if (it == 0) "닫힘" else "열림 $it" },
        ),
        toggleStep(
            id = "hvac_ac", title = "에어컨", signal = VehicleSignal.HVAC_AC,
            isOn = { it == true },
            turnOn = "에어컨, A/C 버튼을 켜 보세요.", turnOff = "에어컨, A/C 버튼을 꺼 보세요.",
            restoreOn = "좋아요. 에어컨을 다시 켜서 원래대로 해 주세요.", restoreOff = "좋아요. 에어컨을 다시 꺼 주세요.",
            hint = "공조 패널이나 화면 아래 공조 메뉴에 A/C 버튼이 있어요.",
            valueLabel = ::onOffLabel,
        ),
        TutorialStep(
            id = "driver_temperature", title = "운전석 온도", signal = VehicleSignal.DRIVER_TEMPERATURE,
            phases = listOf(
                Phase({ "운전석 온도를 1도 내려 보세요." }, "공조 패널의 온도 버튼이나 다이얼을 낮추는 쪽으로 돌리세요. 0.5도씩 바뀌는 차는 두 번 눌러요.", Goals.decreasedBy(1f)),
                Phase({ "좋아요. 이제 원래 온도로 다시 올려 주세요." }, "같은 버튼을 높이는 쪽으로 누르세요.", Goals.returnedToOriginal()),
            ),
            valueLabel = { (it as? Number)?.let { t -> "%.1f℃".format(t.toFloat()) } ?: "확인 중" },
        ),
        toggleStep(
            id = "steering_heat", title = "핸들 열선", signal = VehicleSignal.STEERING_HEAT,
            isOn = { (it as? Number)?.toInt()?.let { level -> level > 0 } == true },
            turnOn = "핸들 열선을 켜 보세요.", turnOff = "핸들 열선을 꺼 보세요.",
            restoreOn = "좋아요. 핸들 열선을 원래 단계로 다시 켜 주세요.", restoreOff = "좋아요. 이제 핸들 열선을 꺼 주세요.",
            hint = "운전대 아래쪽이나 공조 패널에 핸들 모양 열선 버튼이 있어요.",
            valueLabel = { (it as? Number)?.toInt()?.let { level -> if (level == 0) "꺼짐" else "${level}단계" } ?: "확인 중" },
        ),
        toggleStep(
            id = "door_lock", title = "도어 잠금", signal = VehicleSignal.DOOR_LOCK,
            isOn = { it == true },
            turnOn = "문을 잠가 보세요.", turnOff = "문 잠금을 풀어 보세요.",
            restoreOn = "좋아요. 이제 다시 문을 잠가 주세요.", restoreOff = "좋아요. 이제 다시 잠금을 풀어 주세요.",
            hint = "운전석 문 안쪽 손잡이 근처에 잠금 버튼이 있어요.",
            valueLabel = { when (it) { true -> "잠김"; false -> "풀림"; else -> "확인 중" } },
        ),
        toggleStep(
            id = "trunk", title = "트렁크", signal = VehicleSignal.TRUNK,
            isOn = { (it as? Number)?.toInt()?.let { pos -> pos > 0 } == true },
            turnOn = "트렁크를 열어 보세요.", turnOff = "트렁크를 닫아 보세요.",
            restoreOn = "좋아요. 트렁크를 원래대로 열어 두세요.", restoreOff = "좋아요. 이제 트렁크를 닫아 주세요.",
            hint = "운전석 문 아래쪽이나 스마트키에 트렁크 버튼이 있어요. 닫을 때는 트렁크 문 안쪽 버튼을 써도 돼요.",
            valueLabel = { (it as? Number)?.toInt()?.let { pos -> if (pos == 0) "닫힘" else "열림" } ?: "확인 중" },
        ),
        TutorialStep(
            id = "driving_mode", title = "주행 모드", signal = VehicleSignal.DRIVING_MODE,
            phases = listOf(
                Phase({ "주행 모드를 바꿔 보세요." }, "기어 근처나 운전대에 있는 DRIVE MODE 버튼을 누르세요.", Goals.changedFromOriginal()),
                Phase({ "좋아요. 이제 원래 주행 모드로 돌려 주세요." }, "같은 버튼을 다시 눌러 원래 모드로 맞추세요.", Goals.returnedToOriginal()),
            ),
            valueLabel = {
                when (it) {
                    DrivingModeType.NORMAL -> "일반"
                    DrivingModeType.ECO -> "에코"
                    DrivingModeType.SPORT -> "스포츠"
                    null -> "확인 중"
                    else -> it.toString()
                }
            },
        ),
    )

    private fun lightLabel(v: Any?) = when (v) {
        LightState.ON -> "켜짐"
        LightState.OFF -> "꺼짐"
        LightState.DAYTIME_RUNNING -> "주간주행등"
        null -> "확인 중"
        else -> v.toString()
    }

    private fun turnSignalLabel(v: Any?) = when (v) {
        TurnSignalValue.LEFT -> "왼쪽"
        TurnSignalValue.RIGHT -> "오른쪽"
        TurnSignalValue.OFF -> "꺼짐"
        null -> "확인 중"
        else -> v.toString()
    }

    private fun wiperLabel(v: Any?) = when (v) {
        WiperState.ON -> "작동 중"
        WiperState.OFF -> "멈춤"
        WiperState.SERVICE -> "정비 위치"
        null -> "확인 중"
        else -> v.toString()
    }

    private fun onOffLabel(v: Any?) = when (v) {
        true -> "켜짐"
        false -> "꺼짐"
        else -> "확인 중"
    }
}
