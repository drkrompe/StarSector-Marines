package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.converters.basic.StringConverter;
import com.thoughtworks.xstream.converters.basic.IntConverter;
import com.thoughtworks.xstream.converters.basic.BooleanConverter;
import org.junit.jupiter.api.Test;
import com.dillon.starsectormarines.ops.MissionOutcome;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LastLiftProgressTest {
    @Test void saveCodecRetainsOutstandingAttemptAndRejectsUnknownVersions() throws Exception {
        var progress = new LastLiftProgress("jangala:rill");
        progress.settle(new LastLiftProgress.Outcome(progress.beginAttempt(),List.of(0),List.of(1),false));
        var outstanding = progress.beginAttempt();
        // Exercise the game's reflection codec for this primitive record without
        // initializing unrelated legacy JDK collection converters on the tool JDK.
        var codec = new XStream(new StaxDriver()) {
            @Override protected void setupConverters() {
                registerConverter(new ReflectionConverter(getMapper(),getReflectionProvider()),PRIORITY_VERY_LOW);
                registerConverter(new StringConverter());
                registerConverter(new IntConverter());
                registerConverter(new BooleanConverter());
            }
        };
        XStream.setupDefaultSecurity(codec);
        codec.allowTypes(new Class[]{LastLiftProgress.class});
        var restored = (LastLiftProgress) codec.fromXML(codec.toXML(progress));
        assertEquals(outstanding,restored.beginAttempt());
        assertEquals(1,restored.rescuedCount()); assertEquals(1,restored.deadCount());
        var version = LastLiftProgress.class.getDeclaredField("version");
        version.setAccessible(true); version.setInt(restored,99);
        assertThrows(IllegalStateException.class,restored::beginAttempt);
        assertEquals(0,restored.settle(new LastLiftProgress.Outcome(outstanding,List.of(2),List.of(),false)));
    }
    @Test void partialWithdrawalSurvivesSerializationAndOnlyRemainingPeopleRetry() throws Exception {
        CampaignState state = new CampaignState();
        state.lastLift = new LastLiftProgress("jangala:rill");
        var first = state.lastLift.beginAttempt();
        var retreat = new LastLiftProgress.Outcome(first,List.of(0,1,2),List.of(3),false);
        var missionOutcome = MissionOutcome.builder().lastLiftOutcome(retreat).build();
        assertSame(retreat,missionOutcome.lastLiftOutcome);
        assertEquals(0,state.lastLift.settle(missionOutcome.lastLiftOutcome));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(state); }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            state = (CampaignState) in.readObject();
        }
        assertEquals(List.of(4,5,6,7,8,9,10,11),state.lastLift.stranded());
        var second = state.lastLift.beginAttempt();
        assertEquals(second,state.lastLift.beginAttempt());
        assertEquals(0,state.lastLift.settle(retreat));
        var victory = new LastLiftProgress.Outcome(second,List.of(4,5,6,7,8,9,10),List.of(11),true);
        assertEquals(9000,state.lastLift.settle(victory));
        assertEquals(0,state.lastLift.settle(victory));
        assertEquals(10,state.lastLift.rescuedCount());
        assertEquals(2,state.lastLift.deadCount());
        assertTrue(state.lastLift.isClosed());
        assertThrows(IllegalStateException.class,state.lastLift::beginAttempt);
    }
    @Test void exhaustedFailedAttemptCannotClaimVictoryFromEarlierRescues() {
        var progress = new LastLiftProgress("rill");
        progress.settle(new LastLiftProgress.Outcome(progress.beginAttempt(),List.of(0,1),List.of(),false));
        progress.settle(new LastLiftProgress.Outcome(progress.beginAttempt(),List.of(),progress.stranded(),false));
        assertTrue(progress.isClosed()); assertFalse(progress.victorious()); assertEquals(0,progress.payment());
    }
    @Test void rejectsForeignAndMalformedOutcomes() {
        var progress = new LastLiftProgress("rill");
        var attempt = progress.beginAttempt();
        assertThrows(IllegalArgumentException.class,() -> new LastLiftProgress.Outcome(attempt,List.of(0),List.of(0),false));
        assertThrows(IllegalArgumentException.class,() -> new LastLiftProgress.Outcome(attempt,List.of(0),List.of(),true));
        assertEquals(0,progress.settle(new LastLiftProgress.Outcome(new LastLiftProgress("elsewhere").beginAttempt(),List.of(0),List.of(),false)));
        assertEquals(12,progress.stranded().size());
    }
    @Test void deathIsDistinctFromUnsavedAtTerminalBoundary() {
        var tracker = new CivilianEvacuationTracker(3);
        tracker.register(11); tracker.register(12); tracker.register(13);
        tracker.markEvacuated(11); tracker.markDead(12); tracker.seal();
        assertTrue(tracker.isConfirmedDead(12)); assertFalse(tracker.isConfirmedDead(13));
        assertEquals(2,tracker.report().lost); // Existing representative rescue result remains compatible.
        assertFalse(tracker.markDead(13));
    }
}
