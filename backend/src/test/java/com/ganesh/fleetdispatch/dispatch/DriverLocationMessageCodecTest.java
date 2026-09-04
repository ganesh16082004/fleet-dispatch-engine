package com.ganesh.fleetdispatch.dispatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DriverLocationMessageCodecTest {
    private final DriverLocationMessageCodec codec = new DriverLocationMessageCodec();

    @Test
    void shouldDecodeValidLocationMessage() {
        DriverLocationPayload payload = codec.decode(
                "{\"sequenceNumber\":42,\"nodeId\":123,\"timestampMillis\":9000}");

        assertEquals(42L, payload.sequenceNumber());
        assertEquals(123L, payload.nodeId());
        assertEquals(9000L, payload.timestampMillis());
    }

    @Test
    void shouldRejectMissingField() {
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode("{\"sequenceNumber\":42,\"nodeId\":123}"));
    }

    @Test
    void shouldRejectMalformedJson() {
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode("not-json"));
    }

    @Test
    void shouldRejectNonIntegralSequence() {
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(
                        "{\"sequenceNumber\":1.5,\"nodeId\":123,\"timestampMillis\":9000}"));
    }
}
