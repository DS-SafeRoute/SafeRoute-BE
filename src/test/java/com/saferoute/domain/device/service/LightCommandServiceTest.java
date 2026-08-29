package com.saferoute.domain.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.saferoute.domain.device.dto.request.AckLightCommandRequest;
import com.saferoute.domain.device.dto.response.LightCommandAckResponse;
import com.saferoute.domain.device.dto.response.LightCommandListResponse;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.entity.IoTLight;
import com.saferoute.domain.device.entity.IoTLightDirection;
import com.saferoute.domain.device.entity.LightCommand;
import com.saferoute.domain.device.entity.LightCommandStatus;
import com.saferoute.domain.device.repository.IoTLightJpaRepository;
import com.saferoute.domain.device.repository.LightCommandJpaRepository;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.global.api.error.IoTLightErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.security.DevicePrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LightCommandServiceTest {

    @InjectMocks
    private LightCommandService lightCommandService;

    @Mock
    private IoTLightJpaRepository iotLightJpaRepository;

    @Mock
    private LightCommandJpaRepository lightCommandJpaRepository;

    private Floor floor;
    private Cctv cctv;

    @BeforeEach
    void setUp() {
        floor = Mockito.mock(Floor.class);
        cctv = Cctv.create("CCTV_001", "CCTV_001", node("CCTV_001"));
        ReflectionTestUtils.setField(cctv, "id", UUID.randomUUID());
    }

    private MapNode node(String code) {
        MapNode node = MapNode.createCustom(floor, code, code, 0, 0);
        ReflectionTestUtils.setField(node, "id", UUID.randomUUID());
        return node;
    }

    private IoTLight lightWithCctv(String code, Cctv owningCctv) {
        IoTLight light = IoTLight.create(code, code, node(code));
        ReflectionTestUtils.setField(light, "id", UUID.randomUUID());
        if (owningCctv != null) {
            light.assignCctv(owningCctv);
        }
        return light;
    }

    private LightCommand pendingCommand(IoTLight light, IoTLightDirection direction) {
        LightCommand command = LightCommand.createPending(light, direction);
        ReflectionTestUtils.setField(command, "id", UUID.randomUUID());
        return command;
    }

    // === pollCommands ===

    @Test
    @DisplayName("담당 유도등마다 최신 PENDING 명령 하나씩만 SENT로 전환해 반환한다")
    void pollCommands_returnsLatestPendingPerLight_andMarksSent() {
        // given
        IoTLight lightA = lightWithCctv("LIGHT_001", cctv);
        IoTLight lightB = lightWithCctv("LIGHT_002", cctv);
        LightCommand commandA = pendingCommand(lightA, IoTLightDirection.LEFT);
        LightCommand commandB = pendingCommand(lightB, IoTLightDirection.RIGHT);

        given(iotLightJpaRepository.findAllByCctv_Id(cctv.getId())).willReturn(List.of(lightA, lightB));
        given(lightCommandJpaRepository.findFirstByLight_IdAndStatusOrderByCreatedAtDesc(
                lightA.getId(), LightCommandStatus.PENDING)).willReturn(Optional.of(commandA));
        given(lightCommandJpaRepository.findFirstByLight_IdAndStatusOrderByCreatedAtDesc(
                lightB.getId(), LightCommandStatus.PENDING)).willReturn(Optional.of(commandB));

        // when
        LightCommandListResponse response = lightCommandService.pollCommands(cctv);

        // then
        assertThat(response.commands()).hasSize(2);
        assertThat(commandA.getStatus()).isEqualTo(LightCommandStatus.SENT);
        assertThat(commandB.getStatus()).isEqualTo(LightCommandStatus.SENT);
    }

    @Test
    @DisplayName("담당 유도등에 PENDING 명령이 없으면 빈 목록을 반환한다")
    void pollCommands_noPendingCommands_returnsEmptyList() {
        // given
        IoTLight light = lightWithCctv("LIGHT_001", cctv);
        given(iotLightJpaRepository.findAllByCctv_Id(cctv.getId())).willReturn(List.of(light));
        given(lightCommandJpaRepository.findFirstByLight_IdAndStatusOrderByCreatedAtDesc(
                light.getId(), LightCommandStatus.PENDING)).willReturn(Optional.empty());

        // when
        LightCommandListResponse response = lightCommandService.pollCommands(cctv);

        // then
        assertThat(response.commands()).isEmpty();
    }

    // === ack ===

    @Test
    @DisplayName("성공 ACK를 받으면 명령을 ACKED로 전환한다")
    void ack_success_marksAcked() {
        // given
        IoTLight light = lightWithCctv("LIGHT_001", cctv);
        LightCommand command = pendingCommand(light, IoTLightDirection.LEFT);
        command.markSent(java.time.Instant.now());
        given(lightCommandJpaRepository.findById(command.getId())).willReturn(Optional.of(command));
        DevicePrincipal principal = new DevicePrincipal(cctv.getId(), cctv.getCode());

        // when
        LightCommandAckResponse response = lightCommandService.ack(
                command.getId(), principal, new AckLightCommandRequest(true, null));

        // then
        assertThat(response.status()).isEqualTo(LightCommandStatus.ACKED);
        assertThat(command.getStatus()).isEqualTo(LightCommandStatus.ACKED);
    }

    @Test
    @DisplayName("실패 ACK를 받으면 명령을 FAILED로 전환하고 사유를 남긴다")
    void ack_failure_marksFailed() {
        // given
        IoTLight light = lightWithCctv("LIGHT_001", cctv);
        LightCommand command = pendingCommand(light, IoTLightDirection.LEFT);
        command.markSent(java.time.Instant.now());
        given(lightCommandJpaRepository.findById(command.getId())).willReturn(Optional.of(command));
        DevicePrincipal principal = new DevicePrincipal(cctv.getId(), cctv.getCode());

        // when
        lightCommandService.ack(command.getId(), principal, new AckLightCommandRequest(false, "relay timeout"));

        // then
        assertThat(command.getStatus()).isEqualTo(LightCommandStatus.FAILED);
        assertThat(command.getFailReason()).isEqualTo("relay timeout");
    }

    @Test
    @DisplayName("이미 ACKED된 명령에 재전송이 와도 상태가 바뀌지 않는다 (멱등)")
    void ack_alreadyAcked_isIdempotent() {
        // given
        IoTLight light = lightWithCctv("LIGHT_001", cctv);
        LightCommand command = pendingCommand(light, IoTLightDirection.LEFT);
        command.markSent(java.time.Instant.now());
        command.ack(java.time.Instant.now());
        given(lightCommandJpaRepository.findById(command.getId())).willReturn(Optional.of(command));
        DevicePrincipal principal = new DevicePrincipal(cctv.getId(), cctv.getCode());

        // when
        LightCommandAckResponse response = lightCommandService.ack(
                command.getId(), principal, new AckLightCommandRequest(false, "late retry"));

        // then: 이미 ACKED이므로 실패 보고가 와도 그대로 ACKED 유지
        assertThat(response.status()).isEqualTo(LightCommandStatus.ACKED);
        assertThat(command.getStatus()).isEqualTo(LightCommandStatus.ACKED);
    }

    @Test
    @DisplayName("존재하지 않는 명령이면 예외가 발생한다")
    void ack_commandNotFound_throws() {
        // given
        UUID unknownId = UUID.randomUUID();
        given(lightCommandJpaRepository.findById(unknownId)).willReturn(Optional.empty());
        DevicePrincipal principal = new DevicePrincipal(cctv.getId(), cctv.getCode());

        // when & then
        assertThatThrownBy(() -> lightCommandService.ack(
                unknownId, principal, new AckLightCommandRequest(true, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage(IoTLightErrorCode.LIGHT_COMMAND_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("다른 CCTV(Pi)가 ACK를 보내면 예외가 발생한다")
    void ack_cctvMismatch_throws() {
        // given
        IoTLight light = lightWithCctv("LIGHT_001", cctv);
        LightCommand command = pendingCommand(light, IoTLightDirection.LEFT);
        command.markSent(java.time.Instant.now());
        given(lightCommandJpaRepository.findById(command.getId())).willReturn(Optional.of(command));
        DevicePrincipal otherPrincipal = new DevicePrincipal(UUID.randomUUID(), "CCTV_999");

        // when & then
        assertThatThrownBy(() -> lightCommandService.ack(
                command.getId(), otherPrincipal, new AckLightCommandRequest(true, null)))
                .isInstanceOf(ApiException.class)
                .hasMessage(IoTLightErrorCode.LIGHT_COMMAND_CCTV_MISMATCH.getMessage());
        assertThat(command.getStatus()).isEqualTo(LightCommandStatus.SENT);
    }
}
