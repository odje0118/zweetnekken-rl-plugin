package com.zweetnekken.service;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Parses ISO-8601 timestamps returned by the Zweetnekken backend.
 *
 * OffsetDateTime is used instead of Instant.parse because older Java runtimes
 * do not accept a numeric offset such as +00:00 in Instant.parse.
 */
final class BackendClockTimestampParser
{
    private BackendClockTimestampParser()
    {
    }

    static Instant parse(String timestamp)
    {
        return OffsetDateTime.parse(timestamp.trim()).toInstant();
    }
}
