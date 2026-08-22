package com.saferoute.domain.congestion.service;

import com.saferoute.domain.congestion.dto.response.CongestionConfigQueryResponse;
import com.saferoute.domain.congestion.entity.CongestionConfig;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.repository.CctvGridCellRepository;
import com.saferoute.domain.device.util.MonitoredAreaCalculator;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Pi가 주기적으로 호출하는 혼잡 설정 조회(이슈 6)를 처리한다.
// CCTV 인증/활성화 검증은 컨트롤러에서 DeviceAuthorizationService로 먼저 끝낸 뒤 이 서비스를 호출한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CongestionConfigQueryService {

    private final CctvGridCellRepository cctvGridCellRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final CongestionConfigService congestionConfigService;

    public CongestionConfigQueryResponse getConfigFor(Cctv cctv) {
        CongestionConfig config = congestionConfigService.getConfig();
        Floor floor = cctv.getCustomNode().getFloor();
        var buildingId = floor.getBuilding().getId();

        TrainingSession runningSession = trainingSessionRepository
                .findFirstByStatusAndScenario_Building_IdOrderByStartedAtAsc(TrainingStatus.RUNNING, buildingId)
                .orElse(null);

        if (runningSession == null) {
            return CongestionConfigQueryResponse.inactive(cctv.getCode(), config);
        }

        int gridCellCount = cctvGridCellRepository.countByCctv_Id(cctv.getId());
        Double monitoredAreaM2 = MonitoredAreaCalculator.calculate(gridCellCount, floor.getGridCellSizeMeter());

        return CongestionConfigQueryResponse.active(
                runningSession.getId().toString(),
                cctv.getCode(),
                monitoredAreaM2,
                config
        );
    }
}
