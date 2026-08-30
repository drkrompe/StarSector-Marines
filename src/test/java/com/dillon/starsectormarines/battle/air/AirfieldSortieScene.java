package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementRequest;
import com.dillon.starsectormarines.battle.command.reinforcement.ShuttleMeans;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
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
 * <p>So the scene is built as a set. The same field, the same seed. In the
 * first the crew walks out unopposed; in the second a marine fire team is
 * already sitting on the approach to the pad. Watching those two side by side
 * is the whole argument for loading on the ground rather than spawning loaded.
 *
 * <p>The third asks the other question a field raises, and it is not about a
 * crew at all: what happens when the attacker goes for the aircraft instead of
 * the sortie. The aircraft standing on the hardstands are real units, so a fire
 * team can burn them where they sit, and a field with nothing left on it stops
 * answering requests however firmly its ground is still held. That is a
 * different way to end an enemy's air from taking the compound, and this is
 * where it is visible.
 *
 * <p>The map is authored rather than generated. A conquest map's airfield is
 * the real thing and is exercised by the generator's own tests; here it would
 * only make the recording bigger, slower, and harder to read, and the question
 * being asked has nothing to do with where the packing put the apron.
 */
final class AirfieldSortieScene {

    static final int WIDTH = 64;
    static final int HEIGHT = 48;

    /** Where the lot sits on this scene's map, leaving the crew ground to cross. */
    private static final int LOT_LEFT = 8;
    private static final int LOT_BOTTOM = 14;

    /** Far from the field, so the delivery is a flight rather than a hop. */
    private static final int RALLY_X = 32;
    private static final int RALLY_Y = 6;

    /** Fixed roll for every marine kit in the scene, so the recordings stay deterministic. */
    private static final long RIFLE_SEED = 4242L;

    /**
     * The raid's fire team: strung out along the apron, a few cells in front of
     * the stands — close enough that the hulls are inside rifle reach the
     * moment the recording starts.
     */
    private static final int RAID_STANDOFF = 4;
    private static final int RAID_SIZE = 6;

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
    /** What a given recording is about. */
    enum Variant {
        /** The crew walks out and boards with nobody shooting at them. */
        UNOPPOSED,
        /** The same walk with a marine fire team astride it. */
        UNDER_FIRE,
        /** No sortie: a fire team on the field, burning the aircraft on their stands. */
        RAID
    }

    record Scene(BattleSimulation sim, long shuttleId, Variant variant, String[] outcome) {}

    private AirfieldSortieScene() {}

