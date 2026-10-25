package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.creep.CreepGrowth;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * Creep carries the umbrella outward (`colony-breach-nouns.md`, Spore colony):
 * a living bore seeds a spore colony of its own grade on its creep, away from
 * the mouth, once the creep has stood on ground far enough from every colony
 * and bore already standing that the new colony's reach only just overlaps
 * theirs. These are the bore's <b>outposts</b>; they wither with it like the
 * colonies at its mouth.
 *
 * <ul>
 *   <li><b>Spacing.</b> {@link #SPACING_PER_REACH} times the bore's grade of
 *   spore reach, from the bore, every living bore and every living spore
 *   colony on the map.</li>
 *   <li><b>Ground.</b> Living creep, open and walkable, with creep standing
 *   {@link #INTERIOR} cells round it, so an outpost roots inside the growth
 *   and never on its ragged edge, and clear of bodies.</li>
 *   <li><b>Time.</b> A chosen cell must stay good for {@link #HOLD_SECONDS}
 *   before anything roots there. Through the hold the bore's root digs out to
 *   it and thickens ({@link #rootProgress}); the outpost rises as it comes.</li>
 *   <li><b>Cap.</b> {@link #capFor} living outposts per bore by tier; a dead
 *   outpost frees its place, so the creep regrows one elsewhere: where one
 *   fell counts as an anchor for its bore's next ({@link #fallen}), so it never
 *   roots again on the cell a company just cleared.</li>
 * </ul>
 *
 * Runs once per simulation tick after the creep has spread, looking every
 * {@link #CHECK_TICKS}; deterministic in the bores' seed.
 * {@code -Dbattle.swarm.creepOutposts=false} is the control: no outposts.
 */
public final class CreepOutposts {

    /** Control-run switch; outposts root unless it reads {@code false}. */
    public static final String PROPERTY = "battle.swarm.creepOutposts";

    /** Outpost spacing as a share of the grade's spore reach: umbrellas that just overlap. */
    public static final float SPACING_PER_REACH = 1.5f;
    /** Cells of standing creep an outpost's cell needs on every side. */
    public static final int INTERIOR = 2;
    /** Seconds a chosen cell must stay good before a colony roots on it. */
    public static final float HOLD_SECONDS = 20f;
    /** Ticks between looks; the creep front moves well under a cell in that time. */
    public static final int CHECK_TICKS = 30;
    /** Directions tried on each ring, turned by a seeded per-bore angle. */
    static final int DIRECTIONS = 24;
    /** Ring step outward, in cells. */
    static final float RING_STEP = 2f;
    /** Body clearance at an outpost's cell, as at the mouth's growth sites. */
    static final float BODY_CLEARANCE = 0.9f;

    private record Pending(int x, int y, int sinceTick) { }

    private final long seed;
    private final Map<Long, Pending> pending = new HashMap<>();
    /** The cell centre each outpost rooted on, by its id: where it fell, once it is dead. */
    private final Map<Long, float[]> rootedAt = new HashMap<>();
    private final boolean enabled = !"false".equalsIgnoreCase(System.getProperty(PROPERTY));

    public CreepOutposts(long seed) {
        this.seed = seed;
    }

    /** The cell a bore's next outpost waits to root on, {@code {x, y}}, or {@code null}: where a skitterer tends. */
    public int[] pendingCell(long boreId) {
        Pending held = pending.get(boreId);
        return held == null ? null : new int[]{held.x, held.y};
    }

    /**
     * How far the root to a bore's pending outpost has come at {@code tick}, 0 to 1, or -1 with none pending: it digs
     * out from the bore as the cell is first held and has come, thickened, as the hold is done and the outpost rises
     * (`colony-breach-nouns.md`, Nest, Roots grow). A tended hold brings it on as much sooner.
     */
    public float rootProgress(long boreId, int tick) {
        Pending held = pending.get(boreId);
        if (held == null) return -1f;
        float hold = HOLD_SECONDS / BattleSimulation.TICK_DT;
        return Math.max(0f, Math.min(1f, (tick - held.sinceTick) / hold));
    }

    /** A skitterer stood on a bore's pending outpost for {@code ticks}: its hold counts that much more. */
    public void tend(long boreId, int ticks) {
        Pending held = pending.get(boreId);
        if (held != null) pending.put(boreId, new Pending(held.x, held.y, held.sinceTick - ticks));
    }

    /** Living outposts a bore of {@code tier} may hold: 1 at fissure, 2 at bore, 4 at maw. */
    public static int capFor(BoreTier tier) {
        return switch (tier) {
            case FISSURE -> 1;
            case BORE -> 2;
            case MAW -> 4;
        };
    }

    /** Outpost spacing, in cells, for a bore of {@code tier}: its grade's spore reach times {@link #SPACING_PER_REACH}. */
    public static float spacingFor(BoreTier tier) {
        return SPACING_PER_REACH * TurretCatalogRegistry.requireStructure(tier.sporeColonyStructure()).mount.weapon.range;
    }

    /** Whether a cell can hold an outpost, the creep and ground half of the rule. */
    public interface Ground {
        boolean creeped(int x, int y);

        boolean open(int x, int y);
    }

    /**
     * The nearest good cell for a new outpost of a bore at {@code (boreX, boreY)}:
     * rings outward from {@code spacing} to {@code maxRadius}, {@link #DIRECTIONS}
     * a ring starting at {@code turn} (a share of a lap), the first cell that is
     * open, inside the creep by {@link #INTERIOR} and at least {@code spacing}
     * from every anchor ({@code {x, y}} cell centres of standing bores and
     * colonies). Returns {@code {x, y}}, or {@code null}.
     */
    public static int[] choose(Ground ground, int boreX, int boreY, float spacing, float maxRadius,
                               float turn, List<float[]> anchors) {
        for (float r = spacing; r <= maxRadius; r += RING_STEP) {
            for (int k = 0; k < DIRECTIONS; k++) {
                double angle = 2.0 * Math.PI * (turn + k / (double) DIRECTIONS);
                int x = (int) Math.floor(boreX + 0.5 + Math.cos(angle) * r);
                int y = (int) Math.floor(boreY + 0.5 + Math.sin(angle) * r);
                if (good(ground, x, y, spacing, anchors)) return new int[]{x, y};
            }
        }
        return null;
    }

    static boolean good(Ground ground, int x, int y, float spacing, List<float[]> anchors) {
        if (!ground.open(x, y)) return false;
        for (int dy = -INTERIOR; dy <= INTERIOR; dy++) {
            for (int dx = -INTERIOR; dx <= INTERIOR; dx++) {
                if (!ground.creeped(x + dx, y + dy)) return false;
            }
        }
        float cx = x + 0.5f, cy = y + 0.5f;
        for (float[] anchor : anchors) {
            float ax = anchor[0] - cx, ay = anchor[1] - cy;
            if (ax * ax + ay * ay < spacing * spacing) return false;
        }
        return true;
    }

    /** Once per simulation tick, after the creep has spread. No-op while there is no creep. */
    public void tick(BattleSimulation sim) {
        if (!enabled) return;
        CreepField field = sim.creepFieldIfPresent();
        if (field == null) return;
        int tick = sim.getSimTickIndex();
        if (tick % CHECK_TICKS != 0) return;
        SwarmBores bores = sim.swarmBores();
        NavigationGrid grid = sim.getGrid();
        Ground ground = new Ground() {
            @Override public boolean creeped(int x, int y) { return field.isCreeped(x, y); }

            @Override public boolean open(int x, int y) {
                return grid.inBounds(x, y) && grid.isWalkable(x, y)
                        && !sim.isInsideRescueOpeningProtectedZone(x, y);
            }
        };
        int holdTicks = Math.round(HOLD_SECONDS / BattleSimulation.TICK_DT);
        for (SwarmBore bore : bores.bores()) {
            if (bore.collapsed()) {
                pending.remove(bore.entityId());
                continue;
            }
            if (living(sim, bore.outposts()) >= capFor(bore.tier())) {
                pending.remove(bore.entityId());
                continue;
            }
            float spacing = spacingFor(bore.tier());
            List<float[]> anchors = anchors(sim, bores);
            anchors.addAll(fallen(bore.outposts(), id -> sim.resolveUnit(id) != 0L, rootedAt));
            Pending held = pending.get(bore.entityId());
            if (held != null && good(ground, held.x, held.y, spacing, anchors)
                    && !SwarmBores.nearBody(sim, held.x, held.y, BODY_CLEARANCE)) {
                if (tick - held.sinceTick >= holdTicks) {
                    root(sim, bore, held.x, held.y);
                    pending.remove(bore.entityId());
                }
                continue;
            }
            float turn = SwarmBores.unit(SwarmBores.mix(seed ^ bore.entityId() * 0xD6E8FEB86659FD93L));
            int[] cell = choose(ground, bore.x(), bore.y(), spacing, CreepGrowth.forTier(bore.tier().ordinal()).maxReachCells(), turn, anchors);
            if (cell == null || SwarmBores.nearBody(sim, cell[0], cell[1], BODY_CLEARANCE)) {
                pending.remove(bore.entityId());
            } else {
                pending.put(bore.entityId(), new Pending(cell[0], cell[1], tick));
            }
        }
    }

    private void root(BattleSimulation sim, SwarmBore bore, int x, int y) {
        String name = (bore.mother() ? "Mother Bore" : "Bore " + bore.number())
                + " Spore Outpost " + (bore.outposts().size() + 1);
        long colony = sim.spawn(SwarmBores.bodySpec(name, bore.tier().sporeColonyStructure(), x, y));
        bore.outposted(colony);
        // It rises where its root has come, throwing the creep up round it.
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            sim.emitGroundDust(x + 0.5f, y + 0.5f, (float) Math.cos(a) * 0.9f, (float) Math.sin(a) * 0.9f, 0.45f,
                    EffectsService.DUST_WHEEL);
        }
        rootedAt.put(colony, new float[]{x + 0.5f, y + 0.5f});
    }

    /**
     * Where a bore's dead outposts stood, as anchors for its next: of {@code outposts}, each not {@code living}
     * that {@code rootedAt} has a cell for. Frozen 3, 2026-10-25: a company cleared an outpost and the creep rooted
     * the next on the same cell twenty seconds later, then again, a target it kept firing into.
     */
    static List<float[]> fallen(List<Long> outposts, LongPredicate living, Map<Long, float[]> rootedAt) {
        List<float[]> out = new ArrayList<>();
        for (long id : outposts) {
            float[] at = rootedAt.get(id);
            if (at != null && !living.test(id)) out.add(at);
        }
        return out;
    }

    private static int living(BattleSimulation sim, List<Long> ids) {
        int n = 0;
        for (long id : ids) if (sim.resolveUnit(id) != 0L) n++;
        return n;
    }

    /** Cell centres of every standing bore and every living spore colony, at the mouth or out on the creep. */
    private static List<float[]> anchors(BattleSimulation sim, SwarmBores bores) {
        List<float[]> anchors = new ArrayList<>();
        for (SwarmBore bore : bores.bores()) {
            if (bore.collapsed()) continue;
            anchors.add(new float[]{bore.x() + 0.5f, bore.y() + 0.5f});
            addLiving(sim, bore.colonies(), anchors);
            addLiving(sim, bore.outposts(), anchors);
            // A colony on its way counts as standing, so a held cell is never lost to it rising.
            for (SwarmBore.Growing g : bore.growing()) anchors.add(new float[]{g.x() + 0.5f, g.y() + 0.5f});
        }
        return anchors;
    }

    private static void addLiving(BattleSimulation sim, List<Long> ids, List<float[]> anchors) {
        for (long id : ids) {
            if (sim.resolveUnit(id) == 0L) continue;
            anchors.add(new float[]{sim.world().x(id), sim.world().y(id)});
        }
    }
}
