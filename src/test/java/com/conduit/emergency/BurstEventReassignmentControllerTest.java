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
class BurstEventReassignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BurstEventRepository burstEventRepository;

    @Autowired
    private BurstEventReassignmentRepository reassignmentRepository;

    @Autowired
    private EmergencyResourceRepository resourceRepository;

    @Autowired
    private PipeSegmentRepository pipeSegmentRepository;

    private Long segmentId;

    @BeforeEach
    void setUp() throws Exception {
        reassignmentRepository.deleteAll();
        burstEventRepository.deleteAll();
        resourceRepository.deleteAll();
        pipeSegmentRepository.deleteAll();
        segmentId = createSegment();
    }

    @AfterEach
    void tearDown() {
        reassignmentRepository.deleteAll();
        burstEventRepository.deleteAll();
        resourceRepository.deleteAll();
    }

    private Long createSegment() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("code", "WS-REASSIGN-" + System.nanoTime());
        request.put("name", "城东供水主干管");
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

    private Long createDispatchedEvent(List<Long> resourceIds) throws Exception {
        Long eventId = createEvent();
        Map<String, Object> request = new HashMap<>();
        request.put("targetStatus", "DISPATCHED");
        request.put("resourceIds", resourceIds);
        mockMvc.perform(post("/api/burst-events/{id}/transitions", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        return eventId;
    }

    private Map<String, Object> reassignRequest(List<Long> resourceIds, String operator, String reason) {
        Map<String, Object> request = new HashMap<>();
        request.put("resourceIds", resourceIds);
        request.put("operator", operator);
        request.put("reason", reason);
        return request;
    }

    private void reassign(Long eventId, List<Long> resourceIds, String operator, String reason,
                          int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/burst-events/{id}/reassignment", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                reassignRequest(resourceIds, operator, reason))))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void reassignReplacesResourcesAndReturnsAuditRecord() throws Exception {
        Long teamA = createResource("RES-A01");
        Long teamB = createResource("RES-A02");
        Long teamC = createResource("RES-A03");
        Long eventId = createDispatchedEvent(List.of(teamA, teamB));

        mockMvc.perform(post("/api/burst-events/{id}/reassignment", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                reassignRequest(List.of(teamB, teamC), "李四", "一队支援其他现场"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.resources[0].id").value(teamB))
                .andExpect(jsonPath("$.resources[0].status").value("BUSY"))
                .andExpect(jsonPath("$.resources[1].id").value(teamC))
                .andExpect(jsonPath("$.resources[1].status").value("BUSY"))
                .andExpect(jsonPath("$.reassignments", hasSize(1)))
                .andExpect(jsonPath("$.reassignments[0].operator").value("李四"))
                .andExpect(jsonPath("$.reassignments[0].reason").value("一队支援其他现场"))
                .andExpect(jsonPath("$.reassignments[0].operatedAt").isNotEmpty())
                .andExpect(jsonPath("$.reassignments[0].previousResourceIds", hasSize(2)))
                .andExpect(jsonPath("$.reassignments[0].previousResourceIds[0]").value(teamA))
                .andExpect(jsonPath("$.reassignments[0].previousResourceIds[1]").value(teamB))
                .andExpect(jsonPath("$.reassignments[0].newResourceIds", hasSize(2)))
                .andExpect(jsonPath("$.reassignments[0].newResourceIds[0]").value(teamB))
                .andExpect(jsonPath("$.reassignments[0].newResourceIds[1]").value(teamC));
    }

    @Test
    void reassignKeepsRetainedResourcesBusyAndReleasesRemovedOnes() throws Exception {
        Long retained = createResource("RES-B01");
        Long removed = createResource("RES-B02");
        Long eventId = createDispatchedEvent(List.of(retained, removed));

        reassign(eventId, List.of(retained), "李四", "缩减现场人力", 200);

        mockMvc.perform(get("/api/emergency-resources/{id}", retained))
                .andExpect(jsonPath("$.status").value("BUSY"));
        mockMvc.perform(get("/api/emergency-resources/{id}", removed))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].id").value(retained));
    }

    @Test
    void reassignMarksNewlyAddedResourcesBusy() throws Exception {
        Long existing = createResource("RES-C01");
        Long added = createResource("RES-C02");
        Long eventId = createDispatchedEvent(List.of(existing));

        reassign(eventId, List.of(existing, added), "李四", "增援抢修力量", 200);

        mockMvc.perform(get("/api/emergency-resources/{id}", added))
                .andExpect(jsonPath("$.status").value("BUSY"));
    }

    @Test
    void reassignTreatsDuplicateResourceIdsAsOne() throws Exception {
        Long teamA = createResource("RES-D01");
        Long teamB = createResource("RES-D02");
        Long eventId = createDispatchedEvent(List.of(teamA));

        mockMvc.perform(post("/api/burst-events/{id}/reassignment", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                reassignRequest(List.of(teamB, teamB, teamA, teamB), "李四", "重复编号"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.reassignments[0].newResourceIds", hasSize(2)))
                .andExpect(jsonPath("$.reassignments[0].newResourceIds[0]").value(teamB))
                .andExpect(jsonPath("$.reassignments[0].newResourceIds[1]").value(teamA));
    }

    @Test
    void reassignWithMissingEventReturns404() throws Exception {
        reassign(999L, List.of(createResource("RES-E01")), "李四", "事件不存在", 404);
    }

    @Test
    void reassignWithMissingResourceReturns404() throws Exception {
        Long eventId = createDispatchedEvent(List.of(createResource("RES-F01")));
        reassign(eventId, List.of(999L), "李四", "资源不存在", 404);
    }

    @Test
    void reassignWithBusyResourceReturns409() throws Exception {
        Long busyResource = createResource("RES-G01");
        createDispatchedEvent(List.of(busyResource));
        Long freeResource = createResource("RES-G02");
        Long eventId = createDispatchedEvent(List.of(freeResource));

        reassign(eventId, List.of(busyResource), "李四", "资源被其他事件占用", 409);
    }

    @Test
    void reassignWithInvalidEventStatusReturns409() throws Exception {
        Long reportedEventId = createEvent();
        reassign(reportedEventId, List.of(createResource("RES-H01")), "李四", "未派发不能改派", 409);

        Long resolvedEventId = createDispatchedEvent(List.of(createResource("RES-H02")));
        Map<String, Object> resolveRequest = new HashMap<>();
        resolveRequest.put("targetStatus", "RESOLVED");
        resolveRequest.put("resolution", "已修复");
        mockMvc.perform(post("/api/burst-events/{id}/transitions", resolvedEventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveRequest)))
                .andExpect(status().isOk());
        reassign(resolvedEventId, List.of(createResource("RES-H03")), "李四", "已完成不能改派", 409);
    }

    @Test
    void reassignWithInvalidParamsReturns400() throws Exception {
        Long eventId = createDispatchedEvent(List.of(createResource("RES-I01")));

        reassign(eventId, null, "李四", "缺少资源列表", 400);
        reassign(eventId, List.of(), "李四", "空资源列表", 400);
        reassign(eventId, List.of(createResource("RES-I02")), null, "缺少操作人", 400);
        reassign(eventId, List.of(createResource("RES-I03")), "  ", "操作人为空白", 400);
        reassign(eventId, List.of(createResource("RES-I04")), "李四", null, 400);
        reassign(eventId, List.of(createResource("RES-I05")), "李四", " ", 400);
    }

    @Test
    void failedReassignDoesNotPartiallyUpdate() throws Exception {
        Long retained = createResource("RES-J01");
        Long removed = createResource("RES-J02");
        Long eventId = createDispatchedEvent(List.of(retained, removed));
        Long busyElsewhere = createResource("RES-J03");
        createDispatchedEvent(List.of(busyElsewhere));
        Long freeResource = createResource("RES-J04");

        reassign(eventId, List.of(retained, freeResource, busyElsewhere), "李四", "包含不可用资源", 409);

        mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.resources[0].id").value(retained))
                .andExpect(jsonPath("$.resources[1].id").value(removed))
                .andExpect(jsonPath("$.reassignments", hasSize(0)));
        mockMvc.perform(get("/api/emergency-resources/{id}", removed))
                .andExpect(jsonPath("$.status").value("BUSY"));
        mockMvc.perform(get("/api/emergency-resources/{id}", freeResource))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void eventDetailReturnsReassignmentsInChronologicalOrder() throws Exception {
        Long teamA = createResource("RES-K01");
        Long teamB = createResource("RES-K02");
        Long teamC = createResource("RES-K03");
        Long eventId = createDispatchedEvent(List.of(teamA));

        reassign(eventId, List.of(teamA, teamB), "李四", "第一次改派", 200);
        reassign(eventId, List.of(teamC), "王五", "第二次改派", 200);

        mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].id").value(teamC))
                .andExpect(jsonPath("$.reassignments", hasSize(2)))
                .andExpect(jsonPath("$.reassignments[0].operator").value("李四"))
                .andExpect(jsonPath("$.reassignments[0].reason").value("第一次改派"))
                .andExpect(jsonPath("$.reassignments[0].operatedAt").isNotEmpty())
                .andExpect(jsonPath("$.reassignments[0].previousResourceIds", hasSize(1)))
                .andExpect(jsonPath("$.reassignments[0].previousResourceIds[0]").value(teamA))
                .andExpect(jsonPath("$.reassignments[0].newResourceIds", hasSize(2)))
                .andExpect(jsonPath("$.reassignments[0].newResourceIds[0]").value(teamA))
                .andExpect(jsonPath("$.reassignments[0].newResourceIds[1]").value(teamB))
                .andExpect(jsonPath("$.reassignments[1].operator").value("王五"))
                .andExpect(jsonPath("$.reassignments[1].reason").value("第二次改派"))
                .andExpect(jsonPath("$.reassignments[1].previousResourceIds", hasSize(2)))
                .andExpect(jsonPath("$.reassignments[1].previousResourceIds[0]").value(teamA))
                .andExpect(jsonPath("$.reassignments[1].previousResourceIds[1]").value(teamB))
                .andExpect(jsonPath("$.reassignments[1].newResourceIds", hasSize(1)))
                .andExpect(jsonPath("$.reassignments[1].newResourceIds[0]").value(teamC));
    }
}
