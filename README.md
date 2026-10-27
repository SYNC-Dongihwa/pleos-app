# 내 차 튜토리얼

> 처음 타는 차에서 전조등·와이퍼·창문 같은 기능을 직접 조작하면, **Vehicle SDK의 실제 상태 변화로** 조작 완료를 확인해 주는 인터랙티브 튜토리얼 (현대자동차그룹 차량용 앱 개발 공모전 출품작).
> **현재 단계: Phase 2 완료 — 튜토리얼 엔진·화면** (Phase 3: 오프닝·제출 준비). 작업 현황은 [CLAUDE.md](CLAUDE.md).

지금 들어 있는 것 (런처 이름 **내 차 튜토리얼**):
- **홈** — 정차 게이트(기어 P 여부, 주차 브레이크 표시), 시작 / 이어서 하기 / 처음부터, 이 차에서 확인할 수 있는 기능 목록.
- **튜토리얼 11단계** — 전조등 · 상향등 · 방향지시등 · 앞 와이퍼 · 운전석 창문 · 에어컨 · 운전석 온도 · 핸들 열선 · 도어 잠금 · 트렁크 · 주행 모드.
  - 화면 문구 + Pleos TTS 음성 안내. 완료는 **차량 상태 변화(콜백)로만** 판정하고, 바꾼 상태를 원래대로 되돌렸는지까지 확인한다.
  - 20초 동안 변화가 없으면 위치 힌트. 건너뛰기는 언제든 가능. 이 차가 지원하지 않는 기능은 '지원 안 됨'으로 자동으로 넘어간다.
  - 기어가 P가 아니게 되면 즉시 멈추고 음성으로 알리며, P로 돌아오면 이어서 한다. 진행 상황은 저장돼 앱을 다시 열어도 이어서 할 수 있다.
- **결과 화면** — 완료·건너뜀·지원 안 됨 개수, 단계별 확인 근거(예: "꺼짐 → 켜짐, 켜짐 → 꺼짐"), 다시 해보기, "내 차 마스터" 배지.
- **개발자 패널** (홈 제목을 3초 안에 5번 탭) — 구독 중인 신호의 실시간 원시 값, capability 결과, 튜토리얼·TTS 상태, 퍼미션, 최근 오류·경고 50개.
- 이전 단계(SDK Probe·상시 수신 서비스·내비 래퍼)의 코드는 git 태그 `archive/pleos-probe-2026-10-22`, 당시 실측 문서는 `docs/`에 있다.

## 테스트 환경

| 항목 | 값 |
|---|---|
| 대상 | Android Automotive OS, **Pleos Connect Emulator 2.1.0** (API 34, x86_64) |
| 앱 | Kotlin 2.0.21, Jetpack Compose, minSdk 28 / targetSdk 34 / compileSdk 35 |
| 빌드 | Gradle 8.13 (wrapper), AGP 8.13.0, **JDK 17 이상** (Android Studio 내장 JBR 21 사용) |
| SDK | `ai.pleos.playground:Vehicle:2.3.2`, `vehicle-data:1.1.11`, `NaviHelper:3.0.5`, `TextToSpeech:3.3.1` (Pleos Nexus) |

## 다른 컴퓨터에서 이어서 작업하기

### 1. 클론 + 커밋 identity (블라인드 심사 규칙)

```bash
git clone https://github.com/SYNC-Dongihwa/pleos-app.git
```

> 2026-10-22 기준 작업은 모두 `main`에 있다 (도착 예상, AAOS 차량 속성 진단, Pleos TTS 음성 알림, 아이디어·설계 문서). 새 작업은 `main`에서 브랜치를 만들어 시작한다.

커밋 author에 실명·개인 메일이 들어가면 안 된다. 클론한 뒤 **이 repo에만** 익명 identity를 설정한다 (repo-local 설정은 clone으로 따라오지 않음).

```bash
git config user.name "SYNC-Dongihwa"
```

```bash
git config user.email "sync-dongihwa@users.noreply.github.com"
```

### 2. Android Studio / JDK

