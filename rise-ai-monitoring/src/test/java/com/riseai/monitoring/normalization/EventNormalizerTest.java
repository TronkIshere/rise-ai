package com.riseai.monitoring.normalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.dto.request.event.MonitoringEventRequest;
import com.riseai.monitoring.exception.ApplicationException;
import com.riseai.monitoring.exception.ErrorCode;
import com.riseai.monitoring.testsupport.MutableClock;
import com.riseai.monitoring.testsupport.TestEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MonitoringProperties properties;
    private EventNormalizer normalizer;

    @BeforeEach
    void setUp() {
        properties = TestEvents.properties();
        normalizer = new EventNormalizer(objectMapper, properties, new MutableClock(TestEvents.NOW));
    }

    @Test
    void normalizesIdentifiersAttributesAndReceiveTime() {
        MonitoringEventRequest request = TestEvents.request("evt-1");
        request.setEventType(" Request.Completed ");
        request.setSource(" edge-gateway ");

        MonitoringEvent event = normalizer.normalize(request, "corr-1");

        assertThat(event.getEventType()).isEqualTo("request.completed");
        assertThat(event.getSource()).isEqualTo("edge-gateway");
        assertThat(event.getReceivedAt()).isEqualTo(TestEvents.NOW);
        assertThat(event.getCorrelationId()).isEqualTo("corr-1");
        assertThat(event.getAttributes().keySet()).containsExactly("latencyMs", "region");
        assertThat(event.getPayloadBytes()).isPositive();
        assertThat(event.getFingerprint()).hasSize(64);
    }

    @Test
    void prefersCorrelationIdSuppliedWithTheEvent() {
        MonitoringEventRequest request = TestEvents.request("evt-1");
        request.setCorrelationId("order-42");

        assertThat(normalizer.normalize(request, "fallback").getCorrelationId()).isEqualTo("order-42");
    }

    @Test
    void fingerprintIgnoresDeliveryMetadataAndKeyOrder() {
        MonitoringEventRequest first = TestEvents.request("evt-1");
        MonitoringEventRequest second = TestEvents.request("evt-1");
        second.setReceivedAt(TestEvents.NOW.minusSeconds(5));
        second.setCorrelationId("another-correlation");
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("latencyMs", 120);
        reordered.put("region", "eu-west");
        second.setAttributes(reordered);
        second.setPayload(JsonNodeFactory.instance.objectNode().put("status", 200).put("path", "/orders"));

        assertThat(normalizer.normalize(second, "b").getFingerprint())
                .isEqualTo(normalizer.normalize(first, "a").getFingerprint());
    }

    @Test
    void fingerprintChangesWhenContentChanges() {
        MonitoringEventRequest changed = TestEvents.request("evt-1");
        changed.setPayload(JsonNodeFactory.instance.objectNode().put("path", "/orders").put("status", 500));

        assertThat(normalizer.normalize(changed, "a").getFingerprint())
                .isNotEqualTo(normalizer.normalize(TestEvents.request("evt-1"), "a").getFingerprint());
    }

    @Test
    void rejectsNestedAttributeValues() {
        MonitoringEventRequest request = TestEvents.request("evt-1");
        request.getAttributes().put("nested", Map.of("key", 1));

        assertThatThrownBy(() -> normalizer.normalize(request, "a"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("attribute 'nested' must be a string, number or boolean")
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_EVENT);
    }

    @Test
    void rejectsOverlongAttributeValues() {
        properties.getLimits().setMaxAttributeValueLength(10);
        MonitoringEventRequest request = TestEvents.request("evt-1");
        request.getAttributes().put("note", "x".repeat(11));

        assertThatThrownBy(() -> normalizer.normalize(request, "a"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("attribute 'note' exceeds 10 characters");
    }

    @Test
    void rejectsPayloadsNestedBeyondTheLimit() throws Exception {
        properties.getLimits().setMaxPayloadDepth(3);
        MonitoringEventRequest request = TestEvents.request("evt-1");
        request.setPayload(objectMapper.readTree("{\"a\":{\"b\":{\"c\":{\"d\":1}}}}"));

        assertThatThrownBy(() -> normalizer.normalize(request, "a"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("payload nesting exceeds 3 levels");
    }

    @Test
    void treatsJsonNullPayloadAsAbsent() {
        MonitoringEventRequest request = TestEvents.request("evt-1");
        request.setPayload(NullNode.getInstance());

        MonitoringEvent event = normalizer.normalize(request, "a");

        assertThat(event.getPayload()).isNull();
        assertThat(event.getPayloadBytes()).isZero();
    }
}
