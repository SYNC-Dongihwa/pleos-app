// 시연 영상용 Virtual Console 자동 조작 (docs/demo-scenario.md). 브라우저에서 http://127.0.0.1:8088/ 을 열고 개발자 도구 콘솔에 붙여넣는다.
//   await __demo.reset();    // 시작 상태로 맞춤 (기어 P, 조명·와이퍼·열선 Off, 창문 0, 도어 Lock, 트렁크·보닛·충전구 닫힘, 운전석 22℃ …)
//   await __demo.door();     // 운전석 문 열고 닫기 → 앱 오프닝 (홈 화면 + 튜토리얼을 시작한 적 없을 때)
//   (에뮬레이터에서 코스 카드의 "시작"을 누른 뒤 해당 코스 함수를 실행)
//   await __demo.fuel();     // 주유·충전: 주유구(전기차는 자동 '지원 안 됨') → 충전구 → 보닛
//   await __demo.basic();    // 기본 조작 8단계 (+ 창문 단계 뒤 D/P 일시정지 시연). { hint: true }면 상향등에서 21초 대기 → 위치 힌트
//   await __demo.weather();  // 비·김서림 / await __demo.climate(); // 추위·공조 / await __demo.family(); // 아이·야간
//   await __demo.all();      // "전체" 코스 순서대로 전부
// 대기 시간은 앱 음성 안내가 끝날 만큼 잡았다. 실제 반영은 앱 화면과 logcat(SYNC/Tutorial)으로 확인.

