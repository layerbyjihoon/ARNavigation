# Android Model Evaluation

안드로이드 카메라 입력에서 다음 두 모델을 동시에 평가하는 Java 앱입니다.

- **ResNet18-Places365**: 현재 장면의 Places365 Top-3 분류와 신뢰도
- **YOLO26n-seg Doorway Detection**: `doorway`, `door`, `people`, `window` 감지 박스와 신뢰도
- 모델별 추론 지연시간, 처리 FPS, 카메라 FPS 및 누적 프레임 수
- 모델별 실행 토글, YOLO 신뢰도 임계값, 일시정지

추론은 네트워크 없이 단말 안에서 ONNX Runtime으로 실행됩니다. CameraX의 최신 프레임 우선
backpressure를 사용하므로 추론이 카메라보다 느려도 프레임이 무한히 쌓이지 않습니다.

## 1. 모델 준비

모델 바이너리는 저장소에 포함하지 않습니다. 프로젝트 루트에서 다음을 실행합니다.

```bash
python3 -m venv tools/.venv
tools/.venv/bin/pip install -r tools/requirements.txt
tools/.venv/bin/python tools/prepare_models.py
```

스크립트는 공식 Places365 ResNet18 체크포인트를 224×224 ONNX(opset 17)로 변환하고,
Hugging Face 모델 저장소의 640×640 YOLO26n-seg ONNX를 내려받습니다. 생성 결과는 다음과 같습니다.

```text
app/src/main/assets/resnet18_places365.onnx
app/src/main/assets/categories_places365.txt
app/src/main/assets/doorway_seg_yolo26n.onnx
```

## 2. 빌드 및 실행

Android Studio에서 이 폴더를 별도 프로젝트로 열거나 다음 명령을 사용합니다.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

생성 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 있습니다. Android 7.0(API 24)
이상 실기기에 설치한 뒤 카메라 권한을 허용합니다. 모델이 없을 때 앱은 종료되지 않고 모델 준비
명령을 화면에 표시합니다.

## 평가 방법

1. 두 모델을 모두 켠 상태로 동일한 복도·출입구 동선을 30초 이상 촬영합니다.
2. 화면의 모델별 latency/FPS와 오탐·미탐을 기록합니다.
3. 한 모델씩 꺼서 단독 실행 성능도 측정합니다.
4. YOLO 신뢰도 기준을 바꾸며 doorway 재현율과 오탐의 변화를 비교합니다.
5. 발열에 따른 성능 저하를 확인하려면 5분 이상 연속 실행 후 초기/후반 값을 비교합니다.

표시 FPS는 최근 추론 지연시간의 지수 이동 평균으로 계산한 모델 처리량 추정치입니다. 두 모델을
동시에 켜면 단일 직렬 실행기에서 순서대로 실행되므로 실제 각 결과의 갱신 주기는 두 지연시간의
합에 가깝습니다.

## 모델 및 라이선스

- Places365: CSAIL Places365, 사전 학습 모델은 CC BY 조건을 확인하고 출처를 표시해야 합니다.
- Doorway model: `perseusdg/doorway-door-people-window-seg`, AGPL-3.0.
- 런타임: Microsoft ONNX Runtime Android.

Doorway 모델의 `mirror` 클래스는 내부 억제용이므로 화면에 표시하지 않습니다. 앱은 배포 전에
사용 목적과 배포 방식이 AGPL-3.0 의무에 맞는지 별도로 검토해야 합니다.
