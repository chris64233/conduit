package com.conduit.emergency;

import com.conduit.emergency.dto.BurstEventCreateRequest;
import com.conduit.emergency.dto.BurstEventResponse;
import com.conduit.emergency.dto.BurstEventTransitionRequest;
import com.conduit.emergency.dto.EmergencyResourceResponse;
import com.conduit.emergency.exception.BurstEventNotFoundException;
import com.conduit.emergency.exception.EmergencyResourceNotFoundException;
import com.conduit.emergency.exception.InvalidBurstEventStateTransitionException;
import com.conduit.emergency.exception.PipeSegmentNotActiveException;
import com.conduit.emergency.exception.ResourceNotAvailableException;
import com.conduit.pipesegment.PipeSegment;
import com.conduit.pipesegment.PipeSegmentRepository;
import com.conduit.pipesegment.PipeSegmentStatus;
import com.conduit.pipesegment.exception.InvalidRequestException;
import com.conduit.pipesegment.exception.PipeSegmentNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class BurstEventService {

    private final BurstEventRepository burstEventRepository;
    private final EmergencyResourceRepository resourceRepository;
    private final PipeSegmentRepository pipeSegmentRepository;

    public BurstEventService(BurstEventRepository burstEventRepository,
                             EmergencyResourceRepository resourceRepository,
                             PipeSegmentRepository pipeSegmentRepository) {
        this.burstEventRepository = burstEventRepository;
        this.resourceRepository = resourceRepository;
        this.pipeSegmentRepository = pipeSegmentRepository;
    }

    @Transactional
    public BurstEventResponse create(BurstEventCreateRequest request) {
        PipeSegment segment = pipeSegmentRepository.findById(request.pipeSegmentId())
                .orElseThrow(() -> new PipeSegmentNotFoundException(request.pipeSegmentId()));
        if (segment.getStatus() != PipeSegmentStatus.ACTIVE) {
            throw new PipeSegmentNotActiveException(segment.getId());
        }
        BurstEvent event = new BurstEvent(
                segment,
                request.description().trim(),
                request.level(),
                request.reporter().trim()
        );
        return toResponse(burstEventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public BurstEventResponse getById(Long id) {
        BurstEvent event = burstEventRepository.findById(id)
                .orElseThrow(() -> new BurstEventNotFoundException(id));
        return toResponse(event);
    }

    @Transactional
    public BurstEventResponse transition(Long id, BurstEventTransitionRequest request) {
        BurstEvent event = burstEventRepository.findById(id)
                .orElseThrow(() -> new BurstEventNotFoundException(id));
        BurstStatus current = event.getStatus();
        BurstStatus target = request.targetStatus();

        if (current == BurstStatus.REPORTED && target == BurstStatus.DISPATCHED) {
            dispatch(event, request.resourceIds());
        } else if (current == BurstStatus.DISPATCHED && target == BurstStatus.RESOLVED) {
            resolve(event, request.resolution());
        } else {
            throw new InvalidBurstEventStateTransitionException(current, target);
        }
        return toResponse(burstEventRepository.save(event));
    }

    private void dispatch(BurstEvent event, List<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            throw new InvalidRequestException("派发时至少选择一个应急资源");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(resourceIds);
        List<EmergencyResource> resources = resourceRepository.findAllById(uniqueIds);
        if (resources.size() != uniqueIds.size()) {
            Set<Long> found = new LinkedHashSet<>();
            for (EmergencyResource resource : resources) {
                found.add(resource.getId());
            }
            for (Long resourceId : uniqueIds) {
                if (!found.contains(resourceId)) {
                    throw new EmergencyResourceNotFoundException(resourceId);
                }
            }
        }
        for (EmergencyResource resource : resources) {
            if (resource.getStatus() != ResourceStatus.AVAILABLE) {
                throw new ResourceNotAvailableException(resource.getId());
            }
        }
        event.dispatch();
        for (EmergencyResource resource : resources) {
            resource.assignTo(event);
        }
        resourceRepository.saveAll(resources);
    }

    private void resolve(BurstEvent event, String resolution) {
        if (resolution == null || resolution.isBlank()) {
            throw new InvalidRequestException("完成事件时处理结果不能为空");
        }
        event.resolve(resolution.trim());
        List<EmergencyResource> resources = resourceRepository.findByAssignedEventId(event.getId());
        for (EmergencyResource resource : resources) {
            resource.release();
        }
        resourceRepository.saveAll(resources);
    }

    private BurstEventResponse toResponse(BurstEvent event) {
        List<EmergencyResourceResponse> resources = resourceRepository
                .findByAssignedEventId(event.getId())
                .stream()
                .map(EmergencyResourceResponse::from)
                .toList();
        return BurstEventResponse.from(event, resources);
    }
}
