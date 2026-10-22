# 공통 기반 (2026-10-22) — 상시 수신 · 주행 규제 · 1줄 알림 · 경유지 래퍼

후보 A/B 어느 쪽이든 필요한 기반. 브랜치 `feat/common-foundation`. 에뮬레이터(Pleos Connect 2.1.0) + Virtual Console로 검증했다.

## 1. 구조

```
PleosApp ── CarSession.init()                 (객체만 생성, 연결 X)
MainActivity (홈, DO 선언) ── 퍼미션 요청 → MonitorService.start()
   └ onStart/onStop: CarSession.ux.acquire/release("home")   ← 서비스를 꺼도 화면은 주행 규제를 앎
MonitorService (FGS, specialUse)
   ├ CarSession.start()
   │    ├ UxRestrictionsMonitor   CarUxRestrictionsManager 구독 (참조 카운트: session + home)
   │    ├ VehicleMonitor          기어·SOC·주행가능거리·충전포트·경고등 (capability → get → register)
   │    └ NaviController          NaviHelper 래퍼 (경로/경유지 suspend API + 상태 flow)
   ├ AlertRules   실제 신호 → DrivingAlert (경고등 새로 켜짐 / SOC 20%·10% / Navi onError)
   └ AlertNotifier  앱이 안 보이면 heads-up 알림 게시
AlertCenter ── current(StateFlow) → OneLineAlertBar (홈 상단)
ProbeActivity (DO 미선언) ── SDK Probe. MonitorService가 떠 있으면 세션의 Vehicle 인스턴스를 빌려 씀
```

| 파일 | 역할 |
|---|---|
| `core/CarSession.kt` | 전역 연결. `start/stop`은 서비스만 호출 |
| `core/VehicleMonitor.kt` | 상시 감시 신호 + `resubscribe()` |
| `core/UxRestrictionsMonitor.kt` | `UxState(restricted, activeRestrictions, maxStringLength…)`, `limit(text)`, 비트 이름 로그, DO 판정 진단 로그 |
| `core/NaviController.kt` | `requestRoute(dest, waypoints)`, `cancelRoute()`, `addWaypoint(place)`, `removeWaypoint(FIRST/SECOND/ALL)` — 결과 이벤트(`onRouteStarted`/`onRouteCancelled`/`onWaypointChanged`)·`onError`까지 대기, 요청은 Mutex로 직렬화 |
| `core/alert/*` | `AlertCenter`(1건만 표시, 높은 심각도 우선, 심각도별 표시 시간), `AlertRules`, `AlertNotifier`, `Notifications` |
| `service/MonitorService.kt` | `startForeground(type=specialUse)`, 알림의 "중지" 액션 |
| `ui/HomeScreen.kt` | `ux.restricted`면 주행 레이아웃(큰 글자 3개 + 목적지 1줄, 버튼 없음), 아니면 정차 레이아웃(상태 카드 + debug 빌드 전용 개발자 도구) |
| `ui/OneLineAlertBar.kt` | 고대비·한 줄·애니메이션 없음, `LIMIT_STRING_LENGTH` 시 자름, 탭해서 닫기 |

## 2. 검증 결과

| 항목 | 결과 | 근거 |
|---|---|---|
| FGS 시작 | ✅ `startForeground ok (type=specialUse)` | 로그 |
| **백그라운드 상시 수신** | ✅ 내비가 전면인 채 36초 경과 → `curProcState=4`(FGS), `isFrozen=false`, freezing 로그 없음. 그 상태에서 SOC 15% 변경 즉시 수신 | `dumpsys activity processes`, 로그 |
| 주행 규제 구독 | ✅ P: `requiresDO=false BASELINE` / D·N: `requiresDO=true active=0x50 [NO_VIDEO\|NO_SETUP]` — 문서의 "그외 지역" 정책과 일치 | 로그 |
| 주행 레이아웃 전환 | ✅ D에서 앱 UI가 `D / 15% / 300 / 주행가능 km`만 그림 (uiautomator dump) | UI 덤프 |
| **DO 선언 인정** | ❌ 매니페스트 `distractionOptimized=true`인데도 D에서 "주행 중에는 사용할 수 없습니다"로 가려짐 (§3) | [스크린샷](img/do-blocked-overlay.png) |
| 1줄 알림 바 (앱 보임) | ✅ 경고등 BLINK → 노란 바 "경고등 켜짐 · ENGINE_OIL_PRESSURE(깜빡임)", heads-up 생략 | [스크린샷](img/home-alert-bar.png) |
| **heads-up (앱 안 보임)** | ⚠️ 앱은 정상 게시(`heads-up posted`)하지만 **Pleos가 표시하지 않음** (§3) | 로그 |
| 경유지 래퍼 | ✅ 경로 요청(6초) → +정자역(FIRST) → +서현역(SECOND) → 첫 경유지 제거(서현역이 앞으로) → 전체 제거 → 경로 취소 → 경로 없이 추가 시 가드로 실패 | 로그 |
| 서비스 중지/재시작 | ✅ 리스너 5개 해제·release 후 새 인스턴스로 재연결 | 로그 |
| Probe 공존 | ✅ (수정 후) Probe를 열고 닫아도 세션 수신 유지 (§4) | 로그 |

## 3. 플랫폼 제약 (앱 코드로 해결 불가 → 설명회 질문)

