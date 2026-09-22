package com.conduit.emergency;

import com.conduit.emergency.dto.BurstEventResponse;
import com.conduit.emergency.dto.ResourceTransferResponse;
import com.conduit.emergency.exception.BurstEventNotFoundException;
import com.conduit.emergency.exception.DuplicateRequestNoConflictException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ResourceTransferReplayer {

    private final ResourceTransferRequestRepository requestRepository;
    private final ResourceTransferRecordRepository recordRepository;
    private final BurstEventRepository burstEventRepository;

    public ResourceTransferReplayer(ResourceTransferRequestRepository requestRepository,
                                    ResourceTransferRecordRepository recordRepository,
                                    BurstEventRepository burstEventRepository) {
        this.requestRepository = requestRepository;
        this.recordRepository = recordRepository;
        this.burstEventRepository = burstEventRepository;
    }

    @Transactional(readOnly = true)
    public ResourceTransferResponse replayByRequestNo(String requestNo, String currentFingerprint) {
        ResourceTransferRequest stored = requestRepository.findById(requestNo)
                .orElseThrow(() -> new IllegalStateException("幂等转移记录缺失: " + requestNo));
        return replay(stored, currentFingerprint);
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public ResourceTransferResponse replay(ResourceTransferRequest stored, String currentFingerprint) {
        if (!stored.getContentFingerprint().equals(currentFingerprint)) {
            throw new DuplicateRequestNoConflictException(stored.getRequestNo());
        }
        ResourceTransferRecord record = recordRepository.findById(stored.getRecordId())
                .orElseThrow(() -> new IllegalStateException("幂等转移审计缺失: " + stored.getRequestNo()));
        BurstEvent source = burstEventRepository.findById(stored.getSourceEventId())
                .orElseThrow(() -> new BurstEventNotFoundException(stored.getSourceEventId()));
        BurstEvent target = burstEventRepository.findById(stored.getTargetEventId())
                .orElseThrow(() -> new BurstEventNotFoundException(stored.getTargetEventId()));
        List<ResourceTransferRecord> sourceRecords = recordRepository.findRelatedByEventId(source.getId());
        List<ResourceTransferRecord> targetRecords = recordRepository.findRelatedByEventId(target.getId());
        return new ResourceTransferResponse(
                record.getId(),
                record.getRequestNo(),
                record.getSourceEvent().getId(),
                record.getTargetEvent().getId(),
                record.getResourceCodes(),
                record.getOperator(),
                record.getReason(),
                record.getOperatedAt(),
                BurstEventResponse.from(source, sourceRecords),
                BurstEventResponse.from(target, targetRecords)
        );
    }
}
