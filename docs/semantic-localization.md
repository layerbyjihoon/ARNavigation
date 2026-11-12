# SegFormer 실내 경로 보정 구현 및 검증 결과

후속 변경: 여러 프레임 누적, 삼각측량, 벽·복도 방향 제약과 도면 기준점 사용은 `temporal-semantic-localization.md`를 참고한다. 이 문서의 실측 플래그에 따른 운영 보류 설명은 최초 버전 기록이다.

검증일: 2026-11-12. 실제 작업 대상은 `D:/Coding/ARNav_Mobile/ARNavigation/src`이다.

Android에서 모델 추론, 구조물 추출, 선택적 Depth 투영, 지역 지도 매칭, 경로 보정 연결까지 구현했다. 단위 테스트의 가상 지도에서는 평행이동·회전 오차를 복원했다. **문화관 현장의 보정 효과는 아직 검증하지 않았다. 현재 문화관 지도에는 실측·검증된 구조물 기준점이 없어 운영 중 위치 보정은 보류된다.**

## 1. 기존 프로젝트 구조 분석

- `HelloArActivity`: 기존 ARCore Session 생성·재개·종료, SampleRender/GLSurfaceView, 렌더 프레임별 `session.update()`, 카메라 Pose와 출발 Anchor 관리. 이번 기능도 동일 Session과 Frame을 사용한다.
- `MapRouteActivity`, `map` 패키지: 지도 선택 및 기존 A* 경로 탐색. 실제 자산 `maps/munhwagwan.indoor.json`은 5개 층, 206개 노드, 201개 링크다.
- `coordinates/LocalMapCoordinateTransformer`: 기존 지도 좌표를 출발점 기준 미터 단위 AR 로컬 좌표로 변환한다. 지도 앞쪽은 AR의 -Z에 대응한다. 기존 센서 방향 및 문화관의 반시계 85.5도 설정을 유지한다.
- `ArRoutePlan`, 기존 경로 선·화살표 렌더러: 출발 Anchor의 수평 Pose를 기준으로 경로를 그린다. 기존 높이 설정도 유지한다.
- `ArDatasetController`: 기존 녹화·재생 및 기록된 방향 처리를 보존한다. 새 카메라 세션을 만들지 않는다.
- Java 17, AGP 8.13, compileSdk 36.1, minSdk 24, ARCore 1.56.0에 ONNX Runtime Android 1.24.3을 추가했다.

## 2. 새 파일

아래 Java 경로의 기준은 `src/app/src/main/java/com/google/ar/core/examples/java/helloar/`이다.

| 파일 | 역할 |
|---|---|
| `segmentation/FrameSnapshot.java` | 촬영 시점 Pose, intrinsics, 타임스탬프, 화면/Depth 좌표 변환의 복사본 |
| `segmentation/CameraFramePreprocessor.java` | YUV stride 처리, 회전, bilinear resize, RGB 정규화 및 NCHW |
| `segmentation/SegFormerRunner.java` | 실제 ONNX 모델 및 세션 재사용, 픽셀별 클래스와 신뢰도 |
| `segmentation/SegmentationResult.java` | 추론 결과 및 촬영 정보 |
| `segmentation/SegmentationController.java` | 비동기 단일 작업, 프레임 복사·폐기, 생명주기 및 결과 전달 |
| `localization/LandmarkObservation.java` | 구조물 후보의 화면 위치·영역·신뢰도·선택적 3D 좌표 |
| `localization/LandmarkExtractor.java` | 연결 요소와 시간적 일관성 |
| `localization/WallStructureExtractor.java` | 벽·바닥 접점의 연속선과 코너를 보존하는 화면상 폴리라인 |
| `localization/LandmarkPositionEstimator.java` | 동기화된 Depth의 다중 샘플 및 월드/로컬 좌표 투영 |
| `localization/SemanticLandmarkMap.java` | 실제 지도 좌표와 실측 여부를 유지하는 구조물 지도 |
| `localization/SemanticMapMatcher.java` | 현재 경로 주변의 기하·유형·시간 합의 검사 |
| `localization/MatchResult.java` | 승인·보류 상태, 사유, 후보 수와 잔차 |
| `localization/Rigid2D.java` | 수평 이동 및 Y축 회전 |
| `localization/RouteAlignmentManager.java` | 승인된 보정의 제한·평활화 |
| `debug/SegmentationDebugOverlay.java` | 마스크·후보·벽 경계·상태 표시 |

