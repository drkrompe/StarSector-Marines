package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;

import java.util.List;

/**
 * The one place a generated map decides how its defenders' posts are built (`ground-turret-tiers.md`): a garrison
 * on a world with a market behind it and no prepared defences (no Ground Defenses, a defence rating of one or less)
 * digs earthworks, as militia does; every other garrison, and a battle with no market at all, pours concrete. A
 * first-pass reading of the profile, to be moved by play; the faction's tech family is not read yet, because only
 * the low-tech styles exist.
 */
public final class EmplacementStylePolicy {

    /** Defence rating at or below which a world without Ground Defenses fields militia works. */
    static final int MILITIA_MAX_DEFENSE_LEVEL = 1;

    private EmplacementStylePolicy() {}

    public static EmplacementStyle forDefenders(TargetProfile profile) {
        if (profile == null || profile.marketSize() <= 0) return EmplacementStyle.BRUTALIST;
        boolean prepared = profile.groundDefences() || profile.heavyBatteries()
                || profile.defenseLevel() > MILITIA_MAX_DEFENSE_LEVEL;
        return prepared ? EmplacementStyle.BRUTALIST : EmplacementStyle.EARTHWORK;
    }

    /** Every post in {@code posts}, in place, built in {@code style}. */
    static void restyle(List<DefensePost> posts, EmplacementStyle style) {
        posts.replaceAll(post -> post.withStyle(style));
    }
}
