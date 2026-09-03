package com.saferoute.domain.congestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.congestion.dto.request.ReportObservationRequest;
import com.saferoute.domain.congestion.entity.CongestionConfig;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.CctvGridCell;
import com.saferoute.domain.device.repository.CctvGridCellRepository;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.deviation.service.RouteDeviationService;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationTriggerType;
import com.saferoute.domain.evacuation.recalculation.service.RouteRecalculationService;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.telemetry.dynamo.entity.GeneralMonitoringEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.GeneralMonitoringEventType;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.CurrentCctvStateRepository;
import com.saferoute.domain.telemetry.dynamo.repository.GeneralMonitoringEventRepository;
import com.saferoute.domain.telemetry.dynamo.repository.IdempotentSaveResult;
import com.saferoute.domain.telemetry.dynamo.repository.LatestMonitoringCaptureRepository;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationCountRepository;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.global.api.error.CongestionErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CongestionObservationServiceTest {

    @InjectMocks
    private CongestionObservationService service;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private ObservationCountRepository observationCountRepository;

    @Mock
    private GeneralMonitoringEventRepository generalMonitoringEventRepository;

    @Mock
    private LatestMonitoringCaptureRepository latestMonitoringCaptureRepository;

    @Mock
    private CurrentCctvStateRepository currentCctvStateRepository;

    @Mock
    private TrainingSessionRepository trainingSessionRepository;

    @Mock
    private CctvGridCellRepository cctvGridCellRepository;

    @Mock
    private MapEdgeGridCellRepository mapEdgeGridCellRepository;

    @Mock
    private CongestionConfigService congestionConfigService;

    @Mock
    private RouteRecalculationService routeRecalculationService;

    @Mock
    private RouteDeviationService routeDeviationService;

    @Mock
    private TrainingEventPublisher trainingEventPublisher;

    private final UUID cctvId = UUID.randomUUID();
    private final UUID buildingId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private Cctv cctv;

    @BeforeEach
    void setUp() {
        Building building = mock(Building.class);
        org.mockito.Mockito.lenient().when(building.getId()).thenReturn(buildingId);
        Floor floor = mock(Floor.class);
        org.mockito.Mockito.lenient().when(floor.getBuilding()).thenReturn(building);
        org.mockito.Mockito.lenient().when(floor.getGridCellSizeMeter()).thenReturn(1.0);
        MapNode node = mock(MapNode.class);
        org.mockito.Mockito.lenient().when(node.getFloor()).thenReturn(floor);
        cctv = mock(Cctv.class);
        org.mockito.Mockito.lenient().when(cctv.getId()).thenReturn(cctvId);
        org.mockito.Mockito.lenient().when(cctv.getCode()).thenReturn("CCTV_001");
        org.mockito.Mockito.lenient().when(cctv.getCustomNode()).thenReturn(node);

        org.mockito.Mockito.lenient().when(congestionConfigService.getConfig())
                .thenReturn(CongestionConfig.createDefault());
        // 기본은 GridCell 4개(면적 4.0m2)로, avgHeadcount=5 -> density=1.25 (NORMAL)
        org.mockito.Mockito.lenient().when(cctvGridCellRepository.countByCctv_Id(cctvId)).thenReturn(4);
    }

    private ReportObservationRequest request(double avgHeadcount) {
        return request(avgHeadcount, null);
    }

    private ReportObservationRequest request(double avgHeadcount, String monitoringImageKey) {
        return new ReportObservationRequest(
                UUID.randomUUID(), sessionId, "CCTV_001", avgHeadcount, 8, 25,
                1_000L, 2_000L, 2_000L, 1L, monitoringImageKey
        );
    }

    private void givenProcessingClaimed() {
        given(observationRepository.claimProcessing(anyString(), anyString(), anyLong(), anyLong()))
                .willReturn(true);
        given(observationRepository.completeProcessing(anyString(), anyString())).willReturn(true);
        given(observationRepository.saveIfAbsent(any())).willAnswer(invocation ->
                IdempotentSaveResult.created(invocation.getArgument(0, ObservationItem.class)));
    }

    private MapEdge edge(UUID id) {
        MapEdge edge = mock(MapEdge.class);
        org.mockito.Mockito.lenient().when(edge.getId()).thenReturn(id);
        return edge;
    }

    private void givenAffectedEdges(MapEdge... edges) {
        UUID gridCellId = UUID.randomUUID();
        FloorGridCell gridCell = mock(FloorGridCell.class);
        given(gridCell.getId()).willReturn(gridCellId);
        CctvGridCell mapping = mock(CctvGridCell.class);
        given(mapping.getGridCell()).willReturn(gridCell);
        given(cctvGridCellRepository.findAllByCctv_IdOrderByGridCell_RowIndexAscGridCell_ColumnIndexAsc(cctvId))
                .willReturn(List.of(mapping));

        List<MapEdgeGridCell> edgeMappings = java.util.Arrays.stream(edges)
                .map(e -> {
                    MapEdgeGridCell edgeGridCell = mock(MapEdgeGridCell.class);
                    given(edgeGridCell.getMapEdge()).willReturn(e);
                    return edgeGridCell;
                })
                .toList();
        given(mapEdgeGridCellRepository.findAllByGridCell_IdIn(List.of(gridCellId))).willReturn(edgeMappings);
    }

    @Test
    @DisplayName("대상 건물에 RUNNING 세션이 없으면 전용 에러를 반환하고 아무것도 저장하지 않는다")
    void reportObservation_throwsWhenNoRunningSession() {
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.reportObservation(cctv, request(5.0)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", TrainingErrorCode.RUNNING_TRAINING_SESSION_NOT_FOUND);

        verify(observationRepository, never()).saveIfAbsent(any());
    }

    @Test
    @DisplayName("감시 면적을 계산할 수 없으면 전용 에러를 반환한다")
    void reportObservation_throwsWhenMonitoredAreaUnavailable() {
        TrainingSession session = mock(TrainingSession.class);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        given(cctvGridCellRepository.countByCctv_Id(cctvId)).willReturn(0);

        assertThatThrownBy(() -> service.reportObservation(cctv, request(5.0)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", CongestionErrorCode.MONITORED_AREA_NOT_AVAILABLE);

        verify(observationRepository, never()).saveIfAbsent(any());
    }

    @Test
    @DisplayName("BE가 직접 density와 congestionLevel을 계산해서 저장한다 (Pi 값을 받지 않음)")
    void reportObservation_computesDensityAndLevelItself() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        givenProcessingClaimed();
        givenAffectedEdges();

        // avgHeadcount=9, 면적=4.0 -> density=2.25 -> CAUTION (기본 임계값: CAUTION>=2.0, CROWDED>=3.0)
        service.reportObservation(cctv, request(9.0));

        verify(observationRepository).saveIfAbsent(argThat(item ->
                item.getDensity().equals(2.25) && item.getCongestionLevel() == CongestionLevel.CAUTION));
    }

    @Test
    @DisplayName("NORMAL/CAUTION 수준이면 재탐색을 트리거하지 않는다")
    void reportObservation_doesNotTriggerRecalculationForLowLevel() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        givenProcessingClaimed();
        givenAffectedEdges(edge(UUID.randomUUID()));

        // avgHeadcount=5 -> density=1.25 -> NORMAL
        service.reportObservation(cctv, request(5.0));

        verify(routeRecalculationService, never()).trigger(any(), any(), any(), any(), any(), anyDouble());
        verify(latestMonitoringCaptureRepository).updateIfLatest(argThat(capture ->
                capture.getTrainingSessionId().equals(sessionId.toString())
                        && capture.getCctvCode().equals("CCTV_001")
                        && capture.getCapturedAt().equals(2_000L)
                        && capture.getMonitoringImageKey() == null
        ));
        verify(currentCctvStateRepository).updateIfLatest(any());
    }

    @Test
    @DisplayName("CCTV가 여러 Edge를 감시하면 CROWDED 이상일 때 Edge마다 재탐색을 트리거하고 각각 발행한다")
    void reportObservation_triggersRecalculationForEachAffectedEdge() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        givenProcessingClaimed();
        MapEdge edgeA = edge(UUID.randomUUID());
        MapEdge edgeB = edge(UUID.randomUUID());
        givenAffectedEdges(edgeA, edgeB);

        // avgHeadcount=13 -> density=3.25 -> CROWDED
        service.reportObservation(cctv, request(13.0));

        verify(routeRecalculationService).trigger(eq(session), eq(edgeA), eq(CongestionLevel.CROWDED),
                eq(RecalculationTriggerType.LEVEL_UP), eq("CCTV_001"), anyDouble());
        verify(routeRecalculationService).trigger(eq(session), eq(edgeB), eq(CongestionLevel.CROWDED),
                eq(RecalculationTriggerType.LEVEL_UP), eq("CCTV_001"), anyDouble());
        verify(trainingEventPublisher, times(2)).publishCongestionUpdated(eq(sessionId), any(), any());
    }

    @Test
    @DisplayName("매핑된 Edge가 없으면 edgeId 없이 한 번만 발행하고 재탐색은 트리거하지 않는다")
    void reportObservation_noMappedEdges_publishesOnceWithoutEdgeId() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        givenProcessingClaimed();
        given(cctvGridCellRepository.findAllByCctv_IdOrderByGridCell_RowIndexAscGridCell_ColumnIndexAsc(cctvId))
                .willReturn(List.of());

        // avgHeadcount=13 -> density=3.25 -> CROWDED, 그래도 Edge가 없으니 트리거는 없음
        service.reportObservation(cctv, request(13.0));

        verify(routeRecalculationService, never()).trigger(any(), any(), any(), any(), any(), anyDouble());
        verify(trainingEventPublisher).publishCongestionUpdated(eq(sessionId), isNull(), any());
    }

    @Test
    @DisplayName("중복 eventId이면 발행과 재탐색을 다시 수행하지 않는다")
    void reportObservation_doesNotRepeatSideEffectsForDuplicateEvent() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        given(observationRepository.saveIfAbsent(any())).willAnswer(invocation ->
                IdempotentSaveResult.existing(invocation.getArgument(0, ObservationItem.class)));

        var result = service.reportObservation(cctv, request(5.0));

        assertThat(result.created()).isFalse();
        verify(trainingEventPublisher, never()).publishCongestionUpdated(any(), any(), any());
        verify(routeRecalculationService, never()).trigger(any(), any(), any(), any(), any(), anyDouble());
        verify(observationCountRepository, never()).increment(anyString(), anyString());
    }

    @Test
    @DisplayName("동일한 eventId가 다른 세션·CCTV를 가리키면 CONGESTION002를 반환한다")
    void reportObservation_rejectsMismatchedEventIdentity() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        given(observationRepository.saveIfAbsent(any())).willAnswer(invocation -> {
            ObservationItem existing = invocation.getArgument(0, ObservationItem.class);
            existing.setCctvCode("CCTV_999");
            return IdempotentSaveResult.existing(existing);
        });

        assertThatThrownBy(() -> service.reportObservation(cctv, request(5.0)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", CongestionErrorCode.EVENT_IDENTITY_MISMATCH);

        verify(observationRepository, never()).claimProcessing(anyString(), anyString(), anyLong(), anyLong());
        verify(generalMonitoringEventRepository, never()).saveIfAbsent(any());
        verify(routeDeviationService, never()).evaluateObservation(any(), any());
    }

    @Test
    @DisplayName("Observation 저장 자체가 실패하면 AI_ANALYSIS_STARTED 이벤트 생성을 시도하지 않는다")
    void reportObservation_doesNotTryToCreateAiAnalysisStartedEventWhenObservationSaveFails() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        given(observationRepository.saveIfAbsent(any())).willThrow(new RuntimeException("dynamo unavailable"));

        assertThatThrownBy(() -> service.reportObservation(cctv, request(5.0)))
                .isInstanceOf(RuntimeException.class);

        verify(generalMonitoringEventRepository, never()).saveIfAbsent(any());
    }

    @Test
    @DisplayName("monitoringImageKey가 canonical 형식(training/{sessionId}/monitoring/{cctvCode}/{capturedAt}.jpg)이면 저장된다")
    void reportObservation_acceptsValidMonitoringImageKey() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        givenProcessingClaimed();
        givenAffectedEdges();
        String key = "training/" + sessionId + "/monitoring/CCTV_001/2000.jpg";

        service.reportObservation(cctv, request(5.0, key));

        verify(observationRepository).saveIfAbsent(argThat(item ->
                key.equals(item.getMonitoringImageKey())));
    }

    @Test
    @DisplayName("빈 monitoringImageKey는 검증을 통과시키되 null로 정규화해서 저장한다")
    void reportObservation_normalizesBlankMonitoringImageKeyToNull() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        givenProcessingClaimed();
        givenAffectedEdges();

        service.reportObservation(cctv, request(5.0, ""));

        verify(observationRepository).saveIfAbsent(argThat(item ->
                item.getMonitoringImageKey() == null));
        verify(latestMonitoringCaptureRepository).updateIfLatest(argThat(capture ->
                capture.getMonitoringImageKey() == null));
    }

    @Test
    @DisplayName("monitoringImageKey의 capturedAt 세그먼트가 canonical decimal이 아니면 CONGESTION010을 반환한다")
    void reportObservation_rejectsNonCanonicalCapturedAtSegment() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> service.reportObservation(
                cctv, request(5.0, "training/" + sessionId + "/monitoring/CCTV_001/+2000.jpg")))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", CongestionErrorCode.MONITORING_IMAGE_KEY_INVALID);
        assertThatThrownBy(() -> service.reportObservation(
                cctv, request(5.0, "training/" + sessionId + "/monitoring/CCTV_001/02000.jpg")))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", CongestionErrorCode.MONITORING_IMAGE_KEY_INVALID);

        verify(observationRepository, never()).saveIfAbsent(any());
    }

    @Test
    @DisplayName("monitoringImageKey 형식이 canonical 경로가 아니면 CONGESTION010을 반환한다")
    void reportObservation_rejectsMalformedMonitoringImageKey() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> service.reportObservation(
                cctv, request(5.0, "training/" + sessionId + "/monitoring/CCTV_001/2000.png")))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", CongestionErrorCode.MONITORING_IMAGE_KEY_INVALID);

        verify(observationRepository, never()).saveIfAbsent(any());
    }

    @Test
    @DisplayName("monitoringImageKey의 세션 ID가 요청 세션과 다르면 CONGESTION011을 반환한다")
    void reportObservation_rejectsMonitoringImageKeyWithDifferentSession() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        String otherSessionId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> service.reportObservation(
                cctv, request(5.0, "training/" + otherSessionId + "/monitoring/CCTV_001/2000.jpg")))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", CongestionErrorCode.MONITORING_IMAGE_IDENTITY_MISMATCH);

        verify(observationRepository, never()).saveIfAbsent(any());
    }

    @Test
    @DisplayName("monitoringImageKey의 CCTV 코드가 요청 CCTV와 다르면 CONGESTION011을 반환한다")
    void reportObservation_rejectsMonitoringImageKeyWithDifferentCctvCode() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> service.reportObservation(
                cctv, request(5.0, "training/" + sessionId + "/monitoring/CCTV_999/2000.jpg")))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", CongestionErrorCode.MONITORING_IMAGE_IDENTITY_MISMATCH);

        verify(observationRepository, never()).saveIfAbsent(any());
    }

    @Test
    @DisplayName("monitoringImageKey의 capturedAt이 요청 capturedAt과 다르면 CONGESTION011을 반환한다")
    void reportObservation_rejectsMonitoringImageKeyWithDifferentCapturedAt() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> service.reportObservation(
                cctv, request(5.0, "training/" + sessionId + "/monitoring/CCTV_001/9999.jpg")))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", CongestionErrorCode.MONITORING_IMAGE_IDENTITY_MISMATCH);

        verify(observationRepository, never()).saveIfAbsent(any());
    }

    @Test
    @DisplayName("유효한 Observation을 저장하면 경로 이탈 판정을 시도한다")
    void reportObservation_triesToEvaluateRouteDeviation() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        givenProcessingClaimed();
        givenAffectedEdges();

        service.reportObservation(cctv, request(5.0));

        verify(routeDeviationService).evaluateObservation(eq(cctv), argThat(item ->
                item.getTrainingSessionId().equals(sessionId.toString())
                        && item.getCctvCode().equals("CCTV_001")
        ));
    }

    @Test
    @DisplayName("유효한 Observation을 저장하면 AI_ANALYSIS_STARTED 이벤트 생성을 시도한다")
    void reportObservation_triesToCreateAiAnalysisStartedEvent() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        givenProcessingClaimed();
        givenAffectedEdges();

        service.reportObservation(cctv, request(5.0));

        verify(generalMonitoringEventRepository).saveIfAbsent(argThat(item ->
                item.getTrainingSessionId().equals(sessionId.toString())
                        && item.getCctvCode().equals("CCTV_001")
                        && item.getEventType() == GeneralMonitoringEventType.AI_ANALYSIS_STARTED
                        && item.getOccurredAt() == 2_000L
                        && item.getCongestionLevel() == null
        ));
    }

    @Test
    @DisplayName("같은 세션+CCTV로 여러 번 호출해도 AI_ANALYSIS_STARTED 이벤트 eventId는 항상 같다 (결정적 생성)")
    void reportObservation_generatesDeterministicEventIdForAiAnalysisStartedEvent() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        givenProcessingClaimed();
        givenAffectedEdges();

        service.reportObservation(cctv, request(5.0));
        service.reportObservation(cctv, request(7.0));

        org.mockito.ArgumentCaptor<GeneralMonitoringEventItem> captor =
                org.mockito.ArgumentCaptor.forClass(GeneralMonitoringEventItem.class);
        verify(generalMonitoringEventRepository, times(2)).saveIfAbsent(captor.capture());
        List<GeneralMonitoringEventItem> saved = captor.getAllValues();
        assertThat(saved.get(0).getEventId()).isEqualTo(saved.get(1).getEventId());
    }

    @Test
    @DisplayName("AI_ANALYSIS_STARTED 이벤트 저장이 실패해도 Observation 저장 자체는 실패하지 않는다")
    void reportObservation_swallowsExceptionFromGeneralMonitoringEventRepository() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        givenProcessingClaimed();
        givenAffectedEdges();
        given(generalMonitoringEventRepository.saveIfAbsent(any()))
                .willThrow(new RuntimeException("dynamo unavailable"));

        var result = service.reportObservation(cctv, request(5.0));

        assertThat(result.created()).isTrue();
    }

    @Test
    @DisplayName("Observation이 새로 저장되면 세션+CCTV별 카운터를 증가시킨다")
    void reportObservation_incrementsObservationCountWhenNewlyCreated() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        givenProcessingClaimed();
        givenAffectedEdges();

        service.reportObservation(cctv, request(5.0));

        verify(observationCountRepository).increment(sessionId.toString(), "CCTV_001");
    }

    @Test
    @DisplayName("카운터 증가가 실패해도 Observation 저장 자체는 실패하지 않는다")
    void reportObservation_swallowsExceptionFromObservationCountRepository() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId)).willReturn(Optional.of(session));
        givenProcessingClaimed();
        givenAffectedEdges();
        org.mockito.Mockito.doThrow(new RuntimeException("dynamo unavailable"))
                .when(observationCountRepository).increment(anyString(), anyString());

        var result = service.reportObservation(cctv, request(5.0));

        assertThat(result.created()).isTrue();
    }
}
