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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceTransferPartialReversalTest {

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
        request.put("code", "WS-PARTIAL-" + System.nanoTime());
        request.put("name", "城东供水干管");
        request.put("utilityType", "WATER");
        request.put("material", "球墨铸铁");
        request.put("diameterMm", 400);
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
        request.put("description", "干管爆裂，请应急支援");
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

    private JsonNode transfer(Long sourceEventId, Long targetEventId, List<String> resourceCodes,
                              String requestNo, int expectedStatus) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("sourceEventId", sourceEventId);
        request.put("targetEventId", targetEventId);
        request.put("resourceCodes", resourceCodes);
        request.put("requestNo", requestNo);
        request.put("operator", "李四");
        request.put("reason", "跨事件增援");
        MvcResult result = mockMvc.perform(post("/api/resource-transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? null : objectMapper.readTree(body);
    }

    private JsonNode reverse(Long transferId, List<String> resourceCodes, String requestNo,
                             int expectedStatus) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("requestNo", requestNo);
        request.put("resourceCodes", resourceCodes);
        request.put("operator", "王五");
        request.put("reason", "部分增援结束，退回选中资源");
        MvcResult result = mockMvc.perform(post("/api/resource-transfers/{transferId}/reversal", transferId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? null : objectMapper.readTree(body);
    }

    private String newRequestNo() {
        return "PR-" + UUID.randomUUID();
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
        List<Long> sortedExpected = expectedResourceIds.stream().sorted().toList();
        for (int i = 0; i < sortedExpected.size(); i++) {
            Assertions.assertEquals(sortedExpected.get(i), resources.get(i).get("id").asLong());
        }
    }

    private List<String> codeList(JsonNode arrayNode) {
        List<String> codes = new ArrayList<>();
        for (JsonNode code : arrayNode) {
            codes.add(code.asText());
        }
        return codes;
    }

    @Test
    void partialReversalReturnsOnlySelectedResourcesAndKeepsTransferAlive() throws Exception {
        Long keep = createResource("PR-001");
        Long first = createResource("PR-002");
        Long second = createResource("PR-003");
        Long third = createResource("PR-004");
        Long targetOwn = createResource("PR-005");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(keep, first, second, third));
        dispatchEvent(targetEventId, List.of(targetOwn));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId,
                List.of("PR-002", "PR-003", "PR-004"), newRequestNo(), 200);
        Long transferId = transferResponse.get("id").asLong();
        assertEventResourceIds(sourceEventId, List.of(keep));
        assertEventResourceIds(targetEventId, List.of(targetOwn, first, second, third));

        JsonNode reversal = reverse(transferId, List.of(" pr-003 "), newRequestNo(), 200);

        Assertions.assertEquals(transferId, reversal.get("transferId").asLong());
        Assertions.assertEquals(List.of("PR-003"), codeList(reversal.get("resourceCodes")));
        Assertions.assertEquals(sourceEventId, reversal.get("sourceEventId").asLong());
        Assertions.assertEquals(targetEventId, reversal.get("targetEventId").asLong());

        assertEventResourceIds(sourceEventId, List.of(keep, second));
        assertEventResourceIds(targetEventId, List.of(targetOwn, first, third));

        JsonNode transferDetail = null;
        for (JsonNode record : getEventDetail(sourceEventId).get("resourceTransfers")) {
            if (record.get("id").asLong() == transferId) {
                transferDetail = record;
            }
        }
        Assertions.assertNotNull(transferDetail);
        Assertions.assertFalse(transferDetail.get("reversed").asBoolean());
        Assertions.assertEquals(List.of("PR-003"), codeList(transferDetail.get("reversedResourceCodes")));
        Assertions.assertEquals(List.of("PR-002", "PR-004"),
                codeList(transferDetail.get("remainingResourceCodes")));
        Assertions.assertEquals(1, reversalRecordRepository.count());
        Assertions.assertEquals(1, transferRecordRepository.count());
    }

    @Test
    void partialReversalCanDrainTransferOneResourceAtATime() throws Exception {
        Long keep = createResource("PR-101");
        Long first = createResource("PR-102");
        Long second = createResource("PR-103");
        Long targetOwn = createResource("PR-104");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(keep, first, second));
        dispatchEvent(targetEventId, List.of(targetOwn));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId,
                List.of("PR-102", "PR-103"), newRequestNo(), 200);
        Long transferId = transferResponse.get("id").asLong();

        reverse(transferId, List.of("PR-102"), newRequestNo(), 200);
        reverse(transferId, List.of("PR-103"), newRequestNo(), 200);

        assertEventResourceIds(sourceEventId, List.of(keep, first, second));
        assertEventResourceIds(targetEventId, List.of(targetOwn));

        JsonNode transferDetail = getEventDetail(sourceEventId).get("resourceTransfers").get(0);
        Assertions.assertTrue(transferDetail.get("reversed").asBoolean());
        Assertions.assertEquals(List.of("PR-102", "PR-103"),
                codeList(transferDetail.get("reversedResourceCodes")));
        Assertions.assertEquals(List.of(), codeList(transferDetail.get("remainingResourceCodes")));

        JsonNode timeline = getEventDetail(sourceEventId).get("resourceTransferTimeline");
        Assertions.assertEquals(3, timeline.size());
        Assertions.assertEquals("TRANSFER", timeline.get(0).get("type").asText());
        Assertions.assertEquals("REVERSAL", timeline.get(1).get("type").asText());
        Assertions.assertEquals(List.of("PR-102"), codeList(timeline.get(1).get("resourceCodes")));
        Assertions.assertEquals("REVERSAL", timeline.get(2).get("type").asText());
        Assertions.assertEquals(List.of("PR-103"), codeList(timeline.get(2).get("resourceCodes")));
        Assertions.assertEquals(2, reversalRecordRepository.count());
    }

    @Test
    void partialReversalRejectsResourceAlreadyReturned() throws Exception {
        Long keep = createResource("PR-201");
        Long first = createResource("PR-202");
        Long second = createResource("PR-203");
        Long targetOwn = createResource("PR-204");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(keep, first, second));
        dispatchEvent(targetEventId, List.of(targetOwn));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId,
                List.of("PR-202", "PR-203"), newRequestNo(), 200);
        Long transferId = transferResponse.get("id").asLong();

        reverse(transferId, List.of("PR-202"), newRequestNo(), 200);
        reverse(transferId, List.of("PR-202"), newRequestNo(), 409);
        reverse(transferId, List.of("PR-202", "PR-203"), newRequestNo(), 409);

        assertEventResourceIds(sourceEventId, List.of(keep, first));
        assertEventResourceIds(targetEventId, List.of(targetOwn, second));
        Assertions.assertEquals(1, reversalRecordRepository.count());

        reverse(transferId, List.of("PR-203"), newRequestNo(), 200);
        assertEventResourceIds(sourceEventId, List.of(keep, first, second));
        assertEventResourceIds(targetEventId, List.of(targetOwn));
        Assertions.assertEquals(2, reversalRecordRepository.count());
    }

    @Test
    void partialReversalRejectsCodesOutsideTransferAndRollsBack() throws Exception {
        Long keep = createResource("PR-301");
        Long first = createResource("PR-302");
        Long second = createResource("PR-303");
        Long outsider = createResource("PR-304");
        Long targetOwn = createResource("PR-305");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(keep, first, second, outsider));
        dispatchEvent(targetEventId, List.of(targetOwn));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId,
                List.of("PR-302", "PR-303"), newRequestNo(), 200);
        Long transferId = transferResponse.get("id").asLong();

        reverse(transferId, List.of("PR-304"), newRequestNo(), 409);
        reverse(transferId, List.of("PR-302", "PR-304"), newRequestNo(), 409);
        reverse(transferId, List.of("PR-999"), newRequestNo(), 409);

        assertEventResourceIds(sourceEventId, List.of(keep, outsider));
        assertEventResourceIds(targetEventId, List.of(targetOwn, first, second));
        Assertions.assertEquals(0, reversalRecordRepository.count());
        JsonNode transferDetail = getEventDetail(sourceEventId).get("resourceTransfers").get(0);
        Assertions.assertFalse(transferDetail.get("reversed").asBoolean());
        Assertions.assertEquals(List.of("PR-302", "PR-303"),
                codeList(transferDetail.get("remainingResourceCodes")));
    }

    @Test
    void failedPartialReversalLeavesEventsResourcesAndRecordsUntouched() throws Exception {
        Long keep = createResource("PR-401");
        Long first = createResource("PR-402");
        Long second = createResource("PR-403");
        Long targetOwn = createResource("PR-404");
        Long thirdOwn = createResource("PR-405");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        Long thirdEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(keep, first, second));
        dispatchEvent(targetEventId, List.of(targetOwn));
        dispatchEvent(thirdEventId, List.of(thirdOwn));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId,
                List.of("PR-402", "PR-403"), newRequestNo(), 200);
        Long transferId = transferResponse.get("id").asLong();
        transfer(targetEventId, thirdEventId, List.of("PR-403"), newRequestNo(), 200);

        JsonNode targetBefore = getEventDetail(targetEventId);
        JsonNode sourceBefore = getEventDetail(sourceEventId);

        reverse(transferId, List.of("PR-402", "PR-403"), newRequestNo(), 409);

        Assertions.assertEquals(0, reversalRecordRepository.count());
        Assertions.assertEquals(2, transferRecordRepository.count());
        assertEventResourceIds(sourceEventId, List.of(keep));
        assertEventResourceIds(targetEventId, List.of(targetOwn, first));
        assertEventResourceIds(thirdEventId, List.of(thirdOwn, second));

        JsonNode targetAfter = getEventDetail(targetEventId);
        Assertions.assertEquals(targetBefore.get("resourceTransfers").toString(),
                targetAfter.get("resourceTransfers").toString());
        Assertions.assertEquals(targetBefore.get("resourceTransferTimeline").toString(),
                targetAfter.get("resourceTransferTimeline").toString());
        Assertions.assertEquals(sourceBefore.get("resourceTransferTimeline").toString(),
                getEventDetail(sourceEventId).get("resourceTransferTimeline").toString());
        JsonNode transferDetail = targetAfter.get("resourceTransfers").get(0);
        Assertions.assertFalse(transferDetail.get("reversed").asBoolean());
        Assertions.assertEquals(List.of("PR-402", "PR-403"),
                codeList(transferDetail.get("remainingResourceCodes")));
    }

    @Test
    void partialReversalRejectsWhenTargetEventWouldHaveNoResourceLeft() throws Exception {
        Long keep = createResource("PR-501");
        Long first = createResource("PR-502");
        Long second = createResource("PR-503");
        Long targetOwn = createResource("PR-504");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(keep, first, second));
        dispatchEvent(targetEventId, List.of(targetOwn));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId,
                List.of("PR-502", "PR-503"), newRequestNo(), 200);
        Long transferId = transferResponse.get("id").asLong();
        transfer(targetEventId, sourceEventId, List.of("PR-504"), newRequestNo(), 200);

        reverse(transferId, List.of("PR-502", "PR-503"), newRequestNo(), 409);

        assertEventResourceIds(sourceEventId, List.of(keep, targetOwn));
        assertEventResourceIds(targetEventId, List.of(first, second));
        Assertions.assertEquals(0, reversalRecordRepository.count());

        reverse(transferId, List.of("PR-502"), newRequestNo(), 200);
        assertEventResourceIds(sourceEventId, List.of(keep, targetOwn, first));
        assertEventResourceIds(targetEventId, List.of(second));
        Assertions.assertEquals(1, reversalRecordRepository.count());
    }

    @Test
    void idempotentPartialReversalRetryAndConflictOnDifferentResourceSet() throws Exception {
        Long keep = createResource("PR-601");
        Long first = createResource("PR-602");
        Long second = createResource("PR-603");
        Long targetOwn = createResource("PR-604");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(keep, first, second));
        dispatchEvent(targetEventId, List.of(targetOwn));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId,
                List.of("PR-602", "PR-603"), newRequestNo(), 200);
        Long transferId = transferResponse.get("id").asLong();

        String requestNo = newRequestNo();
        JsonNode firstResponse = reverse(transferId, List.of("PR-602"), requestNo, 200);
        JsonNode retryResponse = reverse(transferId, List.of(" pr-602 "), requestNo, 200);
        Assertions.assertEquals(firstResponse.get("id").asLong(), retryResponse.get("id").asLong());
        Assertions.assertEquals(firstResponse.get("operatedAt").asText(),
                retryResponse.get("operatedAt").asText());

        reverse(transferId, List.of("PR-603"), requestNo, 409);
        reverse(transferId, List.of("PR-602", "PR-603"), requestNo, 409);

        Assertions.assertEquals(1, reversalRecordRepository.count());
        assertEventResourceIds(sourceEventId, List.of(keep, first));
        assertEventResourceIds(targetEventId, List.of(targetOwn, second));
    }

    @Test
    void concurrentOverlappingReversalsDoNotReturnSameResourceTwice() throws Exception {
        Long keep = createResource("PR-701");
        Long first = createResource("PR-702");
        Long second = createResource("PR-703");
        Long third = createResource("PR-704");
        Long targetOwn = createResource("PR-705");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(keep, first, second, third));
        dispatchEvent(targetEventId, List.of(targetOwn));

        JsonNode transferResponse = transfer(sourceEventId, targetEventId,
                List.of("PR-702", "PR-703", "PR-704"), newRequestNo(), 200);
        Long transferId = transferResponse.get("id").asLong();

        List<List<String>> selections = List.of(
                List.of("PR-702", "PR-703"),
                List.of("PR-703", "PR-704"));
        CyclicBarrier barrier = new CyclicBarrier(selections.size());
        ExecutorService pool = Executors.newFixedThreadPool(selections.size());
        List<Future<Integer>> futures = new ArrayList<>();
        for (List<String> selection : selections) {
            futures.add(pool.submit(() -> {
                Map<String, Object> request = new HashMap<>();
                request.put("requestNo", newRequestNo());
                request.put("resourceCodes", selection);
                request.put("operator", "王五");
                request.put("reason", "并发部分撤销，范围重叠");
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
        Assertions.assertEquals(1, conflict);
        Assertions.assertEquals(1, reversalRecordRepository.count());

        Set<Long> sourceResourceIds = new HashSet<>();
        for (JsonNode resource : getEventDetail(sourceEventId).get("resources")) {
            sourceResourceIds.add(resource.get("id").asLong());
        }
        Assertions.assertTrue(sourceResourceIds.contains(keep));
        Assertions.assertTrue(sourceResourceIds.contains(second));
        Assertions.assertEquals(3, sourceResourceIds.size());

        Set<Long> targetResourceIds = new HashSet<>();
        for (JsonNode resource : getEventDetail(targetEventId).get("resources")) {
            targetResourceIds.add(resource.get("id").asLong());
        }
        Assertions.assertTrue(targetResourceIds.contains(targetOwn));
        Assertions.assertFalse(targetResourceIds.contains(second));
        Assertions.assertEquals(2, targetResourceIds.size());
    }
}
