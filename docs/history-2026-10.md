# 2026-10-22~26 작업 기록 (아이디어 확정 전, CLAUDE.md에서 이동)

> 원래 CLAUDE.md §4~§9. "내 차 튜토리얼" 확정(2026-10-27) 이전의 SDK 실측·아이디어·진행 로그. 당시 코드는 git 태그 `archive/pleos-probe-2026-10-22`.

## 4. SDK 사용 가능 상태 ⚠️ (가장 중요)

| SDK | 상태 (2026-10-22 문서 기준) |
|---|---|
| **Vehicle State (조회)** | ✅ 신청 없이 사용 가능 |
| **NaviHelper** | ✅ 신청 없이 사용 가능 |
| FusedLocation | ⛔ 준비 중(Coming Soon) — 신청 불가 |
| TextToSpeech | ⛔ 문서상 준비 중 → **[실측] Maven 3.3.1 공개, 에뮬레이터에서 온디바이스 한국어 음성 동작** (주행 중·백그라운드 FGS에서도 재생). 공모전 사용 허용 여부는 설명회 질문 ⑨ |
| SpeechToText | ⛔ 문서상 준비 중 → Maven 3.3.1 공개, CaaS에 온디바이스 구현 있음(미실측) |
| LLM (문서에 없음) | Maven 2.1.3.2 공개. **[실측] 에뮬레이터 온디바이스 LLM은 스텁(요청 무시, 콜백 없음)** → 사용 불가. 서버 모드는 Playground 자격 필요(미시험) |
| Vehicle Control (제어/set) | ⛔ 준비 중 |

→ **핵심 기능은 Vehicle State + NaviHelper로만 설계한다.** 준비 중 SDK에 의존하는 기능은 넣지 않는다 (상태 바뀌면 §9에 기록 후 재검토).
→ TTS는 **전달 수단(부가)** 으로만 사용: 코어 판단은 Vehicle·NaviHelper, TTS가 없으면 화면만 표시. 사용 규칙·수치는 [docs/ondevice-ai-design.md](docs/ondevice-ai-design.md).

### Maven / 의존성
```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}
// app/build.gradle.kts
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:vehicle-data:1.1.11") // ErrorCode 등. Vehicle이 runtime 스코프로만 노출해서 직접 추가 (실측)
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1") // 문서상 준비 중, Maven 공개본. 하위 그룹 ai.pleos.playground.security 도 끌어옴
}
```
> settings의 repo 필터는 `includeGroupByRegex("ai\\.pleos\\.playground(\\..*)?")` — `includeGroup("ai.pleos.playground")`만 두면 TTS의 `security:universal-access`를 못 찾음 (실측).
> 실제 적용: `gradle/libs.versions.toml` + `app/build.gradle.kts`. **SDK 실측 결과는 `docs/sdk-probe-results.md`가 이 섹션보다 우선.**

### Vehicle SDK 사용 패턴
- `val vehicle = Vehicle(context); vehicle.initialize()` → `vehicle.getXxx()`로 도메인 객체 → 사용 후 `vehicle.release()`
- API 4종: **Capability**(`checkXXXCapabilities`), **Get**(`onComplete(값)`), **Set**, **Callback**(`registerXxx`/`unregisterXxx`, 리스너 `onXxxUpdated` + `onFailed`)
  - 실측 시그니처: `checkX(onComplete: (CapabilityConfig) -> Unit | (List<Area>) | (CapabilityConfig, List<Area>) | (CapabilityConfig, Map<Area, AreaBounds<T>>), onFailed: (Exception) -> Unit)` — 문서의 `onComplete(config, areaMap)` 형태 아님
  - Get: `getX([area], onComplete: (T) -> Unit, onFailed: (Exception) -> Unit)` / 리스너 등록: `registerX([area], listener)`
