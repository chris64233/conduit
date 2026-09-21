package com.conduit.emergency.dto;

import com.conduit.emergency.BurstEvent;
import com.conduit.emergency.BurstEventLevel;
import com.conduit.emergency.BurstEventStatus;

import java.util.List;

public record BurstEventResponse(
        Long id,
        Long pipeSegmentId,
        String description,
        BurstEventLevel level,
        String reporter,
        BurstEventStatus status,
        String resolution,
        List<EmergencyResourceResponse> resources
) {

    public static BurstEventResponse from(BurstEvent event) {
        return new BurstEventResponse(
                event.getId(),
                event.getPipeSegment().getId(),
                event.getDescription(),
                event.getLevel(),
                event.getReporter(),
                event.getStatus(),
                event.getResolution(),
                event.getResources().stream()
                        .map(EmergencyResourceResponse::from)
                        .toList()
        );
    }
}
