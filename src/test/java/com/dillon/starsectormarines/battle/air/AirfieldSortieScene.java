package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementRequest;
import com.dillon.starsectormarines.battle.command.reinforcement.ShuttleMeans;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressAirfield;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A garrison airfield, a shuttle on its hardstand, and the crew that has to
 * walk out to it — small enough to run headless in seconds and specific enough
 * to watch one behavior.
 *
 * <p>The behavior is embarkation. A reinforcement sortie flown from an authored
 * field is not loaded when it spawns: the garrison marches a squad out from its
 * own rear edge, the squad crosses open ground to the pad, and the craft lifts
 * with whoever got aboard. Every unit test around that pins one link of the
 * chain — the means sets the state, the system takes a marine aboard, the gate
 * rejects without a field. What none of them can show is the thing the change
 * was actually for: that the crossing is a period of time during which somebody
 * can be shot, and that shooting them stops the lift without touching the
 * aircraft.
 *
 * <p>So the scene is built as a pair. The same field, the same sortie, the same
 * seed; in one the crew walks out unopposed, and in the other a marine fire team
 * is already sitting on the approach to the pad. Watching them side by side is
 * the whole argument for loading on the ground rather than spawning loaded.
 *
 * <p>The map is authored rather than generated. A conquest map's airfield is
 * the real thing and is exercised by the generator's own tests; here it would
 * only make the recording bigger, slower, and harder to read, and the question
 * being asked has nothing to do with where the packing put the apron.
 */
final class AirfieldSortieScene {

    static final int WIDTH = 64;
    static final int HEIGHT = 48;

    /** The band the apron is allowed to sit in, so the crew has ground to cross. */
    private static final int FIELD_BAND_BOTTOM = 18;
    private static final int FIELD_BAND_TOP = 30;

    /** Far from the field, so the delivery is a flight rather than a hop. */
    private static final int RALLY_X = 32;
    private static final int RALLY_Y = 6;

    /** Where the opposed variant puts its fire team: on the crew's line of march. */
    private static final int AMBUSH_X = 31;
    private static final int AMBUSH_Y = 40;
    private static final int AMBUSH_SIZE = 6;

    /**
     * What a recording needs to caption itself.
     *
     * <p>{@code outcome} is a one-element holder because a finished craft is
     * reaped: the mission is gone by the time the last frames are drawn, and a
     * recording whose final caption reads "sortie gone" has thrown away the one
     * fact the whole loop exists to report.
     */
    record Scene(BattleSimulation sim, long shuttleId, boolean opposed, String[] outcome) {}

    private AirfieldSortieScene() {}

