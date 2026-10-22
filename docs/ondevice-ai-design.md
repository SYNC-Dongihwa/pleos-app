# Pleos API 코어 + 온디바이스 AI 부가 — 실측 기반 설계 (2026-10-22)

> 방향: **판단과 데이터는 Pleos Vehicle·NaviHelper가 코어**, AI는 "운전자에게 전달하는 방법(음성)"과 "개인화"를 맡는 부가 재료.
> 상태 표기: **[실측]** 에뮬레이터에서 직접 확인 / **[구현 확인]** 플랫폼 코드에 실제 구현이 있음(동작은 미실측) / **[추정]** / ❌ 불가.

## 핵심 결론
1. **Pleos TextToSpeech SDK 온디바이스 모드가 동작한다 [실측].** 문서상 "준비 중"이지만 Maven에 3.3.1이 공개돼 있고, 에뮬레이터 CaaS가 한국어 음성(화자 seoyoon)을 로컬에서 합성한다.
2. **주행 중(기어 D) + 다른 앱(Navigation) 전면에서도 Foreground Service가 낸 음성이 재생된다 [실측].** 같은 순간 heads-up은 Pleos `NOTIFICATION_INJECTOR`가 차단했다. 사이드로드 앱은 DO도 인정되지 않는다. → **에뮬레이터에서 서드파티 앱이 주행 중 운전자에게 닿는 유일한 경로가 음성.**
3. **온디바이스 LLM은 에뮬레이터에 없다 [실측].** CaaS(4.2.10-coral-aosp)의 `OnDeviceLLMManagerImpl`은 9개 메서드 전부가 "ignored: on-device LLM is disabled" 로그만 남기는 스텁이다. 오류 콜백도 오지 않아 응답을 무한정 기다리게 된다. → **LLM은 핵심 경로에 넣지 않는다.**
4. 온디바이스 STT는 CaaS에 실제 구현(`OnDeviceSttManagerImpl`)이 있다 [구현 확인]. 마이크 권한이 필요해 아직 실측하지 않았다.

## 쓸 수 있는 온디바이스 AI (실측표)

| 수단 | 상태 | 수치·근거 | 사용 조건 / 주의 |
|---|---|---|---|
| **Pleos TTS `Mode.ON_DEVICE`** (`ai.pleos.playground:TextToSpeech:3.3.1`) | ✅ [실측] | initialize 0.15s / 첫 모델 로드 0.4~0.9s / 3.8초 분량 문장 합성 0.29s / 요청→재생 시작: 첫 호출 ≈0.5s, 이후 ≈0.07s / 22.05kHz mono | `pleos.car.permission.TTS_SERVICE` 필수(SDK `ApiChecker`가 확인, 없으면 SecurityException) · `addEventListener`는 initialize 성공 **후에만**(이전이면 SDK 내부 크래시) · 로캘은 `Locale.KOREA`(`ko`만 주면 엔진 오류 → CaaS가 재시작 전까지 멈춤) · `speak(text, isFinal=true)` (false면 다음 조각을 기다리며 끝나지 않음 = **스트리밍 입력 지원**) |
| Pleos STT `Mode.ON_DEVICE` (`SpeechToText:3.3.1`) | ⚠️ [구현 확인] | CaaS `OnDeviceSttManagerImpl`, `IConnectHmgOnDeviceStt` 존재 | `RECORD_AUDIO` + `STT_SERVICE` 필요 [추정]. 미실측 |
| Pleos LLM `Mode.ON_DEVICE` (`LLM:2.1.3.2`) | ❌ [실측] | 에뮬레이터 CaaS 스텁, "handleLlmModelShare - on-device LLM disabled, skip extraction" | 실차 탑재 여부 미확인 → 설명회 질문 ⑨. 진단 코드는 커밋 `e57ad4b`에 보존 |
| Pleos LLM `Mode.SERVER` | 미시험 | `registerApp(id, secret)` 필요 | 온디바이스가 아님. Playground 자격 증명 필요(커밋 금지 대상) |
| Android 표준 `TextToSpeech` | ❌ [실측] | 엔진(CaaS)은 보이지만 init status=-1 | — |
| 앱에 LLM 직접 번들 (예: 2B급 int4 ≈1.3GB) | ❌ [추정] | 에뮬레이터 RAM 4GB, 측정 시점 여유 215MB·스왑 806MB 사용 중 | PT 시연 환경에서 비현실적 |
| 앱 내부 소형 모델 (통계·경량 ML, Kotlin 구현) | ✅ | 외부 의존 없음 | 학습 데이터는 차량 신호 로그(앱 내부 보관) |

