# 문·계단 누적 및 벽·복도 방향 보정 재시험

2026-11-12. 이 문서는 최초 `semantic-localization.md` 이후의 변경 사항과 같은 `2Fto1F.mp4` 재시험을 기록한다.

**앱에 여러 프레임 구조물 누적, 이동 시차를 통한 삼각측량, 벽·바닥 경계의 복도 방향 제약을 연결했다. 같은 영상의 실제 마스크를 다시 추론한 시험에서는 보정 승인이 0회였다.** 가상 AR 위치·방향과 가상 깊이를 제공해도 구조물/방향 합의가 부족했다. 실제 AR 센서가 없는 일반 MP4로 현장의 보정 정확도를 입증하지 않는다.

## 1. 프레임을 넘겨 관측을 모으는 방식

`TemporalLandmarkAccumulator`가 출발 Anchor 기준의 변경되지 않은 좌표계에서 문·계단 관측을 유지한다.

- 최대 12초, 구조물 트랙 최대 32개, 대기 중인 2D 관측 트랙 최대 24개.
- 같은 유형의 3D 위치가 0.45m 이내이면 같은 트랙으로 갱신한다. 한 프레임의 여러 관측을 같은 트랙에 중복 대응하지 않는다. 같은 문을 세 번 봤다고 문 세 개로 계산하지 않는다.
- 여러 시점에서 확인되거나 기존 추출기가 시간적 일관성을 확인한 후보만 지도 매칭에 사용한다.
- 최신 프레임에 새로 확인된 metric 관측이 없으면 과거 구조물만으로 위치 보정 확인 횟수를 늘리지 않는다.
- 정지/재개, 새 경로, 층 변경, 추적 손실, 2초를 넘는 프레임 공백, 2.5m를 넘는 카메라 위치 불연속에서 초기화한다. 현재 앱의 같은 층 경로 제약은 유지된다.

## 2. Depth가 없는 관측의 삼각측량

`CaptureGeometry`가 촬영 시점 intrinsics·회전·Pose·원점 변환을 사용해 시선을 계산한다. `TemporalLandmarkAccumulator`는 같은 유형·유사한 bbox 위치와 면적의 후보를 연결한다.

- 카메라 이동 기준선 최소 0.5m, 시차 최소 3도.
- 두 시선의 양의 거리 0.3~8m, 교차 오차 최대 0.12m.
- 0.2m 이내로 일치하는 두 번의 삼각측량이 필요하다. 검증 카메라 위치가 최소 0.15m 더 달라야 하므로, 두 위치에서 찍은 뒤 멈춰 서서 프레임만 반복하는 것으로 세 번째 시점을 만들 수 없다.
- `PositionSource.TRIANGULATED`와 `DEPTH`를 구분한다. 삼각측량의 `metricValid=true`는 실제 Depth 획득을 뜻하지 않으며 `depthValid=false`를 유지한다.
- 충분한 카메라 이동이나 안정적인 후보 대응이 없으면 3D 좌표를 생성하지 않는다. bbox 근접 기반 연결은 장기 객체 재식별이 아니며 서로 비슷한 문을 잘못 연결할 가능성이 남아 있다.

## 3. 벽·복도 방향 활용

기존 `WallStructureExtractor`의 벽·바닥 접점 폴리라인을 `CorridorDirectionEstimator`로 전달한다. 화면 선의 기울기를 곧바로 yaw로 사용하지 않는다.

1. 촬영 시점의 각 경계 픽셀에서 시선을 구한다.
2. 출발 시 검출한 수평 바닥과 교차시킨다. 앱의 경로 원점은 바닥보다 0.52m 위에 있으므로 원점 기준 바닥 높이는 -0.52m다.
3. 유효한 경계 선분의 수평 방향을 계산한다. 출력 경계 12픽셀 이상, 투영 길이 1.5~12m, 시선 거리 0.5~10m, 카메라 높이 0.6~2.5m 조건을 사용한다.
4. 실제 지도에서 WAYPOINT 사이 복도 선분의 방향을 가져와 현재 위치 주변에서 비교한다. 비슷하게 가까운 수직 방향의 복도가 함께 있는 코너에서는 방향 제약을 사용하지 않는다.
5. 예상 복도 축과 15도 이내인 후보 중에서 선택하고, 최종 보정 각은 ±10도 이내로 제한한다. 서로 다른 세 프레임에서 2도 이내로 일관되어야 한다.

