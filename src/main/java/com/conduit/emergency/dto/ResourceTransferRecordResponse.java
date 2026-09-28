package com.conduit.emergency.dto;

import com.conduit.emergency.ResourceTransferRecord;

import java.time.Instant;
import java.util.List;

public record ResourceTransferRecordResponse(
        Long id,
        String requestNo,
        Long sourceEventId,
        Long targetEventId,
        List<String> resourceCodes,
        String operator,
        String reason,
        Instant operatedAt,
        boolean reversed,
        List<String> reversedResourceCodes,
        List<String> remainingReversibleResourceCodes
) {

    public static ResourceTransferRecordResponse from(ResourceTransferRecord record) {
        return from(record, List.of());
    }

    public static ResourceTransferRecordResponse from(ResourceTransferRecord record,
                                                      List<String> reversedResourceCodes) {
        List<String> transferCodes = record.getResourceCodes();
        List<String> remaining = transferCodes.stream()
                .filter(code -> !reversedResourceCodes.contains(code))
                .toList();
        return new ResourceTransferRecordResponse(
                record.getId(),
                record.getRequestNo(),
                record.getSourceEvent().getId(),
                record.getTargetEvent().getId(),
                transferCodes,
                record.getOperator(),
                record.getReason(),
                record.getOperatedAt(),
                remaining.isEmpty(),
                reversedResourceCodes,
                remaining
        );
    }
}
