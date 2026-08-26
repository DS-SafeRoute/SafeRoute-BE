package com.saferoute.domain.evacuation.deviation.service;

import com.saferoute.domain.device.entity.IoTLight;
import com.saferoute.domain.device.entity.IoTLightDirection;
import com.saferoute.domain.device.repository.CctvGridCellRepository;
import com.saferoute.domain.device.repository.IoTLightJpaRepository;
import com.saferoute.domain.evacuation.deviation.dto.RouteDeviationResponse;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.telemetry.dynamo.entity.LightDirectionEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.LightDirectionEventRepository;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.IoTLightErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 유도등이 안내한 방향(LightDirectionEventItem 이력)과 CCTV가 실제로 인원을 탐지한 위치(ObservationItem)를
// 시간 축으로 조인해 경로 이탈률을 계산한다. CCTV 한 대는 headcount/밀집도만 보고하므로 개별 이동 경로까지는
// 알 수 없지만, "좌/우 통로를 각각 감시하는 CCTV가 분리되어 있다"는 전제(데모 구성) 하에 어느 쪽 CCTV에서
// 인원이 탐지됐는지를 실제 이동 방향의 대리 지표로 사용한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteDeviationService {

    private static final int OBSERVATION_QUERY_LIMIT = 2000;

    private final IoTLightJpaRepository iotLightJpaRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final LightDirectionEventRepository lightDirectionEventRepository;
    private final ObservationRepository observationRepository;
    private final MapEdgeGridCellRepository mapEdgeGridCellRepository;
    private final CctvGridCellRepository cctvGridCellRepository;
    private final SchoolContextService schoolContextService;

    public RouteDeviationResponse calculate(UUID lightId, UUID trainingSessionId, String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        IoTLight light = iotLightJpaRepository
                .findByIdAndCustomNode_Floor_Building_SchoolName(lightId, schoolName)
                .orElseThrow(() -> new ApiException(IoTLightErrorCode.IOT_LIGHT_NOT_FOUND));
        TrainingSession session = trainingSessionRepository
                .findByIdAndScenario_Building_SchoolName(trainingSessionId, schoolName)
                .orElseThrow(() -> new ApiException(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND));

        if (!light.isGuidanceConfigured()) {
            throw new ApiException(IoTLightErrorCode.GUIDANCE_NOT_CONFIGURED);
        }

        Set<String> leftCctvCodes = resolveCctvCodes(light.getLeftEdge().getId());
        Set<String> rightCctvCodes = resolveCctvCodes(light.getRightEdge().getId());
        Set<String> ambiguous = intersect(leftCctvCodes, rightCctvCodes);
        leftCctvCodes.removeAll(ambiguous);
        rightCctvCodes.removeAll(ambiguous);
        if (leftCctvCodes.isEmpty() || rightCctvCodes.isEmpty()) {
            throw new ApiException(IoTLightErrorCode.DEVIATION_CCTV_MAPPING_NOT_FOUND);
        }

        List<LightDirectionEventItem> directionEvents = lightDirectionEventRepository
                .findAllBySessionIdAndLightCode(session.getId().toString(), light.getCode());

        long total = 0;
        long deviated = 0;
        for (String cctvCode : union(leftCctvCodes, rightCctvCodes)) {
            IoTLightDirection guidedSide = leftCctvCodes.contains(cctvCode)
                    ? IoTLightDirection.LEFT
                    : IoTLightDirection.RIGHT;
            List<ObservationItem> observations = observationRepository.findAllBySessionIdAndCctvCode(
                    session.getId().toString(), cctvCode, OBSERVATION_QUERY_LIMIT);
            for (ObservationItem observation : observations) {
                if (observation.getAvgHeadcount() == null || observation.getAvgHeadcount() <= 0) {
                    continue;
                }
                IoTLightDirection activeDirection = activeDirectionAt(directionEvents, observation.getCapturedAt());
                if (activeDirection == null || activeDirection == IoTLightDirection.OFF) {
                    continue;
                }
                total++;
                if (activeDirection != guidedSide) {
                    deviated++;
                }
            }
        }

        double deviationRate = total == 0 ? 0.0 : (double) deviated / total;
        return new RouteDeviationResponse(light.getId(), session.getId(), total, deviated, deviationRate);
    }

    // 관측 시각 시점에 유도등이 가리키고 있던 방향. 이력이 시간순으로 정렬되어 있으므로
    // 해당 시각 이전(이하)의 마지막 전환 이벤트를 찾는다. 전환 이력이 아직 없으면 null.
    private IoTLightDirection activeDirectionAt(List<LightDirectionEventItem> events, long timestamp) {
        IoTLightDirection active = null;
        for (LightDirectionEventItem event : events) {
            if (event.getChangedAt() > timestamp) {
                break;
            }
            active = event.getDirection();
        }
        return active;
    }

    // Edge -> GridCell -> CCTV 역방향 조회로, 이 통로를 감시하는 CCTV 코드 목록을 찾는다.
    private Set<String> resolveCctvCodes(UUID edgeId) {
        List<UUID> gridCellIds = mapEdgeGridCellRepository.findAllByMapEdge_Id(edgeId).stream()
                .map(mapping -> mapping.getGridCell().getId())
                .toList();
        if (gridCellIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<String> codes = new HashSet<>();
        cctvGridCellRepository.findAllByGridCell_IdIn(gridCellIds)
                .forEach(mapping -> codes.add(mapping.getCctv().getCode()));
        return codes;
    }

    private Set<String> intersect(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.retainAll(right);
        return result;
    }

    private Set<String> union(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.addAll(right);
        return result;
    }
}
