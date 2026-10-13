package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ConquestCommand;
import com.dillon.starsectormarines.battle.command.ConquestCommandDisclosure;
import com.dillon.starsectormarines.battle.command.ConquestTrackLayout;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.ScriptedPlayer;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.shield.ShieldDomeService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.bsp.FortStage;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.ShieldFort;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The shield fort, in its production layout, against a capital and a company. Each loop stands the same fort on the
 * same bare field (the stage places it) and the ship's guns open at {@link #SHIP_TICK}; what differs is what the
 * company did to the fort before then.
 *
 * <ul>
 *   <li>{@code intact}: nothing is touched. A marine capital shells defenders sheltering in the keep and the dome takes
 *       the rounds as flux.</li>
 *   <li>{@code power-cut}: a squad walks in through the main gate and holds the power plant. The dome has no power,
 *       so the same shelling lands.</li>
 *   <li>{@code control-taken}: a squad takes the control room and the plant, then falls back into the keep. The dome
 *       now protects the squad's side, and a defender capital's shelling is absorbed.</li>
 *   <li>{@code control-only}: the squad takes the control room and nothing else. The dome is theirs but the plant is
 *       not, so it has no power for them and the defender capital's shelling lands on the keep.</li>
 *   <li>{@code no-emitter}: the control. The generator is destroyed before the ship opens fire; the shelling lands, and
 *       holding every aspect would not bring the dome back.</li>
 * </ul>
 *
 * Every loop keeps a distant marine and defender so the clock runs, and plays with the mission-complete check off.
 */
public final class ShieldFortScene implements BehaviorScene {

    private static final int WIDTH = 150;
    private static final int HEIGHT = 100;
    private static final int TICKS = 4800;
    /** When the capital's guns open, in every loop. */
    static final int SHIP_TICK = 3300;
    private static final float SHIP_STANDOFF = 48f;
    private static final int KNOT = 10;
    /** How far out from the main gate the commander's company starts, along the way in. */
    private static final int COMMANDER_STANDOFF = 16;

    @Override public String id() { return "shield-fort"; }