1. **DO 선언이 사이드로드 앱에서 인정되지 않음.**
   - `CarPackageManager.isActivityDistractionOptimized(MainActivity) = false` (진단 로그).
   - Pleos 홈 화면(`HOME_SCREEN`)은 Activity를 `distractionOptimized: true`로 읽지만, 우리 앱 영역(TaskView 1682×1210)에 차단 오버레이를 덮는다 (`TaskViewContainer::updateDrivingRestrictionRegion`).
   - `CarPackageManagerService` 허용 목록은 서명이 일치하는 시스템 앱뿐(내비·미디어·전화 등). 에뮬레이터엔 앱마켓이 없고 모든 앱의 설치자가 `null`.
   - AOSP 동작상 서드파티 DO는 허용된 설치 출처(마켓)에서 설치된 경우에만 인정되는 것으로 추정 [미검증]. → **에뮬레이터 시연에서는 기어 D 동안 우리 화면이 보이지 않는다.**
2. **서드파티 heads-up이 표시되지 않음.** Pleos `NOTIFICATION_INJECTOR`(`HeadUpNotificationListenerService`)가 `isPrivilegedPackage=false`, `isSystemImportantNotification(privileged=false, platformSigned=false, importance=4)`로 판정한 뒤 띄우지 않는다. 알림 센터 아이콘도 생기지 않음.
   - 심사 가이드: "미디어 앱은 헤드업 알림 금지, 운전과 관련 없는 알림 금지" → 운전 관련 알림은 허용될 여지가 있으나 표시 경로는 미확인.
3. 결과적으로 **에뮬레이터에서 주행 중(D) 서드파티 앱이 운전자에게 무언가를 보여줄 경로가 현재 없다.** 정차(P) 화면, 또는 D→P 전환 직후 요약으로 시연을 설계해야 한다.
4. 심사 기준: **Vehicle SDK 퍼미션은 앱마켓과 사전 협의·승인된 것만 선언 가능**, 승인 안 된 퍼미션 선언 시 심사 탈락. 테스트용 더미 데이터·임시 기능 금지 → 개발자 도구는 `BuildConfig.DEBUG`에서만 보이게 함.

## 4. SDK 동작 실측 (설계 규칙)

- **같은 속성의 리스너 하나를 `unregister`하면, 앱 전체의 그 속성 구독이 서비스에서 지워진다** (참조 카운트 없음). 다른 리스너(같은 인스턴스든 다른 Vehicle 인스턴스든)가 남아 있어도 더는 콜백이 오지 않는다.
  - 근거: Probe 종료 후 서비스 로그 `collect event: …EV_BATTERY_SOC… packageNames=[com.teamsync.pleos]` 직후 `broadcastPropertyCallback`이 ambientai에만 가고 우리 앱으론 안 감.
  - 등록이 두 개인 동안에는 둘 다 받는다 (대체가 아님).
  - **규칙: 속성당 등록 주체는 하나(VehicleMonitor), 앱 안에서는 flow로 나눠 쓴다.** 예외인 Probe는 세션 인스턴스를 빌려 쓰고, 리스너를 해제하면 `VehicleMonitor.resubscribe()`를 호출한다.
- `Vehicle.release()` 후 새 `Vehicle(context).initialize()`로 재연결 가능.
- NaviHelper 인스턴스 두 개(세션 + Probe)는 한쪽을 release해도 다른 쪽 이벤트·요청에 영향 없음.
- **`addWaypoint`/`removeWaypoint` 흐름**: 요청 직후 `onRouteCancelled` → `onNavigationStatus(FREE_DRIVE)` → (약 6초 재탐색) → `onWaypointChanged` → `onRouteEnded` → `ROUTE_DRIVE` → `onWayPointInfo(갱신 목록)`. **`onRouteCancelled`만 보고 사용자가 경로를 취소했다고 판단하면 안 됨.**
- `onWaypointChanged.index`는 추가·제거한 슬롯이 아니라 **다음 경유지**(항상 FIRST였음) → 목록은 `getWaypointInfo()`로 다시 받아야 함.
- `removeWaypoint(FIRST)` 후 SECOND가 FIRST로 당겨짐. `WaypointIndex.ALL` 지원.
- 경로 안내 중이 아닐 때 `addWaypoint` 동작은 미확인 (래퍼가 호출 전에 막음).
- 모든 경로·경유지 요청은 Navigation 앱을 전면으로 올린다.
- 리스너 nullability(Kotlin): `GearSelectionListener.onSelected(VehicleGear?)`, `TelltaleInfoListener.onTelltaleInfoUpdated(Telltales?)`, SOC/주행가능거리/충전포트는 `Int?/Float?/Boolean?`, `onFailed(Exception)`은 non-null.
- `android.car`는 기기 bootclasspath(`/apex/com.android.car.framework/javalib/android.car-module.jar`)에 있어 `<uses-library>` 불필요. Pleos 전용 공유 라이브러리 `ai.pleos.playground.car`도 존재(용도 미확인).

## 5. 시연·설계에 주는 영향

- 상시 수신(FGS)은 동작 → "내비 쓰는 동안 뒤에서 감시"는 가능.
- 하지만 감시 결과를 **주행 중에 보여줄 표시 경로**(DO 화면·heads-up)가 에뮬레이터에서 막혀 있다 → 설명회에서 ① DO 인정 방식, ② 서드파티 운전 관련 알림 표시 방법을 반드시 확인.
- 확인 전까지의 시연 시나리오: 정차 중 화면 + 주행 중 감지한 것을 "정차 시 요약"으로 보여주기.
