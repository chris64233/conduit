package com.conduit.inspection;

import com.conduit.pipesegment.PipeSegmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @BeforeEach
    void cleanUp() {
        taskRepository.deleteAll();
        pipeSegmentRepository.deleteAll();
    }

    private Long createSegment(String code) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("code", code);
        request.put("name", "测试管段");
        request.put("utilityType", "WATER");
        request.put("material", "球墨铸铁");
        request.put("diameterMm", 300);
        request.put("startLongitude", 116.40);
        request.put("startLatitude", 39.90);
        request.put("endLongitude", 116.41);
        request.put("endLatitude", 39.91);
        request.put("status", "ACTIVE");
        String response = mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Map<String, Object> validRequest(Long pipeSegmentId) {
        Map<String, Object> request = new HashMap<>();
        request.put("pipeSegmentId", pipeSegmentId);
        request.put("title", "月度例行巡检");
        request.put("inspector", "张三");
        request.put("scheduledAt", "2026-10-01T09:00:00+08:00");
        return request;
    }

    private Long createTask(Map<String, Object> request) throws Exception {
        String response = mockMvc.perform(post("/api/inspection-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void patchStatus(Long taskId, String targetStatus, String result) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("status", targetStatus);
        request.put("result", result);
        mockMvc.perform(patch("/api/inspection-tasks/{id}/status", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void createReturns201WithPendingStatus() throws Exception {
        Long segmentId = createSegment("WS-001");

        mockMvc.perform(post("/api/inspection-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest(segmentId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.pipeSegmentId").value(segmentId))
                .andExpect(jsonPath("$.title").value("月度例行巡检"))
                .andExpect(jsonPath("$.inspector").value("张三"))
                .andExpect(jsonPath("$.scheduledAt").isString())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void createRejectsBlankAndMissingFields() throws Exception {
        Long segmentId = createSegment("WS-001");

        assertBadRequest(segmentId, Map.of("title", "   "));
        assertBadRequest(segmentId, Map.of("inspector", ""));
        assertBadRequest(segmentId, Map.of("scheduledAt", "not-a-time"));

        Map<String, Object> noTitle = validRequest(segmentId);
        noTitle.remove("title");
        mockMvc.perform(post("/api/inspection-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noTitle)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        Map<String, Object> noSegment = validRequest(segmentId);
        noSegment.remove("pipeSegmentId");
        mockMvc.perform(post("/api/inspection-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noSegment)))
                .andExpect(status().isBadRequest());
    }

    private void assertBadRequest(Long segmentId, Map<String, Object> overrides) throws Exception {
        Map<String, Object> request = validRequest(segmentId);
        request.putAll(overrides);
        mockMvc.perform(post("/api/inspection-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void createReturns404WhenPipeSegmentMissing() throws Exception {
        mockMvc.perform(post("/api/inspection-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest(999999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("管段不存在")));
    }

    @Test
    void getByIdReturnsDetail() throws Exception {
        Long segmentId = createSegment("WS-001");
        Long taskId = createTask(validRequest(segmentId));

        mockMvc.perform(get("/api/inspection-tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId))
                .andExpect(jsonPath("$.pipeSegmentId").value(segmentId))
                .andExpect(jsonPath("$.title").value("月度例行巡检"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/inspection-tasks/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void listFiltersByInspectorAndStatus() throws Exception {
        Long segmentId = createSegment("WS-001");
        Long zhangTask = createTask(validRequest(segmentId));

        Map<String, Object> liRequest = validRequest(segmentId);
        liRequest.put("inspector", "李四");
        liRequest.put("scheduledAt", "2026-10-02T09:00:00+08:00");
        Long liTask = createTask(liRequest);
        patchStatus(liTask, "IN_PROGRESS", null);

        mockMvc.perform(get("/api/inspection-tasks").param("inspector", "张三"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(zhangTask));

        mockMvc.perform(get("/api/inspection-tasks").param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(liTask));

        mockMvc.perform(get("/api/inspection-tasks")
                        .param("inspector", "李四")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/inspection-tasks").param("status", "ARCHIVED"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSortsByScheduledAtThenId() throws Exception {
        Long segmentId = createSegment("WS-001");

        Map<String, Object> later = validRequest(segmentId);
        later.put("title", "较晚任务");
        later.put("scheduledAt", "2026-10-03T09:00:00+08:00");
        createTask(later);

        Map<String, Object> earlier1 = validRequest(segmentId);
        earlier1.put("title", "较早任务一");
        earlier1.put("scheduledAt", "2026-10-01T09:00:00+08:00");
        Long firstId = createTask(earlier1);

        Map<String, Object> earlier2 = validRequest(segmentId);
        earlier2.put("title", "较早任务二");
        earlier2.put("scheduledAt", "2026-10-01T09:00:00+08:00");
        Long secondId = createTask(earlier2);

        mockMvc.perform(get("/api/inspection-tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].id").value(firstId))
                .andExpect(jsonPath("$.content[1].id").value(secondId))
                .andExpect(jsonPath("$.content[2].title").value("较晚任务"));
    }

    @Test
    void listSupportsPagination() throws Exception {
        Long segmentId = createSegment("WS-001");
        createTask(validRequest(segmentId));
        Map<String, Object> second = validRequest(segmentId);
        second.put("scheduledAt", "2026-10-02T09:00:00+08:00");
        createTask(second);

        mockMvc.perform(get("/api/inspection-tasks").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/inspection-tasks").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/inspection-tasks").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusTransitionFollowsPendingToInProgressToCompleted() throws Exception {
        Long segmentId = createSegment("WS-001");
        Long taskId = createTask(validRequest(segmentId));

        patchStatus(taskId, "IN_PROGRESS", null);
        mockMvc.perform(get("/api/inspection-tasks/{id}", taskId))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        patchStatus(taskId, "COMPLETED", "管线运行正常，无泄漏");
        mockMvc.perform(get("/api/inspection-tasks/{id}", taskId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result").value("管线运行正常，无泄漏"));
    }

    @Test
    void statusTransitionRejectsIllegalTransitions() throws Exception {
        Long segmentId = createSegment("WS-001");
        Long taskId = createTask(validRequest(segmentId));

        // PENDING 不能直接完成
        assertTransitionConflict(taskId, "COMPLETED", "已完成");
        // PENDING 不能回到 PENDING
        assertTransitionConflict(taskId, "PENDING", null);

        patchStatus(taskId, "IN_PROGRESS", null);
        // IN_PROGRESS 不能回到 PENDING
        assertTransitionConflict(taskId, "PENDING", null);
        // IN_PROGRESS 不能再次进入 IN_PROGRESS
        assertTransitionConflict(taskId, "IN_PROGRESS", null);
    }

    private void assertTransitionConflict(Long taskId, String targetStatus, String result) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("status", targetStatus);
        request.put("result", result);
        mockMvc.perform(patch("/api/inspection-tasks/{id}/status", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void completeRequiresResult() throws Exception {
        Long segmentId = createSegment("WS-001");
        Long taskId = createTask(validRequest(segmentId));
        patchStatus(taskId, "IN_PROGRESS", null);

        Map<String, Object> request = new HashMap<>();
        request.put("status", "COMPLETED");
        mockMvc.perform(patch("/api/inspection-tasks/{id}/status", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        request.put("result", "   ");
        mockMvc.perform(patch("/api/inspection-tasks/{id}/status", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // 状态未被非法修改
        mockMvc.perform(get("/api/inspection-tasks/{id}", taskId))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void completedTaskCannotBeModified() throws Exception {
        Long segmentId = createSegment("WS-001");
        Long taskId = createTask(validRequest(segmentId));
        patchStatus(taskId, "IN_PROGRESS", null);
        patchStatus(taskId, "COMPLETED", "一切正常");

        assertTransitionConflict(taskId, "IN_PROGRESS", null);
        assertTransitionConflict(taskId, "PENDING", null);
        assertTransitionConflict(taskId, "COMPLETED", "重复完成");
    }

    @Test
    void updateStatusReturns404WhenTaskMissing() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("status", "IN_PROGRESS");
        mockMvc.perform(patch("/api/inspection-tasks/{id}/status", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
