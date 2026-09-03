"""앱 DB를 run-as로 끌어와 핵심 테이블을 요약 출력한다 (debuggable 빌드 전용).

사용:  PYTHONIOENCODING=utf-8 python tools/device/dbdump.py [엔진로그 표시 개수]
기기에 sqlite3가 없어도 동작 (DB+WAL을 로컬로 복사해 Python sqlite3로 연다).
"""
import subprocess, sqlite3, datetime, sys, os

ADB = os.environ.get("ADB", r"C:\Users\PC\AppData\Local\Android\Sdk\platform-tools\adb.exe")
D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out", "db")
PKG = "com.recordofp.app"
os.makedirs(D, exist_ok=True)
for f in ("record_of_p.db", "record_of_p.db-wal", "record_of_p.db-shm"):
    out = subprocess.run([ADB, "exec-out", "run-as", PKG, "cat", f"databases/{f}"], capture_output=True).stdout
    with open(os.path.join(D, f), "wb") as fh:
        fh.write(out)

c = sqlite3.connect(os.path.join(D, "record_of_p.db"))
def ts(ms): return datetime.datetime.fromtimestamp(ms / 1000).strftime("%m-%d %H:%M:%S")

print("== reminder ==")
for r in c.execute("select id,title,memo,status,snoozeUntil,createdAt from reminder order by id"):
    print(f"  #{r[0]} '{r[1]}' memo={r[2]!r} status={r[3]} snooze={ts(r[4]) if r[4] else None} created={ts(r[5])}")
print("== trigger_spec ==")
for r in c.execute("select id,reminderId,type,categoryId,brandKeyword,placeName,placeKakaoId,placeLat,placeLng from trigger_spec order by id"):
    print(f"  #{r[0]} rem={r[1]} {r[2]} cat={r[3]} brand={r[4]} place={r[5]} kid={r[6]} ll={r[7]},{r[8]}")
print("== geofence_reg ==")
rows = c.execute("select geofenceId,kind,lat,lng,radiusM,poiName,matchKey from geofence_reg order by kind,geofenceId").fetchall()
print(f"  count={len(rows)}")
for r in rows[:40]:
    print(f"  {r[0]} {r[1]} r={r[4]} {r[5]} [{r[6]}] @{r[2]:.5f},{r[3]:.5f}")
print("== reg_trigger ==", c.execute("select count(*) from reg_trigger").fetchone()[0], "links")
print("== notification_log ==")
for r in c.execute("select reminderId,poiKakaoId,shownAt from notification_log order by shownAt"):
    print(f"  rem={r[0]} poi={r[1]} at={ts(r[2])}")
n = int(sys.argv[1]) if len(sys.argv) > 1 else 12
print(f"== engine_run_log (last {n}) ==")
for r in c.execute("select at,cause,result,registeredCount,note from engine_run_log order by at desc limit ?", (n,)).fetchall()[::-1]:
    print(f"  {ts(r[0])} {r[1]} -> {r[2]} fences={r[3]} {r[4] or ''}")