추가 자산: `src/app/src/main/assets/segformer_config.json`, `segformer_preprocessor_config.json`. 이미 존재하던 `segformer.onnx`를 재사용했으며 모델을 다시 다운로드하지 않았다.

추가 테스트: `src/app/src/test/java/com/google/ar/core/examples/java/helloar/localization/SemanticLocalizationTest.java`, `src/app/src/androidTest/java/com/google/ar/core/examples/java/helloar/SegFormerIntegrationTest.java`, `src/app/src/androidTest/assets/segformer-smoke-input.f32`, `segformer-smoke-mask.png`.

PC 재현 스크립트: `D:/Coding/ARNav_Mobile/model-tests/ade20k/segformer_onnx_school.py`, `requirements-onnx-video.txt`.

## 3. 수정한 기존 파일

- `src/app/build.gradle`: ONNX Runtime Android 의존성.
- `HelloArActivity.java`: 기존 렌더 흐름에 프레임 복사, 결과 수신, 보정 Pose와 개발자 도구 연결. 지원 기기에 Depth AUTOMATIC 설정.
- `MapRouteActivity.java`: 선택 경로와 동일 좌표 변환을 적용한 해당 층의 구조물 지도 전달.
- `ArRoutePlan.java`: 구조물 지도 전달을 위한 호환 overload와 선택 필드.
- `src/app/src/main/res/layout/activity_main.xml`, `res/values/strings.xml`: 모델 실행·보정·마스크·후보·상태·경로 비교 토글.
- `src/app/src/main/assets/shaders/navigation_route.frag`: 보정 전 경로를 회색으로 비교 표시하는 디버그 분기.

작업 전 존재하던 센서 정확도 관련 변경(`HeadingSensor`, `HeadingSampleQuality`, `ArDatasetController` 등)은 보존했다. 이 보고서의 신규 구현과 구분한다.

## 4. 모델 로딩 및 추론

- 모델: `optimum/segformer-b0-finetuned-ade-512-512`의 기존 ONNX, 15,142,812 bytes.
- SHA256: `3a89102115fe3c16230502437b894844ba50cde6f7c800f9884e87c360bcbfc9`.
- 실제 입력 `pixel_values`: float NCHW, 동적 shape, 앱 입력 `[1,3,512,512]`.
- 실제 출력 `logits`: 이 입력에서 `[1,150,128,128]`.
- 해당 모델의 config와 preprocessor config를 읽는다. RGB, mean `[.485,.456,.406]`, std `[.229,.224,.225]`, 512 정사각 resize를 사용한다. 입력을 crop하지 않고 회전/화면 crop 변환을 별도 보존한다.
- config에서 확인한 관심 클래스는 wall 0, floor 3, door 14, railing 38, column 42, stairs 53, stairway 59다. 추출기와 표시기는 이름으로 구분한다.
- 픽셀별 argmax 및 softmax 기반 선택 클래스 신뢰도를 계산한다. 런타임 입력·출력 메타데이터는 `SegFormer` 로그에 출력한다.
- 세션은 컨트롤러 생명주기 동안 한 번 로드하고 재사용한다. CPU intra-op 2, inter-op 1. 기본 제출 간격 500ms이며 동시에 하나만 실행한다. 작업 중인 프레임은 추가로 쌓지 않는다.
- GL 스레드에서는 YUV/Depth 및 촬영 시점 기하만 복사하고 Image를 닫는다. 전처리·모델·추출·매칭은 전용 작업 스레드에서 실행한다. 리사이즈 루프의 픽셀별 임시 배열 할당은 제거했다.
- pause·새 경로·종료 시 세대 번호로 오래된 결과를 무효화한다. Tensor/Result/Image는 닫고, 종료 시 진행 중인 작업 다음에 세션을 정리한다. 모델 오류·메모리 부족이면 추론을 중지하고 기존 AR 기능을 유지한다.

## 5. 구조물 추출 및 Depth

8방향 연결 요소를 분석해 최소 16픽셀 및 영상 면적의 약 0.1%, 평균 신뢰도 0.6 조건을 통과한 후보를 만든다. 문·계단·기둥은 bbox/중심/면적/클래스/시각을 갖는다. 앞 프레임과 1.5초 이내, 화면 중심 거리 0.1 이내, 면적비 0.4~2.5이면 시간적으로 일관된 후보로 표시한다. 연결 요소 수를 실제 문 개수로 단정하지 않는다.

