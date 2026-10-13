package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.command.ConquestCommand;
import com.dillon.starsectormarines.battle.command.ConquestCommandDisclosure;
import com.dillon.starsectormarines.battle.command.ConquestTrackLayout;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.ScriptedPlayer;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.AirFireOrder;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.FortStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The battery fort, in its production layout, against a capital and a few marines. Each loop stands the same fort on
 * the same bare field (the stage places it) with a capital hanging inside the guns' envelope, and changes one thing.
 *
 * <ul>
 *   <li>{@code guns-fire}: nothing is touched. The guns settle, fire and hurt the hull.</li>
 *   <li>{@code gun-side-hold}: marines stand beside the platform, where the old fire-control ring would have been.
 *       Fire control is in the bunker now, so nothing is contested and the guns do not slow.</li>
 *   <li>{@code bunker-taken}: marines stand inside the fire-control bunker. The guns fall silent while it is
 *       contested, the battery changes hands, and it fires on nothing of theirs.</li>
 *   <li>{@code bunker-wrecked}: the bunker's console is destroyed. The guns have nothing to obey, for good.</li>
 *   <li>{@code magazine-wrecked}: the magazine is destroyed. It goes up, the guns fire out their ready rounds and
 *       then stop.</li>
 *   <li>{@code power-cut}: the power plant is destroyed. The guns still fire, at a fraction of the rate.</li>
 *   <li>{@code squad-takes-bunker}: no ship and no scripted holders. A scripted move order sends a
 *       squad to the bunker; it goes in through a gate and takes it the way a real assault would.</li>
 *   <li>{@code fort-taken-turns}: the company takes the bunker, the magazine and the power building, and then a
 *       defender capital comes into reach. The fort fires on it at the full rate, unrationed: captured aspects work
 *       for their new holder.</li>
 * </ul>
 *
 * Every loop keeps a distant marine and defender so the clock runs, and plays with the mission-complete check off.
 */
public final class BatteryFortScene implements BehaviorScene {

    private static final int OX = BatteryFort.OX;
    private static final int OY = BatteryFort.OY;

    private static final int WIDTH = 150;
    private static final int HEIGHT = 100;
    private static final int TICKS = 3600;
    private static final int EVENT_TICK = 300;
    private static final int LATE_SHIP_TICK = 2400;
    private static final int SECONDS_TICKS = 30;
    /** How far out from the main gate the commander's company starts, along the way in. */
    private static final int COMMANDER_STANDOFF = 16;

    @Override public String id() { return "battery-fort"; }

