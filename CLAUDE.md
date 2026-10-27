# CLAUDE.md — 내 차 튜토리얼 (Pleos App Market 공모전 출품작)

> compact 이후 **항상 이 파일부터 읽고** 이어서 작업한다. Phase 종료·중요한 발견 시 갱신, **200줄 이내** 유지.
> 블라인드 심사용 repo → 어디에도 실명·소속·개인 계정 정보 금지. 공모전 규정·제출 형식: [docs/contest-guide.md](docs/contest-guide.md). 이전 기록: [docs/history-2026-10.md](docs/history-2026-10.md).

## 1. 한 줄 요약
처음 타는 차에서 주유구·전조등·김서림 같은 기능을 직접 조작하면, **Vehicle SDK의 실제 상태 변화로** 조작 완료를 확인해 주는 인터랙티브 튜토리얼. 정차 중(기어 P)에만 진행, 화면 + Pleos TTS 음성 안내. **상황별 코스 5개(+전체, 24단계)**.
출발점(사용자 경험): 친구가 신형 쏘나타를 샀는데 주유구 여는 방식이 예전 차와 달라 주유소에서 못 열었음 → 기본 조작뿐 아니라 "필요한 순간에야 찾는" 기능(주유구·보닛·김서림·창문 잠금 등)을 미리 연습. 사용 장면: 차를 바꾼 직후, 신차 출고 첫날, 렌터카·카셰어링 첫 탑승, 초보·장롱면허.

## 2. 현재 상태
- **Phase 1·2·3 완료 10-27** (Phase 3: 오프닝·release 서명·시연 시나리오·README, STT는 선택이라 보류). `feat/car-tutorial`을 10-27 `main`에 fast-forward 병합 → 작업 기준은 `main`. 이전 앱은 태그 `archive/pleos-probe-2026-10-22`.
  - **상황별 코스 (10-27, `feat/tutorial-courses` → `main` 병합)**: 기본 조작 8 · 주유·충전 3(주유구=AAOS `FUEL_DOOR_OPEN`, 충전구=AAOS `EV_CHARGE_PORT_OPEN`, 보닛) · 비·김서림 4 · 추위·공조 6 · 아이·야간 3 + 전체. 엔진은 코스 순서(`order`)로 진행, 결과는 단계 id로 공유, 코스를 처음부터 하면 그 코스 결과만 지움. 홈 = 코스 카드(진행·이 차 미지원 수). 신호 25개(Pleos 23 + AAOS 2).
  - 검증(debug, 콘솔): `FINISH course=fuel [COMPLETED=2, UNSUPPORTED=1]`(주유구 `AAOS FUEL_DOOR_OPEN 속성 없음` → 충전구·보닛 완료) · weather 4/4 · climate 6/6 · family 3/3(창문 잠금 Area 수정 후). 기본 조작 7 + 미지원 1 → 전 코스 완료로 `master=true`. 단위 테스트 41개(10-27 capability 시간 초과 경합 수정 +5).
  - 오프닝: `opening/OpeningDirector` — 첫 실행 또는 홈·튜토리얼 시작 전에 운전석 문 0→1(콜백, 60초 쿨다운) → 환영 화면·음성 → 시작하기. 실내등은 `CONTROL_CAR_LIGHTS` 부여 시에만 `flashCabinLight`(capability 쓰기 확인 → 원래 값 get → ON → 4초 뒤 복구).
  - release: `apk/SYNC_MyCarTutorial_release.apk` (v1.0.0, v3 서명, 서명자 `CN=SYNC, O=SYNC`). **10-27 제출용 APK 커밋**(41MB) — 앱 코드를 바꾸면 다시 빌드해 교체 커밋. 키스토어는 repo 밖 `..\pleos-keystore\sync-release.jks`, 비밀번호는 repo 루트 `keystore.properties`(gitignore)에만 — **분실 시 같은 서명으로 업데이트 불가, 별도 백업 필요**.
  - 시연: `docs/demo-scenario.md` + `tools/vc-demo.js`(`__demo.reset/door/run`) — release APK로 11단계 완주 2분 10초 실측.
- **남은 일**: ① Playground에서 퍼미션 사전 승인 신청(§5, 사용자) ② 10/28 설명회 질문(§7 끝) ③ 시연 영상 녹화(선택, 가산점 — 찍으면 `[수정제출]`) ④ 접수: 접수양식은 repo 밖에서 관리(개인정보 포함), 04 서명 후 이메일 제출 ⑤ (선택) STT 음성 명령 "다음/다시/건너뛰기" — `RECORD_AUDIO`·STT 퍼미션 승인 필요, 미실측.
  - 기반: `AppGraph`(프로세스 단위) → `VehicleStateRepository`(신호 12개, `state` + 합쳐지지 않는 `changes`) · `AaosCarMonitor`(주행 규제·`GEAR_SELECTION`·`PARKING_BRAKE_ON`) · `ParkingGate` · 숨김 개발자 패널(제목 5탭).
  - 튜토리얼: `tutorial/TutorialSteps`(단계 = 데이터, 11단계) → `TutorialEngine`(순수 Kotlin 상태 기계, 효과 Speak/Log/Save 반환) → `TutorialController`(단일 스레드, 정차 복귀 800ms 디바운스, 1초 tick) · `ProgressStore`(DataStore) · `voice/SpeechGuide`(Pleos TTS, 새 안내가 이전 안내를 끊음) · 화면 `TutorialScreen`/`FinishScreen`/`HomeScreen`.
