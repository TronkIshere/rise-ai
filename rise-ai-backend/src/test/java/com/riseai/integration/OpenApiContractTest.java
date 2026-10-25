package com.riseai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.riseai.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OpenApiContractTest extends AbstractMySqlIntegrationTest {
    static final Path SNAPSHOT = Path.of("docs", "api", "openapi-v1.json");
    static final String UPDATE_FLAG = "openapi.snapshot.update";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void liveContractMatchesTheCommittedSnapshot() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode live = sorted(objectMapper.readTree(body));
        ((ObjectNode) live).remove("servers");

        if (Boolean.getBoolean(UPDATE_FLAG)) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(live) + System.lineSeparator());
        }

        assertThat(SNAPSHOT).as("API snapshot missing; run mvn test -Dtest=OpenApiContractTest -D%s=true", UPDATE_FLAG).exists();
        JsonNode committed = objectMapper.readTree(SNAPSHOT.toFile());
        assertThat(live).as("API contract changed; review the diff and regenerate %s with -D%s=true", SNAPSHOT, UPDATE_FLAG)
                .isEqualTo(committed);
    }

    private JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            names.forEach(name -> result.set(name, sorted(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(element -> result.add(sorted(element)));
            return result;
        }
        return node;
    }
}
