package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.decision.TacticalLinker;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;

/**
 * Step 3d — link tactical nodes. Runs once after every node is emitted (from
 * compound fillers + fortress wall stamping); geometric rules wire up
 * OVERWATCHES, SUPPLIES, FALLBACK_TO, GUARDS. Always runs even in legacy
 * non-conquest mode — the compound fillers may still have contributed
 * BARRACKS/COMMAND/ARMORY nodes.
 *
 * <p>Builds the {@link TacticalMap} over {@code ctx.tactical} and binds it
 * under {@link BspKeys#TACTICAL_MAP} for {@code MapResult}.
 */
public final class TacticalLinkStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        TacticalMap tacticalMap = new TacticalMap(ctx.tactical);
        TacticalLinker.link(tacticalMap);
        ctx.put(BspKeys.TACTICAL_MAP, tacticalMap);
    }
}