- 모두 IPC 비동기 → `onComplete` / `onFailed(Exception)`. **콜백 끝나기 전에 `release()` 금지**. 콜백은 binder 스레드에서 옴
- 호출 전 `checkXXXCapabilities`로 지원 여부 확인 + **Area는 그 capability가 준 것만 사용** (속성마다 다름, 예: 도어 잠금은 4도어 묶음 Area 하나). `register*`는 미지원이어도 예외 없이 끝나기도 함
- 도메인(실측 20개): Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, **Gear**, HVAC, Light, Odometer, Safety(경고), Seat, SideMirror, SteeringWheel, Tire, Toll, TurnSignal, Window, Wiper — **차속 API는 없음**
- EvBattery 예: `getEvBatteryStateOfCharge`, `registerEvBatteryStateOfCharge`, `get/registerInstantaneousEvEfficiency`, `getCumulativeEvEfficiency`, `getSinceChargeEvEfficiency`, `getEvChargeSwitchState`, `getEvChargeCurrentDrawLimit` …
- 퍼미션: 매니페스트에 `pleos.car.permission.{상수}`. 조회 `CAR_*`, 제어 `CONTROL_CAR_*`
  - Door `CAR_DOORS` / Window·Wiper `CAR_WINDOWS` / HVAC `CAR_CLIMATE` / DrivingMode·Brake·Odometer `CAR_DRIVE` / EvBattery·Fuel `CAR_ENERGY` / CarInfo·Display `CAR_INFO` / Light·TurnSignal `CAR_LIGHTS` / SideMirror `CAR_MIRRORS` / Safety·Tire `CAR_SAFETY` / Seat `CAR_SEATS` / Toll `CAR_TOLL`
  - API별 정확한 퍼미션은 Impl 클래스(`api.implement.XxxImpl`) 메서드의 `@RequiresCustomPermission` (RUNTIME 보존, Kotlin internal) — Probe 화면에 API별로 표시됨. 쓰지 않는 퍼미션 선언 금지(앱마켓 심사 반려 사유)
  - 실측 예외: EvBattery 용량은 `CAR_INFO`, SteeringWheel 각도는 `CAR_DRIVE`·열선은 `CAR_CLIMATE`, **경고등(Telltales)·타이어 공기압은 `PRIVATE_CAR_SAFETY`**, `CarInfo.getCapabilityInfo`는 `PRIVATE_CAR_INFO`
- 에러: 문서의 `VehicleException`은 **2.3.2에 public 클래스로 없음**. 난독화된 `callback.exception.a`의 `a(): ErrorCode` → `VehicleErrors.errorCodeOf(e)` 사용. 상당수 실패는 ErrorCode 없는 평범한 `java.lang.Exception`(메시지에 원인)으로 옴
  - `ERROR_NOT_SUPPORTED_PROPERTY/AREA/VALUE_TYPE`, `ERROR_SECURITY_PERMISSION`, `ERROR_SERVICE_CONNECTION_FAILURE`, `ERROR_INTERNAL_SERVICE`, `ERROR_DRIVING_RESTRICTED`, `ERROR_EMPTY_RESULT`, `ERROR_UNDEFINED`

### NaviHelper SDK 사용 패턴
- `NaviHelper` 생성 → `initialize()` → `addListener(NaviHelperEventListener)` → 요청/조회 → `removeListener()` + `release()`
- 기능: 목적지 설정·확인·취소, 경로 옵션 변경·재탐색, 경유지 추가·조회·제거, 경유지 포함 경로 요청, 경로/주행 이벤트, 내비 정보 조회, Navigation 화면 요청
- 퍼미션:
  - `pleos.car.permission.NAVI_ROUTE` — `get*Info()`, `requestTrip()`
  - `pleos.car.permission.NAVI_ROUTE_SEARCH` — `requestRoute()`, `requestRouteWithWaypoints()`, `cancelRoute()`, `requestReRoute()`, `addWaypoint()`, `removeWaypoint()`, `changeRouteOption()`
  - `pleos.car.permission.NAVI_CUSTOM_MAP` — `requestNavigationScreen()`
- 리스너 콜백 (패키지 `ai.pleos.playground.navi.helper.listener.NaviHelperEventListener`):
  - 경로: `onRouteStarted(RouteStartInfo)`, `onRoutingCompleted`, `onRouteCancelled`, `onReRouteEnded`, `onRouteOptionChanged`
  - 목적지/경유지: `onDestinationChanged`, `onDestinationArrived`, `onWaypointChanged`, `onWayPointArrived`
  - 정보: `onBookmarkInfo`, `onBookMarkChanged`, `onRecentDestinationInfo`, `onCurrentLocationInfo`, `onDestinationInfo`, `onWayPointInfo`, `onRouteStateInfo`, `onTBTInfo`, **`onChargerOperatorInfo`**, `onTripInfo`, `onNavigationStatus`, `onDrivingInfo`, **`onCurrentRoadSpeedLimit`**, `onCameraAlert`, `onCautionPointInfo`
  - 오류: `onError(NaviErrorCode)` (`ai.pleos.playground.navi.constants.NaviErrorCode`)
