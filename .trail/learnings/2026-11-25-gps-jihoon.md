---
title: "가까운 실외 방향은 GPS 좌우 오차를 제거한 경로 위치에서 계산한다"
author: Jihoon
date: 2026-11-25
branch: main
id: l-7950e9b0
scope: repo
status: active
src: agent
touches:
  - AR_Navigation_Final/app/src/main/java/com/example/arnavigation/navigation/NavigationViewModel.java
  - AR_Navigation_Final/app/src/test/java/com/example/arnavigation/navigation/NavigationViewModelTest.java
---

## Decided
실외 경로 허용 범위 안에서는 경로에 투영한 위치와 10m 앞 경로 지점 사이의 방향을 사용한다. 화면 방향은 계속 후면 카메라 방위와의 차이로 계산한다. 허용 범위 밖에서는 실제 위치에서 복귀 방향을 계산한다.
## Tried and ruled out
35m까지 경로 안으로 인정하면서 실제 GPS에서 10m 앞 경로 지점을 바라보게 하면, 옆으로 30m 어긋난 GPS가 직진 화살표를 약 71.58도 틀어 버린다. 가까운 타깃만 도입하는 것으로는 해결되지 않았다. 실제 상태 계산 회귀 테스트에서 수정 전 실패하고 수정 후 통과하는지 확인한다.
## Watch out
투영 위치가 다음 안내 지점과 같으면 두 점의 방위가 0도가 되어 북쪽으로 튈 수 있다. 진행 갱신 전에는 해당 구간의 진입 방향을 유지한다. 이 재현은 계산 결함을 증명하며 실제 현장의 자기장 센서 오류까지 증명하지 않는다.
In the code: RouteDirectionTarget.bearing, NavigationViewModel.updateRelativeAngle, gpsSidewaysOffsetInsideCorridorDoesNotTurnForwardArrowSideways.