벽은 점 랜드마크 매칭에 넣지 않는다. 벽과 바닥이 맞닿는 경계를 연속선으로 묶고 1.5 출력 픽셀 허용 오차로 단순화해 복도 방향 변화와 코너를 보존한다. 디버그 후보 표시에서 청록색으로 그린다. 이것은 2D 구조 증거이며 아직 실측 벽 지도와의 선분 매칭은 하지 않는다.

Depth는 지원·획득 가능한 경우에만 사용한다. IMAGE_NORMALIZED → TEXTURE_NORMALIZED 변환으로 깊이 영상 위치를 구하고, 촬영 시점 intrinsics와 Pose로 unproject한다. 영상·Depth 시각 차이는 50ms 이하, 거리는 0.3~8m, 같은 클래스 내부 3×3 샘플 중 5개 이상 및 중앙값 기준 이상치 제한 0.25m를 요구한다. 지도로 비교할 때는 출발 Anchor의 바닥 평면에 투영한다. Depth가 없거나 오래되면 `worldPosition=null`, `depthValid=false`를 유지한다.

## 6. 지도 매칭과 승인 조건

기존 지도에서 현재 층의 DOOR/STAIR 노드를 재사용하고 선택 경로 3m 이내로 제한한다. 다시 현재 추정 위치의 8m 주변만 검색한다. 좌표는 기존 경로와 동일한 변환을 사용한다.

**운영 승인용 기준점**에는 노드 properties에 `field_verified=true`, `localizationVerified=true`, 그리고 기준점 종류를 나타내는 `semanticReference`가 모두 필요하다. 문에는 `DOOR_SURFACE_CENTER_GROUND_PROJECTION`, 계단에는 `STAIR_LANDING_REFERENCE`를 사용한다. 이 필드를 실측 없이 true로 바꾸면 안 된다. 현재 문화관 지도는 승인 기준점 0개다. 출입구 통행 노드를 문 표면의 중심과 같다고 가정하지 않는다.

후보 최대 16개, 관측 최대 8개를 사용한다. 같은 유형·서로 다른 기준점의 쌍으로 2D rigid 가설을 만들고, 일대일 대응 및 Procrustes 재추정으로 검증한다. 기준점 간격과 상대 배치를 사용하므로 단순 문 개수 비교가 아니다.

| 승인 항목 | 기본값 |
|---|---|
| 안정적이고 유효한 3D 관측 | 최소 3개, 신뢰도 ≥0.65 |
| 가설 생성 기준점 간격 | ≥1m |
| inlier 거리 / RMS 잔차 | <0.35m / ≤0.25m |
| 보정 전체 범위 | 이동 ≤1.5m, 회전 ≤10도 |
| 모호성 거절 | 같은 inlier 수의 차선 가설과 잔차 차이 <0.05m |
| 시간 합의 | 서로 다른 3프레임, 간격 <1.5초, 변화 ≤0.15m·2도 |
| 적용 관리자 추가 제한 | 결과 나이 ≤2초, 목표 변화 ≤0.5m·5도 |
| 평활화 속도 | 이동 ≤0.15m/s, 회전 ≤2도/s |

결과는 `NO_MATCH`, `AMBIGUOUS`, `LOW_CONFIDENCE`, `MATCH_CONFIRMED`로 구분한다. 미실측 지도·반복 복도·일부/잘못된 관측·Depth 부재는 승인을 막는다. AR 추적이 끊기면 적용을 멈춘다. 전역 위치 재탐색은 구현하지 않았다.

## 7. 기존 AR 경로와 연결

ARCore가 반환하는 카메라 Pose나 Anchor를 덮어쓰지 않는다. `levelAnchorPose × semanticCorrection`으로 경로 Pose를 계산한다. 동일한 보정 Pose의 역변환을 사용자의 경로 상대 좌표와 경유점 진행 판단에도 적용해 표시와 안내가 일치하게 했다.

기존 경로 계산·화살표·세션 업데이트를 유지했다. 비교 토글을 켜면 기존 Anchor 기준 경로를 회색으로 추가 표시한다. 보정이 항등변환이거나 보류된 경우 두 경로는 겹친다. 일반 화면에서는 디버그 표시가 기본적으로 꺼져 있다. `도구`의 개발자 패널에서 각 표시와 모델 실행/보정을 개별 제어한다.

## 8. 실행한 검증과 결과

### 빌드·단위 테스트

`ARNavigation/src`에서 `./gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --console=plain` 성공. 단위 테스트 **98개, 실패 0개**. Lint 오류 0개, 경고 36개로 경고까지 모두 없앤 상태는 아니다.

