# P의기록 (Record of P)

카테고리·상점별로 할 일을 기록해두면, 그 일을 처리할 수 있는 장소 근처를 지날 때 알림으로 알려주는 위치 기반 리마인더 앱.

> "장소가 기억을 대신 트리거한다."

## 저장소 구조

```
record_of_p/
├─ docs/superpowers/specs/   # 설계 문서 (단일 진실 원천)
├─ android/                  # Android 앱 (Kotlin, v1.0 타깃)
└─ .github/workflows/        # CI
```

- 설계 문서: [`docs/superpowers/specs/2026-08-31-record-of-p-design.md`](docs/superpowers/specs/2026-08-31-record-of-p-design.md)
- 플랫폼 전략: Android 네이티브 선출시 → 검증 후 iOS(Swift) 포팅. 지오펜스 엔진(`domain/engine`)은 순수 Kotlin으로 격리되어 있어 포팅 시 알고리즘을 그대로 이식한다.

## 개발 시작하기

1. Android Studio에서 `android/` 폴더를 연다.
2. `android/local.properties`에 카카오 REST API 키를 넣는다 (없으면 빌드는 되지만 POI 조회가 동작하지 않음):
   ```
   KAKAO_REST_KEY=발급받은_REST_키
   ```
   발급: [developers.kakao.com](https://developers.kakao.com) → 앱 생성 → REST API 키.
3. 빌드/테스트:
   ```
   cd android
   ./gradlew testDebugUnitTest assembleDebug
   ```

## 품질 원칙

- 빠른 출시보다 좋은 사용자 경험. 오발화/미발화 튜닝 파라미터는 `EngineParams` 한 파일에 모여 있다.
- 엔진 핵심(`ReseedPlanner`, `NotificationGate`)은 순수 로직 + JVM 단위 테스트로 검증한다.
- 위치 데이터는 기기 밖으로 나가지 않는다 (유일한 외부 통신 = 카카오 POI 조회).
