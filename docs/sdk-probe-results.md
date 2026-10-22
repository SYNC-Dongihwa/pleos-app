# SDK Probe 실측 결과 (M1, 2026-10-22)

Pleos Connect Emulator에서 Vehicle SDK 전 도메인과 NaviHelper를 실제로 호출하고, Virtual Console을 조작해 값이 바뀌는지 확인한 결과다.
API별 전체 결과표는 [probe-table.md](probe-table.md)에 있다 (`tools/summarize_probe_log.py`로 자동 생성).

## 1. 환경

| 항목 | 버전 |
|---|---|
| 에뮬레이터 이미지 | `system-images;android-34;pleos_car_emulator;x86_64` rev 2 — Pleos Connect v2.1.0, build `RELEASE.release_connect_v2.1.0` (2026-10-16) |
| 화면 | 2560×1440 mdpi (앱 창은 오른쪽 약 1700×1150 영역, 왼쪽은 시스템 클러스터 패널) |
| Vehicle Service (`ai.pleos.playground.service.vehicle`) | 2.3.22 (code 74) — SDK 2.3.2가 요구하는 최소 2.3.13/65 충족 |
| Navi Service (`ai.pleos.playground.service.navi`) | 3.0.4 (code 55) — NaviHelper 3.0.5 요구 최소 2.0.1/11 충족 |
| Navigation 앱 | `ai.umos.maps.android.navigation.app` 3.3.2-rc5-hotfix.3 |
| SDK | `ai.pleos.playground:Vehicle:2.3.2` (+ `vehicle-data:1.1.11`), `NaviHelper:3.0.5` (+ `navi-data:3.0.4`) |
| 기본 위치 | 37.4127, 127.0947 (성남시 수정구 시흥동) |

## 2. 요약

- Vehicle: **20개 도메인**(문서 목록 19개 + `Gear`), 호출 482건 → 성공 약 390 / 실패 약 90 / 타임아웃 0.
  실패는 전부 "이 에뮬레이터 차량에 없는 속성" (아래 §4).
- Virtual Console 조작 → SDK 리스너로 **1초 이내 반영** (§3).
- NaviHelper: 초기화, 조회 8종, 경로 요청/취소 이벤트 모두 수신 (§5).
- 퍼미션: 에뮬레이터에서는 `pleos.car.permission.*` 전부 `protectionLevel=normal` → 설치 시 자동 부여 (`PRIVATE_*` 포함).

## 3. Virtual Console 조작 → SDK 수신 확인

