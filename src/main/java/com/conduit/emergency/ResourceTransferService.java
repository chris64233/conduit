package com.conduit.emergency;

import com.conduit.emergency.dto.ResourceTransferRecordResponse;
import com.conduit.emergency.dto.ResourceTransferRequest;
import com.conduit.emergency.dto.ResourceTransferReversalRequest;
import com.conduit.emergency.dto.ResourceTransferReversalResponse;
import com.conduit.emergency.exception.BurstEventNotFoundException;
import com.conduit.emergency.exception.DuplicateTransferRequestNoException;
import com.conduit.emergency.exception.EmergencyResourceCodeNotFoundException;
import com.conduit.emergency.exception.ResourceTransferConflictException;
import com.conduit.emergency.exception.ResourceTransferNotFoundException;
import com.conduit.pipesegment.exception.InvalidRequestException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ResourceTransferService {

    private final BurstEventRepository burstEventRepository;
    private final EmergencyResourceRepository resourceRepository;
    private final ResourceTransferRecordRepository transferRecordRepository;
    private final ResourceTransferReversalRecordRepository reversalRecordRepository;
    private final TransactionTemplate transactionTemplate;

    public ResourceTransferService(BurstEventRepository burstEventRepository,
                                   EmergencyResourceRepository resourceRepository,
                                   ResourceTransferRecordRepository transferRecordRepository,
                                   ResourceTransferReversalRecordRepository reversalRecordRepository,
                                   TransactionTemplate transactionTemplate) {
        this.burstEventRepository = burstEventRepository;
        this.resourceRepository = resourceRepository;
        this.transferRecordRepository = transferRecordRepository;
        this.reversalRecordRepository = reversalRecordRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public ResourceTransferRecordResponse transfer(ResourceTransferRequest request) {
        Long sourceEventId = request.sourceEventId();
        Long targetEventId = request.targetEventId();
        if (sourceEventId.equals(targetEventId)) {
            throw new InvalidRequestException("源事件和目标事件必须不同");
        }
        List<String> codeKeys = normalizeCodes(request.resourceCodes());
        String requestNo = request.requestNo().trim();
        String operator = request.operator().trim();
        String reason = request.reason().trim();

        Optional<ResourceTransferRecord> replay = transferRecordRepository.findDetailByRequestNo(requestNo);
        if (replay.isPresent()) {
            return replayOrConflict(replay.get(), sourceEventId, targetEventId, codeKeys, operator, reason);
        }

        try {
            return transactionTemplate.execute(status ->
                    doTransfer(requestNo, sourceEventId, targetEventId, codeKeys, operator, reason));
        } catch (DataIntegrityViolationException ex) {
            ResourceTransferRecord raced = transferRecordRepository.findDetailByRequestNo(requestNo)
                    .orElseThrow(() -> ex);
            return replayOrConflict(raced, sourceEventId, targetEventId, codeKeys, operator, reason);
        }
    }

    private ResourceTransferRecordResponse doTransfer(String requestNo, Long sourceEventId, Long targetEventId,
                                                      List<String> codeKeys, String operator, String reason) {
        Map<Long, BurstEvent> lockedEvents = lockEventsById(sourceEventId, targetEventId);
        BurstEvent sourceEvent = lockedEvents.get(sourceEventId);
        if (sourceEvent == null) {
            throw new BurstEventNotFoundException(sourceEventId);
        }
        BurstEvent targetEvent = lockedEvents.get(targetEventId);
        if (targetEvent == null) {
            throw new BurstEventNotFoundException(targetEventId);
        }
        if (sourceEvent.getStatus() != BurstEventStatus.DISPATCHED) {
            throw new ResourceTransferConflictException("源事件不在已派发状态，不能转移资源: " + sourceEventId);
        }
        if (targetEvent.getStatus() != BurstEventStatus.DISPATCHED) {
            throw new ResourceTransferConflictException("目标事件不在已派发状态，不能接收资源: " + targetEventId);
        }

        Optional<ResourceTransferRecord> concurrentReplay =
                transferRecordRepository.findDetailByRequestNo(requestNo);
        if (concurrentReplay.isPresent()) {
            return replayOrConflict(concurrentReplay.get(), sourceEventId, targetEventId,
                    codeKeys, operator, reason);
        }

        Map<String, EmergencyResource> lockedResources = new HashMap<>();
        for (EmergencyResource resource : resourceRepository.findByCodeKeyInForUpdate(codeKeys)) {
            lockedResources.put(resource.getCodeKey(), resource);
        }
        List<EmergencyResource> transferredResources = new ArrayList<>();
        for (String codeKey : codeKeys) {
            EmergencyResource resource = lockedResources.get(codeKey);
            if (resource == null) {
                throw new EmergencyResourceCodeNotFoundException(codeKey);
            }
            if (!belongsToEvent(resource, sourceEvent)) {
                throw new ResourceTransferConflictException(
                        "资源当前不属于源事件，不能转移: " + resource.getCode());
            }
            if (resource.getStatus() != ResourceStatus.BUSY) {
                throw new ResourceTransferConflictException(
                        "资源状态不是 BUSY，不能转移: " + resource.getCode());
            }
            if (belongsToEvent(resource, targetEvent)) {
                throw new ResourceTransferConflictException(
                        "目标事件已包含该资源，不能重复转入: " + resource.getCode());
            }
            transferredResources.add(resource);
        }

        if (sourceEvent.getResources().size() - transferredResources.size() < 1) {
            throw new ResourceTransferConflictException(
                    "转移完成后源事件至少需要保留一个资源: " + sourceEventId);
        }

        sourceEvent.transferOut(transferredResources);
        burstEventRepository.saveAndFlush(sourceEvent);
        targetEvent.transferIn(transferredResources);
        burstEventRepository.save(targetEvent);
        ResourceTransferRecord record = new ResourceTransferRecord(
                requestNo, sourceEvent, targetEvent, codeKeys, operator, reason);
        transferRecordRepository.saveAndFlush(record);
        return ResourceTransferRecordResponse.from(record);
    }

    private ResourceTransferRecordResponse replayOrConflict(ResourceTransferRecord record, Long sourceEventId,
                                                            Long targetEventId, List<String> codeKeys,
                                                            String operator, String reason) {
        boolean sameContent = record.getSourceEvent().getId().equals(sourceEventId)
                && record.getTargetEvent().getId().equals(targetEventId)
                && record.getResourceCodes().equals(codeKeys)
                && record.getOperator().equals(operator)
                && record.getReason().equals(reason);
        if (!sameContent) {
            throw new DuplicateTransferRequestNoException(record.getRequestNo());
        }
        return ResourceTransferRecordResponse.from(record);
    }

    public ResourceTransferReversalResponse reverse(Long transferId, ResourceTransferReversalRequest request) {
        String requestNo = request.requestNo().trim();
        List<String> codeKeys = normalizeCodes(request.resourceCodes());
        String operator = request.operator().trim();
        String reason = request.reason().trim();

        Optional<ResourceTransferReversalRecord> replay = reversalRecordRepository.findDetailByRequestNo(requestNo);
        if (replay.isPresent()) {
            return replayReversalOrConflict(replay.get(), transferId, codeKeys, operator, reason);
        }

        try {
            return transactionTemplate.execute(status ->
                    doReverse(transferId, requestNo, codeKeys, operator, reason));
        } catch (DataIntegrityViolationException ex) {
            ResourceTransferReversalRecord raced = reversalRecordRepository.findDetailByRequestNo(requestNo)
                    .orElseThrow(() -> ex);
            return replayReversalOrConflict(raced, transferId, codeKeys, operator, reason);
        }
    }

    private ResourceTransferReversalResponse doReverse(Long transferId, String requestNo, List<String> codeKeys,
                                                       String operator, String reason) {
        ResourceTransferRecord transfer = transferRecordRepository.findById(transferId)
                .orElseThrow(() -> new ResourceTransferNotFoundException(transferId));
        Long sourceEventId = transfer.getSourceEvent().getId();
        Long targetEventId = transfer.getTargetEvent().getId();

        Map<Long, BurstEvent> lockedEvents = lockEventsById(sourceEventId, targetEventId);
        BurstEvent sourceEvent = lockedEvents.get(sourceEventId);
        BurstEvent targetEvent = lockedEvents.get(targetEventId);
        if (sourceEvent.getStatus() != BurstEventStatus.DISPATCHED) {
            throw new ResourceTransferConflictException("源事件不在已派发状态，不能撤销转移: " + sourceEventId);
        }
        if (targetEvent.getStatus() != BurstEventStatus.DISPATCHED) {
            throw new ResourceTransferConflictException("目标事件不在已派发状态，不能撤销转移: " + targetEventId);
        }

        Optional<ResourceTransferReversalRecord> concurrentReplay =
                reversalRecordRepository.findDetailByRequestNo(requestNo);
        if (concurrentReplay.isPresent()) {
            return replayReversalOrConflict(concurrentReplay.get(), transferId, codeKeys, operator, reason);
        }

        List<String> transferableCodes = transfer.getResourceCodes();
        for (String codeKey : codeKeys) {
            if (!transferableCodes.contains(codeKey)) {
                throw new ResourceTransferConflictException(
                        "资源不属于该转移记录，不能撤销: " + codeKey);
            }
        }
        Set<String> alreadyReversedCodes = new HashSet<>();
        for (ResourceTransferReversalRecord existing
                : reversalRecordRepository.findByTransferIdOrderByOperatedAtAsc(transferId)) {
            alreadyReversedCodes.addAll(existing.getResourceCodes());
        }
        for (String codeKey : codeKeys) {
            if (alreadyReversedCodes.contains(codeKey)) {
                throw new ResourceTransferConflictException(
                        "资源已撤销回迁，不能重复撤销: " + codeKey);
            }
        }

        Map<String, EmergencyResource> lockedResources = new HashMap<>();
        for (EmergencyResource resource : resourceRepository.findByCodeKeyInForUpdate(codeKeys)) {
            lockedResources.put(resource.getCodeKey(), resource);
        }
        List<EmergencyResource> returnedResources = new ArrayList<>();
        for (String codeKey : codeKeys) {
            EmergencyResource resource = lockedResources.get(codeKey);
            if (resource == null) {
                throw new EmergencyResourceCodeNotFoundException(codeKey);
            }
            if (!belongsToEvent(resource, targetEvent)) {
                throw new ResourceTransferConflictException(
                        "资源当前不属于目标事件，不能撤销转移: " + resource.getCode());
            }
            if (resource.getStatus() != ResourceStatus.BUSY) {
                throw new ResourceTransferConflictException(
                        "资源状态不是 BUSY，不能撤销转移: " + resource.getCode());
            }
            returnedResources.add(resource);
        }

        if (targetEvent.getResources().size() - returnedResources.size() < 1) {
            throw new ResourceTransferConflictException(
                    "撤销完成后目标事件至少需要保留一个资源: " + targetEventId);
        }

        targetEvent.transferOut(returnedResources);
        burstEventRepository.saveAndFlush(targetEvent);
        sourceEvent.transferIn(returnedResources);
        burstEventRepository.save(sourceEvent);
        ResourceTransferReversalRecord record = new ResourceTransferReversalRecord(
                requestNo, transfer, codeKeys, operator, reason);
        reversalRecordRepository.saveAndFlush(record);
        return ResourceTransferReversalResponse.from(record);
    }

    private ResourceTransferReversalResponse replayReversalOrConflict(ResourceTransferReversalRecord record,
                                                                      Long transferId, List<String> codeKeys,
                                                                      String operator, String reason) {
        boolean sameContent = record.getTransfer().getId().equals(transferId)
                && record.getResourceCodes().equals(codeKeys)
                && record.getOperator().equals(operator)
                && record.getReason().equals(reason);
        if (!sameContent) {
            throw new DuplicateTransferRequestNoException(record.getRequestNo());
        }
        return ResourceTransferReversalResponse.from(record);
    }

    private Map<Long, BurstEvent> lockEventsById(Long firstEventId, Long secondEventId) {
        List<Long> eventIds = firstEventId < secondEventId
                ? List.of(firstEventId, secondEventId)
                : List.of(secondEventId, firstEventId);
        Map<Long, BurstEvent> lockedEvents = new HashMap<>();
        for (BurstEvent event : burstEventRepository.findAllByIdForUpdate(eventIds)) {
            lockedEvents.put(event.getId(), event);
        }
        return lockedEvents;
    }

    private boolean belongsToEvent(EmergencyResource resource, BurstEvent event) {
        for (EmergencyResource eventResource : event.getResources()) {
            if (eventResource.getId().equals(resource.getId())) {
                return true;
            }
        }
        return false;
    }

    private List<String> normalizeCodes(List<String> resourceCodes) {
        if (resourceCodes == null || resourceCodes.isEmpty()) {
            throw new InvalidRequestException("转移资源列表不能为空");
        }
        LinkedHashSet<String> distinctCodes = new LinkedHashSet<>();
        for (String code : resourceCodes) {
            if (code == null || code.isBlank()) {
                throw new InvalidRequestException("资源编号不能为空");
            }
            distinctCodes.add(code.trim().toUpperCase(Locale.ROOT));
        }
        return distinctCodes.stream().sorted().toList();
    }
}
