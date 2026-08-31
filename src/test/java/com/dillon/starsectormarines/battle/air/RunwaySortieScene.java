package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A station with a strip, and one fighter flying the whole cycle off it.
 *
 * <p>The behaviour is the runway. Everything a strip buys over a vertical lift
 * happens on the ground: the aircraft has to be got out of its shed, round the
 * hangars, down to the threshold, and along the strip before anything is
 * flying, and the same in reverse when it comes home. Every unit test around
 * that pins one link — the phase hands to the next, the route avoids the wall,
 * the landing captures on the centreline. None of them can show the thing the
 * strip is actually for: that the crossing is a stretch of time during which
 * somebody can be standing beside it.
 *
 * <p>So the scene is a pair at one seed. In the first the cycle runs
 * unopposed and is simply what a sortie looks like end to end. In the second a
 * marine fire team is sitting on the apron the aircraft has to taxi across, and
 * the difference between the two recordings is the whole argument for making an
 * aircraft roll.
 *
 * <p>Distinct from {@code AirfieldSortieScene}, which is about a crew walking
 * out to a shuttle that lifts vertically off its pad. That field has no strip
 * and nothing on it rolls; widening it to cover this would have made one scene
 * answer two questions badly.
 *
 * <p>The map is authored by the production lot rather than drawn here. A
 * conquest ward hands the lot a rectangle its packing reserved; a scene has no
 * packing, so it states the rectangle and gets the same base — strip,
 * hardstands, taxiway, hangars and all.
 */
final class RunwaySortieScene {

    static final int WIDTH = 76;
    static final int HEIGHT = 78;

    /** Where the station sits, leaving ground in front of it for the target. */
    private static final int LOT_LEFT = 6;
    private static final int LOT_BOTTOM = 50;

    /** Fixed roll for every marine kit, so the recordings stay deterministic. */
    private static final long RIFLE_SEED = 4242L;

    /** The concentration the field flies against, out in front of the base. */
    private static final int TARGET_X = 40;
    private static final int TARGET_Y = 9;
    private static final int TARGET_SIZE = 9;

    /** The fire team that sits on the taxiway in the interrupted recording. */
    /**
     * Three, not a full fire team.
     *
     * <p>Six of them put twelve a second into the aircraft and killed it in
     * under four, beside its own shed, before it had crossed anything — which
     * is a real thing that can happen and is not a crossing under fire. Three
     * take about half its hull off during the run, so the recording is of an
     * aircraft getting through and paying for it.
     */
    private static final int INTERCEPT_SIZE = 3;

    /** How long the scene may tick while waiting for the field to scramble. */
    private static final int SCRAMBLE_PATIENCE_TICKS = 3000;

    /** What a given recording is about. */
    enum Variant {
        /** The full cycle with nobody interfering. */
        UNOPPOSED,
        /** The same cycle with a marine fire team astride the taxiway. */
        INTERCEPTED
    }

    record Scene(BattleSimulation sim, long craftId, Variant variant, String[] outcome) {}

    private RunwaySortieScene() {}

