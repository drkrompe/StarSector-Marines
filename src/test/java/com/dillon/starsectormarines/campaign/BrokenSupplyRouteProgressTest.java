package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.converters.basic.StringConverter;
import com.thoughtworks.xstream.converters.basic.IntConverter;
import com.thoughtworks.xstream.converters.basic.BooleanConverter;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BrokenSupplyRouteProgressTest {
    @Test void junctionRetriesThenOpensThunderRunExactlyOnce() {
        var p = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        String failed = p.missionId();
        assertTrue(p.begin(failed)); assertFalse(p.begin(failed));
        assertEquals(0, p.settle(failed, false, null));
        assertEquals(-1, p.settle(failed, true, null));
        assertEquals(BrokenSupplyRouteProgress.JUNCTION, p.operation());
        String won = p.missionId(); assertNotEquals(failed, won); assertTrue(p.begin(won));
        assertEquals(12000, p.settle(won, true, null)); assertEquals(-1, p.settle(won, true, null));
        assertEquals(BrokenSupplyRouteProgress.THUNDER, p.operation());
        assertEquals(List.of(0, 1, 2), p.freight().waiting());
        assertNotNull(p.missionId()); assertFalse(p.closed());
        assertFalse(p.successful()); assertEquals(0, p.job()); assertEquals(12000, p.storyPayment());
    }
    @Test void recurringJobsNeverReplayTheStoryAwardOrFreight() {
        var p = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        String id = p.missionId(); p.begin(id); p.settle(id, true, null);
        String convoy = p.missionId(); p.begin(convoy);
        var cargo = new FreightRunProgress.Outcome(p.freight().previewAttempt(), List.of(0, 1, 2), List.of(), true);
        assertEquals(18000, p.settle(convoy, true, null, cargo));
        assertTrue(p.closed()); assertTrue(p.successful()); assertEquals(30000, p.storyPayment());
        assertEquals(BrokenSupplyRouteProgress.RELAY, p.operation());
        String repeat = p.missionId(); assertNotEquals(convoy, repeat); p.begin(repeat);
        assertEquals(-1, p.settle(convoy, true, null, cargo));
        assertEquals(-1, p.settle(repeat, true, null, cargo));
        assertEquals(12000, p.settle(repeat, true, null)); assertEquals(-1, p.settle(repeat, true, null));
        assertEquals(12000, p.recurringPayment()); assertEquals(1, p.jobsCompleted());
        assertEquals(3, p.freight().deliveredCount());
    }
    @Test void rescueResultsCannotConsumeASupplyRouteAttempt() {
        var p = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        String id = p.missionId(); p.begin(id);
        var rescue = new LastLiftProgress("detached");
        var result = new LastLiftProgress.Outcome(rescue.beginAttempt(), List.of(0), List.of(), false);
        assertEquals(-1, p.settle(id, false, result));
        assertEquals(12000, p.settle(id, true, null));
    }
    @Test void reflectionSaveRetainsTheActiveConvoyAndRefusesDuplicateSettlement() {
        var p = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        String junction = p.missionId(); p.begin(junction); p.settle(junction, true, null);
        String convoy = p.missionId(); assertTrue(p.begin(convoy));
        var codec = new XStream(new StaxDriver()) {
            @Override protected void setupConverters() {
                registerConverter(new ReflectionConverter(getMapper(), getReflectionProvider()), PRIORITY_VERY_LOW);
                registerConverter(new StringConverter()); registerConverter(new IntConverter()); registerConverter(new BooleanConverter());
            }
        };
        XStream.setupDefaultSecurity(codec);
        codec.allowTypes(new Class[]{BrokenSupplyRouteProgress.class, FreightRunProgress.class});
        var copy = (BrokenSupplyRouteProgress) codec.fromXML(codec.toXML(p));
        assertTrue(copy.readable()); assertEquals(convoy, copy.missionId()); assertFalse(copy.begin(convoy));
        var outcome = new FreightRunProgress.Outcome(copy.freight().previewAttempt(), List.of(0, 1, 2), List.of(), true);
        assertEquals(18000, copy.settle(convoy, true, null, outcome));
        assertEquals(-1, copy.settle(convoy, true, null, outcome));
        assertEquals(30000, copy.storyPayment()); assertEquals(BrokenSupplyRouteProgress.RELAY, copy.operation());
    }
}
