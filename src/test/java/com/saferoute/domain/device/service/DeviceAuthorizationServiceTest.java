package com.saferoute.domain.device.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.global.api.error.CctvErrorCode;
import com.saferoute.global.api.error.DeviceErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.security.DevicePrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceAuthorizationServiceTest {

    private final CctvJpaRepository repository = mock(CctvJpaRepository.class);
    private final DeviceAuthorizationService service = new DeviceAuthorizationService(repository);

    @Test
    void allowsMatchingCctv() {
        UUID cctvId = UUID.randomUUID();
        Cctv cctv = cctv(cctvId);
        given(repository.findByCode("CCTV_001")).willReturn(Optional.of(cctv));

        assertThatCode(() -> service.validateCctv(
                new DevicePrincipal(cctvId, "CCTV_001"),
                "CCTV_001"
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownCctvCode() {
        given(repository.findByCode("CCTV_999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateCctv(
                new DevicePrincipal(UUID.randomUUID(), "CCTV_001"),
                "CCTV_999"
        )).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", CctvErrorCode.CCTV_NOT_FOUND);
    }

    @Test
    void rejectsDifferentCctv() {
        Cctv requestedCctv = cctv(UUID.randomUUID());
        given(repository.findByCode("CCTV_002")).willReturn(Optional.of(requestedCctv));

        assertThatThrownBy(() -> service.validateCctv(
                new DevicePrincipal(UUID.randomUUID(), "CCTV_001"),
                "CCTV_002"
        )).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", DeviceErrorCode.CCTV_CODE_MISMATCH);
    }

    @Test
    void rejectsCctvDisabledAfterAuthentication() {
        UUID cctvId = UUID.randomUUID();
        Cctv requestedCctv = cctv(cctvId);
        given(requestedCctv.isEnabled()).willReturn(false);
        given(repository.findByCode("CCTV_001")).willReturn(Optional.of(requestedCctv));

        assertThatThrownBy(() -> service.validateCctv(
                new DevicePrincipal(cctvId, "CCTV_001"),
                "CCTV_001"
        )).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", DeviceErrorCode.CCTV_DISABLED);
    }

    private Cctv cctv(UUID id) {
        Cctv cctv = mock(Cctv.class);
        given(cctv.getId()).willReturn(id);
        given(cctv.isEnabled()).willReturn(true);
        return cctv;
    }
}
