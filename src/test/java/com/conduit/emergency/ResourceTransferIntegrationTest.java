package com.conduit.emergency;

import com.conduit.pipesegment.PipeSegmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceTransferIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BurstEventRepository burstEventRepository;

    @Autowired
    private EmergencyResourceRepository resourceRepository;

    @Autowired
    private PipeSegmentRepository pipeSegmentRepository;

    @Autowired
    private ResourceTransferRecordRepository recordRepository;

    @Autowired
    private ResourceTransferRequestRepository requestRepository;

    private Long segmentId;

    @BeforeEach
    void setUp() throws Exception {
        requestRepository.deleteAll();
        recordRepository.deleteAll();
        burstEventRepository.deleteAll();
        resourceRepository.deleteAll();
        pipeSegmentRepository.deleteAll();
        segmentId = createSegment();
    }

    @AfterEach
    void tearDown() {
        requestRepository.deleteAll();
        recordRepository.deleteAll();
        burstEventRepository.deleteAll();
        resourceRepository.deleteAll();
        pipeSegmentRepository.deleteAll();
    }

    private Long createSegment() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("code", "WS-TRANSFER-" + System.nanoTime());
        request.put("name", "城南供水主干管");
        request.put("utilityType", "WATER");
        request.put("material", "球墨铸铁");
        request.put("diameterMm", 400);
        request.put("startLongitude", 116.42);
        request.put("startLatitude", 39.91);
        request.put("endLongitude", 116.43);
        request.put("endLatitude", 39.92);
        request.put("status", "ACTIVE");
        MvcResult result = mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createResource(String code) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("code", code);
        request.put("name", "应急抢修队-" + code);
        request.put("type", "TEAM");
        MvcResult result = mockMvc.perform(post("/api/emergency-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createEvent() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("pipeSegmentId", segmentId);
        request.put("description", "主干管爆裂，路面大量积水");
        request.put("level", "CRITICAL");
        request.put("reporter", "张三");
        MvcResult result = mockMvc.perform(post("/api/burst-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void dispatchEvent(Long eventId, List<Long> resourceIds) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("targetStatus", "DISPATCHED");
        request.put("resourceIds", resourceIds);
        mockMvc.perform(post("/api/burst-events/{id}/transitions", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void resolveEvent(Long eventId) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("targetStatus", "RESOLVED");
        request.put("resolution", "已修复");
        mockMvc.perform(post("/api/burst-events/{id}/transitions", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private MvcResult transfer(Long sourceId, Long targetId, List<String> resourceCodes, String requestNo,
                               String operator, String reason, int expectedStatus) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("sourceEventId", sourceId);
        request.put("targetEventId", targetId);
        request.put("resourceCodes", resourceCodes);
        request.put("requestNo", requestNo);
        request.put("operator", operator);
        request.put("reason", reason);
        return mockMvc.perform(post("/api/burst-events/resource-transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private long recordIdOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void transferMovesResourcesBetweenDispatchedEvents() throws Exception {
        Long first = createResource("RT-001");
        Long second = createResource("RT-002");
        Long third = createResource("RT-003");
        Long sourceId = createEvent();
        Long targetId = createEvent();
        dispatchEvent(sourceId, List.of(first, second));
        dispatchEvent(targetId, List.of(third));

        MvcResult result = transfer(sourceId, targetId, List.of("RT-001"), "REQ-SUCCESS-1",
                "  李四  ", "  现场需要增援  ", 200);
        assertEquals(1, recordRepository.count());

        mockMvc.perform(get("/api/burst-events/{id}", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].id").value(second))
                .andExpect(jsonPath("$.resources[0].status").value("BUSY"));
        mockMvc.perform(get("/api/burst-events/{id}", targetId))
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.resources[0].id").value(first))
                .andExpect(jsonPath("$.resources[1].id").value(third))
                .andExpect(jsonPath("$.resources[1].status").value("BUSY"));
        mockMvc.perform(get("/api/emergency-resources/{id}", first))
                .andExpect(jsonPath("$.status").value("BUSY"));

        assertTrue(recordIdOf(result) > 0);
        mockMvc.perform(get("/api/burst-events/{id}", sourceId))
                .andExpect(jsonPath("$.transfers", hasSize(1)))
                .andExpect(jsonPath("$.transfers[0].requestNo").value("REQ-SUCCESS-1"))
                .andExpect(jsonPath("$.transfers[0].sourceEventId").value(sourceId))
                .andExpect(jsonPath("$.transfers[0].targetEventId").value(targetId))
                .andExpect(jsonPath("$.transfers[0].resourceCodes", hasSize(1)))
                .andExpect(jsonPath("$.transfers[0].resourceCodes[0]").value("RT-001"))
                .andExpect(jsonPath("$.transfers[0].operator").value("李四"))
                .andExpect(jsonPath("$.transfers[0].reason").value("现场需要增援"))
                .andExpect(jsonPath("$.transfers[0].operatedAt").isNotEmpty());
        mockMvc.perform(get("/api/burst-events/{id}", targetId))
                .andExpect(jsonPath("$.transfers", hasSize(1)))
                .andExpect(jsonPath("$.transfers[0].sourceEventId").value(sourceId))
                .andExpect(jsonPath("$.transfers[0].targetEventId").value(targetId));
    }

    @Test
    void duplicateResourceCodesAreTreatedAsOne() throws Exception {
        Long first = createResource("RD-001");
        Long second = createResource("RD-002");
        Long third = createResource("RD-003");
        Long fourth = createResource("RD-004");
        Long sourceId = createEvent();
        Long targetId = createEvent();
        dispatchEvent(sourceId, List.of(first, second, third));
        dispatchEvent(targetId, List.of(fourth));

        transfer(sourceId, targetId, List.of("RD-001", "rd-001", "RD-001"), "REQ-DUP-1",
                "李四", "重复编号去重", 200);

        mockMvc.perform(get("/api/burst-events/{id}", sourceId))
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.transfers", hasSize(1)))
                .andExpect(jsonPath("$.transfers[0].resourceCodes", hasSize(1)))
                .andExpect(jsonPath("$.transfers[0].resourceCodes[0]").value("RD-001"));
        mockMvc.perform(get("/api/burst-events/{id}", targetId))
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.resources[0].id").value(first));
        assertEquals(1, recordRepository.count());
    }

    @Test
    void sourceEventMustKeepAtLeastOneResource() throws Exception {
        Long only = createResource("RK-001");
        Long targetResource = createResource("RK-002");
        Long sourceId = createEvent();
        Long targetId = createEvent();
        dispatchEvent(sourceId, List.of(only));
        dispatchEvent(targetId, List.of(targetResource));

        transfer(sourceId, targetId, List.of("RK-001"), "REQ-KEEP-1",
                "李四", "试图转移全部资源", 409);

        mockMvc.perform(get("/api/burst-events/{id}", sourceId))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].id").value(only))
                .andExpect(jsonPath("$.transfers", hasSize(0)));
        mockMvc.perform(get("/api/burst-events/{id}", targetId))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.transfers", hasSize(0)));
        assertEquals(0, recordRepository.count());
        assertEquals(0, requestRepository.count());
    }

    @Test
    void resourceNotOwnedBySourceReturns409() throws Exception {
        Long sourceOwned = createResource("RO-001");
        Long otherOwned = createResource("RO-002");
        Long targetOwned = createResource("RO-003");
        Long sourceId = createEvent();
        Long otherId = createEvent();
        Long targetId = createEvent();
        dispatchEvent(sourceId, List.of(sourceOwned));
        dispatchEvent(otherId, List.of(otherOwned));
        dispatchEvent(targetId, List.of(targetOwned));

        transfer(sourceId, targetId, List.of("RO-002"), "REQ-OWN-1",
                "李四", "资源属于其他事件", 409);
        transfer(sourceId, targetId, List.of("RO-001", "RO-002"), "REQ-OWN-2",
                "李四", "混合归属也失败", 409);

        mockMvc.perform(get("/api/burst-events/{id}", sourceId))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.transfers", hasSize(0)));
        mockMvc.perform(get("/api/burst-events/{id}", otherId))
                .andExpect(jsonPath("$.transfers", hasSize(0)));
        mockMvc.perform(get("/api/emergency-resources/{id}", otherOwned))
                .andExpect(jsonPath("$.status").value("BUSY"));
        assertEquals(0, recordRepository.count());
    }

    @Test
    void targetAlreadyContainingResourceReturns409() throws Exception {
        Long moving = createResource("RS-001");
        Long kept = createResource("RS-002");
        Long extra = createResource("RS-003");
        Long targetOwned = createResource("RS-004");
        Long sourceId = createEvent();
        Long targetId = createEvent();
        dispatchEvent(sourceId, List.of(moving, kept, extra));
        dispatchEvent(targetId, List.of(targetOwned));

        transfer(sourceId, targetId, List.of("RS-001"), "REQ-ALREADY-0",
                "李四", "首次转移到目标事件", 200);
        transfer(sourceId, targetId, List.of("RS-001"), "REQ-ALREADY-1",
                "李四", "目标已包含该资源", 409);

        mockMvc.perform(get("/api/burst-events/{id}", sourceId))
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.transfers", hasSize(1)));
        mockMvc.perform(get("/api/burst-events/{id}", targetId))
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.transfers", hasSize(1)));
        assertEquals(1, recordRepository.count());
    }

    @Test
    void transfersRequireBothEventsDispatchedAndDistinct() throws Exception {
        Long first = createResource("RZ-001");
        Long second = createResource("RZ-002");
        Long third = createResource("RZ-003");

        Long reportedId = createEvent();

        Long readyId = createEvent();
        dispatchEvent(readyId, List.of(first));

        Long resolvedId = createEvent();
        dispatchEvent(resolvedId, List.of(second));
        resolveEvent(resolvedId);

        Long anotherDispatchedId = createEvent();
        dispatchEvent(anotherDispatchedId, List.of(third));

        transfer(reportedId, anotherDispatchedId, List.of("RZ-003"), "REQ-STATE-1",
                "李四", "源事件未派发", 409);
        transfer(anotherDispatchedId, reportedId, List.of("RZ-003"), "REQ-STATE-2",
                "李四", "目标事件未派发", 409);
        transfer(readyId, resolvedId, List.of("RZ-001"), "REQ-STATE-3",
                "李四", "目标事件已完成", 409);
        transfer(resolvedId, readyId, List.of("RZ-002"), "REQ-STATE-4",
                "李四", "源事件已完成", 409);
        transfer(readyId, readyId, List.of("RZ-001"), "REQ-STATE-5",
                "李四", "两个事件相同", 400);

        mockMvc.perform(get("/api/burst-events/{id}", readyId))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.transfers", hasSize(0)));
        mockMvc.perform(get("/api/emergency-resources/{id}", second))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        assertEquals(0, recordRepository.count());
    }

    @Test
    void failedTransferRollsBackEventsResourcesAndAudit() throws Exception {
        Long first = createResource("RR-001");
        Long second = createResource("RR-002");
        Long third = createResource("RR-003");
        Long targetOwned = createResource("RR-004");
        Long sourceId = createEvent();
        Long targetId = createEvent();
        dispatchEvent(sourceId, List.of(first, second));
        dispatchEvent(targetId, List.of(targetOwned));

        transfer(sourceId, targetId, List.of("RR-001", "RR-002", "RR-MISSING"), "REQ-ROLLBACK-1",
                "李四", "含不存在资源", 404);
        transfer(sourceId, targetId, List.of("RR-001", "RR-002"), "REQ-ROLLBACK-2",
                "李四", "源事件将不保留资源", 409);
        transfer(sourceId, targetId, List.of("RR-001", "RR-003"), "REQ-ROLLBACK-3",
                "李四", "含非源事件资源", 409);

        mockMvc.perform(get("/api/burst-events/{id}", sourceId))
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.resources[0].id").value(first))
                .andExpect(jsonPath("$.resources[1].id").value(second))
                .andExpect(jsonPath("$.transfers", hasSize(0)));
        mockMvc.perform(get("/api/burst-events/{id}", targetId))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.transfers", hasSize(0)));
        mockMvc.perform(get("/api/emergency-resources/{id}", first))
                .andExpect(jsonPath("$.status").value("BUSY"));
        mockMvc.perform(get("/api/emergency-resources/{id}", second))
                .andExpect(jsonPath("$.status").value("BUSY"));
        mockMvc.perform(get("/api/emergency-resources/{id}", third))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        assertEquals(0, recordRepository.count());
        assertEquals(0, requestRepository.count());
    }

    @Test
    void missingEventOrResourceReturns404() throws Exception {
        Long first = createResource("RN-001");
        Long second = createResource("RN-002");
        Long sourceId = createEvent();
        Long targetId = createEvent();
        dispatchEvent(sourceId, List.of(first));
        dispatchEvent(targetId, List.of(second));

        transfer(999L, targetId, List.of("RN-001"), "REQ-NF-1", "李四", "源事件不存在", 404);
        transfer(sourceId, 999L, List.of("RN-001"), "REQ-NF-2", "李四", "目标事件不存在", 404);
        transfer(sourceId, targetId, List.of("RN-MISSING"), "REQ-NF-3", "李四", "资源不存在", 404);

        assertEquals(0, recordRepository.count());
    }

    @Test
    void invalidTransferPayloadReturns400() throws Exception {
        Long first = createResource("RB-001");
        Long second = createResource("RB-002");
        Long sourceId = createEvent();
        Long targetId = createEvent();
        dispatchEvent(sourceId, List.of(first));
        dispatchEvent(targetId, List.of(second));

        transfer(sourceId, targetId, null, "REQ-BAD-1", "李四", "资源列表缺失", 400);
        transfer(sourceId, targetId, List.of(), "REQ-BAD-2", "李四", "资源列表为空", 400);
        transfer(sourceId, targetId, List.of("  "), "REQ-BAD-3", "李四", "资源编号空白", 400);
        transfer(sourceId, targetId, List.of("RB-001"), " ", "李四", "请求号空白", 400);
        transfer(sourceId, targetId, List.of("RB-001"), "REQ-BAD-5", " ", "操作人空白", 400);
        transfer(sourceId, targetId, List.of("RB-001"), "REQ-BAD-6", "李四", "  ", 400);
        transfer(null, targetId, List.of("RB-001"), "REQ-BAD-7", "李四", "源事件缺失", 400);
        transfer(sourceId, null, List.of("RB-001"), "REQ-BAD-8", "李四", "目标事件缺失", 400);

        assertEquals(0, recordRepository.count());
    }

    @Test
    void idempotentReplayReturnsFirstResultWithoutDuplicateTransfer() throws Exception {
        Long first = createResource("RI-001");
        Long second = createResource("RI-002");
        Long third = createResource("RI-003");
        Long sourceId = createEvent();
        Long targetId = createEvent();
        dispatchEvent(sourceId, List.of(first, second));
        dispatchEvent(targetId, List.of(third));

        MvcResult firstResult = transfer(sourceId, targetId, List.of("RI-001"), "REQ-IDEMPOTENT-1",
                "李四", "首次转移", 200);
        long firstRecordId = recordIdOf(firstResult);

        MvcResult replayResult = transfer(sourceId, targetId, List.of("RI-001"), "REQ-IDEMPOTENT-1",
                "李四", "首次转移", 200);
        assertEquals(firstRecordId, recordIdOf(replayResult));

        MvcResult replayWithDupCodes = transfer(sourceId, targetId,
                List.of("ri-001", "RI-001"), "REQ-IDEMPOTENT-1",
                "李四", "首次转移", 200);
        assertEquals(firstRecordId, recordIdOf(replayWithDupCodes));

        mockMvc.perform(get("/api/burst-events/{id}", sourceId))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].id").value(second))
                .andExpect(jsonPath("$.transfers", hasSize(1)));
        mockMvc.perform(get("/api/burst-events/{id}", targetId))
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.resources[0].id").value(first))
                .andExpect(jsonPath("$.resources[1].id").value(third));
        assertEquals(1, recordRepository.count());
        assertEquals(1, requestRepository.count());
    }

    @Test
    void concurrentTransfersOfSameResourceOnlyOneSucceeds() throws Exception {
        Long first = createResource("RCC-001");
        Long second = createResource("RCC-002");
        Long third = createResource("RCC-003");
        Long fourth = createResource("RCC-004");
        Long sourceId = createEvent();
        Long targetA = createEvent();
        Long targetB = createEvent();
        dispatchEvent(sourceId, List.of(first, second));
        dispatchEvent(targetA, List.of(third));
        dispatchEvent(targetB, List.of(fourth));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> firstCall = pool.submit(() ->
                    submitConcurrentTransfer(sourceId, targetA, "RCC-001", "REQ-CONC-A", ready, start));
            Future<Integer> secondCall = pool.submit(() ->
                    submitConcurrentTransfer(sourceId, targetB, "RCC-001", "REQ-CONC-B", ready, start));

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            int firstStatus = firstCall.get(30, TimeUnit.SECONDS);
            int secondStatus = secondCall.get(30, TimeUnit.SECONDS);
            int successCount = (firstStatus == 200 ? 1 : 0) + (secondStatus == 200 ? 1 : 0);
            int conflictCount = (firstStatus == 409 ? 1 : 0) + (secondStatus == 409 ? 1 : 0);
            assertEquals(1, successCount);
            assertEquals(1, conflictCount);
        } finally {
            pool.shutdownNow();
        }

        mockMvc.perform(get("/api/burst-events/{id}", sourceId))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].id").value(second));
        long onA = countResourceOnEvent(targetA, first);
        long onB = countResourceOnEvent(targetB, first);
        assertEquals(1L, onA + onB);
        mockMvc.perform(get("/api/emergency-resources/{id}", first))
                .andExpect(jsonPath("$.status").value("BUSY"));
        assertEquals(1, recordRepository.count());
    }

    @Test
    void concurrentReplaysWithSameRequestNoReturnSameResult() throws Exception {
        Long first = createResource("RCR-001");
        Long second = createResource("RCR-002");
        Long third = createResource("RCR-003");
        Long sourceId = createEvent();
        Long targetId = createEvent();
        dispatchEvent(sourceId, List.of(first, second));
        dispatchEvent(targetId, List.of(third));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> firstCall = pool.submit(() ->
                    submitConcurrentTransfer(sourceId, targetId, "RCR-001", "REQ-CONC-R", ready, start));
            Future<Integer> secondCall = pool.submit(() ->
                    submitConcurrentTransfer(sourceId, targetId, "RCR-001", "REQ-CONC-R", ready, start));

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(200, firstCall.get(30, TimeUnit.SECONDS));
            assertEquals(200, secondCall.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        mockMvc.perform(get("/api/burst-events/{id}", sourceId))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.transfers", hasSize(1)));
        mockMvc.perform(get("/api/burst-events/{id}", targetId))
                .andExpect(jsonPath("$.resources", hasSize(2)));
        assertEquals(1, recordRepository.count());
        assertEquals(1, requestRepository.count());
    }

    @Test
    void eventDetailReturnsRelatedTransfersInChronologicalOrder() throws Exception {
        Long first = createResource("RH-001");
        Long second = createResource("RH-002");
        Long third = createResource("RH-003");
        Long fourth = createResource("RH-004");
        Long fifth = createResource("RH-005");
        Long middleId = createEvent();
        Long otherId = createEvent();
        dispatchEvent(middleId, List.of(first, second, third));
        dispatchEvent(otherId, List.of(fourth, fifth));

        transfer(otherId, middleId, List.of("RH-004"), "REQ-HISTORY-1",
                "赵六", "从其他事件转入", 200);
        transfer(middleId, otherId, List.of("RH-001"), "REQ-HISTORY-2",
                "李四", "从本事件转出", 200);
        transfer(otherId, middleId, List.of("RH-005"), "REQ-HISTORY-3",
                "王五", "再次转入", 200);

        mockMvc.perform(get("/api/burst-events/{id}", middleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfers", hasSize(3)))
                .andExpect(jsonPath("$.transfers[0].requestNo").value("REQ-HISTORY-1"))
                .andExpect(jsonPath("$.transfers[0].sourceEventId").value(otherId))
                .andExpect(jsonPath("$.transfers[0].targetEventId").value(middleId))
                .andExpect(jsonPath("$.transfers[1].requestNo").value("REQ-HISTORY-2"))
                .andExpect(jsonPath("$.transfers[1].sourceEventId").value(middleId))
                .andExpect(jsonPath("$.transfers[1].targetEventId").value(otherId))
                .andExpect(jsonPath("$.transfers[2].requestNo").value("REQ-HISTORY-3"))
                .andExpect(jsonPath("$.transfers[2].sourceEventId").value(otherId))
                .andExpect(jsonPath("$.transfers[2].targetEventId").value(middleId));
        mockMvc.perform(get("/api/burst-events/{id}", otherId))
                .andExpect(jsonPath("$.transfers", hasSize(3)))
                .andExpect(jsonPath("$.transfers[0].requestNo").value("REQ-HISTORY-1"))
                .andExpect(jsonPath("$.transfers[2].requestNo").value("REQ-HISTORY-3"));
    }

    private Integer submitConcurrentTransfer(Long sourceId, Long targetId, String resourceCode,
                                             String requestNo, CountDownLatch ready,
                                             CountDownLatch start) {
        try {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));
            Map<String, Object> request = new HashMap<>();
            request.put("sourceEventId", sourceId);
            request.put("targetEventId", targetId);
            request.put("resourceCodes", List.of(resourceCode));
            request.put("requestNo", requestNo);
            request.put("operator", "李四");
            request.put("reason", "并发转移");
            return mockMvc.perform(post("/api/burst-events/resource-transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private long countResourceOnEvent(Long eventId, Long resourceId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("resources")
                .findValuesAsString("id")
                .stream()
                .filter(value -> value.equals(String.valueOf(resourceId)))
                .count();
    }

    @Test
    void sameRequestNoWithDifferentContentReturns409() throws Exception {
        Long first = createResource("RC-001");
        Long second = createResource("RC-002");
        Long third = createResource("RC-003");
        Long fourth = createResource("RC-004");
        Long sourceId = createEvent();
        Long targetId = createEvent();
        dispatchEvent(sourceId, List.of(first, second));
        dispatchEvent(targetId, List.of(third, fourth));

        transfer(sourceId, targetId, List.of("RC-001"), "REQ-CONFLICT-1",
                "李四", "首次转移", 200);

        transfer(sourceId, targetId, List.of("RC-002"), "REQ-CONFLICT-1",
                "李四", "不同资源编号", 409);
        transfer(sourceId, targetId, List.of("RC-001"), "REQ-CONFLICT-1",
                "李四", "不同原因", 409);
        transfer(sourceId, targetId, List.of("RC-001"), "REQ-CONFLICT-1",
                "王五", "不同操作人", 409);
        transfer(targetId, sourceId, List.of("RC-003"), "REQ-CONFLICT-1",
                "李四", "反向转移", 409);

        mockMvc.perform(get("/api/burst-events/{id}", sourceId))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].id").value(second))
                .andExpect(jsonPath("$.transfers", hasSize(1)));
        mockMvc.perform(get("/api/burst-events/{id}", targetId))
                .andExpect(jsonPath("$.resources", hasSize(3)))
                .andExpect(jsonPath("$.resources[*].id", org.hamcrest.Matchers.hasItem(first.intValue())));
        assertEquals(1, recordRepository.count());
        assertEquals(1, requestRepository.count());
    }
}
