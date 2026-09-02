package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile.ForceTier;
import com.dillon.starsectormarines.battle.setup.GroundRosterRegistry;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.polity.GroundProductionQuality;
import com.dillon.starsectormarines.campaign.polity.MarketProductionSignals;
import com.dillon.starsectormarines.campaign.polity.PolityDoctrine;
import com.dillon.starsectormarines.campaign.polity.PolityDoctrineLedger;
import com.dillon.starsectormarines.campaign.polity.PolityRosterDerivation;
import com.dillon.starsectormarines.campaign.polity.ProductionSignals;
import com.dillon.starsectormarines.campaign.polity.ReleasedKit;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The daily rebuild. Every case runs against a registry of its own, so a derived
 * profile from a test never lands in the process-wide catalog other tests resolve
 * against.
 */
class PolityRosterSystemTest {

    private static final String ADVANCED_PATTERN = "armor.combat";

    @Test
    void aTickPutsTheDerivedProfileUnderThePlayerFaction() throws Exception {
        GroundRosterRegistry registry = authored();
        CampaignState state = new CampaignState();

        system(registry, GroundProductionQuality.BASIC).tick(state, 1);

        GroundRosterProfile resolved = registry.profileFor(Factions.PLAYER);
        assertEquals(PolityRosterDerivation.PROFILE_ID, resolved.id());
        assertTrue(resolved.factionIds().contains(Factions.PLAYER));
    }

    /** Before the first rebuild, the player's own colony fields whatever the catalog falls back to. */
    @Test
    void withoutARebuildThePlayerFactionStillFallsBack() throws Exception {
        GroundRosterRegistry registry = authored();

        assertEquals("roster.independent", registry.profileFor(Factions.PLAYER).id());
    }

    /**
     * The rebuild runs every day against a registry that already holds yesterday's,
     * so the replace is the whole reason a second tick does not throw.
     */
    @Test
    void consecutiveTicksReplaceRatherThanCollide() throws Exception {
        GroundRosterRegistry registry = authored();
        CampaignState state = new CampaignState();
        PolityRosterSystem system = system(registry, GroundProductionQuality.BASIC);
        int authoredCount = registry.size();

        system.tick(state, 1);
        system.tick(state, 2);
        system.tick(state, 3);

        assertEquals(authoredCount + 1, registry.size());
        assertEquals(PolityRosterDerivation.PROFILE_ID,
                registry.profileFor(Factions.PLAYER).id());
    }

    /** A release the day before is in the roster on the next rebuild and not before it. */
    @Test
    void aReleasedAdvancedPatternIsInTheNextDaysRoster() throws Exception {
        GroundRosterRegistry registry = authored();
        CampaignState state = new CampaignState();
        PolityRosterSystem system = system(registry, GroundProductionQuality.ADVANCED_FULL);

        system.tick(state, 1);
        assertFalse(armorIds(registry.profileFor(Factions.PLAYER)).contains(ADVANCED_PATTERN),
                "nobody released it yet");

        ReleasedKit.release(state, EquipmentTemplateCatalog.armorId(ADVANCED_PATTERN));
        system.tick(state, 2);

        assertTrue(armorIds(registry.profileFor(Factions.PLAYER)).contains(ADVANCED_PATTERN));
    }

    /** Doctrine is read off the state at rebuild time, not captured when the system was built. */
    @Test
    void aDoctrinePointSpentTodayIsInTomorrowsRoster() throws Exception {
        GroundRosterRegistry registry = authored();
        CampaignState state = new CampaignState();
        PolityRosterSystem system = system(registry, GroundProductionQuality.BASIC);

        system.tick(state, 1);
        assertEquals(0, registry.profileFor(Factions.PLAYER).heavySupport().size());

        PolityDoctrineLedger.write(state, PolityDoctrine.of(0, 0, 1));
        system.tick(state, 2);

        assertEquals(1, registry.profileFor(Factions.PLAYER).heavySupport().size(),
                "a point of heavy support at a step that can fabricate one puts a lance in");
    }

    /** The industry is the only authority over grade, so a polity with none stays where it is. */
    @Test
    void theProductionStepIsWhatTheSignalsSay() throws Exception {
        GroundRosterRegistry registry = authored();
        CampaignState state = new CampaignState();
        PolityDoctrineLedger.write(state, PolityDoctrine.of(0, 0, 2));

        system(registry, GroundProductionQuality.NONE).tick(state, 1);

        assertEquals(0, registry.profileFor(Factions.PLAYER).heavySupport().size(),
                "two points of heavy support cannot build a mech in a polity with no sheds");
    }

    @Test
    void theSystemDeclaresTheTableItReads() {
        PolityRosterSystem system = new PolityRosterSystem();

        assertTrue(system.reads().contains(CampaignTable.RELEASED_KIT));
        assertTrue(system.writes().isEmpty(),
                "the profile lands in the roster registry, not on CampaignState");
        assertEquals("PolityRoster", system.name());
    }

    /** Nothing to rebuild into, nothing to rebuild from: a missing input is not a crash. */
    @Test
    void anAbsentStateOrRegistryIsToleratedRatherThanThrown() {
        PolityRosterSystem system = new PolityRosterSystem(
                () -> GroundProductionQuality.BASIC, () -> null);

        system.tick(new CampaignState(), 1);
        system.tick(null, 1);
    }

    private static PolityRosterSystem system(GroundRosterRegistry registry,
                                             GroundProductionQuality quality) {
        ProductionSignals signals = () -> quality;
        return new PolityRosterSystem(signals, () -> registry);
    }

    private static GroundRosterRegistry authored() throws Exception {
        GroundRosterRegistry registry = new GroundRosterRegistry();
        for (String path : GroundRosterRegistry.BUILTIN_CATALOGS) {
            registry.ingest(new JSONObject(Files.readString(Paths.get("mod", path))));
        }
        registry.validateCompleteness();
        return registry;
    }

    private static TreeSet<String> armorIds(GroundRosterProfile profile) {
        Random rng = new Random(11);
        TreeSet<String> ids = new TreeSet<>();
        for (int roll = 0; roll < 4000; roll++) {
            MarineArmorCatalogDef pattern =
                    profile.issue(ForceTier.ELITE).pickArmorDef(RiskLevel.HIGH, rng);
            ids.add(pattern.id());
        }
        return ids;
    }

    /** Guards the fixture rather than the code: the pure signals really do read as stated. */
    @Test
    void theStatedSignalsAreWhatTheDerivationSees() {
        assertEquals(GroundProductionQuality.ADVANCED_FULL,
                MarketProductionSignals.signals(
                        MarketProductionSignals.of(false, true, false, false)).bestQuality());
    }
}
