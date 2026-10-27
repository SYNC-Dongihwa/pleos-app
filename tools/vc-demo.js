// 시연 영상용 Virtual Console 자동 조작 (docs/demo-scenario.md). 브라우저에서 http://127.0.0.1:8088/ 을 열고 개발자 도구 콘솔에 붙여넣는다.
//   await __demo.reset();            // 시작 상태로 맞춤 (기어 P, 조명·와이퍼 Off, 창문 0, 핸들 열선 Off, 도어 Lock, 트렁크 Close, 운전석 22℃)
//   await __demo.door();             // 장면 1: 운전석 문 열고 닫기 → 앱 오프닝 (앱이 홈 화면이고 튜토리얼을 시작한 적 없을 때)
//   (에뮬레이터에서 "시작하기"를 누른다)
//   await __demo.run();              // 장면 2~4: 전 단계 조작 (+ 온도 단계에서 D/P 일시정지 시연)
//   await __demo.run({ hint: true }); // 장면 중 상향등 단계에서 21초 기다려 위치 힌트도 보여줌
// 각 조작 사이 대기 시간은 앱 음성 안내가 끝날 만큼 잡았다. 실제 반영은 앱 화면과 logcat(SYNC/Tutorial)으로 확인.

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
  async function step(label, fn, waitMs) {
    fn();
    const line = `${new Date().toISOString().slice(11, 19)} ${label}`;
    log.push(line);
    console.log(line);
    await sleep(waitMs);
  }

  const SAY = 5000; // 다음 안내 음성이 끝날 시간

  window.__demo = {
    log,
    async reset() {
      await open();
      press('Gear Selection', 'P');
      press('Exterior Lights Mode', 'Off');
      press('High Beam', 'Off');
      press('Turn Signal State', 'Off');
      select('Windshield Wiper Switch', 'front'); press('Windshield Wiper Switch', 'Off');
      select('Window Position', 'front_left'); slide('Window Position', 0);
      press('Air Conditioning', 'Off');
      select('Target Temperature', 'driver'); slide('Target Temperature', 22);
      press('Steering Wheel Heat', 'Off');
      press('Door Lock', 'Lock');
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
    async run({ hint = false } = {}) {
      await open();
      await step('전조등 켜기', () => press('Exterior Lights Mode', 'Low beam'), SAY);
      await step('전조등 끄기', () => press('Exterior Lights Mode', 'Off'), SAY);
      if (hint) await sleep(21000); // 상향등 단계: 20초 무변화 → 위치 힌트 음성
      await step('상향등 켜기', () => press('High Beam', 'On'), SAY);
      await step('상향등 끄기', () => press('High Beam', 'Off'), SAY);
      await step('왼쪽 방향지시등', () => press('Turn Signal State', 'Blink left'), SAY);
      await step('오른쪽 방향지시등', () => press('Turn Signal State', 'Blink right'), SAY);
      await step('방향지시등 끄기', () => press('Turn Signal State', 'Off'), SAY);
      select('Windshield Wiper Switch', 'front');
      await step('와이퍼 작동', () => press('Windshield Wiper Switch', 'Low'), SAY);
      await step('와이퍼 끄기', () => press('Windshield Wiper Switch', 'Off'), SAY);
      select('Window Position', 'front_left');
      await step('창문 내리기', () => slide('Window Position', 40), SAY);
      await step('창문 올리기', () => slide('Window Position', 0), SAY);
      await step('에어컨 켜기', () => press('Air Conditioning', 'On'), SAY);
      await step('에어컨 끄기', () => press('Air Conditioning', 'Off'), SAY);
      select('Target Temperature', 'driver');
      await step('기어 D (일시정지 시연)', () => press('Gear Selection', 'D'), 7000);
      await step('기어 P (이어서)', () => press('Gear Selection', 'P'), 6000);
      await step('온도 21℃', () => slide('Target Temperature', 21), SAY);
      await step('온도 22℃ (원래대로)', () => slide('Target Temperature', 22), SAY);
      await step('핸들 열선 켜기', () => press('Steering Wheel Heat', 'High'), SAY);
      await step('핸들 열선 끄기', () => press('Steering Wheel Heat', 'Off'), SAY);
      await step('문 잠금 풀기', () => press('Door Lock', 'Unlock'), SAY);
      await step('문 잠그기', () => press('Door Lock', 'Lock'), SAY);
      select('Trunk / Frunk Position', 'trunk');
      await step('트렁크 열기', () => press('Trunk / Frunk Position', 'Open'), SAY);
      await step('트렁크 닫기', () => press('Trunk / Frunk Position', 'Close'), 9000);
      return log;
    },
  };
  return 'loaded __demo';
})();