| Virtual Console 조작 | SDK 리스너 / 값 | 비고 |
|---|---|---|
| Gear P → D → P | `Gear.registerGearSelection` → `GEAR_DRIVE` → `GEAR_PARK` | **D가 되는 순간 앱 화면이 "주행 중에는 사용할 수 없습니다"로 가려짐** (0 km/h여도) |
| Battery SOC 100 → 35 → 80 | `EvBattery.registerEvBatteryStateOfCharge` → `35`, `80` | 등록 직후 초기값은 안 옴 (첫 콜백이 변경 시점) |
| Battery Energy 77.4 → 27.1 kWh | `EvBattery.registerEvBatteryLevel` → `27100.0` | 단위 **Wh** |
| Remaining Range 0 → 150000 m | `Odometer.registerRangeRemainingListener` → `150000.0` | 단위 **m** |
| Odometer 0 → 1234 km | `Odometer.registerDistanceDriven` → `1234.0` | 단위 km |
| Charge Port Open/Close | `EvBattery.registerEvChargePortOpen` → `true`/`false` | |
| Charge Switch On | `EvBattery.getEvChargeSwitchState` → `true` | register API 없음 (Get만) |
| Steering Angle +90° | `SteeringWheel.registerSteeringWheelAngle` → **`-90.0`** | **부호가 콘솔과 반대** |
| Turn Signal Blink left | `TurnSignal.registerTurnSignalState` → `LEFT` | Hazard On은 `OFF`로 옴 |
| Telltale TPMS On, Engine Oil Pressure Blink | `Safety.registerTelltales` → `TelltaleData(type=TPMS, state=ON)`, `(ENGINE_OIL_PRESSURE, BLINK)` | `PRIVATE_CAR_SAFETY` 필요 (§6) |
| Exterior Lights Low beam | `Light.registerLowBeamLightState` → `ON` (+ TailLamp `ON`) | |
| Brake Pedal 60% | `Brake.registerBrakePedalStatus` → `true` | bool만 (압력값 없음) |
| Door FL/FR Open, Trunk Open | `Door.registerDoorPosition(DoorArea(ROW_1_LEFT))` → `1`, `(REAR)` → `100` | 도어 0..1, 트렁크 0..100 |
| Door Lock Unlock/Lock | `Door.registerDoorLockState(DoorArea(4도어 묶음))` → `false`/`true` | 개별 도어 Area는 미지원 |
| Window FL 40% | `Window.registerWindowPosition(WindowArea(ROW_1_LEFT))` → `4` | 범위 0..10 |
| HVAC Power/A/C, Driver 22→23℃, Fan 6 | `HVAC.registerHvacPowerState`/`AcState`/`Temperature`(23.0, 74.0℉)/`FanSpeed`(6) | 온도 콜백은 ℃·℉ 두 값 |
| Steering Wheel Heat High | `SteeringWheel.registerHvacSteeringWheelHeatState` → `3` | 0..3 |
| Wiper Front Auto | `Wiper.registerWiperState(FRONT_WINDSHIELD)` → `ON` | 스위치값(AUTO)이 아니라 동작상태 |

- 주기 콜백: `EvBatteryLevel`, `DistanceDriven`, `RangeRemaining`, `SteeringWheelAngle`은 값이 같아도 **약 1~3초마다 반복 수신**된다.
- 콘솔의 ACCEL 버튼은 브라우저 합성 이벤트로는 "누르고 있기"가 재현되지 않아 가속 주행은 확인하지 못함 (D에서 8 km/h 크리핑만 확인).

## 4. 이 에뮬레이터에서 미지원 (실패)

| 영역 | API | 실패 형태 |
|---|---|---|
| Fuel 전체 | 연료량·연비 | `ERROR_NOT_SUPPORTED_PROPERTY` / not in carPropertyConfig (EV 차량이라 당연) |
| EvBattery 전비 | `get/register{Instantaneous,SinceCharge,Cumulative,Current}EvEfficiency` | 동일 |
| Odometer 트립 | `getTrip*DrivingDistance/Time` | 동일 |
| DrivingMode | `getCurrentDrivingModeState` | 동일 (크루즈 상태·목표속도는 지원) |
| Safety | 엔진/브레이크/변속기 오일 레벨, 워셔액, 안전벨트 미착용 경고, 에어백 전개 | 동일 (`checkSeatBuckled`·Telltales는 지원) |
| Tire | `getTirePressure`(TIRE_PRESSURE) | 미지원. `getLowTirePressure`는 137.0(임계값)으로 지원 |
| Toll | ETCS 음성 안내 볼륨 | 미지원 (콘솔의 ETCS Card In/Out 시나리오는 SDK에 대응 API 없음) |
| Window | Smart Glass | 미지원 |
| Light | 안개등, 앰비언트 자동색 | 미지원 |
| HVAC | Max A/C | 미지원 |
| CarInfo | VIN, CRN | 값이 `"invalid"` |
| **차속** | — | **Vehicle SDK에 차속 API 자체가 없음** (VHAL엔 `PERF_VEHICLE_SPEED` 존재) |

## 5. NaviHelper 실측

