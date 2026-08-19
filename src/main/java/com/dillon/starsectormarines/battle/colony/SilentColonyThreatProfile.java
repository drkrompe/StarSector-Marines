package com.dillon.starsectormarines.battle.colony;

import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Hidden-seed selection of the dead colony's dormant automated defenses. */
public enum SilentColonyThreatProfile {
    SENTRY_RING,
    HARDPOINT_GRID,
    LAYERED_NETWORK;

    private static final SilentColonyThreatProfile[] VALUES = values();

    public static SilentColonyThreatProfile fromSeed(long threatSeed) {
        if (threatSeed < 0L) return null;
        long mixed = threatSeed ^ (threatSeed >>> 33);
        return VALUES[Math.floorMod((int) mixed, VALUES.length)];
    }

    /** Selects only autonomous structures; no biological or infantry roster. */
    public List<DefensePost> select(List<DefensePost> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<DefensePost> selected = new ArrayList<>();
        for (DefensePost post : candidates) {
            if (post == null) continue;
            boolean include = switch (this) {
                case SENTRY_RING -> post.tier == DefensePostKind.LIGHT;
                case HARDPOINT_GRID -> post.tier == DefensePostKind.MEDIUM
                        || post.tier == DefensePostKind.LARGE;
                case LAYERED_NETWORK -> true;
            };
            if (include) selected.add(post);
        }
        return Collections.unmodifiableList(selected);
    }
}
