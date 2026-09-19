package com.conduit.pipesegment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PipeSegmentApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PipeSegmentRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private String validRequestJson(String code) {
        return """
                {
                  "code": "%s",
                  "name": "城东供水主干管",
                  "utilityType": "WATER",
                  "material": "球墨铸铁",
                  "diameterMm": 300,
                  "startLongitude": 116.30,
                  "startLatitude": 39.90,
                  "endLongitude": 116.40,
                  "endLatitude": 39.95,
                  "status": "ACTIVE"
                }
                """.formatted(code);
    }

    private String createSegment(String code) throws Exception {
        return mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(code)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void createReturns201WithFullPayload() throws Exception {
        mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson("  GS-001  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value("GS-001"))
                .andExpect(jsonPath("$.name").value("城东供水主干管"))
                .andExpect(jsonPath("$.utilityType").value("WATER"))
                .andExpect(jsonPath("$.material").value("球墨铸铁"))
                .andExpect(jsonPath("$.diameterMm").value(300))
                .andExpect(jsonPath("$.startLongitude").value(116.30))
                .andExpect(jsonPath("$.startLatitude").value(39.90))
                .andExpect(jsonPath("$.endLongitude").value(116.40))
                .andExpect(jsonPath("$.endLatitude").value(39.95))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createRejectsBlankCode() throws Exception {
        mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("code")));
    }

    @Test
    void createRejectsBlankNameAndMaterial() throws Exception {
        String body = validRequestJson("GS-002")
                .replace("城东供水主干管", "")
                .replace("球墨铸铁", "  ");
        mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("name")))
                .andExpect(jsonPath("$.message", containsString("material")));
    }

    @Test
    void createRejectsNonPositiveDiameter() throws Exception {
        String body = validRequestJson("GS-003").replace("300", "0");
        mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("diameterMm")));
    }

    @Test
    void createRejectsOutOfRangeCoordinates() throws Exception {
        String badLongitude = validRequestJson("GS-004").replace("116.30", "180.01");
        mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLongitude))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("startLongitude")));

        String badLatitude = validRequestJson("GS-004").replace("39.95", "-90.5");
        mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLatitude))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("endLatitude")));
    }

    @Test
    void createRejectsIdenticalEndpoints() throws Exception {
        String body = validRequestJson("GS-005")
                .replace("116.40", "116.30")
                .replace("39.95", "39.90");
        mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("起点和终点不能完全相同")));
    }

    @Test
    void createRejectsInvalidEnumAndMissingBody() throws Exception {
        String badEnum = validRequestJson("GS-006").replace("WATER", "OIL");
        mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badEnum))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createReturns409ForDuplicateCodeIgnoringCaseAndWhitespace() throws Exception {
        createSegment("GS-007");
        mockMvc.perform(post("/api/pipe-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson("  gs-007 ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void getByIdReturnsSegment() throws Exception {
        String response = createSegment("GS-008");
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();
        mockMvc.perform(get("/api/pipe-segments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.code").value("GS-008"));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/pipe-segments/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listFiltersByUtilityTypeAndStatus() throws Exception {
        createSegment("GS-010");
        String gas = validRequestJson("GS-011").replace("WATER", "GAS");
        mockMvc.perform(post("/api/pipe-segments").contentType(MediaType.APPLICATION_JSON).content(gas))
                .andExpect(status().isCreated());
        String outOfService = validRequestJson("GS-012").replace("ACTIVE", "OUT_OF_SERVICE");
        mockMvc.perform(post("/api/pipe-segments").contentType(MediaType.APPLICATION_JSON).content(outOfService))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/pipe-segments").param("utilityType", "GAS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].code").value("GS-011"));

        mockMvc.perform(get("/api/pipe-segments").param("status", "OUT_OF_SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].code").value("GS-012"));

        mockMvc.perform(get("/api/pipe-segments")
                        .param("utilityType", "WATER").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].code").value("GS-010"));
    }

    @Test
    void listFindsSegmentsOverlappingBoundingBox() throws Exception {
        createSegment("GS-020");
        String farAway = validRequestJson("GS-021")
                .replace("116.30", "10.0").replace("116.40", "11.0")
                .replace("39.90", "20.0").replace("39.95", "21.0");
        mockMvc.perform(post("/api/pipe-segments").contentType(MediaType.APPLICATION_JSON).content(farAway))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/pipe-segments")
                        .param("minLongitude", "116.35")
                        .param("minLatitude", "39.92")
                        .param("maxLongitude", "116.50")
                        .param("maxLatitude", "40.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].code").value("GS-020"));

        mockMvc.perform(get("/api/pipe-segments")
                        .param("minLongitude", "0")
                        .param("minLatitude", "0")
                        .param("maxLongitude", "180")
                        .param("maxLatitude", "80"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void listRejectsInvalidBoundingBoxParams() throws Exception {
        mockMvc.perform(get("/api/pipe-segments")
                        .param("minLongitude", "116.0")
                        .param("minLatitude", "39.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("同时提供")));

        mockMvc.perform(get("/api/pipe-segments")
                        .param("minLongitude", "117.0")
                        .param("minLatitude", "39.0")
                        .param("maxLongitude", "116.0")
                        .param("maxLatitude", "40.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("最小值小于最大值")));
    }

    @Test
    void listSupportsPaginationSortedByCode() throws Exception {
        createSegment("GS-033");
        createSegment("GS-031");
        createSegment("GS-032");

        mockMvc.perform(get("/api/pipe-segments").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].code", contains("GS-031", "GS-032")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/pipe-segments").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].code").value("GS-033"));
    }

    @Test
    void listRejectsInvalidPaginationParams() throws Exception {
        mockMvc.perform(get("/api/pipe-segments").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/pipe-segments").param("size", "0"))
                .andExpect(status().isBadRequest());
    }
}