    /**
     * Stand up the field, dispatch one sortie, and optionally put a fire team
     * across the walk to the pad.
     */
    static Scene build(long seed, boolean opposed) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        boolean[][] ground = new boolean[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
                topology.setRoomPurpose(x, y, RoomPurpose.GENERIC);
                ground[x][y] = x >= 2 && y >= 2 && x < WIDTH - 2 && y < HEIGHT - 2;
            }
        }

        // The field is authored by the production airfield rather than drawn
        // here. A scene that hand-rolls an apron records a scene's idea of one,
        // and the whole value of watching this is that it is the field the
        // generator actually lays down — hardstands, bowsers, fuel point, mast.
        GenContext gen = new GenContext(grid, topology, new Random(seed), WIDTH, HEIGHT, seed);
        // Sited in the middle band rather than wherever it fits. In a ward the
        // field is in the rear and the crew comes from the rear edge, so the
        // walk is short; here it is the subject, and a scene that put the pad
        // beside the spawn would record five marines stepping aboard and
        // nothing else.
        FortressAirfield field = FortressAirfield.site(gen, ground,
                TraversalAxis.SOUTH_TO_NORTH, 2, FIELD_BAND_BOTTOM,
                WIDTH - 3, FIELD_BAND_TOP);
        if (field == null) throw new IllegalStateException("no room for an airfield");
        field.author(gen, TraversalAxis.SOUTH_TO_NORTH);

        BattleSimulation sim = serialSimulation(grid, topology, seed);
        // The command post is the other half of the supply gate. It sits well
        // clear of the field so the recording is about the airfield alone.
        TacticalNode commandPost = new TacticalNode(TacticalNode.Kind.COMMAND_POST,
                6, 6, 4, 4, 8, 8, Faction.DEFENDER, 70, 3);
        List<TacticalNode> nodes = new ArrayList<>(gen.tactical);
        nodes.add(commandPost);
        sim.setTacticalMap(new TacticalMap(nodes));
        for (Doodad doodad : gen.doodads) sim.addDoodad(doodad);

        // Both variants need marines on the map, because the simulation stops
        // the instant a side is absent — one faction present is a finished
        // battle, and a finished battle does not tick an aircraft. The
        // unopposed variant therefore puts a lone marine in the far corner,
        // nowhere near the field or the crew's line of march: the sortie is
        // unopposed where it matters, which is what the pair is comparing.
        spawnMarines(sim, opposed);

        ShuttleMeans means = new ShuttleMeans(TraversalAxis.SOUTH_TO_NORTH,
                null, RiskLevel.LOW, null,
                List.copyOf(gen.landingPads));
        means.dispatch(sim, new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL, RALLY_X, RALLY_Y));

        long[] air = sim.getAirEntityIds();
        if (air.length == 0) throw new IllegalStateException("the scene dispatched no sortie");
        return new Scene(sim, air[0], opposed, new String[]{ null });
    }

    /**
     * The marine presence: a fire team astride the walk to the pad, or a single
     * distant observer that only keeps the battle live.
     */
    private static void spawnMarines(BattleSimulation sim, boolean opposed) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE_BLUE);
        Squad squad = sim.getSquad(squadId);
        List<int[]> cells = new ArrayList<>();
        // The distant observer is in both variants and is the reason either
        // recording runs at all: the simulation returns immediately once a side
        // is absent, so a battle whose only marines were the fire team freezes
        // the moment the crew kills them — mid-crossing, with the shuttle still
        // on its pad and the clock stopped. One marine nobody can reach keeps
        // the clock running so the deadline is allowed to expire.
        cells.add(new int[]{ 2, 2 });
        if (opposed) {
            for (int i = 0; i < AMBUSH_SIZE; i++) {
                cells.add(new int[]{ AMBUSH_X - AMBUSH_SIZE / 2 + i, AMBUSH_Y });
            }
        }
        int index = 0;
        for (int[] cell : cells) {
            EntitySpec spec = new EntitySpec("ambush-" + index++, Faction.MARINE,
                    UnitType.MARINE_BLUE, cell[0], cell[1]);
            spec.role(UnitRole.GARRISON).home(cell[0], cell[1]).squad(squadId);
            sim.spawn(spec);
        }
        if (squad != null) squad.originalSize = cells.size();
    }

    /** What the sortie is doing right now, in the words the caption uses. */
    static String phase(Scene scene) {
        ShuttleMission mission = scene.sim().world().mission(scene.shuttleId());
        if (mission == null) {
            return scene.outcome()[0] != null ? scene.outcome()[0] : "sortie gone";
        }
        if (mission.state == ShuttleState.DEPARTING || mission.state == ShuttleState.GONE) {
            scene.outcome()[0] = mission.deboardedThisSortie > 0
                    ? "DELIVERED " + mission.deboardedThisSortie
                    : "SORTIE LOST — nobody reached the pad";
        }
        return switch (mission.state) {
            case PENDING -> "waiting";
            case LOADING -> "LOADING on the pad  •  " + mission.marinesRemaining
                    + " aboard  •  " + aboardTimeLeft(mission) + "s to go";
            case INCOMING -> "airborne  •  " + mission.marinesRemaining + " aboard";
            case LANDED -> "landed  •  " + mission.marinesRemaining + " still aboard";
            case HOVER_STATION -> "overwatch";
            case DEPARTING -> "outbound for the field  •  " + scene.outcome()[0];
            case GONE -> scene.outcome()[0];
        };
    }

    private static int aboardTimeLeft(ShuttleMission mission) {
        return Math.max(0, Math.round(mission.boardingPatience));
    }

    /** Whether the sortie has run its course, one way or the other. */
    static boolean finished(Scene scene) {
        ShuttleMission mission = scene.sim().world().mission(scene.shuttleId());
        return mission == null || mission.state == ShuttleState.GONE;
    }

    /** How many of the embarking crew are still alive and walking. */
    static int crewStillWalking(Scene scene) {
        ShuttleMission mission = scene.sim().world().mission(scene.shuttleId());
        if (mission == null || mission.embarkSquadId == Squad.NO_SQUAD) return 0;
        return scene.sim().squadMemberCount(mission.embarkSquadId);
    }

    /**
     * Forced-serial, for the reason every other scene is: the same seed has to
     * produce the same battle, or a difference between two recordings means
     * nothing — and this scene's whole point is a comparison between two.
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
