---
title: Room confirmation needs a real background under the integrated theme
author: Jihoon
date: 2026-11-25
branch: main
id: l-7f0ef3dc
scope: repo
status: active
src: agent
---

## Decided
In MapRouteActivity.showRoomPicker, explicitly set the shared nav_button_primary background before tint because the integrated Material3 style under the indoor AppCompat theme produces a null background. These files are present and indexed but currently untracked by git, so the file-recognition check rejects their valid paths.
## Tried and ruled out
Native and support background tint alone failed because there was no drawable to tint. The actual room-tap instrumentation reproduced fill=fffef7ff and text=ffffffff, then passed after adding the drawable.
## Watch out
Use the real activity and select a room because an isolated Material-themed layout misses the cross-module theme interaction. Keep RoomButtonAppearanceTest as the regression.
In the code:
AR_Navigation_Final/indoor/src/main/java/com/google/ar/core/examples/java/helloar/MapRouteActivity.java
AR_Navigation_Final/app/src/androidTest/java/com/example/arnavigation/journey/RoomButtonAppearanceTest.java