## 설계 원칙
- **코어는 AI 없이도 완결.** 감지·판단은 Pleos 신호 규칙으로 하고, 음성은 같은 결과를 눈을 쓰지 않는 경로로 전달한다. TTS 초기화가 실패하면 화면 표시만 하고 "음성 안내 불가"를 그대로 로그·표시한다(§0: 목업·대체 음성 금지).
- **LLM 없이 문장 생성.** 응급·안전 문장은 구조화된 데이터(위치·방향·인원)로 템플릿을 채우는 쪽이 결정적이고 검증 가능하다. LLM이 실차에서 허용되면 비핵심 경로(정차 시 주행 요약 등)에만 얹는다.
- **음성 = 센서로 확인되는 코치.** 음성으로 지시하고, 다음 단계는 Pleos 신호가 확인됐을 때 자동으로 넘어간다. 예: "트렁크를 여세요" → `Door(REAR)` 0→100 → 다음 안내. 이렇게 하면 SDK가 장식이 아니라 흐름의 엔진이 된다(심사 기준 "기술 활용성").
- 주행 중엔 짧게(1문장), 정차 중엔 화면 + 음성.

## 후보별 적용

### D. 고속도로 2차 사고 대피 가이드 (현재 1순위 후보, [secondary-accident-api.md](secondary-accident-api.md))
| 단계 | 코어 (Pleos / AAOS) | AI 부가 |
|---|---|---|
| 비정상 정차 감지 | 차속(AAOS `PERF_VEHICLE_SPEED`)·기어·주차 브레이크·경로 정체 여부·도착 이벤트 | (후속) 개인 정차 패턴 학습으로 오탐 감소 — 톨게이트·휴게소·상습 정체 지점 |
| 비트박스 안내 | 트렁크·도어 위치, 야간(`NIGHT_MODE`) | **음성 코치**: 단계마다 말하고 센서로 확인되면 다음 단계 |
| 대피 확인 | 좌석 점유 5좌석 | 음성: "2열 오른쪽에 아직 사람이 있습니다" |
| 신고 준비 | NaviHelper 위치·heading·주소 | 템플릿 문장을 화면 + 음성으로 (읽어 줄 문장 미리 듣기) |
| 응답 | — | (STT 실측 후) "대피 완료" / "도움 필요" 음성 응답 |
| 이후 | `requestTrip`으로 안전지대·정비소 ETA 비교 | 음성으로 결과 안내 |

### 펫밀리 ([idea-petmily.md](idea-petmily.md))
- 코어: 창문·도어·좌석 점유·공조 온도·외기 온도·기어.
- AI 부가: 휴게소 출발 전 **음성 롤콜**("콩이 탔나요?" + 좌석·도어 확인), 하차 후 방치 경고 음성, 가족 탑승 패턴(요일·좌석) 학습 → 출발 전 체크 자동 구성.

### A. 응급 내비 ([idea-social-issues.md](idea-social-issues.md))
- 코어: `requestTrip`(화면 전환 없이 ETA 0.2초) 다중 비교.
- AI 부가: **주행 중 음성으로 결과 안내**("○○병원 12분") — 화면이 막히는 순간에 바로 필요한 기능.

### 공통 기반 (오늘 반영)
- 경고등·배터리 알림(WARN 이상)을 앱이 뒤에 있거나 주행 중이면 음성으로 읽는다 — `AlertNotifier`.

## 시연 시나리오 (에뮬레이터 + Virtual Console)
1. 기어 D, Navigation 전면 → 콘솔에서 경고등 ON → heads-up 없이 **음성 경고** (실측 완료).
2. (D안) 기어 P + 정차 → 음성 코치 시작 → 콘솔에서 트렁크 열기·도어 열기·좌석 빈자리 → 단계별 음성 → "모두 대피 확인" → 신고 문구 + 안전지대 ETA.

## 남은 확인
- 경고등 이름 음성용 한글화 (현재 `EV_SYSTEM` 같은 enum을 그대로 읽음).
- 내비 음성 안내와 겹칠 때 오디오 포커스·더킹 동작 (미확인).
- STT 실측 (`RECORD_AUDIO` + `STT_SERVICE`, 에뮬레이터 마이크).
- 설명회 질문 ⑨: TTS·STT SDK(문서상 준비 중)를 공모전에서 써도 되는지, `TTS_SERVICE` 승인 대상인지, 실차 온디바이스 LLM 탑재·서드파티 허용, LLM 서버 모드 자격 발급, 주행 중 서드파티 음성 안내 정책.

## 실측 로그 (발췌)
```
# 주행 중 + Navigation 전면 (2026-10-22 01:01)
PLEOS/Vehicle: telltales → EV_SYSTEM:ON, ABS:ON (live)
PLEOS/Notify: heads-up posted '경고등 켜짐 · EV_SYSTEM'
PLEOS/Notify: speak (visible=false driving=true) '경고등 켜짐 · EV_SYSTEM'
PLEOS/Speech: event onStart
NOTIFICATION_INJECTOR: isSystemImportantNotification(com.teamsync.pleos: privileged=false, platformSigned=false, ...)
[GAAS]OnDeviceStreamPlayerImpl: (jobId=2) generate(com.teamsync.pleos:ondevice) complete
PLEOS/Speech: event onDone

# 온디바이스 LLM
[GAAS]OnDeviceLLMManagerImpl: [onDeviceLLM] startLLM ignored: on-device LLM is disabled
[GAAS]OnDeviceLLMManagerImpl: [onDeviceLLM] initialize(com.teamsync.pleos) ignored: on-device LLM is disabled
```
