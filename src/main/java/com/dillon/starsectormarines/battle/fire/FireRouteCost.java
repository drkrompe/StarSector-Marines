package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.nav.RouteCostField;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Fire as route cost (`fire-nouns.md`, Pathing): a burning cell costs up to
 * {@link #BURNING_MAX} times its ground and a cell beside one {@link #BESIDE}
 * times, folded into each side's published costing so routes go round a fire
 * where there is a way round and through it where there is not.
 *
 * <p>Rebuilt on the simulation thread at most every {@link #INTERVAL_TICKS},
 * only while the fire has changed or a side's own costing has, and published
 * whole: readers on route workers see one immutable map per publish. While
 * nothing burns it publishes nothing, so the sides' costings pass through
 * untouched and keep their identity in the shared route caches.
 *
 * <p>{@code -Dbattle.pathfinding.fireRouteCost=false} is the control.
 */
public final class FireRouteCost {

    public static final String PROPERTY = "battle.pathfinding.fireRouteCost";
    public static final int INTERVAL_TICKS = 15;
    public static final float BURNING_MAX = 10f;
    public static final float BESIDE = 3f;

    private final boolean enabled = !"false".equalsIgnoreCase(System.getProperty(PROPERTY));
    private volatile Map<Faction, RouteCostField> published = Map.of();
    private RouteCostField hazard;
    private final Map<Faction, Long> builtFrom = new EnumMap<>(Faction.class);
    private long builtFireRevision = -1;
    private int lastBuild = Integer.MIN_VALUE;

    /** The costing a side's routes should read: its own, with the fire folded in while anything burns. */
    public RouteCostField forSide(Faction faction, RouteCostField own) {
        RouteCostField folded = published.get(faction);
        return folded != null ? folded : own;
    }

    /**
     * The fire alone as multipliers, for movers that read no side's costing (the
     * swarm's flow fields); {@code null} while nothing burns. A new field only
     * when the fire has changed, so its revision says exactly that.
     */
    public RouteCostField hazard() {
        return hazard;
    }

    public void tick(int simTick, FireField field, Function<Faction, RouteCostField> own) {
        if (!enabled || (long) simTick - lastBuild < INTERVAL_TICKS) return;
        if (field.burningCount() == 0) {
            if (!published.isEmpty()) published = Map.of();
            hazard = null;
            builtFireRevision = -1;
            builtFrom.clear();
            return;
        }
        boolean fireChanged = field.revision() != builtFireRevision;
        boolean sideChanged = false;
        for (Faction f : Faction.values()) {
            RouteCostField o = own.apply(f);
            long rev = o == null ? 0L : o.revision();
            Long had = builtFrom.get(f);
            if (had == null || had != rev) { sideChanged = true; break; }
        }
        if (!fireChanged && !sideChanged) return;
        lastBuild = simTick;
        builtFireRevision = field.revision();
        if (fireChanged || hazard == null) hazard = new RouteCostField(hazard(field), RouteCostField.nextRevision());
        RouteCostField fireOnly = hazard;
        Map<Faction, RouteCostField> out = new EnumMap<>(Faction.class);
        for (Faction f : Faction.values()) {
            RouteCostField o = own.apply(f);
            builtFrom.put(f, o == null ? 0L : o.revision());
            if (o == null) { out.put(f, fireOnly); continue; }
            float[] combined = new float[field.width() * field.height()];
            for (int i = 0; i < combined.length; i++) combined[i] = fireOnly.costAt(i) * o.costAt(i);
            out.put(f, new RouteCostField(combined, RouteCostField.nextRevision()));
        }
        published = out;
    }

    static float[] hazard(FireField field) {
        int w = field.width(), h = field.height();
        float[] cost = new float[w * h];
        Arrays.fill(cost, 1f);
        for (int i = 0, n = field.burningCount(); i < n; i++) {
            int idx = field.burningCell(i);
            int x = idx % w, y = idx / w;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int n2 = ny * w + nx;
                    if (cost[n2] < BESIDE) cost[n2] = BESIDE;
                }
            }
        }
        for (int i = 0, n = field.burningCount(); i < n; i++) {
            int idx = field.burningCell(i);
            cost[idx] = Math.max(cost[idx], 1f + (BURNING_MAX - 1f) * field.intensityAt(idx) / 255f);
        }
        return cost;
    }
}
