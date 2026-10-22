# SDK Probe 전체 결과표 (자동 생성)

> 생성: `python -X utf8 tools/summarize_probe_log.py logs/probe-run4.log` — 2026-10-22, Pleos Connect Emulator 2.1.0 (Vehicle Service 2.3.22 / Navi Service 3.0.4), Vehicle SDK 2.3.2.
> Virtual Console을 일부 조작한 상태(SOC 80%, 주행가능거리 150 km, 누적 1234 km, 운전석 23℃ 등)에서 실행. 해석은 [sdk-probe-results.md](sdk-probe-results.md) 참고.
> "(등록됨, 업데이트 없음)" = register 호출은 예외 없이 끝났지만 콜백이 한 번도 오지 않음 → 대부분 미지원 속성 (register는 미지원이어도 실패하지 않는 경우가 있음).

| 도메인 | 종류 | API | 결과 |
|---|---|---|---|
| Brake | CAP | `checkBrakePedalStatusCapabilities` | ✅ CapabilityConfig(accessType=READ) |
| Brake | GET | `getCurrentBrakePedalStatus` | ✅ false |
| Brake | LIVE | `registerBrakePedalStatus` | ✅ 수신값: false |
| CarInfo | CAP | `checkCRNInfoCapabilities` | ✅ CapabilityConfig(accessType=READ) |
| CarInfo | CAP | `checkVINInfoCapabilities` | ✅ CapabilityConfig(accessType=READ) |
| CarInfo | GET | `getCRNInfo` | ✅ "invalid" |
| CarInfo | GET | `getCapabilityInfo` | ✅ {LANE_CHANGE_ASSIST_ENABLED=CapabilityConfig(areaType=0, access=3, changeMode=1, configArr… |
| CarInfo | GET | `getVINInfo` | ✅ "invalid" |
| Display | CAP | `checkTemperatureDisplayUnitCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) |
| Display | GET | `getCurrentTemperatureDisplayUnit` | ✅ CELSIUS |
| Display | LIVE | `registerTemperatureDisplayUnit` | ✅ 수신값: CELSIUS |
| Door | CAP | `checkDoorLockCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [DoorArea(HOOD), DoorArea(ROW_1_LEFT,ROW_1_RIGH… |
| Door | CAP | `checkDoorPositionCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {DoorArea(ROW_2_LEFT)=AreaBounds(minValue=0, ma… |
| Door | CAP | `checkGloveBoxLockCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) |
| Door | GET | `getCurrentDoorPosition(DoorArea(HOOD))` | ✅ 0 |
| Door | GET | `getCurrentDoorPosition(DoorArea(REAR))` | ✅ 0 |
| Door | GET | `getCurrentDoorPosition(DoorArea(ROW_1_LEFT))` | ✅ 0 |
| Door | GET | `getCurrentDoorPosition(DoorArea(ROW_1_RIGHT))` | ✅ 0 |
| Door | GET | `getCurrentDoorPosition(DoorArea(ROW_2_LEFT))` | ✅ 0 |
| Door | GET | `getCurrentDoorPosition(DoorArea(ROW_2_RIGHT))` | ✅ 0 |
| Door | GET | `getCurrentGloveBoxLockState` | ✅ false |
| Door | GET | `getDoorLockState(DoorArea(HOOD))` | ✅ true |
| Door | GET | `getDoorLockState(DoorArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW_2_RIGH…` | ✅ true |
| Door | LIVE | `registerDoorLockState(DoorArea(HOOD))` | ✅ 수신값: true |
| Door | LIVE | `registerDoorLockState(DoorArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW_2…` | ✅ 수신값: true |
| Door | LIVE | `registerDoorPosition(DoorArea(HOOD))` | ✅ 수신값: 0 |
| Door | LIVE | `registerDoorPosition(DoorArea(REAR))` | ✅ 수신값: 0 |
| Door | LIVE | `registerDoorPosition(DoorArea(ROW_1_LEFT))` | ✅ 수신값: 0 |
| Door | LIVE | `registerDoorPosition(DoorArea(ROW_1_RIGHT))` | ✅ 수신값: 0 → 1 → 0 |
| Door | LIVE | `registerDoorPosition(DoorArea(ROW_2_LEFT))` | ✅ 수신값: 0 |
| Door | LIVE | `registerDoorPosition(DoorArea(ROW_2_RIGHT))` | ✅ 수신값: 0 |
| Door | LIVE | `registerGloveBoxLockState` | ✅ (등록됨, 업데이트 없음) |
| DrivingMode | CAP | `checkCruiseControlStateCapabilities` | ✅ CapabilityConfig(accessType=READ) |
| DrivingMode | CAP | `checkCruiseControlTargetSpeedCapabilities` | ✅ CapabilityConfig(accessType=READ) |
| DrivingMode | CAP | `checkDrivingModeStateCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| DrivingMode | GET | `getCruiseControlState` | ✅ ENABLED |
| DrivingMode | GET | `getCruiseControlTargetSpeed` | ✅ 13.8889 |
| DrivingMode | GET | `getCurrentDrivingModeState` | ❌ not in carPropertyConfig: 0x21400410 |
| DrivingMode | LIVE | `registerCruiseControlState` | ✅ 수신값: ENABLED |
| DrivingMode | LIVE | `registerCruiseControlTargetSpeed` | ✅ 수신값: 13.8889 |
| DrivingMode | LIVE | `registerDrivingModeState` | ✅ (등록됨, 업데이트 없음) |
| EvBattery | CAP | `checkCumulativeEvEfficiencyCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| EvBattery | CAP | `checkCurrentEvEfficiencyCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| EvBattery | CAP | `checkEvBatteryStateOfChargeCapabilities` | ✅ CapabilityConfig(accessType=READ) |
| EvBattery | CAP | `checkEvChargeCurrentDrawLimitCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [48.0] |
| EvBattery | CAP | `checkEvChargePercentLimitCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [50.0, 55.0, 60.0, 65.0, 70.0, 75.0, 80.0, 85.0… |
| EvBattery | CAP | `checkEvChargePortOpenCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) |
| EvBattery | CAP | `checkEvChargeSwitchCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) |
| EvBattery | CAP | `checkInstantaneousEvEfficiencyCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| EvBattery | CAP | `checkSinceChargeEvEfficiencyCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| EvBattery | GET | `getCumulativeEvEfficiency` | ❌ not in carPropertyConfig: 0x21600b15 |
| EvBattery | GET | `getCurrentEvEfficiency` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| EvBattery | GET | `getEvBatteryCapacity` | ✅ 77400.0 |
| EvBattery | GET | `getEvBatteryLevel` | ✅ 27100.0 |
| EvBattery | GET | `getEvBatteryStateOfCharge` | ✅ 35 |
| EvBattery | GET | `getEvChargeCurrentDrawLimit` | ✅ 12.5 |
| EvBattery | GET | `getEvChargeSwitchState` | ✅ false |
| EvBattery | GET | `getInstantaneousEvEfficiency` | ❌ not in carPropertyConfig: 0x21600b13 |
| EvBattery | GET | `getSinceChargeEvEfficiency` | ❌ not in carPropertyConfig: 0x21600b14 |
| EvBattery | GET | `isEvChargePortOpen` | ✅ false |
| EvBattery | LIVE | `registerCumulativeEvEfficiency` | ✅ (등록됨, 업데이트 없음) |
| EvBattery | LIVE | `registerCurrentEvEfficiency` | ✅ (등록됨, 업데이트 없음) |
| EvBattery | LIVE | `registerEvBatteryLevel` | ✅ 수신값: 27100.0 |
| EvBattery | LIVE | `registerEvBatteryStateOfCharge` | ✅ 수신값: 80 |
| EvBattery | LIVE | `registerEvChargePortOpen` | ✅ 수신값: false |
| EvBattery | LIVE | `registerInstantaneousEvEfficiency` | ✅ (등록됨, 업데이트 없음) |
| EvBattery | LIVE | `registerSinceChargeEvEfficiency` | ✅ (등록됨, 업데이트 없음) |
| Fuel | CAP | `checkCumulativeFuelEconomyCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Fuel | CAP | `checkCurrentFuelEconomyCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Fuel | CAP | `checkFuelPercentCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Fuel | CAP | `checkInstantaneousFuelEconomyCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Fuel | CAP | `checkSinceChargeFuelEconomyCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Fuel | GET | `getCumulativeFuelEconomy` | ❌ not in carPropertyConfig: 0x21600be2 |
| Fuel | GET | `getCurrentFuelEconomy` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Fuel | GET | `getFuelPercent` | ❌ not in carPropertyConfig: 0x21400be5 |
| Fuel | GET | `getInstantaneousFuelEconomy` | ❌ not in carPropertyConfig: 0x21600be0 |
| Fuel | GET | `getSinceChargeFuelEconomy` | ❌ not in carPropertyConfig: 0x21600be1 |
| Fuel | LIVE | `registerCumulativeFuelEconomy` | ✅ (등록됨, 업데이트 없음) |
| Fuel | LIVE | `registerCurrentFuelEconomy` | ✅ (등록됨, 업데이트 없음) |
| Fuel | LIVE | `registerFuelPercent` | ✅ (등록됨, 업데이트 없음) |
| Fuel | LIVE | `registerInstantaneousFuelEconomy` | ✅ (등록됨, 업데이트 없음) |
| Fuel | LIVE | `registerSinceChargeFuelEconomy` | ✅ (등록됨, 업데이트 없음) |
| Gear | GET | `getCurrentGearSelectionState` | ✅ GEAR_PARK |
| Gear | LIVE | `registerGearSelection` | ✅ 수신값: GEAR_PARK |
| HVAC | CAP | `checkHvacAcCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW… |
| HVAC | CAP | `checkHvacAirCleanerCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW… |
| HVAC | CAP | `checkHvacAutoCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT,ROW_1_RIGHT)] |
| HVAC | CAP | `checkHvacAutoRecirculationCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW… |
| HVAC | CAP | `checkHvacDefrosterCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [WindowArea(FRONT_WINDSHIELD), WindowArea(REAR_… |
| HVAC | CAP | `checkHvacDualCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT,ROW_1_RIGHT)] |
| HVAC | CAP | `checkHvacFanDirectionCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_1_LEFT,ROW_1_RIGHT)=[]} |
| HVAC | CAP | `checkHvacFanSpeedCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_1_LEFT,ROW_1_RIGHT)=AreaBounds(mi… |
| HVAC | CAP | `checkHvacMaxAcCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| HVAC | CAP | `checkHvacMaxDefrostCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW… |
| HVAC | CAP | `checkHvacPowerCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW… |
| HVAC | CAP | `checkHvacRecirculationCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW… |
| HVAC | CAP | `checkHvacTemperatureCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=15.0,… |
| HVAC | CAP | `checkHvacTemperatureCurrentCapabilities` | ✅ CapabilityConfig(accessType=READ) \| [SeatArea(ROW_1_LEFT), SeatArea(ROW_1_RIGHT), SeatAre… |
| HVAC | GET | `getHvacAcState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW_2_CENTER…` | ✅ false |
| HVAC | GET | `getHvacAirCleanerState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW_…` | ✅ false |
| HVAC | GET | `getHvacAutoRecirculationState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LE…` | ✅ false |
| HVAC | GET | `getHvacAutoState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT))` | ✅ false |
| HVAC | GET | `getHvacDefrosterState(WindowArea(FRONT_WINDSHIELD))` | ✅ false |
| HVAC | GET | `getHvacDefrosterState(WindowArea(REAR_WINDSHIELD))` | ✅ false |
| HVAC | GET | `getHvacDualState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT))` | ✅ false |
| HVAC | GET | `getHvacFanDirection(SeatArea(ROW_1_LEFT,ROW_1_RIGHT))` | ✅ FACE |
| HVAC | GET | `getHvacFanSpeed(SeatArea(ROW_1_LEFT,ROW_1_RIGHT))` | ✅ 6 |
| HVAC | GET | `getHvacMaxAcState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW_2_CEN…` | ❌ not in carPropertyConfig: HVAC_MAX_AC_ON |
| HVAC | GET | `getHvacMaxDefrostState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW_…` | ✅ false |
| HVAC | GET | `getHvacPowerState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW_2_CEN…` | ✅ false |
| HVAC | GET | `getHvacRecirculationState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,R…` | ✅ false |
| HVAC | GET | `getHvacTemperature(SeatArea(ROW_1_LEFT), CELSIUS)` | ✅ 23.0 |
| HVAC | GET | `getHvacTemperature(SeatArea(ROW_1_RIGHT), CELSIUS)` | ✅ 22.0 |
| HVAC | GET | `getHvacTemperatureCurrent(SeatArea(ROW_1_LEFT))` | ✅ 19.1 |
| HVAC | GET | `getHvacTemperatureCurrent(SeatArea(ROW_1_LEFT), CELSIUS)` | ✅ 19.1 |
| HVAC | GET | `getHvacTemperatureCurrent(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,R…` | ✅ 19.1 |
| HVAC | GET | `getHvacTemperatureCurrent(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,R…` | ✅ 19.1 |
| HVAC | GET | `getHvacTemperatureCurrent(SeatArea(ROW_1_RIGHT))` | ✅ 19.1 |
| HVAC | GET | `getHvacTemperatureCurrent(SeatArea(ROW_1_RIGHT), CELSIUS)` | ✅ 19.1 |
| HVAC | LIVE | `registerHvacAcState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW_2_C…` | ✅ 수신값: false |
| HVAC | LIVE | `registerHvacAirCleanerState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT…` | ✅ 수신값: false |
| HVAC | LIVE | `registerHvacAutoRecirculationState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW…` | ✅ 수신값: false |
| HVAC | LIVE | `registerHvacAutoState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT))` | ✅ 수신값: false |
| HVAC | LIVE | `registerHvacDefrosterState(WindowArea(FRONT_WINDSHIELD))` | ✅ 수신값: false |
| HVAC | LIVE | `registerHvacDefrosterState(WindowArea(REAR_WINDSHIELD))` | ✅ 수신값: false |
| HVAC | LIVE | `registerHvacDualState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT))` | ✅ 수신값: false |
| HVAC | LIVE | `registerHvacFanSpeed(SeatArea(ROW_1_LEFT,ROW_1_RIGHT))` | ✅ 수신값: 6 |
| HVAC | LIVE | `registerHvacMaxAcState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW_…` | ✅ (등록됨, 업데이트 없음) |
| HVAC | LIVE | `registerHvacMaxDefrostState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT…` | ✅ 수신값: false |
| HVAC | LIVE | `registerHvacPowerState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW_…` | ✅ 수신값: false |
| HVAC | LIVE | `registerHvacRecirculationState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_L…` | ✅ 수신값: false |
| HVAC | LIVE | `registerHvacTemperature(SeatArea(ROW_1_LEFT))` | ✅ 수신값: 23.0 \| 74.0 |
| HVAC | LIVE | `registerHvacTemperature(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: 22.0 → 22.0 \| 72.0 |
| HVAC | LIVE | `registerHvacTemperatureCurrent(SeatArea(ROW_1_LEFT))` | ✅ 수신값: 19.1 → 19.1 \| 66.0 |
| HVAC | LIVE | `registerHvacTemperatureCurrent(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_L…` | ✅ 수신값: 19.1 → 19.1 \| 66.0 |
| HVAC | LIVE | `registerHvacTemperatureCurrent(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: 19.1 → 19.1 \| 66.0 |
| Light | CAP | `checkAmbientColorAutoByDriveModeEnabledCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Light | CAP | `checkAmbientLightBrightnessCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW… |
| Light | CAP | `checkAmbientLightBrightnessColorCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW… |
| Light | CAP | `checkAmbientLightStateCapabilities` | ✅ CapabilityConfig(accessType=READ) \| {SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW_2_CEN… |
| Light | CAP | `checkAmbientLightSwitchCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW… |
| Light | CAP | `checkCabinLightStateCapabilities` | ✅ CapabilityConfig(accessType=READ) \| [OFF, ON] |
| Light | CAP | `checkCabinLightSwitchCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [OFF, ON] |
| Light | CAP | `checkFrontFogLightStateCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Light | CAP | `checkFrontFogLightSwitchCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Light | CAP | `checkHighBeamLightStateCapabilities` | ✅ CapabilityConfig(accessType=READ) \| [OFF, ON] |
| Light | CAP | `checkHighBeamLightSwitchCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [OFF, ON] |
| Light | CAP | `checkLowBeamLightStateCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [OFF, ON] |
| Light | CAP | `checkLowBeamLightSwitchCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [OFF, ON] |
| Light | CAP | `checkReadingLightStateCapabilities` | ✅ CapabilityConfig(accessType=READ) \| {SeatArea(ROW_2_LEFT)=[OFF, ON], SeatArea(ROW_2_RIGHT… |
| Light | CAP | `checkReadingLightSwitchCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_2_LEFT)=[OFF, ON], SeatArea(ROW_2… |
| Light | CAP | `checkRearFogLightStateCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Light | CAP | `checkRearFogLightSwitchCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Light | CAP | `checkTailLampLightStateCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [OFF, ON] |
| Light | CAP | `checkTailLampLightSwitchCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [OFF, ON] |
| Light | GET | `getAmbientColorAutoByDriveModeEnabled` | ❌ not in carPropertyConfig: 0x21200787 |
| Light | GET | `getAmbientLightBrightness(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,R…` | ✅ 100 |
| Light | GET | `getAmbientLightBrightnessColor(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_L…` | ✅ 16777215 |
| Light | GET | `getAmbientLightState(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW_2_…` | ✅ OFF |
| Light | GET | `getAmbientLightSwitch(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW_2…` | ✅ OFF |
| Light | GET | `getCabinLightSwitch` | ✅ OFF |
| Light | GET | `getCurrentCabinLightState` | ✅ OFF |
| Light | GET | `getCurrentFrontFogLightState` | ❌ not in carPropertyConfig: FRONT_FOG_LIGHTS_STATE |
| Light | GET | `getCurrentHighBeamLightState` | ✅ OFF |
| Light | GET | `getCurrentLowBeamLightState` | ✅ ON |
| Light | GET | `getCurrentReadingLightState(SeatArea(ROW_1_LEFT))` | ✅ OFF |
| Light | GET | `getCurrentReadingLightState(SeatArea(ROW_1_RIGHT))` | ✅ OFF |
| Light | GET | `getCurrentReadingLightState(SeatArea(ROW_2_LEFT))` | ✅ OFF |
| Light | GET | `getCurrentReadingLightState(SeatArea(ROW_2_RIGHT))` | ✅ OFF |
| Light | GET | `getCurrentRearFogLightState` | ❌ not in carPropertyConfig: REAR_FOG_LIGHTS_STATE |
| Light | GET | `getCurrentTailLampLightState` | ✅ ON |
| Light | GET | `getFrontFogLightSwitch` | ❌ not in carPropertyConfig: FRONT_FOG_LIGHTS_SWITCH |
| Light | GET | `getHighBeamLightSwitch` | ✅ OFF |
| Light | GET | `getLowBeamLightSwitch` | ✅ ON |
| Light | GET | `getReadingLightSwitch(SeatArea(ROW_1_LEFT))` | ✅ OFF |
| Light | GET | `getReadingLightSwitch(SeatArea(ROW_1_RIGHT))` | ✅ OFF |
| Light | GET | `getReadingLightSwitch(SeatArea(ROW_2_LEFT))` | ✅ OFF |
| Light | GET | `getReadingLightSwitch(SeatArea(ROW_2_RIGHT))` | ✅ OFF |
| Light | GET | `getRearFogLightSwitch` | ❌ not in carPropertyConfig: REAR_FOG_LIGHTS_SWITCH |
| Light | GET | `getTailLampLightSwitch` | ✅ ON |
| Light | LIVE | `registerAmbientColorAutoByDriveModeEnabled` | ✅ (등록됨, 업데이트 없음) |
| Light | LIVE | `registerAmbientLightBrightnessColorListener(SeatArea(ROW_1_LEFT,ROW_1_…` | ✅ 수신값: 16777215 |
| Light | LIVE | `registerAmbientLightBrightnessListener(SeatArea(ROW_1_LEFT,ROW_1_RIGHT…` | ✅ 수신값: 100 |
| Light | LIVE | `registerAmbientLightStateListener(SeatArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_…` | ✅ 수신값: OFF |
| Light | LIVE | `registerCabinLightState` | ✅ 수신값: OFF |
| Light | LIVE | `registerFrontFogLightState` | ✅ (등록됨, 업데이트 없음) |
| Light | LIVE | `registerHighBeamLightState` | ✅ 수신값: OFF |
| Light | LIVE | `registerLowBeamLightState` | ✅ 수신값: ON |
| Light | LIVE | `registerReadingLightState(SeatArea(ROW_1_LEFT))` | ✅ 수신값: OFF |
| Light | LIVE | `registerReadingLightState(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: OFF |
| Light | LIVE | `registerReadingLightState(SeatArea(ROW_2_LEFT))` | ✅ 수신값: OFF |
| Light | LIVE | `registerReadingLightState(SeatArea(ROW_2_RIGHT))` | ✅ 수신값: OFF |
| Light | LIVE | `registerRearFogLightState` | ✅ (등록됨, 업데이트 없음) |
| Light | LIVE | `registerTailLampLightState` | ✅ 수신값: ON |
| Odometer | CAP | `checkCurrentDistanceDrivenCapabilities` | ✅ CapabilityConfig(accessType=READ) |
| Odometer | CAP | `checkTripCumulativeDrivingDistanceCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | CAP | `checkTripCumulativeDrivingTimeCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | CAP | `checkTripCurrentDrivingDistanceCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | CAP | `checkTripCurrentDrivingTimeCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | CAP | `checkTripDrivingDistanceCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | CAP | `checkTripDrivingTimeCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | CAP | `checkTripInstantaneousDrivingDistanceCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | CAP | `checkTripSinceRefillDrivingDistanceCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | CAP | `checkTripSinceRefillDrivingTimeCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | GET | `getCurrentDistanceDriven` | ✅ 1234.0 |
| Odometer | GET | `getRangeRemainingDistance` | ✅ 150000.0 |
| Odometer | GET | `getTripCumulativeDrivingDistance` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | GET | `getTripCumulativeDrivingTime` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | GET | `getTripCurrentDrivingDistance` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | GET | `getTripCurrentDrivingTime` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | GET | `getTripInstantaneousDrivingDistance` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | GET | `getTripSinceRefillDrivingDistance` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | GET | `getTripSinceRefillDrivingTime` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | LIVE | `registerDistanceDriven` | ✅ 수신값: 1234.0 |
| Odometer | LIVE | `registerRangeRemainingListener` | ✅ 수신값: 150000.0 |
| Odometer | LIVE | `registerTripCumulativeDrivingDistance` | ✅ (등록됨, 업데이트 없음) |
| Odometer | LIVE | `registerTripCumulativeDrivingTime` | ✅ (등록됨, 업데이트 없음) |
| Odometer | LIVE | `registerTripCurrentDrivingDistance` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | LIVE | `registerTripCurrentDrivingTime` | ✅ (등록됨, 업데이트 없음) |
| Odometer | LIVE | `registerTripInstantaneousDrivingDistance` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Odometer | LIVE | `registerTripSinceRefillDrivingDistance` | ✅ (등록됨, 업데이트 없음) |
| Odometer | LIVE | `registerTripSinceRefillDrivingTime` | ✅ (등록됨, 업데이트 없음) |
| Safety | CAP | `checkAirbagCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT), SeatArea(ROW_1_RIGHT)] |
| Safety | CAP | `checkAirbagDeployedCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Safety | CAP | `checkBrakeFluidLevelStateCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Safety | CAP | `checkEngineOilLevelStateCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Safety | CAP | `checkLowWasherFluidWarningStateCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Safety | CAP | `checkPowerDownWarningIndicatorCapabilities` | ✅ CapabilityConfig(accessType=READ) |
| Safety | CAP | `checkSeatBuckledCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_2_LEFT), SeatArea(ROW_2_RIGHT), S… |
| Safety | CAP | `checkSeatNotBuckledWarningCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Safety | CAP | `checkTelltalesCapabilities` | ✅ CapabilityConfig(accessType=READ) |
| Safety | CAP | `checkTransmissionOilLevelStateCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Safety | GET | `getBrakeFluidLevelState` | ❌ not in carPropertyConfig: 0x21400730 |
| Safety | GET | `getEngineOilLevelState` | ❌ not in carPropertyConfig: ENGINE_OIL_LEVEL |
| Safety | GET | `getTelltalesInfo` | ✅ Telltales(telltales=[]) |
| Safety | GET | `getTransmissionOilLevelState` | ❌ not in carPropertyConfig: 0x21400731 |
| Safety | GET | `hasLowWasherFluidWarning` | ❌ not in carPropertyConfig: 0x2120071e |
| Safety | GET | `hasPowerDownWarningIndicator` | ✅ false |
| Safety | GET | `hasSeatAirbagBeenDeployed(SeatArea(ROW_1_LEFT))` | ❌ not in carPropertyConfig: 0x2520071f |
| Safety | GET | `hasSeatAirbagBeenDeployed(SeatArea(ROW_1_RIGHT))` | ❌ not in carPropertyConfig: 0x2520071f |
| Safety | GET | `hasSeatAirbagEnabled(SeatArea(ROW_1_LEFT))` | ✅ false |
| Safety | GET | `hasSeatAirbagEnabled(SeatArea(ROW_1_RIGHT))` | ✅ false |
| Safety | GET | `hasSeatBeltNotBuckledWarning(SeatArea(ROW_1_LEFT))` | ❌ not in carPropertyConfig: 0x25200709 |
| Safety | GET | `hasSeatBeltNotBuckledWarning(SeatArea(ROW_1_RIGHT))` | ❌ not in carPropertyConfig: 0x25200709 |
| Safety | GET | `hasSeatBeltNotBuckledWarning(SeatArea(ROW_2_CENTER))` | ❌ not in carPropertyConfig: 0x25200709 |
| Safety | GET | `hasSeatBeltNotBuckledWarning(SeatArea(ROW_2_LEFT))` | ❌ not in carPropertyConfig: 0x25200709 |
| Safety | GET | `hasSeatBeltNotBuckledWarning(SeatArea(ROW_2_RIGHT))` | ❌ not in carPropertyConfig: 0x25200709 |
| Safety | LIVE | `registerBrakeFluidLevelState` | ✅ (등록됨, 업데이트 없음) |
| Safety | LIVE | `registerEngineOilLevelState` | ✅ (등록됨, 업데이트 없음) |
| Safety | LIVE | `registerLowWasherFluidWaringState` | ✅ (등록됨, 업데이트 없음) |
| Safety | LIVE | `registerPowerDownWarningIndicator` | ✅ 수신값: false |
| Safety | LIVE | `registerSeatAirbagDeployed(SeatArea(ROW_1_LEFT))` | ✅ (등록됨, 업데이트 없음) |
| Safety | LIVE | `registerSeatAirbagDeployed(SeatArea(ROW_1_RIGHT))` | ❌ not in carPropertyConfig: 0x2520071f |
| Safety | LIVE | `registerSeatAirbagEnabled(SeatArea(ROW_1_LEFT))` | ✅ 수신값: false |
| Safety | LIVE | `registerSeatAirbagEnabled(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: false |
| Safety | LIVE | `registerSeatBeltNotBuckledWarning(SeatArea(ROW_1_LEFT))` | ✅ (등록됨, 업데이트 없음) |
| Safety | LIVE | `registerSeatBeltNotBuckledWarning(SeatArea(ROW_1_RIGHT))` | ✅ (등록됨, 업데이트 없음) |
| Safety | LIVE | `registerSeatBeltNotBuckledWarning(SeatArea(ROW_2_CENTER))` | ✅ (등록됨, 업데이트 없음) |
| Safety | LIVE | `registerSeatBeltNotBuckledWarning(SeatArea(ROW_2_LEFT))` | ✅ (등록됨, 업데이트 없음) |
| Safety | LIVE | `registerSeatBeltNotBuckledWarning(SeatArea(ROW_2_RIGHT))` | ✅ (등록됨, 업데이트 없음) |
| Safety | LIVE | `registerTelltales` | ✅ 수신값: Telltales(telltales=[]) |
| Safety | LIVE | `registerTransmissionOilLevelState` | ✅ (등록됨, 업데이트 없음) |
| Seat | CAP | `checkMoveSeatBackrestAngleCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_2_LEFT), SeatArea(ROW_2_RIGHT), S… |
| Seat | CAP | `checkMoveSeatBeltHeightCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT), SeatArea(ROW_1_RIGHT)] |
| Seat | CAP | `checkMoveSeatDepthCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT), SeatArea(ROW_1_RIGHT)] |
| Seat | CAP | `checkMoveSeatForeAftCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_2_LEFT), SeatArea(ROW_2_RIGHT), S… |
| Seat | CAP | `checkMoveSeatHeadrestAngleCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT), SeatArea(ROW_1_RIGHT)] |
| Seat | CAP | `checkMoveSeatHeadrestForeAftCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT), SeatArea(ROW_1_RIGHT)] |
| Seat | CAP | `checkMoveSeatHeadrestHeightCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT), SeatArea(ROW_1_RIGHT)] |
| Seat | CAP | `checkMoveSeatHeightCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_2_LEFT), SeatArea(ROW_2_RIGHT), S… |
| Seat | CAP | `checkMoveSeatLumbarForeAftCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT), SeatArea(ROW_1_RIGHT)] |
| Seat | CAP | `checkMoveSeatLumbarSideSupportCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_1_LEFT), SeatArea(ROW_1_RIGHT)] |
| Seat | CAP | `checkMoveSeatTiltCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_2_LEFT), SeatArea(ROW_2_RIGHT), S… |
| Seat | CAP | `checkSeatBackrestAngleCapabilities` | ✅ {SeatArea(ROW_2_LEFT)=AreaBounds(minValue=0, maxValue=100), SeatArea(ROW_2_RIGHT)=AreaBoun… |
| Seat | CAP | `checkSeatBackrestAnglePositionCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_2_LEFT)=AreaBounds(minValue=0, ma… |
| Seat | CAP | `checkSeatBeltBuckledCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [SeatArea(ROW_2_LEFT), SeatArea(ROW_2_RIGHT), S… |
| Seat | CAP | `checkSeatBeltHeightCapabilities` | ✅ {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=0, maxValue=10), SeatArea(ROW_1_RIGHT)=AreaBound… |
| Seat | CAP | `checkSeatBeltHeightPositionCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=0, ma… |
| Seat | CAP | `checkSeatDepthCapabilities` | ✅ {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=-10, maxValue=10), SeatArea(ROW_1_RIGHT)=AreaBou… |
| Seat | CAP | `checkSeatDepthPositionCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=-10, … |
| Seat | CAP | `checkSeatForeAftCapabilities` | ✅ {SeatArea(ROW_2_LEFT)=AreaBounds(minValue=0, maxValue=100), SeatArea(ROW_2_RIGHT)=AreaBoun… |
| Seat | CAP | `checkSeatForeAftPositionCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_2_LEFT)=AreaBounds(minValue=0, ma… |
| Seat | CAP | `checkSeatHeadrestAngleCapabilities` | ✅ {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=-10, maxValue=10), SeatArea(ROW_1_RIGHT)=AreaBou… |
| Seat | CAP | `checkSeatHeadrestAnglePositionCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=-10, … |
| Seat | CAP | `checkSeatHeadrestForeAftCapabilities` | ✅ {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=-10, maxValue=10), SeatArea(ROW_1_RIGHT)=AreaBou… |
| Seat | CAP | `checkSeatHeadrestForeAftPositionCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=-10, … |
| Seat | CAP | `checkSeatHeadrestHeightCapabilities` | ✅ {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=-10, maxValue=10), SeatArea(ROW_1_RIGHT)=AreaBou… |
| Seat | CAP | `checkSeatHeadrestHeightPositionCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=-10, … |
| Seat | CAP | `checkSeatHeightCapabilities` | ✅ {SeatArea(ROW_2_LEFT)=AreaBounds(minValue=0, maxValue=100), SeatArea(ROW_2_RIGHT)=AreaBoun… |
| Seat | CAP | `checkSeatHeightPositionCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_2_LEFT)=AreaBounds(minValue=0, ma… |
| Seat | CAP | `checkSeatLumbarForeAftCapabilities` | ✅ {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=-10, maxValue=10), SeatArea(ROW_1_RIGHT)=AreaBou… |
| Seat | CAP | `checkSeatLumbarForeAftPositionCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=-10, … |
| Seat | CAP | `checkSeatLumbarSideSupportCapabilities` | ✅ {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=-10, maxValue=10), SeatArea(ROW_1_RIGHT)=AreaBou… |
| Seat | CAP | `checkSeatLumbarSideSupportPositionCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=-10, … |
| Seat | CAP | `checkSeatMemoryCapabilities` | ✅ CapabilityConfig(accessType=WRITE) \| {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=1, maxValu… |
| Seat | CAP | `checkSeatOccupancyCapabilities` | ✅ CapabilityConfig(accessType=READ) \| [SeatArea(ROW_2_LEFT), SeatArea(ROW_2_RIGHT), SeatAre… |
| Seat | CAP | `checkSeatTemperatureCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_2_LEFT)=AreaBounds(minValue=0, ma… |
| Seat | CAP | `checkSeatTiltCapabilities` | ✅ {SeatArea(ROW_2_LEFT)=AreaBounds(minValue=0, maxValue=100), SeatArea(ROW_2_RIGHT)=AreaBoun… |
| Seat | CAP | `checkSeatTiltPositionCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_2_LEFT)=AreaBounds(minValue=0, ma… |
| Seat | CAP | `checkSeatVentilationCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {SeatArea(ROW_1_LEFT)=AreaBounds(minValue=0, ma… |
| Seat | GET | `getCurrentSeatBackrestAnglePosition(SeatArea(ROW_1_LEFT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatBackrestAnglePosition(SeatArea(ROW_1_RIGHT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatBackrestAnglePosition(SeatArea(ROW_2_LEFT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatBackrestAnglePosition(SeatArea(ROW_2_RIGHT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatBeltBuckledState(SeatArea(ROW_1_LEFT))` | ✅ false |
| Seat | GET | `getCurrentSeatBeltBuckledState(SeatArea(ROW_1_RIGHT))` | ✅ false |
| Seat | GET | `getCurrentSeatBeltBuckledState(SeatArea(ROW_2_CENTER))` | ✅ false |
| Seat | GET | `getCurrentSeatBeltBuckledState(SeatArea(ROW_2_LEFT))` | ✅ false |
| Seat | GET | `getCurrentSeatBeltBuckledState(SeatArea(ROW_2_RIGHT))` | ✅ false |
| Seat | GET | `getCurrentSeatBeltHeightPosition(SeatArea(ROW_1_LEFT))` | ✅ 10 |
| Seat | GET | `getCurrentSeatBeltHeightPosition(SeatArea(ROW_1_RIGHT))` | ✅ 10 |
| Seat | GET | `getCurrentSeatDepthPosition(SeatArea(ROW_1_LEFT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatDepthPosition(SeatArea(ROW_1_RIGHT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatForeAftPosition(SeatArea(ROW_1_LEFT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatForeAftPosition(SeatArea(ROW_1_RIGHT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatForeAftPosition(SeatArea(ROW_2_LEFT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatForeAftPosition(SeatArea(ROW_2_RIGHT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatHeadrestAnglePosition(SeatArea(ROW_1_LEFT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatHeadrestAnglePosition(SeatArea(ROW_1_RIGHT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatHeadrestForeAftPosition(SeatArea(ROW_1_LEFT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatHeadrestForeAftPosition(SeatArea(ROW_1_RIGHT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatHeadrestHeightAftPosition(SeatArea(ROW_1_LEFT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatHeadrestHeightAftPosition(SeatArea(ROW_1_RIGHT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatHeightPosition(SeatArea(ROW_1_LEFT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatHeightPosition(SeatArea(ROW_1_RIGHT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatHeightPosition(SeatArea(ROW_2_LEFT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatHeightPosition(SeatArea(ROW_2_RIGHT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatLumbarForeAftPosition(SeatArea(ROW_1_LEFT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatLumbarForeAftPosition(SeatArea(ROW_1_RIGHT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatLumbarSideSupportPosition(SeatArea(ROW_1_LEFT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatLumbarSideSupportPosition(SeatArea(ROW_1_RIGHT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatOccupancy(SeatArea(ROW_1_LEFT))` | ✅ true |
| Seat | GET | `getCurrentSeatOccupancy(SeatArea(ROW_1_RIGHT))` | ✅ true |
| Seat | GET | `getCurrentSeatOccupancy(SeatArea(ROW_2_CENTER))` | ✅ true |
| Seat | GET | `getCurrentSeatOccupancy(SeatArea(ROW_2_LEFT))` | ✅ true |
| Seat | GET | `getCurrentSeatOccupancy(SeatArea(ROW_2_RIGHT))` | ✅ true |
| Seat | GET | `getCurrentSeatTiltPosition(SeatArea(ROW_1_LEFT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatTiltPosition(SeatArea(ROW_1_RIGHT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatTiltPosition(SeatArea(ROW_2_LEFT))` | ✅ 0 |
| Seat | GET | `getCurrentSeatTiltPosition(SeatArea(ROW_2_RIGHT))` | ✅ 0 |
| Seat | GET | `getHvacSeatTemperature(SeatArea(ROW_1_LEFT))` | ✅ 0 |
| Seat | GET | `getHvacSeatTemperature(SeatArea(ROW_1_RIGHT))` | ✅ 0 |
| Seat | GET | `getHvacSeatTemperature(SeatArea(ROW_2_LEFT))` | ✅ 0 |
| Seat | GET | `getHvacSeatTemperature(SeatArea(ROW_2_RIGHT))` | ✅ 0 |
| Seat | GET | `getHvacSeatVentilationState(SeatArea(ROW_1_LEFT))` | ✅ 0 |
| Seat | GET | `getHvacSeatVentilationState(SeatArea(ROW_1_RIGHT))` | ✅ 0 |
| Seat | LIVE | `registerHvacSeatTemperature(SeatArea(ROW_1_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerHvacSeatTemperature(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerHvacSeatTemperature(SeatArea(ROW_2_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerHvacSeatTemperature(SeatArea(ROW_2_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerHvacSeatVentilation(SeatArea(ROW_1_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerHvacSeatVentilation(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatBackrestAnglePosition(SeatArea(ROW_1_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatBackrestAnglePosition(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatBackrestAnglePosition(SeatArea(ROW_2_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatBackrestAnglePosition(SeatArea(ROW_2_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatBeltBuckledState(SeatArea(ROW_1_LEFT))` | ✅ 수신값: false |
| Seat | LIVE | `registerSeatBeltBuckledState(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: false |
| Seat | LIVE | `registerSeatBeltBuckledState(SeatArea(ROW_2_CENTER))` | ✅ 수신값: false |
| Seat | LIVE | `registerSeatBeltBuckledState(SeatArea(ROW_2_LEFT))` | ✅ 수신값: false |
| Seat | LIVE | `registerSeatBeltBuckledState(SeatArea(ROW_2_RIGHT))` | ✅ 수신값: false |
| Seat | LIVE | `registerSeatBeltHeightPosition(SeatArea(ROW_1_LEFT))` | ✅ 수신값: 10 |
| Seat | LIVE | `registerSeatBeltHeightPosition(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: 10 |
| Seat | LIVE | `registerSeatDepthPosition(SeatArea(ROW_1_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatDepthPosition(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatForeAftPosition(SeatArea(ROW_1_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatForeAftPosition(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatForeAftPosition(SeatArea(ROW_2_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatForeAftPosition(SeatArea(ROW_2_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatHeadrestAnglePosition(SeatArea(ROW_1_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatHeadrestAnglePosition(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatHeadrestForeAftPosition(SeatArea(ROW_1_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatHeadrestForeAftPosition(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatHeadrestHeightAftPosition(SeatArea(ROW_1_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatHeadrestHeightAftPosition(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatHeightPosition(SeatArea(ROW_1_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatHeightPosition(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatHeightPosition(SeatArea(ROW_2_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatHeightPosition(SeatArea(ROW_2_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatLumbarForeAftPosition(SeatArea(ROW_1_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatLumbarForeAftPosition(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatLumbarSideSupportPosition(SeatArea(ROW_1_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatLumbarSideSupportPosition(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatOccupancy(SeatArea(ROW_1_LEFT))` | ✅ 수신값: true |
| Seat | LIVE | `registerSeatOccupancy(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: true |
| Seat | LIVE | `registerSeatOccupancy(SeatArea(ROW_2_CENTER))` | ✅ 수신값: true |
| Seat | LIVE | `registerSeatOccupancy(SeatArea(ROW_2_LEFT))` | ✅ 수신값: true |
| Seat | LIVE | `registerSeatOccupancy(SeatArea(ROW_2_RIGHT))` | ✅ 수신값: true |
| Seat | LIVE | `registerSeatTiltPosition(SeatArea(ROW_1_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatTiltPosition(SeatArea(ROW_1_RIGHT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatTiltPosition(SeatArea(ROW_2_LEFT))` | ✅ 수신값: 0 |
| Seat | LIVE | `registerSeatTiltPosition(SeatArea(ROW_2_RIGHT))` | ✅ 수신값: 0 |
| SideMirror | CAP | `checkHvacSideMirrorHeatCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {MirrorArea(DRIVER_LEFT)=AreaBounds(minValue=0,… |
| SideMirror | GET | `getHvacSideMirrorHeatState(MirrorArea(DRIVER_LEFT))` | ✅ 0 |
| SideMirror | GET | `getHvacSideMirrorHeatState(MirrorArea(DRIVER_LEFT,DRIVER_RIGHT))` | ✅ 0 |
| SideMirror | GET | `getHvacSideMirrorHeatState(MirrorArea(DRIVER_RIGHT))` | ✅ 0 |
| SideMirror | LIVE | `registerHvacSideMirrorHeatState(MirrorArea(DRIVER_LEFT))` | ✅ 수신값: 0 |
| SideMirror | LIVE | `registerHvacSideMirrorHeatState(MirrorArea(DRIVER_LEFT,DRIVER_RIGHT))` | ✅ 수신값: 0 |
| SideMirror | LIVE | `registerHvacSideMirrorHeatState(MirrorArea(DRIVER_RIGHT))` | ✅ 수신값: 0 |
| SteeringWheel | CAP | `checkHvacSteeringWheelHeatCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| AreaBounds(minValue=0, maxValue=3) |
| SteeringWheel | CAP | `checkSteeringWheelAngleCapabilities` | ✅ CapabilityConfig(accessType=READ) \| AreaBounds(minValue=-470.0, maxValue=500.0) |
| SteeringWheel | GET | `getCurrentSteeringWheelAngle` | ✅ -0.0 |
| SteeringWheel | GET | `getHvacSteeringWheelHeatState` | ✅ 3 |
| SteeringWheel | LIVE | `registerHvacSteeringWheelHeatState` | ✅ 수신값: 3 |
| SteeringWheel | LIVE | `registerSteeringWheelAngle` | ✅ 수신값: -0.0 |
| Tire | CAP | `checkLowTirePressureCapabilities` | ✅ CapabilityConfig(accessType=READ) \| [WheelArea(UNKNOWN,LEFT_FRONT), WheelArea(UNKNOWN,RIG… |
| Tire | CAP | `checkTirePressureCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Tire | CAP | `checkWheelAreaCapabilities` | ✅ [WheelArea(UNKNOWN,LEFT_FRONT), WheelArea(UNKNOWN,RIGHT_FRONT), WheelArea(UNKNOWN,LEFT_REA… |
| Tire | GET | `getLowTirePressure(WheelArea(UNKNOWN,LEFT_FRONT))` | ✅ 137.0 |
| Tire | GET | `getLowTirePressure(WheelArea(UNKNOWN,LEFT_REAR))` | ✅ 137.0 |
| Tire | GET | `getLowTirePressure(WheelArea(UNKNOWN,RIGHT_FRONT))` | ✅ 137.0 |
| Tire | GET | `getLowTirePressure(WheelArea(UNKNOWN,RIGHT_REAR))` | ✅ 137.0 |
| Tire | GET | `getTirePressure(WheelArea(UNKNOWN,LEFT_FRONT))` | ❌ not in carPropertyConfig: TIRE_PRESSURE |
| Tire | GET | `getTirePressure(WheelArea(UNKNOWN,LEFT_REAR))` | ❌ not in carPropertyConfig: TIRE_PRESSURE |
| Tire | GET | `getTirePressure(WheelArea(UNKNOWN,RIGHT_FRONT))` | ❌ not in carPropertyConfig: TIRE_PRESSURE |
| Tire | GET | `getTirePressure(WheelArea(UNKNOWN,RIGHT_REAR))` | ❌ not in carPropertyConfig: TIRE_PRESSURE |
| Tire | LIVE | `registerTirePressure(WheelArea(UNKNOWN,LEFT_FRONT))` | ✅ (등록됨, 업데이트 없음) |
| Tire | LIVE | `registerTirePressure(WheelArea(UNKNOWN,LEFT_REAR))` | ✅ (등록됨, 업데이트 없음) |
| Tire | LIVE | `registerTirePressure(WheelArea(UNKNOWN,RIGHT_FRONT))` | ✅ (등록됨, 업데이트 없음) |
| Tire | LIVE | `registerTirePressure(WheelArea(UNKNOWN,RIGHT_REAR))` | ✅ (등록됨, 업데이트 없음) |
| Toll | CAP | `checkEtcsVoiceGuidanceVolumeCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Toll | GET | `getCurrentEtcsVoiceGuidanceVolume` | ❌ not in carPropertyConfig: 0x21400d01 |
| Toll | LIVE | `registerEtcsVoiceGuidanceVolume` | ✅ (등록됨, 업데이트 없음) |
| TurnSignal | CAP | `checkTurnSignalStateCapabilities` | ✅ CapabilityConfig(accessType=READ) |
| TurnSignal | GET | `getCurrentTurnSignalState` | ✅ OFF |
| TurnSignal | LIVE | `registerTurnSignalState` | ✅ 수신값: OFF |
| Window | CAP | `checkMoveWindowBlindCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [WindowArea(ROW_1_LEFT), WindowArea(ROW_1_RIGHT… |
| Window | CAP | `checkSmartGlassLevelCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Window | CAP | `checkSmartGlassStateCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Window | CAP | `checkSmartGlassZoneStateCapabilities` | ❌ ERROR_NOT_SUPPORTED_PROPERTY |
| Window | CAP | `checkWindowBlindPositionCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {WindowArea(ROW_1_LEFT)=AreaBounds(minValue=0, … |
| Window | CAP | `checkWindowLockCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| [WindowArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,R… |
| Window | CAP | `checkWindowPositionCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {WindowArea(ROW_1_LEFT)=AreaBounds(minValue=0, … |
| Window | GET | `getCurrentSmartGlassLevel(WindowArea(ROW_1_LEFT))` | ❌ not in carPropertyConfig: 0x23400535 |
| Window | GET | `getCurrentSmartGlassLevel(WindowArea(ROW_1_RIGHT))` | ❌ not in carPropertyConfig: 0x23400535 |
| Window | GET | `getCurrentSmartGlassLevel(WindowArea(ROW_2_LEFT))` | ❌ not in carPropertyConfig: 0x23400535 |
| Window | GET | `getCurrentSmartGlassLevel(WindowArea(ROW_2_RIGHT))` | ❌ not in carPropertyConfig: 0x23400535 |
| Window | GET | `getCurrentSmartGlassState` | ❌ not in carPropertyConfig: 0x21200534 |
| Window | GET | `getCurrentWindowBlindPosition(WindowArea(ROW_1_LEFT))` | ✅ 0 |
| Window | GET | `getCurrentWindowBlindPosition(WindowArea(ROW_1_RIGHT))` | ✅ 0 |
| Window | GET | `getCurrentWindowBlindPosition(WindowArea(ROW_2_LEFT))` | ✅ 0 |
| Window | GET | `getCurrentWindowBlindPosition(WindowArea(ROW_2_RIGHT))` | ✅ 0 |
| Window | GET | `getCurrentWindowLockState(WindowArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT…` | ✅ false |
| Window | GET | `getCurrentWindowLockState(WindowArea(ROW_2_LEFT))` | ✅ false |
| Window | GET | `getCurrentWindowLockState(WindowArea(ROW_2_RIGHT))` | ✅ false |
| Window | GET | `getCurrentWindowPosition(WindowArea(ROW_1_LEFT))` | ✅ 4 |
| Window | GET | `getCurrentWindowPosition(WindowArea(ROW_1_RIGHT))` | ✅ 0 |
| Window | GET | `getCurrentWindowPosition(WindowArea(ROW_2_LEFT))` | ✅ 0 |
| Window | GET | `getCurrentWindowPosition(WindowArea(ROW_2_RIGHT))` | ✅ 0 |
| Window | GET | `getSmartGlassConfig(WindowArea(ROW_1_LEFT))` | ❌ not in carPropertyConfig: 0x23411009 |
| Window | GET | `getSmartGlassConfig(WindowArea(ROW_1_RIGHT))` | ❌ not in carPropertyConfig: 0x23411009 |
| Window | GET | `getSmartGlassConfig(WindowArea(ROW_2_LEFT))` | ❌ not in carPropertyConfig: 0x23411009 |
| Window | GET | `getSmartGlassConfig(WindowArea(ROW_2_RIGHT))` | ❌ not in carPropertyConfig: 0x23411009 |
| Window | LIVE | `registerSmartGlassLevel(WindowArea(ROW_1_LEFT))` | ✅ (등록됨, 업데이트 없음) |
| Window | LIVE | `registerSmartGlassLevel(WindowArea(ROW_1_RIGHT))` | ✅ (등록됨, 업데이트 없음) |
| Window | LIVE | `registerSmartGlassLevel(WindowArea(ROW_2_LEFT))` | ✅ (등록됨, 업데이트 없음) |
| Window | LIVE | `registerSmartGlassLevel(WindowArea(ROW_2_RIGHT))` | ✅ (등록됨, 업데이트 없음) |
| Window | LIVE | `registerSmartGlassState` | ✅ (등록됨, 업데이트 없음) |
| Window | LIVE | `registerWindowBlindPosition(WindowArea(ROW_1_LEFT))` | ✅ 수신값: 0 |
| Window | LIVE | `registerWindowBlindPosition(WindowArea(ROW_1_RIGHT))` | ✅ 수신값: 0 |
| Window | LIVE | `registerWindowBlindPosition(WindowArea(ROW_2_LEFT))` | ✅ 수신값: 0 |
| Window | LIVE | `registerWindowBlindPosition(WindowArea(ROW_2_RIGHT))` | ✅ 수신값: 0 |
| Window | LIVE | `registerWindowLockState(WindowArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,R…` | ✅ 수신값: false |
| Window | LIVE | `registerWindowLockState(WindowArea(ROW_2_LEFT))` | ✅ 수신값: false |
| Window | LIVE | `registerWindowLockState(WindowArea(ROW_2_RIGHT))` | ✅ 수신값: false |
| Window | LIVE | `registerWindowPosition(WindowArea(ROW_1_LEFT))` | ✅ 수신값: 4 |
| Window | LIVE | `registerWindowPosition(WindowArea(ROW_1_RIGHT))` | ✅ 수신값: 0 |
| Window | LIVE | `registerWindowPosition(WindowArea(ROW_2_LEFT))` | ✅ 수신값: 0 |
| Window | LIVE | `registerWindowPosition(WindowArea(ROW_2_RIGHT))` | ✅ 수신값: 0 |
| Wiper | CAP | `checkWiperStateCapabilities` | ✅ CapabilityConfig(accessType=READ) \| {WindowArea(FRONT_WINDSHIELD)=[OFF, ON, SERVICE], Win… |
| Wiper | CAP | `checkWiperSwitchCapabilities` | ✅ CapabilityConfig(accessType=READ_WRITE) \| {WindowArea(FRONT_WINDSHIELD)=[OFF, CONTINUOUS_… |
| Wiper | GET | `getCurrentWiperState(WindowArea(FRONT_WINDSHIELD))` | ✅ ON |
| Wiper | GET | `getCurrentWiperState(WindowArea(REAR_WINDSHIELD))` | ✅ OFF |
| Wiper | GET | `getCurrentWiperSwitch(WindowArea(FRONT_WINDSHIELD))` | ✅ AUTO |
| Wiper | GET | `getCurrentWiperSwitch(WindowArea(REAR_WINDSHIELD))` | ✅ OFF |
| Wiper | LIVE | `registerWiperState(WindowArea(FRONT_WINDSHIELD))` | ✅ 수신값: ON |
| Wiper | LIVE | `registerWiperState(WindowArea(REAR_WINDSHIELD))` | ✅ 수신값: OFF |
