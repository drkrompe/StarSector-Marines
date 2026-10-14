package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.deployable.FieldMortarService.Phase;
import com.dillon.starsectormarines.battle.deployable.FieldMortarService.Tube;
import com.dillon.starsectormarines.battle.deployable.MortarSections.Section;
import com.dillon.starsectormarines.battle.deployable.MortarSections.Stage;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mortar sections: a marching squad that meets something worth a tube halts a crew of four to work it while the rest of
 * the squad is leashed to the tube, for a window that an engaged tube may extend within a cap, after which the crew packs
 * up and the squad reforms. The activation gate, the leash and the stage machine are each asked of the production tube and
 * squad AI on open ground; the window arithmetic is {@link MortarSectionWindowTest}'s.
 */
class MortarSectionTest {

    private static final String RIFLE = "weapon.field-rifle";
    private static final int MAP = 160;
    private static final int ROW = 40;
    private static final float DT = BattleSimulation.TICK_DT;

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
    }

    @AfterEach
    void restore() {
        MortarSections.setForEvidence(true, MortarSections.DEFAULT_WINDOW_SECONDS,
                MortarSections.DEFAULT_EXTENSION_SECONDS, MortarSections.DEFAULT_CAP_SECONDS,
                MortarSections.DEFAULT_COOLDOWN_SECONDS);
    }

    /** Eight marines marching east, the first with a tube, and a stationary rifle squad {@code distance} cells ahead. */
    private static SceneWorld march(int distance) {
        return SceneBuilder.openGround(MAP, ROW).missionCompletion(false)
                .squad("team").faction(Faction.MARINE).size(8).at(12, ROW / 2).primary(RIFLE)
                .special(SpecialEquipmentRegistry.FIELD_MORTAR_ID, 1)
                .assigned(id -> ObjectiveAssignment.attackMove(id, MAP - 6, ROW / 2)).done()
                .squad("enemy").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(3)
                .at(12 + distance, ROW / 2).primary(RIFLE).stationary().done()
                .build();
    }

    private static Section awaitSection(BattleSimulation sim, int ticks) {
        for (int i = 0; i < ticks; i++) {
            sim.advance(DT);
            for (Section s : sim.fieldMortars().sections().sections()) return s;
        }
        return null;
    }

    private static void advanceSeconds(BattleSimulation sim, float seconds) {
        for (int i = 0, n = Math.max(1, Math.round(seconds / DT)); i < n; i++) sim.advance(DT);
    }

    // ---- activation ----

    @Test
    void aMarchThatMeetsSomethingInBandHaltsACrewOfFourAndLeashesTheRest() {
        SceneWorld world = march(40);
        BattleSimulation sim = world.sim();
        long[] team = world.members("team");
        Section section = awaitSection(sim, 400);
        assertNotNull(section, "the march brought a believed target into the tube's band");
        assertEquals(world.squadId("team"), section.squadId);
        assertEquals(MortarSections.CREW_SIZE, section.crewSize(), "a crew of about four, not the squad");
        assertEquals(team[0], section.gunner(), "the gunner is the carrier of the tube");
        assertTrue(section.isCrew(team[0]));
        int crew = 0;
        for (long m : team) if (section.isCrew(m)) crew++;
        assertEquals(MortarSections.CREW_SIZE, crew);

        advanceSeconds(sim, 10f);
        Tube tube = sim.fieldMortars().tubeOfCarrier(team[0]);
        assertNotNull(tube, "the gunner halted and set the tube down");
        float limit = MortarSections.LEASH_CELLS + 2.5f;
        for (long m : team) {
            if (!sim.world().isAlive(m)) continue;
            double d = Math.hypot(sim.world().x(m) - section.anchorX(), sim.world().y(m) - section.anchorY());
            assertTrue(d <= limit, "marine " + m + " stayed on the leash: " + d + " cells from the tube");
        }
    }

    @Test
    void nothingOpensWhenNothingIsWorthATubeInBand() {
        // Beyond sight and inside the dead zone alike: nothing believed in the band.
        SceneWorld world = march(14);
        BattleSimulation sim = world.sim();
        assertNull(awaitSection(sim, 90), "a target already inside the dead zone is a job for rifles");
        assertEquals(0, sim.fieldMortars().sections().opened());
    }

    @Test
    void theOffSwitchIsTheOldBehaviour() {
        MortarSections.setForEvidence(false, 30f, 10f, 90f, 45f);
        SceneWorld world = march(40);
        BattleSimulation sim = world.sim();
        advanceSeconds(sim, 12f);
        assertEquals(0, sim.fieldMortars().sections().opened(), "no section is ever opened with the dial off");
    }

    // ---- the window, the pack-up and the reform ----

    @Test
    void whenTheWindowRunsOutTheCrewPacksUpAndTheSquadMovesOn() {
        // No extension: a plain window short enough to see end.
        MortarSections.setForEvidence(true, 12f, 0f, 90f, 45f);
        SceneWorld world = march(40);
        BattleSimulation sim = world.sim();
        long[] team = world.members("team");
        Section section = awaitSection(sim, 400);
        assertNotNull(section);
        float anchorX = section.anchorX();
        advanceSeconds(sim, 11f);
        assertFalse(sim.fieldMortars().sections().sections().isEmpty(), "still inside the window");

        advanceSeconds(sim, 2f + 3.5f);
        assertEquals(1, sim.fieldMortars().sections().expired(), "the window ran out");
        assertTrue(sim.fieldMortars().packs() >= 1 || sim.fieldMortars().tubeOfCarrier(team[0]) == null,
                "and the crew lifted the tube");
        advanceSeconds(sim, 4f);
        assertTrue(sim.fieldMortars().sections().sections().isEmpty(), "the section ended with the tube");
        assertEquals(1, sim.fieldMortars().sections().ended());
        assertEquals(Stage.CLOSING, section.stage(), "and went out through closing, never straight from working");

        double before = centroidX(sim, team);
        advanceSeconds(sim, 6f);
        assertTrue(centroidX(sim, team) > before + 3.0, "the squad reformed and marched on");
        assertTrue(centroidX(sim, team) > anchorX - 2f);
    }

    @Test
    void anEngagedTubeHoldsTheWindowOpenPastItsPlainLengthButNotPastTheCap() {
        MortarSections.setForEvidence(true, 8f, 10f, 24f, 45f);
        SceneWorld world = march(40);
        BattleSimulation sim = world.sim();
        long[] team = world.members("team");
        Section section = awaitSection(sim, 400);
        assertNotNull(section);
        // Make the enemy hard to kill so the tube stays engaged for the whole run.
        for (long id : world.members("enemy")) {
            sim.world().setMaxHp(id, 1_000_000f);
            sim.world().setHp(id, 1_000_000f);
        }
        advanceSeconds(sim, 14f);
        Tube tube = sim.fieldMortars().tubeOfCarrier(team[0]);
        assertNotNull(tube);
        assertTrue(tube.roundsFired() > 0, "the tube is firing");
        assertEquals(Phase.SERVING, tube.phase(), "and is still up at 14s, past the plain 8s window");
        assertTrue(section.window().deadline() > 8f, "because engagement extended the deadline");
        assertTrue(sim.fieldMortars().sections().sectionOf(section.squadId) == section);

        advanceSeconds(sim, 14f);
        assertNull(sim.fieldMortars().sections().sectionOf(section.squadId),
                "but the cap of 24s ended it even though it was still engaged");
        assertEquals(1, sim.fieldMortars().sections().expired());
    }

    // ---- the cooldown and the state machine alone ----

    /** A control whose squad lookups answer null, so a section meets its end by the squad being gone. */
    private static BattleControl blindControl() {
        return (BattleControl) Proxy.newProxyInstance(BattleControl.class.getClassLoader(),
                new Class<?>[] {BattleControl.class}, (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == long.class) return 0L;
                    if (type == float.class) return 0f;
                    return null;
                });
    }

    @Test
    void aSquadMayNotOpenAnotherSectionUntilItsCooldownHasRun() {
        MortarSections.setForEvidence(true, 30f, 10f, 90f, 5f);
        MortarSections sections = new MortarSections();
        FieldMortarService mortars = new FieldMortarService();
        BattleControl blind = blindControl();
        assertTrue(sections.mayOpen(3), "a fresh squad may");
        sections.open(3, new long[] {11L, 12L}, 5f, 5f);
        assertFalse(sections.mayOpen(3), "not while it has one open");
        sections.tick(0.5f, mortars, blind);
        assertEquals(1, sections.ended(), "the squad was gone, so the section ended");
        assertFalse(sections.mayOpen(3), "and it cools down before the next");
        for (int i = 0; i < 9; i++) sections.tick(0.5f, mortars, blind);
        assertFalse(sections.mayOpen(3), "4.5s of a 5s cooldown");
        sections.tick(0.5f, mortars, blind);
        assertTrue(sections.mayOpen(3), "the cooldown has run");
        assertTrue(sections.mayOpen(4), "and another squad was never affected");
    }

    @Test
    void aSectionWhoseGunnerNeverCommitsTheTubeIsCalledOffAndCoolsDown() {
        MortarSections.setForEvidence(true, 30f, 10f, 90f, 5f);
        SceneWorld world = SceneBuilder.openGround(MAP, ROW).missionCompletion(false)
                .squad("team").faction(Faction.MARINE).size(4).at(12, ROW / 2).primary(RIFLE)
                .special(SpecialEquipmentRegistry.FIELD_MORTAR_ID, 1).stationary().done()
                .squad("enemy").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(2)
                .at(100, ROW / 2).primary(RIFLE).stationary().done()
                .build();
        BattleSimulation sim = world.sim();
        long[] team = world.members("team");
        // Open one by hand with nothing in band: the gunner can never commit.
        sim.fieldMortars().sections().open(world.squadId("team"), team, 12f, ROW / 2f);
        advanceSeconds(sim, MortarSections.OPENING_GRACE_SECONDS + 1.5f);
        assertNull(sim.fieldMortars().sections().sectionOf(world.squadId("team")), "called off");
        assertEquals(1, sim.fieldMortars().sections().abandoned());
        assertFalse(sim.fieldMortars().sections().mayOpen(world.squadId("team")), "and it cools down");
    }

    private static double centroidX(BattleSimulation sim, long[] members) {
        double sum = 0;
        int n = 0;
        for (long m : members) {
            if (!sim.world().isAlive(m)) continue;
            sum += sim.world().x(m);
            n++;
        }
        return n == 0 ? 0 : sum / n;
    }
}
