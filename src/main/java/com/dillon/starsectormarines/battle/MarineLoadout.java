package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.objective.Objective;

/**
 * Per-slot loadout for a shuttle's marine roster. One {@code MarineLoadout}
 * describes one marine that will deboard: their {@link UnitRole} and (if the
 * role needs it) the {@link Objective} they're assigned to. SABOTAGE missions
 * put a PLANTER slot at the front of each shuttle pointing at a specific
 * charge site; everyone else stays {@link UnitRole#COMBATANT}.
 *
 * <p>Held as an array on {@link Shuttle}; index N gets popped off when the
 * N-th marine deboards. Null entries fall back to plain combatants.
 */
public final class MarineLoadout {

    public static final MarineLoadout COMBATANT = new MarineLoadout(UnitRole.COMBATANT, null);

    public final UnitRole role;
    public final Objective objective;

    public MarineLoadout(UnitRole role, Objective objective) {
        this.role = role;
        this.objective = objective;
    }
}
