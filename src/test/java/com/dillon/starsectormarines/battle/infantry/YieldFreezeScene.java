package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.Random;

/**
 * One squad, one order, and the question the Conquest matrix could not answer:
 * <b>when a squad's mission goal yields, does the squad still have anything to
 * do?</b>
 *
 * <p>A mission goal may decline its own order deliberately.
 * {@code ClearAssignedZoneGoal} returns zero relevance when the assigned zone
 * turns out to hold no live enemy, and the comment there says why — yield, so
 * the next replan picks up a fresh assignment from the commander. That is a
 * reasonable thing for a goal to want. What it actually produced was a squad
 * with no goal at all, because the ladder beneath a yielded mission goal was
 * empty: a null plan, and members that drop their paths by design.
 *
 * <p>Instrumented counters put that at roughly twenty-nine thousand
 * order-holding squad replans per Conquest matrix run. This scene is the
 * instrument for the same behaviour at a size where it can be watched, because
 * that matrix cannot resolve it: the same code on two mains a few hours apart
 * moved a fixture by fifty percent on sibling work alone, which is larger than
 * any effect worth measuring here.
 *
 * <h2>The map is three rooms in a row</h2>
 * <p>West holds the squad, middle is the zone it is ordered to clear, and east
 * holds one distant defender. Two walls with doorways, so the zone detector
 * gives three zones and the middle one is genuinely reachable — the goal has a
 * second gate for an unreachable target and this scene is not about that one.
 *
 * <p><b>The east defender exists so the battle keeps ticking, and its room is
 * sealed.</b> A side that is absent ends the simulation immediately and a scene
 * that ends on tick one records nothing. Sealed rather than merely distant,
 * because distance is not enough: {@code BreachToEngage} falls back to an
 * omniscient nearest-enemy scan for squads that have not ticked targeting yet,
 * so a far-away reachable enemy is an attractor. With a door in the east wall
 * both loops walked the whole map to it and recorded near-identical distances
 * that had nothing to do with the yield. With the room sealed, that goal's own
 * reachability gate rules the defender out, and the squad can neither see it,
 * hear it, nor path to it.
 *
 * <h2>The control is the same order over a zone worth clearing</h2>
 * <p>"The squad did nothing" means nothing on its own. The {@code workable}
 * loop puts a defender in the middle room, so the very same order over the very
 * same ground is relevant, the goal plans, and the squad crosses. Whatever the
 * {@code yielded} loop does differently it does because its order yielded, and
 * nothing else in the scene differs.
 *
 * <h2>What to read</h2>
 * <p><b>Plan-less ticks are the finding, not distance.</b> A squad that has
 * yielded its order and is holding position deliberately also does not move, so
 * distance cannot tell a considered halt from an absence of orders. What
 * separates them is whether the squad holds a plan at all: a hold is a plan, a
 * freeze is {@code currentPlan == null} and a member dropping the path it was
 * walking. So the scene counts plan-less ticks and settled ticks alongside
 * ground covered, and a fix for this should drive plan-less ticks to zero
 * without necessarily moving the squad one cell.
 */
final class YieldFreezeScene {

    static final int WIDTH = 96;
    static final int HEIGHT = 32;

    private static final int WEST_WALL_X = 30;
    private static final int EAST_WALL_X = 62;
    private static final int DOOR_Y = HEIGHT / 2;

    static final int SQUAD_X = 8;
    static final int MIDDLE_X = (WEST_WALL_X + EAST_WALL_X) / 2;
    /** Off the doorway's row, so reaching the defender means entering the room. */
    private static final int HELD_Y = 5;

    private static final int SQUAD_SIZE = 6;
    private static final long KIT_SEED = 20260831L;

    /**
     * Which dispatcher is under the microscope. The two share the ladder and
     * the replan pass but not their goal and action libraries, and the ladder's
     * floor is a property of the library rather than of the chassis — so the
     * question has to be asked of each separately rather than assumed to
     * transfer.
     */
    enum Force { INFANTRY, MECH }

