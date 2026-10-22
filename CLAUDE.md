# CLAUDE.md — 현대자동차그룹 차량용 앱 개발 공모전 (Pleos Connect)

> compact 이후 이 파일을 먼저 읽고 작업을 이어갈 것.
> 중요한 결정/발견/막힌 문제는 하단 **§9 진행 로그**에 날짜와 함께 한 줄씩 추가할 것.
> ⚠️ 이 repo는 심사용으로 제출됨 → 이 파일 포함 어디에도 **실명·소속·개인 계정 정보 금지** (블라인드 심사). 필요하면 이 파일을 `.gitignore` 처리.

---

## 0. 개발 원칙 (최우선, 반드시 준수)

1. **실제로 동작하는 코드만 작성한다.** 스텁, 가짜 구현, TODO만 있는 함수를 "완료"로 보고하지 않는다.
2. **목업/시뮬레이터 fallback 금지.** SDK·서버·외부 API 연결이 안 될 때 가짜 데이터로 대체하는 코드를 넣지 않는다.
   - 연결 실패 시 → 에러 상태를 UI에 그대로 표시 + 로그 기록. 끝.
   - 예외: 차량 상태값은 **Pleos Connect Emulator + Virtual Console**(공식 개발 환경)이 실제 Vehicle Service로 공급한다. 이는 앱 코드 안의 목업이 아니므로 허용.
3. **기능이 동작하지 않으면 로그로 원인을 판단한다.** 추측으로 코드를 바꾸지 말고, 로그를 추가 → 재현 → 로그 확인 → 수정 순서로 진행.
4. 문서와 실제 SDK 동작이 다르면 **실제 동작이 기준**. 차이는 §9 로그에 기록.
5. 불확실한 API는 Gradle로 받은 SDK AAR의 클래스/시그니처를 직접 확인한다. (Pleos 문서 API Reference 페이지는 JS 렌더링이라 WebFetch로 내용이 안 읽힘)

### 로깅 규칙
- 공통 래퍼 `AppLog` 하나로 통일. 태그 prefix: `PLEOS/` (예: `PLEOS/Vehicle`, `PLEOS/Navi`, `PLEOS/UX`, `PLEOS/Net`).
- 반드시 로그를 남길 지점:
  - SDK `initialize()` / `release()` 호출과 결과
  - 모든 `onFailed` → API 이름, 인자, `VehicleException.errorCode`, `message`, stacktrace
  - `NaviHelperEventListener.onError(NaviErrorCode)` 전부
  - `checkXXXCapabilities` 결과 (지원 여부·범위)
  - `CarUxRestrictions` 변경 (`isRequiresDistractionOptimization`, `activeRestrictions` 비트값)
  - 외부 네트워크 요청 URL(키 제외), HTTP 코드, 실패 사유
- logcat + 앱 내부 파일(`filesDir/logs/app-YYYYMMDD.log`, 용량 제한 롤링) 동시 기록. 구현: `app/.../log/AppLog.kt`.
  - 확인: `adb logcat -v time | grep PLEOS/` (⚠️ `-s 'PLEOS/*'`는 와일드카드 미지원 → 0줄)
  - 파일: `adb exec-out run-as com.teamsync.pleos --user 10 cat files/logs/app-YYYYMMDD.log` (AAOS 앱 사용자 = user 10, 날짜는 에뮬레이터 시간대 PDT 기준)
  - SDK 자체 로그 태그 `[Pleos][SDK]a`, Vehicle 서비스 쪽 `[Pleos]u`도 원인 분석에 유용
- API 키/시크릿은 로그에 절대 출력하지 않음.

---

## 1. 공모전 핵심 정보

- 명칭: 현대자동차그룹 차량용 앱 개발 공모전 — *Code your App into Pleos App Market*
- 사이트: https://appdevchallenge.auto-hmg.io/
- 부문: **일반(스타트업 포함)** 으로 참가 (대학생 부문과 상금·팀수 동일)
- 형태: 개인 또는 팀(대표 포함 최대 5인)

### 일정 (KST)
| 날짜 | 내용 |
|---|---|
| 2026-10-28(수) 오후 | 온라인 설명회 (다시보기 제공) |
| **2026-11-15(일) 23:59** | **1차 접수 마감 (이메일)** |
| 2026-11-23(월) | 1차 서류 결과 → 20팀 |
| **2026-12-03(목)~04(금)** | **2차 PT (오프라인, 현대차 강남대로사옥 1층 UX스튜디오 서울)** — 실제 동작하는 앱 시연 필수 (에뮬레이터 구동 화면 또는 기기) |
| 2026-12-18(금) | 최종 10팀 발표 |
| 2026-12-19~2027-01 | 전시 준비 (추가 개발 기간 아님, 순위 영향 없음) |
| 2027-02 | 현대차그룹 행사 현장 시연·시상식 (참석 의무), 최종 순위 발표 |

