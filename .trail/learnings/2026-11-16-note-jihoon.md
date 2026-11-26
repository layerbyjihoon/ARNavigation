---
title: "문화관 독립 전환 앱은 별도 모델 프로필을 사용한다"
author: Jihoon
date: 2026-11-16
id: l-9d435e44
scope: repo
status: active
src: agent
---

## Decided
문화관 독립 앱은 내비게이션과 GPS 없이 문화관에서 사용자가 스캔하는 범위로 한정한다. EnvironmentTransitionController는 문 밖에서도 실내 계단이 보이므로 구조 일치 후 명시적 진입 확인을 유지한다. 실외 기준의 연속 일치는 퇴장 판정에 사용한다.

## Tried and ruled out
NVIDIA Hugging Face 체크포인트는 resolve/raw/고정 리비전 요청이 HTTP 429로 실패했다. 공식 OpenMMLab ADE20K B0 배포본을 사용했다. 같은 B0라도 디코더가 달라 Hugging Face 모델로 가중치를 임의 로드하지 않았다.

## Watch out
기존 P0 프로필과 새 모델 프로필을 혼용하지 않는다. 모델 교체 시 마스크도 다시 생성한다. 등록 사진의 회귀 통과는 독립 현장 정확도가 아니다.

In the code:
- Entrance_Transition/tools/prepare_assets.py: strict 가중치 로드, ONNX export, 네 사진 마스크 생성 및 수치 비교.
- Entrance_Transition/app/src/main/assets/munhwagwan.json: mmseg-segformer-b0-ade20k-512-v1 및 모델 SHA-256.
- docs/문화관 전환 모듈 실행.md: 독립 앱 실행 및 검증 범위.
