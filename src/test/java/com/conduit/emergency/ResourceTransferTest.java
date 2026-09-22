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
class ResourceTransferTest {

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
    private PipeSegmentRepository pipeSegmentRepository;

    private Long segmentId;

    @BeforeEach
    void setUp() throws Exception {
        transferRecordRepository.deleteAll();
        burstEventRepository.deleteAll();
        resourceRepository.deleteAll();
        pipeSegmentRepository.deleteAll();
        segmentId = createSegment();
    }

    @AfterEach
    void tearDown() {
        transferRecordRepository.deleteAll();
        burstEventRepository.deleteAll();
        resourceRepository.deleteAll();
        pipeSegmentRepository.deleteAll();
    }

    private Long createSegment() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("code", "WS-TRANSFER-" + System.nanoTime());
        request.put("name", "城东供水支管");
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

    private String newRequestNo() {
        return "TR-" + UUID.randomUUID();
    }

    private void assertEventResourceIds(Long eventId, List<Long> expectedResourceIds) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode resources = objectMapper.readTree(result.getResponse().getContentAsString()).get("resources");
        Assertions.assertEquals(expectedResourceIds.size(), resources.size());
        for (int i = 0; i < expectedResourceIds.size(); i++) {
            Assertions.assertEquals(expectedResourceIds.get(i), resources.get(i).get("id").asLong());
        }
    }

    @Test
    void transferMovesBusyResourcesBetweenDispatchedEvents() throws Exception {
        Long first = createResource("RT-001");
        Long second = createResource("RT-002");
        Long third = createResource("RT-003");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetEventId, List.of(third));

        JsonNode response = transfer(sourceEventId, targetEventId, List.of("rt-002"),
                newRequestNo(), "李四", "目标事件需要增援", 200);

        Assertions.assertEquals(sourceEventId, response.get("sourceEventId").asLong());
        Assertions.assertEquals(targetEventId, response.get("targetEventId").asLong());
        Assertions.assertEquals("RT-002", response.get("resourceCodes").get(0).asText());
        Assertions.assertEquals("李四", response.get("operator").asText());
        Assertions.assertEquals("目标事件需要增援", response.get("reason").asText());
        Assertions.assertNotNull(response.get("id"));
        Assertions.assertNotNull(response.get("requestNo"));
        Assertions.assertNotNull(response.get("operatedAt").asText());

        assertEventResourceIds(sourceEventId, List.of(first));
        assertEventResourceIds(targetEventId, List.of(second, third));

        mockMvc.perform(get("/api/emergency-resources/{id}", second))
                .andExpect(jsonPath("$.status").value("BUSY"));
    }

    @Test
    void transferTreatsDuplicateResourceCodesAsOne() throws Exception {
        Long first = createResource("RT-101");
        Long second = createResource("RT-102");
        Long third = createResource("RT-103");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetEventId, List.of(third));

        JsonNode response = transfer(sourceEventId, targetEventId,
                List.of("RT-102", "rt-102", " RT-102 "), newRequestNo(), "李四", "重复编号去重", 200);

        Assertions.assertEquals(1, response.get("resourceCodes").size());
        mockMvc.perform(get("/api/burst-events/{id}", sourceEventId))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].id").value(first));
        mockMvc.perform(get("/api/burst-events/{id}", targetEventId))
                .andExpect(jsonPath("$.resources", hasSize(2)));
        Assertions.assertEquals(1, transferRecordRepository.count());
    }

    @Test
    void transferRejectsWhenSourceEventWouldHaveNoResourceLeft() throws Exception {
        Long only = createResource("RT-201");
        Long targetOwned = createResource("RT-202");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(only));
        dispatchEvent(targetEventId, List.of(targetOwned));

        transfer(sourceEventId, targetEventId, List.of("RT-201"),
                newRequestNo(), "李四", "试图掏空源事件", 409);

        assertEventResourceIds(sourceEventId, List.of(only));
        assertEventResourceIds(targetEventId, List.of(targetOwned));
        Assertions.assertEquals(0, transferRecordRepository.count());
    }

    @Test
    void transferRejectsResourceNotOwnedBySourceEventOrWrongStatus() throws Exception {
        Long sourceOwned = createResource("RT-301");
        Long targetOwned = createResource("RT-302");
        Long free = createResource("RT-303");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(sourceOwned));
        dispatchEvent(targetEventId, List.of(targetOwned));

        transfer(sourceEventId, targetEventId, List.of("RT-302"),
                newRequestNo(), "李四", "资源属于目标事件", 409);
        transfer(sourceEventId, targetEventId, List.of("RT-303"),
                newRequestNo(), "李四", "资源处于 AVAILABLE", 409);

        mockMvc.perform(get("/api/emergency-resources/{id}", free))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        assertEventResourceIds(sourceEventId, List.of(sourceOwned));
        assertEventResourceIds(targetEventId, List.of(targetOwned));
        Assertions.assertEquals(0, transferRecordRepository.count());
    }

    @Test
    void transferRejectsNonDispatchedEventsAndUnknownReferences() throws Exception {
        Long sourceOwned = createResource("RT-401");
        Long targetOwned = createResource("RT-402");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(sourceOwned));
        dispatchEvent(targetEventId, List.of(targetOwned));

        Long reportedEventId = createEvent();
        transfer(reportedEventId, targetEventId, List.of("RT-402"),
                newRequestNo(), "李四", "源事件未派发", 409);
        transfer(sourceEventId, reportedEventId, List.of("RT-401"),
                newRequestNo(), "李四", "目标事件未派发", 409);

        resolveEvent(targetEventId);
        transfer(sourceEventId, targetEventId, List.of("RT-401"),
                newRequestNo(), "李四", "目标事件已完成", 409);
        resolveEvent(sourceEventId);
        Long anotherTarget = createEvent();
        transfer(sourceEventId, anotherTarget, List.of("RT-401"),
                newRequestNo(), "李四", "源事件已完成", 409);

        Long dispatchedEventId = createEvent();
        Long dispatchedResource = createResource("RT-403");
        dispatchEvent(dispatchedEventId, List.of(dispatchedResource));
        Long anotherTargetEvent = createEvent();
        Long anotherResource = createResource("RT-404");
        dispatchEvent(anotherTargetEvent, List.of(anotherResource));
        transfer(999L, anotherTargetEvent, List.of("RT-404"),
                newRequestNo(), "李四", "源事件不存在", 404);
        transfer(dispatchedEventId, 998L, List.of("RT-403"),
                newRequestNo(), "李四", "目标事件不存在", 404);
        transfer(dispatchedEventId, anotherTargetEvent, List.of("RT-999"),
                newRequestNo(), "李四", "资源编号不存在", 404);
    }

    @Test
    void failedTransferRollsBackEventsAndAudit() throws Exception {
        Long moved = createResource("RT-501");
        Long kept = createResource("RT-502");
        Long targetOwned = createResource("RT-503");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(moved, kept));
        dispatchEvent(targetEventId, List.of(targetOwned));

        transfer(sourceEventId, targetEventId, List.of("RT-501", "RT-999"),
                newRequestNo(), "李四", "含不存在资源", 404);
        transfer(sourceEventId, targetEventId, List.of("RT-501", "RT-503"),
                newRequestNo(), "李四", "含目标事件已有资源", 409);

        mockMvc.perform(get("/api/burst-events/{id}", sourceEventId))
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.resourceTransfers", hasSize(0)));
        mockMvc.perform(get("/api/burst-events/{id}", targetEventId))
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resourceTransfers", hasSize(0)));
        mockMvc.perform(get("/api/emergency-resources/{id}", moved))
                .andExpect(jsonPath("$.status").value("BUSY"));
        Assertions.assertEquals(0, transferRecordRepository.count());
    }

    @Test
    void idempotentRetryReturnsFirstResultWithoutDuplicateTransferOrAudit() throws Exception {
        Long first = createResource("RT-601");
        Long second = createResource("RT-602");
        Long third = createResource("RT-603");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetEventId, List.of(third));

        String requestNo = newRequestNo();
        JsonNode firstResponse = transfer(sourceEventId, targetEventId, List.of("RT-602"),
                requestNo, "李四", "跨事件支援", 200);
        JsonNode retryResponse = transfer(sourceEventId, targetEventId, List.of("rt-602"),
                requestNo, " 李四 ", " 跨事件支援 ", 200);

        Assertions.assertEquals(firstResponse.get("id").asLong(), retryResponse.get("id").asLong());
        Assertions.assertEquals(firstResponse.get("operatedAt").asText(),
                retryResponse.get("operatedAt").asText());
        Assertions.assertEquals(1, transferRecordRepository.count());
        assertEventResourceIds(sourceEventId, List.of(first));
        assertEventResourceIds(targetEventId, List.of(second, third));
    }

    @Test
    void sameRequestNoWithDifferentContentReturnsConflict() throws Exception {
        Long first = createResource("RT-701");
        Long second = createResource("RT-702");
        Long third = createResource("RT-703");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetEventId, List.of(third));

        String requestNo = newRequestNo();
        transfer(sourceEventId, targetEventId, List.of("RT-702"),
                requestNo, "李四", "首次请求", 200);

        transfer(sourceEventId, targetEventId, List.of("RT-701"),
                requestNo, "李四", "更换资源编号", 409);
        transfer(sourceEventId, targetEventId, List.of("RT-702"),
                requestNo, "王五", "更换操作人", 409);
        transfer(targetEventId, sourceEventId, List.of("RT-702"),
                requestNo, "李四", "交换源和目标", 409);

        Assertions.assertEquals(1, transferRecordRepository.count());
        assertEventResourceIds(sourceEventId, List.of(first));
        assertEventResourceIds(targetEventId, List.of(second, third));
    }

    @Test
    void concurrentTransfersOfSameResourceOnlyOneSucceeds() throws Exception {
        Long first = createResource("RT-801");
        Long second = createResource("RT-802");
        Long sourceEventId = createEvent();
        Long targetA = createEvent();
        Long targetB = createEvent();
        dispatchEvent(sourceEventId, List.of(first, second));
        dispatchEvent(targetA, List.of(createResource("RT-803")));
        dispatchEvent(targetB, List.of(createResource("RT-804")));

        int threads = 4;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final Long targetEventId = i % 2 == 0 ? targetA : targetB;
            futures.add(pool.submit(() -> {
                Map<String, Object> request = new HashMap<>();
                request.put("sourceEventId", sourceEventId);
                request.put("targetEventId", targetEventId);
                request.put("resourceCodes", List.of("RT-801"));
                request.put("requestNo", newRequestNo());
                request.put("operator", "李四");
                request.put("reason", "并发竞争同一资源");
                barrier.await();
                return mockMvc.perform(post("/api/resource-transfers")
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
        Assertions.assertEquals(1, transferRecordRepository.count());
        assertEventResourceIds(sourceEventId, List.of(second));
    }

    @Test
    void eventDetailReturnsTransferRecordsInChronologicalOrder() throws Exception {
        Long first = createResource("RT-901");
        Long second = createResource("RT-902");
        Long third = createResource("RT-903");
        Long fourth = createResource("RT-904");
        Long fifth = createResource("RT-905");
        Long eventA = createEvent();
        Long eventB = createEvent();
        dispatchEvent(eventA, List.of(first, second, third));
        dispatchEvent(eventB, List.of(fourth, fifth));

        transfer(eventA, eventB, List.of("RT-901"), newRequestNo(), "李四", "第一次转出", 200);
        transfer(eventB, eventA, List.of("RT-904"), newRequestNo(), "王五", "反向转入", 200);

        JsonNode detailA = getEventDetail(eventA);
        Assertions.assertEquals(2, detailA.get("resourceTransfers").size());
        Assertions.assertEquals("李四",
                detailA.get("resourceTransfers").get(0).get("operator").asText());
        Assertions.assertEquals("第一次转出",
                detailA.get("resourceTransfers").get(0).get("reason").asText());
        Assertions.assertEquals(eventA,
                detailA.get("resourceTransfers").get(0).get("sourceEventId").asLong());
        Assertions.assertEquals(eventB,
                detailA.get("resourceTransfers").get(0).get("targetEventId").asLong());
        Assertions.assertEquals("RT-901",
                detailA.get("resourceTransfers").get(0).get("resourceCodes").get(0).asText());
        Assertions.assertNotNull(
                detailA.get("resourceTransfers").get(0).get("operatedAt").asText());
        Assertions.assertEquals("王五",
                detailA.get("resourceTransfers").get(1).get("operator").asText());
        Assertions.assertEquals("反向转入",
                detailA.get("resourceTransfers").get(1).get("reason").asText());
        Assertions.assertEquals(eventB,
                detailA.get("resourceTransfers").get(1).get("sourceEventId").asLong());
        Assertions.assertEquals(eventA,
                detailA.get("resourceTransfers").get(1).get("targetEventId").asLong());
        Assertions.assertEquals("RT-904",
                detailA.get("resourceTransfers").get(1).get("resourceCodes").get(0).asText());

        JsonNode detailB = getEventDetail(eventB);
        Assertions.assertEquals(2, detailB.get("resourceTransfers").size());
        Assertions.assertEquals(3, detailA.get("resources").size());
        Assertions.assertEquals(2, detailB.get("resources").size());
    }

    @Test
    void transferWithInvalidParametersReturnsBadRequest() throws Exception {
        Long first = createResource("RT-1001");
        Long second = createResource("RT-1002");
        Long sourceEventId = createEvent();
        Long targetEventId = createEvent();
        dispatchEvent(sourceEventId, List.of(first));
        dispatchEvent(targetEventId, List.of(second));

        transfer(sourceEventId, targetEventId, null,
                newRequestNo(), "李四", "缺少资源列表", 400);
        transfer(sourceEventId, targetEventId, List.of(),
                newRequestNo(), "李四", "空资源列表", 400);
        transfer(sourceEventId, targetEventId, List.of(" "),
                newRequestNo(), "李四", "空白资源编号", 400);
        transfer(sourceEventId, targetEventId, List.of("RT-1001"),
                " ", "李四", "空白请求号", 400);
        transfer(sourceEventId, targetEventId, List.of("RT-1001"),
                newRequestNo(), " ", "空白操作人", 400);
        transfer(sourceEventId, targetEventId, List.of("RT-1001"),
                newRequestNo(), "李四", "  ", 400);
        transfer(sourceEventId, sourceEventId, List.of("RT-1001"),
                newRequestNo(), "李四", "源事件目标事件相同", 400);

        Assertions.assertEquals(0, transferRecordRepository.count());
    }

    private JsonNode getEventDetail(Long eventId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
