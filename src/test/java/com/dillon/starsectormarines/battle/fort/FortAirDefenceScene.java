package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.airsupport.AirPoolAllowance;
import com.dillon.starsectormarines.battle.airsupport.AirPoolDeclaration;
import com.dillon.starsectormarines.battle.airsupport.AirPoolKind;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService.Event;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService.EventType;
import com.dillon.starsectormarines.battle.airsupport.AirPoolStock;
import com.dillon.starsectormarines.battle.airsupport.AirStrikeCapability;
import com.dillon.starsectormarines.battle.airsupport.AirStrikeDirector;
import com.dillon.starsectormarines.battle.airsupport.CommsOperators;
import com.dillon.starsectormarines.battle.airsupport.CommsOperators.Operator;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
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
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.bsp.FortStage;
import com.dillon.starsectormarines.battle.world.gen.fortress.AirDefenceYard;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * A fort's optional anti-air aspect, against the company's own bombers. Each loop stands the same walled fort on the
 * same field with a garrison inside it and a spotter squad with a radio west of it, and changes one thing about the
 * anti-air yard (guided battery, flak positions and a plotting console in a sandbagged yard inside the works).
 *
 * <ul>
 *   <li>{@code control}: a fort with no anti-air aspect; a strike is forced onto the garrison. The baseline for what the
 *       fort's ordinary nests do to a bomber.</li>
 *   <li>{@code aa-forced}: the same strike, forced, over a fort that carries the aspect. The yard fires on the bomber
 *       from inside its envelope and the bomber pays.</li>
 *   <li>{@code declined}: not forced. The side's director has sighted the fort's works, so the yard is a disclosed fact,
 *       and it estimates the pass at more than an airframe and sends none. {@code declined-control} is the same with
 *       no aspect.</li>
 *   <li>{@code wrecked}: the console is destroyed before the strike. The mounts are silent for good, the director's
 *       estimate falls, and the sky is open.</li>
 *   <li>{@code unpowered}: the power plant is destroyed before the strike. The yard still fires, at a quarter of the rate.</li>
 *   <li>{@code taken}: a squad of the company already stands in the yard and takes it. The mounts change sides with the
 *       yard and do not fire on the company's bomber.</li>
 *   <li>{@code shield-fort}: as {@code aa-forced}, over a shield fort, to show the aspect is not a battery fort's alone.</li>
 * </ul>
 *
 * <p>Five more loops are about the fort as a <em>spotter</em> for its holder's air ({@link FortSensors}, the radar on the
 * bunker roof), with no radio operator on the map at all. A squad of eight stands massed thirty-odd cells from the bunker, in the
 * radar's reach and out of the eye's, and the side whose air is AI-directed has three bombers in stock:
 * <ul>
 *   <li>{@code spots-control}: the fort's spotting is switched off for this battle only. The fort still sees the mass; nothing
 *       is called.</li>
 *   <li>{@code fort-spots}: the defenders have a pool and no radio; the fort is their only operator, sees the mass over the real
 *       ground and a defender bomber is called onto it.</li>
 *   <li>{@code spots-wrecked}: the bunker is destroyed first. The fort sees nothing and calls nothing.</li>
 *   <li>{@code spots-unpowered}: the plant is destroyed first. The dish stops and the fort has only its eye, which does not reach.</li>
 *   <li>{@code spots-taken}: the marines (with the pool) take the bunker and the plant. The fort stops watching for the
 *       garrison and spots for the marines, who call a strike on a defender mass the garrison's own fort used to cover.</li>
 * </ul>
 *
 * <p>Judged from mechanics (does the yard fire, stop, obey its holder, slow without power, and does the director read
 * it), not from a balance target. The numbers in the reports are evidence for design and are UNTUNED.
 */
public final class FortAirDefenceScene implements BehaviorScene {

    static final int WIDTH = 230;
    static final int HEIGHT = 120;
    static final int CX = 125;
    static final int CY = 60;
    private static final int TICKS = 3300;
    /** The tick the aspect is wrecked or unpowered, and the tick a strike is forced after it. */
    private static final int EVENT_TICK = 150;
    private static final int FORCE_TICK = 900;
    /** The window of the forced strike that the yard's loop records densely, and the cadence it records at. */
    static final int SALVO_FROM = 1120, SALVO_TO = 1220, SALVO_EVERY = 4;
    private static final int SPOTTER_X = 74;
    /** The radio squad stands well back, out of the fort's reach, so it is never suppressed; the scouts ahead sight the works. */
    private static final int OBSERVER_X = 24;
    private static final int GARRISON = 8;

    @Override public String id() { return "fort-air-defence"; }

