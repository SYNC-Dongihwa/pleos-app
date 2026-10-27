# CLAUDE.md — 내 차 튜토리얼 (Pleos App Market 공모전 출품작)

> compact 이후 **항상 이 파일부터 읽고** 이어서 작업한다. Phase 종료·중요한 발견 시 갱신, **200줄 이내** 유지.
> 블라인드 심사용 repo → 어디에도 실명·소속·개인 계정 정보 금지. 공모전 규정·제출 형식: [docs/contest-guide.md](docs/contest-guide.md). 이전 기록: [docs/history-2026-10.md](docs/history-2026-10.md).

## 1. 한 줄 요약
처음 타는 차에서 비상등·전조등·와이퍼 같은 기능을 직접 조작하면, **Vehicle SDK의 실제 상태 변화로** 조작 완료를 확인해 주는 인터랙티브 튜토리얼. 정차 중(기어 P)에만 진행, 화면 + Pleos TTS 음성 안내.
사용 장면: 신차 출고 첫날, 렌터카·카셰어링 첫 탑승, 초보·장롱면허 운전자.

## 2. 현재 상태
- **Phase 0 (조사·계획) 완료 — 사용자 계획 확인 대기.** 브랜치 `feat/car-tutorial`. 이전 앱(Probe·FGS·내비·알림)은 태그 `archive/pleos-probe-2026-10-22`.
- 다음 할 일 (Phase 1 — 기반): SDK 의존성 정리·초기화, Logger `SYNC/`, `VehicleStateRepository`(capability → 구독 → StateFlow), 정차 게이트, 숨김 개발자 패널. 완료 기준: Virtual Console 조작 → 패널 값 변화 + 로그.
- 일정: Phase 1~2 → 11/7, Phase 3 → 11/13, 접수 마감 11/15 23:59. 10/28 설명회 질문은 §7 끝.

## 3. 결정 사항과 이유
| 날짜 | 결정 | 이유 |
|---|---|---|
| 10-27 | 제품 = "내 차 튜토리얼" | 사용자 확정 |
| 10-27 | 기어는 **AAOS `GEAR_SELECTION`**(CarPropertyManager) 우선, Pleos `Gear`는 쓰지 않음 | 공식 문서: 기어·속도 등 표준 데이터는 AAOS Car API로 (pleos-connect-sdk-api). Pleos `Gear`는 AAR엔 있으나 deprecated·문서에 없음 |
| 10-27 | (확인 대기) 정차 게이트 = 기어 P 필수 + 주차 브레이크는 표시만 | 주차 브레이크는 Pleos API 없음, AAOS `PARKING_BRAKE_ON`은 에뮬레이터에서 항상 false(콘솔에 조작 카드 없음) → 필수로 두면 시연 불가 |
| 10-27 | (확인 대기) 비상등·사이드미러 접기 단계는 목록에서 제외, 대신 상향등·트렁크 추가 | 두 기능 모두 SDK API 없음(§4). 상향등·트렁크는 SDK 구독 + 콘솔 조작 가능 |
| 10-27 | 주행 모드 단계는 유지 | API 있음, 에뮬레이터는 미지원 → 동적 '지원 안 됨' 처리의 실제 예 |
| 10-27 | Logger 태그 `PLEOS/` → `SYNC/` | 요구사항 |
| 10-27 | FGS·NaviHelper·알림·Probe 제거 (git 태그로 보존) | 튜토리얼은 앞화면 전용. 미사용 퍼미션 = 심사 반려 |
| 10-27 | 완료 판정 = 콜백 상태 전이만, 기준값은 get으로 확보 | 타이머·버튼 완료 금지. register 직후 초기값이 안 오는 속성 있음 |

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
- [ ] (Phase 3 선택) `pleos.car.permission.CONTROL_CAR_LIGHTS` — 오프닝 조명
- [ ] (Phase 3 선택) STT 퍼미션 + `android.permission.RECORD_AUDIO` — 음성 명령 (미확인)
- Phase 1에서 **제거**: `CAR_ENERGY, CAR_INFO, CAR_MIRRORS, CAR_SAFETY, CAR_SEATS, CAR_TOLL, PRIVATE_CAR_INFO, PRIVATE_CAR_SAFETY, NAVI_ROUTE, NAVI_ROUTE_SEARCH`, `android.car.permission.CAR_SPEED`, `CAR_EXTERIOR_ENVIRONMENT`, `FOREGROUND_SERVICE*`, `POST_NOTIFICATIONS`

## 6. 빌드·실행·검증 명령
- 빌드·설치 (JDK 17+, `JAVA_HOME`=Android Studio JBR): `./gradlew :app:installDebug` → `adb shell am start -n com.teamsync.pleos/.MainActivity`
- 에뮬레이터: AVD `Pleos_Connect_v2.1.0_API_34` (설치는 README §3). Virtual Console: `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`, 스크립트 조작은 `tools/vc-helper.js`
- 로그 (Phase 1부터 태그 `SYNC/`; 지금 코드는 `PLEOS/`): `adb logcat -v time | grep "SYNC/"` — ⚠️ `-s 'SYNC/*'`는 와일드카드 미지원으로 0줄
- 앱 로그 파일 (AAOS 앱 사용자 = user 10, 날짜는 에뮬레이터 PDT): `adb exec-out run-as com.teamsync.pleos --user 10 cat files/logs/app-YYYYMMDD.log`
- SDK 내부 로그: `[Pleos][SDK]a`(앱 쪽), `[Pleos]u`(Vehicle 서비스), `[GAAS]`(TTS 엔진)
- TTS가 응답 없이 멈추면: `adb shell am force-stop ai.pleos.playground.caas`
- 단위 테스트(Phase 2~): `./gradlew :app:testDebugUnitTest` (test double은 `src/test`에만)

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
- 10/28 설명회 질문: ① `android.car.permission.CAR_POWERTRAIN` 등 AAOS 표준 퍼미션도 사전 승인 대상인지 ② TTS·STT 사용 허용·`TTS_SERVICE` 승인 ③ `CONTROL_CAR_LIGHTS` 승인 가능성 ④ 퍼미션 사전 승인 신청 절차 ⑤ PT 시연 장비(노트북+에뮬레이터) ⑥ DO 인정 방식(사이드로드 미인정).

## 8. 금지 사항
- SDK 연결 실패·미지원일 때 가짜 값으로 진행하는 시뮬레이터·더미 상태·하드코딩 성공 처리 금지 → '연결 안 됨'/'지원 안 됨'을 그대로 표시.
- test double은 `src/test`에만. 앱 코드 경로에 섞지 않는다.
- 타이머·버튼 입력으로 단계 완료 처리 금지 (완료 = 콜백 상태 전이).
- 추측으로 고치지 않는다: 로그 추가 → 재현 → 로그 확인 → 수정.
- 사용하지 않는 퍼미션 선언 금지. 차량 제어(set)는 MVP에서 사용 금지.
- 실명·개인정보를 코드·README·커밋에 넣지 않는다. 키·토큰·키스토어(`*.jks`, `keystore.properties`)·Playground 시크릿 커밋 금지(`local.properties`/환경변수 → `BuildConfig`).
- 커밋은 repo-local 익명 identity로만 (`git config user.name` = `SYNC-Dongihwa`, 전역 설정 사용 금지). 기능 단위로 작게, Phase 끝마다 빌드 통과.
