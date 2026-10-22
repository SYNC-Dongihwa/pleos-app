# 후보 A vs B 차별화 비교 (2026-10-22, 에뮬레이터 실측 기반)

> 근거 데이터: [sdk-probe-results.md](sdk-probe-results.md), [probe-table.md](probe-table.md). 아래에서 **[실측]**은 에뮬레이터에서 직접 확인한 것, **[미검증]**은 아직 확인 안 한 가정이다.

## 0. 결론

1. **후보 A의 핵심 기능은 순정 내비가 이미 한다.** 배터리 2%로 경로를 요청하면 내비가 스스로 충전소를 경유지에 넣고 충전 시간·도착 잔량까지 계산한다 [실측]. "도착 잔량 예측 + 충전소 경유 추가"로는 차별성이 없다 → A를 하려면 **순정이 안 하는 부분**(충전 정차 시간, 내 조건 기반 충전소 선택)으로 방향을 틀어야 한다.
2. **후보 B는 순정의 빈틈이 실측으로 확인됐다.** 경고등이 켜지면 순정 화면엔 **빨간 아이콘만** 뜨고 설명·팝업·알림이 없다 [실측]. 데이터(Telltales 실시간)도 SDK로 받힌다. 단 **`PRIVATE_CAR_SAFETY` 퍼미션이 실차/앱마켓에서 허용되는지가 성패 조건**이고, 경고등은 드물게 켜져서 **사용 빈도(Retention)가 약점**이다.
3. **권장**: B를 1순위로 두되 10/28 설명회에서 PRIVATE 퍼미션 허용 여부를 확인한 뒤 확정한다. 그 전까지는 **두 후보 모두에 필요한 공통 기반**(Foreground Service 상시 수신, DO+CarUxRestrictions, 주행 중 알림 UI, NaviHelper 경유지 래퍼)을 먼저 만든다. 불허되면 A 변형(A2+A1)으로 간다.

## 1. 실측으로 확인한 사실

### 후보 A 관련 — 순정 내비가 이미 하는 것

| 확인 내용 | 근거 |
|---|---|
| 경로 안내 시 도착 잔량 %와 **"왕복 주행 후 예상 %"** 표시 | [실측] SOC 35%, 판교역 3.1km → 화면 "34% · 왕복 주행 후 예상 33%" ([스크린샷](img/native-nav-arrival-soc.png)) |
| 배터리 부족 시 **충전소를 자동으로 경유지에 추가**, 충전 시간·도착 잔량 계산 | [실측] SOC 2% / 0.3 kWh → "배터리 부족으로 충전소를 경유합니다" (경기창조경제혁신센터 전기차충전소, 충전 6분, 도착 10%) ([스크린샷](img/native-nav-charging-waypoint.png)) |
| 위 결과가 SDK로 그대로 들어옴 | [실측] `onDrivingInfo.waypoints=[DrivingState(name=…전기차충전소, isChargingStation=true, maxChargingPower=100, chargerCount=4, remainDestinationChargingValue=13)]`, `destination.targetSoc=10`, `remainDestinationChargingValue=7740` |
| 단, SDK 쪽 `isRecommendedChargingStation=false`, `chargingPlan=null` | [실측] 자동 추가된 충전소인데도 추천 플래그·충전계획은 비어 옴 |
| `onChargerOperatorInfo` = 충전 **사업자** 목록 (id/name, 위치 없음) | [실측] HD 현대 E-pit, PI GS차지비, ME 환경부, KP 한전, ST SK일렉링크 … |
| 충전 스위치를 켜도 에뮬레이터 SOC는 저절로 오르지 않음 | [실측] 시연 시 SOC 슬라이더를 직접 올려야 함 |
| EV 전비(순간/누적) 속성은 에뮬레이터에서 미지원 | [실측] `*EvEfficiency` 전부 NOT_SUPPORTED |

### 후보 B 관련 — 순정의 빈틈