- 실제 전달 이벤트는 Navigation 앱 구현/상태에 따라 다름 → 에뮬레이터에서 어떤 이벤트가 오는지 **로그로 실측**할 것
  - 실측(2026-10-22): `initialize(onSuccess, onFailed)` 오버로드 있음. `onRouteEnded()` 콜백도 있음(문서 목록에 없음). `requestRoute`/`cancelRoute` 호출 시 **Navigation 앱이 전면으로 올라옴**. `onChargerOperatorInfo`는 사업자 id/name 목록뿐(위치 없음). 상세는 `docs/sdk-probe-results.md` §5

---

## 5. 주행 규제 (설계 필수 조건)

- 앱은 AAOS `CarUxRestrictionsManager.registerListener()` → `onUxRestrictionsChanged(CarUxRestrictions)`로 상태 수신, `isRequiresDistractionOptimization()` + `getActiveRestrictions()` 비트로 기능 제한 적용
- **DO(Distraction Optimization) 미선언 앱은 주행 중 사용 불가** (주차 중만 가능). 주행 중 화면이 필요하면 DO 선언 + 제한 직접 처리
  - 실측: 에뮬레이터에서 **기어 D가 되는 즉시(0 km/h여도)** "주행 중에는 사용할 수 없습니다"로 차단. 차단 중에도 프로세스는 살아 있어 SDK 콜백은 계속 옴
  - 실측: 앱이 백그라운드로 가면 ~10초 뒤 cached-app freezer가 프로세스를 얼려 SDK 콜백 수신 중단 → 백그라운드 감시는 Foreground Service 필요 → **구현·검증 완료** (`MonitorService`, type=specialUse, 백그라운드 36초에도 `curProcState=4`, freeze 없음)
  - DO 선언: 홈(`MainActivity`)에 `<meta-data android:name="distractionOptimized" android:value="true"/>` 선언함. **실측: 사이드로드 앱은 인정 안 됨** — `CarPackageManager.isActivityDistractionOptimized=false`, Pleos 홈 화면이 앱 영역에 차단 오버레이. 에뮬레이터엔 앱마켓 없음(허용 목록=서명 일치 시스템 앱). 마켓 설치 시 인정되는지 [미검증] → 설명회 질문 ⑥
  - 실측: **서드파티 heads-up 미표시** — Pleos `NOTIFICATION_INJECTOR`가 privileged/platformSigned 아니면 띄우지 않음 → 설명회 질문 ⑦. 결국 에뮬레이터에선 D 동안 서드파티 앱이 운전자에게 보여줄 경로가 없음 (`docs/common-foundation.md` §3). **단, Pleos TTS 음성은 D + 다른 앱 전면에서도 재생됨 [실측] → 주행 중 전달은 음성으로** (`docs/ondevice-ai-design.md`)
  - `useLibrary("android.car")`로 컴파일(런타임은 기기 car framework apex에 있어 `<uses-library>` 불필요) — 적용됨
- 주행 중 제한(한국 = "그외 지역", **실측 일치: D/N에서 `0x50 NO_VIDEO|NO_SETUP`**): 미디어 로그인/설정/검색, 차량 설정(도어·시트), 동영상, QR 로그인, 대용량 텍스트 ⛔ / 키보드·다이얼패드·검색·내비 검색은 허용 표기 (단, 디자인 가이드는 주행 중 키보드 입력 지양)
- 플래그: NO_DIALPAD(1), NO_FILTERING(2), LIMIT_STRING_LENGTH(4), NO_KEYBOARD(8), NO_VIDEO(16), LIMIT_CONTENT(32), NO_SETUP(64)
- 디자인 원칙: 첫 화면에 핵심 기능, 얕은 메뉴, 큰 터치 타깃, 고대비, 최소 텍스트, 자동 애니메이션 최소화, 게임·웹서핑 유도 금지
- **앱 카테고리: Mobility Services** 권장 — 주행 중 "차량 관리 서비스, 현재 위치 기반 즉각 정보, 전기차 충전소 찾기 및 경유지 설정, 주유소/주차장 탐색"이 제한적으로 허용됨

