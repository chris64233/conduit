package com.conduit.emergency.dto;

import com.conduit.emergency.BurstEvent;
import com.conduit.emergency.BurstEventLevel;
import com.conduit.emergency.BurstEventStatus;
import com.conduit.emergency.ResourceTransferRecord;

import java.util.List;

public record BurstEventResponse(
        Long id,
        Long pipeSegmentId,
        String description,
        BurstEventLevel level,
        String reporter,
        BurstEventStatus status,
        String resolution,
        List<EmergencyResourceResponse> resources,
        List<ReassignmentRecordResponse> reassignments,
        List<ResourceTransferRecordResponse> transfers
) {

    public static BurstEventResponse from(BurstEvent event, List<ResourceTransferRecord> transferRecords) {
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
                        .toList(),
                event.getReassignments().stream()
                        .map(ReassignmentRecordResponse::from)
                        .toList(),
                transferRecords.stream()
                        .map(ResourceTransferRecordResponse::from)
                        .toList()
        );
    }
}
