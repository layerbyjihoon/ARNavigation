# 문화관 정문 Cloud Anchor 등록

휴대폰 `실내 AR내비게이션 → AR 녹화 데이터`의 최근 파일은 2026-11-26 17:18에 시작한 4분 31초짜리 ARCore 녹화다. 목록의 출발점은 **1층 계단 A**이며, 약 **3:20–3:36**에 정문 밖 기둥과 안내판, 유리 출입문, 진입 직후 복도가 이어진다. 종전 3층 계단 A 녹화와 구분한다. 입구에서는 안내판이 붙은 고정 석재 기둥과 주변 벽을 카메라에 함께 담는다. 유리문은 프레임에서 보이더라도 기준점으로 사용하기 어렵다.

## 등록

1. Google Cloud 프로젝트에서 **ARCore API**를 활성화한다. 공식 샘플과 통합 앱의 Android 패키지·서명 SHA-1 조합을 각각 Android OAuth 클라이언트로 등록한다. 샘플은 `com.google.ar.core.examples.java.persistentcloudanchor`, 통합 디버그 앱은 `com.example.arnavigation.finalapp.debug`다. 각 프로젝트의 Gradle Wrapper에서 `:app:signingReport`를 실행해 서명을 확인한다. [Google Cloud 승인 안내](https://developers.google.com/ar/develop/authorization?platform=android)
2. 공식 Java 샘플 `persistent_cloud_anchor_java`의 **HOST**를 연다. 고정 기둥 가까이에서 도면 좌표를 실측할 수 있는 **수평 바닥 지점**을 탭하고, 기둥과 벽이 여러 각도에서 보이도록 휴대폰을 움직인다. 호스팅에 성공하면 공유 기능으로 실제 Cloud Anchor ID를 확보한다. 사용할 샘플 버전의 호스팅 TTL을 확인하고, 만료 전에 재등록하거나 Keyless로 기간을 관리한다. [샘플 사용법](https://developers.google.com/ar/develop/java/cloud-anchors/quickstart?hl=ko) · [수명과 복원](https://developers.google.com/ar/develop/java/cloud-anchors/developer-guide)
3. 샘플을 새로 열고 **RESOLVE**로 앵커가 실제 바닥 기준점에 다시 나타나는지 확인한다. 정문 바깥 접근 방향과 실내 진입 방향에서 각각 확인한다.
4. 탭한 지점의 문화관 도면 X/Y/Z를 미터로 실측한다. Y는 1층 바닥 기준이다. 정렬에는 **복원된 앵커의 로컬 +Z 축 방향**을 사용한다. 샘플에서 보정할 때는 앵커 Pose의 원점과 `pose.transformPoint(new float[] {0, 0, 1})` 위치를 표시해 방향을 확인할 수 있다. 도면 −Z가 0°, 도면 +X가 +90°, 도면 +Z가 180°다. 카메라 방위, 자북 70° 설정, 문 중심이나 GPS 값으로 대체하면 경로가 어긋날 수 있다.
5. 아래 형식의 파일을 `AR_Navigation_Final/app/src/main/assets/entrance_cloud_anchor.json`에 저장하고 통합 앱을 다시 빌드한다. 꺾쇠로 표시한 값은 실제 ID와 실측 숫자로 채운다.

```json
{
  "schemaVersion": 1,
  "buildingId": "ssu-munhwagwan",
  "entranceId": "F1_ENTRY_MAIN",
  "floorId": "F1",
  "mapId": "munhwagwan-navigation-2026-10-07",
  "mapVersion": "1.0",
  "cloudAnchorId": "<실제 Cloud Anchor ID>",
  "mapPose": {
    "x": "<실측 X>",
    "y": "<실측 Y>",
    "z": "<실측 Z>",
    "yawDegrees": "<실측 앵커 방향>"
  }
}
```

## 검증

- 통합 앱에서 문화관 호실을 선택하고 정문에서 기존 실외 안내의 진입 확인을 통과한다. **입구 스캔**을 선택하고 기둥과 벽을 비춘다. 복원 후 지정된 복도 시작점에서 화살표의 위치와 방향을 현장 바닥과 비교한다.
- **기존 방식으로 안내**를 선택하면 기존 실내 초기화가 실행된다. 복원 오류나 35초 초과 시에는 화면에 표시된 출발점에서 안내를 수동으로 시작할 수 있다.
- 현재 통합본의 녹화 재생 화면은 입구의 촬영 시야와 실내 경로 확인에 쓸 수 있다. ARCore는 데이터셋 재생 중에도 Cloud Anchor 작업을 지원한다. 녹화에서 실제 호스팅을 시험하려면 ARCore 재생 세션에서 대상 바닥점 배치와 호스팅 품질을 확인해야 한다. [ARCore 녹화 및 재생](https://developers.google.com/ar/develop/java/recording-and-playback/developer-guide)
