package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;

/**
 * Station partition — run the textbook BSP primitive over the whole rect to
 * carve it into {@link com.dillon.starsectormarines.battle.world.gen.BlockLeaf
 * leaves}. Each leaf becomes one room ({@link RoomCarveStage}); the road strips
 * BSP reserves between leaves are simply left solid (they become the
 * inter-room hull wall the corridor pass punches through).
 *
 * <p>This is the deliberate reuse the corridors-first-class design calls out:
 * the same {@link Bsp#partition} that drives the city, with the station recipe
 * just <em>ignoring</em> the {@code roadCells} mask instead of painting it
 * walkable. No trunk pre-pass (stations have no arterials), so this calls the
 * trunk-less {@link Bsp#partition(int, int, java.util.Random)} convenience
 * wrapper rather than {@code partitionRect} over sub-rects.
 *
 * <p>Binds the {@link Bsp.Partition} under {@link BspKeys#PARTITION}.
 */
public final class StationPartitionStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = Bsp.partition(ctx.width, ctx.height, ctx.rng);
        ctx.put(BspKeys.PARTITION, partition);
    }
}
