package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckProfile;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipKeys;

/**
 * Ship spawn anchors — the two ends of the spine.
 *
 * <p>A station's assault gradient is radial and its spawns fall out of the
 * room graph's diameter. A ship's runs along the axis, so the longest fight the
 * deck can offer is simply bow against stern and the endpoints are known from
 * the profile without searching for them.
 *
 * <p>This is the placeholder a breach-point stage will replace once boarding
 * entry is authored: a real breach opens on a flank at a chosen frame, not at
 * the tip of the corridor.
 */
public final class DeckEndSpawnStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        DeckProfile profile = ctx.get(ShipKeys.DECK_PROFILE);
        if (profile == null) {
            throw new IllegalStateException("DeckEndSpawnStage requires a published deck profile");
        }
        int spineRow = (profile.spineTop() + profile.spineBottom()) / 2;
        ctx.put(BspKeys.MARINE_SPAWN, new int[]{ 0, spineRow });
        ctx.put(BspKeys.DEFENDER_SPAWN, new int[]{ profile.frames() - 1, spineRow });
    }
}
