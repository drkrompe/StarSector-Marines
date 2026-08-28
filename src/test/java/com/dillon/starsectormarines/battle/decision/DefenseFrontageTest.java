package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.decision.DefenseFrontage.Aperture;
import com.dillon.starsectormarines.battle.decision.DefenseFrontage.Facing;
import com.dillon.starsectormarines.battle.decision.DefenseFrontage.Kind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link DefenseFrontage} — the aperture derivation the garrison
 * behaviors read. The fixture is a walled compound in an open field: solid
 * perimeter, paired firing windows north and south, one doorway south.
 */
class DefenseFrontageTest {

    private static final int W = 36;
    private static final int H = 24;
    private static final int LEFT = 10;
    private static final int TOP = 6;
    private static final int RIGHT = 24;
    private static final int BOTTOM = 18;
    private static final int DOOR_X = 17;

    @Test
    void southWallWindowsFaceOutwardWithAnInteriorStance() {
        BattleSimulation sim = compoundSim();
        List<Aperture> frontage = DefenseFrontage.forCompound(node(), sim);

        Aperture window = require(frontage, 14, BOTTOM);
        assertEquals(Kind.WINDOW, window.kind());
        assertEquals(Facing.SOUTH, window.facing());
        assertEquals(14, window.stanceX());
        assertEquals(BOTTOM - 1, window.stanceY(), "stance is the interior cell behind the window");
        assertEquals(BOTTOM + 1, window.outsideY(), "watched cell is the approach outside the wall");
        assertTrue(sim.getGrid().isWalkable(window.stanceX(), window.stanceY()));
    }

    @Test
    void northAndSouthWindowsFaceOppositeWays() {
        BattleSimulation sim = compoundSim();
        List<Aperture> frontage = DefenseFrontage.forCompound(node(), sim);

        assertEquals(Facing.NORTH, require(frontage, 14, TOP).facing());
        assertEquals(Facing.SOUTH, require(frontage, 14, BOTTOM).facing());
        assertEquals(TOP + 1, require(frontage, 14, TOP).stanceY(),
                "a north-wall stance is inside the compound, not north of it");
    }

    @Test
    void theDoorwayIsAnEntranceAndSolidWallIsNothing() {
        BattleSimulation sim = compoundSim();
        List<Aperture> frontage = DefenseFrontage.forCompound(node(), sim);

        Aperture door = require(frontage, DOOR_X, BOTTOM);
        assertEquals(Kind.ENTRANCE, door.kind());
        assertEquals(Facing.SOUTH, door.facing());
        assertEquals(BOTTOM - 1, door.stanceY(), "a doorway post stands beside the door, not in it");

        assertTrue(find(frontage, 12, BOTTOM).isEmpty(), "solid wall is not an aperture");
        assertTrue(find(frontage, LEFT, TOP).isEmpty(), "a corner is not an aperture");
    }

    @Test
    void openInteriorGroundBesideTheDoorIsNotAnEntrance() {
        BattleSimulation sim = compoundSim();
        List<Aperture> frontage = DefenseFrontage.forCompound(node(), sim);

        assertTrue(find(frontage, DOOR_X, BOTTOM - 1).isEmpty(),
                "unconstricted ground inside the wall must not read as an opening — "
                        + "without that gate an open-sided compound's whole perimeter is frontage");
        assertTrue(find(frontage, DOOR_X, BOTTOM + 1).isEmpty(),
                "nor does the ground outside it");
    }

    @Test
    void everyApertureSeparatesHeldGroundFromUnheldGround() {
        BattleSimulation sim = compoundSim();
        for (Aperture aperture : DefenseFrontage.forCompound(node(), sim)) {
            int insideZone = sim.getZoneGraph().zoneIdAt(aperture.stanceX(), aperture.stanceY());
            int outsideZone = sim.getZoneGraph().zoneIdAt(aperture.outsideX(), aperture.outsideY());
            assertTrue(insideZone != outsideZone,
                    "aperture at " + aperture.x() + "," + aperture.y()
                            + " does not actually separate two zones");
        }
    }

    @Test
    void anInteriorPartitionWindowIsNotFrontage() {
        BattleSimulation sim = compoundSim();
        // Partition the compound interior and put a window in it. Both of its
        // sides are held ground, so it is not part of the outer envelope.
        NavigationGrid grid = sim.getGrid();
        for (int y = TOP + 1; y < BOTTOM; y++) grid.setWalkable(20, y, false);
        grid.setWalkableFloor(20, 12);
        grid.setDoorway(20, 12, true);
        grid.setSeeThrough(20, 9, true);
        sim.getTopology().setWindow(20, 9, true);
        sim.getZoneGraph().rebuild();

        List<Aperture> frontage = DefenseFrontage.forCompound(node(), sim);
        assertTrue(find(frontage, 20, 9).isEmpty(),
                "a window between two interior rooms is not the compound's frontage");
        assertFalse(find(frontage, 14, BOTTOM).isEmpty(),
                "the outer wall is still frontage after the interior is partitioned");
    }