신규 검증은 stride/회전/정규화, 복수 문 후보와 시간 일관성, 벽·바닥 코너 보존, Depth 부재·시각 불일치 거절, 정상 Depth 투영, 가상 이동·회전 복원, 미실측 지도 거절, 잘못된 유형·큰 점프·반복 복도 거절, 평활화·추적 손실·오래된 결과 거절을 포함한다. 가상 좌표는 테스트 코드에만 존재한다.

### 실제 Android 기기

SM-S937N / Android 16에서 `SegFormerIntegrationTest`, `NavigationTest`, `MapUiIntegrationTest` **14개 모두 통과**. `adb install -r`로 업데이트하여 앱과 기존 데이터를 유지했다. 테스트 후 앱을 삭제하지 않았다.

- 실제 문화관 영상의 첫 입력 텐서로 Android ORT를 실행해 PC 결과와 128×128 클래스 마스크 **100% 일치**. 최종 실행의 정적 추론은 약 501ms.
- 기존 AR 카메라 프레임의 실제 YUV 추론 및 pause/resume 이후 새 프레임 추론 성공. 최종 실행에서 약 **238ms**. 이 값은 모델 및 마스크 후처리 시간이며 YUV 전처리를 포함한 전체 지연이나 지속 평균 FPS가 아니다.
- 기존 GL 경로 그리기, 상대 좌표, 경유점 순서, 도착 판정, 실제 지도 선택·경로 적용 테스트 통과.
- 해당 카메라 시험에서 Depth는 **미획득**이었다. 기기의 Depth 지원 여부와 별개로 실제 획득·정합 품질은 현장에서 추가 검증해야 한다. 가상의 깊이를 만들지 않았고 보정 승인도 없었다.
- 최초 카메라 시험은 휴대폰 화면이 잠든 상태여서 프레임을 얻지 못했다. 사용자가 잠금을 해제한 뒤 단독 재시험과 최종 전체 시험에서 통과했다. 이 실패 기록도 원본 로그에 남겼다.
- 실제 디버그 화면을 저장하고 마스크·상태 패널을 육안 확인했다. 정상 화면 버튼과 상태 패널이 겹치지 않게 위치를 조정했다.

증거: `D:/Coding/ARNav_Mobile/verification/segformer-final-device-tests.txt`, `segformer-camera-device-tests.txt`, `segformer-debug-qa.png`. APK: 같은 폴더의 `arnav-segformer-localization-debug.apk`.

### 문화관 실제 이동 영상

앱에 포함한 **동일 ONNX 파일**을 CPU ONNX Runtime 1.24.3으로 실행했다. 두 원본 영상 전체 시간대에서 2Hz로 샘플링했다. 원본과 기존 10fps 캐시의 SHA256을 잠금 파일과 대조하고 검증된 캐시를 사용했다. 캐시는 압축·축소된 영상이므로 원본의 모든 약 60fps 프레임을 직접 추론한 결과는 아니다.

| 영상 | 길이 | 검사 프레임 | PC 추론 중앙값 / p95 | door 검출 프레임 | stairs / stairway 검출 프레임 |
|---|---:|---:|---:|---:|---:|
| `2Fto1F.mp4` | 118.1초 | 237 | 54.49 / 59.10ms | 89 | 16 / 17 |
| `4Fto3F.mp4` | 80.5초 | 161 | 54.51 / 58.35ms | 89 | 6 / 12 |

클래스 검출 프레임은 해당 클래스의 면적이 최소 약 16픽셀 이상인 프레임이다. 정답률, 실제 문 수, 지도 매칭 성공률을 의미하지 않는다. 계단 두 클래스는 같은 프레임에 함께 나타날 수 있다. PC 시간은 모델 실행만 측정하며 Android의 후처리 포함 시간과 직접 비교하면 안 된다.

출력 폴더: `D:/Coding/ARNav_Mobile/model-tests/ade20k/results/school-onnx-localization-20261112`.

- `index.html`: 두 영상의 원본/마스크 비교 재생.
- 각 영상의 `comparison.mp4`, `preview-*.jpg`: 색 마스크 및 후보 bbox. 2개 대표 장면을 육안 확인했다.
- `observations.jsonl`: 각 촬영 시각의 후보·신뢰도·2D 배치·보류 사유.
- `frames.csv`: 프레임별 실행 시간 및 관심 클래스 면적.
- `report.json`: 모델/영상 해시, 런타임, 입출력 정보 및 집계.

