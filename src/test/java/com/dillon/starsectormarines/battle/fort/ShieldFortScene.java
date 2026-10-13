package com.dillon.starsectormarines.battle.fort;

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
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.bsp.FortStage;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.ShieldFort;
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
        for (String loop : List.of("intact", "power-cut", "control-taken", "control-only", "no-emitter")) {
            reports.add(report(loop, playOne(loop, frames)));
        }
        return reports;
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
        boolean marineShip = loop.equals("intact") || loop.equals("power-cut") || loop.equals("no-emitter");
        boolean squadLoop = !loop.equals("intact") && !loop.equals("no-emitter");
        SceneBuilder builder = SceneBuilder.over(terrain.grid(), terrain.topology()).posts(terrain.posts())
                .missionCompletion(false);
        FortPlan.Gate main = plan.gates().get(0);
        int[] start = terrain.cell(main.mouthX() + main.outX() * 8, main.mouthY() + main.outY() * 8);
        if (squadLoop) builder.squad("company").faction(Faction.MARINE).size(8).at(start[0], start[1]).primary("weapon.smg").done();
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
        player.at(SHIP_TICK, s -> {
            s.addWarship(ship);
            ship.direct(8f, true, 1);
        });

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
        };
        SceneRun.of(world).player(player).observe(script, trace)
                .frames(frames, loop, 90, tick -> String.format(Locale.ROOT, "%s / tick %d / %s", loop, tick,
                        trace.caption(sim)))
                .run(TICKS);
        return new Run(trace, sim, fort);
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
        int hitsAfterShip;
        int firstHit = -1;
        int firstOverload = -1;
        int knotDeaths;
        int firstKnotDeath = -1;
        int powerTakenTick = -1;
        int controlTakenTick = -1;
        int maxUnpoweredTicks;
        boolean unpoweredAtShip;
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