### 상금 (부문별)
대상 1,000만(1팀) / 최우수 600만(1팀) / 우수 400만(2팀) + 아이디어 특별상 150만(부문통합 2팀) / 숏폼 챌린지 50만(별도 1팀)

### 심사 기준 (세부 배점 비공개)
1. **차량특화성** — 모바일 대비 차량 환경만의 가치. 주행·정차 맥락 + 차량 기능과 유기적 결합
2. **창의성** — 독창성, 운전자/동승자 Pain Point & Needs 명확 정의와 해결 방식
3. **기술 활용성** — Pleos Playground SDK·API를 **핵심 기능**에 적절히 활용
4. **사업성** — 타깃 고객, 예상 MAU, Retention Rate, 확장 계획
- [가산점] 1차 접수 시 시연 영상 / GitHub(소스+APK) 제출 시 단계별 가산점
- 2차 순위는 **PT에서 제출·시연한 결과물** 기준으로 확정

---

## 2. 제출 규칙 (1차, 이메일)

- 접수: `appdevchallenge@hyundai-partners.com`
- 메일 제목: `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명`
- 수정 제출: `[수정제출] [차량용 앱 개발 공모전] 팀명_대표자명`, **같은 발신 주소**, 전체 자료 + 서명 동의서 재첨부, 팀원 전원 CC. 마감 전 마지막 발송본이 최종.
- 필수: 접수양식(=기획서, docx, 분량 제한 없음) + 개인정보 동의서(참가자 전원 각각 서명·날짜)
- 선택(가산점): 시연 영상(목업·프로토타입·화면녹화 인정), GitHub 저장소(소스+APK)
- 선택(가산점 없음): 숏폼 링크 (Instagram Reels / YouTube Shorts, 30초~1분 세로, `#HMG차량용앱개발공모전` 필수 + 총 2~3개 해시태그)

### 기획서(접수양식) 항목
1. 앱 한 줄 소개
2. 기획 배경 (어떤 니즈/불편한 순간, 왜 해결 필요)
3. 앱 주요 기능 및 차량 특화 요소
4. 주요 사용 시나리오 (누가/어떤 상황/어떻게)
5. **개발 계획 — Pleos Connect 구현 방식, 개발 범위, SDK/API 사용 계획 구체적으로**
6. 기대효과 및 발전 가능성 (타깃 분석, **예상 MAU, Retention Rate**, 확장 계획)
7. 참고자료 (화면 시안, 시연 영상, GitHub)
8. 숏폼 링크

### GitHub 제출 형식
- 소스 + APK를 **같은 repo**에 (APK 단독 제출 불가)
- APK 위치: `apk/` 폴더, 파일명 **`팀명_앱이름_release.apk`**, **실행 가능한 release 빌드**
- 용량 초과 시 GitHub Releases에 올리고 Release 페이지 URL 제출
- README.md 필수 내용: 앱 이름·한 줄 소개 / 실행 방법 / 테스트 환경 및 필요한 설정 / APK 위치 또는 Release 링크
- **블라인드**: 첨부·링크에 개인정보·계정정보·개발자 실명 노출 금지 → 실명 아닌 **별도 GitHub 계정 또는 Organization** 사용, 커밋 author name/email도 확인 (`git config user.name/user.email`)
- Private repo 제출 시 심사용 계정을 Collaborator 초대 (개인 계정 private repo는 쓰기 권한까지 부여됨 → Organization repo에서 Read 권한 권장). 마감 전 접근 확인 필수.

### 기타 규정
- 순수 창작물만. 표절·기 수상작은 제외.
- IP는 응모자 소유. 단 수상작은 주최측이 통상실시권, 발표일부터 4개월 우선협상권을 가질 수 있음.
- 현대차그룹 임직원은 수상 대상 제외.

---

## 3. Pleos 개발 환경 (공식 문서 기준, 2026-10-22 확인)

### 요구사항
- Android Studio 최신, **minSdk 28 이상**(차량 사이버보안: 사이닝 키 로테이션/리니지 — APK Signature v3), Gradle 8.0+, Kotlin 또는 Java
- Pleos Connect Emulator **2.1.0** (API 34 시스템 이미지)

### 에뮬레이터 설치
1. Android Studio → Tools > SDK Manager > **SDK Update Sites** → `+`
   - Name: `Pleos Connect System Image`
   - Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. SDK Platforms → API 34의 **Pleos Connect system Image** 체크 (Show Package Details) → 설치 → Android Studio 재시작