---

## 6. 아이디어 (⚠️ 미확정 — 결정 시 §9에 기록하고 이 섹션 갱신)

> **1차 후보 4안: [docs/idea-candidates.md](docs/idea-candidates.md)** — 피드백: 방향1은 테슬라에 유사 기능, 방향2는 너무 감성적.
> **2차 후보 4안 (1-C 업무용 운행기록부 자동 작성, 1-D 동승 비용 자동 정산 / 2-C EV 차박 전력 플래너, 2-D 통학차량 승하차 기록): [docs/idea-candidates-2.md](docs/idea-candidates-2.md) (2026-10-22)** — 사람이 선택 대기 중.
> **3차 브레인스토밍 (공모전 8개 이동 순간별 21안, 특이·괴상 포함, 실현성·시연 가능성 태그): [docs/idea-brainstorm-3.md](docs/idea-brainstorm-3.md)** — 의외의 강자: P1 지하 주차 층수 추정(조향각), E1 충전기 실측 속도 기록, F1 좌석별 멀미 지수, X1 차가 쓰는 일기.
> **4차 "혁신 유형"별 15안 (차=센서/플랫폼/새 조작/신뢰·경제/공간/예측): [docs/idea-brainstorm-4.md](docs/idea-brainstorm-4.md)** — 혁신 후보: G1 카 숏컷(차량 이벤트 자동화 플랫폼), S1 와이퍼 기상청, I1 동승자 코파일럿, P1 심부름 자동 끼워넣기.
> **가족 + 반려동물 조합 7안: [docs/idea-family-pet.md](docs/idea-family-pet.md)** — 추천: 야간 응급 이동 버튼(아이·반려동물, requestTrip으로 실제 ETA 비교), 목적지 반려동물 동반 자동 확인, 조수석 에어백 경고 → 묶음 "펫밀리 모드".
> **펫밀리 모드 확장 18안 + MVP 구성: [docs/idea-petmily.md](docs/idea-petmily.md)** — MVP(외부 데이터 없이): 출발 전 체크(창문 잠금·에어백·견종 온도), 리드줄 하차 가드, 휴게소 롤콜, 마지막 하차 지점 기록, 주행 기록→정차 시 요약.
> **사회 문제 × 차량 앱 (웹 조사, 출처 포함): [docs/idea-social-issues.md](docs/idea-social-issues.md)** — 추천: A "받아주는 응급실" 가족 응급 내비(응급실 뺑뺑이, 실시간 병상 공공 API + requestTrip), B EV 지하주차장 안심 인증(EV 화재 불안·단지 갈등, SOC 자동 인증), C 펫밀리를 유기·차내 방치·졸음운전 이슈와 연결. **2차 조사 추가: D 고속도로 2차 사고 대피 가이드 + 좌석 센서 인원 확인(2026년 1~5월 2차 사고 사망 400%↑, 정차 순간 동작이라 에뮬레이터 완결) → 새 1순위 후보.** 묶음: 펫밀리(평소) + D(사고) + A(응급).
> **실측 기반 비교: [docs/idea-comparison.md](docs/idea-comparison.md) (2026-10-22)** — A의 핵심(도착 잔량·충전소 자동 경유)은 순정 내비가 이미 함 / B는 순정이 아이콘만 표시(빈틈 확인), 단 `PRIVATE_CAR_SAFETY` 허용이 조건. 잠정: 10/28 확인 후 허용→B(B1+B2+B4), 불허→A(A1 충전 정차 코파일럿+A2 내 조건 충전소 재선택).

> **Pleos API 코어 + 온디바이스 AI 부가 설계 (실측): [docs/ondevice-ai-design.md](docs/ondevice-ai-design.md) (2026-10-22)** — Pleos TTS 온디바이스 ✅(주행 중 유일하게 운전자에게 닿는 경로), 온디바이스 LLM ❌(에뮬레이터 스텁), STT 구현만 확인. 제안: "센서로 확인되는 음성 코치"(D안 비트박스 단계 안내·펫밀리 롤콜·응급 ETA 음성).

