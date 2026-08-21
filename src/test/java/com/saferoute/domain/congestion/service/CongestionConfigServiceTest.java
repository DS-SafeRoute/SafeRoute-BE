package com.saferoute.domain.congestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.congestion.entity.CongestionConfig;
import com.saferoute.domain.congestion.repository.CongestionConfigRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CongestionConfigServiceTest {

    @InjectMocks
    private CongestionConfigService congestionConfigService;

    @Mock
    private CongestionConfigRepository congestionConfigRepository;

    @Test
    @DisplayName("설정이 이미 있으면 그대로 반환하고 새로 만들지 않는다")
    void getConfig_existing_returnsWithoutCreating() {
        CongestionConfig existing = CongestionConfig.createDefault();
        given(congestionConfigRepository.findById(CongestionConfig.SINGLETON_ID))
                .willReturn(Optional.of(existing));

        CongestionConfig result = congestionConfigService.getConfig();

        assertThat(result).isSameAs(existing);
    }

    @Test
    @DisplayName("설정이 없으면 기본값으로 최초 생성한다")
    void getConfig_missing_createsDefault() {
        given(congestionConfigRepository.findById(CongestionConfig.SINGLETON_ID))
                .willReturn(Optional.empty());
        given(congestionConfigRepository.save(org.mockito.ArgumentMatchers.any(CongestionConfig.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CongestionConfig result = congestionConfigService.getConfig();

        assertThat(result.getVersion()).isEqualTo(1L);
        assertThat(result.getCautionFrom()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("동시에 최초 생성이 경합하면 유니크 제약 충돌 후 이미 만들어진 행을 다시 읽는다")
    void getConfig_concurrentCreationConflict_reReadsExistingRow() {
        CongestionConfig alreadyCreatedByOtherTransaction = CongestionConfig.createDefault();
        given(congestionConfigRepository.findById(CongestionConfig.SINGLETON_ID))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(alreadyCreatedByOtherTransaction));
        given(congestionConfigRepository.save(org.mockito.ArgumentMatchers.any(CongestionConfig.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        CongestionConfig result = congestionConfigService.getConfig();

        assertThat(result).isSameAs(alreadyCreatedByOtherTransaction);
    }

    @Test
    @DisplayName("감시 영역 변경 시 설정 값은 그대로 두고 버전만 올린다")
    void incrementVersionForGridChange_bumpsVersionOnly() {
        CongestionConfig config = mock(CongestionConfig.class);
        given(congestionConfigRepository.findById(CongestionConfig.SINGLETON_ID))
                .willReturn(Optional.of(config));

        congestionConfigService.incrementVersionForGridChange();

        verify(config).incrementVersion();
    }

    @Test
    @DisplayName("설정 변경 요청을 엔티티의 updateSettings로 위임한다")
    void updateSettings_delegatesToEntity() {
        CongestionConfig config = mock(CongestionConfig.class);
        given(congestionConfigRepository.findById(CongestionConfig.SINGLETON_ID))
                .willReturn(Optional.of(config));

        congestionConfigService.updateSettings(10, null, 2.5, null, null, null, null, null, null);

        verify(config).updateSettings(10, null, 2.5, null, null, null, null, null, null);
    }
}