- 일정: 접수 마감 11/15 23:59 (11/14 제출 목표). 10/28 설명회 질문은 §7 끝.

**Phase 3 검증 (10-27)** — 단위 테스트 28개 통과(오프닝 조건 4 추가).
```
SYNC/Opening: OPENING play trigger=first_launch parked=true            (pm clear 후 첫 실행)
SYNC/Vehicle: CAP CABIN_LIGHT_SWITCH accessType=READ_WRITE → CABIN original=OFF → ON → (4초) restored → OFF   (debug, 서비스가 CONTROL_CAR_LIGHTS 확인)
SYNC/Vehicle: VAL DRIVER_DOOR = 1 (live) → SYNC/Opening: OPENING play trigger=driver_door → 시작하기 → STEP low_beam start
SYNC/Opening: OPENING lighting skipped: pleos.car.permission.CONTROL_CAR_LIGHTS not granted   (release)
apksigner: Verified using v3 scheme: true · Signer DN: CN=SYNC, O=SYNC · 퍼미션 = §5 체크리스트 + SDK 병합분
vc-demo.js run (release): 11단계 19:27:33→19:29:42, PAUSE/RESUME 포함, FINISH COMPLETED=10 UNSUPPORTED=1 master=true
```

**Phase 2 검증 (10-27)** — 콘솔만으로 11단계 완주: 완료 10 · 미지원 1(주행 모드) · 배지 "내 차 마스터". 단위 테스트 24개 통과(엔진 15). 그 밖에 확인: 20초 힌트, D 전환 즉시 일시정지 + 음성, 멈춘 동안 변화 무시, P 복귀 0.8초 뒤 재개, 건너뛰기, 강제 종료 후 "이어서 하기 (2 / 11)".
```
SYNC/Tutorial: STEP low_beam start original=OFF (꺼짐) goal='원래 값에서 바뀜'
SYNC/Tutorial: JUDGE low_beam phase=1/2 goal='원래 값에서 바뀜' original=OFF phaseStart=OFF OFF→ON (live) → 충족
SYNC/Tutorial: JUDGE low_beam phase=2/2 goal='원래 값으로 돌아옴' original=OFF phaseStart=ON ON→OFF (live) → 충족
SYNC/Tutorial: JUDGE driver_temperature phase=1/2 goal='1.0 이상 감소' original=22.0 phaseStart=22.0 22.0→21.5 (live) → 미충족
SYNC/Tutorial: HINT driver_temperature phase=1 after 20s without change
SYNC/Gate: gate → NotParked(기어 D) → SYNC/Tutorial: PAUSE … → CHANGE 21.5→21.0 ignored (paused) → (P) RESUME
SYNC/Tutorial: STEP driving_mode UNSUPPORTED: checkDrivingModeStateCapabilities: errorCode=ERROR_NOT_SUPPORTED_PROPERTY
SYNC/Tutorial: FINISH [COMPLETED=10, SKIPPED=0, UNSUPPORTED=1] master=true
```
- 콘솔 조작 순서는 README "Virtual Console로 전 단계 완주". 미확인: 실제 스피커 소리(AudioTrack 기록까지만 확인), 재개 시 새 기준값 적용(단위 테스트만), 완료 화면 "다시 해보기"(단위 테스트만).

**Phase 1 검증 (10-27, 에뮬레이터 + Virtual Console)** — 콘솔 조작마다 개발자 패널 값과 로그가 1초 안에 바뀜. 실행 직후 capability 11개 성공·주행 모드만 `ERROR_NOT_SUPPORTED_PROPERTY`로 '지원 안 됨'. 단위 테스트 9개 통과.
```
SYNC/Car: PROP GEAR_SELECTION = 4 P (get) / PARKING_BRAKE_ON = false (get) / UX initial: requiresDO=false active=0x0
SYNC/Vehicle: CAP DOOR_LOCK accessType=READ_WRITE areas=[[HOOD], [ROW_1_LEFT, ROW_1_RIGHT, ROW_2_LEFT, ROW_2_RIGHT]] use=[4도어 묶음]
SYNC/Vehicle: VAL LOW_BEAM = ON (live) → OFF   | HIGH_BEAM ON→OFF | FRONT_WIPER ON→OFF (콘솔 Low/Off)
SYNC/Vehicle: VAL DRIVER_WINDOW = 4 (콘솔 40%) → 0 | HVAC_AC false→true | DRIVER_TEMPERATURE 22.0→21.0 | STEERING_HEAT 3→0
SYNC/Vehicle: VAL DOOR_LOCK false→true | TURN_SIGNAL LEFT→RIGHT→OFF | DRIVER_DOOR 1→0 | TRUNK 100→0
SYNC/Car: PROP GEAR_SELECTION = 8 D (live) → SYNC/Gate: gate → NotParked(기어 D) → P 복귀 후 gate → Parked
```