- `initialize(onSuccess, onFailed)` 오버로드 존재 → 성공 콜백 후 `addListener`.
- 조회(get*Info)는 void, 결과는 리스너로 즉시 옴:
  - `onCurrentLocationInfo` → `CurrentLocationInfo(latitude=37.41, longitude=127.09, heading, address="수정구 시흥동", …)`
  - `onChargerOperatorInfo` → 충전 **사업자** 목록 (`ChargerOperator(id=HD, name=현대자동차(E-pit))` 등 20여 개) — **위치 정보 없음, 충전소 목록 아님**
  - `onBookmarkInfo`(HOME/OFFICE 빈 값), `onRecentDestinationInfo`([]), `onRouteStateInfo`, `onDestinationInfo`, `onWayPointInfo`, `onTBTInfo`
- `onCurrentRoadSpeedLimit(0)`이 경로 없이도 **약 1초마다 반복** 수신.
- `requestRoute(RouteInfo(판교역))` →
  - `onRouteStarted(RouteStartInfo(duration=720, distance=3100, tollFare=0, name=판교역, remainDestinationChargingValue=26704))`
  - `onRouteEnded()`가 `onRouteStarted`와 **같은 시각에** 같이 옴 (이전 경로 종료로 추정)
  - `onNavigationStatus(ROUTE_DRIVE)`, `onRouteStateInfo(naviMode=ROUTE_GUIDING, remainDestinationChargingInt=26704)`
  - `onDrivingInfo(DrivingInfo(destination=DrivingState(name, duration, distance, remainDestinationChargingValue, isChargingStation, isRecommendedChargingStation, maxChargingPower, targetSoc=10, chargerCount, chargingPlan), waypoints))`
  - `onTBTInfo([TBTInfo(TURN_LEFT, 80.0, "기업지원허브 방면"), …])`
- `cancelRoute()` → `onRouteCancelled`, `onNavigationStatus(FREE_DRIVE)`, 남은 거리/잔량 0으로 초기화.
- ⚠️ **`requestRoute`·`cancelRoute`를 호출하면 Navigation 앱이 전면으로 올라온다** (`START u10 … navigation.app/.MainActivity` from navi service). 우리 앱은 백그라운드로 밀림.

## 6. 문서와 다르거나 주의할 점

1. **`VehicleException` 클래스가 없다.** 실제 예외는 난독화된 `ai.pleos.playground.vehicle.callback.exception.a`이고 `a(): ErrorCode`로 코드를 준다 → `VehicleErrors.errorCodeOf()`가 리플렉션으로 처리.
2. **에러 상당수는 평범한 `java.lang.Exception`** (메시지 `java.lang.IllegalArgumentException: property ID is not in carPropertyConfig list…`, `area ID: 0x4 not supported…`, `PropertyNotAvailableException…`)으로 오고 ErrorCode가 없다. `ERROR_NOT_SUPPORTED_PROPERTY`는 주로 `check*Capabilities`에서만 나온다.
3. **Capability 콜백 형태**: 문서의 `onComplete(config, areaMap)`가 아니라 오버로드별로 `(CapabilityConfig)`, `(List<Area>)`, `(CapabilityConfig, List<Area>)`, `(CapabilityConfig, Map<Area, AreaBounds>)`, HVAC 온도는 `(CapabilityConfig, Map, HvacTemperatureConfigArray)`.
4. **Area는 속성마다 다르다.** 도어 잠금은 `DoorArea(ROW_1_LEFT,ROW_1_RIGHT,ROW_2_LEFT,ROW_2_RIGHT)` 하나 + `HOOD`만 지원, HVAC 대부분은 5좌석 묶음 `SeatArea` 하나. 반드시 해당 속성의 `check*Capabilities`가 준 Area로 호출해야 한다 (아니면 `area ID not supported`).
5. **`register*`는 미지원 속성이어도 예외 없이 끝나는 경우가 있다** → 콜백이 영원히 안 옴. `check*` 먼저.
6. `vehicle-data`(ErrorCode 등)가 Vehicle의 **runtime 스코프** 의존성이라 컴파일 타임에 쓰려면 직접 추가해야 한다.
7. API별 필요 퍼미션은 Impl 클래스의 `@RequiresCustomPermission`(RUNTIME 보존, Kotlin `internal`)에 있다. 조회 계열 집계:
   CAR_CLIMATE(HVAC, 핸들열선) / CAR_DOORS / CAR_DRIVE(Brake, DrivingMode, Gear, Odometer, 조향각) / CAR_ENERGY(EvBattery, Fuel) / CAR_INFO(CarInfo, Display, **EvBattery 용량**) / CAR_LIGHTS(Light, TurnSignal) / CAR_MIRRORS / CAR_SAFETY(Safety, Tire) / CAR_SEATS / CAR_TOLL / CAR_WINDOWS(Window, Wiper),
   **PRIVATE_CAR_INFO**(`CarInfo.getCapabilityInfo`), **PRIVATE_CAR_SAFETY**(`Safety` Telltales 3종, `Tire.*TirePressure`).
   에뮬레이터에선 PRIVATE도 normal이라 부여되지만 **실차·앱마켓 심사에서 허용되는지 미확인**.
