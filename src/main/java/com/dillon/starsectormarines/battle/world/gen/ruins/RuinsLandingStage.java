package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;

/**
 * Publishes the delve's landing on the map's own terms after the spawn anchors are chosen: the landing ground's
 * arrival areas become the map's landing areas and berths, the company spawns on the landing ground, and the
 * opposing side's anchor stands at the vault's approach. A ruin has no road out and no working pad
 * ({@code SettlementLink.NONE}), so these are the only berths on the map.
 */
public final class RuinsLandingStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        RuinsDelve delve = ctx.get(RuinsKeys.DELVE);
        if (delve == null) throw new IllegalStateException("RuinsLandingStage requires a laid delve");
        ctx.landingPads.clear();
        ctx.landingAreas.clear();
        for (LandingArea area : delve.landing()) {
            ctx.landingAreas.add(area);
            ctx.landingPads.addAll(area.berths());
        }
        RuinsDelve.Area ground = delve.landingGround();
        ctx.put(BspKeys.MARINE_SPAWN, new int[]{ground.left() + RuinsDelveStage.BERTH_COLUMN + 4, ground.centreY()});
        RuinsDelve.Cell approach = delve.vault().approach();
        ctx.put(BspKeys.DEFENDER_SPAWN, new int[]{approach.x(), approach.y()});
    }
}
