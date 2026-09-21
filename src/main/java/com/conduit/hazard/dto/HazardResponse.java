package com.conduit.hazard.dto;

import com.conduit.hazard.Hazard;
import com.conduit.hazard.HazardLevel;
import com.conduit.hazard.HazardStatus;

public record HazardResponse(
        Long id,
        Long inspectionTaskId,
        String description,
        HazardLevel level,
        String reporter,
        HazardStatus status,
        String assignee,
        String resolution
) {

    public static HazardResponse from(Hazard hazard) {
        return new HazardResponse(
                hazard.getId(),
                hazard.getInspectionTask().getId(),
                hazard.getDescription(),
                hazard.getLevel(),
                hazard.getReporter(),
                hazard.getStatus(),
                hazard.getAssignee(),
                hazard.getResolution()
        );
    }
}
