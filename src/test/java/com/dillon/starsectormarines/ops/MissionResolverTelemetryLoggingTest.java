package com.dillon.starsectormarines.ops;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionResolverTelemetryLoggingTest {

    private final Logger logger = Logger.getLogger(MissionResolver.class);
    private final RecordingAppender appender = new RecordingAppender();
    private Level originalLevel;
    private boolean originalAdditivity;

    @BeforeEach
    void attachAppender() {
        originalLevel = logger.getLevel();
        originalAdditivity = logger.getAdditivity();
        logger.setAdditivity(false);
        logger.addAppender(appender);
    }

    @AfterEach
    void restoreLogger() {
        logger.removeAppender(appender);
        logger.setLevel(originalLevel);
        logger.setAdditivity(originalAdditivity);
    }

    @Test
    void normalInfoLoggingDoesNotEmitPerUnitTelemetry() {
        logger.setLevel(Level.INFO);

        MissionResolver.logCombatTelemetry("debug:CONQUEST", Collections.emptyList());

        assertTrue(appender.events.isEmpty());
    }

    @Test
    void debugLoggingRetainsOptInBalanceReadout() {
        logger.setLevel(Level.DEBUG);

        MissionResolver.logCombatTelemetry("debug:CONQUEST", Collections.emptyList());

        assertEquals(1, appender.events.size());
        LoggingEvent event = appender.events.get(0);
        assertEquals(Level.DEBUG, event.getLevel());
        assertTrue(event.getRenderedMessage().startsWith(
                "MarineOps: combat telemetry for debug:CONQUEST" + System.lineSeparator()));
        assertTrue(event.getRenderedMessage().contains("faction"));
    }

    private static final class RecordingAppender extends AppenderSkeleton {

        private final List<LoggingEvent> events = new ArrayList<>();

        @Override
        protected void append(LoggingEvent event) {
            events.add(event);
        }

        @Override
        public void close() {
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }
}
