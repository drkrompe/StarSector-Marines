package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.drone.DroneHub;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
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
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Time to break and time to kill for the non-mech hard targets, and two infantry controls, under the
 * same anti-armor answers {@code MechTimeToKillScene} measures against mechs: <b>what does tuning the
 * anti-materiel rifle and the heavy cannon against mech armor do to everything else they shoot?</b>
 *
 * <p>One target twenty cells west of one fire team on open ground: each armed ground vehicle class
 * (technical, the three APCs, both tanks), three emplacements (heavy MG, Vulcan, Hephaestus), a drone hub,
 * and two six-man squads as soft controls (line armor, and heavy power suits). Teams: two
 * anti-materiel rifles and two rocket tubes carried as the real specials over sub-machine guns that cannot
 * reach twenty cells (ammunition unlimited, so the reading is a rate; rounds says what a magazine buys), six
 * DMRs, and one hostile Warden with its racks removed so only its heavy cannon speaks. Shooters are
 * immortal and planted; the targets' own guns are live. Times from the team's first round: armor break
 * (first structure lost on any body), and the kill (every body dead or wrecked).
 *
 * <p>Readings only; the before/after comparison is the scene played on the two weapon catalogs. Specials
 * fire only at hard targets ({@code TacticalScoring.canSpecialTarget}), so the AMR and rocket rows against
 * the squads read "never": that is the gate, not a measurement of the weapon against infantry.
 *
 * <p>Switches (forwarded by {@code sceneEvidence}): {@code -Dbattle.scene.hardTtk.seeds=N} (default 4),
 * {@code .threads=N} (default half the processors), {@code .targets=heavy-apc,turret-heavy-mg},
 * {@code .weapons=amr,heavy-cannon}.
 */
public final class HardTargetTimeToKillScene implements BehaviorScene {

    private static final int WIDTH = 40;
    private static final int HEIGHT = 24;
    private static final int MAX_TICKS = 9000;
    private static final int TARGET_X = 10;
    private static final int TARGET_Y = 10;
    private static final int TEAM_X = 30;
    private static final float IMMORTAL = 1_000_000f;
    private static final int UNLIMITED_AMMO = 999;

    private static final String TARGET = "target";
    private static final String TEAM = "team";

    private enum Kind { VEHICLE, TURRET, HUB, SQUAD }

    private record Target(String id, Kind kind, VehicleType vehicle, String structureId,
                          float armorCapacity, float armorRating) { }

    private static final List<Target> TARGETS = List.of(
            new Target("technical", Kind.VEHICLE, VehicleType.MILITIA_TECHNICAL, null, 0f, 0f),
            new Target("heavy-apc", Kind.VEHICLE, VehicleType.HEAVY_APC, null, 0f, 0f),
            new Target("breakthrough-tank", Kind.VEHICLE, VehicleType.BREAKTHROUGH_TANK, null, 0f, 0f),
            new Target("turret-heavy-mg", Kind.TURRET, null, TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, 0f, 0f),
            new Target("turret-vulcan", Kind.TURRET, null, TurretCatalogRegistry.VULCAN_STRUCTURE_ID, 0f, 0f),
            new Target("turret-hephaestus", Kind.TURRET, null, TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID, 0f, 0f),
            new Target("drone-hub", Kind.HUB, null, null, 0f, 0f),
            // armor.line and armor.heavy (marine-armor-catalog).
            new Target("squad-line", Kind.SQUAD, null, null, 10f, 8f),
            new Target("squad-suits", Kind.SQUAD, null, null, 60f, 12f));

    private record Team(String id, String primary, String special, int size, boolean heavyCannon) { }

    private static final List<Team> TEAMS = List.of(
            new Team("amr", "weapon.smg", "special.anti-materiel-rifle", 2, false),
            new Team("rockets", "weapon.smg", "special.rocket-launcher", 2, false),
            new Team("dmr", "weapon.dmr", null, 6, false),
            new Team("heavy-cannon", null, null, 1, true));

    @Override public String id() { return "hard-target-time-to-kill"; }

