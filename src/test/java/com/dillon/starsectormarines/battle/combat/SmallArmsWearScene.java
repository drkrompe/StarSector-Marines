package com.dillon.starsectormarines.battle.combat;

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
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A rifle squad firing on men behind cover: <b>does small-arms fire wear the cover it hits, and only the cover
 * a rifle round can bite?</b>
 *
 * <p>One squad of eight field rifles (penetration 7) holds its ground ten cells west of four defenders and
 * exchanges fire with them for two minutes. Both sides are rooted and given health enough to stand the whole
 * time — a squad that dies stops shooting for a reason that has nothing to do with the cover, which is exactly
 * what the first version of this scene recorded. Four loops, one per thing in front of the defenders:
 *
 * <ul>
 *   <li>{@code crates}: a row of wooden crates in the lane. They should be shot away, the first within
 *       a minute.</li>
 *   <li>{@code sandbags}: a row of blocking sandbags at the defenders' feet. Rifles bite packed earth only partly,
 *       so they should wear, each round taking well under half what it takes from a crate, and fall later.</li>
 *   <li>{@code wall-rifles}: a masonry wall with window openings, and a backstop wall behind the defenders. The
 *       rifles' rounds must be seen to land in the masonry, and the masonry must not lose one point.</li>
 *   <li>{@code wall-anti-materiel}: the same wall, and two marksmen putting anti-materiel rounds (penetration 18)
 *       into it. Their fire is scripted — the rifle's own AI policy keeps it for hardened targets and never aims at
 *       a wall — so this loop says what the round does to the wall, not when a marksman would choose it.</li>
 * </ul>
 *
 * <p>The readings are the cover itself: how many pieces stand, are damaged or are gone, when the first and last
 * fell, how much structure was taken, and where the squad's rounds stopped.
 */
public final class SmallArmsWearScene implements BehaviorScene {

    private static final int WIDTH = 40;
    private static final int HEIGHT = 22;
    private static final int TICKS = 3600;
    private static final int FRAME_EVERY_TICKS = 96;

    private static final int MARINE_X = 14;
    private static final int COVER_X = 25;
    private static final int DEFENDER_X = 26;
    private static final int BACKSTOP_X = 30;
    private static final int[] DEFENDER_ROWS = {7, 9, 11, 13};
    private static final int WALL_Y0 = 5;
    private static final int WALL_Y1 = 17;
    /** Behind solid masonry: what the anti-materiel rounds are aimed through. */
    private static final int ANCHOR_Y = 16;

    private static final String RIFLE = "weapon.field-rifle";
    private static final String ANTI_MATERIEL = "special.anti-materiel-rifle";
    /** One anti-materiel round per marksman at the rifle's own cooldown (3.4 s). */
    private static final int MARKSMAN_INTERVAL_TICKS = 102;
    private static final int MASONRY_HP = 100;
    /** A minute: "tens of seconds", the design target for a crate under a squad's sustained fire. */
    private static final int FIRST_CRATE_BAR_TICKS = 1800;
    /** Crates in front of the four men who are being shot at: the rest of the row is in nobody's lane. */
    private static final int CRATES_BAR = 3;

    private static final String MARINES = "marines";
    private static final String DEFENDERS = "defenders";
    private static final String ANCHOR = "anchor";

    @Override public String id() { return "small-arms-wear"; }

    @Override
    public String label() {
        return "Small arms wear: rifle fire shoots crates away, wears sandbags slowly, leaves masonry standing,"
                + " and the same wall yields to anti-materiel rounds";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        Trace crates = play(frames, "crates", Cover.CRATES, false);
        Trace sandbags = play(frames, "sandbags", Cover.SANDBAGS, false);
        Trace wallRifles = play(frames, "wall-rifles", Cover.WALL, false);
        Trace wallAmr = play(frames, "wall-anti-materiel", Cover.WALL, true);
        return List.of(reportCrates(crates), reportSandbags(sandbags, crates),
                reportWallRifles(wallRifles), reportWallAmr(wallAmr));
    }

    private enum Cover { CRATES, SANDBAGS, WALL }

    // ---- world ----