선정 기준: 지금 사용 가능한 SDK(Vehicle State + NaviHelper)만으로 핵심 기능 구현 가능 / 차량 없으면 의미 없는 기능 / Mobility Services 카테고리 / 수익모델 설명 가능.
피할 것: 공식 예시 그대로(음성 메모, 안전운전 점수, 배달 기사용 앱), 준비 중 SDK 의존, 주행 중 영상·게임.

- **후보 A — EV 충전 코파일럿**
  NaviHelper 목적지 설정 이벤트 + EvBattery(SOC·전비) → 도착 시 잔량 예측 → 부족하면 충전소를 경유지로 제안·추가(`addWaypoint`/`requestRouteWithWaypoints`). 충전 중(정차)엔 충전 진행/남은 시간 표시.
  - 충전소 데이터: NaviHelper `onChargerOperatorInfo`로 얻을 수 있는지 먼저 실측 → 안 되면 공공 데이터 API(환경부/한국환경공단 전기차 충전소 정보, 엔드포인트 **미검증**) 실제 연동
- **후보 B — 경고등 해석 + 정비 연결**
  Safety 경고 / Tire 상태 / Brake 변화 감지 → 주행 중엔 짧은 알림만, 정차 시 쉬운 설명·심각도 → 필요 시 가까운 정비소를 경유지로 추가.
  - 정비소 데이터: 공공 데이터(자동차 정비업체 정보) 실제 연동 — 출처 **미검증**

---

## 7. Repo 구조 (계획)

```
/
├─ CLAUDE.md            # 이 파일 (개인정보 금지)
├─ README.md            # 심사용: 앱 이름·한 줄 소개 / 실행 방법 / 테스트 환경 / APK 위치 + 다른 PC 셋업 절차
├─ apk/                 # 팀명_앱이름_release.apk
├─ app/                 # Android 앱 모듈 (Kotlin+Compose, minSdk 28, targetSdk 34, compileSdk 35, 패키지 com.teamsync.pleos)
│                       #   core/(CarSession·VehicleMonitor·UxRestrictionsMonitor·NaviController·alert/), service/MonitorService,
│                       #   MainActivity=홈(DO), ProbeActivity=SDK Probe, probe/, log/AppLog
├─ docs/                # SDK 실측 결과(sdk-probe-results.md, probe-table.md), 기획서 초안, 시연 시나리오
├─ tools/               # summarize_probe_log.py (Probe 로그 → 결과표)
└─ logs/                # adb로 꺼낸 로그 (gitignore)
```
- GitHub: `https://github.com/SYNC-Dongihwa/pleos-app` (private, Organization). 커밋 identity는 repo-local로 `SYNC-Dongihwa` / `sync-dongihwa@users.noreply.github.com` (clone 후 매번 설정, README 참고)
- 빌드: Gradle 8.13 wrapper, AGP 8.13, Kotlin 2.0.21. CLI 빌드는 JDK 17+ 필요 (`JAVA_HOME`=Android Studio JBR)
- release 서명 키스토어(`*.jks`)와 `keystore.properties`, API 키, Playground 시크릿은 **절대 커밋 금지** (`.gitignore`)
- 키는 `local.properties` 또는 환경변수에서 `BuildConfig`로 주입

---

## 8. 마일스톤