## 3. 결정 사항과 이유
| 날짜 | 결정 | 이유 |
|---|---|---|
| 10-27 | 제품 = "내 차 튜토리얼" | 사용자 확정 |
| 10-27 | 기어는 **AAOS `GEAR_SELECTION`**(CarPropertyManager) 사용, Pleos `Gear`는 쓰지 않음 (사용자 승인) | 공식 문서: 기어·속도 등 표준 데이터는 AAOS Car API로 (pleos-connect-sdk-api). Pleos `Gear`는 AAR엔 있으나 deprecated·문서에 없음 |
| 10-27 | 정차 게이트 = 기어 P + 주행 규제 없음. 주차 브레이크는 표시만 (사용자 승인) | 주차 브레이크는 Pleos API 없음, AAOS `PARKING_BRAKE_ON`은 에뮬레이터에서 항상 false(콘솔에 조작 카드 없음) → 필수로 두면 시연 불가. 기어를 못 읽으면 `Unknown` → 시작 안 함 |
| 10-27 | 비상등·사이드미러 접기 단계 제외, 상향등·트렁크 추가 (사용자 승인) | 두 기능 모두 SDK API 없음(§4). 상향등·트렁크는 SDK 구독 + 콘솔 조작 가능 |
| 10-27 | SDK 구독은 `AppGraph`(Application)에서 한 번만, 화면 단위로 해제하지 않음 | §7-4(해제 시 앱 전체 구독 삭제). D로 가려져도 프로세스는 살아 있어 신호 계속 수신 |
| 10-27 | 개발자 패널은 release에도 숨김 진입(제목 5탭)으로 유지 | PT 현장 진단용, 실제 값만 표시. 심사에서 지적되면 `BuildConfig.DEBUG`로 제한 |
| 10-27 | 주행 모드 단계는 유지 | API 있음, 에뮬레이터는 미지원 → 동적 '지원 안 됨' 처리의 실제 예 |
| 10-27 | Logger 태그 `PLEOS/` → `SYNC/` | 요구사항 |
| 10-27 | FGS·NaviHelper·알림·Probe 제거 (git 태그로 보존) | 튜토리얼은 앞화면 전용. 미사용 퍼미션 = 심사 반려 |
| 10-27 | 완료 판정 = 콜백 상태 전이만, 기준값은 get으로 확보 | 타이머·버튼 완료 금지. register 직후 초기값이 안 오는 속성 있음 |
| 10-27 | 단계 문구는 시작 시점 값(original)으로 고름 (이미 켜져 있으면 "꺼 보세요" → "다시 켜 주세요") | 어떤 상태에서 시작해도 전이 + 원복을 관찰할 수 있게 |
| 10-27 | 일시정지는 즉시, 재개는 P 상태가 800ms 유지될 때. 재개 시 phase 기준값을 현재 값으로 다시 잡음 | §7-12 깜빡임 방지. "1도 내려 보세요"는 재개 시점부터 (원복 목표는 original 기준이라 영향 없음) |
| 10-27 | 창문 1단계 = 방향 무관 2 이상 움직임 | 문서에 값 의미(0=닫힘) 없음. 실측상 0=닫힘으로 보이나 확정 아님 |
| 10-27 | 배지 = 지원되는 단계를 모두 완료 (미지원은 감점 아님, 건너뜀이 있으면 없음) | 차종별 지원 차이를 사용자 탓으로 돌리지 않음 |
| 10-27 | `CONTROL_CAR_LIGHTS`는 `src/debug` 매니페스트에만 선언 | release에 미승인 퍼미션 선언 = 심사 탈락. debug에서 실내등 경로를 실제로 검증 |
| 10-27 | APK 파일명 `SYNC_MyCarTutorial_release.apk` (팀명 SYNC) | 규정 `팀명_앱이름_release.apk`, 한글 파일명 도구 호환 문제 회피. 바꾸려면 README·demo 문서 링크도 수정 |
| 10-27 | release 축소·난독화 끔, v1·v2·v3 서명 설정 (AGP가 minSdk 28이라 v3만 넣음) | VehicleErrors가 SDK 예외를 리플렉션으로 읽음. Pleos 요구 = v3 |
| 10-27 | 다시 해보기는 새 결과가 기록될 때까지 이전 결과 유지 | README 화면 캡처 중 발견: 다시 해보기 → 그만하기 시 완료 결과가 지워짐 → 수정·단위 테스트·에뮬레이터 확인 |
| 10-27 | 상황별 코스 + 기능 13개 추가 (사용자 선택) | 주유구 경험에서 출발: 필요한 상황만 바로 연습. 한 줄 24단계는 너무 김 |
| 10-27 | 주유구·충전구는 AAOS `FUEL_DOOR_OPEN`·`EV_CHARGE_PORT_OPEN` (퍼미션 `CAR_ENERGY_PORTS`) | Pleos Fuel 도메인에 주유구 API 없음, Pleos `EvChargePortOpen`은 deprecated(→ Google 속성). 전기차는 주유구가 자동 '지원 안 됨' → 충전구로 |
| 10-27 | 창문 잠금은 뒷좌석을 포함하는 **가장 넓은** Area(전체 창문 묶음) 구독 | 단독 ROW_2_LEFT로 구독하면 묶음 Area(1360) 이벤트가 걸러짐 (§7-16). 실제 버튼도 전체 잠금 |
| 10-27 | 결과 화면의 '지원 안 됨' 사유는 사용자 말로, 원문은 개발자 패널·로그 | capability 오류 원문은 운전자에게 의미 없음 |
| 10-27 | STT 음성 명령 보류 | 선택 항목, 마이크·STT 퍼미션 승인 필요·미실측. 핵심 흐름에 영향 없음 |

