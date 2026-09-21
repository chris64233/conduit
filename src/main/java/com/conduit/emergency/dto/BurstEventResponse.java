package com.conduit.emergency.dto;

import com.conduit.emergency.BurstEvent;
import com.conduit.emergency.BurstLevel;
import com.conduit.emergency.BurstStatus;

import java.util.List;

public record BurstEventResponse(
        Long id,
        Long pipeSegmentId,
        String description,
        BurstLevel level,
        String reporter,
        BurstStatus status,
        String resolution,
        List<EmergencyResourceResponse> resources
) {

    public static BurstEventResponse from(BurstEvent event, List<EmergencyResourceResponse> resources) {
        return new BurstEventResponse(
                event.getId(),
                event.getPipeSegment().getId(),
                event.getDescription(),
                event.getLevel(),
                event.getReporter(),
                event.getStatus(),
                event.getResolution(),
                resources
        );
    }
}