이것은 ARCore 중력 정렬과 알려진 바닥 평면을 이용하는 방식이다. 계단·경사진 바닥이나 원점 추적 불량에서는 이 평면 가정이 맞지 않을 수 있어 현장 검증이 필요하다. 벽 자체의 실측 선분 좌표는 만들지 않았으며, 대응 기준은 기존 그래프의 복도 축이다.

## 4. 지도 매칭과 보정 승인

`TemporalSemanticLocalizer`가 누적 구조물, 복도 방향, 기존 `SemanticMapMatcher`를 묶는다.

- 복도 방향이 없으면 기존과 같이 서로 다른 구조물 3개 이상을 요구한다. 이제 세 개를 같은 프레임에서 볼 필요는 없다.
- 독립적으로 확인한 복도 방향이 있으면 구조물 2개로도 후보를 만들 수 있다. 유형·거리·일대일 대응·잔차·반복 배치 모호성·서로 다른 프레임의 합의 검사는 유지한다. 후보 회전은 관측한 복도 축과 4도 이내여야 한다.
- 위치를 정할 구조물이 부족해도 복도 방향이 세 프레임 합의되면 **방향만 보정**할 수 있다. 현재 추정 카메라 위치를 회전 중심으로 삼아, 방향 정보만으로 복도를 따라 이동한 거리를 새로 정하지 않는다. 승인 사유는 `CORRIDOR_YAW_ONLY_CAMERA_PIVOT`다.
- 방향만 보정할 때 회전 증분은 최대 0.75도 및 원점 변환 변화 약 0.3m 이내로 제한한다. 기존 `RouteAlignmentManager`의 전체 범위, 목표 점프, 평활화 제한도 그대로 통과해야 한다.
- 기존 카메라 Pose와 Anchor는 덮어쓰지 않는다. 경로 렌더링과 사용자 상대 좌표는 같은 보정 변환을 사용한다.

기존 그래프의 DOOR/STAIR 좌표는 이제 `planReference=true`인 **도면 기준점**으로 사용할 수 있다. `field_verified`나 `verified`를 실측한 것처럼 true로 바꾸지 않는다. 실측 여부와 도면 사용 여부를 별도로 표시한다. 기준점 수 cm 차이를 실측 플래그 하나만으로 차단하던 제한은 해제했으며, 실제 기하학적 잔차와 모호성 검사로 승인한다. 생성자가 단순히 `verified=false`인 임의 좌표를 받은 경우에는 `planReference`도 명시되지 않는 한 계속 거절한다.

## 5. 구현 파일

기준 경로는 `src/app/src/main/java/com/google/ar/core/examples/java/helloar/`이다.

신규:

- `localization/CaptureGeometry.java`
- `localization/TemporalLandmarkAccumulator.java`
- `localization/CorridorDirectionEstimator.java`
- `localization/TemporalSemanticLocalizer.java`
- 단위 테스트 `src/app/src/test/java/com/google/ar/core/examples/java/helloar/localization/TemporalLocalizationTest.java`

변경:

- `LandmarkObservation.java`: metric 유효성과 위치 획득 방식 구분.
- `SemanticLandmarkMap.java`: 도면 기준점과 복도 방향 데이터, 코너 모호성 검사.
- `SemanticMapMatcher.java`: 누적 관측 입력 및 복도 방향 제약.
- `SegmentationController.java`: 기존 단일 비동기 작업 안에서 누적·벽 방향·삼각측량·매칭을 실행. 세대 변경 시 누적 상태도 초기화.
- `HelloArActivity.java`: 실측/도면 사용 수, 누적 구조물 수, 복도 방향 합의 상태 표시.

PC 도구는 `model-tests/ade20k/temporal_wall_replay.py`, `java/TemporalVideoReplay.java`를 추가했다. 기존 모델 시험 스크립트는 영상 하나 선택과 픽셀 마스크·신뢰도 보존을 지원한다. HTML 생성 원본과 검증 도구에는 새 시험 조건과 벽 경계·누적 상태 표시를 추가했다.

