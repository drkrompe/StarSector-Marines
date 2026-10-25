package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.OfficerCareer;
import com.dillon.starsectormarines.marine.OfficerSpecialty;
import com.dillon.starsectormarines.marine.Rank;

import java.util.EnumMap;
import java.util.Map;

/**
 * The debug briefing's commander for one battle: off (every squad under its acting NCO, as
 * debug missions have always fought), or a top-rank officer whose training the developer
 * assigns. It is a setting, not a person — {@link #captain()} builds a fresh officer at each
 * launch, and nothing about it reaches the campaign roster or survives the preset.
 */
final class DebugCommander {

    /** The rank with the most training points, so every allocation the game allows is reachable. */
    static final Rank RANK = Rank.COLONEL;
    static final String PORTRAIT = "graphics/portraits/portrait_mercenary01.png";

    private boolean fielded;
    private final Map<OfficerSpecialty, Integer> levels = new EnumMap<>(OfficerSpecialty.class);

    boolean fielded() { return fielded; }

    void setFielded(boolean fielded) { this.fielded = fielded; }

    int level(OfficerSpecialty specialty) { return levels.getOrDefault(specialty, 0); }

    /** Training points the rank grants, as {@link OfficerCareer#availablePoints} counts them. */
    static int points() { return new OfficerCareer().availablePoints(RANK); }

    int spent() {
        int spent = 0;
        for (int level : levels.values()) spent += level;
        return spent;
    }

    /** Whether {@code specialty} can be set to {@code level} within the rank's points. */
    boolean allows(OfficerSpecialty specialty, int level) {
        return level >= 0 && level <= OfficerCareer.MAX_LEVEL
                && spent() - level(specialty) + level <= points();
    }

    /** Sets a specialty's rank; pressing the rank it already holds steps it down one. */
    void press(OfficerSpecialty specialty, int rank) {
        int target = level(specialty) == rank ? rank - 1 : rank;
        if (allows(specialty, target)) levels.put(specialty, target);
    }

    /** A fresh officer trained as set, or null when no commander is fielded. */
    MarineCaptain captain() {
        if (!fielded) return null;
        MarineCaptain captain = new MarineCaptain("Debug Commander", PORTRAIT, RANK, 0f);
        for (OfficerSpecialty specialty : OfficerSpecialty.values()) {
            for (int i = 0; i < level(specialty); i++) captain.train(specialty);
        }
        return captain;
    }

    /** One line for the drawer: off, or the rank, points spent and what was trained. */
    String summary() {
        if (!fielded) return "OFF  ·  every squad under its acting NCO";
        StringBuilder trained = new StringBuilder();
        for (OfficerSpecialty specialty : OfficerSpecialty.values()) {
            int level = level(specialty);
            if (level == 0) continue;
            trained.append(trained.isEmpty() ? "" : " · ").append(specialty.label()).append(level == 1 ? " I" : " II");
        }
        return RANK.displayName() + "  ·  " + spent() + " / " + points() + " points"
                + (trained.isEmpty() ? "  ·  untrained" : "  ·  " + trained);
    }
}