    @Override public String label() {
        return "Shield fort: the dome absorbs ship fire while the fort is whole; cutting the power drops it, "
                + "the control room hands it to whoever holds it, and the emitter's loss is permanent";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("intact", "power-cut", "control-taken", "control-only", "no-emitter",
                "commander-cuts-the-power", "emitter-in-view", "emitter-in-view-unrestrained",
                "commander-wrecks-the-plant", "commander-wrecks-without-charges")) {
            reports.add(report(loop, playRestrained(loop, !loop.endsWith("-unrestrained"), frames)));
        }
        return reports;
    }

    /** Plays one loop with the fort restraint switched on or off, putting the property back as it was found. */
    private Run playRestrained(String loop, boolean restrained, FrameSink frames) throws Exception {
        String before = System.getProperty(FortRestraint.PROPERTY);
        System.setProperty(FortRestraint.PROPERTY, Boolean.toString(restrained));
        try {
            return playOne(loop, frames);
        } finally {
            if (before == null) System.clearProperty(FortRestraint.PROPERTY);
            else System.setProperty(FortRestraint.PROPERTY, before);
        }
    }

    private record Terrain(NavigationGrid grid, CellTopology topology, List<DefensePost> posts,
                           CompoundLayout.Placement at) {
        int[] cell(int lx, int ly) {
            return at.cell(lx, ly);
        }
    }

    private static Terrain stamp() {
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
                new GroundDefences(0, false).withForts(FortKind.SHIELD_FORT, 1)));
        FortStage stage = new FortStage();
        stage.run(ctx);
        return new Terrain(grid, topology, List.copyOf(ctx.defensePosts), stage.placed().get(0).placement());
    }

    private record Run(Trace trace, BattleSimulation sim, Fort fort) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        Terrain terrain = stamp();
        FortPlan plan = ShieldFort.PLAN;
        boolean commander = loop.startsWith("commander-");
        boolean wreckLoop = loop.startsWith("commander-wrecks");
        boolean viewLoop = loop.startsWith("emitter-in-view");
        boolean marineShip = loop.equals("intact") || loop.equals("power-cut") || loop.equals("no-emitter");
        boolean squadLoop = !loop.equals("intact") && !loop.equals("no-emitter");
        SceneBuilder builder = SceneBuilder.over(terrain.grid(), terrain.topology()).posts(terrain.posts())
                .missionCompletion(false);
        FortPlan.Gate main = plan.gates().get(0);
        int standoff = commander ? COMMANDER_STANDOFF : 8;
        Approach approach = commander ? approachFor(terrain, plan) : null;
        FortPlan.Gate entry = commander ? approach.gate() : main;
        int[] start = terrain.cell(entry.mouthX() + entry.outX() * standoff, entry.mouthY() + entry.outY() * standoff);
        if (squadLoop) {
            SceneBuilder.SquadSpec company = builder.squad("company").faction(Faction.MARINE).size(8)
                    .at(start[0], start[1]).primary("weapon.smg");
            if (loop.equals("commander-wrecks-the-plant")) {
                company.special(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID, 3);
            }
            company.done();
        }
        if (wreckLoop) {
            // One defender nobody can kill stands in the plant: while a hostile stands in the building it cannot be taken,
            // so the only thing a squad can still do to the plant is wreck it, and the loop is not a race between the two.
            int[] plantCore = ShieldFort.coreCentre(FortAspect.POWER);
            int[] keeper = terrain.cell(plantCore[0] + 3, plantCore[1]);
            builder.unit("plant-keeper", Faction.DEFENDER, UnitType.MARINE, keeper[0], keeper[1],
                    spec -> spec.health(1_000_000f).moveSpeed(0f));
        }
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 3, 3);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, 146, 96);
        int[] g = terrain.cell(ShieldFort.GENERATOR[0], ShieldFort.GENERATOR[1]);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261014L);
        Fort fort = sim.forts().forts().get(0);

        // The capital stands off to the side of the fort, well outside the belt, and opens fire at SHIP_TICK.
        float shipX = g[0] - SHIP_STANDOFF;
        float shipY = g[1];
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", marineShip ? Faction.MARINE : Faction.DEFENDER,
                shipX, shipY, 0f);
        ship.stationTo(shipX, shipY);

        long generator = fort.emitterId();
        Trace trace = new Trace(loop, fort, generator, squadLoop ? world.members("company") : new long[0],
                new long[marineShip ? KNOT : 0]);
        ScriptedPlayer player = ScriptedPlayer.on(world);
        int[] room = floorCell(terrain, FortAspect.CONTROL_ROOM, -2, 2);
        int[] plant = floorCell(terrain, FortAspect.POWER, 3, 0);
        int[] keep = terrain.cell(ShieldFort.GENERATOR[0] + 4, ShieldFort.GENERATOR[1] + 1);
        switch (loop) {
            case "power-cut" -> player.moveSquad(30, "company", plant[0], plant[1]);
            case "control-taken" -> {
                player.moveSquad(30, "company", room[0], room[1]);
                player.moveSquad(SHIP_TICK - 1500, "company", plant[0], plant[1]);
                player.moveSquad(SHIP_TICK - 400, "company", keep[0], keep[1]);
            }
            case "control-only" -> {
                player.moveSquad(30, "company", room[0], room[1]);
                player.moveSquad(SHIP_TICK - 1000, "company", keep[0], keep[1]);
            }
            default -> { }
        }
        if (marineShip) {
            // The garrison shelters in the keep as the capital arrives; before then a squad crossing the yard would
            // spend its time shooting at them through the keep's gaps, which is not what this scene is about.
            player.at(SHIP_TICK - 1, s -> {
                for (int i = 0; i < KNOT; i++) {
                    int[] cell = terrain.cell(ShieldFort.GENERATOR[0] + 3 + i % 2, ShieldFort.GENERATOR[1] - 2 + i / 2);
                    trace.knot[i] = s.spawn(new EntitySpec("k" + i, Faction.DEFENDER, UnitType.MARINE, cell[0], cell[1]));
                }
            });
        }
        if (commander) {
            // No scripted order and no ship: the company's commander is told only of what the company has seen. The
            // company comes in on the lane that runs up to the main gate, because a lane that never comes within sight
            // of the generator discloses nothing, and a commander cannot be sent to what nobody has seen.
            sim.setAutonomousCommander(Faction.MARINE,
                    new ConquestCommand(new ConquestTrackLayout(approach.axis(), WIDTH, HEIGHT)),
                    ConquestCommandDisclosure.INSTANCE);
            trace.squadId = world.squadId("company");
        } else if (!viewLoop) {
            player.at(SHIP_TICK, s -> {
                s.addWarship(ship);
                ship.direct(8f, true, 1);
            });
        }
        if (viewLoop) {
            int[] view = viewCell(terrain, sim, fort, start);
            trace.viewCell = view;
            player.moveSquad(30, "company", view[0], view[1]);
        }

        TickObserver script = (s, tick) -> {
            if (tick == 0 && trace.squad.length > 0) {
                // The garrison's nests are shot out beforehand: this is about the aspects, not the siege of the wall, and
                // a squad that stops to trade fire with a tower for an hour never reaches a building.
                List<Long> nests = new ArrayList<>();
                for (int i = 0, n = s.liveUnitCount(); i < n; i++) {
                    long unit = s.liveUnitAt(i);
                    if (s.identity().type(unit) != UnitType.TURRET || s.identity().faction(unit) != Faction.DEFENDER) continue;
                    boolean core = unit == generator;
                    for (Fort.Place place : fort.places()) core |= place.coreId() == unit;
                    if (!core) nests.add(unit);
                }
                for (long id : nests) s.applyDamage(id, 1e7f, 100f);
                for (long id : trace.squad) s.world().setHp(id, 5000f);
            }
            if (loop.equals("no-emitter") && tick == 60) s.applyDamage(generator, 1_000_000f, 100f);
            if (tick == 1) trace.generatorHpStart = s.world().hp(generator);
        };
        SceneRun.of(world).player(player).observe(script, trace)
                .frames(frames, loop, 90, tick -> String.format(Locale.ROOT, "%s / tick %d / %s", loop, tick,
                        trace.caption(sim)))
                .run(TICKS);
        return new Run(trace, sim, fort);
    }

    /** The gate a lane reaches the fort by, and the lane axis that runs up to it. */
    private record Approach(FortPlan.Gate gate, TraversalAxis axis) { }

    /**
     * The lane the company comes in on: the ordinary one, to the main gate, when its way in runs along a lane axis (south
     * to north, or west to east) in the map as it was stamped, and otherwise the gate nearest the power plant that does.
     * The company used to be brought in by the plant's own postern, away from the keep, because a squad that saw the
     * emitter shot it to pieces and left the commander nothing to choose; a fort's cores are no longer opportunity
     * targets, so the lane is free to pass the keep.
     */
    private static Approach approachFor(Terrain terrain, FortPlan plan) {
        int[] plant = ShieldFort.coreCentre(FortAspect.POWER);
        Approach best = null;
        double bestGap = Double.MAX_VALUE;
        for (FortPlan.Gate gate : plan.gates()) {
            int[] at = terrain.cell(gate.mouthX(), gate.mouthY());
            int[] out = terrain.cell(gate.mouthX() + gate.outX(), gate.mouthY() + gate.outY());
            int dx = out[0] - at[0];
            int dy = out[1] - at[1];
            TraversalAxis axis;
            if (Math.abs(dy) >= Math.abs(dx) && dy < 0) axis = TraversalAxis.SOUTH_TO_NORTH;
            else if (Math.abs(dx) > Math.abs(dy) && dx < 0) axis = TraversalAxis.WEST_TO_EAST;
            else continue;
            double gap = gate == plan.gates().get(0) ? -1 : Math.hypot(gate.mouthX() - plant[0], gate.mouthY() - plant[1]);
            if (gap < bestGap) {
                bestGap = gap;
                best = new Approach(gate, axis);
            }
        }
        if (best == null) throw new IllegalStateException("no gate of the shield fort faces a lane");
        return best;
    }

    /**
     * Where a squad stands in plain view of the emitter without an order that names it: a reachable yard cell with a
     * clear line to the generator, inside weapon reach of it and more than {@link FortRestraint#LIFT_REACH_CELLS} cells
     * from every fort core, because an order that ends beside a core is a player pointing at it, which lifts the
     * restraint. The nearest such cell to the squad's start is taken.
     */
    private static int[] viewCell(Terrain terrain, BattleSimulation sim, Fort fort, int[] start) {
        float gx = sim.world().x(fort.emitterId());
        float gy = sim.world().y(fort.emitterId());
        int[] best = null;
        long bestGap = Long.MAX_VALUE;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (!terrain.grid().isWalkable(x, y)) continue;
                double toEmitter = Math.hypot(x + 0.5f - gx, y + 0.5f - gy);
                if (toEmitter > 12.0 || !terrain.grid().hasLineOfSight(x, y, (int) gx, (int) gy)) continue;
                boolean clear = true;
                for (Fort.Place place : fort.places()) {
                    if (Math.max(Math.abs(x - (int) place.x()), Math.abs(y - (int) place.y()))
                            <= FortRestraint.LIFT_REACH_CELLS) clear = false;
                }
                if (!clear) continue;
                long gap = (long) (x - start[0]) * (x - start[0]) + (long) (y - start[1]) * (y - start[1]);
                if (gap >= bestGap) continue;
                if (GridPathfinder.findPath(terrain.grid(), start[0], start[1], x, y, sim.getOccupancyMap()).length == 0) continue;
                bestGap = gap;
                best = new int[] {x, y};
            }
        }
        if (best == null) throw new IllegalStateException("no yard cell sees the emitter");
        return best;
    }

    /** A cell on the floor of an aspect's building, offset from its core. */
    private static int[] floorCell(Terrain terrain, FortAspect aspect, int dx, int dy) {
        int[] core = ShieldFort.coreCentre(aspect);
        return terrain.cell(core[0] + dx, core[1] + dy);
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        Fort fort = run.fort();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "intact" -> {
                verdicts.add(Verdict.of("dome-takes-the-rounds", t.hitsAfterShip > 0 && t.firstHit >= SHIP_TICK,
                        t.hitsAfterShip + " rounds absorbed, the first at tick " + t.firstHit));
                verdicts.add(Verdict.of("nobody-dies-before-the-overload",
                        t.firstOverload >= 0 && (t.firstKnotDeath < 0 || t.firstKnotDeath >= t.firstOverload),
                        "first overload tick " + t.firstOverload + ", first death tick " + t.firstKnotDeath));
                verdicts.add(Verdict.of("dome-never-lost-power-or-holder", t.maxUnpoweredTicks == 0
                        && t.holderAtEnd == Faction.DEFENDER, "unpowered ticks " + t.maxUnpoweredTicks));
            }
            case "power-cut" -> {
                verdicts.add(Verdict.of("squad-holds-the-plant",
                        fort.place(FortAspect.POWER).control().holder() == Faction.MARINE && t.powerTakenTick > 0,
                        "plant taken at tick " + t.powerTakenTick));
                verdicts.add(Verdict.of("dome-is-down-before-the-guns-open",
                        t.unpoweredAtShip && t.powerTakenTick < SHIP_TICK,
                        "unpowered at the first round: " + t.unpoweredAtShip));
                verdicts.add(Verdict.of("rounds-land-and-none-are-absorbed",
                        t.hitsAfterShip == 0 && t.knotDeaths > 0,
                        t.hitsAfterShip + " absorbed, " + t.knotDeaths + " defenders killed, the first at tick "
                                + t.firstKnotDeath));
            }
            case "control-taken" -> {
                verdicts.add(Verdict.of("dome-changes-sides",
                        fort.place(FortAspect.CONTROL_ROOM).control().holder() == Faction.MARINE
                                && t.holderAtEnd == Faction.MARINE,
                        "control room taken at tick " + t.controlTakenTick + ", dome holder " + t.holderAtEnd));
                verdicts.add(Verdict.of("and-has-power-once-the-plant-is-theirs",
                        t.powerTakenTick > 0 && !t.unpoweredAtShip,
                        "plant taken at tick " + t.powerTakenTick + ", unpowered at the first round " + t.unpoweredAtShip));
                verdicts.add(Verdict.of("dome-protects-the-squad", t.hitsAfterShip > 0 && t.squadHpLostBeforeOverload < 1f,
                        t.hitsAfterShip + " rounds absorbed, squad lost " + t.squadHpLostBeforeOverload
                                + " hit points before the overload at tick " + t.firstOverload));
            }
            case "control-only" -> {
                verdicts.add(Verdict.of("dome-is-theirs-but-powerless",
                        t.holderAtEnd == Faction.MARINE && t.unpoweredAtShip
                                && fort.place(FortAspect.POWER).control().holder() == Faction.DEFENDER,
                        "holder " + t.holderAtEnd + ", unpowered at the first round " + t.unpoweredAtShip));
                verdicts.add(Verdict.of("rounds-land-on-the-squad", t.hitsAfterShip == 0 && t.squadHpLost > 1f,
                        t.hitsAfterShip + " absorbed, squad lost " + t.squadHpLost + " hit points"));
            }
            case "commander-cuts-the-power" -> {
                verdicts.add(Verdict.of("commander-orders-the-plant-first",
                        t.firstOrderAspect == FortAspect.POWER, "orders " + t.orders));
                verdicts.add(Verdict.of("ordered-within-two-pulses-of-sighting",
                        t.firstSeenTick >= 0 && t.firstOrderTick >= t.firstSeenTick
                                && t.firstOrderTick - t.firstSeenTick <= 150,
                        "generator first in view at tick " + t.firstSeenTick + ", ordered at tick " + t.firstOrderTick));
                verdicts.add(Verdict.of("company-holds-the-plant-unscripted",
                        t.powerTakenTick > 0 && fort.place(FortAspect.POWER).control().holder() == Faction.MARINE,
                        "plant taken at tick " + t.powerTakenTick));
                // The lane walks the company through the control room on its way in, so the room may fall before the
                // plant is ordered; what matters is that the commander's order finishes the set and the dome is theirs.
                verdicts.add(Verdict.of("ends-owning-a-powered-dome",
                        t.powerTakenTick > 0 && t.controlTakenTick > 0 && t.holderAtEnd == Faction.MARINE && !t.unpoweredNow,
                        "orders " + t.orders + ", control room taken at tick " + t.controlTakenTick + ", plant at tick "
                                + t.powerTakenTick + ", dome holder " + t.holderAtEnd + ", unpowered " + t.unpoweredNow));
            }
            case "emitter-in-view" -> {
                verdicts.add(Verdict.of("squad-has-the-emitter-in-view", t.viewTicks > 300,
                        "a member had the emitter in sight for " + t.viewTicks + " ticks, standing at "
                                + Arrays.toString(t.viewCell)));
                verdicts.add(Verdict.of("emitter-is-not-shot", t.generatorHpEnd >= t.generatorHpStart - 0.5f
                                && !fort.place(FortAspect.EMITTER).wrecked(),
                        "emitter hit points " + t.generatorHpStart + " -> " + t.generatorHpEnd));
            }
            case "emitter-in-view-unrestrained" -> {
                verdicts.add(Verdict.of("control-squad-has-it-in-view", t.viewTicks > 300,
                        "a member had the emitter in sight for " + t.viewTicks + " ticks"));
                verdicts.add(Verdict.of("control-shoots-the-emitter", t.generatorHpEnd < t.generatorHpStart - 50f,
                        "emitter hit points " + t.generatorHpStart + " -> " + t.generatorHpEnd));
            }
            case "commander-wrecks-the-plant" -> {
                verdicts.add(Verdict.of("commander-reads-the-satchels-and-says-wreck",
                        t.wreckOrderedAspect == FortAspect.POWER,
                        "wreck order for " + t.wreckOrderedAspect + ", orders " + t.orders));
                verdicts.add(Verdict.of("plant-is-wrecked", fort.place(FortAspect.POWER).wrecked(),
                        "plant wrecked: " + fort.place(FortAspect.POWER).wrecked()));
                verdicts.add(Verdict.of("emitter-is-left-standing", !fort.place(FortAspect.EMITTER).wrecked()
                                && t.generatorHpEnd >= t.generatorHpStart - 0.5f,
                        "emitter hit points " + t.generatorHpStart + " -> " + t.generatorHpEnd));
            }
            case "commander-wrecks-without-charges" -> {
                verdicts.add(Verdict.of("no-charges-no-wreck-order",
                        t.wreckOrderedAspect == null && t.firstOrderAspect == FortAspect.POWER,
                        "wreck order for " + t.wreckOrderedAspect + ", orders " + t.orders));
                verdicts.add(Verdict.of("plant-stands-for-want-of-leave-to-shoot",
                        !fort.place(FortAspect.POWER).wrecked()
                                && fort.place(FortAspect.POWER).control().holder() == Faction.DEFENDER,
                        "plant wrecked: " + fort.place(FortAspect.POWER).wrecked()));
            }
            default -> {
                verdicts.add(Verdict.of("emitter-destroyed-for-good", fort.place(FortAspect.EMITTER).wrecked()
                        && run.sim().shieldDomes().domes().isEmpty(), "domes left " + run.sim().shieldDomes().domes().size()));
                verdicts.add(Verdict.of("rounds-land-unopposed", t.hitsAfterShip == 0 && t.knotDeaths > 0
                        && t.firstKnotDeath >= SHIP_TICK, t.knotDeaths + " defenders killed, the first at tick " + t.firstKnotDeath));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("hitsAfterShip", t.hitsAfterShip);
        metrics.put("firstOverload", t.firstOverload);
        metrics.put("knotDeaths", t.knotDeaths);
        metrics.put("firstKnotDeath", t.firstKnotDeath);
        metrics.put("powerTakenTick", t.powerTakenTick);
        metrics.put("controlTakenTick", t.controlTakenTick);
        metrics.put("squadHpLost", Math.round(t.squadHpLost));
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final String loop;
        private final Fort fort;
        private final long generator;
        final long[] squad;
        final long[] knot;
        int squadId = -1;
        int firstSeenTick = -1;
        int firstOrderTick = -1;
        FortAspect firstOrderAspect;
        final List<String> orders = new ArrayList<>();
        int hitsAfterShip;
        int[] viewCell;
        int viewTicks;
        float generatorHpStart = -1f;
        float generatorHpEnd = -1f;
        FortAspect wreckOrderedAspect;
        int firstHit = -1;
        int firstOverload = -1;
        int knotDeaths;
        int firstKnotDeath = -1;
        int powerTakenTick = -1;
        int controlTakenTick = -1;
        int maxUnpoweredTicks;
        boolean unpoweredAtShip;
        boolean unpoweredNow;
        float squadHpLost;
        float squadHpLostBeforeOverload;
        Faction holderAtEnd = Faction.DEFENDER;
        private int hitsBefore = -1;
        private float squadHpStart = -1f;
        private final boolean[] dead;

        Trace(String loop, Fort fort, long generator, long[] squad, long[] knot) {
            this.loop = loop;
            this.fort = fort;
            this.generator = generator;
            this.squad = squad;
            this.knot = knot;
            this.dead = new boolean[knot.length];
        }

        String caption(BattleSimulation sim) {
            for (ShieldDomeService.DomeView d : sim.shieldDomes().domes()) {
                if (d.entityId() != generator) continue;
                return String.format(Locale.ROOT, "dome %s for %s %.0f%%", d.overloaded() ? "DOWN" : "up", d.holder(),
                        d.level() * 100f);
            }
            return "no dome";
        }

        private float squadHp(BattleSimulation sim) {
            float hp = 0f;
            for (long id : squad) if (sim.world().isAlive(id)) hp += sim.world().hp(id);
            return hp;
        }

        /** The aspect whose building an order's destination is nearest to. */
        private FortAspect nearestAspect(int x, int y) {
            FortAspect best = null;
            double bestGap = Double.MAX_VALUE;
            for (Fort.Place place : fort.places()) {
                double gap = Math.hypot(place.x() - x, place.y() - y);
                if (gap < bestGap) {
                    bestGap = gap;
                    best = place.aspect();
                }
            }
            return best;
        }

        private void observeCommander(BattleSimulation sim, int tick) {
            if (firstSeenTick < 0 && sim.playerSeesCell((int) sim.world().x(generator), (int) sim.world().y(generator))) {
                firstSeenTick = tick;
            }
            CommanderSnapshot<?> snapshot = sim.getCommanderSnapshot(Faction.MARINE);
            CommandDirective directive = snapshot == null ? null : snapshot.directiveFor(squadId);
            ObjectiveAssignment order = sim.getSquad(squadId).assignedObjective;
            if (directive == null || order == null || order.targetCellX() < 0
                    || !directive.reason().startsWith("DEFENCE_STRUCTURE_")) {
                return;
            }
            FortAspect aspect = nearestAspect(order.targetCellX(), order.targetCellY());
            if (order.wreckAuthorised() && wreckOrderedAspect == null) wreckOrderedAspect = aspect;
            String line = directive.reason() + "->" + aspect;
            if (orders.isEmpty() || !orders.get(orders.size() - 1).endsWith(line)) orders.add("t" + tick + " " + line);
            if (firstOrderTick < 0) {
                firstOrderTick = tick;
                firstOrderAspect = aspect;
            }
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (tick == SHIP_TICK) squadHpStart = squadHp(sim);
            Fort.Place plant = fort.place(FortAspect.POWER);
            Fort.Place room = fort.place(FortAspect.CONTROL_ROOM);
            if (powerTakenTick < 0 && plant.control().holder() == Faction.MARINE) powerTakenTick = tick;
            if (controlTakenTick < 0 && room.control().holder() == Faction.MARINE) controlTakenTick = tick;
            ShieldDomeService.DomeView dome = null;
            for (ShieldDomeService.DomeView d : sim.shieldDomes().domes()) if (d.entityId() == generator) dome = d;
            if (dome != null) {
                holderAtEnd = dome.holder();
                unpoweredNow = sim.shieldDomes().isUnpowered(generator);
                if (tick == SHIP_TICK) unpoweredAtShip = sim.shieldDomes().isUnpowered(generator);
                if (sim.shieldDomes().isUnpowered(generator) && tick < SHIP_TICK) maxUnpoweredTicks++;
                if (tick >= SHIP_TICK) {
                    if (hitsBefore < 0) hitsBefore = dome.hits();
                    hitsAfterShip = dome.hits() - hitsBefore;
                    if (firstHit < 0 && hitsAfterShip > 0) firstHit = tick;
                    if (firstOverload < 0 && dome.overloaded() && !sim.shieldDomes().isUnpowered(generator)
                            && dome.overloads() > 0) {
                        firstOverload = tick;
                    }
                }
            }
            if (squadId >= 0) observeCommander(sim, tick);
            if (sim.getRoster().isAliveById(generator)) generatorHpEnd = sim.world().hp(generator);
            if (loop.startsWith("emitter-in-view") && tick > 30) {
                for (long member : squad) {
                    if (sim.getRoster().isAliveById(member) && sim.getGrid().hasLineOfSight(
                            sim.world().cellX(member), sim.world().cellY(member),
                            sim.world().cellX(generator), sim.world().cellY(generator))) {
                        viewTicks++;
                        break;
                    }
                }
            }
            for (int i = 0; i < knot.length; i++) {
                if (dead[i] || knot[i] == 0L || sim.getRoster().isAliveById(knot[i])) continue;
                dead[i] = true;
                knotDeaths++;
                if (firstKnotDeath < 0) firstKnotDeath = tick;
            }
            if (squad.length > 0 && squadHpStart >= 0f && tick >= SHIP_TICK) {
                squadHpLost = squadHpStart - squadHp(sim);
                if (firstOverload < 0) squadHpLostBeforeOverload = squadHpLost;
            }
        }
    }
}
