package com.conduit.emergency.dto;

import com.conduit.emergency.ReassignmentRecord;

import java.time.Instant;
import java.util.List;

public record ReassignmentRecordResponse(
        Long id,
        String operator,
        String reason,
        Instant operatedAt,
        List<Long> previousResourceIds,
        List<Long> newResourceIds
) {

    public static ReassignmentRecordResponse from(ReassignmentRecord record) {
        return new ReassignmentRecordResponse(
                record.getId(),
                record.getOperator(),
                record.getReason(),
                record.getOperatedAt(),
                record.getPreviousResourceIds(),
                record.getNewResourceIds()
        );
    }
}
