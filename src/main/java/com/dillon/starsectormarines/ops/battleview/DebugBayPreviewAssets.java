package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.texture.TextureLease;
import com.dillon.starsectormarines.render2d.texture.TextureLedger;

import java.util.Set;

/**
 * The unpainted mech set the debug briefing's bay pictures compose from, held under a lease
 * of its own ({@code battle-textures.md}): loaded on the first picture drawn and given back
 * by {@link #release()} when the briefing closes. A release drops the set, so a picture asks
 * again on each draw rather than keeping one.
 */
public final class DebugBayPreviewAssets {

    private TextureLease lease;
    private BattleSprites sprites;

    public LayeredMechAssets mechs() {
        if (sprites == null) {
            lease = TextureLedger.shared().lease("debug bay previews");
            sprites = new BattleSprites(lease);
        }
        sprites.ensureLayeredMechSprites(Set.of());
        return sprites.layeredMechSprites();
    }

    /** Gives every texture back; the next picture drawn loads what it shows afresh. */
    public void release() {
        if (lease == null) return;
        lease.release();
        lease = null;
        sprites = null;
    }
}