## 4. SDK 메모 (출처 + 실측)
출처 약어: **R** = `https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api` (각 도메인 `/<domain>/entry`), **V** = `.../Vehicle/intro`, **E** = `.../Vehicle/vehicle-error-code`, **P** = `.../Vehicle/vehicle-permission`, **TTS** = `https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro`. 시그니처는 AAR(`Vehicle-2.3.2.aar` javap)로 대조함. [실측] = Pleos Connect Emulator 2.1.0 + Virtual Console.

**공통 (V, R)**: `Vehicle(context)` → `initialize()` → `getLight()` 등 도메인 객체 → … → `release()`. 모두 IPC 비동기(`onComplete`/`onFailed: (Exception)->Unit`), 콜백 스레드 = binder. 리스너는 `VehicleListener`(`onFailed(e)`) 상속.
- capability 콜백은 `(CapabilityConfig, List<Area>|Map<Area,…>)` 오버로드 사용 (1인자형은 2.3.2부터 deprecated). `CapabilityConfig.accessType ∈ {NONE, READ, WRITE, READ_WRITE}`.
- **Area는 반드시 capability가 준 것**으로 호출 (속성마다 다름). 에러: E의 `ErrorCode` 9종, 실제 예외 클래스는 §6 참고.

| 단계 | API (구독 / 리스너 콜백 / 값) | 퍼미션 | 출처 | 실측 · 콘솔 카드 |
|---|---|---|---|---|
| 기어 | AAOS `CarPropertyManager` `GEAR_SELECTION` | `android.car.permission.CAR_POWERTRAIN` | pleos-connect-sdk-api 정책 | **미실측**(Pleos `Gear.registerGearSelection`→`onSelected(VehicleGear)`은 실측 OK) · Gear Selection |
| 주차 브레이크 | AAOS `PARKING_BRAKE_ON` | `CAR_POWERTRAIN` | 문서에 Pleos API 없음 (V 표는 있다고 하나 R엔 없음) | 읽기 OK, **항상 false** · 카드 없음 |
| 비상등 | **API 없음** (`TurnSignalValue`=OFF/RIGHT/LEFT, AAOS `HAZARD_LIGHTS_STATE`는 signature) | — | R turn-signal | 콘솔 Hazard On → TurnSignal `OFF` |
| 전조등 | `Light.registerLowBeamLightState(LightStateListener)` → `onLightStateUpdated(LightState?)` {OFF, ON, DAYTIME_RUNNING}. 스위치는 `getLowBeamLightSwitch`(get만) {OFF, ON, DAYTIME_RUNNING, AUTOMATIC} | `CAR_LIGHTS` | R light | 구독 OK · Exterior Lights Mode(Off/Auto/Tail/Low) |
| 상향등 | `registerHighBeamLightState` (같은 리스너) | `CAR_LIGHTS` | R light | 구독 OK(초기값) · High Beam |
| 방향지시등 | `TurnSignal.registerTurnSignalState` → `onTurnSignalStateUpdated(TurnSignalValue?)` | `CAR_LIGHTS` | R turn-signal | LEFT 수신 OK · Turn Signal State |
| 와이퍼 | `Wiper.registerWiperState(WindowArea(FRONT_WINDSHIELD), WiperStateListener)` → `onWiperStateUpdated(WiperState?)` {OTHER, OFF, ON, SERVICE}. 스위치는 get만 | `CAR_WINDOWS` | R wiper | Auto → `ON` · Windshield Wiper Switch(Off/Auto/Low/High) |
| 창문 | `Window.registerWindowPosition(WindowArea(ROW_1_LEFT), WindowPositionListener)` → `onWindowPositionUpdated(Int?)` | `CAR_WINDOWS` | R window | **콘솔 40% → 4 (범위 0..10)**, 0=닫힘 추정 · Window Position |
| 사이드미러 접기 | **API 없음** (SideMirror = 열선만) | — | R side-mirror | 콘솔 카드도 없음 |
| A/C · 온도 | `HVAC.registerHvacAcState(SeatArea, HvacAcStateListener)`→`onHvacAcStateUpdated(Boolean?)` / `registerHvacTemperature(SeatArea(ROW_1_LEFT), HvacTemperatureListener)`→`onHvacTemperatureUpdated(Float?)` ℃ | `CAR_CLIMATE` | R h-v-a-c | A/C area = 5좌석 묶음 1개, 온도 22→23 OK · Air Conditioning, Target Temperature(0.5℃ 단위) |
| 핸들 열선 | `SteeringWheel.registerHvacSteeringWheelHeatState` → `onHvacSteeringWheelHeatStateUpdated(Int?)` 0..3 | `CAR_CLIMATE` | R steering-wheel | High → 3 · Steering Wheel Heat |
| 도어 잠금 | `Door.registerDoorLockState(DoorArea, DoorLockStateListener)` → `onDoorLockStateUpdated(Boolean?)` true=잠김 | `CAR_DOORS` | R door | **Area = 4도어 묶음 1개만**, Unlock→false · Door Lock |
| 트렁크 / 운전석 문 | `registerDoorPosition(DoorArea(REAR) / (ROW_1_LEFT), DoorPositionListener)` → `onPositionUpdated(Int?)` | `CAR_DOORS` | R door | 트렁크 0..100, 도어 0..1 · Trunk, Door Position |
| 주행 모드 | `DrivingMode.registerDrivingModeState` → `onDrivingModeStateUpdated(DrivingModeType?)` {NORMAL, ECO, SPORT}, set 없음 | `CAR_DRIVE` | R driving-mode | **에뮬레이터 미지원** (`ERROR_NOT_SUPPORTED_PROPERTY`) · 카드 없음 |
| 정차 판정 보조 | AAOS `CarUxRestrictionsManager` (`useLibrary("android.car")`) | 없음 | driving-restriction-development-guide | P: DO 불필요 / D·N: `0x50` |
| 주유구 / 충전구 | AAOS `FUEL_DOOR_OPEN` / `EV_CHARGE_PORT_OPEN` (Boolean, area 0) — `getCarPropertyConfig`가 null이면 미지원 | `android.car.permission.CAR_ENERGY_PORTS` (normal) | pleos-connect-sdk-api 정책 | 주유구 **config 없음**(EV) / 충전구 false→true→false · Charge Port |
| 보닛 | `Door.registerDoorPosition(DoorArea(HOOD))` 0..100 | `CAR_DOORS` | R door | 0→100→0 · Trunk/Frunk(**frunk**) |
| 김서림·뒷유리 열선 | `HVAC.registerHvacDefrosterState(WindowArea(FRONT/REAR_WINDSHIELD), HvacDefrosterStateListener)` Boolean | `CAR_CLIMATE` | R h-v-a-c | OK · Defroster(front/rear) |
| 뒷유리 와이퍼 | `registerWiperState(WindowArea(REAR_WINDSHIELD))` | `CAR_WINDOWS` | R wiper | OK · Wiper Switch(rear) |
| 내기 순환 / 오토 / 바람 | `registerHvacRecirculationState`(리스너 메서드 `onHvacAutoRecirculationStateUpdated`) / `registerHvacAutoState` / `registerHvacFanSpeed` 1..8 | `CAR_CLIMATE` | R h-v-a-c | OK · Air Recirculation / HVAC Auto / Fan Speed(0..8) |
| 시트 열선 | `Seat.registerHvacSeatTemperature(SeatArea(ROW_1_LEFT), HvacSeatTemperatureListener)` **-3..3(음수=통풍)** | **`CAR_SEATS`** | R seat | 0→2→0 · Seat Temperature(V3..H3) |
| 창문 잠금 | `Window.registerWindowLockState(WindowArea(묶음), WindowLockStateListener)` | `CAR_WINDOWS` | R window | 묶음 Area만 이벤트 · Window Lock(**all**) |
| 실내등 / 독서등 | `Light.registerCabinLightState` / `registerReadingLightState(SeatArea(ROW_2_LEFT))` LightState | `CAR_LIGHTS` | R light | OK (에뮬레이터 연동 이슈 §7-17) · Cabin Lights / Reading Lights |

