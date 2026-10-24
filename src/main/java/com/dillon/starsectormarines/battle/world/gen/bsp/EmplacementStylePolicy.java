package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;

import java.util.List;

/**
 * The one place a generated map decides how its defenders' posts are built (`ground-turret-tiers.md`). A pirate
 * world holds old works rather than building its own, so its posts are {@link EmplacementStyle#DERELICT}. Otherwise a
 * garrison on a world with a market behind it and no prepared defences (no Ground Defenses, a defence rating of one
 * or less) digs earthworks, as militia does; every other garrison, and a battle with no market at all, pours
 * concrete. A first-pass reading of the profile, to be moved by play.
 *
 * <p>Derelict is otherwise a theme a mission chooses: ground that is old works (a fortification left from the AI
 * Wars, a site nobody has manned in decades) {@link #restyle restyles} its posts before the battle is built.
 */
public final class EmplacementStylePolicy {

    /** Defence rating at or below which a world without Ground Defenses fields militia works. */
    static final int MILITIA_MAX_DEFENSE_LEVEL = 1;

    private EmplacementStylePolicy() {}

    /** The faction whose worlds are squatted old works. */
    static final String PIRATES = "pirates";

    public static EmplacementStyle forDefenders(TargetProfile profile) {
        if (profile == null || profile.marketSize() <= 0) return EmplacementStyle.BRUTALIST;
        if (PIRATES.equals(profile.factionId())) return EmplacementStyle.DERELICT;
        boolean prepared = profile.groundDefences() || profile.heavyBatteries()
                || profile.defenseLevel() > MILITIA_MAX_DEFENSE_LEVEL;
        return prepared ? EmplacementStyle.BRUTALIST : EmplacementStyle.EARTHWORK;
    }

    /**
     * Every post in {@code posts}, in place, built in {@code style}: the hook a mission uses to theme its ground, called
     * on the posts it hands to {@code BattleSetup.buildMap}. Earthwork's sandbags are laid when a post is stamped, so
     * restyling to earthwork afterwards changes the guns and their bases but not the parapets.
     */
    public static void restyle(List<DefensePost> posts, EmplacementStyle style) {
        posts.replaceAll(post -> post.withStyle(style));
    }
}
