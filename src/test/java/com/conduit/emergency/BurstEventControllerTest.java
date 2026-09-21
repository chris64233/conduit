package com.conduit.emergency;

import com.conduit.pipesegment.PipeSegmentRepository;
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
class BurstEventControllerTest {

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
        resourceRepository.deleteAll();
        burstEventRepository.deleteAll();
        pipeSegmentRepository.deleteAll();
        segmentId = createSegment("ACTIVE");
    }

    private Long createSegment(String segmentStatus) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("code", "WS-" + segmentStatus + "-" + System.nanoTime());
        request.put("name", "城东供水主干管");
        request.put("utilityType", "WATER");
        request.put("material", "球墨铸铁");
        request.put("diameterMm", 300);
        request.put("startLongitude", 116.40);
        request.put("startLatitude", 39.90);
        request.put("endLongitude", 116.41);
        request.put("endLatitude", 39.91);
        request.put("status", segmentStatus);
        MvcResult result = mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createResource(String code, String type) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("code", code);
        request.put("name", "应急资源-" + code);
        request.put("type", type);
        MvcResult result = mockMvc.perform(post("/api/emergency-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createEvent(Long pipeSegmentId) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("pipeSegmentId", pipeSegmentId);
        request.put("description", "路口供水主管爆裂，路面大量积水");
        request.put("level", "CRITICAL");
        request.put("reporter", "王五");
        MvcResult result = mockMvc.perform(post("/api/burst-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void transition(Long eventId, Map<String, Object> request, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/burst-events/{id}/transitions", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus));
    }

    private Map<String, Object> dispatchRequest(Long... resourceIds) {
        Map<String, Object> request = new HashMap<>();
        request.put("targetStatus", "DISPATCHED");
        request.put("resourceIds", List.of(resourceIds));
        return request;
    }

    @Test
    void registerResourceReturnsCreatedWithAvailableStatus() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("code", "TEAM-01");
        request.put("name", "抢修一队");
        request.put("type", "TEAM");
        mockMvc.perform(post("/api/emergency-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value("TEAM-01"))
                .andExpect(jsonPath("$.name").value("抢修一队"))
                .andExpect(jsonPath("$.type").value("TEAM"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void registerResourceWithDuplicateCodeReturnsConflict() throws Exception {
        createResource("VEH-01", "VEHICLE");
        Map<String, Object> request = new HashMap<>();
        request.put("code", "veh-01");
        request.put("name", "另一辆抢修车");
        request.put("type", "VEHICLE");
        mockMvc.perform(post("/api/emergency-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("资源编号已存在")));
    }

    @Test
    void registerResourceWithInvalidParamsReturnsBadRequest() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("code", "");
        request.put("type", "TEAM");
        mockMvc.perform(post("/api/emergency-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportEventReturnsCreatedWithReportedStatus() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("pipeSegmentId", segmentId);
        request.put("description", "管段接口处爆裂");
        request.put("level", "MAJOR");
        request.put("reporter", "张三");
        mockMvc.perform(post("/api/burst-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.pipeSegmentId").value(segmentId))
                .andExpect(jsonPath("$.level").value("MAJOR"))
                .andExpect(jsonPath("$.reporter").value("张三"))
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.resources", hasSize(0)));
    }

    @Test
    void reportEventWithMissingSegmentReturnsNotFound() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("pipeSegmentId", 999999L);
        request.put("description", "管段爆裂");
        request.put("level", "MINOR");
        request.put("reporter", "张三");
        mockMvc.perform(post("/api/burst-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("管段不存在")));
    }

    @Test
    void reportEventWithOutOfServiceSegmentReturnsConflict() throws Exception {
        Long outOfServiceSegmentId = createSegment("OUT_OF_SERVICE");
        Map<String, Object> request = new HashMap<>();
        request.put("pipeSegmentId", outOfServiceSegmentId);
        request.put("description", "管段爆裂");
        request.put("level", "MINOR");
        request.put("reporter", "张三");
        mockMvc.perform(post("/api/burst-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("管段已停用")));
    }

    @Test
    void reportEventWithInvalidParamsReturnsBadRequest() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("pipeSegmentId", segmentId);
        request.put("description", " ");
        request.put("reporter", "张三");
        mockMvc.perform(post("/api/burst-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dispatchEventMarksResourcesBusyAndLinksThem() throws Exception {
        Long teamId = createResource("TEAM-01", "TEAM");
        Long vehicleId = createResource("VEH-01", "VEHICLE");
        Long eventId = createEvent(segmentId);

        mockMvc.perform(post("/api/burst-events/{id}/transitions", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dispatchRequest(teamId, vehicleId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.resources", hasSize(2)));

        mockMvc.perform(get("/api/emergency-resources/{id}", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BUSY"));
        mockMvc.perform(get("/api/emergency-resources/{id}", vehicleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BUSY"));

        mockMvc.perform(get("/api/burst-events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.resources[0].id").isNumber())
                .andExpect(jsonPath("$.resources[1].id").isNumber());
    }

    @Test
    void dispatchEventWithoutResourcesReturnsBadRequest() throws Exception {
        Long eventId = createEvent(segmentId);
        Map<String, Object> request = new HashMap<>();
        request.put("targetStatus", "DISPATCHED");
        transition(eventId, request, 400);
    }

    @Test
    void dispatchEventWithMissingResourceReturnsNotFound() throws Exception {
        Long eventId = createEvent(segmentId);
        transition(eventId, dispatchRequest(999999L), 404);
    }

    @Test
    void dispatchEventWithBusyResourceReturnsConflictWithoutPartialUpdate() throws Exception {
        Long busyResourceId = createResource("TEAM-01", "TEAM");
        Long freeResourceId = createResource("EQP-01", "EQUIPMENT");
        Long firstEventId = createEvent(segmentId);
        Long secondEventId = createEvent(segmentId);

        transition(firstEventId, dispatchRequest(busyResourceId), 200);

        transition(secondEventId, dispatchRequest(busyResourceId, freeResourceId), 409);

        mockMvc.perform(get("/api/burst-events/{id}", secondEventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.resources", hasSize(0)));
        mockMvc.perform(get("/api/emergency-resources/{id}", freeResourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void resolveEventReleasesResources() throws Exception {
        Long teamId = createResource("TEAM-01", "TEAM");
        Long eventId = createEvent(segmentId);
        transition(eventId, dispatchRequest(teamId), 200);

        Map<String, Object> request = new HashMap<>();
        request.put("targetStatus", "RESOLVED");
        request.put("resolution", "已更换爆裂管段并恢复供水");
        mockMvc.perform(post("/api/burst-events/{id}/transitions", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("已更换爆裂管段并恢复供水"));

        mockMvc.perform(get("/api/emergency-resources/{id}", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void resolveEventWithoutResolutionReturnsBadRequest() throws Exception {
        Long teamId = createResource("TEAM-01", "TEAM");
        Long eventId = createEvent(segmentId);
        transition(eventId, dispatchRequest(teamId), 200);

        Map<String, Object> request = new HashMap<>();
        request.put("targetStatus", "RESOLVED");
        transition(eventId, request, 400);
    }

    @Test
    void invalidTransitionsReturnConflict() throws Exception {
        Long teamId = createResource("TEAM-01", "TEAM");
        Long eventId = createEvent(segmentId);

        Map<String, Object> resolveDirectly = new HashMap<>();
        resolveDirectly.put("targetStatus", "RESOLVED");
        resolveDirectly.put("resolution", "直接完成");
        transition(eventId, resolveDirectly, 409);

        transition(eventId, dispatchRequest(teamId), 200);
        transition(eventId, dispatchRequest(teamId), 409);

        Map<String, Object> resolve = new HashMap<>();
        resolve.put("targetStatus", "RESOLVED");
        resolve.put("resolution", "抢修完成");
        transition(eventId, resolve, 200);

        transition(eventId, resolve, 409);
        transition(eventId, dispatchRequest(teamId), 409);
    }

    @Test
    void getMissingEventReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/burst-events/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("爆管事件不存在")));
    }
}
