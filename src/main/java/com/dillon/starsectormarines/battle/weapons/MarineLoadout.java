package com.dillon.starsectormarines.battle.weapons;

import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import com.dillon.starsectormarines.battle.command.objective.Objective;

/**
 * Per-slot loadout for a shuttle's marine roster. One {@code MarineLoadout}
 * describes one marine that will deboard: their {@link UnitRole}, their
 * {@link MarineWeapon primary} weapon, an optional {@link MarineSecondary}
 * with ammo, and (if the role needs it) the {@link Objective} they're
 * assigned to. SABOTAGE missions put a PLANTER slot at the front of each
 * shuttle pointing at a specific charge site; everyone else stays
 * {@link UnitRole#COMBATANT}.
 *
 * <p>Held as an array on {@link Shuttle}; index N gets popped off when the
 * N-th marine deboards. Null entries fall back to a plain combatant with
 * the default pulse-rifle primary and no secondary.
 */
public final class MarineLoadout {

    public static final MarineLoadout COMBATANT = new MarineLoadout(UnitRole.COMBATANT, null, MarineWeapon.PULSE_RIFLE, null, 0);

    public final UnitRole role;
    public final Objective objective;
    /** Primary handheld weapon. Null = use the {@link UnitType} default stats with no per-weapon FX. */
    public final MarineWeapon primary;
    /** Optional secondary weapon. Null = no secondary slot. */
    public final MarineSecondary secondary;
    /** Starting ammo for the secondary. Ignored when {@link #secondary} is null. */
    public final int secondaryAmmo;

    public MarineLoadout(UnitRole role, Objective objective) {
        this(role, objective, MarineWeapon.PULSE_RIFLE, null, 0);
    }

    public MarineLoadout(UnitRole role, Objective objective, MarineWeapon primary,
                         MarineSecondary secondary, int secondaryAmmo) {
        this.role = role;
        this.objective = objective;
        this.primary = primary;
        this.secondary = secondary;
        this.secondaryAmmo = secondaryAmmo;
    }
}
