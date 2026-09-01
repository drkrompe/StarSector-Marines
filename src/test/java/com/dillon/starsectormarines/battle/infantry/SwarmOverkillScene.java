package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
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
 * One squad, open ground, and a rush of things that die to a single rifle.
 *
 * <p>It exists because the Conquest matrix cannot ask this question at all: its
 * defenders are marines and emplacements, and the failure here needs an enemy
 * whose durability is smaller than one shooter's output. A {@code SWARM_RUNNER}
 * carries 20 structure and a marine's rifle deals 18, so <b>a second rifle on
 * the same runner is very nearly a wasted shot</b> — and while it is being
 * wasted the rest of the rush is closing unanswered. A runner moves at 3.2
 * cells a second against a marine's 2.0 and fights at a cell and a half, so
 * there is no falling back out of it either.
 *
 * <p>That is the case a per-body crowding term is blind to. Counting how many
 * allies already aim at something says nothing about whether they have killed
 * it, so the same toll is charged for joining fire on a heavy chassis that
 * needs every rifle in the squad and for joining fire on a runner that is
 * already dead.
 *
 * <h2>What to read</h2>
 * <p><b>Marines alive at the end, and how long the swarm took to clear.</b>
 * Both, because either alone can be got the wrong way: a squad that clears fast
 * having lost half its strength has not done well, and one that survives by the
 * rush running out of bodies has not either. The runners still standing at the
 * end says which of those happened.
 *
 * <p>The control is the same scene with {@code -Dbattle.targeting.committedFire=false},
 * which restores the per-body count. Nothing else differs — same seed, same
 * geometry, same rush.
 */
final class SwarmOverkillScene {

    static final int WIDTH = 80;
    static final int HEIGHT = 40;

    private static final int MARINE_X = 15;
    private static final int SQUAD_SIZE = 8;
    private static final long KIT_SEED = 20260901L;

    /**
     * Eighteen cells of open ground between the two, which is inside a runner's
     * 20-cell sight and inside a marine's 24-cell reach. Any further and the
     * rush simply cannot see the squad to start: perception gating means a unit
     * targets what it can see or what its squad believes, and a swarm that
     * believes nothing stands where it spawned.
     */
    private static final int SWARM_X = MARINE_X + 18;
    private static final int SWARM_SIZE = 20;

    /** One loop's running tally. */
    static final class Tally {
        int marineSquadId;
        int ticks;
        int marinesAlive;
        int runnersAlive;
        /** First tick with no live runner left, or -1 if the rush outlasted the recording. */
        int clearedTick = -1;
        int marinesStarting;
        int runnersStarting;
    }

    static final class Scene {
        final BattleSimulation sim;
        final Tally tally = new Tally();

        Scene(BattleSimulation sim) { this.sim = sim; }

        BattleSimulation sim() { return sim; }

        Tally tally() { return tally; }
    }

    private SwarmOverkillScene() {}

    static Scene build() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(WIDTH, HEIGHT));
        // The scene is about how fire is distributed, not about who wins, and a
        // decided battle stops ticking everything it wants to watch.
        sim.setMissionCompletionEnabled(false);

        Scene scene = new Scene(sim);
        scene.tally.marineSquadId = spawnSquad(sim, scene.tally);
        int swarmSquadId = spawnSwarm(sim, scene.tally);

        // Both sides are ordered to clear the one zone this map has, and
        // without that they do not fight at all. Eight marines meeting twenty
        // runners is an unfavourable local balance and the doctrine that reads
        // it is right: the squad disengages. The rush breaks too once it has
        // taken casualties. The first recording of this scene was both sides
        // withdrawing in opposite directions, twenty seconds of nobody in
        // contact, and a difference between arms that could not have shown up
        // because neither was firing. A mission goal outranks survival by
        // bucket, which is what pins them in the fight the scene is named for.
        int zone = sim.getZoneGraph().zoneIdAt(WIDTH / 2, HEIGHT / 2);
        Squad marines = sim.getSquad(scene.tally.marineSquadId);
        if (marines != null) {
            marines.assignedObjective = ObjectiveAssignment.clearZone(
                    scene.tally.marineSquadId, zone);
        }
        Squad swarm = sim.getSquad(swarmSquadId);
        if (swarm != null) {
            swarm.assignedObjective = ObjectiveAssignment.clearZone(swarmSquadId, zone);
        }
        return scene;
    }

    /** Armed and squadded: an unarmed, unsquadded spawn is scenery. */
    private static int spawnSquad(BattleSimulation sim, Tally tally) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        MarineLoadout[] kit = InfantryLoadoutRolls.playerSquad(
                SQUAD_SIZE, new Random(KIT_SEED));
        int midY = HEIGHT / 2;
        for (int i = 0; i < SQUAD_SIZE; i++) {
            EntitySpec spec = new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE,
                    MARINE_X + i % 2, midY - SQUAD_SIZE / 4 + i / 2);
            kit[i].seedInto(spec);
            spec.squad(squadId);
            sim.spawn(spec);
        }
        Squad squad = sim.getSquad(squadId);
        if (squad != null) {
            squad.originalSize = SQUAD_SIZE;
            squad.aliveMembers = SQUAD_SIZE;
        }
        tally.marinesStarting = SQUAD_SIZE;
        tally.marinesAlive = SQUAD_SIZE;
        return squadId;
    }

    /**
     * The rush, as one squad of its own. A swarm minted without a squad would
     * acquire no targets at all — acquisition runs off the squad — and would
     * stand in the open being shot, which is a recording of nothing.
     */
    private static int spawnSwarm(BattleSimulation sim, Tally tally) {
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.SWARM_RUNNER);
        int midY = HEIGHT / 2;
        for (int i = 0; i < SWARM_SIZE; i++) {
            EntitySpec spec = new EntitySpec("s" + i, Faction.DEFENDER,
                    UnitType.SWARM_RUNNER,
                    SWARM_X + i % 4, midY - SWARM_SIZE / 8 + i / 4)
                    .squad(squadId);
            sim.spawn(spec);
        }
        Squad squad = sim.getSquad(squadId);
        if (squad != null) {
            squad.originalSize = SWARM_SIZE;
            squad.aliveMembers = SWARM_SIZE;
        }
        tally.runnersStarting = SWARM_SIZE;
        tally.runnersAlive = SWARM_SIZE;
        return squadId;
    }

    /** One tick, and the tally kept current from what is still standing. */
    static void advance(Scene scene, int tick) {
        BattleSimulation sim = scene.sim;
        Tally tally = scene.tally;
        tally.ticks++;

        int marines = 0;
        int runners = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (!sim.identity().type(u).combatant) continue;
            if (sim.identity().faction(u) == Faction.MARINE) marines++;
            else if (sim.identity().faction(u) == Faction.DEFENDER) runners++;
        }
        tally.marinesAlive = marines;
        tally.runnersAlive = runners;
        if (runners == 0 && tally.clearedTick < 0) tally.clearedTick = tick;

        sim.advance(BattleSimulation.TICK_DT);
    }
}
