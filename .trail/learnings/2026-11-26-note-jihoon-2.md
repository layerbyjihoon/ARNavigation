---
title: "전환 종료는 바닥 높이 변환과 화면 복원 순서에서 발생했다"
author: Jihoon
date: 2026-11-26
branch: main
id: l-8621796c
scope: repo
status: active
src: agent
touches:
  - AR_Navigation_Final/app/src/main/java/com/example/arnavigation/navigation/NavigationViewModel.java
  - AR_Navigation_Final/app/src/test/java/com/example/arnavigation/navigation/NavigationViewModelTest.java
---

## Decided
Preserve nullable live floor results using explicit branches in FloorPlaneGeometry.navigationFloor because mixing primitive float and nullable Float in a ternary unboxes null before the caller's guard. Acquire the host ViewModel in MapFragment.onViewCreated and ArNavigationFragment.onViewCreated because restored fragments attach during Activity.super.onCreate, before MainActivity initializes its session fields.
## Tried and ruled out
Device crash stacks identified Float.floatValue in HelloArActivity.processNavigationRequests and then NavigationViewModel.getState in MapFragment.onViewCreated. A focused FloorPlaneGeometryTest reproduced the null floor crash. NavigationRestoreTest using ActivityScenario.recreate reproduced the exact map stack before the fix. Do not infer a model-loading or camera-handoff problem from these symptoms alone.
## Watch out
A missing floor means wait, not crash. Playback must use recorded height without querying live planes. Test restoration of both the map and outdoor AR fragments; a fresh launch alone misses the early attachment bug. IndoorAutoStartTest keeps AR frames running before reset.
In the code: `AR_Navigation_Final/indoor/src/main/java/com/google/ar/core/examples/java/helloar/FloorPlaneGeometry.java`, `AR_Navigation_Final/indoor/src/main/java/com/google/ar/core/examples/java/helloar/HelloArActivity.java`, `AR_Navigation_Final/app/src/main/java/com/example/arnavigation/ui/map/MapFragment.java`, `AR_Navigation_Final/app/src/main/java/com/example/arnavigation/ui/ar/ArNavigationFragment.java`.
