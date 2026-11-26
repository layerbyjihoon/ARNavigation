---
title: "문화관 시작점은 두 복도 노드의 중점으로 고정한다"
author: Jihoon
date: 2026-11-26
branch: main
id: l-d568cf93
scope: repo
status: active
src: agent
touches:
  - AR_Navigation_Final/app/src/main/java/com/example/arnavigation/navigation/NavigationViewModel.java
  - AR_Navigation_Final/app/src/test/java/com/example/arnavigation/navigation/NavigationViewModelTest.java
---

## Decided
사용자 요청에 따라 통합본의 출발점을 F1_C006·F1_C007 중점으로 고정하고 기존 +X 1m 오프셋을 제거한다. MapRouteActivity.selectedStart가 쓰는 출발 POI ID는 유지해 호실 선택과 앱 인계가 같은 위치를 사용한다.
## Tried and ruled out
기존 별도 중점과 1m 연결 간선은 유지하지 않는다. 시작점을 중점으로 옮긴 뒤 이 간선을 남기면 중복 좌표와 불필요한 거리가 생긴다.
## Watch out
분리 프로젝트 indoor_navi에는 적용하지 않는다. 지도 시작점 지정은 실제 위치의 자동 측위가 아니다.
In the code: `AR_Navigation_Final/indoor/src/main/assets/maps/munhwagwan.indoor.json`의 F1_START_C006_C007은 X 12.251, Z 3.0465이며 두 노드에 각각 2.0845m로 직접 연결한다. F1_C006_C007_MID와 E_START_RIGHT_1M은 제거했다. `docs/PRD.md`와 MunhwagwanMapTest에 반영한다.
