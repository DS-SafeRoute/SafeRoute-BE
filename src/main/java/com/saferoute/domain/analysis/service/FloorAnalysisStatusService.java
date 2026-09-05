package com.saferoute.domain.analysis.service;

import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.entity.SegmentationStatus;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.api.error.AnalysisErrorCode;
import com.saferoute.global.api.error.FloorErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FloorAnalysisStatusService {

    private final FloorRepository floorRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsProcessing(UUID floorId, String schoolName) {
        Floor floor = floorRepository.findByIdAndBuilding_SchoolName(floorId, schoolName)
                .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));

        if (floor.getMapImageKey() == null) {
            throw new ApiException(FloorErrorCode.FLOOR_NOT_FOUND);
        }
        if (floor.getSegmentationStatus() == SegmentationStatus.PROCESSING) {
            throw new ApiException(AnalysisErrorCode.ANALYSIS_ALREADY_IN_PROGRESS);
        }

        floor.updateSegmentationStatus(SegmentationStatus.PROCESSING);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(UUID floorId) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));
        floor.updateSegmentationStatus(SegmentationStatus.FAILED);
    }
}
