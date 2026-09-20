package com.conduit.pipesegment;

import com.conduit.inspection.InspectionTaskRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
class PipeSegmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PipeSegmentRepository repository;

    @Autowired
    private InspectionTaskRepository inspectionTaskRepository;

    @BeforeEach
    void cleanUp() {
        inspectionTaskRepository.deleteAll();
        repository.deleteAll();
    }

    private Map<String, Object> validRequest() {
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
        return request;
    }

    private String createSegment(Map<String, Object> overrides) throws Exception {
        Map<String, Object> request = validRequest();
        request.putAll(overrides);
        String response = mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return response;
    }

    @Test
    void createReturns201WithFullPayload() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("code", "  WS-001  ");

        mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value("WS-001"))
                .andExpect(jsonPath("$.name").value("城东供水主干管"))
                .andExpect(jsonPath("$.utilityType").value("WATER"))
                .andExpect(jsonPath("$.material").value("球墨铸铁"))
                .andExpect(jsonPath("$.diameterMm").value(300))
                .andExpect(jsonPath("$.startLongitude").value(116.40))
                .andExpect(jsonPath("$.startLatitude").value(39.90))
                .andExpect(jsonPath("$.endLongitude").value(116.41))
                .andExpect(jsonPath("$.endLatitude").value(39.91))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createRejectsBlankAndInvalidFields() throws Exception {
        assertBadRequest(Map.of("code", "   "));
        assertBadRequest(Map.of("name", ""));
        assertBadRequest(Map.of("material", " "));
        assertBadRequest(Map.of("diameterMm", 0));
        assertBadRequest(Map.of("diameterMm", -5));
        assertBadRequest(Map.of("startLongitude", 180.01));
        assertBadRequest(Map.of("endLongitude", -180.01));
        assertBadRequest(Map.of("startLatitude", 90.01));
        assertBadRequest(Map.of("endLatitude", -90.01));
        assertBadRequest(Map.of("utilityType", "OIL"));
        assertBadRequest(Map.of("status", "RUNNING"));
    }

    @Test
    void createRejectsIdenticalStartAndEnd() throws Exception {
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("endLongitude", 116.40);
        overrides.put("endLatitude", 39.90);

        Map<String, Object> request = validRequest();
        request.putAll(overrides);
        mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("起点和终点")));
    }

    private void assertBadRequest(Map<String, Object> overrides) throws Exception {
        Map<String, Object> request = validRequest();
        request.putAll(overrides);
        mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void createRejectsDuplicateCodeIgnoringCase() throws Exception {
        createSegment(Map.of("code", "WS-001"));

        mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        Map<String, Object> lowerCase = validRequest();
        lowerCase.put("code", "ws-001");
        mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lowerCase)))
                .andExpect(status().isConflict());
    }

    @Test
    void getByIdReturnsDetail() throws Exception {
        String created = createSegment(Map.of());
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/pipe-segments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.code").value("WS-001"))
                .andExpect(jsonPath("$.utilityType").value("WATER"));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/pipe-segments/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void listFiltersByUtilityTypeAndStatus() throws Exception {
        createSegment(Map.of("code", "A-1", "utilityType", "WATER", "status", "ACTIVE"));
        createSegment(Map.of("code", "B-1", "utilityType", "GAS", "status", "ACTIVE"));
        createSegment(Map.of("code", "C-1", "utilityType", "WATER", "status", "OUT_OF_SERVICE"));

        mockMvc.perform(get("/api/pipe-segments").param("utilityType", "WATER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].code").value("A-1"))
                .andExpect(jsonPath("$.content[1].code").value("C-1"));

        mockMvc.perform(get("/api/pipe-segments")
                        .param("utilityType", "WATER")
                        .param("status", "OUT_OF_SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].code").value("C-1"));

        mockMvc.perform(get("/api/pipe-segments").param("utilityType", "STEAM"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFiltersByBoundingBoxOverlap() throws Exception {
        // 横穿矩形的管段
        createSegment(Map.of("code", "CROSS",
                "startLongitude", 116.35, "startLatitude", 39.85,
                "endLongitude", 116.45, "endLatitude", 39.95));
        // 完全在矩形内的管段
        createSegment(Map.of("code", "INSIDE",
                "startLongitude", 116.40, "startLatitude", 39.90,
                "endLongitude", 116.41, "endLatitude", 39.91));
        // 远离矩形的管段
        createSegment(Map.of("code", "FAR",
                "startLongitude", 117.00, "startLatitude", 40.00,
                "endLongitude", 117.01, "endLatitude", 40.01));
        // 外包矩形与查询矩形相交但线段本身不相交（对角线绕过角点）
        createSegment(Map.of("code", "NEAR-MISS",
                "startLongitude", 116.39, "startLatitude", 39.905,
                "endLongitude", 116.405, "endLatitude", 39.89));

        mockMvc.perform(get("/api/pipe-segments")
                        .param("minLongitude", "116.40")
                        .param("minLatitude", "39.90")
                        .param("maxLongitude", "116.42")
                        .param("maxLatitude", "39.92"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].code").value("CROSS"))
                .andExpect(jsonPath("$.content[1].code").value("INSIDE"));
    }

    @Test
    void listRejectsInvalidBoundingBoxParams() throws Exception {
        // 只提供部分范围参数
        mockMvc.perform(get("/api/pipe-segments")
                        .param("minLongitude", "116.40")
                        .param("minLatitude", "39.90"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 最小值不小于最大值
        mockMvc.perform(get("/api/pipe-segments")
                        .param("minLongitude", "116.42")
                        .param("minLatitude", "39.90")
                        .param("maxLongitude", "116.40")
                        .param("maxLatitude", "39.92"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/pipe-segments")
                        .param("minLongitude", "116.40")
                        .param("minLatitude", "39.92")
                        .param("maxLongitude", "116.42")
                        .param("maxLatitude", "39.92"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSupportsPaginationSortedByCode() throws Exception {
        createSegment(Map.of("code", "SEG-03"));
        createSegment(Map.of("code", "seg-01"));
        createSegment(Map.of("code", "SEG-02"));

        mockMvc.perform(get("/api/pipe-segments").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].code").value("seg-01"))
                .andExpect(jsonPath("$.content[1].code").value("SEG-02"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(0));

        mockMvc.perform(get("/api/pipe-segments").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].code").value("SEG-03"));

        mockMvc.perform(get("/api/pipe-segments").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/pipe-segments").param("size", "0"))
                .andExpect(status().isBadRequest());
    }
}
