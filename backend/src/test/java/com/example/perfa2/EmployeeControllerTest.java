package com.example.perfa2;

import com.example.perfa2.repository.EmployeeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmployeeRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void createReturnsCreatedEmployeeAndAppearsInList() throws Exception {
        MvcResult created = create(validPayload("E1001", "张三"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.employeeNo").value("E1001"))
                .andExpect(jsonPath("$.name").value("张三"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();

        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        assertThat(id).isPositive();

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.items[0].employeeNo").value("E1001"));
    }

    @Test
    void createTrimsEmployeeNoAndName() throws Exception {
        create(validPayload("  E1002  ", "  李四  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeNo").value("E1002"))
                .andExpect(jsonPath("$.name").value("李四"));

        mockMvc.perform(get("/api/employees").param("keyword", "E1002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("李四"));
    }

    @Test
    void missingOrBlankRequiredFieldsReturnValidationErrors() throws Exception {
        Map<String, Object> missingEmployeeNo = new LinkedHashMap<>();
        missingEmployeeNo.put("name", "张三");
        create(missingEmployeeNo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.employeeNo").isNotEmpty());

        Map<String, Object> missingName = new LinkedHashMap<>();
        missingName.put("employeeNo", "E1003");
        create(missingName)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());

        create(validPayload("   ", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.employeeNo").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
    }

    @Test
    void lengthBoundariesAreEnforced() throws Exception {
        create(validPayload("E".repeat(33), "张三")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.employeeNo").isNotEmpty());
        create(validPayload("E".repeat(32), "张三")).andExpect(status().isCreated());
        create(validPayload("E-LEN-2", "名".repeat(51))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
        create(validPayload("E-LEN-3", "名".repeat(50))).andExpect(status().isCreated());
    }

    @Test
    void invalidOptionalFieldsReportOnlyThatField() throws Exception {
        Map<String, Object> invalidGender = validPayload("E-G-1", "张三");
        invalidGender.put("gender", "UNKNOWN");
        create(invalidGender)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.gender").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.length()").value(1));

        Map<String, Object> invalidEmail = validPayload("E-G-2", "张三");
        invalidEmail.put("email", "abc");
        create(invalidEmail)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.length()").value(1));

        Map<String, Object> invalidPhone = validPayload("E-G-3", "张三");
        invalidPhone.put("phone", "abc");
        create(invalidPhone)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.length()").value(1));

        Map<String, Object> invalidHireDate = validPayload("E-G-4", "张三");
        invalidHireDate.put("hireDate", "2026-13-01");
        create(invalidHireDate)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.hireDate").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.length()").value(1));
    }

    @Test
    void duplicateEmployeeNoReturnsConflict() throws Exception {
        create(validPayload("E2001", "张三")).andExpect(status().isCreated());
        MvcResult second = create(validPayload("E2002", "李四")).andExpect(status().isCreated()).andReturn();
        long secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asLong();

        create(validPayload("E2001", "王五"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_NO_DUPLICATE"));

        update(secondId, validPayload("E2001", "李四"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_NO_DUPLICATE"));

        update(secondId, validPayload("E2002", "李四"))
                .andExpect(status().isOk());
    }

    @Test
    void updateMissingOrInvalidIdReturnsUnifiedErrors() throws Exception {
        update(999999L, validPayload("E3001", "张三"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(put("/api/employees/abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPayload("E3002", "张三"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.id").isNotEmpty());
    }

    @Test
    void updateReturnsNewValuesAndKeepsTimestampsOrdered() throws Exception {
        MvcResult created = create(validPayload("E4001", "张三"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createdBody = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = createdBody.get("id").asLong();

        Map<String, Object> changes = validPayload("E4001", "张三改");
        changes.put("department", "研发部");

        MvcResult updated = update(id, changes)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("张三改"))
                .andExpect(jsonPath("$.department").value("研发部"))
                .andReturn();

        JsonNode updatedBody = objectMapper.readTree(updated.getResponse().getContentAsString());
        LocalDateTime createdAt = LocalDateTime.parse(updatedBody.get("createdAt").asText());
        LocalDateTime updatedAt = LocalDateTime.parse(updatedBody.get("updatedAt").asText());
        assertThat(updatedAt).isAfterOrEqualTo(createdAt);

        mockMvc.perform(get("/api/employees").param("keyword", "E4001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("张三改"))
                .andExpect(jsonPath("$.items[0].department").value("研发部"));
    }

    @Test
    void listParameterRulesFollowSpec() throws Exception {
        create(validPayload("E5001", "张三")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/employees").param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.size").isNotEmpty());

        mockMvc.perform(get("/api/employees").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/employees").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/employees").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void paginationIsStableAndFiltersWork() throws Exception {
        create(validPayload("EMP-001", "张三"));
        Map<String, Object> li = validPayload("EMP-002", "李四");
        li.put("department", "市场部");
        create(li);
        Map<String, Object> wang = validPayload("EMP-003", "王五");
        wang.put("department", "技术部");
        create(wang);

        MvcResult firstPage = mockMvc.perform(get("/api/employees").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();

        MvcResult secondPage = mockMvc.perform(get("/api/employees").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();

        Set<Long> ids = new HashSet<>();
        for (JsonNode item : objectMapper.readTree(firstPage.getResponse().getContentAsString()).get("items")) {
            ids.add(item.get("id").asLong());
        }
        for (JsonNode item : objectMapper.readTree(secondPage.getResponse().getContentAsString()).get("items")) {
            ids.add(item.get("id").asLong());
        }
        assertThat(ids).hasSize(3);

        mockMvc.perform(get("/api/employees").param("keyword", "张"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].employeeNo").value("EMP-001"));

        mockMvc.perform(get("/api/employees").param("keyword", "EMP-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/api/employees").param("department", "技术部"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].employeeNo").value("EMP-003"));

        mockMvc.perform(get("/api/employees").param("keyword", "不存在的人"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void emptyDatabaseReturnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));
    }

    private ResultActions create(Map<String, Object> payload) throws Exception {
        return mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));
    }

    private ResultActions update(long id, Map<String, Object> payload) throws Exception {
        return mockMvc.perform(put("/api/employees/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));
    }

    private Map<String, Object> validPayload(String employeeNo, String name) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeNo", employeeNo);
        payload.put("name", name);
        return payload;
    }
}