    @Override
    public String label() {
        return "Hard-target time-to-kill: vehicles, emplacements, a drone hub and two infantry controls under"
                + " each anti-armor answer";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        int seeds = Integer.getInteger("battle.scene.hardTtk.seeds", 4);
        int threads = Integer.getInteger("battle.scene.hardTtk.threads",
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        Set<String> targetFilter = filter("battle.scene.hardTtk.targets");
        Set<String> weaponFilter = filter("battle.scene.hardTtk.weapons");

        List<Cell> cells = new ArrayList<>();
        for (Target target : TARGETS) {
            if (targetFilter != null && !targetFilter.contains(target.id)) continue;
            for (Team team : TEAMS) {
                if (weaponFilter != null && !weaponFilter.contains(team.id)) continue;
                cells.add(new Cell(target, team));
            }
        }
        List<Future<Trace>> futures = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            boolean first = true;
            for (Cell cell : cells) {
                for (long seed = 1; seed <= seeds; seed++) {
                    final long s = seed;
                    if (first) {
                        futures.add(CompletableFuture.completedFuture(playOnce(cell, s)));
                        first = false;
                    } else {
                        futures.add(pool.submit(() -> playOnce(cell, s)));
                    }
                }
            }
            for (Future<Trace> f : futures) {
                Trace t = f.get();
                t.cell.traces.add(t);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } finally {
            pool.shutdownNow();
        }

        List<SceneReport> reports = new ArrayList<>();
        StringBuilder table = new StringBuilder();
        table.append(String.format(Locale.ROOT,
                "%nhard-target-time-to-kill  seeds=%d  (seconds from first round: mean +- sd [min..max], n killed/plays)%n", seeds));
        table.append(String.format(Locale.ROOT, "%-18s %-13s | %-30s | %-26s | %-26s | %s%n",
                "target", "team", "TTK", "armor break (1st struct)", "all armor gone", "heavy rounds per kill"));
        for (Cell cell : cells) {
            reports.add(cell.report(id()));
            table.append(cell.row()).append('\n');
        }
        System.out.println(table);
        return reports;
    }

    private static Set<String> filter(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) return null;
        return new HashSet<>(Arrays.asList(value.toLowerCase(Locale.ROOT).split(",")));
    }