**TTS (TTS 문서, 3.3.1)**: `TextToSpeech(context, Mode.ON_DEVICE)` → `initialize(OnInitializeListener{onSuccess; onFailure(ErrorType)})` → `addEventListener(EventListener{onReady,onStart,onDone,onStop,onError,onUpdatedRms})` → `setLocale` → `speak(text, isFinal)` / `stop()` / `release()`. ON_DEVICE는 앱 등록·네트워크 불필요. 실측 규칙은 §6.
**STT (Phase 3 선택)**: `ai.pleos.playground:SpeechToText:3.3.1`, CaaS에 온디바이스 구현 있음 — **미실측**, 필요 퍼미션 미확인.
**제어(set)**: Reference엔 `setCabinLightSwitch` 등 존재(`CONTROL_CAR_*`), SDK 개요는 Vehicle Control "준비 중" → MVP는 조회·구독만. Phase 3 조명 연출은 `CONTROL_CAR_LIGHTS` 승인 시에만.

## 5. 퍼미션 체크리스트 (Playground 신청은 사용자가 직접)
Vehicle SDK 퍼미션은 **App Market 사전 승인된 것만 선언 가능** (승인 안 된 퍼미션 선언 = 심사 탈락). 형식 `pleos.car.permission.*`, 에뮬레이터에선 normal로 자동 부여(실차 `PRIVATE_*`는 signature|privileged) — P.
- [ ] `pleos.car.permission.CAR_LIGHTS` — 전조등·상향등·방향지시등
- [ ] `pleos.car.permission.CAR_WINDOWS` — 창문·와이퍼
- [ ] `pleos.car.permission.CAR_CLIMATE` — A/C·온도·핸들 열선
- [ ] `pleos.car.permission.CAR_DOORS` — 도어 잠금·트렁크·운전석 문
- [ ] `pleos.car.permission.CAR_DRIVE` — 주행 모드
- [ ] `pleos.car.permission.TTS_SERVICE` — 음성 안내 (SDK `ApiChecker`가 확인, 없으면 SecurityException [실측])
- [ ] `android.car.permission.CAR_POWERTRAIN` — AAOS 기어·주차 브레이크 (AAOS 표준 퍼미션, 사전 승인 대상인지 **미확인**)
- [ ] `pleos.car.permission.CAR_SEATS` — 시트 열선 (10-27 추가)
- [ ] `android.car.permission.CAR_ENERGY_PORTS` — AAOS 주유구·충전구 (10-27 추가, AAOS 표준 퍼미션 사전 승인 대상인지 미확인)
- [ ] (Phase 3 선택) `pleos.car.permission.CONTROL_CAR_LIGHTS` — 오프닝 조명
- [ ] (Phase 3 선택) STT 퍼미션 + `android.permission.RECORD_AUDIO` — 음성 명령 (미확인)
- Phase 1에서 **제거 완료**: `CAR_ENERGY, CAR_INFO, CAR_MIRRORS, CAR_SAFETY, CAR_SEATS, CAR_TOLL, PRIVATE_CAR_INFO, PRIVATE_CAR_SAFETY, NAVI_ROUTE, NAVI_ROUTE_SEARCH`, `android.car.permission.CAR_SPEED`, `CAR_EXTERIOR_ENVIRONMENT`, `FOREGROUND_SERVICE*`, `POST_NOTIFICATIONS`
- 매니페스트 병합으로 SDK가 추가하는 것 (우리가 선언 안 함, 신청 대상 아님 [추정]): `ai.pleos.playground.service.vehicle.VEHICLE_BINDING`(Vehicle), `android.permission.INTERNET`(TextToSpeech·universal-access). 확인: `app/build/outputs/logs/manifest-merger-debug-report.txt`

