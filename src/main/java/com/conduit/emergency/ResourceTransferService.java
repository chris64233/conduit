package com.conduit.emergency;

import com.conduit.emergency.dto.ResourceTransferRequestPayload;
import com.conduit.emergency.dto.ResourceTransferResponse;
import com.conduit.emergency.exception.SameBurstEventTransferException;
import com.conduit.pipesegment.exception.InvalidRequestException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class ResourceTransferService {

    private final ResourceTransferTxService txService;
    private final ResourceTransferRequestRepository requestRepository;
    private final ResourceTransferReplayer replayer;

    public ResourceTransferService(ResourceTransferTxService txService,
                                   ResourceTransferRequestRepository requestRepository,
                                   ResourceTransferReplayer replayer) {
        this.txService = txService;
        this.requestRepository = requestRepository;
        this.replayer = replayer;
    }

    public ResourceTransferResponse transfer(ResourceTransferRequestPayload request) {
        Long sourceId = request.sourceEventId();
        Long targetId = request.targetEventId();
        if (sourceId.equals(targetId)) {
            throw new SameBurstEventTransferException(sourceId);
        }

        String requestNo = request.requestNo().trim();
        String fingerprint = fingerprint(request);

        String existingRequestNo = requestRepository.findById(requestNo)
                .map(ResourceTransferRequest::getRequestNo)
                .orElse(null);
        if (existingRequestNo != null) {
            return replayer.replayByRequestNo(existingRequestNo, fingerprint);
        }

        try {
            return txService.execute(request, fingerprint);
        } catch (DataIntegrityViolationException ex) {
            if (requestRepository.existsById(requestNo)) {
                return replayer.replayByRequestNo(requestNo, fingerprint);
            }
            throw ex;
        }
    }

    private static String fingerprint(ResourceTransferRequestPayload request) {
        LinkedHashSet<String> distinctCodes = new LinkedHashSet<>();
        for (String code : request.resourceCodes()) {
            if (code == null || code.isBlank()) {
                throw new InvalidRequestException("resourceCodes 中的编号不能为空");
            }
            distinctCodes.add(code.trim().toUpperCase(Locale.ROOT));
        }
        if (distinctCodes.isEmpty()) {
            throw new InvalidRequestException("resourceCodes 不能为空");
        }
        List<String> parts = new ArrayList<>();
        parts.add(String.valueOf(request.sourceEventId()));
        parts.add(String.valueOf(request.targetEventId()));
        parts.add(String.join("|", distinctCodes));
        parts.add(request.operator().trim());
        parts.add(request.reason().trim());
        String raw = String.join("#", parts);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
