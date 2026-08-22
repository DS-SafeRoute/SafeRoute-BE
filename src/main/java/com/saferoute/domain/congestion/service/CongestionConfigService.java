package com.saferoute.domain.congestion.service;

import com.saferoute.domain.congestion.entity.CongestionConfig;
import com.saferoute.domain.congestion.repository.CongestionConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CongestionConfigService {

    private final CongestionConfigRepository congestionConfigRepository;

    // 싱글턴 설정을 조회하고, 앱이 아직 한 번도 초기화되지 않았다면 기본값으로 최초 생성한다.
    // 클래스 기본은 readOnly라 쓰기(최초 생성)가 필요한 이 메서드만 별도로 쓰기 트랜잭션을 연다.
    @Transactional
    public CongestionConfig getConfig() {
        return congestionConfigRepository.findById(CongestionConfig.SINGLETON_ID)
                .orElseGet(this::createDefaultConfig);
    }

    // 값 필드 변경. null로 넘긴 필드는 유지되고, 실제로 값이 바뀐 경우에만 configVersion이 증가한다.
    @Transactional
    public CongestionConfig updateSettings(Integer snapshotIntervalSec, Integer targetInferenceFps,
            Double cautionFrom, Double crowdedFrom, Double veryCrowdedFrom,
            Integer requiredConsecutiveFrames, Integer recoveryConsecutiveFrames,
            Integer cooldownSec, Integer stateStaleAfterSec) {
        CongestionConfig config = getConfig();
        config.updateSettings(snapshotIntervalSec, targetInferenceFps, cautionFrom, crowdedFrom, veryCrowdedFrom,
                requiredConsecutiveFrames, recoveryConsecutiveFrames, cooldownSec, stateStaleAfterSec);
        return config;
    }

    // CCTV 감시 영역(GridCell) 매핑이 바뀌었을 때 CctvService/CctvRegistrationService가 호출한다.
    // 이 엔티티 자체의 필드는 그대로지만, Pi가 다시 설정을 조회해야 하므로 버전만 올린다.
    @Transactional
    public void incrementVersionForGridChange() {
        getConfig().incrementVersion();
    }

    // 동시에 여러 요청이 최초 설정을 만들려고 하면 고정 id의 PK 제약에서 하나만 성공하므로,
    // 진 요청은 방금 다른 트랜잭션이 만든 행을 다시 읽어온다 (CctvCodeAllocator와 동일한 컨벤션).
    // getConfig()의 쓰기 트랜잭션 안에서 self-invocation으로 호출되므로 별도 @Transactional을 붙이지 않는다.
    private CongestionConfig createDefaultConfig() {
        try {
            return congestionConfigRepository.save(CongestionConfig.createDefault());
        } catch (DataIntegrityViolationException exception) {
            return congestionConfigRepository.findById(CongestionConfig.SINGLETON_ID)
                    .orElseThrow(() -> exception);
        }
    }
}
