package com.saferoute.domain.training.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.training.dto.CreateFireZoneRequest;
import com.saferoute.domain.training.entity.FireZone;
import com.saferoute.domain.training.entity.ScenarioStatus;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.repository.FireZoneRepository;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
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
class FireZoneServiceTest {

    private static final String EMAIL = "manager@saferoute.com";
    private static final String SCHOOL_NAME = "SafeRoute School";

    @InjectMocks
    private FireZoneService fireZoneService;

    @Mock
    private FireZoneRepository fireZoneRepository;
    @Mock
    private TrainingScenarioRepository scenarioRepository;
    @Mock
    private FloorGridCellRepository gridCellRepository;
    @Mock
    private MapNodeJpaRepository mapNodeRepository;
    @Mock
    private SchoolContextService schoolContextService;

    private final UUID scenarioId = UUID.randomUUID();
    private final UUID buildingId = UUID.randomUUID();
    private final UUID floorId = UUID.randomUUID();
    private final UUID gridCellId = UUID.randomUUID();
    private TrainingScenario scenario;
    private FloorGridCell cell;
    private Floor floor;

    @BeforeEach
    void setUp() {
        scenario = mock(TrainingScenario.class);
        cell = mock(FloorGridCell.class);
        floor = mock(Floor.class);
        Building building = mock(Building.class);

        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
        given(scenarioRepository.findForUpdateByIdAndBuilding_SchoolName(scenarioId, SCHOOL_NAME))
                .willReturn(Optional.of(scenario));
        given(scenario.getStatus()).willReturn(ScenarioStatus.READY);
        org.mockito.Mockito.lenient().when(gridCellRepository.findById(gridCellId)).thenReturn(Optional.of(cell));
        org.mockito.Mockito.lenient().when(scenario.getBuildingId()).thenReturn(buildingId);
        org.mockito.Mockito.lenient().when(cell.getFloor()).thenReturn(floor);
        org.mockito.Mockito.lenient().when(floor.getId()).thenReturn(floorId);
        org.mockito.Mockito.lenient().when(floor.getBuilding()).thenReturn(building);
        org.mockito.Mockito.lenient().when(building.getId()).thenReturn(buildingId);
    }

    @Test
    @DisplayName("발화점을 등록하면 같은 층의 START 노드를 시나리오에 연결한다")
    void designateOrigin_assignsStartNodeOnSameFloor() {
        MapNode startNode = mock(MapNode.class);
        given(mapNodeRepository.findAllByFloor_IdAndType(floorId, NodeType.START))
                .willReturn(List.of(startNode));
        given(fireZoneRepository.save(org.mockito.ArgumentMatchers.any(FireZone.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        fireZoneService.designateOrigin(scenarioId, new CreateFireZoneRequest(gridCellId), EMAIL);

        verify(scenario).assignStartNode(startNode);
        verify(cell).markFired();
        verify(fireZoneRepository).save(org.mockito.ArgumentMatchers.any(FireZone.class));
    }

    @Test
    @DisplayName("시나리오에 최초 발화점이 이미 있으면 추가로 등록할 수 없다")
    void designateOrigin_alreadyConfigured_throwsBeforeMutation() {
        given(fireZoneRepository.existsByScenario_IdAndIsManualAddTrue(scenarioId)).willReturn(true);

        assertThatThrownBy(() -> fireZoneService.designateOrigin(
                scenarioId, new CreateFireZoneRequest(gridCellId), EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.FIRE_ORIGIN_ALREADY_CONFIGURED);

        verify(gridCellRepository, never()).findById(gridCellId);
        verify(cell, never()).markFired();
        verify(scenario, never()).assignStartNode(org.mockito.ArgumentMatchers.any());
        verify(fireZoneRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("발화 층에 START 노드가 없으면 발화점을 등록할 수 없다")
    void designateOrigin_startNodeNotFound_throws() {
        given(mapNodeRepository.findAllByFloor_IdAndType(floorId, NodeType.START)).willReturn(List.of());

        assertThatThrownBy(() -> fireZoneService.designateOrigin(
                scenarioId, new CreateFireZoneRequest(gridCellId), EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.FLOOR_START_NODE_NOT_FOUND);

        verify(cell, never()).markFired();
        verify(fireZoneRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("발화 층에 START 노드가 여러 개면 발화점을 등록할 수 없다")
    void designateOrigin_duplicatedStartNodes_throws() {
        given(mapNodeRepository.findAllByFloor_IdAndType(floorId, NodeType.START))
                .willReturn(List.of(mock(MapNode.class), mock(MapNode.class)));

        assertThatThrownBy(() -> fireZoneService.designateOrigin(
                scenarioId, new CreateFireZoneRequest(gridCellId), EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.FLOOR_START_NODE_DUPLICATED);

        verify(cell, never()).markFired();
        verify(fireZoneRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("READY가 아닌 시나리오의 발화점은 변경할 수 없다")
    void designateOrigin_scenarioNotReady_throwsBeforeMutation() {
        given(scenario.getStatus()).willReturn(ScenarioStatus.IN_PROGRESS);

        assertThatThrownBy(() -> fireZoneService.designateOrigin(
                scenarioId, new CreateFireZoneRequest(gridCellId), EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.INVALID_STATUS_TRANSITION);

        verify(gridCellRepository, never()).findById(gridCellId);
        verify(cell, never()).markFired();
        verify(scenario, never()).assignStartNode(org.mockito.ArgumentMatchers.any());
        verify(fireZoneRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