| 확인 내용 | 근거 |
|---|---|
| 경고등 ON/BLINK 시 순정 화면엔 **아이콘만** (텍스트·팝업·알림 없음) | [실측] ABS·EV 시스템·저전압 배터리·엔진오일압 ON → 클러스터 패널에 빨간 아이콘 4개뿐, 알림 0건 ([스크린샷](img/native-telltale-icons.png)) |
| 반면 도어/트렁크 열림은 순정이 텍스트 팝업으로 안내 | [실측] "트렁크 열림 — 즉시 트렁크를 닫으세요" |
| Telltales 실시간 수신 | [실측] `registerTelltales` → `TelltaleData(type, state=ON/BLINK/OFF, color, priority, warningCount)` 1초 이내 |
| 타입 95종 (EV 전용 포함: `LOW_EV_BATTERY`, `EV_SYSTEM`, `EV_CHARGING_CONNECTOR`, `EV_BATTERY_CONDITIONING`, `LOW_VOLT_BATTERY` …) | [실측] AAR `TelltaleType` |
| `color`는 콘솔 조작 시 모두 `4` → 색 매핑 불명 | [실측] `TelltaleColor` enum은 WHITE/GREEN/BLUE/YELLOW/RED/GRAY |
| 필요 퍼미션 `PRIVATE_CAR_SAFETY` | [실측] AAR 어노테이션. 에뮬레이터에선 normal로 자동 부여, **실차·심사 허용 여부 [미검증]** |
| PRIVATE 없이 쓸 수 있는 안전 신호는 빈약 | [실측] 오일·워셔액·안전벨트 경고·에어백 전개 등은 에뮬레이터 미지원. 지원: 도어/트렁크, PowerDownWarning, 에어백 on/off, 좌석벨트 체결, 타이어 저압 **임계값**(137.0)만 |

### 공통 제약 (둘 다 해당)

- 기어 D 즉시 DO 미선언 앱 차단 → 주행 중 화면은 DO 선언 + 제한 처리 필수.
- 백그라운드 ~10초 후 프로세스 freeze → 운전 중 감시는 **Foreground Service** 필요 (Pleos 시스템 앱들도 FGS 알림을 띄우고 있음 — 3rd-party 허용 여부 [미검증]).
- `requestRoute`/`addWaypoint` 계열 호출 시 Navigation 앱이 전면으로 → 우리 앱은 화면에서 밀려남. 경유지 추가는 "버튼 → 내비로 전환" 흐름으로 설계.
- Vehicle SDK에 차속 없음. 주행 맥락은 Gear + NaviHelper(`onNavigationStatus`, 남은 거리)로 판단.

## 2. 후보 A — 차별화 방향

**하지 말 것**: 도착 잔량 예측, "충전소 경유지 자동 추가" 자체 (순정과 동일).

| 방향 | 내용 | SDK 활용 | 외부 데이터 | 평가 |
|---|---|---|---|---|
| **A1. 충전 정차 코파일럿** | 충전 포트가 열리고 충전이 시작되면(=주차 상태라 주행 규제 없음) "다음 목적지까지 필요한 SOC / 지금 출발 가능 시점 / 목표 SOC 도달 예상"을 보여주고, 도달하면 알림 | `registerEvChargePortOpen`, `getEvChargeSwitchState`(폴링), `registerEvBatteryStateOfCharge`, `getEvBatteryCapacity`, NaviHelper 목적지 남은 거리·`remainDestinationChargingValue` | 없음(충전 속도는 SOC 변화량으로 추정, 목표 SOC는 사용자 입력 — 충전 한도는 SDK에 set만 있고 get 없음) | 순정이 안 하는 영역, 외부 의존 없음. 단 충전 중 SOC 상승을 에뮬레이터가 시뮬레이션하지 않아 시연은 슬라이더 조작 |
| **A2. 내 조건 충전소 재선택** | 순정이 고른 충전소(1곳, 이유 비공개)를 사용자 조건(보유 회원카드/선호 사업자, 실시간 빈자리, 요금, 출력)으로 재평가해 대안 제시 → 버튼으로 교체 | `onDrivingInfo.waypoints`(순정 선택 감지), `onChargerOperatorInfo`(사업자 코드), `removeWaypoint`/`addWaypoint` | 충전소 위치·실시간 상태·요금 공공 API [미검증] — 사업자 코드(ME, KP, PI …)가 공공 API 코드와 같아 보임 [미검증] | 차별화 가능하나 외부 API 품질에 좌우. 순정 기능 "위에" 얹는 구조라 심사에서 "순정과 뭐가 다른가" 설명 필요 |
| A3. 공조·주행 습관 소비 코칭 | "에어컨 끄면 +x km" 등 | HVAC 상태 + SOC/주행거리 | 없음 | 전비 속성 미지원 → 자체 모델 필요, 에뮬레이터 가속 주행 재현 실패로 **시연 근거 부족**. 비추천 |

## 3. 후보 B — 차별화 방향

