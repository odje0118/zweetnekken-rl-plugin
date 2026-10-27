package com.zweetnekken.service;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventAlertServiceTest
{
    @Test
    void parsesBackendTimestampWithExplicitUtcOffsetAndFractionalSeconds()
    {
        assertEquals(
            Instant.parse("2026-10-26T18:34:42.821980Z"),
            BackendClockTimestampParser.parse("2026-10-26T18:34:42.821980+00:00"));
    }

    @Test
    void convertsNonUtcBackendOffsetToInstant()
    {
        assertEquals(
            Instant.parse("2026-10-26T17:34:42.821980Z"),
            BackendClockTimestampParser.parse("2026-10-26T18:34:42.821980+01:00"));
    }
}