- Android Studio 최신 버전 설치 → Android SDK Platform 34·35, Build-Tools, Emulator, Platform-Tools.
- 터미널에서 빌드할 때는 JDK 17+가 필요하다. Android Studio 내장 JBR을 쓰면 된다 (Windows 기본 경로 `C:\Program Files\Android\Android Studio\jbr`).
- `local.properties`는 커밋하지 않는다. Android Studio로 열면 자동 생성되고, CLI만 쓰면 `ANDROID_HOME` 환경변수로 충분하다.

### 3. Pleos Connect Emulator 설치

공식 방법 ([docs/contest-guide.md](docs/contest-guide.md) §3):
1. Android Studio → Settings → Languages & Frameworks → Android SDK → **SDK Update Sites** → `+`
   - Name: `Pleos Connect System Image`
   - URL (Windows/Linux): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - URL (Mac): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. SDK Platforms → Show Package Details → Android 14 (API 34) → **Pleos Connect v2.1.0 APIs** 설치 (약 4.1 GB 다운로드, 압축 해제 후 약 19.6 GB). 설치하면 42dot SDK License에 동의하는 것이다.
3. Device Manager → Create Device → **Automotive → Pleos Connect v2.1.0** → API 34 이미지 선택 → Advanced: Boot **Cold**, RAM **4 GB**.

> 첫 번째 PC에서는 SDK Manager 대신 수동으로 설치했다: 위 XML의 `crp_car_emulator.zip`을 받아 SHA-1(`b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa`)을 확인하고,
> `$ANDROID_HOME/system-images/android-34/pleos_car_emulator/x86_64/`에 풀었다(zip 안의 `crp_avd_out_x86_64/` 폴더 이름을 `x86_64`로 변경). 공식 방법이 더 간단하다.

### 4. 빌드 · 설치 · 실행

```bash
./gradlew :app:installDebug
```

```bash
adb shell am start -n com.teamsync.pleos/.MainActivity
```

Windows PowerShell이면 `.\gradlew.bat :app:installDebug`. `JAVA_HOME`이 JDK 11 이하로 잡혀 있으면 빌드가 실패하므로 JBR 경로로 바꾼다.
- 런타임 권한 요청은 없다 (선언한 퍼미션이 모두 normal). 실행하면 바로 차량 신호를 구독한다.
- 개발자 패널: 홈의 "내 차 튜토리얼" 제목을 3초 안에 5번 탭. adb로는 제목 좌표에 `input tap`을 5번.
- 음성(TTS)이 응답 없이 멈추면 Pleos AI 서비스를 재시작한다: `adb shell am force-stop ai.pleos.playground.caas` (잘못된 로캘 요청 후 서비스가 멈췄던 사례, docs/ondevice-ai-design.md).

### 5. Virtual Console (차량 상태 조작)

```bash
adb forward tcp:8088 tcp:8088
```

PC 브라우저에서 `http://127.0.0.1:8088/` → 화면의 Gear·Climate·Body 패널, 또는 **Emulation Controls**(48개 카드)에서 값을 바꾸면 개발자 패널의 값과 `SYNC/Vehicle` 로그가 1초 안에 바뀐다. 트렁크는 "Trunk / Frunk Position" 카드에서 **trunk를 먼저 선택**해야 한다 (기본값 frunk).
자동화할 때는 [tools/vc-helper.js](tools/vc-helper.js)를 브라우저 콘솔에 붙여넣고 `__vc.press('Gear Selection','D')`처럼 카드 제목으로 조작한다.
⚠️ 기어를 D로 두면 시스템이 앱을 "주행 중에는 사용할 수 없습니다"로 가린다 (정차 전용 앱이라 DO 미선언). 가려진 동안에도 프로세스는 신호를 계속 받고, P로 돌리면 정차 게이트가 다시 열린다.

#### Virtual Console로 전 단계 완주

시작 전 콘솔 상태: 기어 P, 전조등·상향등·방향지시등·와이퍼 Off, 운전석 창문 0%, 핸들 열선 Off, 도어 Lock, 트렁크 Close (트렁크 카드는 **trunk 선택**). 홈에서 "튜토리얼 시작" 후 아래 순서로 조작한다. 처음 값이 다르면 앱이 문구를 바꿔 안내한다(예: 에어컨이 켜져 있으면 "꺼 보세요" → "다시 켜 주세요").

