package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Two symmetric lanes to one objective, an ambush covering one of them, and two
 * marine squads sent up in succession. The question is the whole scene: <b>when
 * the squad ahead of you is destroyed in a lane, does the next squad go up the
 * same lane?</b>
 *
 * <p>It exists because the Conquest matrix cannot answer that. Two whole-battle
 * fixtures sharing a tree with several active sessions attribute badly — a
 * seven-thousand-tick swing there turned out to be another session's
 * reinforcement work arriving on a merge, not the squad behaviour it was
 * credited to. A question about one behaviour wants a scene, and this is the
 * smallest map that can hold this one.
 *
 * <p><b>The lanes are deliberately mirror images.</b> Equal length, equal
 * width, equal cover, both open at both ends, and the start and objective sit
 * on the centre line. Nothing about the geometry prefers either, so lane choice
 * is decided by the pathfinder's tie-break alone — which makes it a clean
 * instrument. A layer that remembers where casualties happened should flip the
 * second wave to the other lane, and nothing else in the scene can.
 *
 * <p><b>The killers are removed once they have done their work.</b> That is the
 * whole trick, and the scene did not work without it: a squad already routes
 * away from enemies it can see, so an ambush left standing sends the second
 * wave down the other lane for a reason that has nothing to do with memory —
 * the first version of this scene recorded exactly that and would have credited
 * a casualty layer with an avoidance the game already had. With the ambush
 * dead, the two lanes are once again identical in every respect a router can
 * observe, except that one of them is full of dead marines.
 *
 * <p>Today it records the honest baseline: routing has no memory of losses, so
 * the second wave takes the same lane the control does. That is the recording
 * to compare against, not a bug the scene is failing to avoid.
 */
final class KillingGroundScene {

    static final int WIDTH = 72;
    static final int HEIGHT = 72;

    /** Start and objective both sit here, so neither lane is the shorter way. */
    static final int CENTRE_X = WIDTH / 2;
    static final int START_Y = HEIGHT - 8;
    static final int OBJECTIVE_Y = 6;

    /** The divider runs between the lanes, open at both ends. */
    private static final int DIVIDER_TOP_Y = 16;
    private static final int DIVIDER_BOTTOM_Y = HEIGHT - 16;
    private static final int DIVIDER_HALF_WIDTH = 6;

    /** Lane centres, mirrored about {@link #CENTRE_X}. */
    static final int WEST_LANE_X = CENTRE_X - 16;
    static final int EAST_LANE_X = CENTRE_X + 16;

    /** The ambush covers the west lane only. */
    private static final int AMBUSH_Y = HEIGHT / 2;
    private static final int AMBUSH_SIZE = 8;

    private static final int SQUAD_SIZE = 6;
    private static final long KIT_SEED = 20260829L;

    /** Which lane a squad is using, read off its centroid rather than its orders. */
    enum Lane { WEST, EAST, NEITHER }

    /** One wave's story: the squad, the lane it committed to, and what became of it. */
    static final class Wave {
        final int squadId;
        Lane lane = Lane.NEITHER;
        int spawnedTick = -1;
        int destroyedTick = -1;
        /**
         * Whether this wave has ever been seen alive. A squad is only destroyed
         * if it was previously standing — without this an empty reading on the
         * tick it spawns counts as a wipe, which is how this scene first
         * recorded both waves annihilated at t0 on a map with no enemy.
         */
        boolean stood;

        Wave(int squadId) { this.squadId = squadId; }
    }

    record Scene(BattleSimulation sim, List<Wave> waves, int[] objective,
                 List<Long> ambush, boolean[] ambushCleared) {

        Wave first() { return waves.get(0); }

        Wave following() { return waves.size() > 1 ? waves.get(1) : null; }
    }

    private KillingGroundScene() {}

