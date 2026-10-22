// Virtual Console(http://127.0.0.1:8088/) 조작 헬퍼 — 브라우저 개발자 도구 콘솔(또는 자동화 도구의 JS 실행)에 붙여넣는다.
// 화면 클릭 대신 Emulation Controls 다이얼로그의 카드 제목으로 값을 바꾼다 (요소 참조가 자주 바뀌어서 만든 것).
// 사용 예:
//   await __vcOpen();
//   __vc.press('Gear Selection', 'D');          // 버튼 카드
//   __vc.select('Telltales', 'ABS'); __vc.press('Telltales', 'On');
//   __vc.slide('Steering Angle', 0);            // 슬라이더 카드
// 주의:
//   - 카드의 상태 글자(예: Gear Selection의 "Park")는 갱신이 늦을 수 있다. 실제 반영은 logcat(PLEOS/Vehicle)으로 확인.
//   - ACCEL은 입력 방식이 http-g29라 이 방법으로 재현되지 않는다 (차속 변화 불가).

window.__vcOpen = async () => {
  if (document.querySelector('[role=dialog]')) return 'already open';
  const emu = [...document.querySelectorAll('*')].find(e => e.childElementCount === 0 && e.textContent.trim() === 'Emulation Controls');
  emu.closest('button,div[role=button],div').click();
  await new Promise(r => setTimeout(r, 1000));
  return 'opened';
};

window.__vc = window.__vc || {
  card(title) {
    const dlg = document.querySelector('[role=dialog]');
    const els = [...dlg.querySelectorAll('*')].filter(e => e.childElementCount === 0 && e.textContent.trim() === title);
    if (!els.length) throw new Error('no card ' + title);
    let c = els[0];
    while (c && !(c.querySelector('input,button,select') && c.innerText.includes('Publish'))) c = c.parentElement;
    return c;
  },
  slide(title, v) {
    const el = this.card(title).querySelector('input[type=range]');
    const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    set.call(el, String(v));
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    el.dispatchEvent(new PointerEvent('pointerup', { bubbles: true }));
    return title + '=' + el.value;
  },
  press(title, label) {
    const b = [...this.card(title).querySelectorAll('button')].find(b => b.innerText.trim() === label);
    if (!b) throw new Error('no button ' + label + ' in ' + title);
    b.click();
    return title + ':' + label;
  },
  select(title, value) {
    const s = this.card(title).querySelector('select');
    const set = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set;
    set.call(s, value);
    s.dispatchEvent(new Event('change', { bubbles: true }));
    return title + ' select=' + s.value;
  },
};