    @Override public String label() {
        return "Battery fort: fire control is held in its bunker, the magazine rations the guns and the power plant "
                + "drives them, and each can be taken or wrecked on its own";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("guns-fire", "gun-side-hold", "bunker-taken", "bunker-wrecked", "magazine-wrecked",
                "power-cut", "fort-taken-turns", "squad-takes-bunker", "commander-takes-bunker")) {
            reports.add(report(loop, playOne(loop, frames)));
        }
        return reports;
    }

    private record Run(Trace trace, Warship ship, BattleSimulation sim, Fort fort, FortSceneWorld layout) {}

    /** The stamped terrain and where the fort stands in it. */
    private record FortSceneWorld(NavigationGrid grid, CellTopology topology,
                                  List<com.dillon.starsectormarines.battle.turret.DefensePost> posts,
                                  com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Placement at) {
        int[] cell(int lx, int ly) {
            return at.cell(lx, ly);
        }
    }

    private static FortSceneWorld stamp() {
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
        return new FortSceneWorld(grid, topology, List.copyOf(ctx.defensePosts), stage.placed().get(0).placement());
    }

    private Run playOne(String loop, FrameSink frames) throws Exception {
        FortSceneWorld terrain = stamp();
        boolean commander = loop.equals("commander-takes-bunker");
        boolean assault = loop.equals("squad-takes-bunker") || commander;
        SceneBuilder builder = SceneBuilder.over(terrain.grid(), terrain.topology()).posts(terrain.posts())
                .missionCompletion(false);
        int[] start = terrain.cell(BatteryFort.OX - 8, BatteryFort.OY + 17);
        if (commander) {
            // The company comes in on the lane that runs up to the main gate: a commander is told only of what the
            // company has seen, and a lane that never comes within sight of the platform discloses nothing.
            FortPlan.Gate gate = FortPlan.of(FortKind.BATTERY_FORT).gates().get(0);
            start = terrain.cell(gate.mouthX() + gate.outX() * COMMANDER_STANDOFF, gate.mouthY() + gate.outY() * COMMANDER_STANDOFF);
        }
        if (assault) builder.squad("company").faction(Faction.MARINE).size(10).at(start[0], start[1]).primary("weapon.smg").done();
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 5, 95);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, 145, 5);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261014L);
        Fort fort = sim.forts().forts().get(0);
        FortressBattery battery = fort.battery();
        // The capital hangs inside the guns' envelope on the gate side of the fort, held where it was put.
        float shipX = battery.x() + 38f;
        float shipY = battery.y();
        // In the assault loop the capital is the garrison's own, so nothing is fired at and the loop is about the squad.
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", assault ? Faction.DEFENDER : Faction.MARINE,
                shipX, shipY, 90f);
        ship.stationTo(shipX, shipY);
        ship.limitFlux();
        ship.airFire(AirFireOrder.OFF);
        ship.shieldAuto(false);
        ship.raiseShield(false);
        sim.addWarship(ship);
        Warship late = null;
        if (loop.equals("fort-taken-turns")) {
            late = Warship.fit("onslaught", "onslaught_Standard", Faction.DEFENDER,
                    battery.x() + 38f, battery.y() + 6f, 90f);
            late.stationTo(battery.x() + 38f, battery.y() + 6f);
            late.limitFlux();
            late.airFire(AirFireOrder.OFF);
            late.shieldAuto(false);
            late.raiseShield(false);
        }
        ScriptedPlayer player = ScriptedPlayer.on(world);
        if (commander) {
            // No scripted order: the company's commander is told only what the company has seen, and must choose.
            sim.setAutonomousCommander(Faction.MARINE,
                    new ConquestCommand(new ConquestTrackLayout(TraversalAxis.SOUTH_TO_NORTH, WIDTH, HEIGHT)),
                    ConquestCommandDisclosure.INSTANCE);
        } else if (assault) {
            int[] core = BatteryFort.coreCentre(FortAspect.FIRE_CONTROL);
            int[] target = terrain.cell(core[0] - 2, core[1]);
            player.moveSquad(30, "company", target[0], target[1]);
        }
        Trace trace = new Trace(loop, ship, late, terrain, fort);
        trace.squadMembers = assault ? world.members("company") : new long[0];
        trace.squadId = assault ? world.squadId("company") : -1;
        SceneRun.of(world).player(player).observe(trace)
                .frames(frames, loop, 90, tick -> String.format(Locale.ROOT,
                        "%s / tick %d / hull %.0f%% / rounds %d / pool %.0f / %s", loop, tick,
                        100f * ship.hullFraction(), trace.shots(), fort.readyRounds(), trace.state()))
                .run(TICKS);
        return new Run(trace, ship, sim, fort, terrain);
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        Warship ship = run.ship();
        Fort fort = run.fort();
        FortressBattery battery = fort.battery();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "guns-fire" -> {
                verdicts.add(Verdict.of("guns-fire-and-hurt", t.shots() > 5 && ship.hullFraction() < 0.9f,
                        String.format(Locale.ROOT, "%d rounds, hull %.3f", t.shots(), ship.hullFraction())));
                verdicts.add(Verdict.of("nothing-is-rationed-or-slowed", fort.magazineSupplies(battery.owner())
                        && fort.powered(battery.owner()) && fort.readyRounds() == Fort.READY_ROUNDS,
                        "pool " + fort.readyRounds()));
                verdicts.add(Verdict.of("fort-is-assembled", fort.place(FortAspect.FIRE_CONTROL) != null
                        && fort.place(FortAspect.MAGAZINE) != null && fort.place(FortAspect.POWER) != null
                        && fort.place(FortAspect.FIRE_CONTROL).interiorCells() > 20,
                        "bunker interior cells " + fort.place(FortAspect.FIRE_CONTROL).interiorCells()));
                // What a commander is told: fire control is the bunker, not the ground round the guns.
                var facts = run.sim().defenceStructures();
                long core = fort.place(FortAspect.FIRE_CONTROL).coreId();
                var guns = facts.stream().filter(f -> f.kind() == DefenceStructureFact.Kind.FIRE_CONTROL).toList();
                boolean atBunker = guns.size() == 1 && guns.get(0).cellX() == (int) Math.floor(run.sim().world().x(core))
                        && guns.get(0).cellY() == (int) Math.floor(run.sim().world().y(core));
                verdicts.add(Verdict.of("commanders-are-sent-to-the-bunker", atBunker,
                        guns.size() + " fire-control fact(s) of " + facts.size() + ", bunker core at "
                                + run.sim().world().x(core) + "," + run.sim().world().y(core)));
                // And are told of the fort's other two aspects, so they can choose between the three.
                verdicts.add(Verdict.of("and-of-its-magazine-and-power",
                        facts.stream().anyMatch(f -> f.kind() == DefenceStructureFact.Kind.FORT_MAGAZINE)
                                && facts.stream().anyMatch(f -> f.kind() == DefenceStructureFact.Kind.FORT_POWER)
                                && facts.stream().allMatch(f -> f.fortId() == fort.id()),
                        facts.size() + " facts of fort " + fort.id()));
            }
            case "gun-side-hold" -> {
                verdicts.add(Verdict.of("never-contested-from-the-platform", t.contestedTicks == 0
                        && battery.owner() == Faction.DEFENDER,
                        t.contestedTicks + " contested ticks, holder " + battery.owner()));
                verdicts.add(Verdict.of("guns-keep-firing", t.shots() > 5 && ship.hullFraction() < 0.9f,
                        String.format(Locale.ROOT, "%d rounds, hull %.3f", t.shots(), ship.hullFraction())));
            }
            case "bunker-taken" -> {
                verdicts.add(Verdict.of("contest-silences-it", t.contestedTicks > 0 && t.shotsWhileContested == 0,
                        t.contestedTicks + " contested ticks, " + t.shotsWhileContested + " rounds fired in them"));
                verdicts.add(Verdict.of("changes-hands", t.flipTick > EVENT_TICK && battery.owner() == Faction.MARINE,
                        "holder at the end " + battery.owner() + ", flipped at tick " + t.flipTick));
                verdicts.add(Verdict.of("silent-towards-its-new-friends", t.shots() == t.shotsAtFlip,
                        "rounds " + t.shotsAtFlip + " at the flip, " + t.shots() + " at the end"));
                verdicts.add(Verdict.of("power-and-magazine-stay-the-garrison-s",
                        fort.place(FortAspect.POWER).control().holder() == Faction.DEFENDER
                                && fort.place(FortAspect.MAGAZINE).control().holder() == Faction.DEFENDER,
                        "taking the bunker did not take the other aspects"));
            }
            case "bunker-wrecked" -> {
                verdicts.add(Verdict.of("guns-stop-for-good", t.shotsAfterEvent <= 3 && t.lastShotTick < EVENT_TICK + 120,
                        t.shotsAfterEvent + " rounds after the console fell, last at tick " + t.lastShotTick));
                verdicts.add(Verdict.of("envelope-withdrawn", !battery.operational() && !t.hazardAtEnd,
                        "hazard registered at the end: " + t.hazardAtEnd));
                verdicts.add(Verdict.of("nothing-left-to-take", run.sim().defenceStructures().isEmpty(),
                        run.sim().defenceStructures().size() + " structure fact(s) after the console fell"));
            }
            case "magazine-wrecked" -> {
                verdicts.add(Verdict.of("it-goes-up", fort.magazineBlown() && t.blastTick >= EVENT_TICK && t.blastTick <= EVENT_TICK + 2,
                        "blast at tick " + t.blastTick + ", magazine building wall hp " + t.magazineWallHpAfter
                                + " against " + t.magazineWallHpBefore));
                verdicts.add(Verdict.of("guns-fire-out-the-ready-rounds",
                        t.shotsAfterEvent >= 1 && t.shotsAfterEvent <= (int) Fort.READY_ROUNDS,
                        t.shotsAfterEvent + " rounds after the magazine fell, the pool held " + (int) Fort.READY_ROUNDS));
                verdicts.add(Verdict.of("then-they-stop", t.lastShotTick < TICKS - 600 && fort.readyRounds() < 1f
                        && !battery.operational(),
                        "last round at tick " + t.lastShotTick + ", pool " + fort.readyRounds()));
                verdicts.add(Verdict.of("the-sky-opens", !t.hazardAtEnd, "hazard registered at the end: " + t.hazardAtEnd));
            }
            case "power-cut" -> {
                float before = t.rate(60, EVENT_TICK);
                float after = t.rate(EVENT_TICK + 90, TICKS);
                verdicts.add(Verdict.of("still-firing", t.shotsAfterEvent > 0 && battery.operational(),
                        t.shotsAfterEvent + " rounds after the plant fell"));
                verdicts.add(Verdict.of("slowed", after > 0f && after < before * 0.6f,
                        String.format(Locale.ROOT, "%.2f rounds a second before, %.2f after", before, after)));
            }
            case "squad-takes-bunker" -> {
                verdicts.add(Verdict.of("squad-takes-the-fire-control-bunker", t.flipTick > 0 && battery.owner() == Faction.MARINE,
                        "holder at the end " + battery.owner() + ", flipped at tick " + t.flipTick));
                verdicts.add(Verdict.of("the-squad-stood-in-the-bunker", t.maxInBunker > 0,
                        t.maxInBunker + " of the squad inside the bunker at most; " + t.alive + " alive at the end, nearest approach " + t.nearestToBunker + " cells, order " + t.lastOrder));
                verdicts.add(Verdict.of("it-came-in-by-a-gate-or-a-breach", t.contestedTicks > 0,
                        t.contestedTicks + " ticks of contest"));
            }
            case "commander-takes-bunker" -> {
                verdicts.add(Verdict.of("commander-orders-the-bunker-first",
                        t.firstOrderAspect == FortAspect.FIRE_CONTROL,
                        "first order " + t.firstOrderReason + " at tick " + t.firstOrderTick + ", nearest aspect "
                                + t.firstOrderAspect));
                verdicts.add(Verdict.of("ordered-within-two-pulses-of-sighting",
                        t.firstSeenTick >= 0 && t.firstOrderTick >= t.firstSeenTick
                                && t.firstOrderTick - t.firstSeenTick <= 150,
                        "platform first in view at tick " + t.firstSeenTick + ", ordered at tick " + t.firstOrderTick));
                verdicts.add(Verdict.of("company-takes-the-bunker-unscripted",
                        t.flipTick > 0 && battery.owner() == Faction.MARINE && t.maxInBunker > 0,
                        "flipped at tick " + t.flipTick + ", holder " + battery.owner()));
                // With the guns its own, the next work is making them shoot properly: the plant, then the magazine, never
                // the bunker again and never a building the company already holds.
                verdicts.add(Verdict.of("then-goes-on-to-what-the-guns-lack",
                        t.laterOrderTick > t.flipTick && t.flipTick > 0
                                && (t.laterOrderAspect == FortAspect.POWER || t.laterOrderAspect == FortAspect.MAGAZINE),
                        "orders " + t.orders + ", plant holder " + fort.place(FortAspect.POWER).control().holder()
                                + ", magazine holder " + fort.place(FortAspect.MAGAZINE).control().holder()));
            }
            default -> {
                verdicts.add(Verdict.of("company-holds-every-aspect",
                        fort.place(FortAspect.POWER).control().holder() == Faction.MARINE
                                && fort.place(FortAspect.MAGAZINE).control().holder() == Faction.MARINE
                                && battery.owner() == Faction.MARINE,
                        "holders: bunker " + battery.owner() + ", magazine "
                                + fort.place(FortAspect.MAGAZINE).control().holder() + ", power "
                                + fort.place(FortAspect.POWER).control().holder()));
                verdicts.add(Verdict.of("captured-fort-fires-on-the-defenders-ship",
                        t.shotsAtDefenderShip > 3 && t.defenderHull < 0.95f,
                        String.format(Locale.ROOT, "%d rounds at the defender ship, hull %.3f", t.shotsAtDefenderShip,
                                t.defenderHull)));
                verdicts.add(Verdict.of("unrationed-and-powered", fort.magazineSupplies(Faction.MARINE)
                        && fort.powered(Faction.MARINE) && fort.readyRounds() == Fort.READY_ROUNDS,
                        "pool " + fort.readyRounds()));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("hullFraction", Math.round(ship.hullFraction() * 10000f) / 10000f);
        metrics.put("rounds", t.shots());
        metrics.put("shotsAfterEvent", t.shotsAfterEvent);
        metrics.put("lastShotTick", t.lastShotTick);
        metrics.put("contestedTicks", t.contestedTicks);
        metrics.put("flipTick", t.flipTick);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final String loop;
        private final Warship ship;
        private final FortSceneWorld terrain;
        private final Fort fort;
        private final List<Integer> shotTicks = new ArrayList<>();
        private final Warship lateShip;
        private boolean lateShipAdded;
        int contestedTicks;
        int shotsWhileContested;
        int flipTick = -1;
        int shotsAtFlip;
        int shotsAfterEvent;
        int lastShotTick = -1;
        int blastTick = -1;
        int magazineWallHpBefore;
        int magazineWallHpAfter;
        boolean hazardAtEnd;
        int shotsAtDefenderShip;
        float defenderHull = 1f;
        private int lastShots;
        private int shotsAtLateShip;
        private boolean blownSeen;

        Trace(String loop, Warship ship, Warship lateShip, FortSceneWorld terrain, Fort fort) {
            this.loop = loop;
            this.lateShip = lateShip;
            this.ship = ship;
            this.terrain = terrain;
            this.fort = fort;
        }

        int shots() {
            int n = 0;
            for (FortressBattery.Gun g : fort.battery().guns()) n += g.shotsFired;
            return n;
        }

        String state() {
            FortressBattery b = fort.battery();
            return b.owner() + (b.fireControl().contested() ? " contested" : b.operational() ? " live" : " silent");
        }

        /** Rounds a second over the tick window. */
        float rate(int from, int to) {
            int n = 0;
            for (int tick : shotTicks) if (tick >= from && tick < to) n++;
            return n / ((to - from) / (float) SECONDS_TICKS);
        }

        private void holders(BattleSimulation sim, String prefix, int[][] cells) {
            for (int i = 0; i < cells.length; i++) {
                int[] at = terrain.cell(cells[i][0], cells[i][1]);
                // The structures keep their own guns, which would clear a few riflemen off in seconds; the scene is
                // about the fort's rules, so the holders are made to outlast them.
                sim.spawn(new EntitySpec(prefix + i, Faction.MARINE, UnitType.MARINE, at[0], at[1]).health(1_000_000f));
            }
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (tick == 0) {
                // The fort's nests and gun would cut ten riflemen down in seconds; this loop is about the way in,
                // so the squad is made to outlast them.
                for (long id : squadMembers) sim.world().setHp(id, 5000f);
            }
            if (tick == EVENT_TICK) {
                switch (loop) {
                    case "gun-side-hold" -> holders(sim, "holder-", new int[][]{{OX + 33, OY + 27}, {OX + 33, OY + 28}, {OX + 33, OY + 29}});
                    case "bunker-taken" -> holders(sim, "holder-", new int[][]{{OX + 10, OY + 11}, {OX + 11, OY + 11}, {OX + 14, OY + 11}});
                    case "bunker-wrecked" -> sim.applyDamage(fort.place(FortAspect.FIRE_CONTROL).coreId(), 1e7f, 100f);
                    case "magazine-wrecked" -> {
                        int[] ring = terrain.cell(OX + 35, OY + 8);
                        magazineWallHpBefore = sim.getGrid().getWallHp(ring[0], ring[1]);
                        sim.applyDamage(fort.place(FortAspect.MAGAZINE).coreId(), 1e7f, 100f);
                    }
                    case "power-cut" -> sim.applyDamage(fort.place(FortAspect.POWER).coreId(), 1e7f, 100f);
                    case "fort-taken-turns" -> {
                        holders(sim, "bunker-", new int[][]{{OX + 10, OY + 11}, {OX + 11, OY + 11}, {OX + 14, OY + 11}});
                        holders(sim, "store-", new int[][]{{OX + 37, OY + 6}, {OX + 38, OY + 6}, {OX + 42, OY + 10}});
                        holders(sim, "plant-", new int[][]{{OX + 9, OY + 31}, {OX + 10, OY + 31}, {OX + 16, OY + 31}});
                    }
                    default -> { }
                }
            }
            if (lateShip != null && tick == LATE_SHIP_TICK) {
                FortressBattery b = fort.battery();
                sim.addWarship(lateShip);
                shotsAtLateShip = shots();
            }
            if (loop.equals("magazine-wrecked") && !blownSeen && fort.magazineBlown()) {
                blownSeen = true;
                blastTick = tick;
            }
            if (loop.equals("magazine-wrecked") && tick == EVENT_TICK + 2) {
                int[] ring = terrain.cell(OX + 35, OY + 8);
                magazineWallHpAfter = sim.getGrid().getWallHp(ring[0], ring[1]);
            }
            int shots = shots();
            boolean fired = shots > lastShots;
            lastShots = shots;
            if (fired) {
                lastShotTick = tick;
                shotTicks.add(tick);
            }
            if (tick == EVENT_TICK) shotsAtEvent = shots;
            if (tick >= EVENT_TICK) shotsAfterEvent = shots - shotsAtEvent;
            FortressBattery b = fort.battery();
            if (b.fireControl().contested()) {
                contestedTicks++;
                if (fired) shotsWhileContested++;
            }
            if (flipTick < 0 && b.owner() == Faction.MARINE) {
                flipTick = tick;
                shotsAtFlip = shots;
            }
            if (squadMembers.length > 0) {
                int inside = 0;
                int living = 0;
                Fort.Place bunker = fort.place(FortAspect.FIRE_CONTROL);
                for (long id : squadMembers) {
                    if (!sim.world().isAlive(id)) continue;
                    living++;
                    if (bunker.contains((int) Math.floor(sim.world().x(id)), (int) Math.floor(sim.world().y(id)))) inside++;
                }
                maxInBunker = Math.max(maxInBunker, inside);
                alive = living;
                var active = sim.getSquadMoveOrderService().activeOrder(squadId);
                if (active != null) lastOrder = active.toString();
                long core = bunker.coreId();
                float bx = sim.world().x(core);
                float by = sim.world().y(core);
                if (firstSeenTick < 0 && sim.playerSeesCell((int) fort.battery().x(), (int) fort.battery().y())) firstSeenTick = tick;
                var snapshot = sim.getCommanderSnapshot(Faction.MARINE);
                var directive = snapshot == null ? null : snapshot.directiveFor(squadId);
                var order = sim.getSquad(squadId).assignedObjective;
                if (firstOrderTick < 0 && directive != null && directive.reason().startsWith("DEFENCE_STRUCTURE_")
                        && order != null) {
                    firstOrderTick = tick;
                    firstOrderReason = directive.reason() + " to " + order.targetCellX() + "," + order.targetCellY();
                    firstOrderAspect = nearestAspect(order.targetCellX(), order.targetCellY());
                }
                if (directive != null && order != null && directive.reason().startsWith("DEFENCE_STRUCTURE_")) {
                    String line = directive.reason() + "->" + nearestAspect(order.targetCellX(), order.targetCellY());
                    if (orders.isEmpty() || !orders.get(orders.size() - 1).endsWith(line)) orders.add("t" + tick + " " + line);
                }
                if (laterOrderTick < 0 && flipTick > 0 && directive != null
                        && directive.reason().startsWith("DEFENCE_STRUCTURE_") && order != null) {
                    FortAspect aspect = nearestAspect(order.targetCellX(), order.targetCellY());
                    if (aspect != FortAspect.FIRE_CONTROL) {
                        laterOrderTick = tick;
                        laterOrderAspect = aspect;
                    }
                }
                for (long id : squadMembers) {
                    if (sim.world().isAlive(id)) {
                        nearestToBunker = Math.min(nearestToBunker,
                                (float) Math.hypot(sim.world().x(id) - bx, sim.world().y(id) - by));
                    }
                }
            }
            hazardAtEnd = !sim.warships().batteryHazards().isEmpty();
            if (lateShip != null && tick >= LATE_SHIP_TICK) {
                shotsAtDefenderShip = shots - shotsAtLateShip;
                defenderHull = lateShip.hullFraction();
            }
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

        private int shotsAtEvent;
        long[] squadMembers = new long[0];
        int squadId = -1;
        int laterOrderTick = -1;
        FortAspect laterOrderAspect;
        final List<String> orders = new ArrayList<>();
        FortAspect firstOrderAspect;
        int firstOrderTick = -1;
        int firstSeenTick = -1;
        String firstOrderReason = "none";
        float nearestToBunker = Float.MAX_VALUE;
        String lastOrder = "none";
        int maxInBunker;
        int alive;
    }
}