    /** One loop's running tally. */
    static final class Tally {
        int squadId;
        int targetZoneId = -1;
        int ticks;
        /** Ticks the squad held no plan at all — the defect this scene exists for. */
        int planlessTicks;
        /** Ticks on which no live member was walking a path. */
        int settledTicks;
        float cellsTravelled;
        float startX;
        float startY;
        String firstGoal = "(none)";
        String lastGoal = "(none)";
        int membersAlive;
        /**
         * First tick the squad held no plan and never held one again. The
         * transition is the reading: a squad between plans for a tick is
         * ordinary, and one that stops holding plans for good is the defect.
         */
        int wentPlanlessForGoodTick = -1;
        private int planlessRunStart = -1;
    }

    static final class Scene {
        final BattleSimulation sim;
        final Tally tally = new Tally();
        float lastX;
        float lastY;

        Scene(BattleSimulation sim) { this.sim = sim; }

        BattleSimulation sim() { return sim; }

        Tally tally() { return tally; }
    }

    private YieldFreezeScene() {}

    /**
     * @param workable when true the assigned zone holds a defender, so the
     *                 order is relevant and the goal plans — the control.
     */
    static Scene build(boolean workable) {
        return build(workable, Force.INFANTRY);
    }

    static Scene build(boolean workable, Force force) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        }
        wallWithDoor(grid, WEST_WALL_X);
        // The east wall has no door on purpose. The lone distant defender has
        // to be somewhere the squad cannot reach *or want*: BreachToEngage
        // falls back to an omniscient nearest-enemy scan for squads that have
        // not ticked targeting yet, so a merely far-away enemy is an attractor
        // rather than a bystander. The first version of this scene left a door
        // here and recorded both loops walking the whole map east to it —
        // identical distances, and nothing whatever about the yield. Sealed,
        // that goal's own reachability gate rules the defender out and the
        // scene measures what it claims to.
        seal(grid, EAST_WALL_X);

        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(WIDTH, HEIGHT));
        // The scene is about one behaviour, not about who wins. A side present
        // only as a single unreachable body is still a decidable battle, and a
        // decided battle stops ticking everything the scene wants to watch.
        sim.setMissionCompletionEnabled(false);

        Scene scene = new Scene(sim);
        int squadId = force == Force.MECH
                ? spawnMechLance(sim, scene.tally)
                : spawnSquad(sim, scene.tally);

        // A whole map away, behind two walls, past marine vision, and immobile.
        // Present so the defender side is never absent; invisible and inaudible
        // so it never hands the floor goal a cue to close on.
        EntitySpec far = new EntitySpec("far", Faction.DEFENDER, UnitType.MARINE,
                WIDTH - 3, HEIGHT - 3);
        far.moveSpeed = 0f;
        sim.spawn(far);

        if (workable) {
            // The only difference between the two loops: somebody in the room
            // the order names, which is what makes that order worth having.
            //
            // Deliberately off the doorway's sight line. On it, the squad shot
            // the defender down the corridor from its own room without ever
            // crossing, the zone went clear, and the control yielded and froze
            // like the case it is supposed to contrast with — a control that
            // reproduces the defect measures nothing.
            EntitySpec held = new EntitySpec("held", Faction.DEFENDER,
                    UnitType.MARINE_RED, MIDDLE_X, HELD_Y);
            held.moveSpeed = 0f;
            sim.spawn(held);
        }

        int targetZone = sim.getZoneGraph().zoneIdAt(MIDDLE_X, DOOR_Y);
        scene.tally.targetZoneId = targetZone;
        Squad squad = sim.getSquad(squadId);
        if (squad != null) {
            squad.assignedObjective = ObjectiveAssignment.clearZone(squadId, targetZone);
        }
        scene.lastX = scene.tally.startX;
        scene.lastY = scene.tally.startY;
        return scene;
    }

    private static void wallWithDoor(NavigationGrid grid, int x) {
        seal(grid, x);
        grid.setWalkableFloor(x, DOOR_Y);
        grid.setDoorway(x, DOOR_Y, true);
    }

    private static void seal(NavigationGrid grid, int x) {
        for (int y = 0; y < HEIGHT; y++) grid.setWalkable(x, y, false);
    }

    /** Armed and squadded: an unarmed, unsquadded spawn is scenery. */
    private static int spawnSquad(BattleSimulation sim, Tally tally) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        MarineLoadout[] kit = InfantryLoadoutRolls.playerSquad(
                SQUAD_SIZE, new Random(KIT_SEED));
        float sumX = 0f;
        float sumY = 0f;
        for (int i = 0; i < SQUAD_SIZE; i++) {
            EntitySpec spec = new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE,
                    SQUAD_X + i % 3, DOOR_Y - 1 + i / 3);
            kit[i].seedInto(spec);
            spec.squad(squadId);
            long id = sim.spawn(spec);
            sumX += sim.world().x(id);
            sumY += sim.world().y(id);
        }
        Squad squad = sim.getSquad(squadId);
        if (squad != null) {
            squad.originalSize = SQUAD_SIZE;
            squad.aliveMembers = SQUAD_SIZE;
        }
        tally.squadId = squadId;
        tally.startX = sumX / SQUAD_SIZE;
        tally.startY = sumY / SQUAD_SIZE;
        return squadId;
    }

    /**
     * A two-mech lance under the same order. Loadouts are attached because that
     * component is what routes a unit to the mech dispatcher at all — without
     * it these are ordinary bodies running the infantry ladder, and the scene
     * would record the infantry answer twice under two names.
     */
    private static int spawnMechLance(BattleSimulation sim, Tally tally) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        float sumX = 0f;
        float sumY = 0f;
        int size = 2;
        for (int i = 0; i < size; i++) {
            EntitySpec spec = MechVariant.BULWARK.applyTo(new EntitySpec(
                    "mech" + i, Faction.MARINE, UnitType.HEAVY_MECH,
                    SQUAD_X + i * 2, DOOR_Y).squad(squadId));
            long id = sim.spawn(spec);
            sim.world().attachMechLoadout(id,
                    MechVariant.BULWARK.createLoadout(MechRole.BALANCED));
            sumX += sim.world().x(id);
            sumY += sim.world().y(id);
        }
        Squad squad = sim.getSquad(squadId);
        if (squad != null) {
            squad.originalSize = size;
            squad.aliveMembers = size;
        }
        tally.squadId = squadId;
        tally.startX = sumX / size;
        tally.startY = sumY / size;
        return squadId;
    }

    /** One tick, and the tally kept current from what the squad actually holds. */
    static void advance(Scene scene, int tick) {
        BattleSimulation sim = scene.sim;
        Tally tally = scene.tally;
        Squad squad = sim.getSquad(tally.squadId);
        if (squad != null) {
            tally.ticks++;
            if (squad.currentPlan == null) {
                tally.planlessTicks++;
                if (tally.planlessRunStart < 0) tally.planlessRunStart = tick;
            } else {
                tally.planlessRunStart = -1;
            }
            tally.wentPlanlessForGoodTick = tally.planlessRunStart;
            String goal = squad.currentGoal == null ? "(none)" : squad.currentGoal.name();
            if (!"(none)".equals(goal) && "(none)".equals(tally.firstGoal)) {
                tally.firstGoal = goal;
            }
            tally.lastGoal = goal;

            int alive = 0;
            boolean anyWalking = false;
            float sumX = 0f;
            float sumY = 0f;
            for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                long member = sim.squadMemberAt(squad.id, i);
                if (sim.resolveUnit(member) == 0L) continue;
                alive++;
                sumX += sim.world().x(member);
                sumY += sim.world().y(member);
                if (!sim.movement().settled(member)) anyWalking = true;
            }
            tally.membersAlive = alive;
            if (!anyWalking) tally.settledTicks++;
            if (alive > 0) {
                float x = sumX / alive;
                float y = sumY / alive;
                tally.cellsTravelled += (float) Math.hypot(x - scene.lastX, y - scene.lastY);
                scene.lastX = x;
                scene.lastY = y;
            }
        }
        sim.advance(BattleSimulation.TICK_DT);
    }
}
