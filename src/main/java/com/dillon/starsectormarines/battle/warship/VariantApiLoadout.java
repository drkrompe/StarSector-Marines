package com.dillon.starsectormarines.battle.warship;

import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;

import java.util.Map;

/**
 * A loadout read from a live {@link ShipVariantAPI}: the weapons a campaign
 * fleet member is actually fitted with.
 *
 * <p>The hull is the variant's <i>base</i> hull, because a skin has no
 * {@code .ship} spec of its own and borrows its base's slots. Fitted slots come
 * first; the hull's built-in weapons fill any slot the variant does not name, so
 * a ship whose built-ins the variant API leaves out still carries them.
 */
public final class VariantApiLoadout implements WarshipLoadout {

    private final String hullId;
    private final ShipVariantAPI variant;
    private final Map<String, String> builtIns;

    public VariantApiLoadout(ShipVariantAPI variant) {
        this.variant = variant;
        ShipHullSpecAPI spec = variant.getHullSpec();
        String base = spec.getBaseHullId();
        this.hullId = base != null && !base.isEmpty() ? base : spec.getHullId();
        this.builtIns = spec.getBuiltInWeapons();
    }

    @Override public String hullId() {
        return hullId;
    }

    @Override public String weaponInSlot(String slotId) {
        if (variant.getFittedWeaponSlots().contains(slotId)) {
            String id = variant.getWeaponId(slotId);
            if (id != null && !id.isEmpty()) return id;
        }
        return builtIns == null ? null : builtIns.get(slotId);
    }
}
