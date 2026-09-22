package com.conduit.emergency;

import com.conduit.emergency.dto.BurstEventCreateRequest;
import com.conduit.emergency.dto.BurstEventResponse;
import com.conduit.emergency.dto.BurstEventTransitionRequest;
import com.conduit.emergency.dto.ReassignmentRequest;
import com.conduit.emergency.exception.BurstEventNotDispatchedException;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class BurstEventService {

    private final BurstEventRepository burstEventRepository;
    private final PipeSegmentRepository pipeSegmentRepository;
    private final EmergencyResourceRepository resourceRepository;

    public BurstEventService(BurstEventRepository burstEventRepository,
                             PipeSegmentRepository pipeSegmentRepository,
                             EmergencyResourceRepository resourceRepository) {
        this.burstEventRepository = burstEventRepository;
        this.pipeSegmentRepository = pipeSegmentRepository;
        this.resourceRepository = resourceRepository;
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
        return BurstEventResponse.from(burstEventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public BurstEventResponse getById(Long id) {
        return burstEventRepository.findById(id)
                .map(BurstEventResponse::from)
                .orElseThrow(() -> new BurstEventNotFoundException(id));
    }

    @Transactional
    public BurstEventResponse transition(Long id, BurstEventTransitionRequest request) {
        BurstEvent event = burstEventRepository.findById(id)
                .orElseThrow(() -> new BurstEventNotFoundException(id));
        BurstEventStatus current = event.getStatus();
        BurstEventStatus target = request.targetStatus();

        if (current == BurstEventStatus.REPORTED && target == BurstEventStatus.DISPATCHED) {
            event.dispatch(loadAvailableResources(request.resourceIds()));
        } else if (current == BurstEventStatus.DISPATCHED && target == BurstEventStatus.RESOLVED) {
            String resolution = request.resolution();
            if (resolution == null || resolution.isBlank()) {
                throw new InvalidRequestException("完成事件时处理结果不能为空");
            }
            event.resolve(resolution.trim());
        } else {
            throw new InvalidBurstEventStateTransitionException(current, target);
        }
        return BurstEventResponse.from(burstEventRepository.save(event));
    }

    @Transactional
    public BurstEventResponse reassign(Long id, ReassignmentRequest request) {
        BurstEvent event = burstEventRepository.findById(id)
                .orElseThrow(() -> new BurstEventNotFoundException(id));
        if (event.getStatus() != BurstEventStatus.DISPATCHED) {
            throw new BurstEventNotDispatchedException(id);
        }
        List<Long> resourceIds = request.resourceIds();
        if (resourceIds == null || resourceIds.isEmpty()) {
            throw new InvalidRequestException("改派时至少选择一个应急资源");
        }
        Set<Long> distinctIds = new LinkedHashSet<>(resourceIds);
        Set<Long> currentIds = new LinkedHashSet<>();
        for (EmergencyResource resource : event.getResources()) {
            currentIds.add(resource.getId());
        }
        List<EmergencyResource> targetResources = new ArrayList<>();
        for (Long resourceId : distinctIds) {
            EmergencyResource resource = resourceRepository.findById(resourceId)
                    .orElseThrow(() -> new EmergencyResourceNotFoundException(resourceId));
            if (!currentIds.contains(resourceId) && resource.getStatus() != ResourceStatus.AVAILABLE) {
                throw new ResourceNotAvailableException(resourceId);
            }
            targetResources.add(resource);
        }
        event.reassign(targetResources, request.operator().trim(), request.reason().trim());
        return BurstEventResponse.from(burstEventRepository.save(event));
    }

    private List<EmergencyResource> loadAvailableResources(List<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            throw new InvalidRequestException("派发时至少选择一个应急资源");
        }
        Set<Long> distinctIds = new LinkedHashSet<>(resourceIds);
        List<EmergencyResource> resources = new ArrayList<>();
        for (Long resourceId : distinctIds) {
            EmergencyResource resource = resourceRepository.findById(resourceId)
                    .orElseThrow(() -> new EmergencyResourceNotFoundException(resourceId));
            if (resource.getStatus() != ResourceStatus.AVAILABLE) {
                throw new ResourceNotAvailableException(resourceId);
            }
            resources.add(resource);
        }
        return resources;
    }
}
