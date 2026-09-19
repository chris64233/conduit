package com.conduit.pipesegment;

import com.conduit.pipesegment.dto.PipeSegmentCreateRequest;
import com.conduit.pipesegment.dto.PipeSegmentResponse;
import com.conduit.pipesegment.exception.DuplicatePipeSegmentCodeException;
import com.conduit.pipesegment.exception.InvalidRequestException;
import com.conduit.pipesegment.exception.PipeSegmentNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class PipeSegmentService {

    private static final Sort SORT_BY_CODE = Sort.by(Sort.Direction.ASC, "codeKey").and(Sort.by("id"));

    private final PipeSegmentRepository repository;

    public PipeSegmentService(PipeSegmentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PipeSegmentResponse create(PipeSegmentCreateRequest request) {
        String code = request.code().trim();
        if (request.startLongitude().equals(request.endLongitude())
                && request.startLatitude().equals(request.endLatitude())) {
            throw new InvalidRequestException("起点和终点不能完全相同");
        }

        PipeSegment segment = new PipeSegment(
                code,
                code.toUpperCase(Locale.ROOT),
                request.name(),
                request.utilityType(),
                request.material(),
                request.diameterMm(),
                request.startLongitude(),
                request.startLatitude(),
                request.endLongitude(),
                request.endLatitude(),
                request.status()
        );

        if (repository.existsByCodeKey(segment.getCodeKey())) {
            throw new DuplicatePipeSegmentCodeException(code);
        }
        try {
            return PipeSegmentResponse.from(repository.saveAndFlush(segment));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicatePipeSegmentCodeException(code);
        }
    }

    @Transactional(readOnly = true)
    public PipeSegmentResponse getById(Long id) {
        return repository.findById(id)
                .map(PipeSegmentResponse::from)
                .orElseThrow(() -> new PipeSegmentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<PipeSegmentResponse> list(UtilityType utilityType, PipeSegmentStatus status,
                                          BoundingBox boundingBox, int page, int size) {
        Specification<PipeSegment> spec = Specification
                .where(PipeSegmentSpecifications.hasUtilityType(utilityType))
                .and(PipeSegmentSpecifications.hasStatus(status));

        if (boundingBox == null) {
            Pageable pageable = PageRequest.of(page, size, SORT_BY_CODE);
            return repository.findAll(spec, pageable).map(PipeSegmentResponse::from);
        }

        List<PipeSegmentResponse> matched = repository.findAll(spec, SORT_BY_CODE).stream()
                .filter(boundingBox::intersects)
                .map(PipeSegmentResponse::from)
                .toList();
        Pageable pageable = PageRequest.of(page, size);
        int from = Math.min((int) pageable.getOffset(), matched.size());
        int to = Math.min(from + pageable.getPageSize(), matched.size());
        return new PageImpl<>(matched.subList(from, to), pageable, matched.size());
    }
}
