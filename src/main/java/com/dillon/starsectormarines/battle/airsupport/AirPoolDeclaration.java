package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The air pools a mission declares, by side. Symmetric by construction: any faction may be given one.
 * {@link #NONE} is what a mission that says nothing gets, and a battle with it behaves exactly as it did before pools.
 */
public record AirPoolDeclaration(Map<Faction, AirPoolAllowance> allowances) {

    public static final AirPoolDeclaration NONE = new AirPoolDeclaration(Map.of());

    public AirPoolDeclaration {
        EnumMap<Faction, AirPoolAllowance> kept = new EnumMap<>(Faction.class);
        if (allowances != null) {
            for (Map.Entry<Faction, AirPoolAllowance> e : allowances.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && !e.getValue().isNone()) kept.put(e.getKey(), e.getValue());
            }
        }
        allowances = Collections.unmodifiableMap(kept);
    }

    /** The allowance declared for {@code side}; {@link AirPoolAllowance#NONE} when none. */
    public AirPoolAllowance forSide(Faction side) {
        return allowances.getOrDefault(side, AirPoolAllowance.NONE);
    }

    /** This declaration with {@code allowance} stated for {@code side} (an empty one removes it). */
    public AirPoolDeclaration with(Faction side, AirPoolAllowance allowance) {
        EnumMap<Faction, AirPoolAllowance> next = new EnumMap<>(Faction.class);
        next.putAll(allowances);
        next.put(side, allowance == null ? AirPoolAllowance.NONE : allowance);
        return new AirPoolDeclaration(next);
    }

    public boolean isNone() {
        return allowances.isEmpty();
    }
}
