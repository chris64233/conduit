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
class EmergencyDispatchControllerTest {

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
        segmentId = createSegment("ACTIVE");
    }

    @AfterEach
    void tearDown() {
        burstEventRepository.deleteAll();
        resourceRepository.deleteAll();
    }

    private Long createSegment(String status) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("code", "WS-" + status + "-" + System.nanoTime());
        request.put("name", "城东供水主干管");
        request.put("utilityType", "WATER");
        request.put("material", "球墨铸铁");
        request.put("diameterMm", 300);
        request.put("startLongitude", 116.40);
        request.put("startLatitude", 39.90);
        request.put("endLongitude", 116.41);
        request.put("endLatitude", 39.91);
        request.put("status", status);
        MvcResult result = mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Map<String, Object> validResourceRequest(String code) {
        Map<String, Object> request = new HashMap<>();
        request.put("code", code);
        request.put("name", "应急抢修一队");
        request.put("type", "TEAM");
        return request;
    }

    private Long createResource(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/emergency-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validResourceRequest(code))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Map<String, Object> validEventRequest(Long pipeSegmentId) {
        Map<String, Object> request = new HashMap<>();
        request.put("pipeSegmentId", pipeSegmentId);
        request.put("description", "主干管爆裂，路面大量积水");
        request.put("level", "CRITICAL");
        request.put("reporter", "张三");
        return request;
    }

    private Long createEvent() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/burst-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEventRequest(segmentId))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void transitionEvent(Long eventId, String targetStatus, List<Long> resourceIds,
                                 String resolution, int expectedStatus) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("targetStatus", targetStatus);
        request.put("resourceIds", resourceIds);
        request.put("resolution", resolution);
        mockMvc.perform(post("/api/burst-events/{id}/transitions", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void createResourceReturns201WithAvailableStatus() throws Exception {
        mockMvc.perform(post("/api/emergency-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validResourceRequest("RES-001"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value("RES-001"))
                .andExpect(jsonPath("$.name").value("应急抢修一队"))
                .andExpect(jsonPath("$.type").value("TEAM"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void createResourceWithInvalidParamsReturns400() throws Exception {
        Map<String, Object> request = validResourceRequest("RES-002");
        request.put("name", "  ");
        mockMvc.perform(post("/api/emergency-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("name 不能为空")));

        request = validResourceRequest("RES-002");
        request.remove("type");
        mockMvc.perform(post("/api/emergency-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("type 不能为空")));

        request = validResourceRequest("RES-002");
        request.put("type", "DRONE");
        mockMvc.perform(post("/api/emergency-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createResourceWithDuplicateCodeReturns409() throws Exception {
        createResource("RES-003");
        mockMvc.perform(post("/api/emergency-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validResourceRequest("res-003"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("应急资源编号已存在")));
    }

    @Test
    void createEventReturns201WithReportedStatus() throws Exception {
        mockMvc.perform(post("/api/burst-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEventRequest(segmentId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.pipeSegmentId").value(segmentId))
                .andExpect(jsonPath("$.description").value("主干管爆裂，路面大量积水"))
                .andExpect(jsonPath("$.level").value("CRITICAL"))
                .andExpect(jsonPath("$.reporter").value("张三"))
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.resources", hasSize(0)));
    }

    @Test
    void createEventWithMissingSegmentReturns404() throws Exception {
        mockMvc.perform(post("/api/burst-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEventRequest(999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("管段不存在")));
    }

    @Test
    void createEventWithOutOfServiceSegmentReturns409() throws Exception {
        Long outOfServiceSegmentId = createSegment("OUT_OF_SERVICE");
        mockMvc.perform(post("/api/burst-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validEventRequest(outOfServiceSegmentId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("管段已停用")));
    }

    @Test
    void createEventWithInvalidParamsReturns400() throws Exception {
        Map<String, Object> request = validEventRequest(segmentId);
        request.put("description", " ");
        mockMvc.perform(post("/api/burst-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("description 不能为空")));

        request = validEventRequest(segmentId);
        request.remove("level");
        mockMvc.perform(post("/api/burst-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("level 不能为空")));

        request = validEventRequest(segmentId);
        request.put("level", "SEVERE");
        mockMvc.perform(post("/api/burst-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        request = validEventRequest(segmentId);
        request.put("reporter", "");
        mockMvc.perform(post("/api/burst-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("reporter 不能为空")));
    }

    @Test
    void dispatchMarksResourcesBusyAndLinksThemToEvent() throws Exception {
        Long teamId = createResource("RES-101");
        Long vehicleId = createResource("RES-102");
        Long eventId = createEvent();

        transitionEvent(eventId, "DISPATCHED", List.of(teamId, vehicleId), null, 200);

        mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.resources[0].id").value(teamId))
                .andExpect(jsonPath("$.resources[0].status").value("BUSY"))
                .andExpect(jsonPath("$.resources[1].id").value(vehicleId))
                .andExpect(jsonPath("$.resources[1].status").value("BUSY"));

        mockMvc.perform(get("/api/emergency-resources/{id}", teamId))
                .andExpect(jsonPath("$.status").value("BUSY"));
    }

    @Test
    void dispatchWithoutResourcesReturns400() throws Exception {
        Long eventId = createEvent();
        transitionEvent(eventId, "DISPATCHED", null, null, 400);
        transitionEvent(eventId, "DISPATCHED", List.of(), null, 400);
    }

    @Test
    void dispatchWithMissingResourceReturns404() throws Exception {
        Long eventId = createEvent();
        transitionEvent(eventId, "DISPATCHED", List.of(999L), null, 404);
    }

    @Test
    void dispatchWithBusyResourceReturns409AndDoesNotPartiallyUpdate() throws Exception {
        Long busyResourceId = createResource("RES-201");
        Long freeResourceId = createResource("RES-202");
        Long firstEventId = createEvent();
        Long secondEventId = createEvent();

        transitionEvent(firstEventId, "DISPATCHED", List.of(busyResourceId), null, 200);

        transitionEvent(secondEventId, "DISPATCHED", List.of(freeResourceId, busyResourceId), null, 409);

        mockMvc.perform(get("/api/burst-events/{id}", secondEventId))
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.resources", hasSize(0)));
        mockMvc.perform(get("/api/emergency-resources/{id}", freeResourceId))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void resolveReleasesResourcesAndStoresResolution() throws Exception {
        Long teamId = createResource("RES-301");
        Long eventId = createEvent();
        transitionEvent(eventId, "DISPATCHED", List.of(teamId), null, 200);

        transitionEvent(eventId, "RESOLVED", null, "已更换爆裂管段并恢复供水", 200);

        mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("已更换爆裂管段并恢复供水"))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].status").value("AVAILABLE"));
        mockMvc.perform(get("/api/emergency-resources/{id}", teamId))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void resolveWithoutResolutionReturns400() throws Exception {
        Long eventId = createEvent();
        transitionEvent(eventId, "DISPATCHED", List.of(createResource("RES-401")), null, 200);
        transitionEvent(eventId, "RESOLVED", null, null, 400);
        transitionEvent(eventId, "RESOLVED", null, "  ", 400);
    }

    @Test
    void illegalTransitionsReturn409() throws Exception {
        Long eventId = createEvent();
        transitionEvent(eventId, "RESOLVED", null, "直接完成", 409);

        transitionEvent(eventId, "DISPATCHED", List.of(createResource("RES-501")), null, 200);
        transitionEvent(eventId, "REPORTED", null, null, 409);
        transitionEvent(eventId, "DISPATCHED", List.of(createResource("RES-502")), null, 409);
    }

    @Test
    void resolvedEventCannotBeModified() throws Exception {
        Long eventId = createEvent();
        transitionEvent(eventId, "DISPATCHED", List.of(createResource("RES-601")), null, 200);
        transitionEvent(eventId, "RESOLVED", null, "已修复", 200);

        transitionEvent(eventId, "DISPATCHED", List.of(createResource("RES-602")), null, 409);
        transitionEvent(eventId, "REPORTED", null, null, 409);
        transitionEvent(eventId, "RESOLVED", null, "重复处理", 409);

        mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("已修复"));
    }

    @Test
    void getByIdReturnsEventWithAssignedResources() throws Exception {
        Long teamId = createResource("RES-701");
        Long eventId = createEvent();
        transitionEvent(eventId, "DISPATCHED", List.of(teamId), null, 200);

        mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId))
                .andExpect(jsonPath("$.pipeSegmentId").value(segmentId))
                .andExpect(jsonPath("$.level").value("CRITICAL"))
                .andExpect(jsonPath("$.resources", hasSize(1)))
                .andExpect(jsonPath("$.resources[0].code").value("RES-701"))
                .andExpect(jsonPath("$.resources[0].type").value("TEAM"));
    }

    @Test
    void getByIdWithMissingEventReturns404() throws Exception {
        mockMvc.perform(get("/api/burst-events/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("爆管事件不存在")));
    }

    @Test
    void transitionWithMissingEventReturns404() throws Exception {
        transitionEvent(999L, "DISPATCHED", List.of(createResource("RES-801")), null, 404);
    }
}