    private Trace playOnce(Cell cell, long seed) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
        }
        // A sealed box in the south-west corner holds one marine nobody can see, so the target's side stays on
        // the map whatever the target is.
        for (int x = 0; x <= 4; x++) {
            for (int y = HEIGHT - 5; y < HEIGHT; y++) {
                boolean edge = x == 0 || x == 4 || y == HEIGHT - 5 || y == HEIGHT - 1;
                if (!edge) continue;
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, false);
                topology.setWall(x, y, true);
            }
        }
        Target target = cell.target;
        if (target.kind == Kind.TURRET || target.kind == Kind.HUB) {
            // As BattleSetup.spawnDefensePostTurrets seats one: blocked to feet, open to sight and fire.
            grid.setWalkable(TARGET_X, TARGET_Y, false);
            grid.setSeeThrough(TARGET_X, TARGET_Y, true);
        }
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) grid.recomputeCoverAt(x, y);

        SceneBuilder builder = SceneBuilder.over(grid, topology).missionCompletion(false).seed(seed);
        builder.unit("holdout", Faction.MARINE, UnitType.MARINE, 2, HEIGHT - 3);
        if (target.kind == Kind.SQUAD) {
            builder.squad(TARGET).faction(Faction.MARINE).size(6)
                    .primary("weapon.field-rifle")
                    .armor(target.armorCapacity, target.armorRating)
                    .inFile(TARGET_X, TARGET_Y - 3, 0, 1)
                    .stationary()
                    .assigned(id -> ObjectiveAssignment.defendArea(id, TARGET_X, TARGET_Y))
                    .done();
        }
        Team team = cell.team;
        if (team.heavyCannon) {
            builder.squad(TEAM).faction(Faction.DEFENDER).size(1).at(TEAM_X + 2, TARGET_Y)
                    .mech(MechVariant.WARDEN, MechRole.ASSAULT)
                    .stationary()
                    .health(IMMORTAL)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, TEAM_X, TARGET_Y))
                    .done();
        } else {
            SceneBuilder.SquadSpec spec = builder.squad(TEAM).faction(Faction.DEFENDER).type(UnitType.MARINE_RED)
                    .size(team.size)
                    .primary(team.primary)
                    .inFile(TEAM_X, TARGET_Y - team.size / 2, 0, 1)
                    .stationary()
                    .health(IMMORTAL)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, TEAM_X, TARGET_Y));
            if (team.special != null) spec.special(team.special, team.size, UNLIMITED_AMMO);
            spec.done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        long[] bodies;
        switch (target.kind) {
            case VEHICLE -> {
                long v = sim.convoy().spawn(target.vehicle, Faction.MARINE, VehicleMission.deployed(TARGET_X, TARGET_Y));
                sim.convoy().body(v).teleport(TARGET_X, TARGET_Y, 0f);
                bodies = new long[] {v};
            }
            case TURRET -> {
                // On the target's side: a defence post would spawn on the defenders', the team's own.
                bodies = new long[] {sim.spawn(MapTurret.create("turret", Faction.MARINE, target.structureId,
                        TARGET_X, TARGET_Y))};
            }
            case HUB -> bodies = new long[] {sim.spawn(DroneHub.create("hub", Faction.MARINE, TARGET_X, TARGET_Y))};
            default -> bodies = world.members(TARGET);
        }
        long[] shooters = world.members(TEAM);
        if (team.heavyCannon) {
            MechLoadoutComponent gun = sim.world().mechLoadout(shooters[0]);
            gun.destroyMount(MechMountSlot.LEFT_SHOULDER);
            gun.destroyMount(MechMountSlot.RIGHT_SHOULDER);
            sim.world().setAttackRange(shooters[0], gun.maxWeaponRange());
        }
        Trace trace = new Trace(cell, seed, bodies, shooters);
        SceneRun run = SceneRun.of(world).observe(trace);
        try {
            while (run.tick() < MAX_TICKS && trace.deathTick < 0) run.step();
        } finally {
            sim.close();
        }
        trace.ticks = run.tick();
        return trace;
    }

    // ---- instrument ----

    private static final class Cell {
        final Target target;
        final Team team;
        final List<Trace> traces = new ArrayList<>();

        Cell(Target target, Team team) {
            this.target = target;
            this.team = team;
        }

        String key() {
            return target.id + "." + team.id;
        }

        List<Trace> sorted() {
            List<Trace> list = new ArrayList<>(traces);
            list.sort((a, b) -> Long.compare(a.seed, b.seed));
            return list;
        }

        String row() {
            List<Trace> list = sorted();
            int rounds = 0;
            int killed = 0;
            for (Trace t : list) {
                if (t.deathTick >= 0) {
                    killed++;
                    rounds += t.heavyRounds;
                }
            }
            return String.format(Locale.ROOT, "%-18s %-13s | %-30s | %-26s | %-26s | %s",
                    target.id, team.id,
                    stat(list, t -> t.deathTick < 0 ? -1 : t.deathTick - t.firstShotTick) + " " + killed + "/" + list.size(),
                    stat(list, t -> t.structureTick < 0 ? -1 : t.structureTick - t.firstShotTick),
                    stat(list, t -> t.armorZeroTick < 0 ? -1 : t.armorZeroTick - t.firstShotTick),
                    killed == 0 ? "-" : String.format(Locale.ROOT, "%.1f", rounds / (float) killed));
        }

        SceneReport report(String sceneId) {
            List<Trace> list = sorted();
            Map<String, Number> m = new LinkedHashMap<>();
            m.put("plays", list.size());
            int ticks = 0;
            int fired = 0;
            int deaths = 0;
            for (Trace t : list) {
                ticks += t.ticks;
                if (t.firstShotTick >= 0) fired++;
                if (t.deathTick >= 0) deaths++;
                String k = "seed" + t.seed;
                m.put(k + ".firstShot", t.firstShotTick);
                m.put(k + ".structure", t.structureTick);
                m.put(k + ".armorZero", t.armorZeroTick);
                m.put(k + ".death", t.deathTick);
                m.put(k + ".heavyRounds", t.heavyRounds);
            }
            m.put("deaths", deaths);
            putStat(m, "ttk", list, t -> t.deathTick < 0 ? -1 : t.deathTick - t.firstShotTick);
            putStat(m, "armorBreak", list, t -> t.structureTick < 0 ? -1 : t.structureTick - t.firstShotTick);
            putStat(m, "armorZero", list, t -> t.armorZeroTick < 0 ? -1 : t.armorZeroTick - t.firstShotTick);
            // The one mechanic: the team engages. Specials are gated off soft targets, so a squad row is exempt.
            boolean gated = target.kind == Kind.SQUAD && team.special != null;
            List<Verdict> verdicts = List.of(Verdict.of("team-engaged", gated || fired == list.size(),
                    fired + " of " + list.size() + " plays in which the team fired" + (gated ? " (special gated off infantry)" : "")));
            return new SceneReport(sceneId, key(), ticks, verdicts, m);
        }
    }

    private interface Reading {
        int ticks(Trace t);
    }

    private static String stat(List<Trace> list, Reading reading) {
        double[] s = stats(list, reading);
        if (s == null) return "never";
        return String.format(Locale.ROOT, "%.1f+-%.1f [%.1f..%.1f]", s[0], s[1], s[2], s[3]);
    }

    private static void putStat(Map<String, Number> m, String key, List<Trace> list, Reading reading) {
        double[] s = stats(list, reading);
        m.put(key + ".n", s == null ? 0 : (int) s[4]);
        if (s == null) return;
        m.put(key + ".meanSeconds", s[0]);
        m.put(key + ".sdSeconds", s[1]);
        m.put(key + ".minSeconds", s[2]);
        m.put(key + ".maxSeconds", s[3]);
    }

    private static double[] stats(List<Trace> list, Reading reading) {
        List<Double> values = new ArrayList<>();
        for (Trace t : list) {
            int ticks = reading.ticks(t);
            if (ticks >= 0) values.add(ticks / 30.0);
        }
        if (values.isEmpty()) return null;
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / values.size();
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        return new double[] {mean, Math.sqrt(var), min, max, values.size()};
    }

    private static final class Trace implements TickObserver {
        final Cell cell;
        final long seed;
        final long[] bodies;
        final Set<Long> shooters = new HashSet<>();
        int ticks;
        int firstShotTick = -1;
        int structureTick = -1;
        int armorZeroTick = -1;
        int deathTick = -1;
        /** Special or mech-mount rounds; for a team with neither (DMR), every round. */
        int heavyRounds;
        final float[] maxHp;

        Trace(Cell cell, long seed, long[] bodies, long[] shooters) {
            this.cell = cell;
            this.seed = seed;
            this.bodies = bodies;
            for (long s : shooters) this.shooters.add(s);
            this.maxHp = new float[bodies.length];
            Arrays.fill(maxHp, Float.NaN);
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            if (deathTick >= 0) return;
            countShots(sim, tick);
            boolean anyAlive = false;
            boolean armorLeft = false;
            for (int i = 0; i < bodies.length; i++) {
                long b = bodies[i];
                boolean alive = sim.getRoster().isAliveById(b)
                        && (!sim.convoy().isVehicle(b) || sim.convoy().isPresent(b));
                if (!alive) {
                    if (structureTick < 0) structureTick = tick;
                    continue;
                }
                anyAlive = true;
                float hp = sim.world().hp(b);
                if (Float.isNaN(maxHp[i])) maxHp[i] = sim.world().maxHp(b);
                if (structureTick < 0 && hp < maxHp[i]) structureTick = tick;
                if (sim.world().hasArmor(b) && sim.world().armor(b) > 0f) armorLeft = true;
            }
            if (armorZeroTick < 0 && !armorLeft) armorZeroTick = tick;
            if (!anyAlive) deathTick = tick;
        }

        private void countShots(BattleSimulation sim, int tick) {
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (!shooters.contains(shot.shooterId)) continue;
                if (firstShotTick < 0) firstShotTick = tick;
                boolean heavy = shot.specialEquipmentDef != null || shot.mechWeaponDef != null;
                if (heavy || cell.team.special == null && !cell.team.heavyCannon) heavyRounds++;
            }
        }
    }
}
