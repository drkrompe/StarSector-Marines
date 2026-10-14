package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment.FireMission;
import com.dillon.starsectormarines.battle.deployable.FieldMortarService.Phase;
import com.dillon.starsectormarines.battle.deployable.FieldMortarService.Tube;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.bsp.FortStage;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The field mortar against a fort's wall nests: the reason it exists. A battery fort in its production layout, a
 * stationary garrison, and one four-marine section standing off the main gate beyond the nests' reach (a nest covers
 * 24 cells; a rifle 22) with a spotter's sight of them. Nothing else of the attacker's force is on the map, so what
 * happens to the nests is the section's doing.
 *
 * <ul>
 *   <li>{@code mortar-team}: the gunner carries a field mortar. The section halts, believes in the nests it can see,
 *       sets the tube down, and shells them for as long as the magazine lasts.</li>
 *   <li>{@code rifle-control}: the same four marines, same ground, field rifles only. Nobody in the section can reach
 *       a nest from there and nothing orders them closer, so this records what the distance alone does.</li>
 *   <li>{@code rushed}: the mortar section again, with a squad of eight sent at it across the open from the flank
 *       once it is in action. The tube is vulnerable while it stands: the rush closes inside the dead zone, the
 *       gunner lifts the tube (a channel that can be interrupted by death) or is killed at it, and the tube goes with
 *       him.</li>
 * </ul>
 *
 * <p>The verdicts judge the shape of what happened (the tube stood off outside the nests' reach, it fired on what the
 * section believed, the control did not reach). How many nests fall, and how fast, is reported and never asserted:
 * every number in the tube's data is untuned, and the scene exists to read them off.
 */
public final class FieldMortarScene implements BehaviorScene {

    /** Where the last loop stood its section and the gate it faced, for a snapshot suite choosing its camera. */
    private static volatile int[] lastSection = new int[] {150, 300, 150, 260};

    /** The section cell and the gate mouth cell of the most recently built loop: {sx, sy, gx, gy}. */
    public static int[] lastSection() { return lastSection.clone(); }

    private static final int WIDTH = 300;
    private static final int HEIGHT = 420;
    private static final int TICKS = 4500;
    /** Cells out from the main gate's mouth the section stands: past every nest's reach, inside a rifleman's sight. */
    private static final int STANDOFF = 37;
    private static final int GARRISON = 6;
    private static final int RUSH_SIZE = 36;
    /** Cells the rush starts from, along the flank. */
    private static final int RUSH_DISTANCE = 75;
    private static final String RIFLE = "weapon.field-rifle";
    private static final List<String> ALL_LOOPS = List.of("mortar-team", "rifle-control", "rushed",
            "section-march", "section-expiry", "section-engaged", "section-belief", "section-control");
    /** Cells about the fort's core the marching squad's fire mission covers: its wall nests and works. */
    private static final int MISSION_RADIUS = 30;
    /** A marching squad: twelve marines, one of them the gunner. */
    private static final int MARCH_SIZE = 12;
    /** Cells past the standoff the marching squad starts, so it meets the fort's works at the edge of the tube's band. */
    private static final int MARCH_LEAD = 30;
    /** A short plain window for the expiry and engaged loops, so the window is seen to end inside the run. */
    private static final float SHORT_WINDOW = 10f;

    private final List<String> loops;
    private final int frameFrom;
    private final int frameTo;
    private final int frameEvery;

    /** Every loop, a frame every 60 ticks across the run. */
    public FieldMortarScene() {
        this(ALL_LOOPS, 0, TICKS, 60);
    }

    /** Only the named loops, recording frames in a window of the run. */
    public FieldMortarScene(List<String> loops, int frameFrom, int frameTo, int frameEvery) {
        this.loops = List.copyOf(loops);
        this.frameFrom = frameFrom;
        this.frameTo = frameTo;
        this.frameEvery = frameEvery;
    }

    @Override public String id() { return "field-mortar"; }

