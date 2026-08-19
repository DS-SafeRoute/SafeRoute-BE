package com.saferoute.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeviceTokenServiceTest {

    private final DeviceTokenService service = new DeviceTokenService();

    @Test
    void issueReturnsRawTokenAndOnlyItsHash() {
        DeviceTokenService.IssuedDeviceToken issued = service.issue();

        assertThat(issued.rawToken()).isNotBlank();
        assertThat(issued.hash()).hasSize(64);
        assertThat(issued.hash()).isEqualTo(service.hash(issued.rawToken()));
        assertThat(issued.hash()).isNotEqualTo(issued.rawToken());
    }

    @Test
    void issueGeneratesDifferentTokens() {
        assertThat(service.issue().rawToken()).isNotEqualTo(service.issue().rawToken());
    }
}
