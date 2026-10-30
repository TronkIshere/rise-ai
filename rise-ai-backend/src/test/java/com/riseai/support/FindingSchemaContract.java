package com.riseai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class FindingSchemaContract {
    public static final Path SCHEMA_PATH = Path.of("..", "contracts", "findings", "finding.v1.schema.json");
    private static final JsonSchema INSTANCE = load();

    private FindingSchemaContract() {
    }

    public static Set<ValidationMessage> validate(JsonNode finding) {
        return INSTANCE.validate(finding);
    }

    public static Set<ValidationMessage> validateText(String json) {
        return INSTANCE.validate(json, InputFormat.JSON);
    }

    private static JsonSchema load() {
        try {
            JsonNode schema = new ObjectMapper().readTree(Files.readString(SCHEMA_PATH));
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schema, new SchemaValidatorsConfig());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
