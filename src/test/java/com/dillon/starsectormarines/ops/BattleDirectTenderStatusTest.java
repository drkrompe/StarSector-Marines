package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.tender.TenderPilot.Notice;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Carrier;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Durability;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.ShipCard;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Snapshot;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Tone;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** What the piloted tender's strip reads off the tender, and that it fits the shared action strip. */
class BattleDirectTenderStatusTest {
    private Tender tender;
    private NavigationGrid grid;

    @BeforeEach
    void fit() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        tender = Tender.fit(Faction.MARINE, 20f, 20f, 0f);
        grid = new NavigationGrid(60, 60);
        for (int x = 0; x < 60; x++) {
            for (int y = 0; y < 60; y++) grid.setWalkableFloor(x, y);
        }
        tender.takeHelm();
    }

    private ShipCard card(String key, Notice notice) {
        for (ShipCard c : BattleDirectTenderStatus.status(tender, 0, notice).cards()) {
            if (c.key().equals(key)) return c;
        }
        return null;
    }

    @Test
    void theStripIsHelmOptionSiteBuildersDefenceAndShield() {
        var status = BattleDirectTenderStatus.status(tender, 0, Notice.NONE);
        assertEquals(List.of("HELM", "OPTION", "SITE", "BUILDERS", "DEFENCE", "SHIELD"),
                status.cards().stream().map(ShipCard::key).toList());
        assertTrue(status.flux().limited(), "the shield runs on the hull's flux");
    }

    @Test
    void theOptionCardListsTheRealOptionAndCallsTheEmptySlotsSoon() {
        assertEquals(1, BuildOption.ALL.size(), "the text below assumes one option; update it with the list");
        ShipCard option = card("OPTION", Notice.NONE);
        assertEquals("[1] VULCAN TURRET", option.detail());
        assertEquals("SELECTED", option.state());
        assertEquals("[2]SOON [3]SOON [4]SOON", option.resource());
        ShipCard empty = BattleDirectTenderStatus.optionCard(2);
        assertEquals("[3] EMPTY", empty.detail());
        assertEquals("SOON", empty.state());
        assertEquals(Tone.WARN, empty.tone());
    }

    @Test
    void theSiteCardReadsIdleThenTheLaidSiteAndItsProgress() {
        ShipCard idle = card("SITE", Notice.NONE);
        assertEquals("HOLD LMB ON MAP", idle.detail());
        assertEquals("IDLE", idle.state());
        assertEquals("0/4 SITES", idle.resource());

        tender.lay(BuildOption.VULCAN_TURRET, 40.5f, 40.5f, grid);
        ShipCard queued = card("SITE", Notice.NONE);
        assertEquals("VULCAN TURRET 0%", queued.detail());
        assertEquals("QUEUED", queued.state());
        assertEquals("[X] CANCEL 1", queued.resource());
    }

    @Test
    void aFreshNoticeOutranksThePhaseAndSaysWhatWentWrong() {
        assertEquals("UNSEEN GROUND", card("SITE", Notice.UNSEEN).state());
        assertEquals(Tone.WARN, card("SITE", Notice.UNSEEN).tone());
        assertEquals("SITE LAID", card("SITE", Notice.LAID).state());
        assertEquals(Tone.READY, card("SITE", Notice.LAID).tone());
        assertEquals("LINE FULL", card("SITE", Notice.FULL).state());
        assertEquals("CAN'T BUILD THERE", card("SITE", Notice.BLOCKED).state());
        assertEquals("NO CHARGES", card("SITE", Notice.NO_CHARGES).state());
    }

    @Test
    void theDroneCardsCountReadyAwayAndLost() {
        ShipCard builders = card("BUILDERS", Notice.NONE);
        assertEquals("3/3 READY", builders.resource());
        assertEquals("DOCKED", builders.state());
        assertEquals("OUT 0  LOST 0", builders.detail());
        ShipCard defence = card("DEFENCE", Notice.NONE);
        assertEquals("3/3 READY", defence.resource());
        assertEquals("DEPLOYED", defence.state());
        tender.defenceOut(false);
        assertEquals("RECALLED", card("DEFENCE", Notice.NONE).state());
    }

    @Test
    void theStripFitsTheSharedActionStripAndOffersNoClickableCards() throws Exception {
        tender.lay(BuildOption.VULCAN_TURRET, 40.5f, 40.5f, grid);
        var status = BattleDirectTenderStatus.status(tender, 0, Notice.BLOCKED);
        var ship = tender.ship();
        Snapshot snapshot = new Snapshot(0L, ship.hullId, Carrier.TENDER,
                new Durability(ship.hitPoints(), ship.maxHitPoints, ship.armour, ship.armour, ship.armour),
                List.of(), List.of(), null, null, status);
        try (var fixture = BattleDirectControlHudTest.fixture(Path.of("mod"), snapshot, 0, false,
                ignored -> fail("A ship's cards select nothing"), () -> {}, () -> {})) {
            for (ShipCard card : status.cards()) {
                var element = fixture.markup().requireElement("direct-hud-ship-" + card.key());
                assertTrue(element.disabled());
                var bounds = element.box().borderBox();
                assertTrue(bounds.right() <= BattleDirectControlOverlay.ACTION_WIDTH, card.key());
                assertTrue(bounds.bottom() <= BattleDirectControlOverlay.ACTION_HEIGHT, card.key());
                assertEquals(card.state(), fixture.markup().requireElement(
                        "direct-hud-ship-" + card.key() + "-state").text());
            }
        }
    }
}
