package com.conduit.emergency.dto;

import java.time.Instant;
import java.util.List;

public record ResourceTransferResponse(
        Long id,
        String requestNo,
        Long sourceEventId,
        Long targetEventId,
        List<String> resourceCodes,
        String operator,
        String reason,
        Instant operatedAt,
        BurstEventResponse sourceEvent,
        BurstEventResponse targetEvent
) {
}