    @Test
    void breachingTheOuterWallDissolvesTheFrontage() {
        BattleSimulation sim = compoundSim();
        assertFalse(DefenseFrontage.forCompound(node(), sim).isEmpty());

        // A breach is non-doorway rubble, so the zone graph merges the
        // interior into the open field: inside and outside stop being
        // distinguishable and the envelope no longer exists to be manned.
        sim.getGrid().setWalkableFloor(13, BOTTOM);
        sim.getZoneGraph().rebuild();

        assertTrue(DefenseFrontage.forCompound(node(), sim).isEmpty(),
                "a breached envelope has no frontage — the fight belongs to the "
                        + "room-clearing behaviors, not to posts at intact windows");
    }

    @Test
    void threatReadsTheFactionsOwnBeliefAndFavoursTheApproachedWall() {
        BattleSimulation sim = compoundSim();
        int garrison = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE_RED);
        spawnStill(sim, "garrison", Faction.DEFENDER, UnitType.MARINE_RED, 17, 12, garrison);
        long attacker = spawnStill(sim, "attacker", Faction.MARINE, UnitType.MARINE,
                14, BOTTOM + 3, Squad.NO_SQUAD);
        // A shot south of the compound is a legal defender contact on its shooter.
        sim.postShot(new ShotEvent(attacker, 14.5f, BOTTOM + 3.5f, 17.5f, 12.5f,
                false, Faction.MARINE, 0.1f));
        sim.advance(BattleSimulation.TICK_DT);

        List<Aperture> frontage = DefenseFrontage.forCompound(node(), sim);
        float south = DefenseFrontage.threatAt(require(frontage, 14, BOTTOM), Faction.DEFENDER, sim);
        float north = DefenseFrontage.threatAt(require(frontage, 14, TOP), Faction.DEFENDER, sim);

        assertTrue(south > 0f, "the approached wall carries believed pressure");
        assertTrue(south > north, "the wall the assault is behind outranks the far wall");
        assertEquals(0f, DefenseFrontage.threatAt(require(frontage, 14, BOTTOM),
                        Faction.MARINE, sim), 1e-6,
                "threat is read from one faction's own belief, never from the other's");
    }

    @Test
    void anUnobservedApproachLeavesTheFrontageQuiet() {
        BattleSimulation sim = compoundSim();
        int garrison = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE_RED);
        spawnStill(sim, "garrison", Faction.DEFENDER, UnitType.MARINE_RED, 17, 12, garrison);
        // East of the solid east wall: no window on that face, so the garrison
        // has no line to it. The windowed walls genuinely do see their own
        // approach, which is the point of them.
        spawnStill(sim, "attacker", Faction.MARINE, UnitType.MARINE, RIGHT + 6, 12, Squad.NO_SQUAD);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(0f, sim.getCommanderInfluence(Faction.DEFENDER).maxHostile(), 1e-6,
                "belief, not truth — an unseen approach must not man the wall");
        List<Aperture> frontage = DefenseFrontage.forCompound(node(), sim);
        assertEquals(0f, DefenseFrontage.threatAt(require(frontage, 14, BOTTOM),
                        Faction.DEFENDER, sim), 1e-6);
    }

    /** Open field with a walled compound: solid perimeter, paired windows north and south, one south doorway. */
    private static BattleSimulation compoundSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean onPerimeter = (x == LEFT || x == RIGHT) && y >= TOP && y <= BOTTOM
                        || (y == TOP || y == BOTTOM) && x >= LEFT && x <= RIGHT;
                if (!onPerimeter) grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(DOOR_X, BOTTOM);
        grid.setDoorway(DOOR_X, BOTTOM, true);

        CellTopology topology = new CellTopology(W, H);
        for (int x = 14; x <= 15; x++) {
            for (int y : new int[]{TOP, BOTTOM}) {
                grid.setSeeThrough(x, y, true);
                topology.setWindow(x, y, true);
            }
        }
        return new BattleSimulation(grid, topology);
    }

    private static TacticalNode node() {
        return new TacticalNode(TacticalNode.Kind.COMMAND_POST, DOOR_X, 12,
                LEFT, TOP, RIGHT, BOTTOM, Faction.DEFENDER, 90, 6);
    }

    private static Optional<Aperture> find(List<Aperture> frontage, int x, int y) {
        return frontage.stream().filter(a -> a.x() == x && a.y() == y).findFirst();
    }

    private static Aperture require(List<Aperture> frontage, int x, int y) {
        return find(frontage, x, y).orElseThrow(() ->
                new AssertionError("no aperture derived at " + x + "," + y + " (got " + frontage + ")"));
    }

    private static long spawnStill(BattleSimulation sim, String name, Faction faction,
                                   UnitType type, int x, int y, int squadId) {
        EntitySpec spec = new EntitySpec(name, faction, type, x, y);
        spec.moveSpeed = 0f;
        if (squadId != Squad.NO_SQUAD) spec.squad(squadId);
        return sim.spawn(spec);
    }
}
