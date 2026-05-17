package com.dillon.starsectormarines.ops.intel;

import java.util.Collections;
import java.util.List;

/**
 * Frozen snapshot of everything the mission system needs to know about a planet.
 * Pulled by {@link IntelReader} from a {@code PlanetAPI}; immutable so the generator
 * can hold it across a screen build without worrying about market mutation mid-frame.
 *
 * <p>Uninhabited worlds get a {@link #empty} instance — present but with no industries,
 * UNDEFENDED, size 0. The mission generator branches on emptiness for the
 * "derelict salvage / abandoned base" mission set.
 */
public final class PlanetIntel {

    public static final PlanetIntel EMPTY = new PlanetIntel(
            null, 0, 0f, Collections.<String>emptyList(),
            Collections.<IndustryEntry>emptyList(),
            DefenseLevel.UNDEFENDED, 0);

    public final String              factionId;
    public final int                 size;
    public final float               stability;
    public final List<String>        conditions;
    public final List<IndustryEntry> industries;
    public final DefenseLevel        defenseLevel;
    /** Raw numeric defense score; exposed for debug/balancing inspection. */
    public final int                 defenseScore;

    public PlanetIntel(String factionId, int size, float stability,
                       List<String> conditions, List<IndustryEntry> industries,
                       DefenseLevel defenseLevel, int defenseScore) {
        this.factionId    = factionId;
        this.size         = size;
        this.stability    = stability;
        this.conditions   = Collections.unmodifiableList(conditions);
        this.industries   = Collections.unmodifiableList(industries);
        this.defenseLevel = defenseLevel;
        this.defenseScore = defenseScore;
    }

    public boolean isInhabited() {
        return !industries.isEmpty();
    }

    public boolean hasIndustry(String id) {
        for (IndustryEntry e : industries) {
            if (e.id.equals(id)) return true;
        }
        return false;
    }

    public IndustryEntry getIndustry(String id) {
        for (IndustryEntry e : industries) {
            if (e.id.equals(id)) return e;
        }
        return null;
    }
}
