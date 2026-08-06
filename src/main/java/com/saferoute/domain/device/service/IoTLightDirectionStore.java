package com.saferoute.domain.device.service;

import com.saferoute.domain.device.entity.IoTLightDirection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

// 유도등의 실시간 방향 상태를 보관한다. 훈련별로 바뀌는 동적 상태라 IoTLight 엔티티(DB)에는
// 저장하지 않고 서버 메모리에서만 관리한다 (IoTLight 클래스 상단 주석 참고).
// 서버가 재시작되면 초기화되는 것을 의도한 동작으로 본다 - 재시작 후에는 관리자가 방향을 다시 지정해야 한다.
@Component
public class IoTLightDirectionStore {

    private final Map<UUID, IoTLightDirection> directions = new ConcurrentHashMap<>();

    public void update(UUID lightId, IoTLightDirection direction) {
        directions.put(lightId, direction);
    }

    public IoTLightDirection get(UUID lightId) {
        return directions.getOrDefault(lightId, IoTLightDirection.OFF);
    }
}
