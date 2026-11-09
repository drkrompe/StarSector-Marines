package com.dillon.starsectormarines.campaign.ruins;

import java.util.Collection;

/**
 * How large a world's ruins are: the size of its vanilla ruins condition ({@code ruins-expedition.md}). Vanilla picks
 * the ruins' salvage spec by it; an expedition's map and delve scale with it too.
 */
public enum RuinsGrade {
    SCATTERED("ruins_scattered"),
    WIDESPREAD("ruins_widespread"),
    EXTENSIVE("ruins_extensive"),
    VAST("ruins_vast");

    /** The vanilla market condition id, which is also vanilla's salvage spec id for these ruins. */
    public final String conditionId;

    RuinsGrade(String conditionId) {
        this.conditionId = conditionId;
    }

    /** The grade a market's conditions name, the largest if several do; null when it has no ruins. */
    public static RuinsGrade of(Collection<String> conditionIds) {
        if (conditionIds == null) return null;
        RuinsGrade found = null;
        for (RuinsGrade grade : values()) {
            if (conditionIds.contains(grade.conditionId)) found = grade;
        }
        return found;
    }
}