    static Scene build(boolean ambushed) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        }
        // The divider, and the map edge, are what make two lanes rather than
        // one open field. Without it a squad walks the straight line between
        // start and objective and there is no choice to observe.
        for (int y = DIVIDER_TOP_Y; y <= DIVIDER_BOTTOM_Y; y++) {
            for (int dx = -DIVIDER_HALF_WIDTH; dx <= DIVIDER_HALF_WIDTH; dx++) {
                grid.setWalkable(CENTRE_X + dx, y, false);
            }
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(WIDTH, HEIGHT));
        // The scene is about a behaviour and not about who wins. Between waves
        // the marine side is briefly empty, and a decided battle stops ticking
        // everything — including the defenders whose fire is the entire point.
        sim.setMissionCompletionEnabled(false);

        List<Long> ambush = ambushed ? spawnAmbush(sim) : List.of();
        // One distant defender nobody can reach, so the far side is never
        // absent even in the unambushed control.
        sim.spawn(new EntitySpec("far", Faction.DEFENDER, UnitType.MARINE,
                WIDTH - 2, 1).moveSpeed(0f));

        List<Wave> waves = new ArrayList<>(2);
        // The lost squad starts in the lane beside the ambush rather than at the
        // start line. Its job is to become casualties in a known place, not to
        // choose a route - giving it the choice too would ask two questions at
        // once and answer neither.
        waves.add(ambushed ? spawnWave(sim, 0, WEST_LANE_X, AMBUSH_Y + 8)
                : spawnWave(sim, 0, CENTRE_X, START_Y));
        waves.get(0).spawnedTick = 0;
        return new Scene(sim, waves, new int[]{CENTRE_X, OBJECTIVE_Y},
                ambush, new boolean[1]);
    }

    /**
     * A dug-in group covering the west lane. Static and short-sighted on
     * purpose: this is a killing ground, not a manoeuvre element, and a
     * defender that chases would carry the fight into the other lane and
     * destroy the very symmetry the scene depends on.
     */
    private static List<Long> spawnAmbush(BattleSimulation sim) {
        List<Long> ids = new ArrayList<>(AMBUSH_SIZE);
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE_RED);
        MarineLoadout[] kit = InfantryLoadoutRolls.playerSquad(
                AMBUSH_SIZE, new Random(KIT_SEED));
        for (int i = 0; i < AMBUSH_SIZE; i++) {
            EntitySpec spec = new EntitySpec("amb-" + i, Faction.DEFENDER,
                    UnitType.MARINE_RED,
                    WEST_LANE_X - 3 + i % 4,
                    AMBUSH_Y - 2 + i / 4);
            kit[i].seedInto(spec);
            spec.squad(squadId).moveSpeed(0f);
            ids.add(sim.spawn(spec));
        }
        Squad squad = sim.getSquad(squadId);
        if (squad != null) {
            squad.originalSize = AMBUSH_SIZE;
            squad.aliveMembers = AMBUSH_SIZE;
        }
        return ids;
    }

    /**
     * A marine squad on the centre line under an attack move to the objective.
     * Armed and squadded rather than bare — target acquisition runs off the
     * squad, and an unarmed unsquadded spawn is scenery that records a
     * contested advance which never happened.
     */
    private static Wave spawnWave(BattleSimulation sim, int index, int atX, int atY) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        MarineLoadout[] kit = InfantryLoadoutRolls.playerSquad(
                SQUAD_SIZE, new Random(KIT_SEED + index));
        for (int i = 0; i < SQUAD_SIZE; i++) {
            EntitySpec spec = new EntitySpec("w" + index + "-" + i, Faction.MARINE,
                    UnitType.MARINE,
                    atX - 2 + i % 4, atY + i / 4);
            kit[i].seedInto(spec);
            spec.squad(squadId);
            sim.spawn(spec);
        }
        Squad squad = sim.getSquad(squadId);
        if (squad != null) {
            squad.originalSize = SQUAD_SIZE;
            // Populated at spawn rather than left for the first tick: the
            // scene reads liveness to decide when a wave is gone, and a squad
            // that reports nobody alive before the sim has run once is read as
            // destroyed on the tick it was created.
            squad.aliveMembers = SQUAD_SIZE;
            squad.assignedObjective = ObjectiveAssignment.attackMove(
                    squadId, CENTRE_X, OBJECTIVE_Y);
        }
        return new Wave(squadId);
    }

    /**
     * Advances one tick and keeps the waves' story current: which lane each has
     * committed to, when it died, and when the follow-up goes in.
     *
     * <p>The follow-up is sent when the first wave is gone rather than on a
     * timer, because "the squad ahead of you was destroyed" is the premise. A
     * timer would sometimes send it alongside survivors and record a different
     * scene under the same name.
     */
    static void advance(Scene scene, int tick) {
        BattleSimulation sim = scene.sim();
        for (Wave wave : scene.waves()) {
            if (wave.spawnedTick < 0) continue;
            Lane lane = laneOf(sim, wave.squadId);
            if (lane != Lane.NEITHER && wave.lane == Lane.NEITHER) wave.lane = lane;
            int alive = alive(sim, wave.squadId);
            if (alive > 0) wave.stood = true;
            if (wave.stood && wave.destroyedTick < 0 && alive == 0) {
                wave.destroyedTick = tick;
            }
        }
        Wave first = scene.first();
        if (first.destroyedTick >= 0 && !scene.ambushCleared()[0]) {
            // The killers leave with their work done. Everything a router can
            // observe about the two lanes is now identical again, except that
            // one of them is full of dead marines — which is the only variable
            // this scene is trying to change.
            for (long id : scene.ambush()) {
                if (sim.resolveUnit(id) != 0L) sim.world().setHp(id, 0f);
            }
            scene.ambushCleared()[0] = true;
        }
        if (first.destroyedTick >= 0 && scene.following() == null) {
            Wave next = spawnWave(sim, scene.waves().size(), CENTRE_X, START_Y);
            next.spawnedTick = tick;
            scene.waves().add(next);
        }
        sim.advance(BattleSimulation.TICK_DT);
    }

    /**
     * True once the squad whose choice this loop is about has made it. In the
     * control that is the only squad on the map; in the ambushed loop it is the
     * follow-up, and the first wave committing to the lane it was spawned in
     * says nothing.
     */
    static boolean finished(Scene scene) {
        if (scene.ambush().isEmpty()) return scene.first().lane != Lane.NEITHER;
        Wave following = scene.following();
        return following != null
                && (following.lane != Lane.NEITHER || following.destroyedTick >= 0);
    }

    /**
     * The lane a squad is in, from where its living members actually are. Read
     * off position rather than off the order because the order names only the
     * objective — which lane it took is exactly the thing not written down.
     */
    static Lane laneOf(BattleSimulation sim, int squadId) {
        Squad squad = sim.getSquad(squadId);
        if (squad == null || squad.aliveMembers <= 0) return Lane.NEITHER;
        float sum = 0f;
        int count = 0;
        for (int i = 0, n = sim.squadMemberCount(squadId); i < n; i++) {
            long member = sim.squadMemberAt(squadId, i);
            if (sim.resolveUnit(member) == 0L) continue;
            float y = sim.world().y(member);
            if (y > DIVIDER_BOTTOM_Y || y < DIVIDER_TOP_Y) continue;
            sum += sim.world().x(member);
            count++;
        }
        if (count == 0) return Lane.NEITHER;
        return sum / count < CENTRE_X ? Lane.WEST : Lane.EAST;
    }

    static int alive(BattleSimulation sim, int squadId) {
        Squad squad = sim.getSquad(squadId);
        return squad == null ? 0 : Math.max(0, squad.aliveMembers);
    }
}
