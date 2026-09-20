package com.conduit.inspection.dto;

import com.conduit.inspection.InspectionStatus;
import com.conduit.inspection.InspectionTask;

import java.time.OffsetDateTime;

public record InspectionTaskResponse(
        Long id,
        Long pipeSegmentId,
        String title,
        String inspector,
        OffsetDateTime scheduledAt,
        InspectionStatus status,
        String result
) {

    public static InspectionTaskResponse from(InspectionTask task) {
        return new InspectionTaskResponse(
                task.getId(),
                task.getPipeSegment().getId(),
                task.getTitle(),
                task.getInspector(),
                task.getScheduledAt(),
                task.getStatus(),
                task.getResult()
        );
    }
}
