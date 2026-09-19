package com.conduit.pipesegment;

import com.conduit.pipesegment.dto.PipeSegmentCreateRequest;
import com.conduit.pipesegment.dto.PipeSegmentResponse;
import com.conduit.pipesegment.exception.InvalidRequestException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

@RestController
@RequestMapping("/api/pipe-segments")
public class PipeSegmentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PipeSegmentService service;

    public PipeSegmentController(PipeSegmentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PipeSegmentResponse> create(@Valid @RequestBody PipeSegmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{id}")
    public PipeSegmentResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping
    public Page<PipeSegmentResponse> list(
            @RequestParam(required = false) UtilityType utilityType,
            @RequestParam(required = false) PipeSegmentStatus status,
            @RequestParam(required = false) Double minLongitude,
            @RequestParam(required = false) Double minLatitude,
            @RequestParam(required = false) Double maxLongitude,
            @RequestParam(required = false) Double maxLatitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        BoundingBox boundingBox = buildBoundingBox(minLongitude, minLatitude, maxLongitude, maxLatitude);
        if (page < 0) {
            throw new InvalidRequestException("page 不能小于 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size 必须在 1 至 " + MAX_PAGE_SIZE + " 之间");
        }
        return service.list(utilityType, status, boundingBox, page, size);
    }

    private static BoundingBox buildBoundingBox(Double minLongitude, Double minLatitude,
                                                Double maxLongitude, Double maxLatitude) {
        long provided = Stream.of(minLongitude, minLatitude, maxLongitude, maxLatitude)
                .filter(value -> value != null)
                .count();
        if (provided == 0) {
            return null;
        }
        if (provided < 4) {
            throw new InvalidRequestException("minLongitude、minLatitude、maxLongitude、maxLatitude 必须同时提供");
        }
        try {
            return new BoundingBox(minLongitude, minLatitude, maxLongitude, maxLatitude);
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(ex.getMessage());
        }
    }
}
