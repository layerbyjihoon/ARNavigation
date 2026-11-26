# 숭실대학교 캠퍼스 통합 AR 내비게이션

Android Studio에서 **이 폴더(`AR_Navigation_Final`)**를 열고 `app`을 실행합니다. 기존 세 프로젝트와 별개인 단일 APK이며, `indoor`는 앱에 포함되는 Android 라이브러리입니다.

## 실행 순서

1. 위치·카메라 권한을 허용합니다.
2. 검색에서 문화관을 선택하고 목적 호실을 설정합니다.
3. 문화관 앞문(위도 37.49637, 경도 126.95442)까지의 TMAP 경로가 준비되면 지도에서 AR 안내를 시작합니다.
4. 출입구 30m 이내의 최신 GPS(5초 이내, 수평 정확도 20m 이하)가 관측되면 전환 추론을 시작합니다.
5. 실내 구조 유사도가 0.75 이상이고 유리문 밖을 포함한 실외 기준 유사도가 0.90 미만인 프레임이 300ms 이상에 걸쳐 3개 연속 관측되면 실내 전환을 확정합니다.
6. 실외 GPS·방향 센서·CameraX와 전환 모델을 정리하고 **카메라 CLOSED 확인 후** 실내 ARCore 화면을 엽니다.
7. 전달된 첫 호실 경로는 추적·방향·바닥이 준비되면 한 번 자동 시작합니다. 실제 출발점은 F1_C006·F1_C007 중점(X 12.251m, Z 3.0465m)입니다.

문화관 안에서 출발할 때는 지도 메뉴의 **문화관 실내에서 출발** 또는 실내 AR의 **실외 목적지**를 선택합니다. 실외 목적지와 현재 실내 출발점을 고른 뒤 정문까지 안내받습니다. 문 밖으로 나가면서 가까운 바닥과 주변을 비추면 현재 GPS 위치로부터 기존 목적지까지 TMAP AR 안내가 자동으로 이어집니다.

실외 화살표는 목적지까지 직선거리 50m 이내에서 목적지를 직접 가리키며, 그 밖에서는 경로의 10m 앞 지점을 가리킵니다.
경로에서 35m를 벗어난 상태가 2초간 지속되면 이탈로 판단하고, 재탐색 최소 간격은 5초입니다.
문화관 실내 정렬은 자북 기준 반시계 70°이며, AR 높이 확인 후 다음 층 안내를 자동 시작하는 최신 실내 모듈을 포함합니다.
실내 출발점은 실제 위치에 자동으로 맞춰지는 절대 측위가 아니므로 사용자가 지정 위치에서 시작해야 합니다.

## 빌드와 API 설정

- 전체 JDK 21, Android SDK 36.1, Java 소스 17, Min SDK 24 / Target SDK 36
- 각 폴더에 포함된 Gradle Wrapper 사용
- `AR_Navigation_Final/.env`에 `MAPS_API_KEY`, `PLACES_API_KEY`, 그리고 `TMAP_APP_KEY` 또는 `TMAP_PROXY_BASE_URL`을 따옴표 없이 입력합니다.
- Gradle은 환경변수, `.env`의 채워진 값, `local.properties` 순서로 설정을 읽습니다.
- 새 디버그 패키지: **`com.example.arnavigation.finalapp.debug`**. Google Cloud 키에 Android 앱 제한이 있으면 이 패키지와 해당 서명 SHA-1 등록이 필요합니다. API 활성화·결제·실제 경로 응답 성공 여부는 별도 확인 대상입니다.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :indoor:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.arnavigation.journey.FinalPipelineIntegrationTest
./gradlew :app:signingReport
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

TMAP 경로 요청·키 설정·검증 범위는 [TMAP 연동 가이드](../docs/TMAP 연동 가이드.md)를 참고하세요. Google Routes 구현은 참고용으로 남겨두었으며 통합본 실행 경로에서는 사용하지 않습니다.

## 구성

| 폴더·클래스 | 역할 |
|---|---|
| `app/` | 지도·검색·실외 안내, 문화관 전환 추론과 인계 |
| `indoor/` | 기존 실내 지도·A*·ARCore·비전 보정 |
| `journey/JourneyGate` | GPS 근접 조건, 유효한 단일 실내 관측, 중복 인계 차단 |
| `journey/JourneyState` | 선택한 실내 경로와 목적지 ID 보관 |
| `journey/EntryVisionWorker` | 공유 CameraX 프레임으로 전환 추론, 직렬 종료 |
| `camera/CameraXBindingBackend` | 프리뷰·분석 공동 바인딩, 하드웨어 카메라 종료 확인 |

전환 모델은 실외 카메라 하나를 공유합니다. 별도 전환 앱을 실행하지 않습니다. 잘못된 세션·오래된 관측은 버리며, 인계 후 실외 점수로 되돌아가지 않습니다. 카메라가 5초 내 닫히지 않으면 ARCore를 열지 않고 오류를 안내합니다. 앱이 배경에 있는 동안 인계 준비가 끝나면 화면 복귀 시 실내 화면을 시작합니다.

## 검증과 남은 현장 확인

APK 빌드·JVM 테스트·Lint 및 에뮬레이터 UI/경로 반환 검증 결과는 저장소 `docs/통합본 실행 가이드.md`를 참고하세요. 문화관 현장 GPS, 지도·검색 Google API 권한 및 TMAP appKey, 카메라에서 실제 실내 전환 후 ARCore 안내, 정렬 정확도와 층간 이동은 실기기로 확인해야 합니다. 모델의 실내 판정은 실제 문 통과나 절대 위치·층 판정이 아닙니다.

원본 프로젝트 폴더는 수정하지 않았습니다. 기존 소스·모델 라이선스 표시는 각 모듈에 유지했습니다. 배포용 서명 APK를 만들기 전에는 별도 서명 설정과 현장 검증이 필요합니다.
