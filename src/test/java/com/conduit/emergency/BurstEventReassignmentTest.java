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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BurstEventReassignmentTest {

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

    private Long segmentId;

    @BeforeEach
    void setUp() throws Exception {
        burstEventRepository.deleteAll();
        resourceRepository.deleteAll();
        pipeSegmentRepository.deleteAll();
        segmentId = createSegment();
    }

    @AfterEach
    void tearDown() {
        burstEventRepository.deleteAll();
        resourceRepository.deleteAll();
    }

    private Long createSegment() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("code", "WS-REASSIGN-" + System.nanoTime());
        request.put("name", "城西供水主干管");
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

    private void reassignEvent(Long eventId, List<Long> resourceIds, String operator,
                               String reason, int expectedStatus) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("resourceIds", resourceIds);
        request.put("operator", operator);
        request.put("reason", reason);
        mockMvc.perform(post("/api/burst-events/{id}/reassignment", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void reassignReplacesResourcesAndKeepsEventDispatched() throws Exception {
        Long first = createResource("RA-101");
        Long second = createResource("RA-102");
        Long eventId = createEvent();
        dispatchEvent(eventId, List.of(first));

        reassignEvent(eventId, List.of(second), "李四", "一队另有任务，改派二队", 200);

        mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].id").value(second))
                .andExpect(jsonPath("$.resources[0].status").value("BUSY"));
    }

    @Test
    void reassignKeepsRetainedResourcesBusyAndReleasesRemovedOnes() throws Exception {
        Long kept = createResource("RA-201");
        Long removed = createResource("RA-202");
        Long added = createResource("RA-203");
        Long eventId = createEvent();
        dispatchEvent(eventId, List.of(kept, removed));

        reassignEvent(eventId, List.of(kept, added), "李四", "调整抢修力量", 200);

        mockMvc.perform(get("/api/emergency-resources/{id}", kept))
                .andExpect(jsonPath("$.status").value("BUSY"));
        mockMvc.perform(get("/api/emergency-resources/{id}", removed))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        mockMvc.perform(get("/api/emergency-resources/{id}", added))
                .andExpect(jsonPath("$.status").value("BUSY"));

        mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.resources[0].id").value(kept))
                .andExpect(jsonPath("$.resources[1].id").value(added));
    }

    @Test
    void reassignTreatsDuplicateResourceIdsAsOne() throws Exception {
        Long first = createResource("RA-301");
        Long second = createResource("RA-302");
        Long eventId = createEvent();
        dispatchEvent(eventId, List.of(first));

        reassignEvent(eventId, List.of(second, second, first, second), "李四", "重复编号去重", 200);

        mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.reassignments", hasSize(1)))
                .andExpect(jsonPath("$.reassignments[0].newResourceIds", hasSize(2)));
    }

    @Test
    void reassignWithMissingEventReturns404() throws Exception {
        reassignEvent(999L, List.of(createResource("RA-401")), "李四", "事件不存在", 404);
    }

    @Test
    void reassignWithMissingResourceReturns404() throws Exception {
        Long eventId = createEvent();
        dispatchEvent(eventId, List.of(createResource("RA-501")));

        reassignEvent(eventId, List.of(999L), "李四", "资源不存在", 404);
    }

    @Test
    void reassignWithUnavailableResourceReturns409() throws Exception {
        Long busyResource = createResource("RA-601");
        Long otherEventId = createEvent();
        dispatchEvent(otherEventId, List.of(busyResource));

        Long eventId = createEvent();
        dispatchEvent(eventId, List.of(createResource("RA-602")));

        reassignEvent(eventId, List.of(busyResource), "李四", "资源被其他事件占用", 409);
    }

    @Test
    void reassignOnNonDispatchedEventReturns409() throws Exception {
        Long reportedEventId = createEvent();
        reassignEvent(reportedEventId, List.of(createResource("RA-701")), "李四", "未派发不能改派", 409);

        Long resolvedEventId = createEvent();
        dispatchEvent(resolvedEventId, List.of(createResource("RA-702")));
        resolveEvent(resolvedEventId);
        reassignEvent(resolvedEventId, List.of(createResource("RA-703")), "李四", "已完成不能改派", 409);
    }

    @Test
    void reassignWithInvalidParamsReturns400() throws Exception {
        Long eventId = createEvent();
        dispatchEvent(eventId, List.of(createResource("RA-801")));

        reassignEvent(eventId, null, "李四", "缺少资源列表", 400);
        reassignEvent(eventId, List.of(), "李四", "资源列表为空", 400);
        reassignEvent(eventId, List.of(createResource("RA-802")), " ", "操作人为空", 400);
        reassignEvent(eventId, List.of(createResource("RA-803")), "李四", null, 400);
        reassignEvent(eventId, List.of(createResource("RA-804")), "李四", "  ", 400);
    }

    @Test
    void failedReassignDoesNotPartiallyUpdate() throws Exception {
        Long kept = createResource("RA-901");
        Long busyResource = createResource("RA-902");
        Long otherEventId = createEvent();
        dispatchEvent(otherEventId, List.of(busyResource));

        Long eventId = createEvent();
        dispatchEvent(eventId, List.of(kept));

        reassignEvent(eventId, List.of(createResource("RA-903"), busyResource), "李四", "含不可用资源", 409);

        mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].id").value(kept))
                .andExpect(jsonPath("$.reassignments", hasSize(0)));
        mockMvc.perform(get("/api/emergency-resources/{id}", kept))
                .andExpect(jsonPath("$.status").value("BUSY"));
        mockMvc.perform(get("/api/emergency-resources/{id}", busyResource))
                .andExpect(jsonPath("$.status").value("BUSY"));

        reassignEvent(eventId, List.of(kept, 999L), "李四", "含不存在资源", 404);
        mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].id").value(kept))
                .andExpect(jsonPath("$.reassignments", hasSize(0)));
    }

    @Test
    void eventDetailReturnsReassignmentRecordsInChronologicalOrder() throws Exception {
        Long first = createResource("RA-1001");
        Long second = createResource("RA-1002");
        Long third = createResource("RA-1003");
        Long eventId = createEvent();
        dispatchEvent(eventId, List.of(first));

        reassignEvent(eventId, List.of(first, second), "李四", "第一次改派", 200);
        reassignEvent(eventId, List.of(third), "王五", "第二次改派", 200);

        mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].id").value(third))
                .andExpect(jsonPath("$.reassignments", hasSize(2)))
                .andExpect(jsonPath("$.reassignments[0].operator").value("李四"))
                .andExpect(jsonPath("$.reassignments[0].reason").value("第一次改派"))
                .andExpect(jsonPath("$.reassignments[0].operatedAt").isNotEmpty())
                .andExpect(jsonPath("$.reassignments[0].previousResourceIds[0]").value(first))
                .andExpect(jsonPath("$.reassignments[0].newResourceIds", hasSize(2)))
                .andExpect(jsonPath("$.reassignments[0].newResourceIds[0]").value(first))
                .andExpect(jsonPath("$.reassignments[0].newResourceIds[1]").value(second))
                .andExpect(jsonPath("$.reassignments[1].operator").value("王五"))
                .andExpect(jsonPath("$.reassignments[1].reason").value("第二次改派"))
                .andExpect(jsonPath("$.reassignments[1].previousResourceIds", hasSize(2)))
                .andExpect(jsonPath("$.reassignments[1].newResourceIds", hasSize(1)))
                .andExpect(jsonPath("$.reassignments[1].newResourceIds[0]").value(third));
    }

    @Test
    void reassignResponseContainsAuditRecordWithoutJpaEntities() throws Exception {
        Long first = createResource("RA-1101");
        Long second = createResource("RA-1102");
        Long eventId = createEvent();
        dispatchEvent(eventId, List.of(first));

        Map<String, Object> request = new HashMap<>();
        request.put("resourceIds", List.of(second));
        request.put("operator", "  李四  ");
        request.put("reason", "  现场需要更大功率设备  ");
        mockMvc.perform(post("/api/burst-events/{id}/reassignment", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reassignments", hasSize(1)))
                .andExpect(jsonPath("$.reassignments[0].operator").value("李四"))
                .andExpect(jsonPath("$.reassignments[0].reason").value("现场需要更大功率设备"))
                .andExpect(jsonPath("$.reassignments[0].previousResourceIds[0]").value(first))
                .andExpect(jsonPath("$.reassignments[0].newResourceIds[0]").value(second));
    }
}
