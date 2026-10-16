package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Disposable native battle: ordinary catalog batteries against finite, ordinary fighter sorties. */
public final class MissileAaTestSetup {
    public static final int WIDTH = 300, HEIGHT = 200;
    public static final int WAVES = 6;
    public static final float FIRST_WAVE_SECONDS = 4f, WAVE_INTERVAL_SECONDS = 15f;
    public static final List<Lane> LANES = List.of(
            new Lane("locust", "@missileAaLaneLocust", 55, 95),
            new Lane("swarmer", "@missileAaLaneSwarmer", 150, 95),
            new Lane("proximity", "@missileAaLaneProximity", 245, 95));

    public record Lane(String id, String labelKey, int x, int y) {
        public String structureId() { return "structure.aa-" + id; }
        public String label() { return Strings.resolve(labelKey); }
    }
    public record Flight(long id, int laneIndex, int wave, FighterProfile profile, float dueSeconds,
                         ShuttleMission mission) { }
    public record Setup(BattleSimulation sim, List<Long> batteries, List<Flight> flights) { }

    private MissileAaTestSetup() { }

    public static BattleSimulation create(long seed, List<ShuttleAssignment> manifest) {
        return createDetailed(seed, manifest).sim();
    }

    public static Setup createDetailed(long seed, List<ShuttleAssignment> manifest) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        // Close the actual footprints before navigation is constructed; AA rounds clear their own low mount.
        for (Lane lane : LANES) for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            grid.setWalkable(lane.x + dx, lane.y + dy, false);
            grid.setSeeThrough(lane.x + dx, lane.y + dy, true);
            grid.setEdgeCoverSuppressed(lane.x + dx, lane.y + dy, true);
        }
        BattleSimulation sim = new BattleSimulation(grid, topology, seed);
        sim.setSurface(SurfacePalette.ARID);
        sim.setMissionCompletionEnabled(false);
        // Neutral range observation reveals both sides; targeting still reads normal faction hostility.
        sim.getVisionState().addContributor(Faction.DEFENDER);
        sim.getBattleResources().disableCompoundProduction();
        List<Long> batteries = new ArrayList<>();
        for (Lane lane : LANES) batteries.add(sim.spawn(MapTurret.create("test-aa-" + lane.id,
                Faction.MARINE, lane.structureId(), lane.x, lane.y)));
        companyLift(sim, manifest == null ? List.of() : manifest, seed);
        List<Flight> flights = new ArrayList<>();
        FighterProfile[] profiles = {FighterProfile.TALON, FighterProfile.WASP, FighterProfile.THUNDER,
                FighterProfile.BROADSWORD, FighterProfile.TALON, FighterProfile.WASP};
        for (int wave = 0; wave < WAVES; wave++) for (int index = 0; index < LANES.size(); index++) {
            Lane lane = LANES.get(index);
            float due = FIRST_WAVE_SECONDS + wave * WAVE_INTERVAL_SECONDS + index * .6f;
            // Alternating oblique approaches keep the same ordinary strike/turn/egress machinery in play.
            float side = wave % 2 == 0 ? -1f : 1f;
            float entryX = lane.x + side * 38f;
            float entryY = HEIGHT + 12f;
            long craft = sim.spawnSortie(profiles[wave], Faction.DEFENDER, lane.x, lane.y,
                    entryX, entryY, lane.x - side * 38f, HEIGHT + 12f, due);
            ShuttleMission mission = sim.world().mission(craft);
            mission.strikeSortie = true;
            flights.add(new Flight(craft, index, wave, profiles[wave], due, mission));
        }
        List<Flight> retained = List.copyOf(flights);
        for (int index = 0; index < LANES.size(); index++)
            sim.addObjective(new LaneObjective(LANES.get(index), batteries.get(index), index, retained));
        return new Setup(sim, List.copyOf(batteries), retained);
    }

    private static void companyLift(BattleSimulation sim, List<ShuttleAssignment> manifest, long seed) {
        Random random = new Random(seed);
        List<LandingPad> pads = new ArrayList<>();
        for (int index = 0; index < manifest.size(); index++) {
            ShuttleAssignment assignment = manifest.get(index);
            int x = 28 + index * 12, y = 10;
            LandingPad pad = LandingPad.civilian(x, y, LandingPad.Approach.SOUTH);
            pads.add(pad);
            long craft = sim.spawnShuttle(assignment.type, assignment.airframe, Faction.MARINE,
                    x + .5f, y + .5f, x, -12, x, -12, index * 1.5f, assignment.seatsPerSortie);
            ShuttleMission mission = sim.world().mission(craft);
            mission.totalCycles = assignment.cycles;
            mission.manifestOrdinal = index;
            mission.commandClaim = SquadCommandClaim.mission("missile-aa-test", "Company observes from the safe apron");
            mission.cycleLoadouts = new MarineLoadout[assignment.cycles][];
            for (int cycle = 0; cycle < assignment.cycles; cycle++)
                mission.cycleLoadouts[cycle] = InfantryLoadoutRolls.playerSquad(assignment.seatsForCycle(cycle), random);
            mission.marineLoadout = mission.cycleLoadouts[0];
        }
        sim.setMarineLandingPads(pads);
    }

    /** Marker/readout only: debug observation neither awards a win nor substitutes combat outcomes. */
    public static final class LaneObjective implements Objective {
        public final Lane lane;
        public final long battery;
        private final int laneIndex;
        private final List<Flight> flights;
        LaneObjective(Lane lane, long battery, int laneIndex, List<Flight> flights) {
            this.lane = lane; this.battery = battery; this.laneIndex = laneIndex; this.flights = flights;
        }
        public String measure(BattleSimulation sim) {
            int launched = 0, lost = 0, escaped = 0;
            float seconds = sim.getSimTickIndex() * BattleSimulation.TICK_DT;
            for (Flight flight : flights) if (flight.laneIndex == laneIndex) {
                if (seconds >= flight.dueSeconds) launched++;
                ShuttleMission mission = flight.mission;
                if (mission != null && mission.destroyed) lost++;
                else if (seconds >= flight.dueSeconds && mission != null && mission.state == ShuttleState.GONE) escaped++;
            }
            return Strings.format("missileAaLaneMeasure", launched, WAVES, lost, escaped);
        }
        @Override public Faction owningFaction() { return Faction.MARINE; }
        @Override public void tick(BattleView sim) { }
        @Override public boolean isComplete() { return false; }
        @Override public boolean isFailed() { return false; }
        @Override public String displayName() { return lane.label(); }
    }
}
