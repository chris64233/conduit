package com.conduit.pipesegment;

import com.conduit.common.PageResponse;
import com.conduit.pipesegment.dto.CreatePipeSegmentRequest;
import com.conduit.pipesegment.dto.PipeSegmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pipe-segments")
public class PipeSegmentController {

    private final PipeSegmentService service;

    public PipeSegmentController(PipeSegmentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PipeSegmentResponse> create(@Valid @RequestBody CreatePipeSegmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{id}")
    public PipeSegmentResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping
    public PageResponse<PipeSegmentResponse> list(
            @RequestParam(required = false) UtilityType utilityType,
            @RequestParam(required = false) PipeSegmentStatus status,
            @RequestParam(required = false) Double minLongitude,
            @RequestParam(required = false) Double minLatitude,
            @RequestParam(required = false) Double maxLongitude,
            @RequestParam(required = false) Double maxLatitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(utilityType, status,
                minLongitude, minLatitude, maxLongitude, maxLatitude, page, size);
    }
}