## 6. 같은 영상 재시험

실제 `2Fto1F.mp4`의 118.1초 전체 시간대에서 2Hz로 237프레임을 다시 추론했다. 모델 SHA256은 최초 시험과 같다. 원본 SHA256도 `d8d98cb2f8e5da06a8e88ff94640c3289e9e67d1e4dedd4279718ec435cde7b5`로 동일하다. 검증된 10fps 캐시를 디코딩했다.

SegFormer CPU 추론 중앙값은 60.43ms, p95는 67.09ms였다. 이는 PC의 모델 실행 시간이며 Android 전체 파이프라인 지연이 아니다. 관심 클래스 검출 프레임 수는 최초 결과와 동일했다.

Java의 실제 `LandmarkExtractor`, `WallStructureExtractor`, `CorridorDirectionEstimator`, `TemporalSemanticLocalizer`, `SemanticMapMatcher`, `RouteAlignmentManager`를 PC에서 그대로 컴파일해 사용했다. Python으로 보정 알고리즘을 흉내 내지 않았다.

| 시험 조건 | 승인 | 구조물 위치 보정 / 방향만 | 최대 누적 | 평균 가상 위치 오차 전 → 후 |
|---|---:|---:|---:|---:|
| 기존 단일 프레임 + 가상 AR·깊이 | 0 | 0 / 0 | — | 44.49 → 44.49cm |
| 문·계단 누적 + 가상 AR·깊이 | 0 | 0 / 0 | 2 | 44.49 → 44.49cm |
| 누적 + 실제 벽 경계 + 가상 AR·깊이 | 0 | 0 / 0 | 2 | 44.49 → 44.49cm |
| 누적 + 실제 벽 경계 + 가상 AR, Depth 없음 | 0 | 0 / 0 | 0 | 44.49 → 44.49cm |
| 누적·벽 + 80~86초 추적 손실 | 0 | 0 / 0 | 2 | 44.49 → 44.49cm |
| 이상적 구조물·방향의 가상 대조군 | 141 | 130 / 11 | 8 | 44.49 → 1.71cm |
| 실제 MP4만, AR Pose 없음 | 0 | 0 / 0 | 0 | 측정 불가 |

**대조군의 1.71cm는 현장 성능이 아니다.** 지도 구조물의 정확한 대응과 이상적인 방향을 알고 있다고 가정한 코드 동작 시험이다. 같은 영상의 실제 인식 결과로 141회 보정했다고 읽으면 안 된다.

영상은 벽·바닥 경계를 173프레임에서 제공했다. 가상 카메라 자세와 intrinsics를 사용해 지도 축 주변의 유효한 방향 후보가 생긴 프레임은 11개였다. 그러나 세 프레임의 최종 방향 합의는 0회였다. 구조물 누적도 최대 2개였고, 현재 유효한 방향 제약이 없을 때 필요한 3개를 충족하지 못했다. No-Depth 시험은 삼각측량을 승인할 만큼 안정적인 대응·시차를 확보하지 못했다.

## 7. 시험 가정과 해석 범위

실제 입력은 모델의 전체 마스크·픽셀 신뢰도와 문·계단·벽 경계다. 실제 ARCore Pose·Depth·intrinsics는 영상에 없다.

- 사용자가 지정한 13/50/69/106초, 층과 그래프 동선을 재사용했다. 복도 이동은 등속, 층고·계단 모양·카메라 시선은 가정이다.
- 가상 깊이를 제공하는 조건은 검출된 유형과 후보 수에 맞춰 가까운 같은 유형의 지도 기준점에서 metric 관측을 생성한다. 이는 **정확한 기준점 대응을 제공하는 합성 가정**이지 영상에서 대응이나 깊이를 알아낸 결과가 아니다.
- No-Depth 조건은 실제 bbox와 가상 카메라 시선을 사용해 시차 검사를 수행한다. 실제 영상의 카메라 회전과 가정한 회전이 다를 수 있어 실패나 방향 불일치를 앱의 현장 성능으로 단정하지 않는다.
- 순수 영상 조건은 AR Pose를 제공하지 않으며 metric 보정을 보류한다. HTML에서도 위치 마커와 오차 선을 표시하지 않는다.

