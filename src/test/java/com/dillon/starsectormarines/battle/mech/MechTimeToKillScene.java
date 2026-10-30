package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
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
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
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
 * Mech time-to-kill by chassis and weapon: <b>how long does each answer to a mech take, and what
 * does the mech lose on the way?</b> The calibration instrument for mech locational damage
 * ({@code mechs-nouns.md}, Section pools / Section loss) against the whole-body control, and for the
 * d4 promise that rifles, anti-materiel rifles, rockets and heavy cannon are each a practical answer
 * to intact mech armor.
 *
 * <p>One planted stock mech of each chassis, its own guns live, under one fire team of one weapon at
 * twenty cells, in the open and again hull-down behind a masonry parapet (the {@link MechHullDownScene}
 * ground). Teams: six field rifles, six pulse rifles, six DMRs; two anti-materiel rifles and two rocket
 * tubes carried as the real specials (ammunition unlimited, so the reading is a rate; rounds-to-kill
 * says what a magazine would buy) over sub-machine guns that cannot reach twenty cells; and one
 * hostile Warden (under the assault role, which engages a planted target where long-range support
 * perches and never fires) with its shoulder racks removed, so only its heavy cannon speaks.
 * Shooters are immortal and planted: the question is how the mech comes apart, not who wins.
 *
 * <p>Every time is measured from the team's first round and reported as mean, spread and range over
 * the seeds: armor break (first structure lost anywhere), all armor gone, first mount lost, arms (the
 * main weapon) lost, crippled, disarmed, and the kill; with each loss also as a share of that play's
 * time to kill, and the share of all damage each section took. Plays run side by side on separate
 * simulations; two runs have produced identical readings.
 *
 * <p>The whole-body control cannot run in the same JVM ({@link MechSections#ENABLED} is read once),
 * so the scene played under {@code -Dbattle.mech.locationalDamage=false} is the control. Readings
 * only; the one verdict is the mechanic that must hold on every play: death only through the torso.
 *
 * <p>Switches (forwarded by {@code sceneEvidence}): {@code -Dbattle.scene.mechTtk.seeds=N} (default 4),
 * {@code .threads=N} (default half the processors), {@code .chassis=hound,bulwark},
 * {@code .weapons=rifle,amr}, {@code .ground=open,hull-down}.
 */
public final class MechTimeToKillScene implements BehaviorScene {

    private static final int WIDTH = 40;
    private static final int HEIGHT = 21;
    private static final int MAX_TICKS = 5400;
    private static final int MECH_X = 10;
    private static final int MECH_Y = 10;
    private static final int BERM_X = MECH_X + 1;
    private static final int BERM_HALF_LENGTH = 3;
    private static final int PARAPET_HP = 400;
    private static final int TEAM_X = 30;
    private static final float IMMORTAL = 1_000_000f;
    private static final int UNLIMITED_AMMO = 999;

    private static final String MECH = "mech";
    private static final String TEAM = "team";

    /** One fire team. */
    private record Team(String id, String primary, String special, int size, boolean heavyCannon) { }

    private static final List<Team> TEAMS = List.of(
            new Team("rifle", "weapon.field-rifle", null, 6, false),
            new Team("pulse", "weapon.pulse-rifle", null, 6, false),
            new Team("dmr", "weapon.dmr", null, 6, false),
            new Team("amr", "weapon.smg", "special.anti-materiel-rifle", 2, false),
            new Team("rockets", "weapon.smg", "special.srm-1", 2, false),
            new Team("heavy-cannon", null, null, 1, true));

    private static final List<String> GROUNDS = List.of("open", "hull-down");

    @Override public String id() { return "mech-time-to-kill"; }

    @Override
    public String label() {
        return "Mech time-to-kill: each chassis under each anti-mech answer, open and hull-down,"
                + " readings against the whole-body control";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        int seeds = Integer.getInteger("battle.scene.mechTtk.seeds", 4);
        int threads = Integer.getInteger("battle.scene.mechTtk.threads",
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        Set<String> chassisFilter = filter("battle.scene.mechTtk.chassis");
        Set<String> weaponFilter = filter("battle.scene.mechTtk.weapons");
        Set<String> groundFilter = filter("battle.scene.mechTtk.ground");

        List<Cell> cells = new ArrayList<>();
        for (MechVariant variant : MechVariant.values()) {
            if (chassisFilter != null && !chassisFilter.contains(variant.id)) continue;
            for (Team team : TEAMS) {
                if (weaponFilter != null && !weaponFilter.contains(team.id)) continue;
                for (String ground : GROUNDS) {
                    if (groundFilter != null && !groundFilter.contains(ground)) continue;
                    cells.add(new Cell(variant, team, ground));
                }
            }
        }
        // The first play runs alone so every lazily loaded registry is loaded before the pool starts.
        List<Future<Trace>> futures = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            boolean first = true;
            for (Cell cell : cells) {
                for (long seed = 1; seed <= seeds; seed++) {
                    final long s = seed;
                    if (first) {
                        Trace t = playOnce(cell, s);
                        futures.add(CompletableFuture.completedFuture(t));
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
        table.append(String.format(Locale.ROOT, "%nmech-time-to-kill  locationalDamage=%s  seeds=%d  (seconds: mean +- sd [min..max], n killed/plays)%n",
                MechSections.ENABLED, seeds));
        table.append(String.format(Locale.ROOT, "%-8s %-12s %-9s | %-30s | %-26s | %-22s | %-22s | %-22s | %-22s | %-7s | %s%n",
                "chassis", "team", "ground", "TTK (from first round)", "armor break (1st struct)",
                "all armor gone", "first mount lost", "arms lost", "crippled", "rounds", "damage share L/T/A/LS/RS/H"));
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
        boolean berm = cell.ground.equals("hull-down");
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
        }
        if (berm) {
            for (int y = MECH_Y - BERM_HALF_LENGTH; y <= MECH_Y + BERM_HALF_LENGTH; y++) {
                grid.setWalkable(BERM_X, y, false);
                grid.setSeeThrough(BERM_X, y, true);
                grid.setWallHp(BERM_X, y, PARAPET_HP);
                topology.setWall(BERM_X, y, true);
                topology.setWindow(BERM_X, y, true);
            }
        }
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) grid.recomputeCoverAt(x, y);

        SceneBuilder builder = SceneBuilder.over(grid, topology).missionCompletion(false).seed(seed);
        builder.squad(MECH).faction(Faction.MARINE).size(1).at(MECH_X + 2, MECH_Y)
                .mech(cell.variant, cell.variant.defaultRole)
                .stationary()
                .assigned(id -> ObjectiveAssignment.defendArea(id, MECH_X, MECH_Y))
                .done();
        Team team = cell.team;
        if (team.heavyCannon) {
            builder.squad(TEAM).faction(Faction.DEFENDER).size(1).at(TEAM_X + 2, MECH_Y)
                    .mech(MechVariant.WARDEN, MechRole.ASSAULT)
                    .stationary()
                    .health(IMMORTAL)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, TEAM_X, MECH_Y))
                    .done();
        } else {
            SceneBuilder.SquadSpec spec = builder.squad(TEAM).faction(Faction.DEFENDER).type(UnitType.MARINE_RED)
                    .size(team.size)
                    .primary(team.primary)
                    .inFile(TEAM_X, MECH_Y - team.size / 2, 0, 1)
                    .stationary()
                    .health(IMMORTAL)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, TEAM_X, MECH_Y));
            if (team.special != null) spec.special(team.special, team.size, UNLIMITED_AMMO);
            spec.done();
        }
        SceneWorld world = builder.build();
        long mech = world.members(MECH)[0];
        long[] shooters = world.members(TEAM);
        if (team.heavyCannon) {
            // Only the heavy cannon: the racks would make this an LRM reading.
            MechLoadoutComponent gun = world.sim().world().mechLoadout(shooters[0]);
            gun.destroyMount(MechMountSlot.LEFT_SHOULDER);
            gun.destroyMount(MechMountSlot.RIGHT_SHOULDER);
            world.sim().world().setAttackRange(shooters[0], gun.maxWeaponRange());
        }
        Trace trace = new Trace(cell, seed, mech, shooters);
        SceneRun run = SceneRun.of(world).observe(trace);
        try {
            while (run.tick() < MAX_TICKS && trace.deathTick < 0) run.step();
        } finally {
            world.sim().close();
        }
        trace.ticks = run.tick();
        return trace;
    }

    // ---- instrument ----

    private static final class Cell {
        final MechVariant variant;
        final Team team;
        final String ground;
        final List<Trace> traces = new ArrayList<>();

        Cell(MechVariant variant, Team team, String ground) {
            this.variant = variant;
            this.team = team;
            this.ground = ground;
        }

        String key() {
            return variant.id + "." + team.id + "." + ground;
        }

        List<Trace> sorted() {
            List<Trace> list = new ArrayList<>(traces);
            list.sort((a, b) -> Long.compare(a.seed, b.seed));
            return list;
        }

        String row() {
            List<Trace> list = sorted();
            float[] share = damageShare(list);
            int rounds = 0;
            int killed = 0;
            for (Trace t : list) {
                if (t.deathTick >= 0) {
                    killed++;
                    rounds += t.teamRounds;
                }
            }
            return String.format(Locale.ROOT, "%-8s %-12s %-9s | %-30s | %-26s | %-22s | %-22s | %-22s | %-22s | %-7s | %s",
                    variant.id, team.id, ground,
                    stat(list, t -> t.deathTick < 0 ? -1 : t.deathTick - t.firstShotTick) + " " + killed + "/" + list.size(),
                    stat(list, t -> t.structureTick < 0 ? -1 : t.structureTick - t.firstShotTick),
                    stat(list, t -> t.armorZeroTick < 0 ? -1 : t.armorZeroTick - t.firstShotTick),
                    stat(list, t -> t.firstMountLostTick < 0 ? -1 : t.firstMountLostTick - t.firstShotTick),
                    stat(list, t -> t.armsLostTick < 0 ? -1 : t.armsLostTick - t.firstShotTick),
                    stat(list, t -> t.crippleTick < 0 ? -1 : t.crippleTick - t.firstShotTick),
                    killed == 0 ? "-" : String.format(Locale.ROOT, "%.1f", rounds / (float) killed),
                    share == null ? "-" : String.format(Locale.ROOT, "%.2f/%.2f/%.2f/%.2f/%.2f/%.2f",
                            share[0], share[1], share[2], share[3], share[4], share[5]));
        }

        SceneReport report(String sceneId) {
            List<Trace> list = sorted();
            Map<String, Number> m = new LinkedHashMap<>();
            m.put("locationalDamageEnabled", MechSections.ENABLED ? 1 : 0);
            m.put("plays", list.size());
            int deaths = 0;
            int torsoKills = 0;
            int ticks = 0;
            for (Trace t : list) {
                ticks += t.ticks;
                if (t.deathTick >= 0) {
                    deaths++;
                    if (!MechSections.ENABLED || t.torsoAtZeroOnDeath) torsoKills++;
                }
                String k = "seed" + t.seed;
                m.put(k + ".firstShot", t.firstShotTick);
                m.put(k + ".firstDamage", t.firstDamageTick);
                m.put(k + ".structure", t.structureTick);
                m.put(k + ".armorZero", t.armorZeroTick);
                m.put(k + ".torsoArmorZero", t.torsoArmorZeroTick);
                m.put(k + ".firstMountLost", t.firstMountLostTick);
                m.put(k + ".cripple", t.crippleTick);
                m.put(k + ".headLost", t.headLostTick);
                m.put(k + ".armsLost", t.armsLostTick);
                m.put(k + ".disarmed", t.disarmedTick);
                m.put(k + ".death", t.deathTick);
                m.put(k + ".teamRounds", t.teamRounds);
                m.put(k + ".mechRounds", t.mechRounds);
                m.put(k + ".mechRoundsAfterFirstMountLost", t.mechRoundsAfterMountLost);
                m.put(k + ".deathStruck", t.deathStruck);
            }
            m.put("deaths", deaths);
            putStat(m, "ttk", list, t -> t.deathTick < 0 ? -1 : t.deathTick - t.firstShotTick);
            putStat(m, "armorBreak", list, t -> t.structureTick < 0 ? -1 : t.structureTick - t.firstShotTick);
            putStat(m, "armorZero", list, t -> t.armorZeroTick < 0 ? -1 : t.armorZeroTick - t.firstShotTick);
            putStat(m, "firstMountLost", list, t -> t.firstMountLostTick < 0 ? -1 : t.firstMountLostTick - t.firstShotTick);
            putStat(m, "armsLost", list, t -> t.armsLostTick < 0 ? -1 : t.armsLostTick - t.firstShotTick);
            putStat(m, "disarmed", list, t -> t.disarmedTick < 0 ? -1 : t.disarmedTick - t.firstShotTick);
            putFraction(m, "firstMountLostShareOfTtk", list, t -> t.firstMountLostTick);
            putFraction(m, "armsLostShareOfTtk", list, t -> t.armsLostTick);
            putFraction(m, "crippleShareOfTtk", list, t -> t.crippleTick);
            putFraction(m, "disarmedShareOfTtk", list, t -> t.disarmedTick);
            putStat(m, "cripple", list, t -> t.crippleTick < 0 ? -1 : t.crippleTick - t.firstShotTick);
            float[] share = damageShare(list);
            if (share != null) {
                for (MechSection s : MechSection.VALUES) m.put("damageShare." + s.name(), share[s.ordinal()]);
            }
            List<Verdict> verdicts = List.of(Verdict.of("every-death-a-torso-kill", torsoKills == deaths,
                    torsoKills + " of " + deaths + " deaths with the torso at zero over " + list.size() + " plays"));
            return new SceneReport(sceneId, key(), ticks, verdicts, m);
        }
    }

    private interface Reading {
        int ticks(Trace t);
    }

    /** "mean +- sd [min..max]" in seconds over the plays that reached the reading. */
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

    /**
     * Over the plays that killed: mean share of the time to kill at which {@code reading} came (a loss
     * the mech never suffered before dying counts as 1), and how many of them suffered it first.
     */
    private static void putFraction(Map<String, Number> m, String key, List<Trace> list, Reading reading) {
        double sum = 0;
        int n = 0;
        int before = 0;
        for (Trace t : list) {
            if (t.deathTick < 0) continue;
            int span = t.deathTick - t.firstShotTick;
            if (span <= 0) continue;
            int at = reading.ticks(t);
            boolean happened = at >= 0 && at < t.deathTick;
            if (happened) before++;
            sum += happened ? (at - t.firstShotTick) / (double) span : 1.0;
            n++;
        }
        if (n == 0) return;
        m.put(key, sum / n);
        m.put(key + ".before", before);
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

    /** Share of all armor and structure taken off each section, summed over plays; null under the control. */
    private static float[] damageShare(List<Trace> list) {
        float[] sum = new float[MechSection.VALUES.length];
        float total = 0f;
        for (Trace t : list) {
            for (int i = 0; i < sum.length; i++) {
                sum[i] += t.taken[i];
                total += t.taken[i];
            }
        }
        if (total <= 0f) return null;
        for (int i = 0; i < sum.length; i++) sum[i] /= total;
        return sum;
    }

    private static final class Trace implements TickObserver {
        final Cell cell;
        final long seed;
        final long mech;
        final Set<Long> shooters = new HashSet<>();
        int ticks;
        MechLoadoutComponent loadout;
        MechSections sections;
        int firstDamageTick = -1;
        /** The first round the fire team fired: every time below is measured from it. */
        int firstShotTick = -1;
        int structureTick = -1;
        int armorZeroTick = -1;
        int torsoArmorZeroTick = -1;
        int firstMountLostTick = -1;
        int crippleTick = -1;
        int headLostTick = -1;
        /** The main weapon (the arms mount) gone. */
        int armsLostTick = -1;
        /** Every mount the chassis deployed with gone. */
        int disarmedTick = -1;
        int deathTick = -1;
        boolean torsoAtZeroOnDeath;
        /** Ordinal of the section(s) whose pools moved on the death tick, as a bit mask; 0 when unknown. */
        int deathStruck;
        int teamRounds;
        int mechRounds;
        int mechRoundsAfterMountLost;
        final float[] last = new float[MechSection.VALUES.length];
        final float[] taken = new float[MechSection.VALUES.length];

        Trace(Cell cell, long seed, long mech, long[] shooters) {
            this.cell = cell;
            this.seed = seed;
            this.mech = mech;
            for (long s : shooters) this.shooters.add(s);
            Arrays.fill(last, Float.NaN);
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            if (deathTick >= 0) return;
            boolean alive = sim.getRoster().isAliveById(mech);
            if (loadout == null && sim.world().hasMechLoadout(mech)) loadout = sim.world().mechLoadout(mech);
            if (sections == null && loadout != null) sections = loadout.sections();
            int moved = 0;
            if (sections != null) {
                for (MechSection section : MechSection.VALUES) {
                    int i = section.ordinal();
                    float now = sections.fitted(section) ? sections.armor(section) + sections.structure(section) : 0f;
                    float before = Float.isNaN(last[i])
                            ? (sections.fitted(section) ? sections.maxArmor(section) + sections.maxStructure(section) : 0f)
                            : last[i];
                    if (now < before - 1e-3f) {
                        taken[i] += before - now;
                        moved |= 1 << i;
                    }
                    last[i] = now;
                }
                if (torsoArmorZeroTick < 0 && sections.armor(MechSection.TORSO) <= 0f) torsoArmorZeroTick = tick;
                if (firstMountLostTick < 0 && (sections.destroyed(MechSection.ARMS)
                        || sections.destroyed(MechSection.LEFT_SHOULDER)
                        || sections.destroyed(MechSection.RIGHT_SHOULDER))) firstMountLostTick = tick;
                if (crippleTick < 0 && sections.crippled()) crippleTick = tick;
                if (armsLostTick < 0 && sections.destroyed(MechSection.ARMS)) armsLostTick = tick;
                if (disarmedTick < 0 && gone(MechSection.ARMS) && gone(MechSection.LEFT_SHOULDER)
                        && gone(MechSection.RIGHT_SHOULDER)) disarmedTick = tick;
                if (headLostTick < 0 && sections.sensorsLost()) headLostTick = tick;
            }
            if (!alive) {
                deathTick = tick;
                if (firstDamageTick < 0) firstDamageTick = tick;
                if (structureTick < 0) structureTick = tick;
                if (armorZeroTick < 0) armorZeroTick = tick;
                torsoAtZeroOnDeath = sections != null && sections.torsoDestroyed();
                deathStruck = moved;
                countShots(sim, tick);
                return;
            }
            float hp = sim.world().hp(mech);
            float maxHp = sim.world().maxHp(mech);
            boolean hasArmor = sim.world().hasArmor(mech);
            float armor = hasArmor ? sim.world().armor(mech) : 0f;
            float maxArmor = hasArmor ? sim.world().maxArmor(mech) : 0f;
            if (firstDamageTick < 0 && (hp < maxHp || armor < maxArmor)) firstDamageTick = tick;
            if (structureTick < 0 && hp < maxHp) structureTick = tick;
            if (armorZeroTick < 0 && hasArmor && armor <= 0f) armorZeroTick = tick;
            countShots(sim, tick);
        }

        private boolean gone(MechSection section) {
            return !sections.fitted(section) || sections.destroyed(section);
        }

        private void countShots(BattleSimulation sim, int tick) {
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shooters.contains(shot.shooterId)) {
                    teamRounds++;
                    if (firstShotTick < 0) firstShotTick = tick;
                }
                if (shot.shooterId == mech) {
                    mechRounds++;
                    if (firstMountLostTick >= 0) mechRoundsAfterMountLost++;
                }
            }
        }
    }
}
