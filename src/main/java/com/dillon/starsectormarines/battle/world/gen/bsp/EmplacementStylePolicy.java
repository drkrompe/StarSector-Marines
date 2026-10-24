package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;

import java.util.List;
import java.util.Set;

/**
 * The one place a generated map decides how its defenders' posts are built (`ground-turret-tiers.md`). A pirate
 * world holds old works rather than building its own, so its posts are {@link EmplacementStyle#DERELICT}. Otherwise
 * the defenders build in their faction's tech family (`tech-families.md`): the Persean League builds midline in
 * League standard, the Diktat and the Lion's Guard midline in Diktat export grade, Tri-Tachyon high-tech, everyone
 * else low-tech. Within the family a garrison on a world with no prepared
 * defences (no Ground Defenses or heavy batteries, a defence rating of one or less) puts up the militia style, and
 * every other garrison the prepared one; a battle with no market at all pours concrete. A first-pass reading of the
 * profile, to be moved by play.
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

    /** The faction whose works are midline in League coalition standards. */
    static final Set<String> LEAGUE = Set.of("persean");

    /** Factions whose works are midline in Diktat export grade. */
    static final Set<String> DIKTAT = Set.of("sindrian_diktat", "lions_guard");

    /** Factions whose works are high-tech. */
    static final Set<String> HIGH_TECH = Set.of("tritachyon");

    public static EmplacementStyle forDefenders(TargetProfile profile) {
        if (profile == null || profile.marketSize() <= 0) return EmplacementStyle.BRUTALIST;
        String faction = profile.factionId();
        if (PIRATES.equals(faction)) return EmplacementStyle.DERELICT;
        boolean prepared = profile.groundDefences() || profile.heavyBatteries()
                || profile.defenseLevel() > MILITIA_MAX_DEFENSE_LEVEL;
        if (LEAGUE.contains(faction)) return prepared ? EmplacementStyle.MODULAR : EmplacementStyle.BARRIER;
        if (DIKTAT.contains(faction)) return prepared ? EmplacementStyle.EXPORT : EmplacementStyle.BLOCKWORK;
        if (HIGH_TECH.contains(faction)) return prepared ? EmplacementStyle.COMPOSITE : EmplacementStyle.DEPLOYED;
        return prepared ? EmplacementStyle.BRUTALIST : EmplacementStyle.EARTHWORK;
    }

    /**
     * Every post in {@code posts}, in place, built in {@code style}: the hook a mission uses to theme its ground, called
     * on the posts it hands to {@code BattleSetup.buildMap}. A militia style's sandbags are laid when a post is stamped,
     * so restyling to one afterwards changes the guns and their bases but not the parapets.
     */
    public static void restyle(List<DefensePost> posts, EmplacementStyle style) {
        posts.replaceAll(post -> post.withStyle(style));
    }
}
