package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.creep.CreepGrowth;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The swarm's answer to fortification (`colony-breach-nouns.md`, Ground colony):
 * a static growth that roots only on creep, within reach of a hostile emplacement
 * the creep has come up against, and drives spines up from below into whatever
 * stands in its short reach, wrecking emplacements and the walls round its
 * target. Mobile strains never gain ranged attacks; this is how the hive deals
 * with a gun it cannot rush.
 *
 * <ul>
 *   <li><b>Where.</b> Living, open creep with creep standing {@link #INTERIOR}
 *   cell round it, inside {@link #REACH_SHARE} of the spines' range of a
 *   hostile emplacement, inside the bore's own creep reach, and at least
 *   {@link #SPACING} cells from every bore, colony and ground colony.</li>
 *   <li><b>When.</b> A chosen cell must stay good for {@link #HOLD_SECONDS}.</li>
 *   <li><b>How many.</b> {@link #capFor} living ground colonies per bore.</li>
 *   <li><b>Withering.</b> A ground colony dies with its bore, and as soon as
 *   the creep under it is no longer living.</li>
 * </ul>
 *
 * Runs once per tick after the creep has spread, looking every
 * {@link #CHECK_TICKS}; deterministic. {@code -Dbattle.swarm.groundColonies=false}
 * is the control: none root.
 */
public final class GroundColonies {

    /** Control-run switch; ground colonies root unless it reads {@code false}. */
    public static final String PROPERTY = "battle.swarm.groundColonies";
    /** The structure every ground colony is. */
    public static final String STRUCTURE = "structure.ground-colony";
    /** Cells of standing creep a ground colony's cell needs on every side. */
    public static final int INTERIOR = 1;
    /** Cells from every bore, colony and other ground colony. */
    public static final float SPACING = 3f;
    /** Share of the spines' range a target emplacement must lie within, so it stays in reach. */
    public static final float REACH_SHARE = 0.8f;
    /** Seconds a chosen cell must stay good before a colony roots on it. */
    public static final float HOLD_SECONDS = 15f;
    /** Ticks between looks. */
    public static final int CHECK_TICKS = 30;
    static final float BODY_CLEARANCE = 0.9f;
    private static final float WITHER_DAMAGE = 1_000_000f;

    private record Pending(int x, int y, int sinceTick) { }

    private final Map<Long, Pending> pending = new HashMap<>();
    private final Map<Long, List<Long>> grown = new HashMap<>();
    private final boolean enabled = !"false".equalsIgnoreCase(System.getProperty(PROPERTY));

    /** Living ground colonies a bore of {@code tier} may hold: 1 at fissure, 2 at bore, 3 at maw. */
    public static int capFor(BoreTier tier) {
        return switch (tier) {
            case FISSURE -> 1;
            case BORE -> 2;
            case MAW -> 3;
        };
    }

    /** The spines' range, in cells. */
    public static float reach() {
        return TurretCatalogRegistry.requireStructure(STRUCTURE).mount.weapon.range;
    }

    /** Whether a cell can hold a ground colony: the creep and ground half of the rule. */
    public interface Ground {
        boolean creeped(int x, int y);

        boolean open(int x, int y);
    }

    /**
     * The cell a new ground colony of a bore at {@code (boreX, boreY)} roots on: for the target nearest the bore
     * ({@code {x, y}} cell centres), the good cell nearest that target and within {@code reach} of it, inside
     * {@code boreReach} of the bore and {@link #SPACING} from every anchor. Targets with no good cell are passed over.
     * Ties go to the lower row, then column, so the choice is deterministic. Returns {@code {x, y}}, or {@code null}.
     */
    public static int[] choose(Ground ground, int boreX, int boreY, float boreReach, List<float[]> targets,
                               float reach, List<float[]> anchors) {
        List<float[]> order = new ArrayList<>(targets);
        float bx = boreX + 0.5f, by = boreY + 0.5f;
        order.sort((a, b) -> Float.compare(dist2(a[0], a[1], bx, by), dist2(b[0], b[1], bx, by)));
        int r = (int) Math.ceil(reach);
        for (float[] t : order) {
            if (dist2(t[0], t[1], bx, by) > (boreReach + reach) * (boreReach + reach)) continue;
            int tx = (int) Math.floor(t[0]), ty = (int) Math.floor(t[1]);
            int[] best = null;
            float bestD = Float.MAX_VALUE;
            for (int y = ty - r; y <= ty + r; y++) {
                for (int x = tx - r; x <= tx + r; x++) {
                    float d = dist2(x + 0.5f, y + 0.5f, t[0], t[1]);
                    if (d > reach * reach || d >= bestD) continue;
                    if (dist2(x + 0.5f, y + 0.5f, bx, by) > boreReach * boreReach) continue;
                    if (!good(ground, x, y, anchors)) continue;
                    bestD = d;
                    best = new int[]{x, y};
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    static boolean good(Ground ground, int x, int y, List<float[]> anchors) {
        if (!ground.open(x, y)) return false;
        for (int dy = -INTERIOR; dy <= INTERIOR; dy++) {
            for (int dx = -INTERIOR; dx <= INTERIOR; dx++) {
                if (!ground.creeped(x + dx, y + dy)) return false;
            }
        }
        for (float[] a : anchors) {
            if (dist2(a[0], a[1], x + 0.5f, y + 0.5f) < SPACING * SPACING) return false;
        }
        return true;
    }

    /** Every ground colony this system has rooted, living or not, by bore entity id. */
    public List<Long> of(long boreId) {
        return grown.getOrDefault(boreId, List.of());
    }

    /** Once per simulation tick, after the creep has spread. No-op while there is no creep. */
    public void tick(BattleSimulation sim) {
        if (!enabled) return;
        CreepField field = sim.creepFieldIfPresent();
        if (field == null) return;
        int tick = sim.getSimTickIndex();
        if (tick % CHECK_TICKS != 0) return;
        SwarmBores bores = sim.swarmBores();
        wither(sim, bores, field);
        NavigationGrid grid = sim.getGrid();
        Ground ground = new Ground() {
            @Override public boolean creeped(int x, int y) { return field.isCreeped(x, y); }

            @Override public boolean open(int x, int y) {
                return grid.inBounds(x, y) && grid.isWalkable(x, y)
                        && !sim.isInsideRescueOpeningProtectedZone(x, y);
            }
        };
        List<float[]> targets = null;
        float reach = reach() * REACH_SHARE;
        int holdTicks = Math.round(HOLD_SECONDS / BattleSimulation.TICK_DT);
        for (SwarmBore bore : bores.bores()) {
            if (bore.collapsed() || living(sim, of(bore.entityId())) >= capFor(bore.tier())) {
                pending.remove(bore.entityId());
                continue;
            }
            if (targets == null) targets = hostileEmplacements(sim);
            if (targets.isEmpty()) {
                pending.remove(bore.entityId());
                continue;
            }
            List<float[]> anchors = anchors(sim, bores);
            Pending held = pending.get(bore.entityId());
            if (held != null && good(ground, held.x, held.y, anchors)
                    && !SwarmBores.nearBody(sim, held.x, held.y, BODY_CLEARANCE)) {
                if (tick - held.sinceTick >= holdTicks) {
                    root(sim, bore, held.x, held.y);
                    pending.remove(bore.entityId());
                }
                continue;
            }
            float boreReach = CreepGrowth.forTier(bore.tier().ordinal()).maxReachCells();
            int[] cell = choose(ground, bore.x(), bore.y(), boreReach, targets, reach, anchors);
            if (cell == null || SwarmBores.nearBody(sim, cell[0], cell[1], BODY_CLEARANCE)) {
                pending.remove(bore.entityId());
            } else {
                pending.put(bore.entityId(), new Pending(cell[0], cell[1], tick));
            }
        }
    }

    private void root(BattleSimulation sim, SwarmBore bore, int x, int y) {
        List<Long> list = grown.computeIfAbsent(bore.entityId(), k -> new ArrayList<>());
        String name = (bore.mother() ? "Mother Bore" : "Bore " + bore.number()) + " Ground Colony " + (list.size() + 1);
        // A gun, not a body: armed and sighted from its weapon, as a map turret is.
        list.add(sim.spawn(MapTurret.create(name, Faction.DEFENDER, STRUCTURE, x, y)));
    }

    /** Kills every living ground colony whose bore has collapsed or whose creep has died under it. */
    private void wither(BattleSimulation sim, SwarmBores bores, CreepField field) {
        for (SwarmBore bore : bores.bores()) {
            for (long id : of(bore.entityId())) {
                if (sim.resolveUnit(id) == 0L) continue;
                if (bore.collapsed() || !field.isCreeped(sim.world().cellX(id), sim.world().cellY(id))) {
                    sim.applyDamage(id, WITHER_DAMAGE, WITHER_DAMAGE);
                }
            }
        }
    }

    /** Cell centres of every living turret structure hostile to the swarm: the guns the hive grows against. */
    private static List<float[]> hostileEmplacements(BattleSimulation sim) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (!sim.world().isAlive(id) || !sim.turretState().isTurret(id)) continue;
            if (!Faction.DEFENDER.hostileTo(sim.identity().faction(id))) continue;
            out.add(new float[]{sim.world().x(id), sim.world().y(id)});
        }
        return out;
    }

    private List<float[]> anchors(BattleSimulation sim, SwarmBores bores) {
        List<float[]> anchors = new ArrayList<>();
        for (SwarmBore bore : bores.bores()) {
            if (bore.collapsed()) continue;
            anchors.add(new float[]{bore.x() + 0.5f, bore.y() + 0.5f});
            addLiving(sim, bore.colonies(), anchors);
            addLiving(sim, bore.outposts(), anchors);
            addLiving(sim, of(bore.entityId()), anchors);
        }
        return anchors;
    }

    private static void addLiving(BattleSimulation sim, List<Long> ids, List<float[]> anchors) {
        for (long id : ids) {
            if (sim.resolveUnit(id) != 0L) anchors.add(new float[]{sim.world().x(id), sim.world().y(id)});
        }
    }

    private static int living(BattleSimulation sim, List<Long> ids) {
        int n = 0;
        for (long id : ids) if (sim.resolveUnit(id) != 0L) n++;
        return n;
    }

    private static float dist2(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return dx * dx + dy * dy;
    }
}
