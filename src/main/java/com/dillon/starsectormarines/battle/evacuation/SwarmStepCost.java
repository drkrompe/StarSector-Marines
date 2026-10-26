package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.RouteCostField;
import com.dillon.starsectormarines.battle.nav.SwarmFlowFields;

import java.util.Arrays;
import java.util.BitSet;

/**
 * What a step costs the swarm's flow fields ({@link SwarmFlowFields}, Step cost): the fire's hazard, times
 * {@link #STANDING_HULL} on every cell a stopped hull keeps routes off. A hull stamps no walkability, so a field that
 * did not see it led bodies into its side, where the hull pushes them back as fast as they walk in and the stall
 * watch replans them into the same cells. A costed hull is gone round where there is a way round and still pressed
 * through where it plugs the only one, so a vehicle parked across a street holds the swarm there while it stands.
 *
 * <p>Serial: the stopped hulls' cells are marked each tick, then {@link #publish} hands over the costing. The hull
 * layer is rebuilt, with a new revision, only when those cells change, so a parked column does not keep the fields
 * rebuilding.
 */
public final class SwarmStepCost {

    /** The multiplier on a stopped hull's cell: the most a field pays, as for a cell at the heart of a fire. */
    public static final float STANDING_HULL = SwarmFlowFields.MAX_STEP_MULTIPLIER;

    private final int width, height;
    private final BitSet marked = new BitSet();
    private final BitSet built = new BitSet();
    private RouteCostField hulls, fire, combined;

    public SwarmStepCost(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** Marks a cell a stopped hull keeps routes off, for the next {@link #publish}. */
    public void standingHull(int x, int y) {
        if (x >= 0 && y >= 0 && x < width && y < height) marked.set(y * width + x);
    }

    /**
     * The costing the fields read from now on: {@code fireHazard} (null while nothing burns) times the stopped
     * hulls marked since the last call, or null when neither costs anything. Starts the next tick's marks.
     */
    public RouteCostField publish(RouteCostField fireHazard) {
        if (!marked.equals(built)) {
            built.clear();
            built.or(marked);
            hulls = built.isEmpty() ? null : hullLayer();
            combined = null;
        }
        marked.clear();
        if (hulls == null) return fireHazard;
        if (fireHazard == null) return hulls;
        if (combined == null || fire != fireHazard) {
            float[] product = new float[width * height];
            for (int i = 0; i < product.length; i++) product[i] = fireHazard.costAt(i) * hulls.costAt(i);
            combined = new RouteCostField(product, RouteCostField.nextRevision());
            fire = fireHazard;
        }
        return combined;
    }

    private RouteCostField hullLayer() {
        float[] cost = new float[width * height];
        Arrays.fill(cost, 1f);
        for (int i = built.nextSetBit(0); i >= 0; i = built.nextSetBit(i + 1)) cost[i] = STANDING_HULL;
        return new RouteCostField(cost, RouteCostField.nextRevision());
    }
}
