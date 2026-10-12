# AR Navigation Workspace

숭실대학교 캠퍼스 AR 내비게이션과 비전 모델 실기기 평가 앱을 함께 관리하는 저장소다.

## 프로젝트 구성

- `P0_POC/`: Google 보행 경로, GPS, 기기 방향, CameraX 오버레이를 사용하는 실외 AR 내비게이션 Android 앱
- `Model_Evaluation/`: ResNet18-Places365와 YOLO26n-seg를 실시간 비교하는 Android 앱


두 Android 앱은 서로 독립된 Gradle 프로젝트다. Android Studio에서는 실행할 앱의 폴더인
`P0_POC` 또는 `Model_Evaluation`을 각각 열어 사용한다.

## P0 앱 빌드

```bash
cd P0_POC
./gradlew :app:assembleDebug
```

## 모델 평가 앱 빌드

모델 파일은 소스 관리에서 제외된다. 처음 내려받은 저장소에서는 모델을 준비한 뒤 빌드한다.

```bash
cd Model_Evaluation
python3 -m venv tools/.venv
tools/.venv/bin/pip install -r tools/requirements.txt
tools/.venv/bin/python tools/prepare_models.py
./gradlew :app:assembleDebug
```
