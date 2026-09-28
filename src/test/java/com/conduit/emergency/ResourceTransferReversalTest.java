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
        request.put("reason", "现场调整资源");
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

    private JsonNode reverse(Long transferId, List<String> resourceCodes, String requestNo, String operator,
                             String reason, int expectedStatus) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("requestNo", requestNo);
        request.put("resourceCodes", resourceCodes);
        request.put("operator", operator);
        request.put("reason", reason);
        MvcResult result = mockMvc.perform(post("/api/resource-transfers/{transferId}/reversal", transferId)
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
    void reversalMovesResourcesBackAtomicallyAndKeepsThemBusy() throws Exception {
        Long first = createResource("RV-001");
        Long second = createResource("RV-002");
        Long third = createResource("RV-003");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetEventId, List.of(third));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-002"),
                newRequestNo(), "李四", "目标事件需要增援", 200);
        Long transferId = transferResponse.get("id").asLong();
        assertEventResourceIds(sourceEventId, List.of(first));
        assertEventResourceIds(targetEventId, List.of(second, third));

        JsonNode reversal = reverse(transferId, List.of("RV-002"), newRequestNo(), "王五", "增援结束，退回资源", 200);

        Assertions.assertNotNull(reversal.get("id"));
        Assertions.assertNotNull(reversal.get("requestNo"));
        Assertions.assertEquals(transferId, reversal.get("transferId").asLong());
        Assertions.assertEquals(sourceEventId, reversal.get("sourceEventId").asLong());
        Assertions.assertEquals(targetEventId, reversal.get("targetEventId").asLong());
        Assertions.assertEquals(1, reversal.get("resourceCodes").size());
        Assertions.assertEquals("RV-002", reversal.get("resourceCodes").get(0).asText());
        Assertions.assertEquals("王五", reversal.get("operator").asText());
        Assertions.assertEquals("增援结束，退回资源", reversal.get("reason").asText());
        Assertions.assertNotNull(reversal.get("operatedAt").asText());

        assertEventResourceIds(sourceEventId, List.of(first, second));
        assertEventResourceIds(targetEventId, List.of(third));
        mockMvc.perform(get("/api/emergency-resources/{id}", second))
                .andExpect(jsonPath("$.status").value("BUSY"));
        Assertions.assertEquals(1, reversalRecordRepository.count());
    }

    @Test
    void reversalRejectsNonDispatchedEventsAndUnknownTransfer() throws Exception {
        Long first = createResource("RV-101");
        Long second = createResource("RV-102");
        Long third = createResource("RV-103");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetEventId, List.of(third));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-102"),
                newRequestNo(), "李四", "跨事件支援", 200);
        Long transferId = transferResponse.get("id").asLong();

        reverse(999999L, List.of("RV-102"), newRequestNo(), "王五", "转移记录不存在", 404);

        resolveEvent(targetEventId);
        reverse(transferId, List.of("RV-102"), newRequestNo(), "王五", "目标事件已完成", 409);
        resolveEvent(sourceEventId);
        reverse(transferId, List.of("RV-102"), newRequestNo(), "王五", "源事件也已完成", 409);

        Assertions.assertEquals(0, reversalRecordRepository.count());
    }

    @Test
    void reversalRejectsWhenResourceNoLongerHeldByTargetEvent() throws Exception {
        Long first = createResource("RV-201");
        Long second = createResource("RV-202");
        Long third = createResource("RV-203");
        Long fourth = createResource("RV-204");
        Long fifth = createResource("RV-205");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        Long thirdEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second, fifth));
        dispatchEvent(targetEventId, List.of(third));
        dispatchEvent(thirdEventId, List.of(fourth));

        JsonNode movedOn = transfer(sourceEventId, targetEventId, List.of("RV-202"),
                newRequestNo(), "李四", "先转到目标事件", 200);
        transfer(targetEventId, thirdEventId, List.of("RV-202"),
                newRequestNo(), "李四", "再转到第三事件", 200);
        reverse(movedOn.get("id").asLong(), List.of("RV-202"), newRequestNo(), "王五", "资源已转往其他事件", 409);

        JsonNode reassignedAway = transfer(sourceEventId, targetEventId, List.of("RV-201"),
                newRequestNo(), "李四", "再次转入目标事件", 200);
        reassignEvent(targetEventId, List.of(third));
        reverse(reassignedAway.get("id").asLong(), List.of("RV-201"), newRequestNo(), "王五", "资源已被改派释放", 409);

        mockMvc.perform(get("/api/emergency-resources/{id}", first))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        Assertions.assertEquals(0, reversalRecordRepository.count());
        assertEventResourceIds(targetEventId, List.of(third));
    }

    @Test
    void reversalRejectsWhenTargetEventWouldHaveNoResourceLeft() throws Exception {
        Long first = createResource("RV-301");
        Long second = createResource("RV-302");
        Long third = createResource("RV-303");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetEventId, List.of(third));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-302"),
                newRequestNo(), "李四", "转入目标事件", 200);
        transfer(targetEventId, sourceEventId, List.of("RV-303"),
                newRequestNo(), "李四", "目标事件原有资源反向转出", 200);

        reverse(transferResponse.get("id").asLong(), List.of("RV-302"), newRequestNo(), "王五", "撤销会掏空目标事件", 409);

        assertEventResourceIds(sourceEventId, List.of(first, third));
        assertEventResourceIds(targetEventId, List.of(second));
        Assertions.assertEquals(0, reversalRecordRepository.count());
    }

    @Test
    void reversalRejectsAlreadyReversedTransfer() throws Exception {
        Long first = createResource("RV-401");
        Long second = createResource("RV-402");
        Long third = createResource("RV-403");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetEventId, List.of(third));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-402"),
                newRequestNo(), "李四", "跨事件支援", 200);
        Long transferId = transferResponse.get("id").asLong();

        reverse(transferId, List.of("RV-402"), newRequestNo(), "王五", "首次撤销", 200);
        reverse(transferId, List.of("RV-402"), newRequestNo(), "王五", "重复撤销", 409);

        Assertions.assertEquals(1, reversalRecordRepository.count());
        assertEventResourceIds(sourceEventId, List.of(first, second));
        assertEventResourceIds(targetEventId, List.of(third));
    }

    @Test
    void idempotentReversalRetryReturnsFirstResultWithoutDuplicateReversal() throws Exception {
        Long first = createResource("RV-501");
        Long second = createResource("RV-502");
        Long third = createResource("RV-503");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetEventId, List.of(third));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-502"),
                newRequestNo(), "李四", "跨事件支援", 200);
        Long transferId = transferResponse.get("id").asLong();

        String requestNo = newRequestNo();
        JsonNode firstResponse = reverse(transferId, List.of("RV-502"), requestNo, "王五", "撤销转移", 200);
        JsonNode retryResponse = reverse(transferId, List.of(" rv-502 "), requestNo, " 王五 ", " 撤销转移 ", 200);

        Assertions.assertEquals(firstResponse.get("id").asLong(), retryResponse.get("id").asLong());
        Assertions.assertEquals(firstResponse.get("operatedAt").asText(),
                retryResponse.get("operatedAt").asText());
        Assertions.assertEquals(1, reversalRecordRepository.count());
        assertEventResourceIds(sourceEventId, List.of(first, second));
        assertEventResourceIds(targetEventId, List.of(third));
    }

    @Test
    void sameReversalRequestNoWithDifferentContentReturnsConflict() throws Exception {
        Long first = createResource("RV-601");
        Long second = createResource("RV-602");
        Long third = createResource("RV-603");
        Long fourth = createResource("RV-604");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second, third));
        dispatchEvent(targetEventId, List.of(fourth));

        JsonNode firstTransfer = transfer(sourceEventId, targetEventId, List.of("RV-602"),
                newRequestNo(), "李四", "第一笔转移", 200);
        JsonNode secondTransfer = transfer(sourceEventId, targetEventId, List.of("RV-603"),
                newRequestNo(), "李四", "第二笔转移", 200);

        String requestNo = newRequestNo();
        reverse(firstTransfer.get("id").asLong(), List.of("RV-602"), requestNo, "王五", "撤销第一笔", 200);

        reverse(firstTransfer.get("id").asLong(), List.of("RV-602"), requestNo, "赵六", "更换操作人", 409);
        reverse(firstTransfer.get("id").asLong(), List.of("RV-602"), requestNo, "王五", "更换原因", 409);
        reverse(firstTransfer.get("id").asLong(), List.of("RV-603"), requestNo, "王五", "更换资源集合", 409);
        reverse(secondTransfer.get("id").asLong(), List.of("RV-602"), requestNo, "王五", "更换转移记录", 409);

        Assertions.assertEquals(1, reversalRecordRepository.count());
        assertEventResourceIds(sourceEventId, List.of(first, second));
        assertEventResourceIds(targetEventId, List.of(third, fourth));
    }

    @Test
    void failedReversalLeavesEventsResourcesAndAuditUntouched() throws Exception {
        Long first = createResource("RV-701");
        Long second = createResource("RV-702");
        Long third = createResource("RV-703");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetEventId, List.of(third));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-702"),
                newRequestNo(), "李四", "跨事件支援", 200);
        Long transferId = transferResponse.get("id").asLong();

        transfer(targetEventId, sourceEventId, List.of("RV-703"),
                newRequestNo(), "李四", "目标事件原有资源转出", 200);
        reverse(transferId, List.of("RV-702"), newRequestNo(), "王五", "撤销会掏空目标事件", 409);

        JsonNode sourceDetail = getEventDetail(sourceEventId);
        Assertions.assertEquals("DISPATCHED", sourceDetail.get("status").asText());
        Assertions.assertEquals(2, sourceDetail.get("resourceTransfers").size());
        Assertions.assertEquals(2, sourceDetail.get("resourceTransferTimeline").size());
        JsonNode targetDetail = getEventDetail(targetEventId);
        Assertions.assertEquals(1, targetDetail.get("resources").size());
        Assertions.assertEquals(0, targetDetail.get("resourceTransferTimeline").size()
                - targetDetail.get("resourceTransfers").size());
        mockMvc.perform(get("/api/emergency-resources/{id}", second))
                .andExpect(jsonPath("$.status").value("BUSY"));
        Assertions.assertEquals(0, reversalRecordRepository.count());
        Assertions.assertEquals(2, transferRecordRepository.count());
    }

    @Test
    void concurrentReversalsOfSameTransferOnlyOneSucceeds() throws Exception {
        Long first = createResource("RV-801");
        Long second = createResource("RV-802");
        Long third = createResource("RV-803");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetEventId, List.of(third));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-802"),
                newRequestNo(), "李四", "跨事件支援", 200);
        Long transferId = transferResponse.get("id").asLong();

        int threads = 4;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                Map<String, Object> request = new HashMap<>();
                request.put("requestNo", newRequestNo());
                request.put("resourceCodes", List.of("RV-802"));
                request.put("operator", "王五");
                request.put("reason", "并发撤销同一笔转移");
                barrier.await();
                return mockMvc.perform(post("/api/resource-transfers/{transferId}/reversal", transferId)
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
        assertEventResourceIds(sourceEventId, List.of(first, second));
        assertEventResourceIds(targetEventId, List.of(third));
    }

    @Test
    void eventDetailShowsTransferAndReversalTimelineInChronologicalOrder() throws Exception {
        Long first = createResource("RV-901");
        Long second = createResource("RV-902");
        Long third = createResource("RV-903");
        Long fourth = createResource("RV-904");
        Long fifth = createResource("RV-905");
        Long eventA = createEvent();
        Long eventB = createEvent();
        dispatchEvent(eventA, List.of(first, second, third));
        dispatchEvent(eventB, List.of(fourth, fifth));

        JsonNode firstTransfer = transfer(eventA, eventB, List.of("RV-901"),
                newRequestNo(), "李四", "第一次转出", 200);
        transfer(eventB, eventA, List.of("RV-904"), newRequestNo(), "王五", "反向转入", 200);
        JsonNode reversal = reverse(firstTransfer.get("id").asLong(), List.of("RV-901"),
                newRequestNo(), "赵六", "撤销第一次转出", 200);

        JsonNode detailA = getEventDetail(eventA);
        JsonNode timeline = detailA.get("resourceTransferTimeline");
        Assertions.assertEquals(3, timeline.size());

        JsonNode firstEntry = timeline.get(0);
        Assertions.assertEquals("TRANSFER", firstEntry.get("type").asText());
        Assertions.assertEquals(firstTransfer.get("id").asLong(), firstEntry.get("id").asLong());
        Assertions.assertEquals(firstTransfer.get("id").asLong(), firstEntry.get("transferId").asLong());
        Assertions.assertEquals(eventA, firstEntry.get("sourceEventId").asLong());
        Assertions.assertEquals(eventB, firstEntry.get("targetEventId").asLong());
        Assertions.assertEquals("RV-901", firstEntry.get("resourceCodes").get(0).asText());
        Assertions.assertEquals("李四", firstEntry.get("operator").asText());
        Assertions.assertEquals("第一次转出", firstEntry.get("reason").asText());
        Assertions.assertNotNull(firstEntry.get("requestNo").asText());
        Assertions.assertNotNull(firstEntry.get("operatedAt").asText());

        JsonNode secondEntry = timeline.get(1);
        Assertions.assertEquals("TRANSFER", secondEntry.get("type").asText());
        Assertions.assertEquals(eventB, secondEntry.get("sourceEventId").asLong());
        Assertions.assertEquals(eventA, secondEntry.get("targetEventId").asLong());
        Assertions.assertEquals("王五", secondEntry.get("operator").asText());

        JsonNode thirdEntry = timeline.get(2);
        Assertions.assertEquals("REVERSAL", thirdEntry.get("type").asText());
        Assertions.assertEquals(reversal.get("id").asLong(), thirdEntry.get("id").asLong());
        Assertions.assertEquals(firstTransfer.get("id").asLong(), thirdEntry.get("transferId").asLong());
        Assertions.assertEquals(eventA, thirdEntry.get("sourceEventId").asLong());
        Assertions.assertEquals(eventB, thirdEntry.get("targetEventId").asLong());
        Assertions.assertEquals("RV-901", thirdEntry.get("resourceCodes").get(0).asText());
        Assertions.assertEquals("赵六", thirdEntry.get("operator").asText());
        Assertions.assertEquals("撤销第一次转出", thirdEntry.get("reason").asText());
        Assertions.assertNotNull(thirdEntry.get("operatedAt").asText());

        JsonNode transfers = detailA.get("resourceTransfers");
        Assertions.assertEquals(2, transfers.size());
        Assertions.assertTrue(transfers.get(0).get("reversed").asBoolean());
        Assertions.assertFalse(transfers.get(1).get("reversed").asBoolean());

        JsonNode detailB = getEventDetail(eventB);
        Assertions.assertEquals(3, detailB.get("resourceTransferTimeline").size());
        Assertions.assertEquals(4, detailA.get("resources").size());
        Assertions.assertEquals(1, detailB.get("resources").size());
    }

    @Test
    void reversalWithInvalidParametersReturnsBadRequest() throws Exception {
        Long first = createResource("RV-1001");
        Long second = createResource("RV-1002");
        Long third = createResource("RV-1003");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetEventId, List.of(third));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-1002"),
                newRequestNo(), "李四", "跨事件支援", 200);
        Long transferId = transferResponse.get("id").asLong();

        reverse(transferId, List.of("RV-1002"), " ", "王五", "空白请求号", 400);
        reverse(transferId, List.of(), newRequestNo(), "王五", "资源列表为空", 400);
        reverse(transferId, List.of(" "), newRequestNo(), "王五", "空白资源编号", 400);
        reverse(transferId, List.of("RV-1002"), newRequestNo(), " ", "空白操作人", 400);
        reverse(transferId, List.of("RV-1002"), newRequestNo(), "王五", "  ", 400);
        reverse(transferId, List.of("RV-1002"), "R".repeat(65), "王五", "请求号超长", 400);

        Assertions.assertEquals(0, reversalRecordRepository.count());
        assertEventResourceIds(sourceEventId, List.of(first));
        assertEventResourceIds(targetEventId, List.of(second, third));
    }
}
