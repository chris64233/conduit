package com.conduit.inspection.dto;

import com.conduit.inspection.InspectionTask;
import com.conduit.inspection.InspectionTaskStatus;

import java.time.OffsetDateTime;

public record InspectionTaskResponse(
        Long id,
        Long pipeSegmentId,
        String title,
        String inspector,
        OffsetDateTime scheduledAt,
        InspectionTaskStatus status,
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