## 6. 빌드·실행·검증 명령
- 빌드·설치 (JDK 17+, `JAVA_HOME`=Android Studio JBR): `./gradlew :app:installDebug` → `adb shell am start -n com.teamsync.pleos/.MainActivity`
- 에뮬레이터: AVD `Pleos_Connect_v2.1.0_API_34` (설치는 README §3). Virtual Console: `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`, 스크립트 조작은 `tools/vc-helper.js`
- 로그 (태그 `SYNC/Vehicle`·`SYNC/Car`·`SYNC/Gate`·`SYNC/UI`·`SYNC/App`): `adb logcat -v time | grep "SYNC/"` — ⚠️ `-s 'SYNC/*'`는 와일드카드 미지원으로 0줄. 키워드: `CAP`(capability), `REG`/`UNREG`, `VAL`(값 변경), `FAIL`, `UNSUPPORTED`, `PROP`(AAOS 속성), `UX`, `gate →`
- 개발자 패널: 홈 제목 "내 차 튜토리얼"을 3초 안에 5번 탭 (adb: `uiautomator dump`로 제목 좌표 → `input tap` ×5를 한 `adb shell` 안에서)
- 앱 로그 파일 (AAOS 앱 사용자 = user 10, 날짜는 에뮬레이터 PDT): `adb exec-out run-as com.teamsync.pleos --user 10 cat files/logs/app-YYYYMMDD.log`
- SDK 내부 로그: `[Pleos][SDK]a`(앱 쪽), `[Pleos]u`(Vehicle 서비스), `[GAAS]`(TTS 엔진)
- TTS가 응답 없이 멈추면: `adb shell am force-stop ai.pleos.playground.caas`
- 단위 테스트: `./gradlew :app:testDebugUnitTest` (test double은 `src/test`에만)
- release: `./gradlew :app:assembleRelease` (repo 루트 `keystore.properties` 필요) → `cp app/build/outputs/apk/release/app-release.apk apk/SYNC_MyCarTutorial_release.apk` → 서명 확인 `"$ANDROID_HOME/build-tools/36.1.0/apksigner" verify --verbose --print-certs <apk>`. debug→release 전환 시 서명이 달라 `adb uninstall com.teamsync.pleos` 먼저
- 오프닝 재확인: `adb shell pm clear --user 10 com.teamsync.pleos` 후 실행

