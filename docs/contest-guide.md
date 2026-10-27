# 공모전·제출 규칙·개발 환경 (CLAUDE.md에서 이동, 2026-10-27)

> 원래 CLAUDE.md §1~§3, §10. 내용은 2026-10-22 확인 기준.

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