    @Override public String label() {
        return "Fort anti-air aspect: a guided battery and flak in a yard inside the works shoot at a bomber in their "
                + "envelope; wrecking the console silences them, losing the power slows them, taking the yard turns them, "
                + "and a director that has sighted the fort declines the strike";
    }

    @Override public int frameDelayMillis() { return 250; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledWeaponSlots.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        try {
            Run control = playOne("control", frames);
            Run forced = playOne("aa-forced", frames);
            Run declinedControl = playOne("declined-control", frames);
            Run declined = playOne("declined", frames);
            Run wrecked = playOne("wrecked", frames);
            Run unpowered = playOne("unpowered", frames);
            Run taken = playOne("taken", frames);
            Run shield = playOne("shield-fort", frames);
            Spot spotControl = playSpot("spots-control", frames);
            Spot spots = playSpot("fort-spots", frames);
            Spot spotWrecked = playSpot("spots-wrecked", frames);
            Spot spotUnpowered = playSpot("spots-unpowered", frames);
            Spot spotTaken = playSpot("spots-taken", frames);
            reports.add(report("control", control, control, null));
            reports.add(report("aa-forced", forced, control, null));
            reports.add(report("declined-control", declinedControl, control, null));
            reports.add(report("declined", declined, control, declinedControl));
            reports.add(report("wrecked", wrecked, control, forced));
            reports.add(report("unpowered", unpowered, control, forced));
            reports.add(report("taken", taken, control, forced));
            reports.add(report("shield-fort", shield, control, forced));
            reports.add(spotReport("spots-control", spotControl, spotControl));
            reports.add(spotReport("fort-spots", spots, spotControl));
            reports.add(spotReport("spots-wrecked", spotWrecked, spots));
            reports.add(spotReport("spots-unpowered", spotUnpowered, spots));
            reports.add(spotReport("spots-taken", spotTaken, spots));
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
        return reports;
    }

    // ------------------------------------------------------------------------------------------------ world

    record Stamped(NavigationGrid grid, CellTopology topology, List<DefensePost> posts, FortStage.Placed placed) {}

    static Stamped stamp(FortKind kind, boolean air) {
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
                new GroundDefences(0, false).withForts(kind, 1)));
        FortStage stage = FortStage.pinned(List.of(new FortStage.Pin(kind, CX, CY, 0, CY, air)));
        stage.run(ctx);
        return new Stamped(grid, topology, List.copyOf(ctx.defensePosts), stage.placed().get(0));
    }

    private record Run(Trace trace, BattleSimulation sim, Fort fort) {}

    private static boolean hasAspect(String loop) {
        return !loop.equals("control") && !loop.equals("declined-control");
    }

    private static boolean forced(String loop) {
        return !loop.startsWith("declined");
    }

    private Run playOne(String loop, FrameSink frames) {
        FortKind kind = loop.equals("shield-fort") ? FortKind.SHIELD_FORT : FortKind.BATTERY_FORT;
        Stamped stamped = stamp(kind, hasAspect(loop));
        FortPlan plan = stamped.placed().plan();
        AirDefenceYard yard = plan.airDefence();
        int[] yardCell = stamped.placed().placement().cell(yard.centreX(), yard.centreY());
        int[] garrisonCell = openNear(stamped.grid(), stamped.placed().placement().cell(yard.centreX() + 5, yard.centreY() + 6));

        SceneBuilder builder = SceneBuilder.over(stamped.grid(), stamped.topology()).posts(stamped.posts())
                .missionCompletion(false);
        builder.squad("scouts").faction(Faction.MARINE).size(2).at(SPOTTER_X, CY).primary("weapon.smg")
                .health(100_000f).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, SPOTTER_X, CY)).done();
        builder.squad("observers").faction(Faction.MARINE).size(3).at(OBSERVER_X, CY).primary("weapon.smg")
                .special("special.comms-radio", 1).health(100_000f).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, OBSERVER_X, CY)).done();
        builder.squad("garrison").faction(Faction.DEFENDER).size(GARRISON).at(garrisonCell[0], garrisonCell[1])
                .primary("weapon.smg").health(100_000f).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, garrisonCell[0], garrisonCell[1])).done();
        if (loop.equals("taken")) {
            builder.squad("raiders").faction(Faction.MARINE).size(6).at(yardCell[0], yardCell[1]).primary("weapon.smg")
                    .health(100_000f).stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, yardCell[0], yardCell[1])).done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);
        Fort fort = sim.forts().forts().get(0);

        AirPoolStock bombers = new AirPoolStock(3, 3, 60f);
        // A commander asks for the strike itself (through its observer, as a company's plan does): the pool is not left to
        // its own director, whose designations need a line of sight no wall lets it have.
        AirPoolAllowance allowance = AirPoolAllowance.of(bombers, null, /*aiDirected*/ false);
        sim.setAirPool(new AirPoolService(AirPoolDeclaration.NONE.with(Faction.MARINE, allowance)));

        Trace trace = new Trace(world, fort, garrisonCell);
        TickObserver script = (s, tick) -> {
            if (tick == FORCE_TICK) {
                var pool = s.airPool();
                trace.outcome = pool.director(Faction.MARINE).strike(s, pool, garrisonCell[0] + 0.5f, garrisonCell[1] + 0.5f,
                        forced(loop)).name();
            }
            if (tick != EVENT_TICK) return;
            if (loop.equals("wrecked")) s.applyDamage(fort.place(FortAspect.AIR_DEFENCE).coreId(), 1_000_000f, 3.5f, 0f);
            if (loop.equals("unpowered")) s.applyDamage(fort.place(FortAspect.POWER).coreId(), 1_000_000f, 3.5f, 0f);
        };
        SceneRun run = SceneRun.of(world).observe(script, trace)
                .frames(frames, loop, 110, tick -> trace.caption(loop, tick));
        if (loop.equals("aa-forced")) {
            // The salvo is the evidence of a missile site, and it is over in a few seconds: sample it densely.
            run.run(SALVO_FROM);
            run.frames(frames, loop, SALVO_EVERY, tick -> trace.caption(loop, tick));
            run.run(SALVO_TO - SALVO_FROM);
            run.frames(frames, loop, 110, tick -> trace.caption(loop, tick));
            run.run(TICKS - SALVO_TO);
        } else {
            run.run(TICKS);
        }
        return new Run(trace, sim, fort);
    }

    static int[] openNear(NavigationGrid grid, int[] want) {
        for (int r = 0; r < 12; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = want[0] + dx, y = want[1] + dy;
                    if (grid.inBounds(x, y) && grid.isWalkable(x, y)) return new int[]{x, y};
                }
            }
        }
        return want;
    }

    // ------------------------------------------------------------------------------------------------ the fort as a spotter

    private static final int SPOT_TICKS = 2700;
    /** Cells west of the bunker the massed squad stands: inside the radar's reach and outside the eye's. */
    private static final int MASS_DISTANCE = 32;
    private static final int MASS_SIZE = 8;
    /** The tick the bunker or the plant is wrecked in the loops that do: before the first bomber is in stock. */
    private static final int SPOT_EVENT_TICK = EVENT_TICK;
    /** The tick the operators are counted: after the wreck, before the first airframe regenerates (10 s in). */
    private static final int SPOT_OBSERVE_TICK = 290;

    private record Spot(Fort fort, BattleSimulation sim, SpotTrace trace, int[] mass) {}

    private Spot playSpot(String loop, FrameSink frames) {
        // No anti-air yard: a yard the other side has sighted would refuse the strike for its own reason (see the loops above).
        Stamped stamped = stamp(FortKind.BATTERY_FORT, false);
        FortStage.Placed placed = stamped.placed();
        int[] bunker = placed.placement().cell(BatteryFort.coreCentre(FortAspect.FIRE_CONTROL)[0],
                BatteryFort.coreCentre(FortAspect.FIRE_CONTROL)[1]);
        int[] mass = openNear(stamped.grid(), new int[]{bunker[0] - MASS_DISTANCE, bunker[1]});
        boolean taken = loop.equals("spots-taken");
        Faction massSide = taken ? Faction.DEFENDER : Faction.MARINE;
        Faction poolSide = taken ? Faction.MARINE : Faction.DEFENDER;
        int[] bunkerFloor = BatteryFort.floor(FortAspect.FIRE_CONTROL);
        int[] plantFloor = BatteryFort.floor(FortAspect.POWER);
        int[] bunkerCell = openNear(stamped.grid(), placed.placement().cell(bunkerFloor[0] + 1, bunkerFloor[1] + 1));
        int[] plantCell = openNear(stamped.grid(), placed.placement().cell(plantFloor[0] + 1, plantFloor[1] + 1));
        int[] garrisonCell = openNear(stamped.grid(), placed.placement().cell(
                bunkerFloor[0] + 3, bunkerFloor[1] + 12));

        SceneBuilder builder = SceneBuilder.over(stamped.grid(), stamped.topology()).posts(stamped.posts())
                .missionCompletion(false);
        builder.squad("massed").faction(massSide).size(MASS_SIZE).at(mass[0], mass[1]).primary("weapon.smg")
                .health(100_000f).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, mass[0], mass[1])).done();
        builder.squad("garrison").faction(Faction.DEFENDER).size(GARRISON).at(garrisonCell[0], garrisonCell[1])
                .primary("weapon.smg").health(100_000f).stationary()
                .assigned(id -> ObjectiveAssignment.defendTrack(id, garrisonCell[0], garrisonCell[1])).done();
        if (taken) {
            builder.squad("bunker-raiders").faction(Faction.MARINE).size(4).at(bunkerCell[0], bunkerCell[1]).primary("weapon.smg")
                    .health(100_000f).stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, bunkerCell[0], bunkerCell[1])).done();
            builder.squad("plant-raiders").faction(Faction.MARINE).size(4).at(plantCell[0], plantCell[1]).primary("weapon.smg")
                    .health(100_000f).stationary()
                    .assigned(id -> ObjectiveAssignment.defendTrack(id, plantCell[0], plantCell[1])).done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261017L);
        Fort fort = sim.forts().forts().get(0);
        if (loop.equals("spots-control")) sim.forts().setSpotting(false);
        // The pool is the side's own AI director's: nobody asks for the strike here. Nobody carries a radio either.
        AirPoolAllowance allowance = AirPoolAllowance.of(new AirPoolStock(0, 3, 10f), null, true);
        sim.setAirPool(new AirPoolService(AirPoolDeclaration.NONE.with(poolSide, allowance)));

        SpotTrace trace = new SpotTrace(fort, poolSide, mass, garrisonCell);
        TickObserver script = (s, tick) -> {
            if (tick != SPOT_EVENT_TICK) return;
            if (loop.equals("spots-wrecked")) s.applyDamage(fort.place(FortAspect.FIRE_CONTROL).coreId(), 1_000_000f, 3.5f, 0f);
            if (loop.equals("spots-unpowered")) s.applyDamage(fort.place(FortAspect.POWER).coreId(), 1_000_000f, 3.5f, 0f);
        };
        SceneRun.of(world).observe(script, trace)
                .frames(frames, loop, 110, tick -> trace.caption(loop, tick))
                .run(SPOT_TICKS);
        return new Spot(fort, sim, trace, mass);
    }

    private static final class SpotTrace implements TickObserver {
        private final Fort fort;
        private final Faction poolSide;
        private final int[] mass;
        private final int[] garrison;
        final List<Event> events = new ArrayList<>();
        private int seenEvents;
        int operatorsAtStart = -1;
        int fortOperatorsAtStart = -1;
        boolean seesMassAtStart;
        boolean seesMassAtEnd;
        float rangeAtEnd;
        int flipTick = -1;
        int firstCallTick = -1;
        boolean fortObservesForOldHolderAtEnd = true;
        boolean observesForMarinesAtEnd;

        SpotTrace(Fort fort, Faction poolSide, int[] mass, int[] garrison) {
            this.fort = fort;
            this.poolSide = poolSide;
            this.mass = mass;
            this.garrison = garrison;
        }

        int count(EventType type) {
            int n = 0;
            for (Event e : events) if (e.type() == type && e.side() == poolSide) n++;
            return n;
        }

        /** Calls placed on the massed squad: within a cluster radius of where it stands. */
        int callsOnTheMass() {
            return callsNear(mass);
        }

        /** Calls placed on the garrison inside the works, which the radar also has in sight. */
        int callsOnTheGarrison() {
            return callsNear(garrison);
        }

        private int callsNear(int[] cell) {
            int n = 0;
            for (Event e : events) {
                if (e.type() == EventType.CALLED && e.side() == poolSide
                        && Math.hypot(e.x() - (cell[0] + 0.5f), e.y() - (cell[1] + 0.5f)) <= 8f) n++;
            }
            return n;
        }

        String caption(String loop, int tick) {
            Faction observer = FortSensors.observer(fort);
            String radar = fort.place(FortAspect.FIRE_CONTROL).wrecked() ? "radar WRECKED"
                    : observer == null ? "radar silent" : "radar for " + observer.name().toLowerCase(Locale.ROOT)
                    + String.format(Locale.ROOT, " (%.0f cells)", FortSensors.range(fort));
            return String.format(Locale.ROOT, "%s / tick %d / %s / calls %d / sorties %d", loop, tick, radar,
                    count(EventType.CALLED), count(EventType.LAUNCHED));
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            List<Event> all = sim.airPool().events();
            while (seenEvents < all.size()) events.add(all.get(seenEvents++));
            if (tick == SPOT_OBSERVE_TICK) {
                List<Operator> ops = CommsOperators.available(sim, poolSide);
                operatorsAtStart = ops.size();
                int atFort = 0;
                for (Operator op : ops) if (op.atFort()) atFort++;
                fortOperatorsAtStart = atFort;
                seesMassAtStart = FortSensors.sees(sim, fort, mass[0], mass[1]);
            }
            if (flipTick < 0 && fort.battery().owner() == Faction.MARINE) flipTick = tick;
            if (firstCallTick < 0 && count(EventType.CALLED) > 0) firstCallTick = tick;
            seesMassAtEnd = FortSensors.sees(sim, fort, mass[0], mass[1]);
            rangeAtEnd = FortSensors.range(fort);
            fortObservesForOldHolderAtEnd = FortSensors.observesFor(fort, Faction.DEFENDER);
            observesForMarinesAtEnd = FortSensors.observesFor(fort, Faction.MARINE);
        }
    }

    private SceneReport spotReport(String loop, Spot run, Spot reference) {
        SpotTrace t = run.trace();
        List<Verdict> verdicts = new ArrayList<>();
        int calls = t.count(EventType.CALLED);
        int launched = t.count(EventType.LAUNCHED);
        switch (loop) {
            case "spots-control" -> {
                verdicts.add(Verdict.of("the-fort-still-sees-the-mass", t.seesMassAtStart,
                        "the radar has the mass in sight: " + t.seesMassAtStart));
                verdicts.add(Verdict.of("but-nothing-is-called", calls == 0 && launched == 0 && t.fortOperatorsAtStart == 0,
                        "calls " + calls + ", sorties " + launched + ", fort operators " + t.fortOperatorsAtStart));
            }
            case "fort-spots" -> {
                verdicts.add(Verdict.of("the-fort-is-the-only-operator", t.operatorsAtStart == 1 && t.fortOperatorsAtStart == 1,
                        t.operatorsAtStart + " operators, " + t.fortOperatorsAtStart + " of them the fort"));
                verdicts.add(Verdict.of("it-sees-the-mass-over-real-ground", t.seesMassAtStart,
                        "the mass stands " + MASS_DISTANCE + " cells from the bunker, radar reach "
                                + (int) FortSensors.RADAR_RANGE_CELLS));
                verdicts.add(Verdict.of("a-strike-is-called-on-the-mass-and-flies", t.callsOnTheMass() >= 1 && launched >= 1,
                        t.callsOnTheMass() + " calls on the mass, first at tick " + t.firstCallTick + ", " + launched + " sorties"));
            }
            case "spots-wrecked" -> {
                verdicts.add(Verdict.of("the-bunker-is-wrecked", run.fort().place(FortAspect.FIRE_CONTROL).wrecked(), "wrecked"));
                verdicts.add(Verdict.of("the-fort-sees-nothing", !t.seesMassAtEnd && t.rangeAtEnd == 0f,
                        "sees the mass: " + t.seesMassAtEnd + ", range " + t.rangeAtEnd));
                verdicts.add(Verdict.of("and-calls-nothing", calls == 0 && launched == 0,
                        "calls " + calls + ", sorties " + launched + " (" + reference.trace().count(EventType.LAUNCHED)
                                + " with the radar standing)"));
            }
            case "spots-unpowered" -> {
                verdicts.add(Verdict.of("the-plant-is-gone", run.fort().place(FortAspect.POWER).wrecked(), "plant wrecked"));
                verdicts.add(Verdict.of("the-dish-stops-and-the-fort-has-only-its-eye",
                        t.rangeAtEnd == FortSensors.EYE_RANGE_CELLS && !t.seesMassAtEnd,
                        "range " + t.rangeAtEnd + " cells, the mass is " + MASS_DISTANCE + " away"));
                verdicts.add(Verdict.of("so-nothing-is-called", calls == 0 && launched == 0,
                        "calls " + calls + ", sorties " + launched));
            }
            case "spots-taken" -> {
                verdicts.add(Verdict.of("the-company-takes-the-bunker", t.flipTick > 0 && run.fort().battery().owner() == Faction.MARINE,
                        "owner " + run.fort().battery().owner() + ", flipped at tick " + t.flipTick));
                verdicts.add(Verdict.of("the-fort-no-longer-watches-for-the-garrison", !t.fortObservesForOldHolderAtEnd,
                        "observes for the defenders at the end: " + t.fortObservesForOldHolderAtEnd));
                verdicts.add(Verdict.of("it-spots-for-the-marines-only-after-the-flip",
                        t.firstCallTick > t.flipTick && t.flipTick > 0 && calls >= 1 && t.observesForMarinesAtEnd,
                        "first call at tick " + t.firstCallTick + ", flip at " + t.flipTick + ", " + calls + " calls: "
                                + t.callsOnTheMass() + " on the defenders' mass, " + t.callsOnTheGarrison()
                                + " on the garrison, the rest on ground the radar has in sight (the works' nests); the marines carry no radio"));
            }
            default -> throw new IllegalStateException(loop);
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("calls", calls);
        metrics.put("launched", launched);
        metrics.put("returned", t.count(EventType.RETURNED));
        metrics.put("lost", t.count(EventType.LOST));
        metrics.put("firstCallTick", t.firstCallTick);
        metrics.put("flipTick", t.flipTick);
        metrics.put("radarRangeCells", Math.round(t.rangeAtEnd));
        return new SceneReport(id(), loop, SPOT_TICKS, verdicts, metrics);
    }

    // ------------------------------------------------------------------------------------------------ verdicts

    private SceneReport report(String loop, Run run, Run control, Run other) {
        Trace t = run.trace();
        BattleSimulation sim = run.sim();
        Fort fort = run.fort();
        List<Verdict> verdicts = new ArrayList<>();
        AirPoolService pool = sim.airPool();
        int launched = t.count(EventType.LAUNCHED);
        int lost = t.count(EventType.LOST);
        int aborted = t.count(EventType.ABORTED);
        int returned = t.count(EventType.RETURNED);
        Trace c = control.trace();
        switch (loop) {
            case "control" -> {
                verdicts.add(Verdict.of("the-forced-strike-flies", launched >= 1, "request " + t.outcome + ", launched " + launched));
                verdicts.add(Verdict.of("no-yard-no-yard-fire", t.mountShots() == 0 && fort.airMountIds().isEmpty(),
                        t.mountShots() + " yard rounds, " + fort.airMountIds().size() + " yard mounts"));
            }
            case "aa-forced" -> {
                verdicts.add(Verdict.of("the-yard-is-assembled", fort.place(FortAspect.AIR_DEFENCE) != null
                                && fort.airMountIds().size() == 4,
                        fort.airMountIds().size() + " mounts, console " + (fort.place(FortAspect.AIR_DEFENCE) != null)));
                verdicts.add(Verdict.of("the-yard-fires-on-the-bomber", t.mountShots() > 0 && t.guidedShots() > 0,
                        t.mountShots() + " yard rounds, " + t.guidedShots() + " guided, first at tick " + t.firstMountShotTick));
                verdicts.add(Verdict.of("it-fires-from-inside-its-reach",
                        t.firstMountShotTick >= 0 && t.distanceAtFirstShot <= t.ridgeRange + 1f,
                        String.format(Locale.ROOT, "craft %.1f cells from the battery at the first round, reach %.0f",
                                t.distanceAtFirstShot, t.ridgeRange)));
                // Not a hull comparison: the fort's ordinary nests drain a lone bomber crossing the works on their own,
                // so the yard's marginal cost to it is not separable here. Its reach and the director's reading are.
                verdicts.add(Verdict.of("the-director-reads-the-yard",
                        t.directorRiskAtGarrison > c.directorRiskAtGarrison,
                        String.format(Locale.ROOT, "believed loss share %.2f against %.2f without the aspect (bomber hull low %.0f%% against %.0f%%, lost %d; "
                                        + "the nests' area drain is in both)",
                                t.directorRiskAtGarrison, c.directorRiskAtGarrison, t.minHullShare * 100f,
                                c.minHullShare * 100f, lost)));
            }
            case "declined-control" -> verdicts.add(Verdict.of("a-commander-s-request-is-accepted",
                    t.outcome.equals("CALLING") && fort.airMountIds().isEmpty(),
                    String.format(Locale.ROOT, "believed loss share %.2f over the garrison, launched %d",
                            t.directorRiskAtGarrison, launched)));
            case "declined" -> {
                verdicts.add(Verdict.of("the-fort-is-disclosed-once-seen", t.directorKnowsYard,
                        "the director knows the yard's battery: " + t.directorKnowsYard));
                verdicts.add(Verdict.of("believed-anti-air-is-too-strong",
                        t.directorRiskAtGarrison > AirStrikeDirector.RISK_CEILING,
                        String.format(Locale.ROOT, "believed loss share %.2f over a ceiling of %.2f (%.2f with no aspect)",
                                t.directorRiskAtGarrison, AirStrikeDirector.RISK_CEILING,
                                other == null ? Float.NaN : other.trace().directorRiskAtGarrison)));
                verdicts.add(Verdict.of("no-strike-is-sent",
                        t.outcome.equals("TOO_DEFENDED") && launched == 0 && pool.available(Faction.MARINE, AirPoolKind.BOMBER) >= 3,
                        "request " + t.outcome + ", launched " + launched + ", bombers in stock " + pool.available(Faction.MARINE, AirPoolKind.BOMBER)));
            }
            case "wrecked" -> {
                verdicts.add(Verdict.of("the-console-is-wrecked",
                        fort.place(FortAspect.AIR_DEFENCE).wrecked() && fort.airDefenceWrecked(),
                        "wrecked " + fort.place(FortAspect.AIR_DEFENCE).wrecked()));
                verdicts.add(Verdict.of("the-mounts-are-silent-for-good", t.mountShots() == 0,
                        t.mountShots() + " yard rounds after the console fell at tick " + EVENT_TICK));
                verdicts.add(Verdict.of("the-director-reads-an-open-sky",
                        launched >= 1 && t.directorRiskAtGarrison < other.trace().directorRiskAtGarrison,
                        String.format(Locale.ROOT, "believed loss share %.2f against %.2f with the yard standing; launched %d",
                                t.directorRiskAtGarrison, other.trace().directorRiskAtGarrison, launched)));
            }
            case "unpowered" -> {
                verdicts.add(Verdict.of("the-plant-is-gone", fort.place(FortAspect.POWER).wrecked(), "plant wrecked"));
                verdicts.add(Verdict.of("the-yard-still-fires", t.mountShots() > 0, t.mountShots() + " yard rounds"));
                float slow = t.shotsPerSecondInReach();
                float full = other.trace().shotsPerSecondInReach();
                verdicts.add(Verdict.of("at-a-quarter-of-the-rate", slow > 0f && slow < full * 0.6f,
                        String.format(Locale.ROOT, "%.2f rounds a second while the bomber was in reach, %.2f with power",
                                slow, full)));
            }
            case "taken" -> {
                verdicts.add(Verdict.of("the-company-takes-the-yard",
                        fort.place(FortAspect.AIR_DEFENCE).control().holder() == Faction.MARINE && t.flipTick > 0,
                        "holder " + fort.place(FortAspect.AIR_DEFENCE).control().holder() + ", flipped at tick " + t.flipTick));
                verdicts.add(Verdict.of("the-mounts-change-sides", t.mountsMarine == fort.airMountIds().size(),
                        t.mountsMarine + " of " + fort.airMountIds().size() + " mounts are the company's"));
                verdicts.add(Verdict.of("it-does-not-fire-on-its-own-holders-bomber", t.mountShotsAfterFlip == 0,
                        t.mountShotsAfterFlip + " yard rounds after the flip"));
                verdicts.add(Verdict.of("the-director-no-longer-counts-it",
                        launched >= 1 && t.directorRiskAtGarrison < other.trace().directorRiskAtGarrison,
                        String.format(Locale.ROOT, "believed loss share %.2f against %.2f with the garrison's yard; launched %d",
                                t.directorRiskAtGarrison, other.trace().directorRiskAtGarrison, launched)));
            }
            case "shield-fort" -> {
                verdicts.add(Verdict.of("a-shield-fort-carries-the-yard", fort.kind() == FortKind.SHIELD_FORT
                                && fort.place(FortAspect.AIR_DEFENCE) != null && fort.airMountIds().size() == 4,
                        fort.airMountIds().size() + " mounts in a " + fort.kind()));
                verdicts.add(Verdict.of("and-it-fires", t.mountShots() > 0,
                        t.mountShots() + " yard rounds, hull low " + Math.round(t.minHullShare * 100f) + "%"));
            }
            default -> throw new IllegalStateException(loop);
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("launched", launched);
        metrics.put("returned", returned);
        metrics.put("lost", lost);
        metrics.put("aborted", aborted);
        metrics.put("minHullPercent", Math.round(t.minHullShare * 100f));
        metrics.put("yardRounds", t.mountShots());
        metrics.put("guidedRounds", t.guidedShots());
        metrics.put("garrisonDead", t.garrisonDead);
        metrics.put("directorRiskPercent", Math.round(t.directorRiskAtGarrison * 100f));
        metrics.put("airframesInStock", pool.available(Faction.MARINE, AirPoolKind.BOMBER));
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    // ------------------------------------------------------------------------------------------------ trace

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final Fort fort;
        private final int[] garrisonCell;
        private final List<Event> events = new ArrayList<>();
        private int seenEvents;
        float minHullShare = 1f;
        int garrisonDead;
        int firstMountShotTick = -1;
        float distanceAtFirstShot = Float.NaN;
        float ridgeRange;
        int flipTick = -1;
        int mountsMarine;
        int mountShotsAtFlip;
        int mountShotsAfterFlip;
        String outcome = "none";
        float directorRiskAtGarrison;
        boolean directorKnowsYard;
        private int lastShots;
        private int inReachTicks;
        private int shotsInReach;

        Trace(SceneWorld world, Fort fort, int[] garrisonCell) {
            this.world = world;
            this.fort = fort;
            this.garrisonCell = garrisonCell;
        }

        int count(EventType type) {
            int n = 0;
            for (Event e : events) if (e.type() == type) n++;
            return n;
        }

        int mountShots() {
            return lastShots;
        }

        int guidedShots() {
            return guidedRounds;
        }

        /** The guided battery, found while it stands: a dead entity no longer answers what it was. */
        private long guidedMount(BattleSimulation sim) {
            if (guided == 0L) {
                for (long id : fort.airMountIds()) {
                    if (sim.world().isAlive(id) && sim.turretState().structure(id).mount.weapon.guidance != null) {
                        guided = id;
                        guidedRange = sim.turretState().structure(id).mount.weapon.range;
                    }
                }
            }
            return guided;
        }

        private long guided;
        private float guidedRange;
        private int guidedRounds;

        float shotsPerSecondInReach() {
            return inReachTicks == 0 ? 0f : shotsInReach / (inReachTicks / 30f);
        }

        String caption(String loop, int tick) {
            AirPoolService pool = world.sim().airPool();
            Fort.Place yard = fort.place(FortAspect.AIR_DEFENCE);
            String state = yard == null ? "no anti-air aspect"
                    : yard.wrecked() ? "yard WRECKED" : yard.control().contested() ? "yard CONTESTED"
                    : "yard " + yard.control().holder().name().toLowerCase(Locale.ROOT);
            return String.format(Locale.ROOT, "%s / tick %d / %s / yard rounds %d / bomber hull low %.0f%% / lost %d",
                    loop, tick, state, mountShots(), minHullShare * 100f, pool.lost(Faction.MARINE, AirPoolKind.BOMBER));
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            AirPoolService pool = sim.airPool();
            List<Event> all = pool.events();
            while (seenEvents < all.size()) events.add(all.get(seenEvents++));

            int dead = 0;
            for (long u : world.members("garrison")) if (!sim.getRoster().isAliveById(u)) dead++;
            garrisonDead = dead;

            int shots = 0;
            for (long id : fort.airMountIds()) shots += sim.telemetry().roundsFired(id);
            boolean fired = shots > lastShots;
            if (fired && firstMountShotTick < 0) {
                firstMountShotTick = tick;
                long mount = guidedMount(sim);
                for (long craft : sim.getAirEntityIds()) {
                    ShuttleMission m = sim.world().mission(craft);
                    if (m == null || !m.poolSortie) continue;
                    distanceAtFirstShot = (float) Math.hypot(sim.world().x(craft) - sim.world().x(mount),
                            sim.world().y(craft) - sim.world().y(mount));
                }
                ridgeRange = guidedRange;
            }
            Fort.Place yard = fort.place(FortAspect.AIR_DEFENCE);
            if (yard != null && flipTick < 0 && yard.control().holder() == Faction.MARINE) {
                flipTick = tick;
                mountShotsAtFlip = shots;
            }
            if (flipTick >= 0) mountShotsAfterFlip = shots - mountShotsAtFlip;
            if (guidedMount(sim) != 0L) guidedRounds = sim.telemetry().roundsFired(guided);
            lastShots = shots;
            if (yard != null) {
                int marine = 0;
                for (long id : fort.airMountIds()) if (sim.identity().faction(id) == Faction.MARINE) marine++;
                mountsMarine = marine;
            }

            boolean bomberInReach = false;
            for (long craft : sim.getAirEntityIds()) {
                ShuttleMission m = sim.world().mission(craft);
                if (m == null || !m.poolSortie || m.destroyed || sim.world().airframe(craft) == null) continue;
                minHullShare = Math.min(minHullShare, sim.world().hp(craft) / sim.world().airframe(craft).maxHp());
                if (guidedMount(sim) != 0L) {
                    long mount = guided;
                    float reach = guidedRange;
                    if (Math.hypot(sim.world().x(craft) - sim.world().x(mount),
                            sim.world().y(craft) - sim.world().y(mount)) <= reach) {
                        bomberInReach = true;
                    }
                }
            }
            if (bomberInReach) {
                inReachTicks++;
                if (fired) shotsInReach++;
            }
            if (tick >= 200) {
                directorRiskAtGarrison = AirStrikeCapability.aaRisk(sim, Faction.MARINE,
                        garrisonCell[0] + 0.5f, garrisonCell[1] + 0.5f);
                if (!fort.airMountIds().isEmpty()) {
                    directorKnowsYard = pool.director(Faction.MARINE).knows(fort.airMountIds().get(0));
                }
            }
        }
    }
}
