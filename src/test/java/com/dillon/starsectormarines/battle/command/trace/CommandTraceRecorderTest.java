package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTraceRecorderTest {

    @Test
    void perspectiveRowsAreCanonicalSortedAndDeduplicated() {
        CommandDirective later = directive(9, "later");
        CommandDirective earlier = directive(2, "quote \" and slash \\");
        CommanderSnapshot<Void> snapshot = new CommanderSnapshot<>(
                Faction.MARINE, "conquest", "LANE_ADVANCE", 75, 60,
                2, 0, List.of("remaining compounds=3"),
                List.of(later, earlier), null);
        CommandTraceRecorder recorder = new CommandTraceRecorder(
                "CONQUEST", "SERIAL_DETERMINISTIC", 0);

        recorder.recordPerspective(snapshot);
        recorder.recordPerspective(snapshot);

        List<String> lines = recorder.canonicalJsonLines().lines().toList();
        assertEquals(2, lines.size());
        assertEquals("{\"stream\":\"run\",\"tick\":0,\"schemaVersion\":1,"
                + "\"fixtureKind\":\"CONQUEST\","
                + "\"schedulerMode\":\"SERIAL_DETERMINISTIC\"}", lines.get(0));
        String line = lines.get(1);
        assertEquals("{\"stream\":\"perspective\",\"tick\":75,"
                        + "\"perspective\":\"MARINE\",\"strategy\":\"conquest\","
                        + "\"phase\":\"LANE_ADVANCE\",\"influenceTick\":60,"
                        + "\"commandPoolSize\":2,\"reserveCount\":0,"
                        + "\"objectives\":[\"remaining compounds=3\"],"
                        + "\"directives\":["
                        + directiveJson(2, "quote \\\" and slash \\\\" ) + ","
                        + directiveJson(9, "later") + "]}", line);
        assertTrue(recorder.canonicalJsonLines().endsWith(line + "\n"));
    }

    @Test
    void eachPerspectiveHasItsOwnDeduplicationCursor() {
        CommandTraceRecorder recorder = new CommandTraceRecorder(
                null, "TEST", 12);
        recorder.recordPerspective(snapshot(Faction.DEFENDER, 75));
        recorder.recordPerspective(snapshot(Faction.MARINE, 75));
        recorder.recordPerspective(snapshot(Faction.DEFENDER, 75));

        List<String> lines = recorder.canonicalJsonLines().lines().toList();
        assertEquals(3, lines.size());
        assertTrue(lines.get(1)
                .contains("\"perspective\":\"DEFENDER\""));
        assertTrue(lines.get(2)
                .contains("\"perspective\":\"MARINE\""));
    }

    private static CommanderSnapshot<Void> snapshot(Faction side, int tick) {
        return new CommanderSnapshot<>(side, "conquest", "phase", tick, tick,
                0, 0, List.of(), List.of(), null);
    }

    private static CommandDirective directive(int squadId, String reason) {
        return new CommandDirective(squadId, Faction.MARINE, "conquest",
                CommandAuthority.MISSION_COMMAND, reason,
                ObjectiveAssignment.clearZone(squadId, 4), 75, 150, 225,
                CommandDirective.Status.ACTIVE, "committed");
    }

    private static String directiveJson(int squadId, String escapedReason) {
        return "{\"squadId\":" + squadId
                + ",\"issuer\":\"conquest\",\"authority\":\"MISSION_COMMAND\""
                + ",\"status\":\"ACTIVE\",\"reason\":\"" + escapedReason + "\""
                + ",\"disposition\":\"committed\",\"issuedTick\":75"
                + ",\"stableUntilTick\":150,\"leaseUntilTick\":225"
                + ",\"assignment\":{\"kind\":\"CLEAR_ZONE\",\"targetZoneId\":4"
                + ",\"targetNode\":null,\"objectiveId\":-1"
                + ",\"targetCellX\":-1,\"targetCellY\":-1}}";
    }
}
