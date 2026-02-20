package com.bko.orchestration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonProcessingServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonProcessingService service = new JsonProcessingService(objectMapper);

    static record TestBean(String name, int age) {}

    @Test
    void testParseJsonResponse() {
        String raw = "Here is the result: {\"name\":\"John\", \"age\":30} and some extra text.";
        TestBean bean = service.parseJsonResponse("test", raw, TestBean.class);
        assertNotNull(bean);
        assertEquals("John", bean.name());
        assertEquals(30, bean.age());
    }

    @Test
    void testParseJsonResponseDirect() {
        String raw = "{\"name\":\"Jane\", \"age\":25}";
        TestBean bean = service.parseJsonResponse("test", raw, TestBean.class);
        assertNotNull(bean);
        assertEquals("Jane", bean.name());
        assertEquals(25, bean.age());
    }

    @Test
    void testParseEmptyResponse() {
        TestBean bean = service.parseJsonResponse("test", "", TestBean.class);
        assertNull(bean);
    }

    @Test
    void testParseInvalidJson() {
        String raw = "{invalid-json}";
        TestBean bean = service.parseJsonResponse("test", raw, TestBean.class);
        assertNull(bean);
    }

    @Test
    void testToJson() {
        TestBean bean = new TestBean("Alice", 20);
        String json = service.toJson(bean);
        assertTrue(json.contains("\"name\" : \"Alice\""));
        assertTrue(json.contains("\"age\" : 20"));
    }
}