8. **백그라운드 = 콜백 끊김.** 앱이 전면에서 내려가면 약 10초 뒤 Android cached-app freezer가 프로세스를 얼린다 (`ActivityManager: freezing <pid> com.teamsync.pleos`, `isFrozen=true`). 서비스는 계속 보내지만 앱은 못 받고, 다시 전면에 오면 밀린 이벤트가 몰아서 들어온다. → 내비가 전면인 동안 계속 감시하려면 **Foreground Service 필요**.
9. **DO 미선언 앱은 기어 D에서 즉시 차단** ("주행 중에는 사용할 수 없습니다"). `dumpsys car_service`: `Display id: 0 UXR: DO: false` (P), baseline `Requires DO? true`. 차단 화면이 떠 있어도 프로세스는 얼지 않아 콜백은 계속 옴.
10. 로그 확인: `adb logcat -s 'PLEOS/*'`는 **와일드카드 미지원으로 0줄**. 태그를 나열하거나 grep: `adb logcat -v brief | grep PLEOS/`.
11. SDK 자체 로그 태그: `[Pleos][SDK]a` (예: `onChangeEvent() called with: propIdName = PERF_ODOMETER …`). 서비스 쪽은 `[Pleos]u` (pid = vehicle service).
12. 에뮬레이터 시간대는 `America/Los_Angeles` → 로그 파일명 날짜(`app-YYYYMMDD.log`)가 한국 날짜보다 하루 이를 수 있음.

## 7. 아이디어 후보에 미치는 영향

- **후보 A (EV 충전 코파일럿)**: 데이터는 충분하다 (SOC·배터리 Wh·주행가능거리·충전포트/스위치 + Navi 목적지·남은 거리·**도착 시 잔량(Wh)**).
  하지만 **순정 내비가 이미 도착 시 잔량(%), "왕복 주행 후 예상 %", 추천 충전소·충전 계획(`chargingPlan`, `targetSoc`)을 제공**한다. 단순 "도착 잔량 예측 + 충전소 경유지 추가"는 차별성이 약함.
  `onChargerOperatorInfo`는 사업자 목록뿐이라 충전소 위치/실시간 가용 정보는 외부 API가 필요. 경유지 추가 시 내비가 전면으로 올라오는 것도 UX 설계에 반영해야 함.
- **후보 B (경고등 해석 + 정비 연결)**: Telltales 실시간 수신은 에뮬레이터에서 동작 (SDK `TelltaleType` 95종 중 Virtual Console에서 37종 조작 가능, 상태 OFF/ON/BLINK, color).
  단 **`PRIVATE_CAR_SAFETY`가 3rd-party 앱에 허용되는지가 성패를 가름** → 설명회(10/28) 질문 목록에 추가할 것. 타이어 공기압 실측값은 미지원(임계값만).
- 공통: 주행 중 화면을 쓰려면 DO 선언 + CarUxRestrictions 처리 필수, 백그라운드 감시는 Foreground Service 필요.
