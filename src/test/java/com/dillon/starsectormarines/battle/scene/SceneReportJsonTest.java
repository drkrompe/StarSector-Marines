package com.dillon.starsectormarines.battle.scene;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneReportJsonTest {

    private static SceneReport sample() {
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("planlessTicks", 1);
        metrics.put("cellsTravelled", 12.5f);
        metrics.put("casualties", 0L);
        return new SceneReport("yield-freeze", "control", 1801,
                List.of(Verdict.pass("squad-holds-a-plan", "1 plan-less tick of 1801"),
                        Verdict.fail("squad-stays-put", "covered 12.5 cells, bar is 0.0")),
                metrics);
    }

    @Test
    void roundTripsThroughAFile(@TempDir Path dir) throws IOException {
        SceneReport written = sample();
        Path file = dir.resolve("yield-freeze").resolve("control.verdict.json");

        SceneReportJson.write(written, file);
        SceneReport read = SceneReportJson.read(file);

        assertEquals(written.sceneId(), read.sceneId());
        assertEquals(written.loopId(), read.loopId());
        assertEquals(written.ticks(), read.ticks());
        assertEquals(written.verdicts(), read.verdicts());
        assertFalse(read.passed());
        assertEquals(List.of("planlessTicks", "cellsTravelled", "casualties"),
                List.copyOf(read.metrics().keySet()));
        assertEquals(1, read.metrics().get("planlessTicks").intValue());
        assertEquals(12.5, read.metrics().get("cellsTravelled").doubleValue(), 1e-6);
        assertEquals(0, read.metrics().get("casualties").longValue());
    }

    @Test
    void keysAreWrittenInTheOrderAReaderWantsThem() {
        String json = SceneReportJson.toJson(sample());

        assertTrue(json.indexOf("\"sceneId\"") < json.indexOf("\"loopId\""), json);
        assertTrue(json.indexOf("\"loopId\"") < json.indexOf("\"ticks\""), json);
        assertTrue(json.indexOf("\"ticks\"") < json.indexOf("\"passed\""), json);
        assertTrue(json.indexOf("\"passed\"") < json.indexOf("\"verdicts\""), json);
        assertTrue(json.indexOf("\"verdicts\"") < json.indexOf("\"metrics\""), json);
    }

    @Test
    void aLoopWithNothingToSayStillRoundTrips() {
        SceneReport empty = new SceneReport("swarm-overkill", "main", 0, List.of(), Map.of());

        SceneReport read = SceneReportJson.fromJson(SceneReportJson.toJson(empty));

        assertEquals(empty, read);
        assertTrue(read.passed());
    }
}
