package com.conduit.pipesegment.dto;

import com.conduit.pipesegment.PipeSegment;
import com.conduit.pipesegment.PipeSegmentStatus;
import com.conduit.pipesegment.UtilityType;

public record PipeSegmentResponse(
        Long id,
        String code,
        String name,
        UtilityType utilityType,
        String material,
        Integer diameterMm,
        Double startLongitude,
        Double startLatitude,
        Double endLongitude,
        Double endLatitude,
        PipeSegmentStatus status
) {

    public static PipeSegmentResponse from(PipeSegment segment) {
        return new PipeSegmentResponse(
                segment.getId(),
                segment.getCode(),
                segment.getName(),
                segment.getUtilityType(),
                segment.getMaterial(),
                segment.getDiameterMm(),
                segment.getStartLongitude(),
                segment.getStartLatitude(),
                segment.getEndLongitude(),
                segment.getEndLatitude(),
                segment.getStatus()
        );
    }
}
