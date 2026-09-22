package com.conduit.emergency;

import com.conduit.emergency.dto.ResourceTransferRequestPayload;
import com.conduit.emergency.dto.ResourceTransferResponse;
import com.conduit.emergency.dto.BurstEventResponse;
import com.conduit.emergency.exception.BurstEventNotDispatchedException;
import com.conduit.emergency.exception.BurstEventNotFoundException;
import com.conduit.emergency.exception.EmergencyResourceNotFoundException;
import com.conduit.emergency.exception.ResourceAlreadyOnTargetEventException;
import com.conduit.emergency.exception.ResourceNotBusyException;
import com.conduit.emergency.exception.ResourceNotFromSourceEventException;
import com.conduit.emergency.exception.SourceEventKeepsNoResourceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ResourceTransferTxService {

    private final BurstEventRepository burstEventRepository;
    private final EmergencyResourceRepository resourceRepository;
    private final ResourceTransferRecordRepository recordRepository;
    private final ResourceTransferRequestRepository requestRepository;
    private final ResourceTransferReplayer replayer;

    public ResourceTransferTxService(BurstEventRepository burstEventRepository,
                                     EmergencyResourceRepository resourceRepository,
                                     ResourceTransferRecordRepository recordRepository,
                                     ResourceTransferRequestRepository requestRepository,
                                     ResourceTransferReplayer replayer) {
        this.burstEventRepository = burstEventRepository;
        this.resourceRepository = resourceRepository;
        this.recordRepository = recordRepository;
        this.requestRepository = requestRepository;
        this.replayer = replayer;
    }

    @Transactional
    public ResourceTransferResponse execute(ResourceTransferRequestPayload request, String fingerprint) {
        Long sourceId = request.sourceEventId();
        Long targetId = request.targetEventId();

        ResourceTransferRequest stored = requestRepository.findById(request.requestNo().trim()).orElse(null);
        if (stored != null) {
            return replayer.replay(stored, fingerprint);
        }

        List<Long> eventIds = sourceId.compareTo(targetId) < 0
                ? List.of(sourceId, targetId)
                : List.of(targetId, sourceId);
        List<BurstEvent> lockedEvents = burstEventRepository.findAllByIdInIdOrderForUpdate(eventIds);
        BurstEvent source = findLockedEvent(lockedEvents, sourceId);
        BurstEvent target = findLockedEvent(lockedEvents, targetId);

        stored = requestRepository.findById(request.requestNo().trim()).orElse(null);
        if (stored != null) {
            return replayer.replay(stored, fingerprint);
        }

        if (source.getStatus() != BurstEventStatus.DISPATCHED) {
            throw new BurstEventNotDispatchedException(sourceId);
        }
        if (target.getStatus() != BurstEventStatus.DISPATCHED) {
            throw new BurstEventNotDispatchedException(targetId);
        }

        List<String> distinctCodeKeys = request.resourceCodes().stream()
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        List<EmergencyResource> transferringResources = new ArrayList<>();
        for (String codeKey : distinctCodeKeys) {
            EmergencyResource resource = resourceRepository.findByCodeKey(codeKey)
                    .orElseThrow(() -> new EmergencyResourceNotFoundException(codeKey));
            if (!source.containsResource(resource)) {
                throw new ResourceNotFromSourceEventException(resource.getCode(), sourceId);
            }
            if (resource.getStatus() != ResourceStatus.BUSY) {
                throw new ResourceNotBusyException(resource.getCode());
            }
            if (target.containsResource(resource)) {
                throw new ResourceAlreadyOnTargetEventException(resource.getCode(), targetId);
            }
            transferringResources.add(resource);
        }

        if (source.getResources().size() - transferringResources.size() < 1) {
            throw new SourceEventKeepsNoResourceException(sourceId);
        }

        List<String> canonicalCodes = transferringResources.stream()
                .map(EmergencyResource::getCode)
                .toList();
        String operator = request.operator().trim();
        String reason = request.reason().trim();

        source.transferResourcesOut(transferringResources);
        target.transferResourcesIn(transferringResources);

        ResourceTransferRecord record = new ResourceTransferRecord(
                request.requestNo().trim(), source, target, canonicalCodes, operator, reason);
        record = recordRepository.save(record);
        burstEventRepository.save(source);
        burstEventRepository.save(target);

        requestRepository.save(new ResourceTransferRequest(
                request.requestNo().trim(), fingerprint, record.getId(),
                sourceId, targetId, String.join(",", canonicalCodes)));

        return buildResponse(record, source, target);
    }

    private static BurstEvent findLockedEvent(List<BurstEvent> lockedEvents, Long eventId) {
        return lockedEvents.stream()
                .filter(event -> event.getId().equals(eventId))
                .findFirst()
                .orElseThrow(() -> new BurstEventNotFoundException(eventId));
    }

    private ResourceTransferResponse buildResponse(ResourceTransferRecord record,
                                                   BurstEvent source, BurstEvent target) {
        List<ResourceTransferRecord> sourceRecords = recordRepository.findRelatedByEventId(source.getId());
        List<ResourceTransferRecord> targetRecords = recordRepository.findRelatedByEventId(target.getId());
        return new ResourceTransferResponse(
                record.getId(),
                record.getRequestNo(),
                source.getId(),
                target.getId(),
                record.getResourceCodes(),
                record.getOperator(),
                record.getReason(),
                record.getOperatedAt(),
                BurstEventResponse.from(source, sourceRecords),
                BurstEventResponse.from(target, targetRecords)
        );
    }
}
