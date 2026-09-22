package com.conduit.emergency.dto;

import com.conduit.emergency.BurstEventReassignment;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

public record BurstEventReassignmentResponse(
        Long id,
        String operator,
        String reason,
        OffsetDateTime operatedAt,
        List<Long> previousResourceIds,
        List<Long> newResourceIds
) {

    public static BurstEventReassignmentResponse from(BurstEventReassignment record) {
        return new BurstEventReassignmentResponse(
                record.getId(),
                record.getOperator(),
                record.getReason(),
                record.getOperatedAt(),
                parse(record.getPreviousResourceIds()),
                parse(record.getNewResourceIds())
        );
    }

    private static List<Long> parse(String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ids.split(",")).map(Long::valueOf).toList();
    }
}