    /**
     * Stands up the station, waits for it to launch, and hands back the scene
     * at the moment the aircraft starts moving.
     *
     * <p>Waited for rather than dispatched by hand. The field decides to fly on
     * its own cadence and a scene that reached in and started the sortie itself
     * would be recording the scene's idea of a launch rather than the one the
     * game performs. The cost is a few silent seconds of setup, and the payoff
     * is that the first frame is the aircraft leaving its shed.
     */
    static Scene build(long seed, Variant variant) {
        // An aircraft is the size its hull's own spec says, and headless
        // nothing has told the resolver where that spec lives. Left unprimed
        // every hull on the field is one length, which is a recording of
        // aircraft none of them are.
        InstalledHullSpecs.install();
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
                topology.setRoomPurpose(x, y, RoomPurpose.GENERIC);
            }
        }

        GenContext gen = new GenContext(grid, topology, new Random(seed), WIDTH, HEIGHT, seed);
        // A station, because it is the only size with a strip and sheds — the
        // two things this recording exists to show.
        AirbaseLot.Size size = AirbaseLot.Size.STATION;
        AirbaseLot lot = new AirbaseLot(LOT_LEFT, LOT_BOTTOM,
                LOT_LEFT + size.width - 1, LOT_BOTTOM + size.depth - 1,
                AirbaseLot.Facing.SOUTH, size);
        lot.author(gen, new Random(seed));
        if (gen.runways.isEmpty()) throw new IllegalStateException("the station laid no strip");
        if (gen.shelters.isEmpty()) throw new IllegalStateException("the station laid no sheds");

        BattleSimulation sim = serialSimulation(grid, topology, seed);
        // About one behaviour and not about who wins. A decided battle stops
        // ticking everything, aircraft included.
        sim.setMissionCompletionEnabled(false);

        TacticalNode commandPost = new TacticalNode(TacticalNode.Kind.COMMAND_POST,
                LOT_LEFT + 4, HEIGHT - 6, 4, 4, 8, 8, Faction.DEFENDER, 70, 3);
        List<TacticalNode> nodes = new ArrayList<>(gen.tactical);
        nodes.add(commandPost);
        sim.setTacticalMap(new TacticalMap(nodes));
        for (Doodad doodad : gen.doodads) sim.addDoodad(doodad);

        // Fighters in the sheds and the strip installed, the way BattleSetup
        // does it. Without the strip the shed aircraft are sealed in.
        sim.getAirfieldService().installRunway(gen.runways.get(0));
        for (Gantry shelter : gen.shelters) {
            sim.getAirfieldService().addShelterBerth(shelter, FighterProfile.BROADSWORD);
        }
        sim.advance(BattleSimulation.TICK_DT);

        digInTheTarget(grid);
        spawnTarget(sim);

        long craft = waitForTheScramble(sim);
        if (variant == Variant.INTERCEPTED) spawnInterceptors(sim, craft);
        return new Scene(sim, craft, variant, new String[]{ null });
    }

    /**
     * Ticks until the field launches, and answers with the aircraft it sent.
     *
     * <p>The strike system picks the densest concentration on the map, which is
     * why the target is a bunched platoon rather than a scatter: this scene is
     * about the runway, and a field that declined to fly because nothing was
     * worth attacking would record an empty apron.
     */
    private static long waitForTheScramble(BattleSimulation sim) {
        for (int tick = 0; tick < SCRAMBLE_PATIENCE_TICKS; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
            for (long id : sim.getAirEntityIds()) {
                ShuttleMission mission = sim.world().mission(id);
                if (mission != null && mission.strikeSortie) return id;
            }
        }
        throw new IllegalStateException("the station never scrambled");
    }

    /** Cells of walled enclosure around the platoon, half-extent. */
    private static final int TARGET_PIT_HALF = 4;

    /**
     * Walls the platoon into a position it holds.
     *
     * <p>Not decoration, and found the hard way. A platoon left standing in the
     * open advances — it is infantry with an enemy on the map — and it walked
     * the twenty cells to the airfield and shot two of the three parked
     * fighters before the field's first sortie was due, so the recording was of
     * an air arm destroyed on the ground by the people it was about to attack.
     * Dug in, it stays where it is, stays bunched enough to be worth a sortie,
     * and stays alive to be attacked.
     *
     * <p>The walls are not the whole answer, which is the second thing this
     * cost. A gun run takes structure off every wall it lands on, so the strike
     * breaches the pit it is attacking and the survivors walk out of it — which
     * is right, and is what the ordnance is for. So the platoon also sits most
     * of the map from the base: far enough that a counterattack cannot reach
     * the field inside the recording, which is what keeps an unopposed loop
     * unopposed.
     *
     * <p>Open to the sky on purpose: a roof would intercept the aerial delivery
     * and the strike would arrive to find its target immune.
     */
    private static void digInTheTarget(NavigationGrid grid) {
        int left = TARGET_X - TARGET_PIT_HALF;
        int right = TARGET_X + TARGET_PIT_HALF;
        int bottom = TARGET_Y - TARGET_PIT_HALF;
        int top = TARGET_Y + TARGET_PIT_HALF;
        for (int x = left; x <= right; x++) {
            grid.setWalkable(x, bottom, false);
            grid.setWalkable(x, top, false);
        }
        for (int y = bottom; y <= top; y++) {
            grid.setWalkable(left, y, false);
            grid.setWalkable(right, y, false);
        }
    }

    /**
     * The concentration the strike is flown against: a bunched platoon well
     * clear of the base, so the run is a flight rather than a hop and the
     * aircraft is over open ground when it fires.
     */
    private static void spawnTarget(BattleSimulation sim) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        MarineLoadout[] kit = InfantryLoadoutRolls.playerSquad(
                TARGET_SIZE, new Random(RIFLE_SEED));
        for (int i = 0; i < TARGET_SIZE; i++) {
            EntitySpec spec = new EntitySpec("t" + i, Faction.MARINE, UnitType.MARINE,
                    TARGET_X + (i % 3), TARGET_Y + (i / 3));
            kit[i].seedInto(spec);
            spec.role(UnitRole.GARRISON).home(TARGET_X, TARGET_Y).squad(squadId);
            sim.spawn(spec);
        }
        if (squad != null) squad.originalSize = TARGET_SIZE;
    }

    /**
     * The fire team on the taxiway, put down once the aircraft is rolling.
     *
     * <p>Placed after the scramble rather than before it, and that is the
     * difference between this recording and a raid. A team standing on the
     * apron when the recording starts does not wait for anybody to taxi: it
     * shoots the parked fighters in their sheds, and measured over thirteen
     * seconds it destroyed all three before the field's first sortie was even
     * due. That is a real thing an attacker can do and it is
     * {@code AirfieldSortieScene}'s raid loop, not this one. Here the question
     * is what a fire team does to an aircraft that is already crossing open
     * ground.
     *
     * <p>Strung across the line the aircraft is actually taxiing, between where
     * it is now and the threshold it is heading for, so it has to go past them.
     * Given rifles and a squad: a marine spawned bare carries nothing and
     * cannot fire, and an ambush of those is six people watching an aircraft
     * taxi past.
     */
    private static void spawnInterceptors(BattleSimulation sim, long craft) {
        ShuttleMission mission = sim.world().mission(craft);
        AirBody body = sim.world().kinematics(craft);
        // Two thirds of the way to the threshold, across the direction of travel.
        float alongX = mission.holdX - body.x;
        float alongY = mission.holdY - body.y;
        float meetX = body.x + alongX * 0.75f;
        float meetY = body.y + alongY * 0.75f;
        float length = Math.max(0.001f, (float) Math.hypot(alongX, alongY));
        float acrossX = -alongY / length;
        float acrossY = alongX / length;

        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE_BLUE);
        Squad squad = sim.getSquad(squadId);
        MarineLoadout[] kit = InfantryLoadoutRolls.playerSquad(
                INTERCEPT_SIZE, new Random(RIFLE_SEED));
        int placed = 0;
        for (int i = 0; i < INTERCEPT_SIZE; i++) {
            float offset = i - (INTERCEPT_SIZE - 1) / 2f;
            int x = Math.round(meetX + acrossX * offset);
            int y = Math.round(meetY + acrossY * offset);
            if (!sim.getGrid().inBounds(x, y) || !sim.getGrid().isWalkable(x, y)) continue;
            EntitySpec spec = new EntitySpec("i" + i, Faction.MARINE,
                    UnitType.MARINE_BLUE, x, y);
            kit[i].seedInto(spec);
            spec.role(UnitRole.GARRISON).home(x, y).squad(squadId);
            sim.spawn(spec);
            placed++;
        }
        if (placed == 0) throw new IllegalStateException("nowhere to put the ambush");
        if (squad != null) squad.originalSize = placed;
    }

    /** Whether the sortie this recording is about has resolved, either way. */
    static boolean finished(Scene scene) {
        ShuttleMission mission = scene.sim().world().mission(scene.craftId());
        return mission == null || mission.state == ShuttleState.GONE;
    }

    /** What the recording is showing right now, in the words the caption uses. */
    static String phase(Scene scene) {
        ShuttleMission mission = scene.sim().world().mission(scene.craftId());
        if (mission == null) {
            return scene.outcome()[0] != null ? scene.outcome()[0] : "sortie over";
        }
        if (mission.state == ShuttleState.GONE) {
            if (scene.outcome()[0] == null) scene.outcome()[0] = "HOME";
            return scene.outcome()[0];
        }
        return switch (mission.state) {
            case TAXI_OUT -> "taxiing out";
            case HOLDING_SHORT -> "holding short";
            case TAKEOFF_ROLL -> "takeoff roll";
            case INCOMING -> "outbound";
            case ATTACK_RUN -> "GUN RUN";
            case REPOSITION -> "coming round";
            case RETURNING -> "on approach";
            case LANDING_ROLL -> "rolling out";
            case TAXI_IN -> "taxiing in";
            default -> mission.state.name().toLowerCase(java.util.Locale.ROOT);
        };
    }

    /** Hull left on the aircraft, as a percentage — what the fire team is taking off it. */
    static int hullPercent(Scene scene) {
        ShuttleMission mission = scene.sim().world().mission(scene.craftId());
        if (mission == null) return 0;
        float max = Math.max(1f, FighterProfile.BROADSWORD.maxHp());
        return Math.max(0, Math.round(100f * scene.sim().world().hp(scene.craftId()) / max));
    }

    /** How many of the platoon the strike was flown against are still alive. */
    static int targetsStanding(Scene scene) {
        BattleSimulation sim = scene.sim();
        int alive = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (sim.identity().faction(u) != Faction.MARINE) continue;
            if (sim.identity().type(u) != UnitType.MARINE) continue;
            alive++;
        }
        return alive;
    }

    /**
     * Serial unit updates, so two recordings of the same seed are the same
     * battle. Parallel scheduling is fine for a game and useless for a
     * comparison.
     */
    private static BattleSimulation serialSimulation(NavigationGrid grid, CellTopology topology,
                                                     long seed) {
        String property = UnitUpdateSystem.MINIMUM_PARALLEL_UNITS_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, Integer.toString(Integer.MAX_VALUE));
        try {
            return new BattleSimulation(grid, topology, seed);
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }
}