- [ ] **M0 (~10/26)** ~~에뮬레이터 + Virtual Console 구동~~ ✅ / ~~빈 프로젝트에 Vehicle·NaviHelper 의존성 추가 후 빌드 성공~~ ✅ (10/22) / Playground 가입·프로젝트 생성 ⬜ (사람이 직접)
- [ ] **M1 (~10/27) SDK Probe 화면**: ~~전 도메인 Capability/Get/Callback + NaviHelper 이벤트 실시간 표시·로그~~ ✅ / ~~Virtual Console 조작 시 값 변화 확인, 사용 가능 데이터 목록 기록~~ ✅ (10/22, `docs/sdk-probe-results.md`) / **아이디어 확정 ⬜**
- [ ] 10/28 설명회 질문: ① 준비 중 SDK 공모 기간 내 오픈 여부 ② 표준 Android API(TTS/위치) 대체 시 기술 활용성 인정 여부 ③ PT 시연 장비(본인 노트북+에뮬레이터 OK?) ④ **`PRIVATE_CAR_SAFETY`(경고등·타이어)·`PRIVATE_CAR_INFO`를 3rd-party 앱이 써도 되는지** (에뮬레이터에선 normal로 부여됨) ⑤ 차량용 앱의 Foreground Service 허용 여부·타입 (현재 `specialUse`) ⑥ **DO 인정 방식** — 매니페스트 meta-data를 넣었지만 사이드로드 앱은 `isActivityDistractionOptimized=false`. 마켓 설치 시 인정? 에뮬레이터에서 DO 앱을 시연·테스트하는 방법? ⑦ **서드파티 앱의 주행 중 알림(heads-up) 표시 방법** (현재 Pleos가 비권한 앱 HUN을 띄우지 않음) ⑧ Vehicle SDK 퍼미션 **사전 승인 절차** (심사 기준: 승인 안 된 퍼미션 선언 시 탈락) ⑨ **TTS·STT·LLM SDK** — 문서상 준비 중인데 Maven 공개본(TTS 3.3.1)이 에뮬레이터에서 동작함. 공모전에서 써도 되는지, `TTS_SERVICE` 승인 대상인지, 실차 온디바이스 LLM 탑재·서드파티 허용, LLM 서버 모드 자격 발급, 주행 중 서드파티 음성 안내 정책
- [x] **공통 기반 (10/22)**: FGS 상시 수신 / CarUxRestrictions 구독 + 주행·정차 레이아웃 / 1줄 알림(앱 내 바 + heads-up 게시) / NaviHelper 경유지 래퍼 — `docs/common-foundation.md`, 브랜치 `feat/common-foundation`
- [ ] **M2 (~11/10)** 핵심 기능 1~2개 실제 동작 + CarUxRestrictions 처리
- [ ] **M3 (~11/14)** release APK 빌드·서명, 블라인드 GitHub(Organization) 업로드, README, 시연 영상(에뮬레이터 화면녹화 + Virtual Console 조작), 기획서 작성(MAU·Retention 근거 포함)
- [ ] **11/15 23:59 전 제출** (여유 있게 11/14 제출 목표), 숏폼은 시연 영상 재편집
- [ ] 1차 합격 시(11/23) → 12/3 PT 전까지 고도화 + 시연 안정화

---

## 9. 진행 로그 (compact 대비 — 날짜, 한 줄씩 추가)

