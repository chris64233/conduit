package com.conduit.pipesegment;

import com.conduit.common.BadRequestException;
import com.conduit.common.DuplicateResourceException;
import com.conduit.common.PageResponse;
import com.conduit.common.ResourceNotFoundException;
import com.conduit.pipesegment.dto.CreatePipeSegmentRequest;
import com.conduit.pipesegment.dto.PipeSegmentResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class PipeSegmentService {

    private final PipeSegmentRepository repository;

    public PipeSegmentService(PipeSegmentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PipeSegmentResponse create(CreatePipeSegmentRequest request) {
        String code = request.code().trim();
        String codeNormalized = normalize(code);
        if (repository.existsByCodeNormalized(codeNormalized)) {
            throw new DuplicateResourceException("管段编码已存在: " + code);
        }
        PipeSegment segment = new PipeSegment(
                code,
                codeNormalized,
                request.name().trim(),
                request.utilityType(),
                request.material().trim(),
                request.diameterMm(),
                request.startLongitude(),
                request.startLatitude(),
                request.endLongitude(),
                request.endLatitude(),
                request.status()
        );
        try {
            return PipeSegmentResponse.from(repository.saveAndFlush(segment));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("管段编码已存在: " + code);
        }
    }

    @Transactional(readOnly = true)
    public PipeSegmentResponse getById(Long id) {
        return repository.findById(id)
                .map(PipeSegmentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("管段不存在: " + id));
    }

    @Transactional(readOnly = true)
    public PageResponse<PipeSegmentResponse> list(UtilityType utilityType, PipeSegmentStatus status,
                                                  Double minLongitude, Double minLatitude,
                                                  Double maxLongitude, Double maxLatitude,
                                                  int page, int size) {
        validateRange(minLongitude, minLatitude, maxLongitude, maxLatitude);
        if (page < 0) {
            throw new BadRequestException("page 不能小于 0");
        }
        if (size < 1 || size > 200) {
            throw new BadRequestException("size 必须在 1 至 200 之间");
        }
        var specification = PipeSegmentSpecifications.withFilters(
                utilityType, status, minLongitude, minLatitude, maxLongitude, maxLatitude);
        var pageable = PageRequest.of(page, size, Sort.by("codeNormalized").ascending());
        return PageResponse.from(repository.findAll(specification, pageable), PipeSegmentResponse::from);
    }

    private static void validateRange(Double minLongitude, Double minLatitude,
                                      Double maxLongitude, Double maxLatitude) {
        long provided = java.util.stream.Stream.of(minLongitude, minLatitude, maxLongitude, maxLatitude)
                .filter(java.util.Objects::nonNull)
                .count();
        if (provided == 0) {
            return;
        }
        if (provided != 4) {
            throw new BadRequestException("minLongitude、minLatitude、maxLongitude、maxLatitude 必须同时提供");
        }
        if (minLongitude >= maxLongitude || minLatitude >= maxLatitude) {
            throw new BadRequestException("范围参数必须满足最小值小于最大值");
        }
    }

    private static String normalize(String code) {
        return code.toUpperCase(Locale.ROOT);
    }
}
