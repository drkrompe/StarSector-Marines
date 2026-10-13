package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The mech-drop allowances a mission declares, by side. Symmetric by construction:
 * any faction may be given one, though today only a Conquest declares one and only
 * for its marines. {@link #NONE} is what a mission that says nothing gets.
 */
public record MechDropDeclaration(Map<Faction, MechDropAllowance> allowances) {

    public static final MechDropDeclaration NONE = new MechDropDeclaration(Map.of());

    public MechDropDeclaration {
        EnumMap<Faction, MechDropAllowance> kept = new EnumMap<>(Faction.class);
        if (allowances != null) {
            for (Map.Entry<Faction, MechDropAllowance> e : allowances.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && !e.getValue().isNone()) {
                    kept.put(e.getKey(), e.getValue());
                }
            }
        }
        allowances = Collections.unmodifiableMap(kept);
    }

    /** The allowance declared for {@code side}; {@link MechDropAllowance#NONE} when none. */
    public MechDropAllowance forSide(Faction side) {
        return allowances.getOrDefault(side, MechDropAllowance.NONE);
    }

    /** This declaration with {@code allowance} stated for {@code side} (an empty one removes it). */
    public MechDropDeclaration with(Faction side, MechDropAllowance allowance) {
        EnumMap<Faction, MechDropAllowance> next = new EnumMap<>(Faction.class);
        next.putAll(allowances);
        next.put(side, allowance == null ? MechDropAllowance.NONE : allowance);
        return new MechDropDeclaration(next);
    }

    public boolean isNone() {
        return allowances.isEmpty();
    }
}
