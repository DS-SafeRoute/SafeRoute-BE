package com.saferoute.domain.evacuation.graph.dto.request;

// DOOR 타입 노드의 시작 후보 여부만 위치/타입과 별개로 토글할 때 사용
public record UpdateMapNodeStartCandidateRequest(boolean isStartCandidate) {}