3. Device Manager → Create device → Form Factor **Automotive** → **Pleos Connect v2.1.0** → Advanced: Default boot **Cold**, RAM **4 GB** → API 34 / Pleos Connect v2.1.0 APIs
4. 이전 버전 에뮬레이터가 있으면 삭제 후 설치

### Virtual Console (차량 상태 조작 — 데모·테스트용)
```
adb forward tcp:8088 tcp:8088
# PC 브라우저: http://127.0.0.1:8088/
```
- 도메인: Vehicle(알림), Motion(기어·브레이크·조향각·주행거리·핸들열선), ADAS(크루즈), Convenience(도어·창문·트렁크·시트·조명·와이퍼·ETCS), Safety(경고등 on/off/점멸), Energy(주행가능거리·배터리·충전 상태·충전포트), Climate(공조)
- 2차 PT 시연과 시연 영상은 **에뮬레이터 + Virtual Console 조작** 화면으로 구성 가능

### Playground 계정/프로젝트
- https://pleos.ai/playground 가입 → Workspace > My Project > 프로젝트 생성 (카테고리 선택 필수)
- 프로젝트 ID(Key): 앱마켓 심사 요청 시 사용 / 클라이언트 ID·시크릿: TTS·STT용 (시크릿은 repo에 커밋 금지)
- Emulator 2.1.0+ 는 CRN 입력 불필요

---

## 4. SDK 사용 가능 상태 ⚠️ (가장 중요)

| SDK | 상태 (2026-10-22 문서 기준) |
|---|---|
| **Vehicle State (조회)** | ✅ 신청 없이 사용 가능 |
| **NaviHelper** | ✅ 신청 없이 사용 가능 |
| FusedLocation | ⛔ 준비 중(Coming Soon) — 신청 불가 |
| TextToSpeech | ⛔ 준비 중 |
| SpeechToText | ⛔ 준비 중 |
| Vehicle Control (제어/set) | ⛔ 준비 중 |

→ **핵심 기능은 Vehicle State + NaviHelper로만 설계한다.** 준비 중 SDK에 의존하는 기능은 넣지 않는다 (상태 바뀌면 §9에 기록 후 재검토).

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
}
```
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
  - 실측: **서드파티 heads-up 미표시** — Pleos `NOTIFICATION_INJECTOR`가 privileged/platformSigned 아니면 띄우지 않음 → 설명회 질문 ⑦. 결국 에뮬레이터에선 D 동안 서드파티 앱이 운전자에게 보여줄 경로가 없음 (`docs/common-foundation.md` §3)
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
- [ ] 10/28 설명회 질문: ① 준비 중 SDK 공모 기간 내 오픈 여부 ② 표준 Android API(TTS/위치) 대체 시 기술 활용성 인정 여부 ③ PT 시연 장비(본인 노트북+에뮬레이터 OK?) ④ **`PRIVATE_CAR_SAFETY`(경고등·타이어)·`PRIVATE_CAR_INFO`를 3rd-party 앱이 써도 되는지** (에뮬레이터에선 normal로 부여됨) ⑤ 차량용 앱의 Foreground Service 허용 여부·타입 (현재 `specialUse`) ⑥ **DO 인정 방식** — 매니페스트 meta-data를 넣었지만 사이드로드 앱은 `isActivityDistractionOptimized=false`. 마켓 설치 시 인정? 에뮬레이터에서 DO 앱을 시연·테스트하는 방법? ⑦ **서드파티 앱의 주행 중 알림(heads-up) 표시 방법** (현재 Pleos가 비권한 앱 HUN을 띄우지 않음) ⑧ Vehicle SDK 퍼미션 **사전 승인 절차** (심사 기준: 승인 안 된 퍼미션 선언 시 탈락)
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

---

## 10. 참고 링크

- 공모전: https://appdevchallenge.auto-hmg.io/ (가이드 `/guide/*`, FAQ `/faq`, 공지 `/notice`, 접수양식 `/downloads/contest-application-consent.docx`)
- Playground: https://pleos.ai/playground
- 프로젝트 생성·SDK 신청: https://document.pleos.ai/docs/pleos-only/create-my-project
- 에뮬레이터: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- Virtual Console: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator
- Vehicle SDK: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro (permission: `.../Vehicle/vehicle-permission`, 에러: `.../Vehicle/vehicle-error-code`)
- NaviHelper SDK: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro (`importing-sdk`, `declaring-permissions`, `event-listeners`, `destinations`, `routes`, `waypoint-settings`, `error-handling`)
- 주행 규제 개발: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- 지역별 주행 규제: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy
- 앱 카테고리: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category
- 운전 분산 가이드: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline
- 앱 심사 가이드: https://document.pleos.ai/en/docs/pleos-only/distribute/app-review-guide/