## 7. 알려진 이슈 (문서 ≠ 실측, 근거 포함)
1. **Gear**: 문서엔 Gear 도메인·`getGear()` 없음 ↔ AAR 2.3.2엔 `api.Gear` 존재, 컴파일 경고 "deprecated, use the Google GEAR_SELECTION property", 실측 `GEAR_PARK`/`GEAR_DRIVE` 수신.
2. **주차 브레이크**: V 도메인 표엔 Brake에 "주차 브레이크" ↔ Reference·AAR엔 `BrakePedalStatus`만. AAOS `PARKING_BRAKE_ON`은 콘솔 텔테일 ParkingBrake/EPB On이어도 `false` (로그 `PLEOS/UX: AAOS PARKING_BRAKE_ON = false`, 10-26).
3. **예외**: 문서의 `VehicleException`은 public 클래스로 없음 → 난독화 `callback.exception.a`의 `a(): ErrorCode`(`VehicleErrors.errorCodeOf`). 상당수 실패는 ErrorCode 없는 `java.lang.Exception`(메시지에 `not in carPropertyConfig`, `area ID … not supported`).
4. **리스너 해제**: 같은 속성 리스너 하나를 unregister하면 **앱 전체 구독이 서비스에서 삭제**됨 → 속성당 등록 주체는 Repository 하나.
5. `register*`는 미지원 속성이어도 예외 없이 끝나기도 함(콜백 영영 없음) → 반드시 `check*` 먼저. 일부 속성은 등록 직후 초기값이 안 옴 → 기준값은 `get*`.
6. 주기 콜백(배터리 Wh·주행거리·조향각 등)은 같은 값이 1~3초마다 반복 → 전이 판정은 값이 바뀔 때만.
7. 창문: 콘솔은 0~100% ↔ SDK 값 0..10. 문서에 값 의미(0=닫힘) 없음.
8. 도어 잠금 Area는 4도어 묶음 하나뿐(개별 도어 Area는 `area ID not supported`).
9. 기어 D가 되는 즉시(0 km/h) 사이드로드 앱은 "주행 중에는 사용할 수 없습니다"로 가려짐(DO meta-data 무시). 가려져도 프로세스·콜백은 유지.
10. TTS: `addEventListener`는 `initialize` 성공 **후**(이전이면 SDK lateinit 크래시) / 로캘 `Locale.KOREA`(`ko`만 주면 엔진 오류 후 CaaS 멈춤) / `speak(text, isFinal=true)`(false면 다음 조각 대기로 안 끝남) / repo 필터는 `ai\.pleos\.playground(\..*)?`(TTS가 `security:universal-access`를 끌어옴).
11. 앱이 백그라운드로 가면 ~10초 뒤 freezer로 콜백 끊김 (튜토리얼은 앞화면 전용이라 FGS 안 씀).
12. D→P 복귀 시 `GEAR_SELECTION=P`가 주행 규제 해제보다 ~6ms 먼저 와서 게이트가 잠깐 `NotParked(주행 규제 중)`를 거친 뒤 `Parked` (로그 10-27 18:50:42.017~.024) → Phase 2 일시정지/재개 판정은 짧게 디바운스.
13. 콘솔 "Trunk / Frunk Position" 기본 선택은 frunk(=HOOD) → trunk를 고르지 않으면 `TRUNK` 신호가 안 바뀜. 콘솔 카드 라벨(예: Gear Selection의 "Park")은 갱신이 늦을 수 있어 실제 반영은 로그로 확인.
14. Compose Material3 버튼에 `contentColor`를 안 주면 `darkColorScheme().onPrimary`(어두운 보라)가 글자색 → 파란 배경에서 안 읽힘. `BigButton`은 흰색 지정 (10-27 화면 확인).
15. Pleos TTS `stop()` 직후 `speak()`해도 새 안내는 정상 재생 (로그 `event onStop` → `onReady` → `onStart`, 10-27).
16. 창문 잠금: 콘솔 Window Lock(all)은 `WINDOW_LOCK areaId=1360`(전체 묶음)으로 발행, 단독 ROW_2_LEFT 구독은 SDK가 걸러 콜백 없음. rear_left 선택은 이벤트 자체가 없음 (로그 `[Pleos][SDK]a onChangeEvent … areaId = 1360`, 10-27).
17. 에뮬레이터: 콘솔 실내등 On 시 뒷좌석 독서등도 ON, 독서등 카드 On/Off 반영이 엇갈려 보임 → 앱은 시작 시점 값 기준 전이로 판정해 진행에 문제없음. `FUEL_DOOR_OPEN`은 config 자체가 없음(EV).
18. 콘솔 버튼을 눌러도 가끔 발행되지 않는다 (10-27: 기본 조작 첫 Low beam 클릭 때 Vehicle 서비스 로그 `LOW_BEAM_LIGHTS_STATE` 0건, 다시 누르니 정상). 앱이 안 넘어가면 먼저 `adb logcat | grep "[Pleos]u"`로 서비스에 이벤트가 왔는지 확인.
19. 방향지시등을 켜면 시스템(`ai.umos.car.evs`, user 0이 실행)이 **후측방 카메라 화면**(`CarEvsBlindSpotCameraPreviewActivity`)을 앱 위에 띄운다 — 실차 BVM 재현. 앱은 가려진 채로도 판정 정상(10-27 로그). 에뮬레이터에선 꺼진 뒤에도 안 닫히고 `drivingview`·evs ANR + 부하 → 신호 전달 수십 초 지연. 녹화 때만 `pm disable-user --user 0/10 ai.umos.car.evs`, 끝나면 `pm enable`.
20. **(10-27 수정)** capability 응답이 5초 시간 초과와 같은 순간에 오면 `armCapabilityTimeout`이 Supported를 Unsupported로 덮어썼다(RECIRCULATION: `CAP` 1ms 뒤 `UNSUPPORTED`, 부팅 직후 서비스 응답 ~13초일 때). → 확인·변경을 `_state.update` 한 번으로(`timedOut()`: Checking일 때만), `CapabilityTimeoutTest` 5개. 늦게 온 응답은 원래대로 Supported로 복구.
- 10/28 설명회 질문: ① `android.car.permission.CAR_POWERTRAIN` 등 AAOS 표준 퍼미션도 사전 승인 대상인지 ② TTS·STT 사용 허용·`TTS_SERVICE` 승인 ③ `CONTROL_CAR_LIGHTS` 승인 가능성 ④ 퍼미션 사전 승인 신청 절차 ⑤ PT 시연 장비(노트북+에뮬레이터) ⑥ DO 인정 방식(사이드로드 미인정).

