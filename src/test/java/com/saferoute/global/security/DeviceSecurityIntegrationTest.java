package com.saferoute.global.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.congestion.dto.request.ReportCongestionRequest;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.congestion.service.CongestionEventService;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.evacuation.graph.entity.CustomDeviceType;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.IdempotentSaveResult;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeviceSecurityIntegrationTest {

    private static final String DEVICE_API = "/api/v1/device/congestion-events";
    private static final String RAW_TOKEN = "test-device-token";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DeviceTokenService deviceTokenService;
    @Autowired BuildingRepository buildingRepository;
    @Autowired FloorRepository floorRepository;
    @Autowired MapNodeJpaRepository mapNodeJpaRepository;
    @Autowired CctvJpaRepository cctvJpaRepository;

    @MockitoBean CongestionEventService congestionEventService;

    private Cctv authenticatedCctv;

    @BeforeEach
    void setUp() {
        authenticatedCctv = saveCctv("CCTV_001", RAW_TOKEN, true);
        given(congestionEventService.reportCongestion(any())).willAnswer(invocation -> {
            ReportCongestionRequest request = invocation.getArgument(0);
            ObservationItem item = ObservationItem.create(
                    request.eventId(),
                    request.trainingSessionId(),
                    request.cctvCode(),
                    request.avgHeadcount(),
                    request.peakHeadcount(),
                    request.sampleCount(),
                    request.density(),
                    request.congestionLevel(),
                    request.windowStart(),
                    request.windowEnd(),
                    request.capturedAt(),
                    request.monitoringImageKey(),
                    request.configVersion()
            );
            return IdempotentSaveResult.created(item);
        });
    }

    @Test
    @DisplayName("정상 디바이스 토큰과 일치하는 CCTV 코드는 요청을 허용한다")
    void allowsMatchingDeviceTokenAndCctvCode() throws Exception {
        mockMvc.perform(post(DEVICE_API)
                        .header("Authorization", "Bearer " + RAW_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("CCTV_001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cctvCode").value("CCTV_001"));
    }

    @Test
    @DisplayName("디바이스 토큰이 없으면 401을 반환한다")
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(post(DEVICE_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("CCTV_001")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEVICE001"));
    }

    @Test
    @DisplayName("등록되지 않은 디바이스 토큰이면 401을 반환한다")
    void rejectsUnknownToken() throws Exception {
        mockMvc.perform(post(DEVICE_API)
                        .header("Authorization", "Bearer unknown-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("CCTV_001")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DEVICE002"));
    }

    @Test
    @DisplayName("다른 CCTV 코드로 요청하면 403을 반환한다")
    void rejectsDifferentCctvCode() throws Exception {
        saveCctv("CCTV_002", "second-device-token", true);

        mockMvc.perform(post(DEVICE_API)
                        .header("Authorization", "Bearer " + RAW_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("CCTV_002")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEVICE003"));
    }

    @Test
    @DisplayName("등록되지 않은 CCTV 코드로 요청하면 404를 반환한다")
    void rejectsUnknownCctvCode() throws Exception {
        mockMvc.perform(post(DEVICE_API)
                        .header("Authorization", "Bearer " + RAW_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("CCTV_999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CCTV001"));
    }

    @Test
    @DisplayName("비활성 CCTV의 토큰이면 403을 반환한다")
    void rejectsDisabledCctv() throws Exception {
        authenticatedCctv.disable();
        cctvJpaRepository.flush();

        mockMvc.perform(post(DEVICE_API)
                        .header("Authorization", "Bearer " + RAW_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("CCTV_001")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEVICE004"));
    }

    @Test
    @DisplayName("디바이스 토큰으로 관리자 API에 접근할 수 없다")
    void deviceTokenCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/v1/buildings")
                        .header("Authorization", "Bearer " + RAW_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401"));
    }

    private Cctv saveCctv(String code, String rawToken, boolean enabled) {
        Building building = buildingRepository.save(Building.create(
                "테스트관",
                "서울특별시 안전구 테스트로 123",
                1,
                BuildingType.CLASSROOM
        ));
        Floor floor = floorRepository.save(Floor.create(building, 1));
        MapNode node = mapNodeJpaRepository.save(MapNode.createCustom(
                floor,
                code,
                code,
                0.5,
                0.5,
                CustomDeviceType.CCTV
        ));
        Cctv cctv = Cctv.create(code, code, node);
        cctv.issueDeviceToken(deviceTokenService.hash(rawToken));
        if (!enabled) {
            cctv.disable();
        }
        return cctvJpaRepository.saveAndFlush(cctv);
    }

    private String request(String cctvCode) throws Exception {
        ReportCongestionRequest request = new ReportCongestionRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                cctvCode,
                5.0,
                8,
                25,
                2.5,
                CongestionLevel.CROWDED,
                1_000L,
                2_000L,
                2_000L,
                1L,
                null
        );
        return objectMapper.writeValueAsString(request);
    }
}
