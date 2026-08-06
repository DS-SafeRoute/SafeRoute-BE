package com.saferoute.domain.device.client;

import com.saferoute.domain.device.entity.IoTLightDirection;

// 라즈베리파이(Pi Flask 서버)로 전달하는 방향 명령 요청 body.
// lightCode는 IoTLight.code를 그대로 사용한다 - Pi 한 대가 릴레이 2대(분기점 2곳)를 담당하므로
// 어느 분기점에 대한 명령인지 Pi 쪽이 이 값으로 식별한다.
public record PiLightDirectionRequest(
        String lightCode,
        IoTLightDirection direction
) {}
