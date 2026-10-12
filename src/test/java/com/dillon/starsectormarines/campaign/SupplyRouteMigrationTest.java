package com.dillon.starsectormarines.campaign;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.converters.basic.StringConverter;
import com.thoughtworks.xstream.converters.basic.IntConverter;
import com.thoughtworks.xstream.converters.basic.BooleanConverter;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SupplyRouteMigrationTest {
    private static void field(BrokenSupplyRouteProgress p, String name, Object value) throws Exception {
        var f = BrokenSupplyRouteProgress.class.getDeclaredField(name);
        f.setAccessible(true); f.set(p, value);
    }
    private static BrokenSupplyRouteProgress legacyRelay() throws Exception {
        var p = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        String id = p.missionId(); p.begin(id); p.settle(id, true, null); p.commit(3);
        field(p, "version", 1); field(p, "freight", null);
        return p;
    }
    private static BrokenSupplyRouteProgress reload(BrokenSupplyRouteProgress p) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(p); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (BrokenSupplyRouteProgress) in.readObject();
        }
    }
    @Test void pendingLegacyRelayAdvancesWithoutInventingAWonBattleOrPayment() throws Exception {
        var old = legacyRelay(); String oldId = old.missionId();
        var p = reload(old);
        assertEquals(4, p.operation()); assertEquals(3, p.branch());
        assertEquals(12000, p.storyPayment()); assertEquals(List.of(0,1,2), p.freight().waiting());
        assertFalse(p.matches(oldId)); assertFalse(p.commit(2)); assertFalse(p.successful());
        assertEquals(old.lastMissionId(), p.lastMissionId());
        assertEquals(p.missionId(), reload(p).missionId());
    }
    @Test void activeLegacyRelayHonorsItsFrozenAttemptAndThenSkipsFurtherRelayRetries() throws Exception {
        for (boolean victory : List.of(false, true)) {
            var old = legacyRelay(); String id = old.missionId(); assertTrue(old.begin(id));
            var p = reload(old);
            assertEquals(3, p.operation()); assertEquals(id, p.missionId()); assertFalse(p.begin(id));
            assertEquals(victory ? 16000 : 0, p.settle(id, victory, null));
            assertEquals(4, p.operation()); assertEquals(victory ? 28000 : 12000, p.storyPayment());
            assertEquals(-1, p.settle(id, victory, null));
            assertEquals(4, reload(p).operation());
        }
    }
    @Test void existingFreightReceiptsAndClosedRecurringEntitlementsSurvive() throws Exception {
        var old = legacyRelay(); String id = old.missionId(); old.begin(id); old.settle(id, true, null);
        id = old.missionId(); old.begin(id);
        old.settle(id, false, null, new FreightRunProgress.Outcome(old.freight().previewAttempt(), List.of(0), List.of(1), false));
        field(old, "version", 1);
        var p = reload(old);
        assertEquals(List.of(2), p.freight().waiting()); assertEquals(1, p.freight().deliveredCount());
        assertEquals(1, p.freight().lostCount()); assertEquals(28000, p.storyPayment());
        old = legacyRelay();
        field(old, "closed", true); field(old, "successful", true); field(old, "job", 1); field(old, "storyPayment", 28000);
        p = reload(old);
        assertTrue(p.available()); assertEquals(3, p.operation()); assertEquals(1, p.job());
        assertNull(p.freight()); assertEquals(28000, p.storyPayment());
    }
    @Test void reflectionCampaignLoaderAlsoMigratesAndOldJavaIdentityIsRetained() throws Exception {
        assertEquals(4282145429580416505L, ObjectStreamClass.lookup(BrokenSupplyRouteProgress.class).getSerialVersionUID());
        var codec = new XStream(new StaxDriver()) {
            @Override protected void setupConverters() {
                registerConverter(new ReflectionConverter(getMapper(), getReflectionProvider()), PRIORITY_VERY_LOW);
                registerConverter(new StringConverter()); registerConverter(new IntConverter()); registerConverter(new BooleanConverter());
            }
        };
        XStream.setupDefaultSecurity(codec);
        codec.allowTypes(new Class[]{BrokenSupplyRouteProgress.class, FreightRunProgress.class});
        var old = legacyRelay();
        var p = (BrokenSupplyRouteProgress) codec.fromXML(codec.toXML(old));
        assertTrue(p.readable()); assertEquals(4, p.operation()); assertEquals(12000, p.storyPayment());
        String id = old.missionId(); old.begin(id);
        p = (BrokenSupplyRouteProgress) codec.fromXML(codec.toXML(old));
        assertEquals(id, p.missionId()); assertEquals(0, p.settle(id, false, null)); assertEquals(4, p.operation());
    }
}
