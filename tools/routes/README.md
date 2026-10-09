# 필드 테스트 경로 (설계 §10.2)

에뮬레이터 Extended Controls > Location > Import GPX로 불러와 재생한다.
- 도보 시나리오: 재생 속도 1x (Playback speed)
- 차량 시나리오: 같은 경로를 5x로 재생 — DWELL 60초 필터가 걸러야 정상
- 결과 판정: 앱 설정 > 진단 화면에서 FENCE_EVENT/차단 사유 로그 확인
- 회차별 튜닝 변경은 EngineParams 커밋으로 기록한다
