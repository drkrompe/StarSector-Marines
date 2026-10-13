package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.warship.PilotShipFixture;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipOrder;
import com.dillon.starsectormarines.battle.warship.WarshipPilot;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Carrier;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.ShipCard;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Snapshot;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Tone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** What the piloted hull's strip reads off the ship, and that it fits the shared action strip. */
class BattleDirectWarshipStatusTest {
    private Warship ship;

    @BeforeEach
    void fit() throws Exception {
        PilotShipFixture.install();
        ship = PilotShipFixture.ship(100f, 60f);
        ship.takeHelm();
    }

    @AfterEach
    void restore() {
        PilotShipFixture.restore();
    }

    private ShipCard card(String key, boolean gunsExposed) {
        for (ShipCard card : BattleDirectWarshipStatus.status(ship, gunsExposed, false).cards()) {
            if (card.key().equals(key)) return card;
        }
        return null;
    }

    @Test
    void theStripHasAGroupForEveryBatteryTheHullCarriesAndNoneForWhatItLacks() {
        assertEquals(List.of("HELM", "GUNS", "MISSILES", "PD", "PASS", "DIRECTOR"),
                BattleDirectWarshipStatus.status(ship, false, false).cards().stream().map(ShipCard::key).toList());
        assertNull(card("SHIELD", false), "no shield, no card");
        assertNull(card("LANCES", false), "no lance, no card");
    }

    @Test
    void aBatteryReadsIdleLaidDangerousOrHeldFromTheShipsOwnState() {
        assertEquals("IDLE", card("GUNS", false).state());
        assertEquals("HOLD LMB ON MAP", card("GUNS", false).detail());
        ship.layArea(Warship.FireGroup.GUNS, new WarshipOrder.FireOnArea(40f, 30f, WarshipPilot.AREA_RADIUS_CELLS, true));
        assertEquals("LAID", card("GUNS", false).state());
        assertEquals("AREA 40,30", card("GUNS", false).detail());
        assertEquals("DANGER", card("GUNS", true).state(), "a friendly in the blast is said, not hidden");
        assertEquals(Tone.WARN, card("GUNS", true).tone());
        assertEquals("IDLE", card("MISSILES", true).state(), "the other battery has nothing laid");
        ship.groundFire(Warship.GroundFire.HOLD);
        assertEquals("HOLD", card("GUNS", false).state());
    }

    @Test
    void theAutomationCardReadsTheDirectorPresetAndPolicy() {
        assertEquals("OFF", card("DIRECTOR", false).state());
        ship.pilotDirector(true);
        ShipCard director = card("DIRECTOR", false);
        assertEquals("ON", director.state());
        assertTrue(director.detail().contains(ship.missilePreset().name()));
        assertEquals("[4] ALL", director.resource());
        ship.cycleGroundFire();
        assertEquals("[4] HEAVY", card("DIRECTOR", false).resource());
    }

    @Test
    void fluxReadsOffUntilTheShipIsOnFlux() {
        var off = BattleDirectWarshipStatus.status(ship, false, false).flux();
        assertFalse(off.limited());
        assertEquals("FLUX OFF", off.label());
        ship.limitFlux();
        var on = BattleDirectWarshipStatus.status(ship, false, false).flux();
        assertTrue(on.limited());
        assertEquals("FLUX 0%", on.label());
    }

    @Test
    void durabilityIsTheHullsOwnAndArmourIsShownWholeBecauseItIsARating() {
        Snapshot snapshot = BattleDirectWarshipStatus.snapshot(ship, false, false);
        assertEquals(Carrier.WARSHIP, snapshot.carrier());
        assertEquals(1f, snapshot.durability().hpFraction());
        assertEquals(1f, snapshot.durability().armorFraction());
        ship.takeDamage(1e9f, 1e6f);
        assertEquals(Snapshot.EMPTY, BattleDirectWarshipStatus.snapshot(null, ship));
    }

    @Test
    void theStripFitsTheSharedActionStripAndOffersNoClickableCards() throws Exception {
        ship.pilotDirector(true);
        ship.layArea(Warship.FireGroup.MISSILES, new WarshipOrder.FireOnArea(40f, 30f, 10f, true));
        Snapshot snapshot = BattleDirectWarshipStatus.snapshot(ship, false, true);
        try (var fixture = BattleDirectControlHudTest.fixture(Path.of("mod"), snapshot, 0, false,
                ignored -> fail("A ship's cards select nothing"), () -> {}, () -> {})) {
            for (ShipCard card : snapshot.warship().cards()) {
                var element = fixture.markup().requireElement("direct-hud-ship-" + card.key());
                assertTrue(element.disabled());
                var bounds = element.box().borderBox();
                assertTrue(bounds.right() <= BattleDirectControlOverlay.ACTION_WIDTH, card.key());
                assertTrue(bounds.bottom() <= BattleDirectControlOverlay.ACTION_HEIGHT, card.key());
                assertEquals(card.state(), fixture.markup().requireElement(
                        "direct-hud-ship-" + card.key() + "-state").text());
            }
            assertEquals("FLUX 0%", fixture.markup().requireElement("direct-hud-heat-label").text(),
                    "the director puts an unconfigured ship on its hull's flux");
            assertEquals("DANGER", fixture.markup().requireElement("direct-hud-ship-MISSILES-state").text());
        }
    }
}
