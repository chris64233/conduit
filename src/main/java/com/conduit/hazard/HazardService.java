package com.conduit.hazard;

import com.conduit.hazard.dto.HazardCreateRequest;
import com.conduit.hazard.dto.HazardResponse;
import com.conduit.hazard.dto.HazardTransitionRequest;
import com.conduit.hazard.exception.HazardNotFoundException;
import com.conduit.hazard.exception.InspectionTaskNotCompletedException;
import com.conduit.hazard.exception.InvalidHazardStateTransitionException;
import com.conduit.inspection.InspectionStatus;
import com.conduit.inspection.InspectionTask;
import com.conduit.inspection.InspectionTaskRepository;
import com.conduit.inspection.exception.InspectionTaskNotFoundException;
import com.conduit.pipesegment.exception.InvalidRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HazardService {

    private static final Sort SORT_BY_ID_DESC = Sort.by(Sort.Direction.DESC, "id");

    private final HazardRepository hazardRepository;
    private final InspectionTaskRepository taskRepository;

    public HazardService(HazardRepository hazardRepository,
                         InspectionTaskRepository taskRepository) {
        this.hazardRepository = hazardRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public HazardResponse create(HazardCreateRequest request) {
        InspectionTask task = taskRepository.findById(request.inspectionTaskId())
                .orElseThrow(() -> new InspectionTaskNotFoundException(request.inspectionTaskId()));
        if (task.getStatus() != InspectionStatus.COMPLETED) {
            throw new InspectionTaskNotCompletedException(task.getId());
        }
        Hazard hazard = new Hazard(
                task,
                request.description().trim(),
                request.level(),
                request.reporter().trim()
        );
        return HazardResponse.from(hazardRepository.save(hazard));
    }

    @Transactional(readOnly = true)
    public HazardResponse getById(Long id) {
        return hazardRepository.findById(id)
                .map(HazardResponse::from)
                .orElseThrow(() -> new HazardNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<HazardResponse> list(HazardLevel level, HazardStatus status, int page, int size) {
        Specification<Hazard> spec = Specification
                .where(HazardSpecifications.hasLevel(level))
                .and(HazardSpecifications.hasStatus(status));
        Pageable pageable = PageRequest.of(page, size, SORT_BY_ID_DESC);
        return hazardRepository.findAll(spec, pageable).map(HazardResponse::from);
    }

    @Transactional
    public HazardResponse transition(Long id, HazardTransitionRequest request) {
        Hazard hazard = hazardRepository.findById(id)
                .orElseThrow(() -> new HazardNotFoundException(id));
        HazardStatus current = hazard.getStatus();
        HazardStatus target = request.targetStatus();

        if (current == HazardStatus.OPEN && target == HazardStatus.RECTIFYING) {
            String assignee = request.assignee();
            if (assignee == null || assignee.isBlank()) {
                throw new InvalidRequestException("开始整改时整改负责人不能为空");
            }
            hazard.startRectification(assignee.trim());
        } else if (current == HazardStatus.RECTIFYING && target == HazardStatus.RESOLVED) {
            String resolution = request.resolution();
            if (resolution == null || resolution.isBlank()) {
                throw new InvalidRequestException("完成整改时整改结果不能为空");
            }
            hazard.resolve(resolution.trim());
        } else {
            throw new InvalidHazardStateTransitionException(current, target);
        }
        return HazardResponse.from(hazardRepository.save(hazard));
    }
}
