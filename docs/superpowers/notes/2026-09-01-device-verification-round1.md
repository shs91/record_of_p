# 실기기 검증 1차 (2026-09-01) — 결과·발견·남은 작업

| | |
|---|---|
| 기기 | Samsung Galaxy A25 (SM-A256N), Android 16 / SDK 36, One UI 8.5 |
| 빌드 | feat/v1 @ 369f763, 카카오 REST 키 내장(32자, local.properties) |
| 환경 | GMS 있음 · 카카오맵 앱 없음(웹 폴백 경로) · 모의 위치 앱 없음 · 기기 sqlite3 없음(run-as+로컬 Python으로 DB 검증) |

## 통과한 것 (체크리스트 기준)

- **1. 온보딩**: 렌더링 정상(따옴표 이스케이프 포함). 알림·정밀 위치 허용, 백그라운드 미요청(설계대로). 홈 진입, 보호 배너 노출.
- **엔진 열화 모드**: 첫 실행 `APP_OPEN/PERIODIC → NO_PERMISSION → STOOD_DOWN` — I2 스탠드다운 경로 실기기 검증.
- **2. 기록**: '건전지 사기' + 편의점·대형마트·GS25(브랜드)·서울가양동우체국(PLACE kid=8724736). 홈 칩 4개, DB trigger_spec 4행, 저장 30초 후 `ITEM_CHANGE → STOOD_DOWN`(권한 없음 시 정상). canSave 게이트 동작.
- **대시보드 → 시스템 설정 연결**: '항상 허용' 행 탭 → `InstalledAppDetails` 열림 확인. 사용자가 항상 허용 부여.
- **3. 지오펜스 등록**: 콜드스타트 후 `APPLIED fences=33` — 센티널 1 + 장소 1 + POI 31 (편의점 20=상한 도달, GS25 14 중 8개는 편의점 펜스와 병합=N:M 검증, 마트 5). 링크 40 = 계산치 일치. GMS 활성 증거: `dumpsys location`에 geofencer_provider가 앱 WorkSource로 5분 주기 요청 보유.
- **BOOT 복구 경로**: `am force-stop` 후 재실행 시 Android 15+가 `BOOT_COMPLETED`를 재전달(unstop 동작) → BootReceiver → 전량 재등록 확인. (실제 재부팅 테스트는 미실시 — 남은 작업)
- **진단 화면**: 실행 이력 최신순 렌더링, 결과·펜스 수 표시.
- **8. 주변 보기**: 편의점 5곳(84~213m)·대형마트 3곳(319~1713m) 거리순 그룹 표시.
- **9. 설정 유지**: 쿨다운 4→1시간 변경, force-stop 재실행 후 유지(DataStore).
- **10(부분). 다크 모드**: 홈 정상 / 설정 화면 버그 발견(→ F3).

## 발견 사항 (수정 대기)