(() => {
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const dialog = () => document.querySelector('[role=dialog]');

  async function open() {
    if (dialog()) return;
    const emu = [...document.querySelectorAll('*')].find((e) => e.childElementCount === 0 && e.textContent.trim() === 'Emulation Controls');
    emu.closest('button,div[role=button],div').click();
    await sleep(1000);
  }
  function card(title) {
    const els = [...dialog().querySelectorAll('*')].filter((e) => e.childElementCount === 0 && e.textContent.trim() === title);
    if (!els.length) throw new Error('no card ' + title);
    let c = els[0];
    while (c && !(c.querySelector('input,button,select') && c.innerText.includes('Publish'))) c = c.parentElement;
    return c;
  }
  function press(title, label) {
    const b = [...card(title).querySelectorAll('button')].find((x) => x.innerText.trim() === label);
    if (!b) throw new Error(`no button ${label} in ${title}`);
    b.click();
  }
  function select(title, value) {
    const s = card(title).querySelector('select');
    Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set.call(s, value);
    s.dispatchEvent(new Event('change', { bubbles: true }));
  }
  function slide(title, v) {
    const el = card(title).querySelector('input[type=range]');
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, String(v));
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    el.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
  }

  const log = [];
  async function step(label, fn, waitMs = SAY) {
    fn();
    const line = `${new Date().toISOString().slice(11, 19)} ${label}`;
    log.push(line);
    console.log(line);
    await sleep(waitMs);
  }
  const SAY = 5000; // 다음 안내 음성이 끝날 시간
  const END = 9000; // 코스 결과 음성

  const courses = {
    async fuel() {
      // 주유구: 에뮬레이터(전기차)는 FUEL_DOOR_OPEN이 없어 앱이 자동으로 넘어감 → 충전구부터
      await step('충전구 열기', () => press('Charge Port', 'Open'));
      await step('충전구 닫기', () => press('Charge Port', 'Closed'));
      select('Trunk / Frunk Position', 'frunk');
      await step('보닛 열기', () => press('Trunk / Frunk Position', 'Open'));
      await step('보닛 닫기', () => press('Trunk / Frunk Position', 'Close'), END);
      select('Trunk / Frunk Position', 'trunk');
    },
    async basic({ hint = false } = {}) {
      await step('전조등 켜기', () => press('Exterior Lights Mode', 'Low beam'));
      await step('전조등 끄기', () => press('Exterior Lights Mode', 'Off'));
      if (hint) await sleep(21000); // 상향등 단계: 20초 무변화 → 위치 힌트 음성
      await step('상향등 켜기', () => press('High Beam', 'On'));
      await step('상향등 끄기', () => press('High Beam', 'Off'));
      await step('왼쪽 방향지시등', () => press('Turn Signal State', 'Blink left'));
      await step('오른쪽 방향지시등', () => press('Turn Signal State', 'Blink right'));
      await step('방향지시등 끄기', () => press('Turn Signal State', 'Off'));
      select('Windshield Wiper Switch', 'front');
      await step('와이퍼 작동', () => press('Windshield Wiper Switch', 'Low'));
      await step('와이퍼 끄기', () => press('Windshield Wiper Switch', 'Off'));
      select('Window Position', 'front_left');
      await step('창문 내리기', () => slide('Window Position', 40));
      await step('창문 올리기', () => slide('Window Position', 0));
      await step('기어 D (일시정지 시연)', () => press('Gear Selection', 'D'), 7000);
      await step('기어 P (이어서)', () => press('Gear Selection', 'P'), 6000);
      await step('문 잠금 풀기', () => press('Door Lock', 'Unlock'));
      await step('문 잠그기', () => press('Door Lock', 'Lock'));
      select('Trunk / Frunk Position', 'trunk');
      await step('트렁크 열기', () => press('Trunk / Frunk Position', 'Open'));
      await step('트렁크 닫기', () => press('Trunk / Frunk Position', 'Close'), END); // 주행 모드는 에뮬레이터 미지원 → 자동 종료
    },
    async weather() {
      select('Defroster', 'front_defroster_on');
      await step('앞유리 김서림 제거 켜기', () => press('Defroster', 'On'));
      await step('앞유리 김서림 제거 끄기', () => press('Defroster', 'Off'));
      select('Defroster', 'rear_defroster_on');
      await step('뒷유리 열선 켜기', () => press('Defroster', 'On'));
      await step('뒷유리 열선 끄기', () => press('Defroster', 'Off'));
      select('Windshield Wiper Switch', 'rear');
      await step('뒷유리 와이퍼 작동', () => press('Windshield Wiper Switch', 'Low'));
      await step('뒷유리 와이퍼 끄기', () => press('Windshield Wiper Switch', 'Off'));
      select('Windshield Wiper Switch', 'front');
      await step('내기 순환', () => press('Air Recirculation', 'Recirculate'));
      await step('외기 유입', () => press('Air Recirculation', 'Fresh'), END);
    },
    async climate() {
      await step('에어컨 켜기', () => press('Air Conditioning', 'On'));
      await step('에어컨 끄기', () => press('Air Conditioning', 'Off'));
      select('Target Temperature', 'driver');
      await step('온도 21℃', () => slide('Target Temperature', 21));
      await step('온도 22℃ (원래대로)', () => slide('Target Temperature', 22));
      await step('오토 공조 켜기', () => press('HVAC Auto', 'On'));
      await step('오토 공조 끄기', () => press('HVAC Auto', 'Off'));
      await step('바람 세기 4', () => slide('Fan Speed', 4));
      await step('바람 세기 3 (원래대로)', () => slide('Fan Speed', 3));
      select('Seat Temperature', 'driver');
      await step('시트 열선 H2', () => press('Seat Temperature', 'H2'));
      await step('시트 열선 끄기', () => press('Seat Temperature', 'Off'));
      await step('핸들 열선 켜기', () => press('Steering Wheel Heat', 'High'));
      await step('핸들 열선 끄기', () => press('Steering Wheel Heat', 'Off'), END);
    },
    async family() {
      // 창문 잠금은 콘솔 "all"이 전체 창문 묶음 Area로 발행 — 앱도 그 Area를 구독
      select('Window Lock', 'all');
      await step('창문 잠금', () => press('Window Lock', 'Lock'));
      await step('창문 잠금 해제', () => press('Window Lock', 'Unlock'));
      await step('실내등 켜기', () => press('Cabin Lights Switch', 'On'));
      await step('실내등 끄기', () => press('Cabin Lights Switch', 'Off'));
      // 에뮬레이터는 실내등을 켜면 뒷좌석 독서등도 같이 켜질 수 있음 → 앱이 시작 시점 값에 맞춰 "꺼 보세요"부터 안내
      select('Reading Lights', 'rear_left');
      await step('독서등 On', () => press('Reading Lights', 'On'));
      await step('독서등 Off', () => press('Reading Lights', 'Off'), END);
    },
  };

  window.__demo = {
    log,
    async reset() {
      await open();
      press('Gear Selection', 'P');
      press('Exterior Lights Mode', 'Off');
      press('High Beam', 'Off');
      press('Turn Signal State', 'Off');
      select('Windshield Wiper Switch', 'front'); press('Windshield Wiper Switch', 'Off');
      select('Windshield Wiper Switch', 'rear'); press('Windshield Wiper Switch', 'Off');
      select('Windshield Wiper Switch', 'front');
      select('Window Position', 'front_left'); slide('Window Position', 0);
      press('Air Conditioning', 'Off');
      select('Target Temperature', 'driver'); slide('Target Temperature', 22);
      press('HVAC Auto', 'Off');
      slide('Fan Speed', 3);
      select('Seat Temperature', 'driver'); press('Seat Temperature', 'Off');
      press('Steering Wheel Heat', 'Off');
      select('Defroster', 'front_defroster_on'); press('Defroster', 'Off');
      select('Defroster', 'rear_defroster_on'); press('Defroster', 'Off');
      press('Air Recirculation', 'Fresh');
      select('Window Lock', 'all'); press('Window Lock', 'Unlock');
      press('Cabin Lights Switch', 'Off');
      select('Reading Lights', 'rear_left'); press('Reading Lights', 'Off');
      press('Door Lock', 'Lock');
      press('Charge Port', 'Closed');
      select('Trunk / Frunk Position', 'frunk'); press('Trunk / Frunk Position', 'Close');
      select('Trunk / Frunk Position', 'trunk'); press('Trunk / Frunk Position', 'Close');
      select('Door Position', 'front_left'); press('Door Position', 'Close');
      await sleep(1500);
      return 'reset done';
    },
    async door() {
      await open();
      select('Door Position', 'front_left');
      await step('운전석 문 열림', () => press('Door Position', 'Open'), 3000);
      await step('운전석 문 닫힘', () => press('Door Position', 'Close'), 8000);
      return log.slice(-2);
    },
    async fuel() { await open(); await courses.fuel(); return log; },
    async basic(opts) { await open(); await courses.basic(opts); return log; },
    async weather() { await open(); await courses.weather(); return log; },
    async climate() { await open(); await courses.climate(); return log; },
    async family() { await open(); await courses.family(); return log; },
    // "전체" 코스 순서: 기본 → 주유·충전 → 비·김서림 → 추위·공조 → 아이·야간 (D/P 시연 포함)
    async all() {
      await open();
      await courses.basic();
      await courses.fuel();
      await courses.weather();
      await courses.climate();
      await courses.family();
      return log;
    },
  };
  return 'loaded __demo';
})();
