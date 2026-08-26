package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.Termination;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SabotageCommandTraceAnalyzerTest {

    @Test
    void reportsCoverageRoleTransitionsProgressAndTimeout() throws Exception {
        String trace = header("SABOTAGE")
                + perspective(75, "PLANTER", "PLANTER_ACTIVE", 1, 0, false)
                + chargeState(75, 2_500, false)
                + perspective(150, "KIT_RETRIEVER", "KIT_RECOVERY_ASSIGNED",
                0, 1, false)
                + chargeState(150, 4_000, false)
                + perspective(180, "KIT_RETRIEVER", "KIT_RECOVERY_UNSUPPORTED",
                0, 0, false)
                + perspective(225, "PLANTER", "PLANTER_ACTIVE", 1, 0, false)
                + chargeState(225, 10_000, true)
                + "{\"stream\":\"referee\",\"tick\":300,"
                + "\"event\":\"timeout\",\"maxTicks\":300}\n";

        SabotageCommandTraceAnalyzer.Analysis analysis =
                SabotageCommandTraceAnalyzer.analyze(trace);

        assertEquals(Termination.TIMEOUT, analysis.run().termination());
        assertEquals(300, analysis.run().durationTicks());
        assertEquals(3, analysis.sabotage().siteCount());
        assertEquals(1, analysis.sabotage().completedSites());
        assertEquals(4, analysis.sabotage().coverage().commandSamples());
        assertEquals(0, analysis.sabotage().coverage()
                .minimumPlanterOrRetrieverSites());
        assertEquals(1, analysis.sabotage().coverage()
                .maximumPlanterOrRetrieverSites());
        assertEquals(2, analysis.sabotage().coverage().minimumSecuritySites());
        assertEquals(2, analysis.sabotage().coverage().maximumSecuritySites());
        assertEquals(0, analysis.sabotage().coverage()
                .samplesWithEveryUnfinishedSiteSupported());
        assertEquals(1, analysis.sabotage().roleTransitions()
                .retrieverToPlanter());
        assertEquals(1, analysis.sabotage().roleTransitions()
                .planterToRetriever());
        assertEquals(1, analysis.sabotage().roleTransitions()
                .sitePlanterLosses());
        assertEquals(1, analysis.sabotage().roleTransitions()
                .siteRecoveryStarts());
        assertEquals(1, analysis.sabotage().roleTransitions()
                .siteRecoverySupportChanges());
        assertEquals(1, analysis.sabotage().roleTransitions()
                .siteRecoveryToPlanter());
        assertEquals(10_000, analysis.sabotage().sites().get("SAB-01")
                .maximumProgressBasisPoints());
        assertEquals(225, analysis.sabotage().sites().get("SAB-01")
                .completionTick());
        assertTrue(analysis.canonicalJson()
                .contains("\"termination\":\"TIMEOUT\""));
        assertTrue(analysis.canonicalJson()
                .contains("\"retrieverToPlanter\":1"));
    }

    @Test
    void rejectsAnotherMissionKind() throws Exception {
        String trace = header("CONQUEST")
                + perspective(75, "PLANTER", "PLANTER_ACTIVE", 1, 0, false)
                + "{\"stream\":\"referee\",\"tick\":75,"
                + "\"event\":\"timeout\",\"maxTicks\":75}\n";

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SabotageCommandTraceAnalyzer.analyze(trace));

        assertTrue(error.getMessage().contains("SABOTAGE fixture"));
    }

    private static String header(String kind) {
        return "{\"stream\":\"run\",\"tick\":0,\"schemaVersion\":4,"
                + "\"fixtureKind\":\"" + kind + "\","
                + "\"schedulerMode\":\"SERIAL_DETERMINISTIC\"}\n";
    }

    private static String perspective(int tick, String role, String groupReason,
                                      int planterSquads,
                                      int retrieverSquads,
                                      boolean siteComplete) throws Exception {
        JSONObject assignment = new JSONObject()
                .put("kind", "SABOTAGE_SITE")
                .put("targetZoneId", 4)
                .put("targetNode", JSONObject.NULL)
                .put("objectiveId", 0)
                .put("targetCellX", 20)
                .put("targetCellY", 30);
        JSONObject directive = new JSONObject()
                .put("squadId", 7)
                .put("issuer", "sabotage")
                .put("authority", "MISSION")
                .put("status", "ACTIVE")
                .put("reason", "SITE_SECURITY_ASSIGNED")
                .put("disposition", "accepted")
                .put("issuedTick", tick)
                .put("stableUntilTick", tick + 75)
                .put("leaseUntilTick", -1)
                .put("assignment", assignment);
        JSONArray sites = new JSONArray()
                .put(site(0, "SAB-01", siteComplete, planterSquads,
                        retrieverSquads, 1, groupReason))
                .put(site(1, "SAB-02", false, 0, 0, 1,
                        "AWAITING_PLANTER"))
                .put(site(2, "SAB-03", false, 0, 0, 0,
                        "AWAITING_PLANTER"));
        JSONObject action = new JSONObject()
                .put("squadId", 7)
                .put("siteIndex", 0)
                .put("groupRole", role)
                .put("reason", "SITE_SECURITY_ASSIGNED")
                .put("assignmentKind", "SABOTAGE_SITE")
                .put("targetZoneId", 4)
                .put("markerCellX", 20)
                .put("markerCellY", 30);
        JSONObject sabotage = new JSONObject()
                .put("phase", siteComplete ? "PLANTING" : "SITE_APPROACH")
                .put("sites", sites)
                .put("actions", new JSONArray().put(action));
        return new JSONObject()
                .put("stream", "perspective")
                .put("tick", tick)
                .put("observedTick", tick)
                .put("perspective", "MARINE")
                .put("strategy", "sabotage")
                .put("phase", "SITE_APPROACH")
                .put("influenceTick", tick)
                .put("commandPoolSize", 1)
                .put("reserveCount", 0)
                .put("objectives", new JSONArray())
                .put("directives", new JSONArray().put(directive))
                .put("sabotage", sabotage)
                .toString() + '\n';
    }

    private static JSONObject site(int index, String id, boolean complete,
                                   int planterSquads, int retrieverSquads,
                                   int securitySquads, String groupReason)
            throws Exception {
        return new JSONObject()
                .put("index", index)
                .put("id", id)
                .put("complete", complete)
                .put("planterSquads", planterSquads)
                .put("retrieverSquads", retrieverSquads)
                .put("securitySquads", securitySquads)
                .put("groupReason", groupReason);
    }

    private static String chargeState(int tick, int progress,
                                      boolean complete) throws Exception {
        return new JSONObject()
                .put("stream", "referee")
                .put("tick", tick)
                .put("event", "charge-site-state")
                .put("siteId", "SAB-01")
                .put("cellX", 20)
                .put("cellY", 30)
                .put("progressBasisPoints", progress)
                .put("planterOnSite", progress > 0)
                .put("complete", complete)
                .toString() + '\n';
    }
}