- **F0 (환경, 해결됨)**: 카카오 REST 키 "호출 허용 IP 주소" 활성화로 401 `ip mismatched`. 콘솔 [앱]>[플랫폼 키]>REST API 키>허용 IP 전부 삭제로 해제. **잔여 리스크**: APK 내장 REST 키는 추출 가능 → 쿼터 도용. v2에서 프록시 서버 검토(스펙 §13에 반영할 것).
- **F1 (Important)**: 설정에서 '항상 허용' 부여 후 **복귀만으로는 재배치가 트리거되지 않음**(APP_OPEN은 onCreate에서만). 실증: 부여(16:56~57)~콜드스타트(16:58:29) 사이 엔진 기록 없음. 수정안: 홈/설정 LifecycleResumeEffect에서 권한 스냅샷이 fullyProtected로 전이하면 재배치 요청(data의 ReseedRequester에 requestOpportunistic 추가). **→ 2026-09-03 해결(18847ae)**: ProtectionReseedTrigger 싱글턴이 홈·설정 재개 시 보고받아 미보호→보호 전이에서만 기회적 재배치. TDD 전이 4케이스.
- **F2 (Minor)**: 권한 없음 시 시도마다 NO_PERMISSION + STOOD_DOWN("no registrations") 2행 → 진단 노이즈. standDown은 걷어낼 등록이 있을 때만 로그.
- **F3 (Important/UX)**: Scaffold 없는 화면(설정·에디터, 온보딩 확인 필요)의 ①상단 상태바 겹침 ②다크 모드에서 창 배경(라이트) 노출 — 절전 안내문 비가시(밝은 배경+밝은 글자). 수정: 해당 화면에 Scaffold+TopAppBar(뒤로) 도입 + XML 테마 DayNight 정합. **→ 2026-09-03 디자인 개편안에 포함됨.**
- **F4 (Important/진단성)**: 장소 검색 실패 시 원인 미로깅, 메시지가 항상 "위치와 네트워크 확인"(401도 네트워크로 오인 — F0 진단이 이것 때문에 지연). 수정: 원인 분기(위치 없음/네트워크/API 오류) + Log.w + 메시지 분리. **→ 2026-09-03 해결(5992dc9)**: PlaceSearchError(NO_LOCATION/NETWORK/SERVICE) 분기 + Log.w(RecordOfP) + 문구 분리(ko/en). 실기기: 네트워크 차단 후 검색 → "네트워크 연결을 확인해주세요" 확인.
- **F5 (Minor)**: BOOT(강한 큐)와 APP_OPEN(기회적 큐)이 동시 실행 → 카카오 호출·GMS 등록 2배(Geofencer "registration not active" 경고 66건, 무해). 수정: ReseedService.reseed를 Mutex 직렬화 + 거버너 판정을 락 안에서. **→ 2026-09-03 해결(4ad6b51)**: reseed·standDown 공용 Mutex, 뒤에 든 쪽이 갱신된 스탬프로 디바운스. 실기기: force-stop→재실행 시 BOOT APPLIED 1건만(15:23:09, 이중 실행 소멸). TDD 동시성 2케이스.

## 남은 검증 (후순위 확정, 2026-09-03)

- **4·5 부분 실증(2026-09-03)**: notification_log에 09-03 14:57:09 rem=1, poi=CU 덕은아이에스점·이마트24 덕은지엘점 2행 — 백그라운드 FENCE_EVENT→DWELL→알림 표시 경로가 자연 발화로 실증됨(09-02 04:42 BLOCK_QUIET_HOURS 차단 기록도 게이트 동작 증거). 도보 프로토콜에서 남은 것: 알림 거리 표기 육안, [완료]/[오늘 그만] 액션, 소규모 diff 재배치.
- **4·5·6. 알림·액션·백그라운드 전달** — 도보 프로토콜 (기기 위치 37.58071,126.85892 고양 덕은 기준):
  1. 앱은 홈 버튼으로 백그라운드(강제 종료 금지), 화면 꺼도 됨
  2. GS25 덕은지엘메트로점(서쪽 ~155m) 앞 1~3분 → 알림(거리 표기 확인) → **[완료]** → 홈에서 사라짐
  3. 그 자리에서 "우유 사기"(편의점) 저장 → 소규모 diff 재배치 확인
  4. 귀가(CU 덕은아이에스점 펜스 재진입) → 1~3분 → 알림 → **[오늘 그만]** → DB snoozeUntil=내일 05:00
  - 주의: 현재 위치가 이미 CU·이마트24 펜스(~85m) 안 → 등록 즉발 금지 설계라 나갔다 들어와야 함
  - 검증: notification_log 행, FENCE_EVENT/BLOCK_* 진단 행, DWELL+GMS 지연 체감치
- **7. 재부팅** — `adb reboot`(사용자 동의 후) → BOOT → APPLIED 확인. unstop 경로는 검증됐으나 실부팅·WorkManager 부활은 미확인.
- **10. TalkBack** — 미실시.
- 필드 튜닝(§10.2): 발화/미발화/오발화 집계 후 EngineParams 조정.

## 도구 (레포 tools/device/에 커밋됨)

- `tools/device/e2e.sh` — 스크린샷(ss)·탭(tap, 1080x2340 원본 좌표)·권한 조회(perms) 헬퍼. Git Bash 전용(MSYS 경로 변환 주의 반영).
- `tools/device/dbdump.py` — run-as로 DB(+WAL) 풀어서 리마인더·트리거·펜스·엔진 로그 요약. `PYTHONIOENCODING=utf-8` 필요.
- 스크린샷 증거는 세션 스크래치패드에만 있었음(만료) — 위 통과 항목 서술이 기록.
