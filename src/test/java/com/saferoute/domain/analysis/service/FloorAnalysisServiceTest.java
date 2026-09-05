package com.saferoute.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.saferoute.domain.analysis.AiAnalysisClient;
import com.saferoute.domain.analysis.dto.AnalyseFloorResponse;
import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.entity.SegmentationStatus;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.api.error.AnalysisErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FloorAnalysisServiceTest {

    @InjectMocks
    private FloorAnalysisService floorAnalysisService;

    @Mock
    private FloorRepository floorRepository;
    @Mock
    private AiAnalysisClient aiAnalysisClient;
    @Mock
    private FloorAnalysisResultService resultService;
    @Mock
    private FloorAnalysisStatusService statusService;

    @Test
    void persistAnalysisResult_delegatesToTransactionalResultService() {
        UUID floorId = UUID.randomUUID();
        AnalyseFloorResponse response = org.mockito.Mockito.mock(AnalyseFloorResponse.class);

        floorAnalysisService.persistAnalysisResult(floorId, response);

        then(resultService).should().persistAnalysisResult(floorId, response);
    }

    @Test
    void analyzeFloor_marksFloorAsFailedWhenAiRequestFails() {
        UUID floorId = UUID.randomUUID();
        Floor floor = Floor.create(org.mockito.Mockito.mock(Building.class), 3);
        floor.upload(3.0, 4.0, "floors/third-floor.png");
        floor.updateSegmentationStatus(SegmentationStatus.PROCESSING);
        given(floorRepository.findById(floorId)).willReturn(Optional.of(floor));
        given(aiAnalysisClient.analyze("floors/third-floor.png", 4.0, 3.0))
                .willThrow(new RuntimeException("AI server unavailable"));

        assertThatThrownBy(() -> floorAnalysisService.analyzeFloor(floorId))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(AnalysisErrorCode.AI_ANALYSIS_FAILED);
        then(statusService).should().markAsFailed(floorId);
    }
}
