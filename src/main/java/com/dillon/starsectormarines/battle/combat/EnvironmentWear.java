package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;

/**
 * Wears the piece of the environment a round stopped in. A round's payload damage is
 * scaled by its penetration against the struck feature's {@link EnvironmentMaterial}
 * and spent through {@link MapEditor} — the same channel a blast's wall damage uses —
 * so a damaged or destroyed prop changes state, drops its cover and reopens navigation
 * exactly as it does under explosives. There is no second destruction pipeline here:
 * only the question of how much of one round reaches the feature.
 *
 * <p>{@link #wouldWear} is read-only and is asked from the parallel fire path, so a
 * round that cannot hurt what it hit never queues a record; {@link #apply} writes and
 * runs only in the serial shots phase.
 */
public final class EnvironmentWear {

    private final NavigationGrid grid;
    private final CellTopology topology;
    private final DoodadService doodads;
    private final MapEditor mapEditor;

    private long strikesWorn;
    private long structureRemoved;
    private long featuresBroken;

    public EnvironmentWear(NavigationGrid grid, CellTopology topology, DoodadService doodads,
                           MapEditor mapEditor) {
        this.grid = grid;
        this.topology = topology;
        this.doodads = doodads;
        this.mapEditor = mapEditor;
    }

    /**
     * What stands at a whole cell for a round to wear: the destructible prop a structural
     * hit there would reach, else a structural wall, else nothing. A prop no material is
     * listed for, a fixture, permanent terrain and open floor all answer null.
     */
    public EnvironmentMaterial materialAt(int x, int y) {
        if (!grid.inBounds(x, y)) return null;
        String propId = doodads == null ? null : doodads.damageablePropIdAt(x, y, topology);
        if (propId != null) return EnvironmentMaterial.forProp(propId);
        if (grid.isWalkable(x, y) || topology.isFixture(x, y)
                || grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID)
                || grid.getWallHp(x, y) <= 0) return null;
        return EnvironmentMaterial.MASONRY;
    }

    /** The material of whatever {@code feature} names now, or null. */
    public EnvironmentMaterial materialOf(StruckFeature feature) {
        if (feature.edge() == null) return materialAt(feature.cellX(), feature.cellY());
        if (!grid.inBounds(feature.cellX(), feature.cellY())) return null;
        SharedEdgeBarrier barrier = grid.getEdgeBarrier(feature.cellX(), feature.cellY(), feature.edge());
        return barrier == null ? null : EnvironmentMaterial.forBarrier(barrier.kind());
    }

    /** Whether a payload of this damage and penetration would remove any structure from the feature as it stands. Read-only. */
    public boolean wouldWear(StruckFeature feature, float damage, float penetration) {
        return wouldWear(feature, damage, penetration, false);
    }

    /** As above, for a round that may burn ({@link EnvironmentMaterial#structuralDamage(float, float, boolean)}). */
    public boolean wouldWear(StruckFeature feature, float damage, float penetration, boolean burn) {
        EnvironmentMaterial material = materialOf(feature);
        return material != null && material.structuralDamage(damage, penetration, burn) > 0;
    }

    /** Spends one arrived round on its feature. Returns whether that round broke it. */
    public boolean apply(StruckFeature feature, float damage, float penetration) {
        return apply(feature, damage, penetration, false);
    }

    /** As above, for a round that may burn. */
    public boolean apply(StruckFeature feature, float damage, float penetration, boolean burn) {
        EnvironmentMaterial material = materialOf(feature);
        if (material == null) return false;
        int amount = material.structuralDamage(damage, penetration, burn);
        if (amount <= 0) return false;
        strikesWorn++;
        structureRemoved += amount;
        boolean broke = feature.edge() == null
                ? mapEditor.damageWall(feature.cellX(), feature.cellY(), amount)
                : mapEditor.damageEdgeBarrier(feature.cellX(), feature.cellY(), feature.edge(), amount);
        if (broke) featuresBroken++;
        return broke;
    }

    /** The serial sink for {@link ShotService#tickStrikes}. */
    public void apply(ShotService.PendingStrike strike) {
        apply(strike.feature, strike.damage, strike.penetration, strike.burns);
    }

    /** Rounds that removed structure from the environment, this battle. */
    public long strikesWorn() { return strikesWorn; }

    /** Structure points those rounds removed. */
    public long structureRemoved() { return structureRemoved; }

    /** Features those rounds broke. */
    public long featuresBroken() { return featuresBroken; }
}
