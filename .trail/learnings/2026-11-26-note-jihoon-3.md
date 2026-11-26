---
title: "목적지까지 오십 미터 이내에서는 목적지를 직접 가리킨다"
author: Jihoon
date: 2026-11-26
branch: main
id: l-27d17ab2
scope: repo
status: active
replaces: l-7950e9b0
src: agent
touches:
  - AR_Navigation_Final/app/src/main/java/com/example/arnavigation/navigation/NavigationViewModel.java
  - AR_Navigation_Final/app/src/test/java/com/example/arnavigation/navigation/NavigationViewModelTest.java
---

## Decided
The user explicitly requested direct destination bearing within 50m on 2026-11-26 because the ten-meter route cue was misleading near the entrance. NavigationViewModel.updateRelativeAngle uses straight-line distance from current GPS to the selected Destination point. At <=50m it computes the bearing from current GPS directly to that point, including the configured Culture Hall entrance. Beyond 50m it retains the previous ten-meter path lookahead and corridor projection rules.
## Tried and ruled out
Do not apply the older projection-only learning inside the new 50m radius: this is an explicit user-authorized exception. Do not use the active intermediate waypoint or the route provider's snapped endpoint instead of the selected destination. Temporary entry direction probe logs were removed after collecting the reproduction.
## Watch out
This changes arrow aiming, not GPS accuracy or arrival detection. Indoor entry checking remains 30m with its existing GPS quality and vision requirements. Unit coverage at 49.9m and 50.1m uses a detouring route to distinguish direct destination guidance from route guidance. The old GPS lateral-drift test remains valid farther than 50m.
In the code: NavigationViewModel.updateRelativeAngle, AppConfig.DIRECT_DESTINATION_RADIUS_METERS, NavigationViewModelTest.fiftyMeterBoundarySwitchesFromRouteLookAheadToSelectedDestination; `AR_Navigation_Final/app/src/main/java/com/example/arnavigation/navigation/NavigationViewModel.java`; `docs/PRD.md`.
