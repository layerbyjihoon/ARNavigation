---
title: "카메라 상태 피드백은 동일 값의 재발행을 막는다"
author: Jihoon
date: 2026-11-24
id: l-63134b32
scope: repo
status: active
src: agent
---

## Decided
NavigationViewModel.dispatch에서 SetCameraAvailable의 값이 실제로 바뀔 때만 publish한다. AR 관찰자가 상태를 동기적으로 돌려보내므로 동일 값도 발행하면 LiveData가 재진입 갱신을 끝내지 못해 UI가 멈춘다.
## Tried and ruled out
TMAP 요청이나 모델 로딩을 원인으로 추측하지 않고 실기기 data_app_anr의 main 스택으로 syncCamera 권한 거부 분기와 publish의 반복을 확인했다. 실제 LiveData 관찰자가 같은 상태를 되돌려 보내는 회귀 테스트는 수정 전 실패하고 후 통과했다.
## Watch out
권한 거부 외에 BINDING과 BOUND의 동기 콜백도 같은 경로를 쓴다. 렌더링 빈도 제한만으로는 갱신 루프가 멈추지 않는다. 원본 outdoor_navi는 이번 통합본 수정 범위 밖이다.

In the code:
AR_Navigation_Final/app/src/main/java/com/example/arnavigation/navigation/NavigationViewModel.java
NavigationViewModel.dispatch
AR_Navigation_Final/app/src/test/java/com/example/arnavigation/navigation/NavigationViewModelTest.java
cameraObserverFeedbackSettlesForDeniedBindingAndBoundStates
