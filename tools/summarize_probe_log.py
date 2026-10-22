"""SDK Probe AppLog 파일 → Vehicle 도메인별 결과 마크다운 표.

사용:
  adb exec-out run-as com.teamsync.pleos --user 10 cat files/logs/app-YYYYMMDD.log > logs/probe.log
  python -X utf8 tools/summarize_probe_log.py logs/probe.log > docs/probe-table.md

파일 안에 Probe가 여러 번 돈 경우 마지막 `PLEOS/Vehicle initialize() call` 이후만 집계한다.
"""
import re
import sys
from collections import OrderedDict, defaultdict

ENTRY = re.compile(r"^\S+ (\S+) ([VDIWE])/PLEOS/(\w+) \[[^\]]*\] (.*)$")
FAILED = re.compile(r"onFailed api=(\w+)\.(\w+)(#onFailed)? args=\[(.*?)\] errorCode=(\S+) type=\S+ message=(.*)")
REGISTERED = re.compile(r"register ok (\w+)\.(\w+) args=\[(.*)\]")
# 신규 형식: LIVE Door.registerDoorPosition(DoorArea(HOOD))#onPositionUpdated → 1 (update #2)
# 구 형식:   LIVE Door.registerDoorPosition#onPositionUpdated → 1 (update #2)  (Area 구분 없음)
LIVE = re.compile(r"LIVE (\w+)\.(\w+)(?:\((.*)\))?#\w+ → (.*) \(update #\d+\)")


def short(s, n=90):
    s = s.replace("|", "\\|")
    return s if len(s) <= n else s[:n] + "…"


def main(path):
    lines = open(path, encoding="utf-8").read().splitlines()
    starts = [i for i, l in enumerate(lines) if "PLEOS/Vehicle" in l and "initialize() call" in l]
    if starts:
        lines = lines[starts[-1]:]

    rows = OrderedDict()             # (domain, kind, label) -> {"ok": bool, "v": str}
    live_values = defaultdict(list)  # (domain, label) -> distinct consecutive values

    for line in lines:
        m = ENTRY.match(line)
        if not m or m.group(3) != "Vehicle":
            continue
        msg = m.group(4)
        if msg.startswith(("CAP ", "GET ")):
            kind, rest = msg.split(" ", 1)
            label, value = rest.split(" → ", 1)
            dom, label = label.split(".", 1)
            rows[(dom, kind, label)] = {"ok": True, "v": value}
        elif msg.startswith("onFailed api="):
            mm = FAILED.match(msg)
            if not mm:
                continue
            dom, meth, _, args, code, message = mm.groups()
            kind = "LIVE" if meth.startswith("register") else ("CAP" if meth.startswith("check") else "GET")
            reason = code if code != "-" else re.sub(
                r"^java\.lang\.IllegalArgumentException: |^android\.car\.hardware\.property\.", "", message)
            reason = reason.replace("property ID is not in carPropertyConfig list, and so it is not supported: ",
                                    "not in carPropertyConfig: ")
            key = (dom, kind, meth + (f"({args})" if args else ""))
            if key not in rows or not rows[key]["ok"]:
                rows[key] = {"ok": False, "v": reason}
        elif msg.startswith("register ok "):
            dom, meth, args = REGISTERED.match(msg).groups()
            rows.setdefault((dom, "LIVE", meth + (f"({args})" if args else "")),
                            {"ok": True, "v": "(등록됨, 업데이트 없음)"})
        elif msg.startswith("LIVE "):
            mm = LIVE.match(msg)
            if mm:
                dom, meth, args, v = mm.groups()
                key = (dom, meth + (f"({args})" if args else ""))
                if not live_values[key] or live_values[key][-1] != v:
                    live_values[key].append(v)

    print("| 도메인 | 종류 | API | 결과 |")
    print("|---|---|---|---|")
    order = {"CAP": 0, "GET": 1, "LIVE": 2}
    for (dom, kind, label), r in sorted(rows.items(), key=lambda kv: (kv[0][0], order[kv[0][1]], kv[0][2])):
        v = r["v"]
        if kind == "LIVE" and r["ok"]:
            seen = live_values.get((dom, label)) or live_values.get((dom, label.split("(")[0]))
            if seen:
                v = "수신값: " + " → ".join(seen[:8])
        print(f"| {dom} | {kind} | `{short(label, 70)}` | {'✅' if r['ok'] else '❌'} {short(v)} |")


if __name__ == "__main__":
    main(sys.argv[1])
