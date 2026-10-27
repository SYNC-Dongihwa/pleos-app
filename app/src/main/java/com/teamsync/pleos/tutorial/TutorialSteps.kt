package com.teamsync.pleos.tutorial

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperState
import com.teamsync.pleos.vehicle.VehicleSignal

/**
 * 튜토리얼 단계와 상황별 코스. 비상등·사이드미러 접기는 SDK에 API가 없어 제외 (CLAUDE.md §3).
 * 위치 힌트는 일반적인 배치 기준이라 "차종마다 다를 수 있어요"를 붙인다.
 *
 * 출발점: 신형으로 바꾼 차에서 주유구 여는 법이 예전 차와 달라 못 연 경험 → 와이퍼·전조등 같은 기본 조작뿐 아니라
 * 주유구·보닛·김서림처럼 "필요한 순간에야 찾게 되는" 기능을 상황별 코스로 연습하게 한다.
 */
object TutorialSteps {

    private val defined: List<TutorialStep> = listOf(
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

        // ---- 주유·충전 ----
        toggleStep(
            id = "fuel_door", title = "주유구", signal = VehicleSignal.FUEL_DOOR,
            isOn = { it == true },
            turnOn = "주유구를 열어 보세요.", turnOff = "주유구를 닫아 보세요.",
            restoreOn = "좋아요. 주유구를 다시 열어 두세요.", restoreOff = "좋아요. 이제 주유구 덮개를 닫아 주세요.",
            hint = "차마다 여는 방법이 달라요. 운전석 아래쪽 주유구 레버를 당기는 차도 있고, 문이 열린 상태에서 주유구 덮개를 꾹 누르면 열리는 차도 있어요.",
            restoreHint = "주유구 덮개를 손으로 눌러 닫아요.",
            valueLabel = ::openLabel,
        ),
        toggleStep(
            id = "charge_port", title = "충전구", signal = VehicleSignal.CHARGE_PORT,
            isOn = { it == true },
            turnOn = "충전구를 열어 보세요.", turnOff = "충전구를 닫아 보세요.",
            restoreOn = "좋아요. 충전구를 다시 열어 두세요.", restoreOff = "좋아요. 이제 충전구 덮개를 닫아 주세요.",
            hint = "충전구 덮개를 누르거나, 화면의 충전 메뉴나 스마트키 버튼으로 여는 차도 있어요. 차종마다 달라요.",
            restoreHint = "충전구 덮개를 손으로 눌러 닫거나 같은 버튼을 다시 눌러요.",
            valueLabel = ::openLabel,
        ),
        toggleStep(
            id = "hood", title = "보닛", signal = VehicleSignal.HOOD,
            isOn = { (it as? Number)?.toInt()?.let { pos -> pos > 0 } == true },
            turnOn = "보닛을 열어 보세요.", turnOff = "보닛을 닫아 보세요.",
            restoreOn = "좋아요. 보닛을 다시 열어 두세요.", restoreOff = "좋아요. 이제 보닛을 닫아 주세요.",
            hint = "운전석 아래 왼쪽의 보닛 레버를 당긴 뒤, 차 앞쪽 보닛 틈의 걸쇠를 밀면서 들어 올려요. 차종마다 다를 수 있어요.",
            restoreHint = "보닛을 낮게 내린 뒤 손바닥으로 눌러 딸깍 소리가 나게 닫아요.",
            valueLabel = { (it as? Number)?.toInt()?.let { pos -> if (pos == 0) "닫힘" else "열림" } ?: "확인 중" },
        ),

        // ---- 비·김서림 ----
        toggleStep(
            id = "front_defroster", title = "앞유리 김서림 제거", signal = VehicleSignal.FRONT_DEFROSTER,
            isOn = { it == true },
            turnOn = "앞유리 김서림 제거를 켜 보세요.", turnOff = "앞유리 김서림 제거를 꺼 보세요.",
            restoreOn = "좋아요. 김서림 제거를 다시 켜 주세요.", restoreOff = "좋아요. 이제 김서림 제거를 꺼 주세요.",
            hint = "공조 패널에서 앞유리 모양에 물결 화살표가 그려진 버튼을 찾아보세요.",
            valueLabel = ::onOffLabel,
        ),
        toggleStep(
            id = "rear_defroster", title = "뒷유리 열선", signal = VehicleSignal.REAR_DEFROSTER,
            isOn = { it == true },
            turnOn = "뒷유리 열선을 켜 보세요.", turnOff = "뒷유리 열선을 꺼 보세요.",
            restoreOn = "좋아요. 뒷유리 열선을 다시 켜 주세요.", restoreOff = "좋아요. 이제 뒷유리 열선을 꺼 주세요.",
            hint = "공조 패널에서 네모난 유리 모양에 물결 화살표가 그려진 버튼이에요.",
            valueLabel = ::onOffLabel,
        ),
        toggleStep(
            id = "rear_wiper", title = "뒷유리 와이퍼", signal = VehicleSignal.REAR_WIPER,
            isOn = { it == WiperState.ON },
            turnOn = "뒷유리 와이퍼를 작동해 보세요.", turnOff = "뒷유리 와이퍼를 멈춰 보세요.",
            restoreOn = "좋아요. 뒷유리 와이퍼를 다시 켜 주세요.", restoreOff = "좋아요. 이제 뒷유리 와이퍼를 꺼 주세요.",
            hint = "오른쪽 와이퍼 레버 끝의 다이얼을 돌리거나 레버를 앞으로 밀어요. 차종마다 다를 수 있어요.",
            valueLabel = ::wiperLabel,
        ),
        toggleStep(
            id = "recirculation", title = "내기 순환", signal = VehicleSignal.RECIRCULATION,
            isOn = { it == true },
            turnOn = "내기 순환을 켜 보세요. 터널이나 매연이 많은 곳에서 써요.", turnOff = "외기 유입으로 바꿔 보세요.",
            restoreOn = "좋아요. 다시 내기 순환으로 돌려 주세요.", restoreOff = "좋아요. 이제 외기 유입으로 돌려 주세요.",
            hint = "공조 패널에서 차 모양 안에 화살표가 도는 버튼이에요.",
            valueLabel = { when (it) { true -> "내기 순환"; false -> "외기 유입"; else -> "확인 중" } },
        ),

        // ---- 추위·공조 (에어컨·운전석 온도·핸들 열선은 위에서 정의) ----
        toggleStep(
            id = "hvac_auto", title = "오토 공조", signal = VehicleSignal.HVAC_AUTO,
            isOn = { it == true },
            turnOn = "공조를 오토로 켜 보세요.", turnOff = "오토 공조를 꺼 보세요.",
            restoreOn = "좋아요. 오토 공조를 다시 켜 주세요.", restoreOff = "좋아요. 이제 오토 공조를 꺼 주세요.",
            hint = "공조 패널이나 화면 아래 공조 메뉴의 AUTO 버튼이에요.",
            valueLabel = ::onOffLabel,
        ),
        TutorialStep(
            id = "fan_speed", title = "바람 세기", signal = VehicleSignal.FAN_SPEED,
            phases = listOf(
                Phase({ "바람 세기를 한 단계 올려 보세요." }, "공조 패널의 바람개비 모양 버튼이나 다이얼을 올리는 쪽으로 돌리세요.", Goals.increasedBy(1f)),
                Phase({ "좋아요. 이제 원래 바람 세기로 돌려 주세요." }, "같은 버튼을 내리는 쪽으로 누르세요.", Goals.returnedToOriginal()),
            ),
            valueLabel = { (it as? Number)?.toInt()?.let { level -> if (level == 0) "꺼짐" else "${level}단계" } ?: "확인 중" },
        ),
        toggleStep(
            id = "seat_heat", title = "시트 열선", signal = VehicleSignal.SEAT_HEAT,
            isOn = { (it as? Number)?.toInt()?.let { level -> level > 0 } == true },
            turnOn = "운전석 시트 열선을 켜 보세요.", turnOff = "운전석 시트 열선을 꺼 보세요.",
            restoreOn = "좋아요. 시트 열선을 원래 단계로 다시 켜 주세요.", restoreOff = "좋아요. 이제 시트 열선을 꺼 주세요.",
            hint = "공조 패널이나 화면 아래쪽에 의자 모양에 물결이 그려진 버튼이 있어요.",
            // 콘솔·capability 범위 -3..3: 음수는 통풍 (10-27 실측 bounds=-3..3)
            valueLabel = { (it as? Number)?.toInt()?.let { level -> if (level == 0) "꺼짐" else if (level > 0) "열선 ${level}단계" else "통풍 ${-level}단계" } ?: "확인 중" },
        ),

        // ---- 아이·야간 ----
        toggleStep(
            id = "window_lock", title = "창문 잠금", signal = VehicleSignal.WINDOW_LOCK,
            isOn = { it == true },
            turnOn = "창문 잠금을 켜 보세요. 뒷좌석에서 아이가 창문을 못 열게 막아 줘요.", turnOff = "창문 잠금을 풀어 보세요.",
            restoreOn = "좋아요. 창문 잠금을 다시 켜 주세요.", restoreOff = "좋아요. 이제 창문 잠금을 풀어 주세요.",
            hint = "운전석 문 창문 스위치 옆에 자물쇠 모양 버튼이 있어요.",
            valueLabel = { when (it) { true -> "잠김"; false -> "풀림"; else -> "확인 중" } },
        ),
        toggleStep(
            id = "cabin_light", title = "실내등", signal = VehicleSignal.CABIN_LIGHT,
            isOn = { it == LightState.ON },
            turnOn = "실내등을 켜 보세요.", turnOff = "실내등을 꺼 보세요.",
            restoreOn = "좋아요. 실내등을 다시 켜 주세요.", restoreOff = "좋아요. 이제 실내등을 꺼 주세요.",
            hint = "천장 앞쪽 조명 근처의 버튼이에요.",
            valueLabel = ::lightLabel,
        ),
        toggleStep(
            id = "reading_light", title = "뒷좌석 독서등", signal = VehicleSignal.READING_LIGHT,
            isOn = { it == LightState.ON },
            turnOn = "뒷좌석 독서등을 켜 보세요.", turnOff = "뒷좌석 독서등을 꺼 보세요.",
            restoreOn = "좋아요. 독서등을 다시 켜 주세요.", restoreOff = "좋아요. 이제 독서등을 꺼 주세요.",
            hint = "뒷좌석 천장 조명을 누르거나 옆의 버튼을 눌러요.",
            valueLabel = ::lightLabel,
        ),
    )

