package com.conduit.inspection;

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

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InspectionTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InspectionTaskRepository taskRepository;

    @Autowired
    private PipeSegmentRepository pipeSegmentRepository;

    private Long segmentId;

    @BeforeEach
    void setUp() throws Exception {
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

    private Map<String, Object> validRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("pipeSegmentId", segmentId);
        request.put("title", "第一季度例行巡检");
        request.put("inspector", "张三");
        request.put("scheduledAt", "2026-03-01T09:00:00+08:00");
        return request;
    }

    private Long createTask(Map<String, Object> overrides) throws Exception {
        Map<String, Object> request = validRequest();
        request.putAll(overrides);
        MvcResult result = mockMvc.perform(post("/api/inspection-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void transition(Long taskId, String targetStatus, String result, int expectedStatus) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("targetStatus", targetStatus);
        request.put("result", result);
        mockMvc.perform(post("/api/inspection-tasks/{id}/transitions", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void createReturns201WithPendingStatus() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/inspection-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.pipeSegmentId").value(segmentId))
                .andExpect(jsonPath("$.title").value("第一季度例行巡检"))
                .andExpect(jsonPath("$.inspector").value("张三"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andReturn();
        String scheduledAt = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("scheduledAt").asString();
        assertTrue(OffsetDateTime.parse(scheduledAt)
                .isEqual(OffsetDateTime.parse("2026-03-01T09:00:00+08:00")));
    }

    @Test
    void createRejectsMissingSegment() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("pipeSegmentId", 999999L);

        mockMvc.perform(post("/api/inspection-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("管段不存在")));
    }

    @Test
    void createRejectsBlankTitleAndInspector() throws Exception {
        Map<String, Object> blankTitle = validRequest();
        blankTitle.put("title", "   ");
        mockMvc.perform(post("/api/inspection-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankTitle)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        Map<String, Object> blankInspector = validRequest();
        blankInspector.put("inspector", "");
        mockMvc.perform(post("/api/inspection-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankInspector)))
                .andExpect(status().isBadRequest());

        Map<String, Object> missingScheduledAt = validRequest();
        missingScheduledAt.remove("scheduledAt");
        mockMvc.perform(post("/api/inspection-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingScheduledAt)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByIdReturnsTaskAnd404WhenMissing() throws Exception {
        Long taskId = createTask(Map.of());

        mockMvc.perform(get("/api/inspection-tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId))
                .andExpect(jsonPath("$.title").value("第一季度例行巡检"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/inspection-tasks/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("巡检任务不存在")));
    }

    @Test
    void listFiltersByInspectorAndStatus() throws Exception {
        Long zhangTask = createTask(Map.of("inspector", "张三", "scheduledAt", "2026-03-01T09:00:00+08:00"));
        createTask(Map.of("inspector", "李四", "title", "燃气管道巡检", "scheduledAt", "2026-03-02T09:00:00+08:00"));
        transition(zhangTask, "IN_PROGRESS", null, 200);

        mockMvc.perform(get("/api/inspection-tasks").param("inspector", "张三"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].inspector").value("张三"));

        mockMvc.perform(get("/api/inspection-tasks").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].inspector").value("李四"));

        mockMvc.perform(get("/api/inspection-tasks")
                        .param("inspector", "张三")
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(zhangTask));

        mockMvc.perform(get("/api/inspection-tasks").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void listSortsByScheduledAtThenId() throws Exception {
        Long first = createTask(Map.of("scheduledAt", "2026-03-02T09:00:00+08:00"));
        Long second = createTask(Map.of("scheduledAt", "2026-03-01T09:00:00+08:00"));
        Long third = createTask(Map.of("scheduledAt", "2026-03-01T09:00:00+08:00"));

        mockMvc.perform(get("/api/inspection-tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[1].id").value(third))
                .andExpect(jsonPath("$.content[2].id").value(first));
    }

    @Test
    void listPaginates() throws Exception {
        createTask(Map.of("scheduledAt", "2026-03-01T09:00:00+08:00"));
        createTask(Map.of("scheduledAt", "2026-03-02T09:00:00+08:00"));
        createTask(Map.of("scheduledAt", "2026-03-03T09:00:00+08:00"));

        mockMvc.perform(get("/api/inspection-tasks")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/inspection-tasks").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transitionFollowsHappyPath() throws Exception {
        Long taskId = createTask(Map.of());

        transition(taskId, "IN_PROGRESS", null, 200);

        mockMvc.perform(post("/api/inspection-tasks/{id}/transitions", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("targetStatus", "COMPLETED", "result", "管道运行正常，无泄漏"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result").value("管道运行正常，无泄漏"));

        mockMvc.perform(get("/api/inspection-tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result").value("管道运行正常，无泄漏"));
    }

    @Test
    void transitionRejectsIllegalJumps() throws Exception {
        Long taskId = createTask(Map.of());

        // PENDING -> COMPLETED 不允许
        transition(taskId, "COMPLETED", "结果", 409);
        // PENDING -> PENDING 不允许
        transition(taskId, "PENDING", null, 409);

        transition(taskId, "IN_PROGRESS", null, 200);
        // IN_PROGRESS -> PENDING 不允许
        transition(taskId, "PENDING", null, 409);
        // IN_PROGRESS -> IN_PROGRESS 不允许
        transition(taskId, "IN_PROGRESS", null, 409);
    }

    @Test
    void completeRequiresResult() throws Exception {
        Long taskId = createTask(Map.of());
        transition(taskId, "IN_PROGRESS", null, 200);

        transition(taskId, "COMPLETED", null, 400);
        transition(taskId, "COMPLETED", "   ", 400);

        mockMvc.perform(get("/api/inspection-tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void completedTaskCannotBeModified() throws Exception {
        Long taskId = createTask(Map.of());
        transition(taskId, "IN_PROGRESS", null, 200);
        transition(taskId, "COMPLETED", "一切正常", 200);

        transition(taskId, "IN_PROGRESS", null, 409);
        transition(taskId, "COMPLETED", "新的结果", 409);
        transition(taskId, "PENDING", null, 409);

        mockMvc.perform(get("/api/inspection-tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result").value("一切正常"));
    }

    @Test
    void transitionReturns404ForMissingTask() throws Exception {
        mockMvc.perform(post("/api/inspection-tasks/{id}/transitions", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetStatus", "IN_PROGRESS"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("巡检任务不存在")));
    }
}
