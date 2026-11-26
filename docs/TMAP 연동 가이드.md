# 통합본 TMAP 보행 경로 연동

2026-11-25 사용자 요청으로 `AR_Navigation_Final`의 실외 경로 공급자를 TMAP으로 변경했다.
Google 지도와 Places 검색은 유지하며, 기존 Google Routes 키만으로는 보행 안내를 활성화하지 않는다.
`outdoor_navi`와 `indoor_navi` 원본은 변경하지 않는다.

## 설정

SK Open API에서 앱 생성 후 TMAP 상품을 신청하고 appKey를 발급받는다.
`AR_Navigation_Final/local.properties`에 다음 값을 추가하거나 같은 이름의 환경변수를 설정한다.
환경변수가 로컬 파일보다 우선한다. 키를 저장소에 커밋하지 않는다.

```properties
TMAP_APP_KEY=발급받은_appKey
```

키를 추가·교체한 후에는 APK 재빌드와 재설치가 필요하다.
Google 지도·장소 검색에는 기존 `MAPS_API_KEY`, `PLACES_API_KEY`를 계속 사용한다.
설정 화면의 준비 문구는 값의 존재만 검사하며 실제 API 사용 권한을 보장하지 않는다.

백엔드를 사용하는 경우 `TMAP_PROXY_BASE_URL=https://your-backend.example/`를 설정한다.
프록시는 `POST /tmap/routes/pedestrian?version=1`을 받아 서버에서 TMAP appKey를 넣고
동일한 GeoJSON 응답/HTTP 상태를 반환해야 한다. 이 경우 앱은 appKey를 전송하지 않는다.
기존 Google Routes 프록시는 요청/응답 규격이 달라 그대로 사용할 수 없다.
프록시 구현·배포는 이번 변경에 포함하지 않는다.

## 공식 API 계약

- [보행자 경로안내](https://tmap-skopenapi.readme.io/reference/보행자-경로안내)
- [요청·응답 예제](https://tmap-skopenapi.readme.io/reference/경로안내-샘플예제)

`POST https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1`을 호출한다.
직접 호출은 `appKey` 헤더, `Accept: application/json`, JSON 본문을 사용한다.
출발/도착 X는 경도, Y는 위도이며 요청/응답 좌표계는 모두 `WGS84GEO`다.
출발·도착 이름은 UTF-8 URL 인코딩한다. `searchOption=0`, `sort=index`를 사용한다.

`FeatureCollection`의 `properties.index` 순서로 정렬한다.
`LineString`의 모든 좌표를 경로 선과 웨이포인트에 보존하고, 인접 중복 좌표만 합친다.
`Point`의 `description`, `turnType`을 안내 문구와 회전 정보로 변환한다.
횡단보도 방향 코드도 회전으로 변환하고, 별도 enum이 없는 계단·육교 등의 문구는 그대로 유지한다.
출발점의 `totalDistance`(m), `totalTime`(초)를 사용하며 없으면 선 구간의 거리·시간을 합산한다.
기존 실내 전환 목적지를 유지하기 위해 선택한 출입구를 최종 도착 웨이포인트로 설정한다.
TMAP이 보행로에 맞춘 끝점과 선택한 출입구가 실제로 연결되는지는 현장 검증이 필요하다.

HTTP 401/403은 AUTHENTICATION, 429는 QUOTA, 5xx·통신 실패는 NETWORK로 처리한다.
빈 features는 NO_ROUTE, 잘못된 좌표·누락/음수 거리·시간 등은 MALFORMED_RESPONSE다.
기타 4xx는 MALFORMED_RESPONSE로 분류한다. 서비스 상세 오류 코드별 분류는 아직 하지 않는다.
새 요청이 시작되면 이전 요청을 취소하고 콜백은 요청당 한 번만 전달한다.

## 검증과 제한

MockWebServer와 GeoJSON 테스트로 요청 좌표·이름 인코딩·헤더, 응답 선의 꺾임,
횡단보도 회전, 순서, 거리·시간, 잘못된 응답과 취소·타임아웃을 검증한다.
실제 TMAP 키가 없는 환경에서는 실서버 경로 성공을 검증할 수 없다.

```bash
cd AR_Navigation_Final
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

TMAP 데이터는 디스크에 저장하지 않고 현재 안내 세션에서 사용한다.
[TMAP 약관](https://tmapapi.tmapmobility.com/terms.html)의 데이터 사용 시간 제한을 지켜야 한다.
Google 지도 위 경로 표출·카메라 AR 안내의 이용 허용 범위는 이번 공개 문서만으로
확정되지 않았으므로 배포 전에 TMAP에 해당 사용 방식을 확인해야 한다.

## 실내와 같은 경로 띠 표시

통합본 실외 카메라는 `OutdoorRouteRibbon`과 `ArrowOverlayView`로 하늘색 경로 띠,
짙은 테두리, 60cm 간격의 흰색 삼각형을 그린다. 활성 구간은 밝은 하늘색, 이후 구간은
짙은 하늘색이다. 현재 위치부터 활성 웨이포인트 이후의 경로를 진북 방위로 카메라 전방 좌표에
변환하고, 가상 수평면을 화면에 원근 투영한다. 현재 위치에서 경로를 따라 누적 12m까지만 만들고, 1.5m~12m 깊이에서 잘라 뒤쪽·지평선 부근의
발산을 방지하며 한 번에 최대 128구간을 처리한다. 렌더링은 기존 100ms 제한을 따른다.

실내처럼 보이는 스타일을 공유하지만 실외에는 ARCore 앵커·바닥 추적·휴대폰 pitch 투영이 없다.
휴대폰을 세운 상태를 가정한 고정 지평선이므로 카메라를 기울이면 실제 바닥과 겹치는 위치가
달라질 수 있다. GPS·방향 저신뢰, 경로 이탈, 카메라 미준비 시 기존 조건에 따라 숨긴다.
`OutdoorRouteRibbonTest`는 꺾임·방위·근평면·장거리 처리를,
`OutdoorRibbonRenderTest`는 실제 Android Canvas의 색상·삼각형·투명 배경을 검증한다.