- 2026-10-22: 공모전·Pleos 문서 분석 완료. 사용 가능 SDK = Vehicle State + NaviHelper. 부문 = 일반. 아이디어 후보 A/B, 미확정.
- 2026-10-22: M0 — Pleos Connect Emulator 2.1.0 이미지 설치(수동, SHA-1 검증), AVD `Pleos_Connect_v2.1.0_API_34`(RAM 4GB, cold boot). 프로젝트 생성·빌드 성공 (Gradle 8.13/AGP 8.13/Kotlin 2.0.21/Compose, 패키지 `com.teamsync.pleos`). 기기 서비스: Vehicle 2.3.22, Navi 3.0.4.
- 2026-10-22: M1 — SDK Probe 구현(리플렉션으로 20개 도메인의 check/get/register 전수 호출, 482건) + AppLog(logcat+롤링 파일). Virtual Console 조작(기어·SOC·배터리Wh·주행가능거리·누적거리·충전포트·조향각·방향지시등·경고등·조명·도어·창문·공조·핸들열선) → 리스너 1초 내 반영 확인. 결과 `docs/sdk-probe-results.md`, 표 `docs/probe-table.md`.
- 2026-10-22: [문서≠실측] public `VehicleException` 없음(난독화 `callback.exception.a`, `a(): ErrorCode`) / 실패 상당수는 ErrorCode 없는 `java.lang.Exception` / capability 콜백은 `(CapabilityConfig[, List|Map])` 오버로드 / `Gear` 도메인 존재 / `vehicle-data`는 runtime 스코프라 직접 의존성 추가 / `adb logcat -s 'PLEOS/*'` 동작 안 함.
- 2026-10-22: [주의] Area는 속성별로 다름(도어 잠금=4도어 묶음 1개, HVAC=5좌석 묶음) → 반드시 capability가 준 Area로 호출. 조향각 부호가 콘솔과 반대(+90°→-90.0). 배터리 잔량 단위 Wh, 주행가능거리 m. EV라 Fuel 전체·전비·트립·오일레벨·타이어 실측압·안전벨트경고 미지원. **Vehicle SDK에 차속 API 없음.**
- 2026-10-22: [주의] 기어 D 즉시(0 km/h) DO 미선언 앱 차단. 앱이 백그라운드면 ~10초 후 freezer로 콜백 끊김 → 상시 감시 기능은 Foreground Service 필요. NaviHelper `requestRoute`/`cancelRoute` 호출 시 Navigation 앱이 전면으로 올라옴.
- 2026-10-22: [아이디어 영향] 순정 내비가 이미 도착 시 잔량(`remainDestinationChargingValue` Wh, 화면엔 %·"왕복 후 예상 %"), 추천 충전소·`chargingPlan`·`targetSoc` 제공 → 후보 A의 "도착 잔량 예측+충전소 경유" 자체는 차별성 약함. `onChargerOperatorInfo`=사업자 목록뿐. 후보 B는 Telltales 실시간 수신이 동작하나 `PRIVATE_CAR_SAFETY` 허용 여부가 관건(설명회 질문 ④).
- 2026-10-22: GitHub Organization `SYNC-Dongihwa` 생성(사람), private repo `pleos-app`에 push. 커밋 identity는 repo-local 익명.
- 2026-10-22: [실측] SOC 2%로 경로 요청 → 순정 내비가 "배터리 부족으로 충전소를 경유합니다"로 충전소 자동 경유(충전 6분, 도착 10%), SDK `onDrivingInfo.waypoints`에 `isChargingStation=true, maxChargingPower=100, chargerCount=4`로 들어옴(단 `isRecommendedChargingStation=false`, `chargingPlan=null`). 충전 스위치 On이어도 에뮬레이터 SOC는 자동 상승 안 함.
- 2026-10-22: [실측] 경고등(ABS·EV시스템·저전압·엔진오일압) ON → 순정은 클러스터 아이콘만, 텍스트/팝업/알림 없음 (도어·트렁크 열림만 텍스트 팝업). Telltale `color`는 전부 4(매핑 불명). 앱이 freeze 중 받지 못한 Navi 이벤트는 전면 복귀 시 몰아서 도착. A/B 비교 → `docs/idea-comparison.md`.
- 2026-10-22: 공통 기반 구현·검증 (`docs/common-foundation.md`). FGS(specialUse)로 내비 전면 36초+ 동안 freeze 없이 수신 ✅ / 주행 규제 구독·주행 레이아웃 전환 ✅ / 앱 내 1줄 알림 바 ✅ / 경유지 래퍼(경로→추가×2→제거→전체제거→취소) ✅.
- 2026-10-22: [제약] DO meta-data 선언해도 사이드로드 앱은 `isActivityDistractionOptimized=false` → D에서 Pleos 홈이 오버레이로 가림. 서드파티 heads-up은 Pleos `NOTIFICATION_INJECTOR`가 privileged/platformSigned 아니면 미표시. → 에뮬레이터에선 주행 중 표시 경로 없음, 시연은 정차 화면 중심.
- 2026-10-22: [SDK 규칙] 같은 속성 리스너 하나를 unregister하면 앱 전체 구독이 서비스에서 삭제됨(참조 카운트 없음, 다른 Vehicle 인스턴스 포함) → **속성당 등록 주체는 VehicleMonitor 하나**, Probe는 세션 인스턴스를 빌리고 해제 후 `resubscribe()`. addWaypoint/removeWaypoint는 `onRouteCancelled`→FREE_DRIVE→(~6초)→`onWaypointChanged`→ROUTE_DRIVE 순서, `onWaypointChanged.index`는 다음 경유지(추가한 슬롯 아님).
- 2026-10-22: [심사 가이드 확인] Vehicle SDK 퍼미션은 앱마켓 사전 승인된 것만 선언 가능(아니면 탈락), 테스트용 더미 데이터 금지(개발자 도구는 debug 빌드 전용으로 처리), 미디어 앱 heads-up 금지·운전 무관 알림 금지.
- 2026-10-22: [실측] NaviHelper `requestTrip(TripRequest)` → `onTripInfo(distance, eta, estimatedSocAtDest, energyConsumption, summary.routePoints)` 약 0.2초, **Navigation 앱 전면 전환 없음** (판교 3211m/740s/59%, 강릉 195331m/8579s/23%). 도착 SOC는 SOC 속성이 아닌 `EV_BATTERY_LEVEL`(Wh) 기준으로 보임. 충전 경유 포함 여부 미확인. `NaviController.estimateTrip()`으로 래핑 (브랜치 `feat/trip-estimate`). → 아이디어 후보 4안 작성.
- 2026-10-22: [실측] **AAOS 표준 `CarPropertyManager`로 Pleos SDK에 없는 신호 읽기 가능**: `PERF_VEHICLE_SPEED`(권한 `android.car.permission.CAR_SPEED` = dangerous, 런타임 허용), `PARKING_BRAKE_ON`(CAR_POWERTRAIN normal), `NIGHT_MODE`·`ENV_OUTSIDE_TEMPERATURE`(CAR_EXTERIOR_ENVIRONMENT normal). **비상등 `HAZARD_LIGHTS_STATE`는 signature라 SecurityException**, Pleos `TurnSignalValue`도 OFF/LEFT/RIGHT뿐 → 비상등은 서드파티가 알 수 없음. 콘솔 가속(ACCEL)은 입력 방식이 `http-g29`라 마우스·키보드로 재현 불가 → 차속 변화 미확인. NaviHelper 경로 상태에 `TrafficEventType.ACCIDENT` 이벤트 존재, `CruiseControl.ACTIVATED`로 ACC 작동 감지 가능. → `docs/secondary-accident-api.md`. AAOS 표준 퍼미션의 심사 허용 여부는 설명회 질문.
- 2026-10-22: [실측] 좌석 점유: 콘솔 "빈자리" → `registerSeatOccupancy(ROW_2_LEFT)=false` 실시간 수신, 뒷문 1→0 수신. 콘솔 "탑승" 복귀는 에뮬레이터 단 발행만 되고 Vehicle 서비스 이벤트 없음(에뮬레이터 이슈 추정). 순정 `PLEOS_CAR_SERVICE` 안전벨트 경고가 좌석 점유 사용. → 2차 후보 4안 작성.
- 2026-10-22: [실측] **Pleos TextToSpeech 3.3.1 온디바이스 동작** (Maven 공개본, 문서상 준비 중): init 0.15s, 첫 모델 로드 0.4~0.9s, 3.8초 분량 합성 0.29s, 화자 seoyoon. 규칙: `TTS_SERVICE` 필수(SDK ApiChecker) / `addEventListener`는 initialize 성공 후 / `Locale.KOREA`(`ko`면 엔진 오류 후 CaaS가 재시작 전까지 멈춤) / `speak(text, isFinal=true)`. Android 표준 TTS는 status=-1로 불가. 의존성 추가 시 repo 필터를 하위 그룹까지 넓혀야 함.
- 2026-10-22: [실측] **기어 D + Navigation 전면에서 FGS가 낸 TTS 음성은 재생됨**(heads-up은 같은 순간 차단) → 주행 중 서드파티 앱의 유일한 전달 경로. `AlertNotifier`가 WARN 이상을 화면을 볼 수 없을 때 음성으로 읽도록 반영.
- 2026-10-22: [실측] **온디바이스 LLM 불가**: 에뮬레이터 CaaS(4.2.10-coral-aosp) `OnDeviceLLMManagerImpl` 전 메서드가 "ignored: on-device LLM is disabled" 스텁, 오류 콜백 없음. LLM 진단 코드는 커밋 `e57ad4b`에 보존 후 제거. STT는 CaaS에 실제 구현 존재(미실측). → `docs/ondevice-ai-design.md`.
- 2026-10-22: [인계] 다른 PC로 이동. **`feat/trip-estimate`를 main에 fast-forward 병합** → 모든 작업이 main에 있음, push 완료. Virtual Console 조작 스크립트 `tools/vc-helper.js` 추가. 다음 할 일: ① 아이디어 확정 (제안: D안 2차 사고 대피 "센서로 확인되는 음성 코치") ② STT 실측(RECORD_AUDIO + STT_SERVICE) ③ 경고등 이름 음성용 한글화 ④ 내비 음성과 겹칠 때 오디오 포커스 확인 ⑤ 10/28 설명회 질문 ①~⑨.

---

