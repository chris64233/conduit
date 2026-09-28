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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    private ResourceTransferReversalItemRepository reversalItemRepository;

    @Autowired
    private PipeSegmentRepository pipeSegmentRepository;

    private Long segmentId;

    @BeforeEach
    void setUp() throws Exception {
        reversalItemRepository.deleteAll();
        reversalRecordRepository.deleteAll();
        transferRecordRepository.deleteAll();
        burstEventRepository.deleteAll();
        resourceRepository.deleteAll();
        pipeSegmentRepository.deleteAll();
        segmentId = createSegment();
    }

    @AfterEach
    void tearDown() {
        reversalItemRepository.deleteAll();
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

    private JsonNode reverse(Long transferId, List<String> resourceCodes, String requestNo,
                             String operator, String reason, int expectedStatus) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("resourceCodes", resourceCodes);
        request.put("requestNo", requestNo);
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

    private List<String> codes(JsonNode node, String field) {
        List<String> codes = new ArrayList<>();
        node.get(field).forEach(code -> codes.add(code.asText()));
        return codes;
    }

    @Test
    void reversalMovesSelectedResourcesBackAtomicallyAndKeepsThemBusy() throws Exception {
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

        JsonNode reversal = reverse(transferId, List.of("RV-002"),
                newRequestNo(), "王五", "增援结束，退回资源", 200);

        Assertions.assertNotNull(reversal.get("id"));
        Assertions.assertNotNull(reversal.get("requestNo"));
        Assertions.assertEquals(transferId, reversal.get("transferId").asLong());
        Assertions.assertEquals(sourceEventId, reversal.get("sourceEventId").asLong());
        Assertions.assertEquals(targetEventId, reversal.get("targetEventId").asLong());
        Assertions.assertEquals(List.of("RV-002"), codes(reversal, "resourceCodes"));
        Assertions.assertEquals(List.of(), codes(reversal, "remainingReversibleCodes"));
        Assertions.assertEquals("王五", reversal.get("operator").asText());
        Assertions.assertEquals("增援结束，退回资源", reversal.get("reason").asText());
        Assertions.assertNotNull(reversal.get("operatedAt").asText());

        assertEventResourceIds(sourceEventId, List.of(first, second));
        assertEventResourceIds(targetEventId, List.of(third));
        mockMvc.perform(get("/api/emergency-resources/{id}", second))
                .andExpect(jsonPath("$.status").value("BUSY"));
        Assertions.assertEquals(1, reversalRecordRepository.count());
        Assertions.assertEquals(1, reversalItemRepository.count());
    }

    @Test
    void partialReversalMovesOnlySelectedResourcesBackAndKeepsRestValid() throws Exception {
        Long anchor = createResource("RV-1001");
        Long first = createResource("RV-1002");
        Long second = createResource("RV-1003");
        Long targetAnchor = createResource("RV-1004");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(anchor, first, second));
        dispatchEvent(targetEventId, List.of(targetAnchor));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-1002", "RV-1003"),
                newRequestNo(), "李四", "批量增援", 200);
        Long transferId = transferResponse.get("id").asLong();
        assertEventResourceIds(sourceEventId, List.of(anchor));
        assertEventResourceIds(targetEventId, List.of(first, second, targetAnchor));

        JsonNode firstReversal = reverse(transferId, List.of("RV-1002"),
                newRequestNo(), "王五", "先退回一个资源", 200);
        Assertions.assertEquals(List.of("RV-1002"), codes(firstReversal, "resourceCodes"));
        Assertions.assertEquals(List.of("RV-1003"), codes(firstReversal, "remainingReversibleCodes"));

        // 只回迁选中的资源，其余资源与原转移记录继续有效。
        assertEventResourceIds(sourceEventId, List.of(anchor, first));
        assertEventResourceIds(targetEventId, List.of(second, targetAnchor));
        mockMvc.perform(get("/api/emergency-resources/{id}", second))
                .andExpect(jsonPath("$.status").value("BUSY"));

        JsonNode transferDetail = getEventDetail(targetEventId).get("resourceTransfers").get(0);
        Assertions.assertFalse(transferDetail.get("reversed").asBoolean());
        Assertions.assertEquals(List.of("RV-1002"), codes(transferDetail, "reversedResourceCodes"));
        Assertions.assertEquals(List.of("RV-1003"), codes(transferDetail, "remainingReversibleResourceCodes"));

        // 剩余资源可再次撤销，撤销完成后整笔转移才算全部回迁。
        JsonNode secondReversal = reverse(transferId, List.of("RV-1003"),
                newRequestNo(), "王五", "再退回剩余资源", 200);
        Assertions.assertEquals(List.of("RV-1003"), codes(secondReversal, "resourceCodes"));
        Assertions.assertEquals(List.of(), codes(secondReversal, "remainingReversibleCodes"));

        assertEventResourceIds(sourceEventId, List.of(anchor, first, second));
        assertEventResourceIds(targetEventId, List.of(targetAnchor));
        transferDetail = getEventDetail(targetEventId).get("resourceTransfers").get(0);
        Assertions.assertTrue(transferDetail.get("reversed").asBoolean());
        Assertions.assertEquals(List.of("RV-1002", "RV-1003"),
                codes(transferDetail, "reversedResourceCodes"));
        Assertions.assertEquals(List.of(), codes(transferDetail, "remainingReversibleResourceCodes"));
        Assertions.assertEquals(2, reversalRecordRepository.count());
    }

    @Test
    void partialReversalRejectsDuplicateSelectionButAllowsRemainingResource() throws Exception {
        Long anchor = createResource("RV-1101");
        Long first = createResource("RV-1102");
        Long second = createResource("RV-1103");
        Long targetAnchor = createResource("RV-1104");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(anchor, first, second));
        dispatchEvent(targetEventId, List.of(targetAnchor));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-1102", "RV-1103"),
                newRequestNo(), "李四", "批量增援", 200);
        Long transferId = transferResponse.get("id").asLong();

        reverse(transferId, List.of("RV-1102"), newRequestNo(), "王五", "第一次部分撤销", 200);
        reverse(transferId, List.of("RV-1102"), newRequestNo(), "王五", "重复回迁同一资源", 409);
        reverse(transferId, List.of("RV-1102", "RV-1103"),
                newRequestNo(), "王五", "范围包含已回迁资源", 409);

        // 失败请求不得影响其余资源，剩余资源仍可回迁。
        assertEventResourceIds(sourceEventId, List.of(anchor, first));
        assertEventResourceIds(targetEventId, List.of(second, targetAnchor));
        reverse(transferId, List.of("RV-1103"), newRequestNo(), "王五", "回迁剩余资源", 200);
        assertEventResourceIds(sourceEventId, List.of(anchor, first, second));
        assertEventResourceIds(targetEventId, List.of(targetAnchor));
        Assertions.assertEquals(2, reversalRecordRepository.count());
        Assertions.assertEquals(2, reversalItemRepository.count());
    }

    @Test
    void partialReversalRejectsResourceOutsideOriginalTransfer() throws Exception {
        Long anchor = createResource("RV-1201");
        Long first = createResource("RV-1202");
        Long second = createResource("RV-1203");
        Long outsider = createResource("RV-1204");
        Long targetAnchor = createResource("RV-1205");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(anchor, first, second));
        dispatchEvent(targetEventId, List.of(targetAnchor));
        dispatchEvent(createEvent(), List.of(outsider));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-1202", "RV-1203"),
                newRequestNo(), "李四", "批量增援", 200);
        Long transferId = transferResponse.get("id").asLong();

        reverse(transferId, List.of("RV-1202", "RV-1204"),
                newRequestNo(), "王五", "混入不在原转移范围的资源", 409);

        // 同一事务内即便范围内的资源也不能回迁。
        assertEventResourceIds(sourceEventId, List.of(anchor));
        assertEventResourceIds(targetEventId, List.of(first, second, targetAnchor));
        Assertions.assertEquals(0, reversalRecordRepository.count());
        Assertions.assertEquals(0, reversalItemRepository.count());
    }

    @Test
    void reversalRejectsNonDispatchedEventsAndUnknownTransfer() throws Exception {
        Long first = createResource("RV-201");
        Long second = createResource("RV-202");
        Long third = createResource("RV-203");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetEventId, List.of(third));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-202"),
                newRequestNo(), "李四", "跨事件支援", 200);
        Long transferId = transferResponse.get("id").asLong();

        reverse(999999L, List.of("RV-202"), newRequestNo(), "王五", "转移记录不存在", 404);

        resolveEvent(targetEventId);
        reverse(transferId, List.of("RV-202"), newRequestNo(), "王五", "目标事件已完成", 409);
        resolveEvent(sourceEventId);
        reverse(transferId, List.of("RV-202"), newRequestNo(), "王五", "源事件也已完成", 409);

        Assertions.assertEquals(0, reversalRecordRepository.count());
    }

    @Test
    void reversalRejectsWhenResourceNoLongerHeldByTargetEvent() throws Exception {
        Long first = createResource("RV-301");
        Long second = createResource("RV-302");
        Long third = createResource("RV-303");
        Long fourth = createResource("RV-304");
        Long fifth = createResource("RV-305");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        Long thirdEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second, fifth));
        dispatchEvent(targetEventId, List.of(third));
        dispatchEvent(thirdEventId, List.of(fourth));

        JsonNode movedOn = transfer(sourceEventId, targetEventId, List.of("RV-302"),
                newRequestNo(), "李四", "先转到目标事件", 200);
        transfer(targetEventId, thirdEventId, List.of("RV-302"),
                newRequestNo(), "李四", "再转到第三事件", 200);
        reverse(movedOn.get("id").asLong(), List.of("RV-302"),
                newRequestNo(), "王五", "资源已转往其他事件", 409);

        JsonNode reassignedAway = transfer(sourceEventId, targetEventId, List.of("RV-301"),
                newRequestNo(), "李四", "再次转入目标事件", 200);
        reassignEvent(targetEventId, List.of(third));
        reverse(reassignedAway.get("id").asLong(), List.of("RV-301"),
                newRequestNo(), "王五", "资源已被改派释放", 409);

        mockMvc.perform(get("/api/emergency-resources/{id}", first))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        Assertions.assertEquals(0, reversalRecordRepository.count());
        assertEventResourceIds(targetEventId, List.of(third));
    }

    @Test
    void reversalRejectsWhenTargetEventWouldHaveNoResourceLeft() throws Exception {
        Long first = createResource("RV-401");
        Long second = createResource("RV-402");
        Long third = createResource("RV-403");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetEventId, List.of(third));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-402"),
                newRequestNo(), "李四", "转入目标事件", 200);
        transfer(targetEventId, sourceEventId, List.of("RV-403"),
                newRequestNo(), "李四", "目标事件原有资源反向转出", 200);

        reverse(transferResponse.get("id").asLong(), List.of("RV-402"),
                newRequestNo(), "王五", "撤销会掏空目标事件", 409);

        assertEventResourceIds(sourceEventId, List.of(first, third));
        assertEventResourceIds(targetEventId, List.of(second));
        Assertions.assertEquals(0, reversalRecordRepository.count());
    }

    @Test
    void partialReversalMustKeepAtLeastOneResourceAtTargetEvent() throws Exception {
        Long sourceAnchor = createResource("RV-451");
        Long first = createResource("RV-452");
        Long second = createResource("RV-453");
        Long targetAnchor = createResource("RV-454");
        Long thirdAnchor = createResource("RV-455");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        Long thirdEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(sourceAnchor, first, second));
        dispatchEvent(targetEventId, List.of(targetAnchor));
        dispatchEvent(thirdEventId, List.of(thirdAnchor));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-452", "RV-453"),
                newRequestNo(), "李四", "批量转入目标事件", 200);
        Long transferId = transferResponse.get("id").asLong();
        // 目标事件把自有资源转出，仅靠转入的两个资源支撑。
        transfer(targetEventId, thirdEventId, List.of("RV-454"),
                newRequestNo(), "李四", "目标事件自有资源转出", 200);
        assertEventResourceIds(targetEventId, List.of(first, second));

        // 一次性回迁两个资源会掏空目标事件。
        reverse(transferId, List.of("RV-452", "RV-453"),
                newRequestNo(), "王五", "一次性回迁全部资源", 409);
        assertEventResourceIds(sourceEventId, List.of(sourceAnchor));
        assertEventResourceIds(targetEventId, List.of(first, second));
        Assertions.assertEquals(0, reversalRecordRepository.count());

        // 只回迁一个，目标事件仍保留最后一个资源。
        reverse(transferId, List.of("RV-452"),
                newRequestNo(), "王五", "回迁一个资源", 200);
        assertEventResourceIds(sourceEventId, List.of(sourceAnchor, first));
        assertEventResourceIds(targetEventId, List.of(second));

        // 最后一个资源不能再回迁，否则目标事件将没有任何资源。
        reverse(transferId, List.of("RV-453"),
                newRequestNo(), "王五", "回迁最后一个资源", 409);
        assertEventResourceIds(targetEventId, List.of(second));
        Assertions.assertEquals(1, reversalRecordRepository.count());
        Assertions.assertEquals(1, reversalItemRepository.count());
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
        JsonNode firstResponse = reverse(transferId, List.of("RV-502"),
                requestNo, "王五", "撤销转移", 200);
        JsonNode retryResponse = reverse(transferId, List.of(" RV-502 "),
                requestNo, " 王五 ", " 撤销转移 ", 200);

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

        JsonNode multiTransfer = transfer(sourceEventId, targetEventId, List.of("RV-602", "RV-603"),
                newRequestNo(), "李四", "批量转移", 200);
        JsonNode otherTransfer = transfer(targetEventId, sourceEventId, List.of("RV-604"),
                newRequestNo(), "李四", "另一笔转移", 200);

        String requestNo = newRequestNo();
        reverse(multiTransfer.get("id").asLong(), List.of("RV-602"), requestNo, "王五", "撤销部分资源", 200);

        // 同号不同资源集合、操作人、原因或转移记录均返回冲突。
        reverse(multiTransfer.get("id").asLong(), List.of("RV-603"), requestNo, "王五", "换一批资源", 409);
        reverse(multiTransfer.get("id").asLong(), List.of("RV-602"), requestNo, "赵六", "更换操作人", 409);
        reverse(multiTransfer.get("id").asLong(), List.of("RV-602"), requestNo, "王五", "更换原因", 409);
        reverse(otherTransfer.get("id").asLong(), List.of("RV-604"), requestNo, "王五", "更换转移记录", 409);

        Assertions.assertEquals(1, reversalRecordRepository.count());
        assertEventResourceIds(sourceEventId, List.of(first, second, fourth));
        assertEventResourceIds(targetEventId, List.of(third));

        // 同号同内容重放仍然返回首次结果，且剩余可回迁资源反映最新状态。
        JsonNode replay = reverse(multiTransfer.get("id").asLong(), List.of("RV-602"),
                requestNo, " 王五 ", " 撤销部分资源 ", 200);
        Assertions.assertEquals(List.of("RV-603"), codes(replay, "remainingReversibleCodes"));
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
        Assertions.assertEquals(0, reversalItemRepository.count());
        Assertions.assertEquals(2, transferRecordRepository.count());
    }

    @Test
    void concurrentReversalsOfSameResourceOnlyOneSucceeds() throws Exception {
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
                request.put("resourceCodes", List.of("RV-802"));
                request.put("requestNo", newRequestNo());
                request.put("operator", "王五");
                request.put("reason", "并发撤销同一资源");
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
        Assertions.assertEquals(1, reversalItemRepository.count());
        assertEventResourceIds(sourceEventId, List.of(first, second));
        assertEventResourceIds(targetEventId, List.of(third));
    }

    @Test
    void concurrentOverlappingPartialReversalsNeverReturnResourceTwice() throws Exception {
        Long sourceAnchor = createResource("RV-901");
        Long resourceA = createResource("RV-902");
        Long resourceB = createResource("RV-903");
        Long targetAnchor = createResource("RV-904");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(sourceAnchor, resourceA, resourceB));
        dispatchEvent(targetEventId, List.of(targetAnchor));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-902", "RV-903"),
                newRequestNo(), "李四", "批量增援", 200);
        Long transferId = transferResponse.get("id").asLong();

        // 四个请求范围互相重叠：{A}、{B}、{A,B}、{A}。
        List<List<String>> ranges = List.of(
                List.of("RV-902"),
                List.of("RV-903"),
                List.of("RV-902", "RV-903"),
                List.of("RV-902"));
        int threads = ranges.size();
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> futures = new ArrayList<>();
        for (List<String> range : ranges) {
            futures.add(pool.submit(() -> {
                Map<String, Object> request = new HashMap<>();
                request.put("resourceCodes", range);
                request.put("requestNo", newRequestNo());
                request.put("operator", "王五");
                request.put("reason", "并发重叠撤销");
                barrier.await();
                return mockMvc.perform(post("/api/resource-transfers/{transferId}/reversal", transferId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn().getResponse().getStatus();
            }));
        }
        int success = 0;
        for (Future<Integer> future : futures) {
            int status = future.get(30, TimeUnit.SECONDS);
            Assertions.assertTrue(status == 200 || status == 409, "意外的响应状态: " + status);
            if (status == 200) {
                success++;
            }
        }
        pool.shutdown();

        // 无论谁先抢到锁，每个重叠资源只能回迁一次：A、B 各一次。
        Assertions.assertTrue(success >= 1 && success <= 2, "成功请求数应在 1~2 之间: " + success);
        List<ResourceTransferReversalItem> items = reversalItemRepository.findAll();
        Assertions.assertEquals(2, items.size());
        Set<String> returnedCodeKeys = new HashSet<>();
        for (ResourceTransferReversalItem item : items) {
            Assertions.assertTrue(returnedCodeKeys.add(item.getResourceCodeKey()),
                    "重叠资源被重复回迁: " + item.getResourceCode());
        }
        Assertions.assertEquals(Set.of("RV-902", "RV-903"), returnedCodeKeys);
        assertEventResourceIds(sourceEventId, List.of(sourceAnchor, resourceA, resourceB));
        assertEventResourceIds(targetEventId, List.of(targetAnchor));
    }

    @Test
    void eventDetailShowsTransferAndPartialReversalTimelineInChronologicalOrder() throws Exception {
        Long first = createResource("RV-1001");
        Long second = createResource("RV-1002");
        Long third = createResource("RV-1003");
        Long fourth = createResource("RV-1004");
        Long fifth = createResource("RV-1005");
        Long eventA = createEvent();
        Long eventB = createEvent();
        dispatchEvent(eventA, List.of(first, second, third));
        dispatchEvent(eventB, List.of(fourth, fifth));

        JsonNode firstTransfer = transfer(eventA, eventB, List.of("RV-1001", "RV-1002"),
                newRequestNo(), "李四", "批量转出", 200);
        transfer(eventB, eventA, List.of("RV-1004"), newRequestNo(), "王五", "反向转入", 200);
        JsonNode firstReversal = reverse(firstTransfer.get("id").asLong(), List.of("RV-1001"),
                newRequestNo(), "赵六", "先撤销一个", 200);
        JsonNode secondReversal = reverse(firstTransfer.get("id").asLong(), List.of("RV-1002"),
                newRequestNo(), "赵六", "再撤销剩余一个", 200);

        JsonNode detailA = getEventDetail(eventA);
        JsonNode timeline = detailA.get("resourceTransferTimeline");
        Assertions.assertEquals(4, timeline.size());

        JsonNode firstEntry = timeline.get(0);
        Assertions.assertEquals("TRANSFER", firstEntry.get("type").asText());
        Assertions.assertEquals(firstTransfer.get("id").asLong(), firstEntry.get("id").asLong());
        Assertions.assertEquals(firstTransfer.get("id").asLong(), firstEntry.get("transferId").asLong());
        Assertions.assertEquals(eventA, firstEntry.get("sourceEventId").asLong());
        Assertions.assertEquals(eventB, firstEntry.get("targetEventId").asLong());
        Assertions.assertEquals(List.of("RV-1001", "RV-1002"), codes(firstEntry, "resourceCodes"));
        Assertions.assertEquals("李四", firstEntry.get("operator").asText());
        Assertions.assertEquals("批量转出", firstEntry.get("reason").asText());
        Assertions.assertNotNull(firstEntry.get("requestNo").asText());
        Assertions.assertNotNull(firstEntry.get("operatedAt").asText());

        JsonNode secondEntry = timeline.get(1);
        Assertions.assertEquals("TRANSFER", secondEntry.get("type").asText());
        Assertions.assertEquals(eventB, secondEntry.get("sourceEventId").asLong());
        Assertions.assertEquals(eventA, secondEntry.get("targetEventId").asLong());
        Assertions.assertEquals("王五", secondEntry.get("operator").asText());

        JsonNode thirdEntry = timeline.get(2);
        Assertions.assertEquals("REVERSAL", thirdEntry.get("type").asText());
        Assertions.assertEquals(firstReversal.get("id").asLong(), thirdEntry.get("id").asLong());
        Assertions.assertEquals(firstTransfer.get("id").asLong(), thirdEntry.get("transferId").asLong());
        Assertions.assertEquals(eventA, thirdEntry.get("sourceEventId").asLong());
        Assertions.assertEquals(eventB, thirdEntry.get("targetEventId").asLong());
        Assertions.assertEquals(List.of("RV-1001"), codes(thirdEntry, "resourceCodes"));
        Assertions.assertEquals("赵六", thirdEntry.get("operator").asText());
        Assertions.assertEquals("先撤销一个", thirdEntry.get("reason").asText());

        JsonNode fourthEntry = timeline.get(3);
        Assertions.assertEquals("REVERSAL", fourthEntry.get("type").asText());
        Assertions.assertEquals(secondReversal.get("id").asLong(), fourthEntry.get("id").asLong());
        Assertions.assertEquals(List.of("RV-1002"), codes(fourthEntry, "resourceCodes"));
        Assertions.assertEquals("再撤销剩余一个", fourthEntry.get("reason").asText());

        JsonNode transfers = detailA.get("resourceTransfers");
        Assertions.assertEquals(2, transfers.size());
        Assertions.assertTrue(transfers.get(0).get("reversed").asBoolean());
        Assertions.assertEquals(List.of("RV-1001", "RV-1002"),
                codes(transfers.get(0), "reversedResourceCodes"));
        Assertions.assertEquals(List.of(),
                codes(transfers.get(0), "remainingReversibleResourceCodes"));
        Assertions.assertFalse(transfers.get(1).get("reversed").asBoolean());
        Assertions.assertEquals(List.of(),
                codes(transfers.get(1), "reversedResourceCodes"));
        Assertions.assertEquals(List.of("RV-1004"),
                codes(transfers.get(1), "remainingReversibleResourceCodes"));

        JsonNode detailB = getEventDetail(eventB);
        Assertions.assertEquals(4, detailB.get("resourceTransferTimeline").size());
        Assertions.assertEquals(4, detailA.get("resources").size());
        Assertions.assertEquals(1, detailB.get("resources").size());
    }

    @Test
    void reversalWithInvalidParametersReturnsBadRequest() throws Exception {
        Long first = createResource("RV-11001");
        Long second = createResource("RV-11002");
        Long third = createResource("RV-11003");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetEventId, List.of(third));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId, List.of("RV-11002"),
                newRequestNo(), "李四", "跨事件支援", 200);
        Long transferId = transferResponse.get("id").asLong();

        reverse(transferId, List.of(), newRequestNo(), "王五", "空资源列表", 400);
        reverse(transferId, List.of(" "), newRequestNo(), "王五", "空白资源编号", 400);
        reverse(transferId, List.of("RV-11002"), " ", "王五", "空白请求号", 400);
        reverse(transferId, List.of("RV-11002"), newRequestNo(), " ", "空白操作人", 400);
        reverse(transferId, List.of("RV-11002"), newRequestNo(), "王五", "  ", 400);
        reverse(transferId, List.of("RV-11002"), "R".repeat(65), "王五", "请求号超长", 400);

        Assertions.assertEquals(0, reversalRecordRepository.count());
        assertEventResourceIds(sourceEventId, List.of(first));
        assertEventResourceIds(targetEventId, List.of(second, third));
    }
}