## 8. 금지 사항
- SDK 연결 실패·미지원일 때 가짜 값으로 진행하는 시뮬레이터·더미 상태·하드코딩 성공 처리 금지 → '연결 안 됨'/'지원 안 됨'을 그대로 표시.
- test double은 `src/test`에만. 앱 코드 경로에 섞지 않는다.
- 타이머·버튼 입력으로 단계 완료 처리 금지 (완료 = 콜백 상태 전이).
- 추측으로 고치지 않는다: 로그 추가 → 재현 → 로그 확인 → 수정.
- 사용하지 않는 퍼미션 선언 금지. 차량 제어(set)는 MVP에서 사용 금지.
- 실명·개인정보를 코드·README·커밋에 넣지 않는다. 키·토큰·키스토어(`*.jks`, `keystore.properties`)·Playground 시크릿 커밋 금지(`local.properties`/환경변수 → `BuildConfig`).
- 커밋은 repo-local 익명 identity로만 (`git config user.name` = `SYNC-Dongihwa`, 전역 설정 사용 금지). 기능 단위로 작게, Phase 끝마다 빌드 통과.
- 저장소는 **공개**(10-27, 블라인드 심사용으로 새로 만듦). 푸시는 익명 계정 `anonymity-repo`로만 — GitHub Activity 화면에 푸시한 계정이 공개됨. repo-local `credential.helper`가 `gh auth token --user anonymity-repo`를 써서 gh 활성 계정과 무관하게 그 계정으로 푸시된다(다른 PC에서는 같은 설정 필요). `gh` API로 저장소를 바꿀 때는 활성 계정을 먼저 확인.
