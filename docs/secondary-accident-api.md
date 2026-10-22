# 고속도로 2차 사고 방지 — 쓸 수 있는 API 지도 (2026-10-22)

> 배경: [idea-social-issues.md](idea-social-issues.md) ⑨/D안. 2차 사고 치사율이 일반 사고의 6.5배, 2026년 1~5월 사망 급증.
> 도로공사 공식 행동요령 **"비트박스"**: 사고·고장 시 갓길로 이동 → **비상등** 켜기 → **트렁크** 열기 → **가드레일 밖** 대피 → 112·119·도로공사 콜센터 신고 [출처 1].
> 상태 표기: **[실측]** 에뮬레이터에서 직접 확인 / **[AAR]** SDK 클래스로 존재만 확인 / **[미확인]** / ❌ 불가.
> 퍼미션 등급은 에뮬레이터 `dumpsys package permission` 실측. Pleos `pleos.car.permission.*`은 에뮬레이터에선 모두 normal이지만 **앱마켓 사전 승인 대상**(심사 기준).

## 핵심 결론
- **D안의 핵심 흐름(비정상 정차 감지 → 비트박스 단계 안내 → 좌석 센서로 대피 확인 → 위치·방향 신고 문구 → 안전지대 경로)은 지금 있는 API로 구현 가능.**
- **Pleos SDK에 없는 차속은 AAOS 표준 API로 받을 수 있다**: `PERF_VEHICLE_SPEED`, 권한 `android.car.permission.CAR_SPEED`(dangerous → 서드파티도 런타임 허용으로 사용 가능) [실측: 읽기 0.0, 변화 콜백 등록 성공 / 값 변화는 에뮬레이터 가속 재현 불가로 미확인].
- **비상등 상태는 서드파티가 읽을 수 없다**: Pleos `TurnSignalValue`는 `OFF/LEFT/RIGHT`뿐 [AAR], AAOS `HAZARD_LIGHTS_STATE`는 `CAR_EXTERIOR_LIGHTS`(signature|privileged) → `SecurityException` [실측]. → 비상등은 "켜셨나요?" 수동 확인 단계로.
- 다가오는 차에 경고하는 쪽(예방)은 데이터는 일부 있으나 **주행 중 표시 경로가 에뮬레이터에 없음**(DO 미인정·heads-up 미표시) → 정차 시 요약·출발 전 브리핑만 가능.

## 단계별 API

### ① 도로 위 비정상 정차 감지

| 필요한 정보 | API | 퍼미션(등급) | 상태 |
|---|---|---|---|
| 차가 멈췄나 (차속 0) | AAOS `CarPropertyManager` `PERF_VEHICLE_SPEED` (m/s) | `android.car.permission.CAR_SPEED` (dangerous) | [실측] 읽기·콜백 OK, 변화 미확인 |
| 기어 P/N | `Vehicle.getGear().registerGearSelection` | `CAR_DRIVE` | [실측] 실시간 |
| 주차 브레이크 | AAOS `PARKING_BRAKE_ON` | `CAR_POWERTRAIN` (normal) | [실측] 읽기 |
| 이동 여부 보조 | `Odometer.registerDistanceDriven`, NaviHelper `getCurrentLocationInfo` 위치 변화 | `CAR_DRIVE`, `NAVI_ROUTE` | [실측] |
| 고속도로 위인가 | NaviHelper `onCurrentRoadSpeedLimit`(도로 제한속도 80~110 → 고속도로 추정), `onTBTInfo` 방면(IC 등), `RouteStateInfo.trafficStatus.congestionItemList[].roadName` | `NAVI_ROUTE` | [실측] 콜백 수신 (정지 시 제한속도 0 반복 — 주행 중 값 미확인) |
| 정체로 선 것과 구분 | `RouteStateInfo.trafficStatus` 정체 구간(`TrafficCongestionType` HEAVY·SLOW, 평균속도), 경로상 `TrafficEventInfo`(`ACCIDENT` 등) | `NAVI_ROUTE` | [실측] 정체 목록 수신 / 사고 이벤트는 [AAR] (에뮬레이터에선 빈 목록) |
| 의도된 정차 제외 | `onDestinationArrived`, `onWayPointArrived`, 경유지가 휴게소인지 | `NAVI_ROUTE` | [AAR] 콜백 존재 (도착 이벤트는 실제 주행 필요) |
| 고장 원인 | `Safety.hasPowerDownWarningIndicator`(출력 저하 경고), `EvBattery` SOC(방전), `Safety.registerTelltales`(EV_SYSTEM·MASTER_WARNING 등) | `CAR_SAFETY`, `CAR_ENERGY`, 경고등은 `PRIVATE_CAR_SAFETY` | [실측] |
| 충돌 여부 | `Safety.hasSeatAirbagBeenDeployed` | `CAR_SAFETY` | 에뮬레이터 미지원(속성 없음) |

**판정 예**: 경로 안내 중 + 차속 0이 N초 이상 + (P 또는 주차 브레이크) + 정체 구간 아님 + 목적지·경유지 아님 → "도로 위 정차" 화면.

### ② 비트박스 단계 안내 (정차 화면 — 에뮬레이터 표시 가능)