| 단계 | 콘솔 카드 → 조작 |
|---|---|
| 전조등 | Exterior Lights Mode → Low beam → Off |
| 상향등 | High Beam → On → Off |
| 방향지시등 | Turn Signal State → Blink left → Blink right → Off |
| 앞 와이퍼 | Windshield Wiper Switch (front) → Low → Off |
| 운전석 창문 | Window Position (front_left) → 40 → 0 |
| 에어컨 | Air Conditioning → (켜져 있으면) Off → On |
| 운전석 온도 | Target Temperature (driver) → 원래보다 1℃ 낮게 → 원래 값 |
| 핸들 열선 | Steering Wheel Heat → High → Off |
| 도어 잠금 | Door Lock → Unlock → Lock |
| 트렁크 | Trunk / Frunk Position (trunk) → Open → Close |
| 주행 모드 | 콘솔에 카드 없음, 에뮬레이터 미지원 → 앱이 '지원 안 됨'으로 넘어감 |

일시정지 확인: 아무 단계에서 Gear Selection → D (앱이 멈추고 음성 안내) → P (약 1초 뒤 이어서).

### 6. 로그 보기

로그 태그는 모두 `SYNC/` 접두사다 (`SYNC/Vehicle`, `SYNC/Car`, `SYNC/Gate`, `SYNC/Tutorial`, `SYNC/Speech`, `SYNC/Progress`, `SYNC/UI`, `SYNC/App`). 튜토리얼 판정 근거는 `SYNC/Tutorial`의 `STEP`·`JUDGE`·`HINT`·`PAUSE`·`RESUME`·`SKIP`·`FINISH` 줄. `adb logcat -s 'SYNC/*'`는 와일드카드가 지원되지 않아 동작하지 않는다.

```bash
adb logcat -v time | grep "SYNC/"
```

앱 내부 로그 파일 (AAOS 에뮬레이터의 앱 사용자는 **user 10**, 날짜는 에뮬레이터 시간대 PDT 기준):

```bash
adb exec-out run-as com.teamsync.pleos --user 10 cat files/logs/app-20261026.log > logs/app.log
```

`logs/`는 `.gitignore` 대상이다.

## 구조

```
├─ CLAUDE.md                 # 현재 상태·결정·SDK 메모·퍼미션·알려진 이슈 — 작업 시작 전에 먼저 읽기
├─ README.md
├─ app/src/main/java/com/teamsync/pleos/
│  ├─ PleosApp.kt · AppGraph.kt   # 프로세스 단위 초기화 (SDK 구독은 여기서 한 번만)
│  ├─ MainActivity.kt        # 홈 ↔ 개발자 패널
│  ├─ vehicle/               # VehicleStateRepository(capability → get → 구독 → StateFlow), VehicleSignal, VehicleErrors
│  ├─ car/                   # AaosCarMonitor(주행 규제·GEAR_SELECTION·PARKING_BRAKE_ON), ParkingGate
│  ├─ tutorial/              # TutorialSteps(단계 데이터), TutorialEngine(판정 상태 기계), TutorialController, ProgressStore(DataStore)
│  ├─ voice/SpeechGuide.kt   # Pleos TTS 온디바이스 래퍼
│  ├─ log/AppLog.kt          # 공통 로그 (SYNC/ 태그, logcat + 롤링 파일 + 최근 오류 50개)
│  └─ ui/                    # HomeScreen, TutorialScreen, FinishScreen, DevPanel (Compose)
├─ app/src/test/             # 단위 테스트 (ParkingGate, Area 선택, TutorialEngine) — test double은 여기에만
├─ docs/                     # 공모전 규정, 이전 실측 기록, 스크린샷
├─ tools/vc-helper.js        # Virtual Console 스크립트 조작
└─ apk/                      # 제출용 release APK 위치 (아직 없음)
```

## 비밀정보

release 키스토어(`*.jks`), `keystore.properties`, API 키, Playground 클라이언트 시크릿은 절대 커밋하지 않는다 (`.gitignore` 처리됨). 키는 `local.properties` 또는 환경변수에서 `BuildConfig`로 주입한다.
