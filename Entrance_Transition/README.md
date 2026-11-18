# 문화관 실내·실외 전환 앱

Android Studio에서 이 `Entrance_Transition` 폴더를 열어 실행합니다. Java 기반 독립 앱이며 실외·실내 내비게이션 앱 없이 빌드됩니다.

## 실행

1. Android Studio의 Gradle JDK를 전체 JDK 21로 설정합니다.
2. 필요하면 `local.properties`에 해당 컴퓨터의 Android SDK 경로를 지정합니다.
3. `app`을 Android 7.0(API 24) 이상 기기에 실행하고 카메라 권한을 허용합니다.

모델과 문화관 기준 마스크는 포함되어 있습니다. API 키·인터넷 연결이 필요하지 않으며 카메라 영상은 저장하지 않습니다.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. 빌드 결과물과 개인 SDK 설정은 Git에 포함하지 않습니다.

## 판정 방식

- SegFormer-B0(ADE20K)로 카메라 영상의 벽·바닥·천장·계단 등을 구분합니다.
- 등록된 gate1~4의 구조와 비교해 가장 높은 값을 실내 점수로 사용합니다.
- 유효한 점수가 **0.75 이상이면 즉시 실내**, 미만이면 실외로 표시합니다.
- 구조 부족·오류·오래된 결과는 `판정 중`으로 표시합니다.
- 실외 점수·연속 관측 대기·진입 확인 버튼은 사용하지 않습니다.

카메라 위에 반투명 분류 마스크도 표시합니다. 다른 건물의 자동 식별이나 실제 문 통과 확인 기능은 없으며, 문화관 현장 정확도 검증은 별도로 필요합니다. 내비게이션은 연결하지 않았습니다.

## 구성

| 경로 | 역할 |
|---|---|
| `app/src/main/java/com/example/entrancetransition/` | 화면·카메라·환경 판정·마스크 표시 |
| `app/src/main/java/com/example/arnavigation/entrance/` | 모듈 내부에 포함한 마스크·구조 비교·ONNX 실행 코드 |
| `app/src/main/assets/` | ONNX 모델, gate1~4 기준 마스크, 라이선스 |
| `tools/` | 모델·기준 마스크 생성과 영상 재생 실험 도구 |

`tools`의 재생 실험에는 별도의 영상 입력, FFmpeg, Python 추론 환경과 로컬 Gson 캐시가 필요합니다. 기준 마스크 재생성에는 저장소 루트의 `assets/gate1~4.jpeg`, `avoidance.jpeg`, `avoidance2.jpeg`가 추가로 필요합니다. 원본 사진·테스트 영상은 이 모듈 배포에 포함하지 않았습니다. 앱 실행에는 재생성 작업이 필요하지 않습니다.
