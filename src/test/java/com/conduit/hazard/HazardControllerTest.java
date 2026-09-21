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

    private Long createTask() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("pipeSegmentId", segmentId);
        request.put("title", "第一季度例行巡检");
        request.put("inspector", "张三");
        request.put("scheduledAt", "2026-03-01T09:00:00+08:00");
        MvcResult result = mockMvc.perform(post("/api/inspection-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void transitionTask(Long taskId, String targetStatus, String result) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("targetStatus", targetStatus);
        request.put("result", result);
        mockMvc.perform(post("/api/inspection-tasks/{id}/transitions", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private Long createCompletedTask() throws Exception {
        Long taskId = createTask();
        transitionTask(taskId, "IN_PROGRESS", null);
        transitionTask(taskId, "COMPLETED", "巡检完成，发现管壁腐蚀");
        return taskId;
    }

    private Map<String, Object> validRequest(Long taskId) {
        Map<String, Object> request = new HashMap<>();
        request.put("inspectionTaskId", taskId);
        request.put("description", "管壁出现明显腐蚀");
        request.put("level", "HIGH");
        request.put("reporter", "李四");
        return request;
    }

    private Long createHazard(Map<String, Object> overrides) throws Exception {
        Map<String, Object> request = validRequest(createCompletedTask());
        request.putAll(overrides);
        MvcResult result = mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void transitionHazard(Long hazardId, String targetStatus, String assignee,
                                  String resolution, int expectedStatus) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("targetStatus", targetStatus);
        request.put("assignee", assignee);
        request.put("resolution", resolution);
        mockMvc.perform(post("/api/hazards/{id}/transitions", hazardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void createReturns201WithOpenStatus() throws Exception {
        Long taskId = createCompletedTask();
        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest(taskId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.inspectionTaskId").value(taskId))
                .andExpect(jsonPath("$.description").value("管壁出现明显腐蚀"))
                .andExpect(jsonPath("$.level").value("HIGH"))
                .andExpect(jsonPath("$.reporter").value("李四"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignee").doesNotExist())
                .andExpect(jsonPath("$.resolution").doesNotExist());
    }

    @Test
    void createWithMissingTaskReturns404() throws Exception {
        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest(999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("巡检任务不存在")));
    }

    @Test
    void createWithPendingTaskReturns409() throws Exception {
        Long taskId = createTask();
        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest(taskId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("尚未完成")));
    }

    @Test
    void createWithInProgressTaskReturns409() throws Exception {
        Long taskId = createTask();
        transitionTask(taskId, "IN_PROGRESS", null);
        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest(taskId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("尚未完成")));
    }

    @Test
    void createWithBlankDescriptionReturns400() throws Exception {
        Map<String, Object> request = validRequest(createCompletedTask());
        request.put("description", "  ");
        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("description 不能为空")));
    }

    @Test
    void createWithBlankReporterReturns400() throws Exception {
        Map<String, Object> request = validRequest(createCompletedTask());
        request.put("reporter", "");
        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("reporter 不能为空")));
    }

    @Test
    void createWithMissingLevelOrTaskIdReturns400() throws Exception {
        Map<String, Object> request = validRequest(createCompletedTask());
        request.remove("level");
        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("level 不能为空")));

        request.put("level", "LOW");
        request.remove("inspectionTaskId");
        mockMvc.perform(post("/api/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("inspectionTaskId 不能为空")));
    }

    @Test
    void getByIdReturnsDetail() throws Exception {
        Long hazardId = createHazard(Map.of());
        mockMvc.perform(get("/api/hazards/{id}", hazardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hazardId))
                .andExpect(jsonPath("$.description").value("管壁出现明显腐蚀"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void getByIdWithMissingHazardReturns404() throws Exception {
        mockMvc.perform(get("/api/hazards/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("隐患不存在")));
    }

    @Test
    void listFiltersByLevelAndStatusAndSortsByIdDesc() throws Exception {
        Long low = createHazard(Map.of("level", "LOW"));
        Long medium = createHazard(Map.of("level", "MEDIUM"));
        Long high = createHazard(Map.of("level", "HIGH"));
        transitionHazard(medium, "RECTIFYING", "王五", null, 200);

        mockMvc.perform(get("/api/hazards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].id").value(high))
                .andExpect(jsonPath("$.content[1].id").value(medium))
                .andExpect(jsonPath("$.content[2].id").value(low));

        mockMvc.perform(get("/api/hazards").param("level", "MEDIUM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(medium));

        mockMvc.perform(get("/api/hazards").param("status", "RECTIFYING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(medium));

        mockMvc.perform(get("/api/hazards")
                        .param("level", "LOW")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(low));

        mockMvc.perform(get("/api/hazards").param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void listSupportsPagination() throws Exception {
        createHazard(Map.of());
        createHazard(Map.of());
        createHazard(Map.of());

        mockMvc.perform(get("/api/hazards").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/hazards").param("page", "-1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/hazards").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullLifecycleOpenToRectifyingToResolved() throws Exception {
        Long hazardId = createHazard(Map.of());

        transitionHazard(hazardId, "RECTIFYING", "王五", null, 200);
        mockMvc.perform(get("/api/hazards/{id}", hazardId))
                .andExpect(jsonPath("$.status").value("RECTIFYING"))
                .andExpect(jsonPath("$.assignee").value("王五"));

        transitionHazard(hazardId, "RESOLVED", null, "已更换腐蚀管段", 200);
        mockMvc.perform(get("/api/hazards/{id}", hazardId))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("已更换腐蚀管段"));
    }

    @Test
    void startRectificationWithoutAssigneeReturns400() throws Exception {
        Long hazardId = createHazard(Map.of());
        transitionHazard(hazardId, "RECTIFYING", null, null, 400);
        transitionHazard(hazardId, "RECTIFYING", "  ", null, 400);
    }

    @Test
    void resolveWithoutResolutionReturns400() throws Exception {
        Long hazardId = createHazard(Map.of());
        transitionHazard(hazardId, "RECTIFYING", "王五", null, 200);
        transitionHazard(hazardId, "RESOLVED", null, null, 400);
        transitionHazard(hazardId, "RESOLVED", null, "", 400);
    }

    @Test
    void illegalTransitionsReturn409() throws Exception {
        Long hazardId = createHazard(Map.of());
        transitionHazard(hazardId, "RESOLVED", null, "直接完成", 409);

        transitionHazard(hazardId, "RECTIFYING", "王五", null, 200);
        transitionHazard(hazardId, "OPEN", null, null, 409);
        transitionHazard(hazardId, "RECTIFYING", "王五", null, 409);
    }

    @Test
    void resolvedHazardCannotBeModified() throws Exception {
        Long hazardId = createHazard(Map.of());
        transitionHazard(hazardId, "RECTIFYING", "王五", null, 200);
        transitionHazard(hazardId, "RESOLVED", null, "已更换腐蚀管段", 200);

        transitionHazard(hazardId, "RECTIFYING", "赵六", null, 409);
        transitionHazard(hazardId, "OPEN", null, null, 409);
        transitionHazard(hazardId, "RESOLVED", null, "重复处理", 409);

        mockMvc.perform(get("/api/hazards/{id}", hazardId))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.assignee").value("王五"))
                .andExpect(jsonPath("$.resolution").value("已更换腐蚀管段"));
    }

    @Test
    void transitionWithMissingHazardReturns404() throws Exception {
        transitionHazard(999L, "RECTIFYING", "王五", null, 404);
    }
}