    private val byId = defined.associateBy { it.id }

    val courses: List<Course> = listOf(
        Course("basic", "기본 조작", "처음 타면 먼저 익힐 것", listOf("low_beam", "high_beam", "turn_signal", "front_wiper", "driver_window", "door_lock", "trunk", "driving_mode")),
        Course("fuel", "주유·충전", "주유소·충전소·정비소에서", listOf("fuel_door", "charge_port", "hood")),
        Course("weather", "비·김서림", "비 오는 날, 유리에 김이 서릴 때", listOf("front_defroster", "rear_defroster", "rear_wiper", "recirculation")),
        Course("climate", "추위·공조", "덥거나 추울 때", listOf("hvac_ac", "driver_temperature", "hvac_auto", "fan_speed", "seat_heat", "steering_heat")),
        Course("family", "아이·야간", "아이와 탈 때, 어두울 때", listOf("window_lock", "cabin_light", "reading_light")),
    )

    /** 전체 단계 (코스 순서). "전체 코스"도 이 순서로 진행. */
    val all: List<TutorialStep> = courses.flatMap { it.stepIds }.distinct().map { byId.getValue(it) }

    val everything = Course("all", "전체", "모든 코스를 한 번에", all.map { it.id })

    private fun openLabel(v: Any?) = when (v) {
        true -> "열림"
        false -> "닫힘"
        else -> "확인 중"
    }

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