    @Override public String label() {
        return "Field mortar: a four-marine section with a tube stands off a fort beyond its nests' reach and shells "
                + "them from what its spotter has seen; the same section with rifles cannot; a rushed tube is lost";
    }

    @Override public int frameDelayMillis() { return 100; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : loops) {
            reports.add(loop.startsWith("section-") ? playSection(loop, window(frames)) : playOne(loop, window(frames)));
        }
        return reports;
    }

    private FrameSink window(FrameSink sink) {
        if (sink == FrameSink.NONE) return sink;
        return new FrameSink() {
            @Override public void frame(String loopId, BattleSimulation sim, int tick, String caption) {
                if (tick >= frameFrom && tick <= frameTo) sink.frame(loopId, sim, tick, caption);
            }

            @Override public void advanced(String loopId, BattleSimulation sim, int tick) {
                sink.advanced(loopId, sim, tick);
            }
        };
    }

    private record FortGround(NavigationGrid grid, CellTopology topology, List<DefensePost> posts,
                              CompoundLayout.Placement at) {
        int[] cell(int lx, int ly) {
            return at.cell(lx, ly);
        }
    }

    private static FortGround stamp() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L), WIDTH, HEIGHT, 7L);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(grid, topology, null));
        ctx.put(BspKeys.BUILDINGS, BuildingFloodFill.populate(topology, 7L));
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL.withDeclaredDefences(
                new GroundDefences(0, false).withForts(FortKind.BATTERY_FORT, 1)));
        FortStage stage = new FortStage();
        stage.run(ctx);
        return new FortGround(grid, topology, List.copyOf(ctx.defensePosts), stage.placed().get(0).placement());
    }

    private SceneReport playOne(String loop, FrameSink frames) {
        boolean tube = !loop.equals("rifle-control");
        boolean rushed = loop.equals("rushed");
        FortGround terrain = stamp();
        FortPlan.Gate gate = FortPlan.of(FortKind.BATTERY_FORT).gates().get(0);
        int[] mouth = terrain.cell(gate.mouthX(), gate.mouthY());
        int[] outStep = terrain.cell(gate.mouthX() + gate.outX(), gate.mouthY() + gate.outY());
        int outX = outStep[0] - mouth[0];
        int outY = outStep[1] - mouth[1];
        int[] team = clamp(mouth[0] + outX * STANDOFF, mouth[1] + outY * STANDOFF);
        lastSection = new int[] {team[0], team[1], mouth[0], mouth[1]};
        SceneBuilder builder = SceneBuilder.over(terrain.grid(), terrain.topology()).posts(terrain.posts())
                .missionCompletion(false);
        SceneBuilder.SquadSpec section = builder.squad("section").faction(Faction.MARINE).size(4)
                .at(team[0], team[1]).primary(RIFLE).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, team[0], team[1]));
        if (tube) section.special(SpecialEquipmentRegistry.FIELD_MORTAR_ID, 1);
        section.done();
        int[] core = BatteryFort.coreCentre(FortAspect.FIRE_CONTROL);
        int[] garrisonAt = terrain.cell(core[0], core[1] + 2);
        builder.squad("garrison").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(GARRISON)
                .at(garrisonAt[0], garrisonAt[1]).primary("weapon.smg").stationary().done();
        // Keeps both sides on the map for the whole run; nobody can reach either.
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 5, HEIGHT - 5);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, 145, 5);
        if (rushed) {
            // From the flank, perpendicular to the way out of the gate, across open ground.
            int[] start = clamp(team[0] - outY * RUSH_DISTANCE, team[1] + outX * RUSH_DISTANCE);
            builder.squad("rush").faction(Faction.DEFENDER).type(UnitType.MARINE_RED).size(RUSH_SIZE)
                    .at(start[0], start[1]).primary("weapon.smg")
                    .assigned(id -> ObjectiveAssignment.attackMove(id, team[0], team[1])).done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261017L);
        Fort fort = sim.forts().forts().get(0);
        Trace trace = new Trace(fort, world, rushed);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, frameEvery, tick -> String.format(Locale.ROOT, "%s / tick %d / %s",
                        loop, tick, trace.caption()))
                .run(TICKS);
        return report(loop, tube, rushed, trace, sim);
    }

    /**
     * The section loops: a twelve-marine squad marching on the fort's main gate from beyond the tube's band, its first
     * member carrying the tube. {@code section-march} is the production dials; {@code section-expiry} a ten-second window
     * that engagement may not extend, to see the crew pack up and the squad reform; {@code section-engaged} the same
     * window that engagement may extend, to a cap; {@code section-belief} the broad trigger (any worthwhile belief, no
     * assigned mission); {@code section-control} the old behaviour (sections off), in which a marching gunner never halts
     * to set up. Every loop but {@code section-belief} carries a fire mission on the fort's works, which is what
     * opens a section by default.
     */
    private SceneReport playSection(String loop, FrameSink frames) {
        float window = MortarSections.DEFAULT_WINDOW_SECONDS;
        float extension = MortarSections.DEFAULT_EXTENSION_SECONDS;
        float cap = MortarSections.DEFAULT_CAP_SECONDS;
        boolean on = !loop.equals("section-control");
        boolean belief = loop.equals("section-belief");
        MortarSections.Trigger previousTrigger = MortarSections.trigger();
        MortarSections.setTriggerForEvidence(belief ? MortarSections.Trigger.ANY : MortarSections.DEFAULT_TRIGGER);
        if (loop.equals("section-expiry")) {
            window = SHORT_WINDOW;
            extension = 0f;
        } else if (loop.equals("section-engaged")) {
            window = SHORT_WINDOW;
            cap = 40f;
        }
        MortarSections.setForEvidence(on, window, extension, cap, MortarSections.DEFAULT_COOLDOWN_SECONDS);
        try {
            FortGround terrain = stamp();
            FortPlan.Gate gate = FortPlan.of(FortKind.BATTERY_FORT).gates().get(0);
            int[] mouth = terrain.cell(gate.mouthX(), gate.mouthY());
            int[] outStep = terrain.cell(gate.mouthX() + gate.outX(), gate.mouthY() + gate.outY());
            int outX = outStep[0] - mouth[0];
            int outY = outStep[1] - mouth[1];
            int[] start = clamp(mouth[0] + outX * (STANDOFF + MARCH_LEAD), mouth[1] + outY * (STANDOFF + MARCH_LEAD));
            lastSection = new int[] {start[0], start[1], mouth[0], mouth[1]};
            SceneBuilder builder = SceneBuilder.over(terrain.grid(), terrain.topology()).posts(terrain.posts())
                    .missionCompletion(false);
            // Every loop but the belief one carries the fire mission a commander would give it: the fort's works.
            int[] core = BatteryFort.coreCentre(FortAspect.FIRE_CONTROL);
            int[] coreCell = terrain.cell(core[0], core[1]);
            FireMission mission = belief ? null : new FireMission(coreCell[0], coreCell[1], MISSION_RADIUS);
            builder.squad("section").faction(Faction.MARINE).size(MARCH_SIZE).at(start[0], start[1]).primary(RIFLE)
                    .special(SpecialEquipmentRegistry.FIELD_MORTAR_ID, 1)
                    .assigned(id -> ObjectiveAssignment.attackMove(id, mouth[0], mouth[1]).withFireMission(mission)).done();
            int[] garrisonAt = terrain.cell(core[0], core[1] + 2);
            builder.squad("garrison").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(GARRISON)
                    .at(garrisonAt[0], garrisonAt[1]).primary("weapon.smg").stationary().done();
            builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 5, HEIGHT - 5);
            builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, 145, 5);
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            sim.random().setSeed(20261017L);
            Fort fort = sim.forts().forts().get(0);
            SectionTrace trace = new SectionTrace(fort, world, mouth);
            SceneRun.of(world).observe(trace)
                    .frames(frames, loop, frameEvery, tick -> String.format(Locale.ROOT, "%s / tick %d / %s",
                            loop, tick, trace.caption()))
                    .run(TICKS);
            return sectionReport(loop, on, window, extension, cap, trace, sim);
        } finally {
            MortarSections.setTriggerForEvidence(previousTrigger);
            MortarSections.setForEvidence(true, MortarSections.DEFAULT_WINDOW_SECONDS,
                    MortarSections.DEFAULT_EXTENSION_SECONDS, MortarSections.DEFAULT_CAP_SECONDS,
                    MortarSections.DEFAULT_COOLDOWN_SECONDS);
        }
    }

    private SceneReport sectionReport(String loop, boolean on, float window, float extension, float cap,
                                      SectionTrace t, BattleSimulation sim) {
        List<Verdict> verdicts = new ArrayList<>();
        MortarSections sections = sim.fieldMortars().sections();
        if (!on) {
            verdicts.add(Verdict.of("no-section-was-ever-opened", sections.opened() == 0,
                    sections.opened() + " sections opened; tubes set down " + sim.fieldMortars().setUps()));
        } else {
            verdicts.add(Verdict.of("the-march-halted-for-the-tube", t.openTick >= 0,
                    "section opened at tick " + t.openTick + " with the squad " + round(t.distanceAtOpen)
                            + " cells from the gate"));
            verdicts.add(Verdict.of("a-crew-of-four-set-the-tube-down", t.crewSize == MortarSections.CREW_SIZE
                    && t.tubeStandingTick >= 0, "crew of " + t.crewSize + ", tube standing at tick " + t.tubeStandingTick));
            verdicts.add(Verdict.of("the-rest-stayed-on-the-leash",
                    t.openTick >= 0 && t.maxLeashDistance <= MortarSections.LEASH_CELLS + 2.5f,
                    "furthest leashed marine " + round(t.maxLeashDistance) + " cells from the tube (leash "
                            + MortarSections.LEASH_CELLS + ")"));
            verdicts.add(Verdict.of("it-fired", t.roundsFired > 0, t.roundsFired + " rounds fired, first at tick "
                    + t.firstRoundTick));
            verdicts.add(Verdict.of("the-section-ended-and-the-squad-moved-on", t.endTick >= 0 && t.squadAdvancedAfter,
                    "ended at tick " + t.endTick + " (" + t.endReason + "); squad " + round(t.distanceAtEnd)
                            + " cells from the gate then, " + round(t.distanceAtFinish) + " at the finish"));
            if (loop.equals("section-expiry")) {
                verdicts.add(Verdict.of("the-window-ran-out", "window expired".equals(t.endReason),
                        "ended because: " + t.endReason + ", held " + round(t.openSeconds) + "s of a " + window + "s window"));
            }
            if (loop.equals("section-engaged")) {
                verdicts.add(Verdict.of("engagement-held-the-window-open",
                        t.openSeconds > window + 1f && t.openSeconds <= cap + MortarSections.OPENING_GRACE_SECONDS,
                        "held " + round(t.openSeconds) + "s against a " + window + "s window and a " + cap + "s cap"));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("windowSeconds", window);
        metrics.put("extensionSeconds", extension);
        metrics.put("capSeconds", cap);
        metrics.put("sectionsOpened", sections.opened());
        metrics.put("sectionsExpired", sections.expired());
        metrics.put("sectionsAbandoned", sections.abandoned());
        metrics.put("sectionsUnderFire", sections.underFire());
        metrics.put("sectionOpenTick", t.openTick);
        metrics.put("sectionEndTick", t.endTick);
        metrics.put("sectionHeldSeconds", round(t.openSeconds));
        metrics.put("tubeSetDowns", sim.fieldMortars().setUps());
        metrics.put("tubeStandingTick", t.tubeStandingTick);
        metrics.put("roundsFired", t.roundsFired);
        metrics.put("nestsKilled", t.nestsKilled);
        metrics.put("nestsTotal", t.nestTotal);
        metrics.put("squadDistanceToGateAtOpen", round(t.distanceAtOpen));
        metrics.put("squadDistanceToGateAtEnd", round(t.distanceAtEnd));
        metrics.put("squadDistanceToGateAtFinish", round(t.distanceAtFinish));
        metrics.put("furthestLeashedMarine", round(t.maxLeashDistance));
        metrics.put("marinesLost", MARCH_SIZE - t.squadAlive);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    /** Watches one marching squad and its section. */
    private static final class SectionTrace implements TickObserver {
        private final Fort fort;
        private final long[] squad;
        private final int[] gate;
        int nestTotal;
        int openTick = -1;
        int endTick = -1;
        int tubeStandingTick = -1;
        int firstRoundTick = -1;
        int roundsFired;
        int nestsKilled;
        int crewSize;
        int squadAlive;
        float distanceAtOpen;
        float distanceAtEnd;
        float distanceAtFinish;
        float maxLeashDistance;
        float openSeconds;
        String endReason = "";
        boolean squadAdvancedAfter;
        private MortarSections.Section section;
        private String caption = "";

        SectionTrace(Fort fort, SceneWorld world, int[] gate) {
            this.fort = fort;
            this.squad = world.members("section");
            this.gate = gate;
            this.nestTotal = fort.nestIds().size();
        }

        String caption() {
            return caption;
        }

        private float distanceToGate(BattleSimulation sim) {
            double sx = 0;
            double sy = 0;
            int n = 0;
            for (long id : squad) {
                if (!sim.world().isAlive(id)) continue;
                sx += sim.world().x(id);
                sy += sim.world().y(id);
                n++;
            }
            if (n == 0) return Float.MAX_VALUE;
            return (float) Math.hypot(sx / n - gate[0], sy / n - gate[1]);
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            int alive = 0;
            for (long id : squad) if (sim.world().isAlive(id)) alive++;
            squadAlive = alive;
            int killed = 0;
            for (long nest : fort.nestIds()) if (!sim.world().isAlive(nest)) killed++;
            nestsKilled = killed;
            float here = distanceToGate(sim);
            distanceAtFinish = here;
            MortarSections sections = sim.fieldMortars().sections();
            if (section == null) {
                for (MortarSections.Section s : sections.sections()) {
                    section = s;
                    openTick = tick;
                    crewSize = s.crewSize();
                    distanceAtOpen = here;
                }
            }
            if (section != null && endTick < 0) {
                if (sections.sectionOf(section.squadId) == section) {
                    for (long id : squad) {
                        if (!sim.world().isAlive(id) || section.isCrew(id)) continue;
                        maxLeashDistance = Math.max(maxLeashDistance, (float) Math.hypot(
                                sim.world().x(id) - section.anchorX(), sim.world().y(id) - section.anchorY()));
                    }
                    openSeconds = (tick - openTick) * BattleSimulation.TICK_DT;
                } else {
                    endTick = tick;
                    endReason = section.endReason();
                    distanceAtEnd = here;
                }
            }
            if (endTick >= 0 && distanceToGate(sim) < distanceAtEnd - 4f) squadAdvancedAfter = true;
            Tube tube = section == null ? null : sim.fieldMortars().tubeOfCarrier(squad[0]);
            if (tube != null) {
                if (tube.phase() == Phase.SERVING && tubeStandingTick < 0) tubeStandingTick = tick;
                roundsFired = Math.max(roundsFired, tube.roundsFired());
                if (firstRoundTick < 0 && roundsFired > 0) firstRoundTick = tick;
            }
            caption = String.format(Locale.ROOT, "section %s / tube %s / rounds %d / nests down %d of %d / squad %d "
                            + "alive %s cells out", section == null ? "not yet" : (endTick >= 0 ? "ended" : "open"),
                    tube == null ? "none" : tube.phase().name().toLowerCase(Locale.ROOT), roundsFired, nestsKilled,
                    nestTotal, squadAlive, round(here));
        }
    }

    private static int[] clamp(int x, int y) {
        return new int[] {Math.max(4, Math.min(WIDTH - 5, x)), Math.max(4, Math.min(HEIGHT - 5, y))};
    }

    private SceneReport report(String loop, boolean tube, boolean rushed, Trace t, BattleSimulation sim) {
        List<Verdict> verdicts = new ArrayList<>();
        if (tube) {
            verdicts.add(Verdict.of("the-section-set-the-tube-down", t.servingTick >= 0,
                    "tube registered at tick " + t.setupTick + ", standing at tick " + t.servingTick));
            verdicts.add(Verdict.of("it-stood-off-beyond-the-nests-reach", t.closestNestToSection >= 24f,
                    "closest live nest to any section member: " + round(t.closestNestToSection) + " cells (a nest reaches 24)"));
            verdicts.add(Verdict.of("it-fired-on-what-the-section-believed", t.roundsFired > 0,
                    t.roundsFired + " rounds fired, first at tick " + t.firstRoundTick));
            verdicts.add(Verdict.of("it-spent-its-magazine-not-more", t.roundsFired <= t.magazine,
                    t.roundsFired + " fired of a " + t.magazine + "-round magazine"));
        } else {
            verdicts.add(Verdict.of("the-rifles-did-not-reach-a-nest", t.nestWear < 0.05f,
                    "nest wear " + round(t.nestWear) + " nest-equivalents, closest nest " + round(t.closestNestToSection) + " cells"));
        }
        if (rushed) {
            verdicts.add(Verdict.of("the-rush-reached-the-section", t.rushContactTick >= 0,
                    "rush within 14 cells (the dead zone) of the gunner at tick " + t.rushContactTick));
            verdicts.add(Verdict.of("the-tube-answered-the-rush",
                    t.packTick >= 0 || t.tubeLostTick >= 0 || sim.fieldMortars().packs() > 0,
                    "pack began at tick " + t.packTick + ", tube lost at tick " + t.tubeLostTick));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("tubeRegisteredTick", t.setupTick);
        metrics.put("tubeStandingTick", t.servingTick);
        metrics.put("firstRoundTick", t.firstRoundTick);
        metrics.put("roundsFired", t.roundsFired);
        metrics.put("roundsLeft", t.roundsLeft);
        metrics.put("nestsKilled", t.nestsKilled);
        metrics.put("nestsTotal", fortNests(t));
        metrics.put("nestWearEquivalentNests", round(t.nestWear));
        metrics.put("firstNestKilledTick", t.firstNestKilledTick);
        metrics.put("lastNestKilledTick", t.lastNestKilledTick);
        metrics.put("sectionAliveAtEnd", t.sectionAlive);
        metrics.put("marinesLost", 4 - t.sectionAlive);
        metrics.put("closestNestToSection", round(t.closestNestToSection));
        metrics.put("garrisonAliveAtEnd", t.garrisonAlive);
        metrics.put("packBeganTick", t.packTick);
        metrics.put("tubeLostTick", t.tubeLostTick);
        metrics.put("packs", sim.fieldMortars().packs());
        metrics.put("tubesLostWithGunner", sim.fieldMortars().lostWithGunner());
        metrics.put("tubesShotOut", sim.fieldMortars().destroyedStanding());
        if (rushed) {
            metrics.put("rushContactTick", t.rushContactTick);
            metrics.put("rushAliveAtEnd", t.rushAlive);
            metrics.put("rushKilledByTheTube", RUSH_SIZE - t.rushAliveAtContact);
        }
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static int fortNests(Trace t) {
        return t.nestTotal;
    }

    private static float round(float v) {
        return Math.round(v * 10f) / 10f;
    }

    private static final class Trace implements TickObserver {
        private final Fort fort;
        private final SceneWorld world;
        private final boolean rushed;
        private final long[] section;
        private final long[] garrison;
        private final long[] rush;
        int nestTotal;
        int setupTick = -1;
        int servingTick = -1;
        int firstRoundTick = -1;
        int roundsFired;
        int roundsLeft;
        int magazine;
        int nestsKilled;
        float nestWear;
        int firstNestKilledTick = -1;
        int lastNestKilledTick = -1;
        int sectionAlive;
        int garrisonAlive;
        float closestNestToSection = Float.MAX_VALUE;
        int packTick = -1;
        int tubeLostTick = -1;
        int rushContactTick = -1;
        int rushAlive;
        int rushAliveAtContact = RUSH_SIZE;
        private boolean wasStanding;
        private String caption = "";

        Trace(Fort fort, SceneWorld world, boolean rushed) {
            this.fort = fort;
            this.world = world;
            this.rushed = rushed;
            this.section = world.members("section");
            this.garrison = world.members("garrison");
            this.rush = rushed ? world.members("rush") : new long[0];
            this.nestTotal = fort.nestIds().size();
        }

        String caption() {
            return caption;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            long gunner = section[0];
            int alive = 0;
            for (long id : section) if (sim.world().isAlive(id)) alive++;
            sectionAlive = alive;
            int g = 0;
            for (long id : garrison) if (sim.world().isAlive(id)) g++;
            garrisonAlive = g;
            int r = 0;
            for (long id : rush) if (sim.world().isAlive(id)) r++;
            rushAlive = r;

            int killed = 0;
            float worn = 0f;
            float closest = Float.MAX_VALUE;
            for (long nest : fort.nestIds()) {
                if (!sim.world().isAlive(nest)) {
                    killed++;
                    worn += 1f;
                    continue;
                }
                float full = sim.world().maxHp(nest) + (sim.world().hasArmor(nest) ? sim.world().maxArmor(nest) : 0f);
                float left = sim.world().hp(nest) + (sim.world().hasArmor(nest) ? sim.world().armor(nest) : 0f);
                if (full > 0) worn += 1f - left / full;
                for (long id : section) {
                    if (!sim.world().isAlive(id)) continue;
                    closest = Math.min(closest, (float) Math.hypot(sim.world().x(id) - sim.world().x(nest),
                            sim.world().y(id) - sim.world().y(nest)));
                }
            }
            if (killed > nestsKilled) {
                if (firstNestKilledTick < 0) firstNestKilledTick = tick;
                lastNestKilledTick = tick;
            }
            nestsKilled = killed;
            nestWear = worn;
            closestNestToSection = Math.min(closestNestToSection, closest);

            FieldMortarService mortars = sim.fieldMortars();
            Tube tube = mortars.tubeOfCarrier(gunner);
            boolean standing = tube != null && tube.phase() != Phase.SETTING_UP;
            if (tube != null) {
                if (setupTick < 0) setupTick = tick;
                magazine = tube.structure.mount.ammoCapacity;
                roundsFired = tube.roundsFired();
                roundsLeft = tube.rounds();
                if (tube.phase() == Phase.SERVING && servingTick < 0) servingTick = tick;
                if (tube.phase() == Phase.PACKING && packTick < 0) packTick = tick;
                if (firstRoundTick < 0 && roundsFired > 0) firstRoundTick = tick;
            }
            if (wasStanding && tube == null && tubeLostTick < 0 && packTick < 0) tubeLostTick = tick;
            wasStanding = standing;

            if (rushed && rushContactTick < 0 && sim.world().isAlive(gunner)) {
                for (long id : rush) {
                    if (!sim.world().isAlive(id)) continue;
                    if (Math.hypot(sim.world().x(id) - sim.world().x(gunner), sim.world().y(id) - sim.world().y(gunner)) <= 14f) {
                        rushContactTick = tick;
                        rushAliveAtContact = rushAlive;
                        break;
                    }
                }
            }
            caption = String.format(Locale.ROOT, "tube %s / rounds %d fired, %d left / nests down %d of %d / section %d%s",
                    tube == null ? "packed" : tube.phase().name().toLowerCase(Locale.ROOT), roundsFired, roundsLeft,
                    nestsKilled, nestTotal, sectionAlive, rushed ? " / rush " + rushAlive : "");
        }
    }
}