따라서 이번 결과는 누적과 방향 제약이 앱에 연결되고, 근거가 부족할 때 보류함을 보여준다. 실제 문화관에서의 오차 감소는 카메라/ARCore Pose/바닥 높이가 동기화된 녹화와 비교해야 한다.

## 8. 빌드·폰·화면 검증

- `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` 성공. 최종 단위 테스트 **109개 실패 0개**. Lint 오류 0개, 경고 36개.
- 새 단위 테스트는 구조물 중복 제거, 한 프레임에 하나씩 관측한 세 기준점으로 이동 오차 복원, 과거 관측만 재사용한 승인 금지, 만료·리셋, 정지 시 삼각측량 금지, 실제 시차가 있는 여러 시점의 복원, 두 위치 반복을 세 번째 시점으로 세지 않기, 벽 경계의 바닥 투영, 방향만 보정할 때 카메라 위치 보존, 코너 거절, 구조물 2개+복도 방향 제약을 포함한다.
- 연결된 SM-S937N에서 정적 모델·실제 AR 카메라·pause/resume·기존 경로 렌더링·지도 선택을 포함한 **14개 테스트 통과**. 실제 카메라 추론 약 262ms, Depth 미획득. 정적 입력의 PC/Android 마스크 일치율 100%.
- 이후 정지한 두 번째 카메라 시점을 재사용하지 않게 하는 삼각측량 검사를 추가하고, 최종 빌드·단위 테스트·동일 영상 재시험을 다시 통과했다. 이 마지막 변경은 폰의 실제 복도 삼각측량으로 검증한 것이 아니다.
- 폰은 앱 데이터를 보존하는 `adb install -r`로 업데이트하고 설치 상태를 유지한다.
- Chrome 자동 검증: 영상 재생, 프레임 시각 동기화, 문 검출 시각 클릭, 가상 보정 시각 클릭, 순수 영상 조건의 승인 0건, 360px 화면 폭 검사 통과. HTML 안에 영상이 포함돼 별도 상대 경로에 의존하지 않는다.

## 9. 산출물과 재현

- 결과 뷰어: `D:/Coding/ARNav_Mobile/model-tests/ade20k/results/temporal-wall-replay-20261112/index.html`
- 기존 입구 `model-tests/ade20k/synthetic_replay_viewer.html`은 새 뷰어로 연결된다.
- 새 모델 결과/마스크: `model-tests/ade20k/results/school-temporal-wall-20261112/2Fto1F/`
- 보정별 상태·누적 수·방향 후보·삼각측량 수: `temporal-results.json`, 조건별 CSV.
- 문 검출/가상 보정 시각: `events.json`, `events.csv`.
- 입력과 코드 SHA256 및 가정: `scenario.json`.
- 폰 검증 로그: `D:/Coding/ARNav_Mobile/verification/temporal-wall-device-tests.txt`.
- 최종 APK: `D:/Coding/ARNav_Mobile/verification/arnav-temporal-wall-debug.apk`.

```powershell
& model-tests/ade20k/.venv/Scripts/python.exe model-tests/ade20k/segformer_onnx_school.py --video 2Fto1F --output model-tests/ade20k/results/a-new-segmentation-directory
# 기본 재시험은 위에 기록한 school-temporal-wall-20261112의 보존 마스크를 사용한다.
& model-tests/ade20k/.venv/Scripts/python.exe model-tests/ade20k/temporal_wall_replay.py
node model-tests/ade20k/validate_synthetic_viewer.cjs model-tests/ade20k/results/temporal-wall-replay-20261112
```

## 10. 남은 검증

문화관에서 실제 ARCore 녹화·카메라 intrinsics·바닥 높이·센서 Pose를 함께 확보해 재시험해야 한다. 열린 문/가려진 문에서 bbox 중심이 움직이는 문제, 여러 시점의 동일 문 대응, 벽 바닥 경계가 가구에 가려지는 상황, 긴 복도 방향만으로 진행 위치를 결정할 수 없는 모호성을 확인해야 한다. 전체 건물 위치 재탐색·자동 층 변경·계단 보행 중 수평 바닥 보정은 이번 범위에 포함하지 않았다.
