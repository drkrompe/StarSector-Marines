package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HostileShipIntelTest {

    private static final Map<String, String> TEXT = Map.of(
            HostileShipIntel.KEY_COMING, "{0}, {1}, with the {2} - expect it over the battle.",
            HostileShipIntel.KEY_COMING_ALONE, "{0}, {1} - expect it over the battle.",
            HostileShipIntel.KEY_HELD, "{0}, {1}, with the {2}, is {3}; it will not answer this battle.",
            HostileShipIntel.KEY_ABSENT, "No hostile capital in the system.",
            HostileFleetStatus.IN_BATTLE.stringsKey, "engaged in battle");
    private static final UnaryOperator<String> STRINGS = k -> TEXT.getOrDefault(k, k);

    private static final ShipSupportSource SOURCE = new ShipSupportSource("onslaught", "HSS Anvil", false, Map.of());

    @Test
    void aShipThatWillComeIsNamedWithItsFleet() {
        HostileShipReport r = new HostileShipReport(HostileShipReport.Outcome.COMING, SOURCE, "HSS Anvil",
                "Onslaught", "Kanta Defense Fleet", null);
        assertEquals("HSS Anvil, Onslaught, with the Kanta Defense Fleet - expect it over the battle.",
                HostileShipIntel.line(r, STRINGS));
    }

    @Test
    void aHeldShipSaysWhyItWillNotComeSoKeepingItBusyIsVisible() {
        HostileShipReport r = new HostileShipReport(HostileShipReport.Outcome.HELD, null, "HSS Anvil",
                "Onslaught", "Kanta Defense Fleet", HostileFleetStatus.IN_BATTLE);
        assertEquals("HSS Anvil, Onslaught, with the Kanta Defense Fleet, is engaged in battle; it will not answer this battle.",
                HostileShipIntel.line(r, STRINGS));
    }

    @Test
    void anAbsentShipIsSaidToBeAbsent() {
        assertEquals("No hostile capital in the system.", HostileShipIntel.line(HostileShipReport.ABSENT, STRINGS));
    }

    @Test
    void anUndefendedWorldOrNoReportSaysNothing() {
        assertNull(HostileShipIntel.line(HostileShipReport.UNDEFENDED, STRINGS));
        assertNull(HostileShipIntel.line(null, STRINGS));
    }

    @Test
    void aDeclaredShipHasNoFleetToName() {
        assertEquals("HSS Anvil, onslaught - expect it over the battle.",
                HostileShipIntel.line(HostileShipReport.declared(SOURCE), STRINGS));
    }
}
