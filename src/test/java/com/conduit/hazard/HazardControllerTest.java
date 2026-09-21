package com.conduit.hazard;

import com.conduit.inspection.InspectionTaskRepository;
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
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HazardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HazardRepository hazardRepository;

    @Autowired
    private InspectionTaskRepository taskRepository;

    @Autowired
    private PipeSegmentRepository pipeSegmentRepository;

    private Long segmentId;

    @BeforeEach
    void setUp() throws Exception {
        hazardRepository.deleteAll();
        taskRepository.deleteAll();
        pipeSegmentRepository.deleteAll();
        segmentId = createSegment();
    }

    private Long createSegment() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("code", "WS-001");
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

    private Long createTask(String inspector) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("pipeSegmentId", segmentId);
        request.put("title", "第一季度例行巡检");
        request.put("inspector", inspector);
        request.put("scheduledAt", "2026-03-01T09:00:00+08:00");
        MvcResult result = mockMvc.perform(post("/api/inspection-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void taskTransition(Long taskId, String targetStatus, String result, int expectedStatus)
            throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("targetStatus", targetStatus);
        request.put("result", result);
        mockMvc.perform(post("/api/inspection-tasks/{id}/transitions", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus));
    }

    private Long completeTask() throws Exception {
        Long taskId = createTask("张三");
        taskTransition(taskId, "IN_PROGRESS", null, 200);
        taskTransition(taskId, "COMPLETED", "巡检完成，发现管壁锈蚀", 200);
        return taskId;
    }

    private Map<String, Object> validHazardRequest(Long inspectionTaskId) {
        Map<String, Object> request = new HashMap<>();
        request.put("inspectionTaskId", inspectionTaskId);
        request.put("description", "发现管壁锈蚀严重");
        request.put("level", "HIGH");
        request.put("reporter", "王五");
        return request;
    }

    private Long reportHazard(Map<String, Object> overrides) throws Exception {
        Long taskId = completeTask();
        Map<String, Object> request = validHazardRequest(taskId);
        request.putAll(overrides);
        MvcResult result = mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void hazardTransition(Long hazardId, Map<String, Object> body, int expectedStatus)
            throws Exception {
        mockMvc.perform(post("/api/hazards/{id}/transitions", hazardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void reportReturns201WithOpenStatus() throws Exception {
        Long taskId = completeTask();

        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validHazardRequest(taskId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.inspectionTaskId").value(taskId))
                .andExpect(jsonPath("$.description").value("发现管壁锈蚀严重"))
                .andExpect(jsonPath("$.level").value("HIGH"))
                .andExpect(jsonPath("$.reporter").value("王五"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignee").doesNotExist())
                .andExpect(jsonPath("$.resolution").doesNotExist());
    }

    @Test
    void reportReturns404WhenTaskMissing() throws Exception {
        Map<String, Object> request = validHazardRequest(999999L);

        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("巡检任务不存在")));
    }

    @Test
    void reportReturns409WhenTaskNotCompleted() throws Exception {
        Long pendingTaskId = createTask("张三");

        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validHazardRequest(pendingTaskId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("尚未完成")));

        taskTransition(pendingTaskId, "IN_PROGRESS", null, 200);
        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validHazardRequest(pendingTaskId))))
                .andExpect(status().isConflict());
    }

    @Test
    void reportRejectsBlankDescriptionAndReporter() throws Exception {
        Long taskId = completeTask();

        Map<String, Object> blankDescription = validHazardRequest(taskId);
        blankDescription.put("description", "   ");
        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankDescription)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        Map<String, Object> blankReporter = validHazardRequest(taskId);
        blankReporter.put("reporter", "");
        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankReporter)))
                .andExpect(status().isBadRequest());

        Map<String, Object> missingLevel = validHazardRequest(taskId);
        missingLevel.remove("level");
        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingLevel)))
                .andExpect(status().isBadRequest());

        Map<String, Object> invalidLevel = validHazardRequest(taskId);
        invalidLevel.put("level", "CRITICAL");
        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLevel)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByIdReturnsHazardAnd404WhenMissing() throws Exception {
        Long hazardId = reportHazard(Map.of());

        mockMvc.perform(get("/api/hazards/{id}", hazardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hazardId))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.level").value("HIGH"));

        mockMvc.perform(get("/api/hazards/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("隐患不存在")));
    }

    @Test
    void listFiltersByLevelAndStatus() throws Exception {
        Long highOpen = reportHazard(Map.of("level", "HIGH"));
        Long mediumOpen = reportHazard(Map.of("level", "MEDIUM", "description", "轻微渗漏"));
        hazardTransition(highOpen, Map.of("targetStatus", "RECTIFYING", "assignee", "李四"), 200);

        mockMvc.perform(get("/api/hazards").param("level", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].level").value("HIGH"));

        mockMvc.perform(get("/api/hazards").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(mediumOpen));

        mockMvc.perform(get("/api/hazards")
                        .param("level", "HIGH")
                        .param("status", "RECTIFYING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(highOpen));

        mockMvc.perform(get("/api/hazards").param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/hazards").param("status", "BOGUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSortsByIdDesc() throws Exception {
        Long first = reportHazard(Map.of());
        Long second = reportHazard(Map.of("level", "MEDIUM"));
        Long third = reportHazard(Map.of("level", "LOW"));

        mockMvc.perform(get("/api/hazards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].id").value(third))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.content[2].id").value(first));
    }

    @Test
    void listPaginates() throws Exception {
        reportHazard(Map.of());
        reportHazard(Map.of());
        reportHazard(Map.of());

        mockMvc.perform(get("/api/hazards").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/hazards").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transitionFollowsHappyPath() throws Exception {
        Long hazardId = reportHazard(Map.of());

        mockMvc.perform(post("/api/hazards/{id}/transitions", hazardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("targetStatus", "RECTIFYING", "assignee", "李四"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECTIFYING"))
                .andExpect(jsonPath("$.assignee").value("李四"))
                .andExpect(jsonPath("$.resolution").doesNotExist());

        mockMvc.perform(post("/api/hazards/{id}/transitions", hazardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("targetStatus", "RESOLVED",
                                        "resolution", "已更换锈蚀管段并复检合格"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("已更换锈蚀管段并复检合格"))
                .andExpect(jsonPath("$.assignee").value("李四"));

        mockMvc.perform(get("/api/hazards/{id}", hazardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("已更换锈蚀管段并复检合格"));
    }

    @Test
    void transitionRejectsIllegalJumps() throws Exception {
        Long hazardId = reportHazard(Map.of());

        hazardTransition(hazardId, Map.of("targetStatus", "RESOLVED", "resolution", "结果"), 409);
        hazardTransition(hazardId, Map.of("targetStatus", "OPEN"), 409);

        hazardTransition(hazardId, Map.of("targetStatus", "RECTIFYING", "assignee", "李四"), 200);
        hazardTransition(hazardId, Map.of("targetStatus", "OPEN"), 409);
        hazardTransition(hazardId, Map.of("targetStatus", "RECTIFYING", "assignee", "李四"), 409);
    }

    @Test
    void startRectificationRequiresAssignee() throws Exception {
        Long hazardId = reportHazard(Map.of());

        hazardTransition(hazardId, Map.of("targetStatus", "RECTIFYING"), 400);
        hazardTransition(hazardId, Map.of("targetStatus", "RECTIFYING", "assignee", "   "), 400);

        mockMvc.perform(get("/api/hazards/{id}", hazardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void resolveRequiresResolution() throws Exception {
        Long hazardId = reportHazard(Map.of());
        hazardTransition(hazardId, Map.of("targetStatus", "RECTIFYING", "assignee", "李四"), 200);

        hazardTransition(hazardId, Map.of("targetStatus", "RESOLVED"), 400);
        hazardTransition(hazardId, Map.of("targetStatus", "RESOLVED", "resolution", "   "), 400);

        mockMvc.perform(get("/api/hazards/{id}", hazardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECTIFYING"))
                .andExpect(jsonPath("$.assignee").value("李四"));
    }

    @Test
    void resolvedHazardCannotBeModified() throws Exception {
        Long hazardId = reportHazard(Map.of());
        hazardTransition(hazardId, Map.of("targetStatus", "RECTIFYING", "assignee", "李四"), 200);
        hazardTransition(hazardId,
                Map.of("targetStatus", "RESOLVED", "resolution", "已完成修复"), 200);

        hazardTransition(hazardId,
                Map.of("targetStatus", "RECTIFYING", "assignee", "王五"), 409);
        hazardTransition(hazardId,
                Map.of("targetStatus", "RESOLVED", "resolution", "再次整改"), 409);
        hazardTransition(hazardId, Map.of("targetStatus", "OPEN"), 409);

        mockMvc.perform(get("/api/hazards/{id}", hazardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.assignee").value("李四"))
                .andExpect(jsonPath("$.resolution").value("已完成修复"));
    }

    @Test
    void transitionReturns404ForMissingHazard() throws Exception {
        mockMvc.perform(post("/api/hazards/{id}/transitions", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("targetStatus", "RECTIFYING", "assignee", "李四"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("隐患不存在")));
    }

    @Test
    void transitionRejectsMissingTargetStatus() throws Exception {
        Long hazardId = reportHazard(Map.of());

        hazardTransition(hazardId, Map.of("assignee", "李四"), 400);
    }
}
