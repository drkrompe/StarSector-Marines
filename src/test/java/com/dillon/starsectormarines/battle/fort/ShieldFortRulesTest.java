package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.shield.ShieldDomeService;
import com.dillon.starsectormarines.battle.shield.ShieldGenerator;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shield fort's aspect rules at the smallest scale that can show them: a generator and two cores tagged as one
 * fort on an empty field, with capture driven directly on the places (their building interiors are the map's, not
 * these rules'). Power cut drops the dome, the control room's holder is the side the dome serves, the emitter's loss
 * is permanent; every rule is written for sides, not for attacker or defender.
 */
class ShieldFortRulesTest {

    private static final int GEN_X = 30, GEN_Y = 20;

    @BeforeAll
    static void data() {
        InstalledVanillaData.install();
    }

    private record Rig(BattleSimulation sim, Fort fort, long generator, long power, long room) { }

    private static Rig rig() {
        int w = 60, h = 40;
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        BattleSimulation sim = new BattleSimulation(grid, topology);
        sim.spawn(new EntitySpec("m-far", Faction.MARINE, UnitType.MARINE, 2, 2).moveSpeed(0f));
        sim.spawn(new EntitySpec("d-far", Faction.DEFENDER, UnitType.MARINE, 57, 37).moveSpeed(0f));
        long generator = sim.spawn(ShieldGenerator.create("g", Faction.DEFENDER, GEN_X, GEN_Y));
        long power = sim.spawn(FortAspectCore.create("p", Faction.DEFENDER,
                TurretCatalogRegistry.FORT_POWER_STRUCTURE_ID, 10, 10));
        long room = sim.spawn(FortAspectCore.create("c", Faction.DEFENDER,
                TurretCatalogRegistry.FORT_CONTROL_ROOM_STRUCTURE_ID, 14, 30));
        sim.forts().tagEmitter(0, generator, Faction.DEFENDER);
        sim.forts().tagAspect(0, FortAspect.POWER, power, Faction.DEFENDER);
        sim.forts().tagAspect(0, FortAspect.CONTROL_ROOM, room, Faction.DEFENDER);
        sim.shieldDomes().govern(generator);
        return new Rig(sim, sim.forts().forts().get(0), generator, power, room);
    }

    private static void run(BattleSimulation sim, float seconds) {
        int ticks = Math.round(seconds / BattleSimulation.TICK_DT);
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    private static void take(Fort.Place place, Faction taker) {
        for (int i = 0; i < 4000 && place.control().holder() != taker; i++) place.control().tick(0.1f, false, taker);
        assertEquals(taker, place.control().holder());
    }

    private static boolean shellsLand(Rig rig, Faction from) {
        PendingDetonation round = new PendingDetonation(0L, GEN_X + 4f, GEN_Y, 0f, 3f, 40f, 8f, 40, from, false)
                .asShipGrade();
        return rig.sim().shieldDomes().absorbs(round);
    }

    private static ShieldDomeService.DomeView dome(Rig rig) {
        return rig.sim().shieldDomes().domes().get(0);
    }

    @Test
    void anIntactFortKeepsItsDomeUpForTheDefenderAndStopsTheMarinesShells() {
        Rig rig = rig();
        run(rig.sim(), 0.5f);
        assertTrue(shellsLand(rig, Faction.MARINE), "the dome stops hostile ship fire");
        assertFalse(shellsLand(rig, Faction.DEFENDER), "its own side fires through it");
        assertEquals(Faction.DEFENDER, dome(rig).holder());
    }

    @Test
    void cuttingThePowerDropsTheDomeForGood() {
        Rig rig = rig();
        run(rig.sim(), 0.2f);
        rig.sim().applyDamage(rig.power(), 1e7f, 100f);
        run(rig.sim(), 0.5f);
        assertTrue(rig.fort().place(FortAspect.POWER).wrecked());
        assertTrue(dome(rig).overloaded(), "a dome with no power is down");
        assertFalse(shellsLand(rig, Faction.MARINE), "so the shell lands");
        run(rig.sim(), 60f);
        assertFalse(shellsLand(rig, Faction.MARINE), "a wrecked plant stays wrecked");
    }

    @Test
    void takingThePowerPlantDropsTheDomeAndRetakingItRaisesItAgainAfterAReboot() {
        Rig rig = rig();
        Fort.Place plant = rig.fort().place(FortAspect.POWER);
        take(plant, Faction.MARINE);
        run(rig.sim(), 0.5f);
        assertFalse(shellsLand(rig, Faction.MARINE), "the defender's dome has lost its power");
        take(plant, Faction.DEFENDER);
        run(rig.sim(), 0.5f);
        assertFalse(shellsLand(rig, Faction.MARINE), "rebooting");
        run(rig.sim(), ShieldDomeService.REBOOT_SECONDS + 1f);
        assertTrue(shellsLand(rig, Faction.MARINE));
    }

    @Test
    void holdingTheControlRoomTransfersTheDomeToTheHolderOnceItHasPowerToo() {
        Rig rig = rig();
        take(rig.fort().place(FortAspect.CONTROL_ROOM), Faction.MARINE);
        run(rig.sim(), 0.5f);
        assertEquals(Faction.MARINE, dome(rig).holder(), "the dome obeys the control room");
        assertEquals(Faction.MARINE, rig.sim().identity().faction(rig.generator()), "and the generator changes sides");
        run(rig.sim(), ShieldDomeService.REBOOT_SECONDS + 1f);
        assertFalse(shellsLand(rig, Faction.DEFENDER),
                "the plant is still the defender's to withhold, so the new holder's dome has no power yet");
        take(rig.fort().place(FortAspect.POWER), Faction.MARINE);
        run(rig.sim(), ShieldDomeService.REBOOT_SECONDS + 1f);
        assertTrue(shellsLand(rig, Faction.DEFENDER), "with both taken the dome protects the marines");
        assertFalse(shellsLand(rig, Faction.MARINE));
    }

    @Test
    void theControlRoomIsRetakenLikeAnyAspect() {
        Rig rig = rig();
        Fort.Place room = rig.fort().place(FortAspect.CONTROL_ROOM);
        take(room, Faction.MARINE);
        run(rig.sim(), 0.3f);
        take(room, Faction.DEFENDER);
        run(rig.sim(), 0.3f);
        assertEquals(Faction.DEFENDER, dome(rig).holder());
    }

    @Test
    void aWreckedControlRoomLeavesTheDomeWithWhoeverHeldItLast() {
        Rig rig = rig();
        take(rig.fort().place(FortAspect.CONTROL_ROOM), Faction.MARINE);
        run(rig.sim(), 0.3f);
        rig.sim().applyDamage(rig.room(), 1e7f, 100f);
        run(rig.sim(), 0.3f);
        assertTrue(rig.fort().place(FortAspect.CONTROL_ROOM).wrecked());
        assertEquals(Faction.MARINE, dome(rig).holder());
    }

    @Test
    void destroyingTheEmitterEndsTheDomePermanently() {
        Rig rig = rig();
        run(rig.sim(), 0.2f);
        rig.sim().applyDamage(rig.generator(), 1e7f, 100f);
        run(rig.sim(), 0.5f);
        assertTrue(rig.sim().shieldDomes().domes().isEmpty());
        assertTrue(rig.fort().place(FortAspect.EMITTER).wrecked());
        assertFalse(shellsLand(rig, Faction.MARINE));
        // Holding every other aspect cannot bring it back.
        take(rig.fort().place(FortAspect.CONTROL_ROOM), Faction.MARINE);
        run(rig.sim(), 5f);
        assertTrue(rig.sim().shieldDomes().domes().isEmpty());
        assertNotNull(rig.fort().place(FortAspect.POWER));
    }

    @Test
    void aGovernedDomeIsNotTakenByStandingNearTheGenerator() {
        Rig rig = rig();
        rig.sim().spawn(new EntitySpec("raider", Faction.MARINE, UnitType.MARINE, GEN_X + 3, GEN_Y).moveSpeed(0f));
        run(rig.sim(), 30f);
        assertEquals(Faction.DEFENDER, dome(rig).holder(), "only the control room hands a fort's dome over");
    }

    @Test
    void aCommandIsSentToTheControlRoomAndThePlantAndDisclosedBySightingTheGenerator() {
        Rig rig = rig();
        run(rig.sim(), 0.2f);
        List<DefenceStructureFact> facts = rig.sim().defenceStructures();
        assertEquals(2, facts.size());
        DefenceStructureFact room = facts.stream().filter(f -> f.kind() == DefenceStructureFact.Kind.SHIELD_GENERATOR)
                .findFirst().orElseThrow();
        DefenceStructureFact plant = facts.stream().filter(f -> f.kind() == DefenceStructureFact.Kind.FORT_POWER)
                .findFirst().orElseThrow();
        assertEquals(14, room.cellX(), "the dome is held at the control room, not at the generator");
        assertEquals(10, plant.cellX());
        assertEquals(GEN_X, room.seenCellX(), "both are disclosed by the one part that stands over the wall");
        assertEquals(GEN_Y, plant.seenCellY());
        // A destroyed plant is nothing to take.
        rig.sim().applyDamage(rig.power(), 1e7f, 100f);
        run(rig.sim(), 0.3f);
        assertEquals(1, rig.sim().defenceStructures().size());
    }
}