    /**
     * Stand up the field, dispatch one sortie, and optionally put a fire team
     * across the walk to the pad.
     */
    static Scene build(long seed, Variant variant) {
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
        // The production lot, placed by hand at a fixed rectangle. A ward hands
        // it one that its own packing reserved; a scene has no packing, so it
        // states the rectangle and gets the same base. Set back from the
        // marines' spawn edge so the crew's walk is the subject rather than a
        // step: in a ward the base is in the rear and that walk is short.
        AirbaseLot.Size size = AirbaseLot.Size.FIELD;
        AirbaseLot lot = new AirbaseLot(LOT_LEFT, LOT_BOTTOM,
                LOT_LEFT + size.width - 1, LOT_BOTTOM + size.depth - 1,
                AirbaseLot.Facing.SOUTH, size);
        lot.author(gen, new Random(seed));
        rememberPads(gen.landingPads);

        BattleSimulation sim = serialSimulation(grid, topology, seed);
        // These recordings are about one behaviour, not about who wins, and a
        // terminal battle stops ticking — including the aircraft. The raid
        // variant needs this outright: its defender side is three parked hulls
        // and nothing else, so the battle is decided on the first tick and the
        // whole loop would record six marines standing perfectly still. The
        // sortie variants need it for the same reason once their crew is dead.
        sim.setMissionCompletionEnabled(false);
        // The command post is the other half of the supply gate. It sits well
        // clear of the field so the recording is about the airfield alone.
        TacticalNode commandPost = new TacticalNode(TacticalNode.Kind.COMMAND_POST,
                6, 6, 4, 4, 8, 8, Faction.DEFENDER, 70, 3);
        List<TacticalNode> nodes = new ArrayList<>(gen.tactical);
        nodes.add(commandPost);
        sim.setTacticalMap(new TacticalMap(nodes));
        for (Doodad doodad : gen.doodads) sim.addDoodad(doodad);

        // Real aircraft on the hardstands, registered the way BattleSetup does
        // it. Without this the field is scenery and a sortie conjures its craft
        // at the pad — which is exactly the thing based aircraft replaced, and
        // would make this recording a picture of the old behaviour.
        for (LandingPad pad : gen.landingPads) {
            if (pad.purpose != LandingPad.Purpose.GARRISON_AIRFIELD) continue;
            sim.getAirfieldService().addBerth(pad, ShuttleMeans.SORTIE_TYPE,
                    AirBody.facingToward(pad.approach.dx, pad.approach.dy));
        }
        // One tick to stand them up before anybody is asked about them.
        sim.advance(1f / 30f);

        // Both variants need marines on the map, because the simulation stops
        // the instant a side is absent — one faction present is a finished
        // battle, and a finished battle does not tick an aircraft. The
        // unopposed variant therefore puts a lone marine in the far corner,
        // nowhere near the field or the crew's line of march: the sortie is
        // unopposed where it matters, which is what the pair is comparing.
        spawnMarines(sim, variant);

        if (variant == Variant.RAID) {
            // Nothing is dispatched. The recording is about the aircraft on the
            // ground, and a sortie in the air would only take one of them out
            // of the picture.
            return new Scene(sim, 0L, variant, new String[]{ null });
        }

        ShuttleMeans means = new ShuttleMeans(TraversalAxis.SOUTH_TO_NORTH,
                null, RiskLevel.LOW, null,
                List.copyOf(gen.landingPads));
        means.dispatch(sim, new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL, RALLY_X, RALLY_Y));

