package com.conduit.emergency;

import com.conduit.pipesegment.PipeSegmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceTransferReversalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BurstEventRepository burstEventRepository;

    @Autowired
    private EmergencyResourceRepository resourceRepository;

    @Autowired
    private ResourceTransferRecordRepository transferRecordRepository;

    @Autowired
    private ResourceTransferReversalRecordRepository reversalRecordRepository;

    @Autowired
    private PipeSegmentRepository pipeSegmentRepository;

    private Long segmentId;

    @BeforeEach
    void setUp() throws Exception {
        reversalRecordRepository.deleteAll();
        transferRecordRepository.deleteAll();
        burstEventRepository.deleteAll();
        resourceRepository.deleteAll();
        pipeSegmentRepository.deleteAll();
        segmentId = createSegment();
    }

    @AfterEach
    void tearDown() {
        reversalRecordRepository.deleteAll();
        transferRecordRepository.deleteAll();
        burstEventRepository.deleteAll();
        resourceRepository.deleteAll();
        pipeSegmentRepository.deleteAll();
    }

    private Long createSegment() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("code", "WS-REVERSAL-" + System.nanoTime());
        request.put("name", "城西供水支管");
        request.put("utilityType", "WATER");
        request.put("material", "球墨铸铁");
        request.put("diameterMm", 300);
        request.put("startLongitude", 116.40);
        request.put("startLatitude", 39.90);
        request.put("endLongitude", 116.41);
        request.put("endLatitude", 39.91);
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
        request.put("description", "支管爆裂，请应急支援");
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

    private void reassignEvent(Long eventId, List<Long> resourceIds) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("resourceIds", resourceIds);
        request.put("operator", "赵六");
        request.put("reason", "调整资源");
        mockMvc.perform(post("/api/burst-events/{id}/reassignment", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private JsonNode transfer(Long sourceEventId, Long targetEventId, List<String> resourceCodes,
                              String requestNo, String operator, String reason, int expectedStatus)
            throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("sourceEventId", sourceEventId);
        request.put("targetEventId", targetEventId);
        request.put("resourceCodes", resourceCodes);
        request.put("requestNo", requestNo);
        request.put("operator", operator);
        request.put("reason", reason);
        MvcResult result = mockMvc.perform(post("/api/resource-transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? null : objectMapper.readTree(body);
    }

    private JsonNode reverse(Long transferId, String requestNo, String operator, String reason,
                             int expectedStatus) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("requestNo", requestNo);
        request.put("operator", operator);
        request.put("reason", reason);
        MvcResult result = mockMvc.perform(post("/api/resource-transfers/{id}/reversals", transferId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? null : objectMapper.readTree(body);
    }

    private String newRequestNo() {
        return "RV-" + UUID.randomUUID();
    }

    private JsonNode getEventDetail(Long eventId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertEventResourceIds(Long eventId, List<Long> expectedResourceIds) throws Exception {
        JsonNode resources = getEventDetail(eventId).get("resources");
        Assertions.assertEquals(expectedResourceIds.size(), resources.size());
        for (int i = 0; i < expectedResourceIds.size(); i++) {
            Assertions.assertEquals(expectedResourceIds.get(i), resources.get(i).get("id").asLong());
        }
    }

    @Test
    void reversalMovesAllResourcesBackToSourceEventAtomically() throws Exception {
        Long first = createResource("RV-001");
        Long second = createResource("RV-002");
        Long third = createResource("RV-003");
        Long targetOwned = createResource("RV-004");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second, third));
        dispatchEvent(targetEventId, List.of(targetOwned));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId,
                List.of("RV-001", "RV-002"), newRequestNo(), "李四", "目标事件需要增援", 200);
        Long transferId = transferResponse.get("id").asLong();
        assertEventResourceIds(sourceEventId, List.of(third));
        assertEventResourceIds(targetEventId, List.of(first, second, targetOwned));

        JsonNode reversal = reverse(transferId, newRequestNo(), "王五", "增援结束，撤回资源", 200);

        Assertions.assertNotNull(reversal.get("id"));
        Assertions.assertEquals(transferId, reversal.get("transferId").asLong());
        Assertions.assertEquals(transferResponse.get("requestNo").asText(),
                reversal.get("transferRequestNo").asText());
        Assertions.assertEquals(sourceEventId, reversal.get("sourceEventId").asLong());
        Assertions.assertEquals(targetEventId, reversal.get("targetEventId").asLong());
        Assertions.assertEquals(2, reversal.get("resourceCodes").size());
        Assertions.assertEquals("RV-001", reversal.get("resourceCodes").get(0).asText());
        Assertions.assertEquals("RV-002", reversal.get("resourceCodes").get(1).asText());
        Assertions.assertEquals("王五", reversal.get("operator").asText());
        Assertions.assertEquals("增援结束，撤回资源", reversal.get("reason").asText());
        Assertions.assertNotNull(reversal.get("requestNo"));
        Assertions.assertNotNull(reversal.get("operatedAt").asText());

        assertEventResourceIds(sourceEventId, List.of(first, second, third));
        assertEventResourceIds(targetEventId, List.of(targetOwned));
        mockMvc.perform(get("/api/emergency-resources/{id}", first))
                .andExpect(jsonPath("$.status").value("BUSY"));
        mockMvc.perform(get("/api/emergency-resources/{id}", second))
                .andExpect(jsonPath("$.status").value("BUSY"));
        Assertions.assertEquals(1, reversalRecordRepository.count());
    }

    @Test
    void reversalRejectsWhenEitherEventNotDispatched() throws Exception {
        Long moved = createResource("RV-101");
        Long kept = createResource("RV-102");
        Long targetOwned = createResource("RV-103");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(moved, kept));
        dispatchEvent(targetEventId, List.of(targetOwned));
        Long transferId = transfer(sourceEventId, targetEventId, List.of("RV-101"),
                newRequestNo(), "李四", "增援", 200).get("id").asLong();

        resolveEvent(targetEventId);
        reverse(transferId, newRequestNo(), "王五", "目标事件已完成", 409);

        Long movedBack = createResource("RV-104");
        Long keptBack = createResource("RV-105");
        Long targetOwnedBack = createResource("RV-106");
        Long sourceEventB = createEvent();
        Long targetEventB = createEvent();
        dispatchEvent(sourceEventB, List.of(movedBack, keptBack));
        dispatchEvent(targetEventB, List.of(targetOwnedBack));
        Long transferIdB = transfer(sourceEventB, targetEventB, List.of("RV-104"),
                newRequestNo(), "李四", "增援", 200).get("id").asLong();
        resolveEvent(sourceEventB);
        reverse(transferIdB, newRequestNo(), "王五", "源事件已完成", 409);

        Assertions.assertEquals(0, reversalRecordRepository.count());
    }

    @Test
    void reversalRejectsWhenResourceLeftTargetEventOrNotBusy() throws Exception {
        Long moved = createResource("RV-201");
        Long kept = createResource("RV-202");
        Long targetOwned = createResource("RV-203");
        Long thirdOwned = createResource("RV-204");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        Long thirdEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(moved, kept));
        dispatchEvent(targetEventId, List.of(targetOwned));
        dispatchEvent(thirdEventId, List.of(thirdOwned));

        Long transferId = transfer(sourceEventId, targetEventId, List.of("RV-201"),
                newRequestNo(), "李四", "增援", 200).get("id").asLong();
        transfer(targetEventId, thirdEventId, List.of("RV-201"),
                newRequestNo(), "李四", "继续向第三事件增援", 200);
        reverse(transferId, newRequestNo(), "王五", "资源已不在目标事件", 409);

        Long movedB = createResource("RV-205");
        Long keptB = createResource("RV-206");
        Long targetOwnedB = createResource("RV-207");
        Long sourceEventB = createEvent();
        Long targetEventB = createEvent();
        dispatchEvent(sourceEventB, List.of(movedB, keptB));
        dispatchEvent(targetEventB, List.of(targetOwnedB));
        Long transferIdB = transfer(sourceEventB, targetEventB, List.of("RV-205"),
                newRequestNo(), "李四", "增援", 200).get("id").asLong();
        reassignEvent(targetEventB, List.of(targetOwnedB));
        mockMvc.perform(get("/api/emergency-resources/{id}", movedB))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        reverse(transferIdB, newRequestNo(), "王五", "资源已改派释放", 409);

        Assertions.assertEquals(0, reversalRecordRepository.count());
    }

    @Test
    void reversalRejectsWhenTargetEventWouldHaveNoResourceLeft() throws Exception {
        Long moved = createResource("RV-301");
        Long kept = createResource("RV-302");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(moved, kept));
        dispatchEvent(targetEventId, List.of(createResource("RV-303")));

        Long transferId = transfer(sourceEventId, targetEventId, List.of("RV-302"),
                newRequestNo(), "李四", "增援", 200).get("id").asLong();
        // 目标事件原有资源被转走，撤销将掏空目标事件
        Long thirdEventId = createEvent();
        dispatchEvent(thirdEventId, List.of(createResource("RV-304")));
        transfer(targetEventId, thirdEventId, List.of("RV-303"),
                newRequestNo(), "李四", "继续调配", 200);

        reverse(transferId, newRequestNo(), "王五", "撤销将掏空目标事件", 409);

        assertEventResourceIds(targetEventId, List.of(resourceIdByCode("RV-302")));
        Assertions.assertEquals(0, reversalRecordRepository.count());
    }

    private Long resourceIdByCode(String code) {
        return resourceRepository.findByCodeKey(code).orElseThrow().getId();
    }

    @Test
    void reversalRejectsAlreadyReversedTransfer() throws Exception {
        Long moved = createResource("RV-401");
        Long kept = createResource("RV-402");
        Long targetOwned = createResource("RV-403");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(moved, kept));
        dispatchEvent(targetEventId, List.of(targetOwned));
        Long transferId = transfer(sourceEventId, targetEventId, List.of("RV-401"),
                newRequestNo(), "李四", "增援", 200).get("id").asLong();

        reverse(transferId, newRequestNo(), "王五", "首次撤销", 200);
        reverse(transferId, newRequestNo(), "赵六", "重复撤销", 409);

        Assertions.assertEquals(1, reversalRecordRepository.count());
        assertEventResourceIds(sourceEventId, List.of(moved, kept));
        assertEventResourceIds(targetEventId, List.of(targetOwned));
    }

    @Test
    void idempotentReversalRetryReturnsFirstResultWithoutDuplicateReversal() throws Exception {
        Long moved = createResource("RV-501");
        Long kept = createResource("RV-502");
        Long targetOwned = createResource("RV-503");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(moved, kept));
        dispatchEvent(targetEventId, List.of(targetOwned));
        Long transferId = transfer(sourceEventId, targetEventId, List.of("RV-501"),
                newRequestNo(), "李四", "增援", 200).get("id").asLong();

        String requestNo = newRequestNo();
        JsonNode firstResponse = reverse(transferId, requestNo, "王五", "撤销增援", 200);
        JsonNode retryResponse = reverse(transferId, requestNo, " 王五 ", " 撤销增援 ", 200);

        Assertions.assertEquals(firstResponse.get("id").asLong(), retryResponse.get("id").asLong());
        Assertions.assertEquals(firstResponse.get("operatedAt").asText(),
                retryResponse.get("operatedAt").asText());
        Assertions.assertEquals(1, reversalRecordRepository.count());
        assertEventResourceIds(sourceEventId, List.of(moved, kept));
        assertEventResourceIds(targetEventId, List.of(targetOwned));
    }

    @Test
    void sameReversalRequestNoWithDifferentContentReturnsConflict() throws Exception {
        Long moved = createResource("RV-601");
        Long kept = createResource("RV-602");
        Long targetOwned = createResource("RV-603");
        Long targetExtra = createResource("RV-604");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(moved, kept));
        dispatchEvent(targetEventId, List.of(targetOwned, targetExtra));
        Long transferId = transfer(sourceEventId, targetEventId, List.of("RV-601"),
                newRequestNo(), "李四", "增援", 200).get("id").asLong();

        String requestNo = newRequestNo();
        reverse(transferId, requestNo, "王五", "首次撤销", 200);

        reverse(transferId, requestNo, "赵六", "更换操作人", 409);
        reverse(transferId, requestNo, "王五", "更换原因", 409);

        Long anotherTransferId = transfer(targetEventId, sourceEventId, List.of("RV-603"),
                newRequestNo(), "李四", "反向增援", 200).get("id").asLong();
        reverse(anotherTransferId, requestNo, "王五", "更换关联转移", 409);

        Assertions.assertEquals(1, reversalRecordRepository.count());
    }

    @Test
    void reversalOfUnknownTransferReturnsNotFound() throws Exception {
        reverse(999L, newRequestNo(), "王五", "转移记录不存在", 404);
        Assertions.assertEquals(0, reversalRecordRepository.count());
    }

    @Test
    void failedReversalRollsBackEventsAndAudit() throws Exception {
        Long moved = createResource("RV-701");
        Long kept = createResource("RV-702");
        Long targetOwned = createResource("RV-703");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(moved, kept));
        dispatchEvent(targetEventId, List.of(targetOwned));
        Long transferId = transfer(sourceEventId, targetEventId, List.of("RV-701"),
                newRequestNo(), "李四", "增援", 200).get("id").asLong();

        resolveEvent(sourceEventId);
        reverse(transferId, newRequestNo(), "王五", "源事件已完成，撤销失败", 409);

        mockMvc.perform(get("/api/burst-events/{id}", sourceEventId))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resourceTransferReversals", hasSize(0)));
        mockMvc.perform(get("/api/burst-events/{id}", targetEventId))
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.resourceTransferReversals", hasSize(0)));
        Assertions.assertEquals(0, reversalRecordRepository.count());
    }

    @Test
    void concurrentReversalsOfSameTransferOnlyOneSucceeds() throws Exception {
        Long moved = createResource("RV-801");
        Long kept = createResource("RV-802");
        Long targetOwned = createResource("RV-803");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(moved, kept));
        dispatchEvent(targetEventId, List.of(targetOwned));
        Long transferId = transfer(sourceEventId, targetEventId, List.of("RV-801"),
                newRequestNo(), "李四", "增援", 200).get("id").asLong();

        int threads = 4;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                Map<String, Object> request = new HashMap<>();
                request.put("requestNo", newRequestNo());
                request.put("operator", "王五");
                request.put("reason", "并发撤销同一转移");
                barrier.await();
                return mockMvc.perform(post("/api/resource-transfers/{id}/reversals", transferId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn().getResponse().getStatus();
            }));
        }
        int success = 0;
        int conflict = 0;
        for (Future<Integer> future : futures) {
            int status = future.get(30, TimeUnit.SECONDS);
            if (status == 200) {
                success++;
            } else if (status == 409) {
                conflict++;
            } else {
                Assertions.fail("意外的响应状态: " + status);
            }
        }
        pool.shutdown();

        Assertions.assertEquals(1, success);
        Assertions.assertEquals(threads - 1, conflict);
        Assertions.assertEquals(1, reversalRecordRepository.count());
        assertEventResourceIds(sourceEventId, List.of(moved, kept));
        assertEventResourceIds(targetEventId, List.of(targetOwned));
    }

    @Test
    void eventDetailShowsTransfersAndReversalsInChronologicalOrder() throws Exception {
        Long first = createResource("RV-901");
        Long second = createResource("RV-902");
        Long third = createResource("RV-903");
        Long targetOwned = createResource("RV-904");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second, third));
        dispatchEvent(targetEventId, List.of(targetOwned));

        JsonNode firstTransfer = transfer(sourceEventId, targetEventId, List.of("RV-901"),
                newRequestNo(), "李四", "第一次转出", 200);
        JsonNode secondTransfer = transfer(sourceEventId, targetEventId, List.of("RV-902"),
                newRequestNo(), "李四", "第二次转出", 200);
        reverse(firstTransfer.get("id").asLong(), newRequestNo(), "王五", "撤销第一次转移", 200);

        JsonNode detailSource = getEventDetail(sourceEventId);
        Assertions.assertEquals(2, detailSource.get("resourceTransfers").size());
        Assertions.assertEquals(1, detailSource.get("resourceTransferReversals").size());
        JsonNode reversal = detailSource.get("resourceTransferReversals").get(0);
        Assertions.assertEquals(firstTransfer.get("id").asLong(), reversal.get("transferId").asLong());
        Assertions.assertEquals(firstTransfer.get("requestNo").asText(),
                reversal.get("transferRequestNo").asText());
        Assertions.assertEquals(sourceEventId, reversal.get("sourceEventId").asLong());
        Assertions.assertEquals(targetEventId, reversal.get("targetEventId").asLong());
        Assertions.assertEquals("RV-901", reversal.get("resourceCodes").get(0).asText());
        Assertions.assertEquals("王五", reversal.get("operator").asText());
        Assertions.assertEquals("撤销第一次转移", reversal.get("reason").asText());
        Assertions.assertNotNull(reversal.get("operatedAt").asText());
        Assertions.assertFalse(java.time.Instant.parse(reversal.get("operatedAt").asText())
                .isBefore(java.time.Instant.parse(
                        detailSource.get("resourceTransfers").get(0).get("operatedAt").asText())));

        JsonNode detailTarget = getEventDetail(targetEventId);
        Assertions.assertEquals(2, detailTarget.get("resourceTransfers").size());
        Assertions.assertEquals(1, detailTarget.get("resourceTransferReversals").size());
        Assertions.assertEquals(reversal.get("id").asLong(),
                detailTarget.get("resourceTransferReversals").get(0).get("id").asLong());

        assertEventResourceIds(sourceEventId, List.of(first, third));
        assertEventResourceIds(targetEventId, List.of(second, targetOwned));
        Assertions.assertEquals(secondTransfer.get("id").asLong(),
                detailSource.get("resourceTransfers").get(1).get("id").asLong());
    }

    @Test
    void reversalWithInvalidParametersReturnsBadRequest() throws Exception {
        Long moved = createResource("RV-1001");
        Long kept = createResource("RV-1002");
        Long targetOwned = createResource("RV-1003");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(moved, kept));
        dispatchEvent(targetEventId, List.of(targetOwned));
        Long transferId = transfer(sourceEventId, targetEventId, List.of("RV-1001"),
                newRequestNo(), "李四", "增援", 200).get("id").asLong();

        reverse(transferId, " ", "王五", "空白请求号", 400);
        reverse(transferId, newRequestNo(), " ", "空白操作人", 400);
        reverse(transferId, newRequestNo(), "王五", "  ", 400);

        Assertions.assertEquals(0, reversalRecordRepository.count());
        assertEventResourceIds(sourceEventId, List.of(kept));
        assertEventResourceIds(targetEventId, List.of(moved, targetOwned));
    }
}