일반 MP4에는 ARCore Pose·Depth·측정 정답이 없어 영상 테스트의 위치 보정은 **0회**이며 위치 오차·mIoU·보정 향상률을 산출할 수 없다. 복도 장면에서 바닥·벽의 큰 영역은 구분되지만 일부 출입구가 벽과 섞이는 등 오류가 보인다. PC의 시간적 후보 표시는 간단한 영상 진단용이며 Android 매처를 영상에서 실행한 것으로 간주하지 않는다.

## 9. 문화관 현장에서 추가 검증할 항목

1. 실험 복도 1~2개의 문 표면 중심, 계단 기준점, 좌표·방향을 같은 지도 좌표계에서 실측하고 기준점 의미를 확인한다. 최소 3개 이상의 안정적으로 관측되는 기준점이 필요하다.
2. 휴대폰을 움직여 Depth를 실제 획득하고, 문 표면에서 영상 마스크·Depth·투영 좌표가 정합하는지 확인한다. 현재 자동 테스트는 Depth 부재 조건까지만 실제 기기로 검증했다.
3. 알려진 출발 위치에서 현장 정답점별 보정 전/후 횡방향·진행방향 오차와 yaw 오차를 기록한다.
4. 동일 문 반복, 사람 가림, 유리/반사, 열린 문, 조명 변화, 계단에서 오보정 횟수와 보류율을 측정한다. 단일·복수·부분 가림 문에 대한 정답 라벨 기반 정확도 검증은 아직 하지 않았다.
5. 장시간 이동의 추론 전체 지연·메모리·발열·배터리·렌더링 FPS와 AR 녹화/재생 중 동작을 측정한다.

## 10. 제한사항 및 남은 기능

- 문화관 실측 의미 기준점, 문 방향, 벽 선분 지도와 층고가 부족하다. 노드 좌표를 실측 구조물 중심으로 꾸며 승인하지 않았다. 현재 운영 보정은 보류된다.
- 벽 경계는 2D 추출/표시 단계다. 실측 벽 지도와 3D 선분·복도 방향을 결합하는 매칭은 미구현이다. Depth 부재 시 화면상 배치만으로 metric 위치를 보정하는 fallback도 사용하지 않는다.
- 현재 프레임 간 bbox 근접 확인은 장기 객체 추적·광학 흐름·인스턴스 분할이 아니다. 붙은 문 분리, 가려진 동일 문 재식별과 계단 랜딩의 정확한 대표점 선택은 추가 연구가 필요하다.
- 지도 매칭은 지역 2D rigid 정합이다. 높이·층 변경·스케일 오차·건물 전역 위치 탐색을 해결하지 않는다. 후보 수를 제한하므로 매우 조밀한 구간의 유효 기준점을 놓칠 수 있다.
- ADE20K 범용 모델을 문화관에 맞춰 재학습하지 않았다. 영상 정답 라벨이 없어 정량 분할 정확도와 현장 오보정률을 알 수 없다.
- 휴대폰에서 실제 추론과 기존 렌더링은 검증했지만, 문화관의 물리적 경로 정합 및 장시간 성능을 완료했다고 주장하지 않는다.

## 재현

Android 프로젝트 루트 `D:/Coding/ARNav_Mobile/ARNavigation/src`에서 위 Gradle 명령을 실행한다. 폰 테스트는 화면이 켜지고 잠금이 해제된 상태에서 앱 및 androidTest APK를 각각 `adb install -r`로 설치한 후 AndroidJUnitRunner로 위 세 클래스를 실행한다. 기존 데이터 보존을 위해 자동 제거하는 connected-test 작업 대신 수동 instrumentation을 사용했다.

PC는 `requirements-onnx-video.txt`의 라이브러리와 PATH의 FFmpeg가 필요하다. 작업 공간 루트에서 다음을 실행한다. 출력은 기존 결과를 덮어쓰지 않는 새 폴더여야 한다.

```powershell
& model-tests/ade20k/.venv/Scripts/python.exe model-tests/ade20k/segformer_onnx_school.py --output model-tests/ade20k/results/school-onnx-new-run
```

모델 설정 원본: https://huggingface.co/optimum/segformer-b0-finetuned-ade-512-512/blob/main/config.json 및 같은 저장소의 `preprocessor_config.json`. ONNX Android 의존성/Java API는 https://onnxruntime.ai/docs/get-started/with-java.html 을 기준으로 확인했다.