| 방향 | 내용 | SDK 활용 | 외부 데이터 | 평가 |
|---|---|---|---|---|
| **B1. 경고등 해석 + 심각도 + 행동** | 주행 중: 고대비 1줄 + 심각도 색(즉시 정차 / 오늘 점검 / 참고). 정차 시: 의미·원인·조치 상세. 관련 차량 상태로 맥락 보강 (TPMS ↔ 저압 임계값, 저전압 ↔ PowerDownWarning, EV 시스템 ↔ SOC·충전 상태) | `registerTelltales`, Tire/EvBattery/Door 상태, Gear·CarUxRestrictions | 경고등 설명 콘텐츠 — **자체 작성** (제조사 매뉴얼 문구 복제 금지) | 순정 공백이 실측으로 확인된 핵심 |
| **B2. 목적지 연계 판단 + 정비 경유** | "목적지까지 12 km — 계속 주행 가능 / 가까운 곳에 정차" 판단, 필요하면 서비스센터·정비소를 경유지로 추가 | NaviHelper `onDrivingInfo`(남은 거리·시간), `getCurrentLocationInfo`, `addWaypoint` | 정비업체 위치 공공데이터 [미검증] | 차량 기능 + 내비 결합 → 차량특화성·기술활용성 점수에 유리 |
| B3. 이력·공유 | 경고 발생 이력 타임라인, 정차 시 정비소·보험사 공유 | 로그 저장 | 없음 | 부가 기능 |
| **B4. 출발 전 30초 점검 (Retention 보완)** | P 상태에서 시동 직후 도어/트렁크·타이어 임계·배터리·켜진 경고등을 한 화면 요약 | 비-PRIVATE 데이터 다수 사용 가능 | 없음 | 경고등은 드물게 켜지므로 **매일 쓰는 이유**를 만들어 줌 |

**B의 리스크**
- `PRIVATE_CAR_SAFETY` 불허 시 B1·B2의 데이터 원천이 사라짐 → 사실상 불가 (비-PRIVATE 안전 신호는 빈약).
- 경고등 의미는 차종별로 다를 수 있음 → "일반적 의미 + 매뉴얼 확인 권고" 수준으로, 단정적 진단 금지.
- `color` 값 매핑 불명 → 심각도는 타입 기준 자체 테이블로.

## 4. 비교표

| 기준 | A (A1+A2 변형) | B (B1+B2+B4) |
|---|---|---|
| 차량특화성 | 충전 정차·내비 결합 — 높음 | 경고등·내비·정비 결합, 차 없으면 무의미 — **매우 높음** |
| 창의성 / 순정과 겹침 | 순정이 핵심 기능 보유 → **설명 부담 큼** | 순정은 아이콘만 → **빈틈 명확** |
| 기술 활용성 | EvBattery 5종 + NaviHelper 경유지 API | Telltales + Tire/EvBattery/Door + NaviHelper 경유지 API |
| 사업성 | EV 운전자, 충전은 주 1~2회 → **사용 빈도 높음**. 충전 사업자 제휴·결제 | 전 차량 운전자 + 렌터카/카셰어링/리스(B2B 관리자 연동), 정비 예약 제휴. 경고 빈도 낮음 → B4로 보완 필요 |
| 퍼미션 리스크 | 낮음 (CAR_ENERGY, NAVI_ROUTE_SEARCH) | **높음** (PRIVATE_CAR_SAFETY) |
| 외부 데이터 의존 | A2는 충전소 실시간 API 필수 | 설명 콘텐츠(자체 작성), B2는 정비소 데이터 |
| 2차 PT 시연 | SOC·충전 포트 슬라이더 조작 (충전 진행은 수동) | Virtual Console 경고등 37종 ON/BLINK → 즉시 반응, **시연 임팩트 큼** |
| 구현 난이도 (11/10 M2) | 중 (A2의 외부 API 연동이 변수) | 중 (콘텐츠 작성량이 변수) |

## 5. 다음 단계 제안

1. **지금~10/28 (공통 기반)** — 어느 쪽으로 가든 필요:
   - Foreground Service에서 Vehicle/NaviHelper 상시 수신 (백그라운드 freeze 대응)
   - DO 선언 + `CarUxRestrictionsManager` 처리, 주행 중 1줄 알림 UI
   - NaviHelper 경유지 추가/제거 래퍼 + 내비 전면 전환 흐름
2. **10/28 설명회 질문** (CLAUDE.md §8): ④ PRIVATE_CAR_SAFETY/INFO 허용 ⑤ Foreground Service 허용·타입 ⑥ DO 선언 방식.
3. **10/28 이후 확정**: 허용 → **B (B1+B2+B4)** / 불허 → **A (A1+A2)**.
4. 결정되면 CLAUDE.md §6 갱신 + §9 기록.