    private Trace play(FrameSink frames, String loopId, Cover cover, boolean marksmen) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
            }
        }
        List<int[]> wallCells = new ArrayList<>();
        if (cover == Cover.WALL) {
            for (int y = WALL_Y0; y <= WALL_Y1; y++) {
                masonry(grid, topology, COVER_X, y, isDefenderRow(y));
                wallCells.add(new int[] {COVER_X, y});
                masonry(grid, topology, BACKSTOP_X, y, false);
                wallCells.add(new int[] {BACKSTOP_X, y});
            }
        } else if (cover == Cover.SANDBAGS) {
            for (int row : DEFENDER_ROWS) {
                // A blocking parapet: impassable, seen and shot over.
                grid.setWalkable(COVER_X, row, false);
                grid.setSeeThrough(COVER_X, row, true);
                topology.setFixture(COVER_X, row, true);
            }
        }
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) grid.recomputeCoverAt(x, y);

        SceneBuilder builder = SceneBuilder.over(grid, topology).missionCompletion(false);
        SceneBuilder.SquadSpec squad = builder.squad(MARINES)
                .faction(Faction.MARINE).type(UnitType.MARINE).size(marksmen ? 2 : 8)
                .primary(RIFLE)
                .inFile(MARINE_X, marksmen ? 9 : 7, 0, 1)
                .stationary()
                .health(100_000f)
                .assigned(id -> ObjectiveAssignment.defendArea(id, MARINE_X, 10));
        if (marksmen) squad.special(ANTI_MATERIEL, 2, 0);
        squad.done();
        builder.squad(DEFENDERS)
                .faction(Faction.DEFENDER).type(UnitType.MARINE_RED).size(DEFENDER_ROWS.length)
                .unarmed()
                .inFile(DEFENDER_X, DEFENDER_ROWS[0], 0, 2)
                .stationary()
                .health(100_000f)
                .done();
        if (marksmen) {
            builder.unit(ANCHOR, Faction.DEFENDER, UnitType.MARINE_RED, DEFENDER_X, ANCHOR_Y,
                    spec -> spec.moveSpeed(0f).health(100_000f));
        }
        SceneWorld world = builder.build();
        TileRegistry registry = TileRegistry.installed();
        List<int[]> propCells = new ArrayList<>();
        if (cover == Cover.CRATES) {
            // A continuous row, so a defender who shifts along the line is still behind one.
            for (int y = DEFENDER_ROWS[0] - 1; y <= DEFENDER_ROWS[DEFENDER_ROWS.length - 1] + 1; y++) {
                world.sim().addDoodad(new Doodad(COVER_X - 1, y, registry.doodad("doodad.crate")));
                propCells.add(new int[] {COVER_X - 1, y});
            }
        }
        for (int row : DEFENDER_ROWS) {
            if (cover == Cover.SANDBAGS) {
                world.sim().addDoodad(new Doodad(COVER_X, row, registry.doodad("doodad.sandbag-straight-w")));
                propCells.add(new int[] {COVER_X, row});
            }
        }

        Trace trace = new Trace(cover, propCells, wallCells, world.members(MARINES));
        List<TickObserver> observers = new ArrayList<>();
        observers.add(trace);
        if (marksmen) {
            long anchor = world.unit(ANCHOR);
            long[] shooters = world.members(MARINES);
            for (long shooter : shooters) {
                world.sim().world().setSecondaryAmmo(shooter, 0);
            }
            observers.add((sim, tick) -> {
                if (tick == 0 || tick % MARKSMAN_INTERVAL_TICKS != 0 || !sim.getRoster().isAliveById(anchor)) return;
                for (long shooter : shooters) {
                    if (!sim.getRoster().isAliveById(shooter)) continue;
                    sim.world().setSecondaryAmmo(shooter, 1);
                    sim.fireSecondary(shooter, anchor);
                    trace.amrRounds++;
                }
            });
        }
        SceneRun.of(world).observe(observers.toArray(new TickObserver[0]))
                .frames(frames, loopId, FRAME_EVERY_TICKS,
                        tick -> "small-arms wear / " + loopId + "  t=" + tick + "  " + trace.caption())
                .run(TICKS);
        return trace;
    }

    private static boolean isDefenderRow(int y) {
        for (int row : DEFENDER_ROWS) if (row == y) return true;
        return false;
    }

    /** A structural wall cell; a window is the same masonry, seen and shot through. */
    private static void masonry(NavigationGrid grid, CellTopology topology, int x, int y, boolean window) {
        grid.setWalkable(x, y, false);
        grid.setSeeThrough(x, y, window);
        grid.setWallHp(x, y, MASONRY_HP);
        topology.setWall(x, y, true);
        topology.setWindow(x, y, window);
    }

    // ---- instrument ----

    private static final class Trace implements TickObserver {
        final Cover cover;
        final List<int[]> propCells;
        final List<int[]> wallCells;
        final Map<BallisticResolver.StopKind, Integer> stops = new LinkedHashMap<>();
        int amrRounds;
        int lastShotTick = -1;
        final long[] marines;
        int marinesAlive;
        int firstDamagedTick = -1;
        int firstDestroyedTick = -1;
        int lastDestroyedTick = -1;
        int intact;
        int damaged;
        int destroyed;
        int wallHpLost;
        int wallBreached;
        int firstBreachTick = -1;
        long structureRemoved;
        long strikesWorn;

        Trace(Cover cover, List<int[]> propCells, List<int[]> wallCells, long[] marines) {
            this.cover = cover;
            this.marines = marines;
            this.propCells = propCells;
            this.wallCells = wallCells;
            for (BallisticResolver.StopKind kind : BallisticResolver.StopKind.values()) stops.put(kind, 0);
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            for (var shot : sim.getShotsThisFrame()) {
                if (shot.shooterFaction != Faction.MARINE || shot.stopKind == null) continue;
                stops.merge(shot.stopKind, 1, Integer::sum);
                lastShotTick = tick;
            }
            marinesAlive = 0;
            for (long marine : marines) if (sim.getRoster().isAliveById(marine)) marinesAlive++;
            intact = 0;
            damaged = 0;
            int nowDestroyed = 0;
            for (Doodad prop : sim.getDoodads()) {
                if (!isCoverCell(prop.cellX, prop.cellY)) continue;
                String id = prop.definitionId();
                if (id == null) continue;
                if (id.endsWith("-destroyed") || id.contains("destroyed-")) nowDestroyed++;
                else if (id.endsWith("-damaged") || id.contains("damaged-")) damaged++;
                else intact++;
            }
            if (damaged + nowDestroyed > 0 && firstDamagedTick < 0) firstDamagedTick = tick;
            if (nowDestroyed > 0 && firstDestroyedTick < 0) firstDestroyedTick = tick;
            if (nowDestroyed > destroyed) lastDestroyedTick = tick;
            destroyed = nowDestroyed;
            NavigationGrid grid = sim.getGrid();
            int lost = 0;
            int breached = 0;
            for (int[] cell : wallCells) {
                if (grid.isWalkable(cell[0], cell[1])) {
                    breached++;
                    lost += MASONRY_HP;
                } else {
                    lost += MASONRY_HP - grid.getWallHp(cell[0], cell[1]);
                }
            }
            wallHpLost = lost;
            if (breached > 0 && firstBreachTick < 0) firstBreachTick = tick;
            wallBreached = breached;
            structureRemoved = sim.environmentWear().structureRemoved();
            strikesWorn = sim.environmentWear().strikesWorn();
        }

        boolean isCoverCell(int x, int y) {
            for (int[] cell : propCells) if (cell[0] == x && cell[1] == y) return true;
            return false;
        }

        int stopped(BallisticResolver.StopKind kind) {
            return stops.get(kind);
        }

        String caption() {
            if (cover == Cover.WALL) {
                return String.format(Locale.ROOT, "masonry lost %d hp, %d breached", wallHpLost, wallBreached);
            }
            return String.format(Locale.ROOT, "standing %d  damaged %d  gone %d", intact, damaged, destroyed);
        }

        Map<String, Number> metrics() {
            Map<String, Number> m = new LinkedHashMap<>();
            m.put("intact", intact);
            m.put("damaged", damaged);
            m.put("destroyed", destroyed);
            m.put("firstDamagedTick", firstDamagedTick);
            m.put("firstDestroyedTick", firstDestroyedTick);
            m.put("lastDestroyedTick", lastDestroyedTick);
            m.put("wallHpLost", wallHpLost);
            m.put("wallCellsBreached", wallBreached);
            m.put("firstBreachTick", firstBreachTick);
            m.put("structureRemoved", structureRemoved);
            m.put("roundsThatWore", strikesWorn);
            m.put("amrRounds", amrRounds);
            m.put("lastShotTick", lastShotTick);
            m.put("marinesAlive", marinesAlive);
            for (Map.Entry<BallisticResolver.StopKind, Integer> stop : stops.entrySet()) {
                m.put("stops." + stop.getKey().name(), stop.getValue());
            }
            return m;
        }
    }

    // ---- verdicts ----

    private static String seconds(int tick) {
        return tick < 0 ? "never" : String.format(Locale.ROOT, "%.1fs", tick / 30f);
    }

    private SceneReport reportCrates(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        int total = t.propCells.size();
        verdicts.add(Verdict.of("crates-caught-rounds", t.stopped(BallisticResolver.StopKind.DOODAD_BLOCK) > 0,
                t.stopped(BallisticResolver.StopKind.DOODAD_BLOCK) + " rounds stopped in the crates"
                        + " (wanted some, or nothing was shooting through them)"));
        verdicts.add(Verdict.of("crates-shot-away", t.firstDestroyedTick >= 0
                        && t.firstDestroyedTick <= FIRST_CRATE_BAR_TICKS && t.destroyed >= CRATES_BAR,
                t.destroyed + " of " + total + " crates destroyed, the first at " + seconds(t.firstDestroyedTick)
                        + " and the last at " + seconds(t.lastDestroyedTick) + " (wanted the first within "
                        + seconds(FIRST_CRATE_BAR_TICKS) + " and at least " + CRATES_BAR + " by " + seconds(TICKS)
                        + "; only a crate in front of a man being shot at is in anybody's lane)"));
        return new SceneReport(id(), "crates", TICKS, verdicts, t.metrics());
    }

    private SceneReport reportSandbags(Trace t, Trace crates) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("sandbags-wear", t.structureRemoved > 0 && t.firstDamagedTick >= 0,
                t.structureRemoved + " structure taken off the sandbags by " + t.strikesWorn
                        + " rounds, first damaged at " + seconds(t.firstDamagedTick) + " (wanted some)"));
        float perRound = t.strikesWorn > 0 ? t.structureRemoved / (float) t.strikesWorn : 0f;
        float cratePerRound = crates.strikesWorn > 0 ? crates.structureRemoved / (float) crates.strikesWorn : 0f;
        boolean laterFirst = crates.firstDestroyedTick >= 0
                && (t.firstDestroyedTick < 0 || t.firstDestroyedTick > crates.firstDestroyedTick);
        verdicts.add(Verdict.of("sandbags-outlast-crates", perRound > 0f && 2f * perRound < cratePerRound && laterFirst,
                String.format(Locale.ROOT, "%.1f structure a round against the crates' %.1f; sandbags: %d of %d gone,"
                                + " first at %s; crates: %d gone, first at %s (wanted under half the wear a round"
                                + " and the first sandbag to fall later)",
                        perRound, cratePerRound, t.destroyed, t.propCells.size(), seconds(t.firstDestroyedTick),
                        crates.destroyed, seconds(crates.firstDestroyedTick))));
        return new SceneReport(id(), "sandbags", TICKS, verdicts, t.metrics());
    }

    private SceneReport reportWallRifles(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        int inMasonry = t.stopped(BallisticResolver.StopKind.WALL) + t.stopped(BallisticResolver.StopKind.COVER_CLIP);
        verdicts.add(Verdict.of("rifles-hit-the-wall", inMasonry > 0,
                inMasonry + " rifle rounds stopped in the masonry (" + t.stopped(BallisticResolver.StopKind.WALL)
                        + " against it, " + t.stopped(BallisticResolver.StopKind.COVER_CLIP)
                        + " caught by its window sills; wanted some, or the wall was never tested)"));
        verdicts.add(Verdict.of("wall-stands", t.wallHpLost == 0 && t.strikesWorn == 0,
                t.wallHpLost + " masonry structure lost to rifle fire, " + t.strikesWorn
                        + " rounds that wore anything (wanted none)"));
        return new SceneReport(id(), "wall-rifles", TICKS, verdicts, t.metrics());
    }

    private SceneReport reportWallAmr(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("wall-yields", t.wallBreached > 0,
                t.wallBreached + " masonry cells breached, the first at " + seconds(t.firstBreachTick) + ", by "
                        + t.amrRounds + " anti-materiel rounds (" + t.wallHpLost
                        + " structure lost in all; wanted a breach)"));
        return new SceneReport(id(), "wall-anti-materiel", TICKS, verdicts, t.metrics());
    }
}
