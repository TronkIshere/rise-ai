package com.riseai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

public final class EventSchemaContract {
    private static final Path SCHEMA = Path.of("..", "contracts", "events", "rise-event-envelope.v1.schema.json");
    private static final JsonSchema INSTANCE = load();

    private EventSchemaContract() {
    }

    public static Set<ValidationMessage> validate(JsonNode envelope) {
        return INSTANCE.validate(envelope);
    }

    private static JsonSchema load() {
        try {
            JsonNode schema = new ObjectMapper().readTree(Files.readString(SCHEMA));
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schema, new SchemaValidatorsConfig());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