| 단계 | 자동 확인 API | 상태 |
|---|---|---|
| 비상등 | ❌ 읽기 불가 → 수동 체크 | — |
| 트렁크 열기 | `Door.registerDoorPosition(DoorArea(REAR))` 0↔100 | [실측] |
| 탑승자 하차 시작 | 도어별 위치(`ROW_1_LEFT` 등) | [실측] |
| 야간이면 추가 주의 | AAOS `NIGHT_MODE` (`CAR_EXTERIOR_ENVIRONMENT`, normal), `Light` 미등·전조등 상태 | [실측] |

### ③ 대피 확인 (모두 차 밖으로)

| 필요한 정보 | API | 상태 |
|---|---|---|
| 좌석에 남은 사람 | `Seat.registerSeatOccupancy` (5좌석) | [실측] 빈자리 전환 실시간 (콘솔 "탑승" 복귀 이벤트는 에뮬레이터 이슈) |
| 벨트 상태 | `Seat.getCurrentSeatBeltBuckledState` | [실측] Get, 콘솔 조작 불가 |
| 누가 탔었나 | 펫밀리 탑승 명단(앱 내부) | — |

→ "차 안에 아직 1명(2열 오른쪽)" 같은 경고. 반려견 리드줄 안내.

### ④ 신고 준비

| 필요한 정보 | API | 상태 |
|---|---|---|
| 위치·주소·진행 방향 | NaviHelper `getCurrentLocationInfo` (위도·경도·heading·주소) | [실측] |
| 상행/하행 | heading + 경로 TBT 방면으로 추정 | [미확인] 정확도 |
| 이정표(km) | ❌ SDK에 없음 | — |
| 전화 연결 | 표준 Android 다이얼 인텐트(블루투스 연결 폰) — **자동 발신은 하지 않고 준비만** | [미확인] 에뮬레이터 |

### ⑤ 견인·이동 후

| 필요한 정보 | API | 상태 |
|---|---|---|
| 가장 가까운 안전지대(휴게소·졸음쉼터)·정비소까지 | `NaviHelper.requestTrip` (화면 전환 없이 ETA 비교), `requestRoute` | [실측] |

### ⑥ 다가오는 차 쪽 (예방)

| 필요한 정보 | API | 상태 |
|---|---|---|
| 경로 앞 사고 | `RouteStateInfo.trafficStatus.trafficEventList` → `TrafficEventType.ACCIDENT` (위치·시작 시각·설명) | [AAR] (에뮬레이터 실데이터 미확인) |
| ACC 사용 중 | `DrivingMode.registerCruiseControlState` (`ACTIVATED`, `SUSPENDED` 등) | [실측] 초기값 수신 / 콘솔 변경 이벤트 미수신 |
| 내 차속 | AAOS `PERF_VEHICLE_SPEED` | [실측] 읽기 |
| 운전자에게 표시 | ❌ 에뮬레이터: DO 미인정·heads-up 미표시 → 정차 시 요약·출발 전 브리핑만 | [실측] |

## SDK·플랫폼에 없는 것 (설계에서 빼야 할 것)
- 비상등 상태 (서드파티 불가) / 가속도·충격 센서 / 후방 접근 차량 감지(레이더) / 차로·이정표 정보 / 자동 신고(eCall은 순정 영역).
- 공공 **고속도로 실시간 사고 API**는 이번 검색에서 공식 명세를 찾지 못함 — 지자체 돌발상황 데이터셋, 과거 사고 통계만 확인 [출처 2·3]. (내비 경로 이벤트의 `ACCIDENT`가 대안)

## 필요한 퍼미션 (D안 MVP 기준)
`CAR_DRIVE`, `CAR_DOORS`, `CAR_SEATS`, `CAR_SAFETY`, `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`(안전지대 경로) + AAOS `CAR_SPEED`(런타임 허용), `CAR_POWERTRAIN`, `CAR_EXTERIOR_ENVIRONMENT` (+ 선택 `PRIVATE_CAR_SAFETY` 경고등).
⚠️ AAOS 표준 퍼미션 사용이 Pleos 앱마켓 심사에서 허용되는지는 미확인 → 설명회 질문.

## 진단 코드
`UxRestrictionsMonitor.logAaosProperties()`가 연결 시 AAOS 속성(차속·주차 브레이크·야간·외기온도·비상등)을 읽어 `PLEOS/UX` 로그로 남긴다. 실측 로그(2026-10-22):
```
AAOS PERF_VEHICLE_SPEED(m/s) = 0.0
AAOS PARKING_BRAKE_ON = false
AAOS NIGHT_MODE = false
AAOS ENV_OUTSIDE_TEMPERATURE(C) = 25.0
AAOS HAZARD_LIGHTS_STATE failed: SecurityException: requires android.car.permission.CAR_EXTERIOR_LIGHTS
AAOS speed callback registered=true
```
(CAR_SPEED는 시험을 위해 `pm grant --user 10`으로 부여. 실제 앱은 런타임 요청 필요)

## 출처
1. 도로공사 '비트박스' 행동요령 — https://m.boannews.com/html/detail.html?idx=128068 , https://mobile.newsis.com/view/NISX20260730_0003729913
2. 지자체 돌발상황 데이터셋(공공데이터포털) — https://data.go.kr/biz/dcat/metadata/15131594.do , 경기도교통정보센터 https://gits.gg.go.kr/web/center/webCenterTrafficInfo1.do
3. 도로공사 고속도로 사고 통계 데이터셋 — https://www.data.go.kr/dcat/metadata/15145192
