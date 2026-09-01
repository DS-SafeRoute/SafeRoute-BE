package com.saferoute.domain.device.entity;

public enum LightCommandStatus {
    // BE가 적재했고 아직 Pi가 폴링해가지 않음
    PENDING,
    // Pi가 폴링해가서 실행 중 (ACK 대기)
    SENT,
    // Pi가 실행 성공을 보고함
    ACKED,
    // Pi가 실행 실패를 보고함
    FAILED,
    // SENT 상태로 일정 시간 ACK가 없어 스케줄러가 타임아웃 처리함
    TIMED_OUT,
    // 같은 유도등에 더 최신 명령이 적재되어 이 명령은 실행 의미가 없어짐
    SUPERSEDED
}
