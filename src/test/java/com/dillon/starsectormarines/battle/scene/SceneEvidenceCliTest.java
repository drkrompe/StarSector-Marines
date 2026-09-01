package com.dillon.starsectormarines.battle.scene;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneEvidenceCliTest {

    private static final SceneReport PASSING = new SceneReport("yield-freeze", "subject", 1801,
            List.of(Verdict.pass("squad-holds-a-plan", "1 plan-less tick of 1801"),
                    Verdict.pass("squad-stays-put", "covered 0.0 cells")),
            Map.of());

    private static final SceneReport FAILING = new SceneReport("yield-freeze", "control", 1801,
            List.of(Verdict.pass("squad-holds-a-plan", "1 plan-less tick of 1801"),
                    Verdict.fail("squad-stays-put", "covered 12.5 cells, bar is 0.0")),
            Map.of());

    @Test
    void tableRowsCarryTheTallyAndFailingVerdictsExplainThemselves() {
        String table = SceneEvidenceCli.table(List.of(PASSING, FAILING));

        assertEquals(String.join("\n",
                "      scene         loop     verdicts",
                "PASS  yield-freeze  subject  2/2 verdicts",
                "FAIL  yield-freeze  control  1/2 verdicts",
                "        squad-stays-put: covered 12.5 cells, bar is 0.0",
                ""), table);
    }

    @Test
    void anEmptyCatalogStillPrintsATable() {
        assertEquals("      scene  loop  verdicts\n", SceneEvidenceCli.table(List.of()));
    }

    @Test
    void exitStatusIsOneWhenAnyVerdictFailed() {
        assertEquals(0, SceneEvidenceCli.exitStatus(List.of()));
        assertEquals(0, SceneEvidenceCli.exitStatus(List.of(PASSING)));
        assertEquals(1, SceneEvidenceCli.exitStatus(List.of(PASSING, FAILING)));
    }

    @Test
    void summaryJsonCarriesEveryLoopAndTheOverallAnswer(@TempDir Path dir) throws Exception {
        SceneEvidenceCli.writeSummary(List.of(PASSING, FAILING), dir);

        String json = Files.readString(dir.resolve("summary.json"), StandardCharsets.UTF_8);
        JSONObject summary = new JSONObject(json);
        assertFalse(summary.getBoolean("passed"));
        assertEquals(2, summary.getJSONArray("loops").length());
        assertEquals("subject", summary.getJSONArray("loops").getJSONObject(0)
                .getString("loopId"));
        assertTrue(Files.readString(dir.resolve("summary.md"), StandardCharsets.UTF_8)
                .contains("FAIL  yield-freeze  control"));
    }

    @Test
    void aSceneThatThrowsFailsItsOwnLoopRatherThanTheRun() {
        BehaviorScene broken = new BehaviorScene() {
            @Override public String id() { return "broken-scene"; }
            @Override public String label() { return "a scene that cannot stand its world up"; }
            @Override public List<SceneReport> play(FrameSink frames) {
                throw new IllegalStateException("no spawn cell for the objective");
            }
        };

        List<SceneReport> reports = SceneEvidenceCli.playScene(broken);

        assertEquals(1, reports.size());
        SceneReport report = reports.get(0);
        assertEquals("broken-scene", report.sceneId());
        assertEquals("main", report.loopId());
        assertFalse(report.passed());
        assertEquals(1, report.verdicts().size());
        assertEquals("played", report.verdicts().get(0).name());
        assertTrue(report.verdicts().get(0).detail().contains("no spawn cell for the objective"),
                report.verdicts().get(0).detail());
    }
}
