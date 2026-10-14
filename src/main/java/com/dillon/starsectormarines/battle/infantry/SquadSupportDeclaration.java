package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The support kits a mission declares, by side. Symmetric by construction: any faction may be given one.
 * {@link #NONE} is what a mission that says nothing gets, and a battle with it behaves exactly as it did before kits.
 */
public record SquadSupportDeclaration(Map<Faction, SquadSupportKit> kits) {

    public static final SquadSupportDeclaration NONE = new SquadSupportDeclaration(Map.of());

    public SquadSupportDeclaration {
        EnumMap<Faction, SquadSupportKit> kept = new EnumMap<>(Faction.class);
        if (kits != null) {
            for (Map.Entry<Faction, SquadSupportKit> e : kits.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && !e.getValue().isNone()) kept.put(e.getKey(), e.getValue());
            }
        }
        kits = Collections.unmodifiableMap(kept);
    }

    /** The kit declared for {@code side}; {@link SquadSupportKit#NONE} when none. */
    public SquadSupportKit forSide(Faction side) {
        return kits.getOrDefault(side, SquadSupportKit.NONE);
    }

    /** This declaration with {@code kit} stated for {@code side} (an empty one removes it). */
    public SquadSupportDeclaration with(Faction side, SquadSupportKit kit) {
        EnumMap<Faction, SquadSupportKit> next = new EnumMap<>(Faction.class);
        next.putAll(kits);
        next.put(side, kit == null ? SquadSupportKit.NONE : kit);
        return new SquadSupportDeclaration(next);
    }

    public boolean isNone() {
        return kits.isEmpty();
    }
}
