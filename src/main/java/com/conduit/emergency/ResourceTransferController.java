package com.conduit.emergency;

import com.conduit.emergency.dto.ResourceTransferRecordResponse;
import com.conduit.emergency.dto.ResourceTransferRequest;
import com.conduit.emergency.dto.ResourceTransferReversalRequest;
import com.conduit.emergency.dto.ResourceTransferReversalResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resource-transfers")
public class ResourceTransferController {

    private final ResourceTransferService service;

    public ResourceTransferController(ResourceTransferService service) {
        this.service = service;
    }

    @PostMapping
    public ResourceTransferRecordResponse transfer(@Valid @RequestBody ResourceTransferRequest request) {
        return service.transfer(request);
    }

    @PostMapping("/{transferId}/reversal")
    public ResourceTransferReversalResponse reverse(@PathVariable Long transferId,
                                                    @Valid @RequestBody ResourceTransferReversalRequest request) {
        return service.reverse(transferId, request);
    }
}