        long[] air = sim.getAirEntityIds();
        if (air.length == 0) throw new IllegalStateException("the scene dispatched no sortie");
        return new Scene(sim, air[0], variant, new String[]{ null });
    }

    /**
     * The marine presence: a fire team astride the walk to the pad, or a single
     * distant observer that only keeps the battle live.
     */
    private static void spawnMarines(BattleSimulation sim, Variant variant) {
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
        if (variant == Variant.UNDER_FIRE) {
            for (int i = 0; i < AMBUSH_SIZE; i++) {
                cells.add(new int[]{ AMBUSH_X - AMBUSH_SIZE / 2 + i, AMBUSH_Y });
            }
        }
        MarineLoadout[] kit = InfantryLoadoutRolls.playerSquad(
                cells.size(), new Random(RIFLE_SEED));
        int index = 0;
        for (int[] cell : cells) {
            EntitySpec spec = new EntitySpec("ambush-" + index, Faction.MARINE,
                    UnitType.MARINE_BLUE, cell[0], cell[1]);
            // Rifles. A marine spawned without a loadout carries nothing and
            // cannot shoot, so an "ambush" of them is six people standing in a
            // field watching a crew walk past — which is what this variant
            // recorded before anyone checked.
            kit[index].seedInto(spec);
            index++;
            spec.role(UnitRole.GARRISON).home(cell[0], cell[1]).squad(squadId);
            sim.spawn(spec);
        }
        if (squad != null) squad.originalSize = cells.size();
        if (variant == Variant.RAID) spawnRaid(sim);
    }

    /** The berths a raid is aimed at, filled in when the field is authored. */
    private static final List<int[]> RAID_TARGET_PADS = new ArrayList<>();

    private static void rememberPads(List<LandingPad> pads) {
        RAID_TARGET_PADS.clear();
        for (LandingPad pad : pads) {
            if (pad.purpose == LandingPad.Purpose.GARRISON_AIRFIELD) {
                RAID_TARGET_PADS.add(new int[]{ pad.centerX, pad.centerY });
            }
        }
    }

    /**
     * The raiding fire team: a marine squad on the apron itself, pushing the
     * airfield.
     *
     * <p>Not a garrison like the ambush team. A garrison holds a post and
     * shoots what walks into it, which is exactly right for an ambush on
     * somebody's line of march and exactly wrong here — a parked aircraft never
     * walks anywhere, so a garrison would sit and look at it. These are given
     * the field as an objective and push it, the same way the frontage scene's
     * assault squads are pointed at a compound.
     */
    private static void spawnRaid(BattleSimulation sim) {
        if (RAID_TARGET_PADS.isEmpty()) throw new IllegalStateException("no hardstands to raid");
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        MarineLoadout[] kit = InfantryLoadoutRolls.playerSquad(
                RAID_SIZE, new Random(RIFLE_SEED));
        // Spread across the apron in front of the stands, within rifle reach of
        // the hulls. Placed off the pads the generator actually laid down
        // rather than a guessed cell: the field moves with the seed, and a fire
        // team standing where the apron used to be shoots nothing.
        int[] first = RAID_TARGET_PADS.get(0);
        int[] last = RAID_TARGET_PADS.get(RAID_TARGET_PADS.size() - 1);
        int span = Math.max(1, last[0] - first[0]);
        for (int i = 0; i < RAID_SIZE; i++) {
            int x = first[0] + span * i / Math.max(1, RAID_SIZE - 1);
            int y = first[1] - RAID_STANDOFF;
            EntitySpec spec = new EntitySpec("raid-" + i, Faction.MARINE,
                    UnitType.MARINE, x, y);
            kit[i].seedInto(spec);
            spec.squad(squadId);
            sim.spawn(spec);
        }
        if (squad != null) squad.originalSize = RAID_SIZE;
    }

    /** What the recording is showing right now, in the words the caption uses. */
    static String phase(Scene scene) {
        if (scene.variant() == Variant.RAID) return raidPhase(scene);
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
            case RETURNING -> "on approach  •  " + scene.outcome()[0];
            // This scene's aircraft lift off their hardstands; a strip is a
            // different base's procedure and no loop here flies one.
            case TAXI_OUT, HOLDING_SHORT, TAKEOFF_ROLL, LANDING_ROLL, TAXI_IN ->
                    "on the ground";
            case GONE -> scene.outcome()[0];
        };
    }

    /**
     * The raid caption: how much of the field is left, and whether it can still
     * put anything in the air.
     */
    private static String raidPhase(Scene scene) {
        AirfieldService field = scene.sim().getAirfieldService();
        int standing = 0;
        int burned = 0;
        for (AirfieldService.Berth berth : field.berths()) {
            if (berth.state == AirfieldService.BerthState.DESTROYED) burned++;
            else standing++;
        }
        if (standing == 0) {
            scene.outcome()[0] = "FIELD BURNED — " + burned + " aircraft lost, no lift";
            return scene.outcome()[0];
        }
        return standing + " aircraft standing  •  " + burned + " burned"
                + (field.hasAirworthyAirframe() ? "  •  field can still fly" : "");
    }

    /** Aircraft still standing on their hardstands. */
    static int aircraftStanding(Scene scene) {
        int standing = 0;
        for (AirfieldService.Berth berth : scene.sim().getAirfieldService().berths()) {
            if (berth.state != AirfieldService.BerthState.DESTROYED) standing++;
        }
        return standing;
    }

    private static int aboardTimeLeft(ShuttleMission mission) {
        return Math.max(0, Math.round(mission.boardingPatience));
    }

    /** Whether the recording has run its course, one way or the other. */
    static boolean finished(Scene scene) {
        if (scene.variant() == Variant.RAID) return aircraftStanding(scene) == 0;
        ShuttleMission mission = scene.sim().world().mission(scene.shuttleId());
        return mission == null || mission.state == ShuttleState.GONE;
    }

    /** How many of the embarking crew are still alive and walking. */
    static int crewStillWalking(Scene scene) {
        if (scene.variant() == Variant.RAID) return 0;
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
