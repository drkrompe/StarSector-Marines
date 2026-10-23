package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

import java.util.ArrayList;
import java.util.List;

/**
 * A Marine's right-button list (`direct-control-nouns.md`, Mouse buttons and weapon groups): the thrown and
 * utility items it carries, in a fixed order, smoke, then a hand grenade, then the integral shield. Only what
 * the Marine has is listed; an item with no uses left stays listed and reads empty, as a mech's dry rack does.
 * E moves the pick along the list and the right button uses the pick. The flamer is not listed: it is a
 * direct-fire weapon, not a throw.
 */
public final class MarineRightTrigger {
    private MarineRightTrigger() {}

    /** One right-button item, named by the one-shot request that uses it. */
    public enum Item {
        SMOKE(DirectControlAbility.SMOKE),
        GRENADE(DirectControlAbility.GRENADE),
        SHIELD(DirectControlAbility.SHIELD);

        public final DirectControlAbility ability;

        Item(DirectControlAbility ability) { this.ability = ability; }
    }

    /**
     * The list for a Marine carrying {@code special} (null for none) and integral system {@code system} (null
     * for none).
     */
    public static List<Item> members(SpecialEquipmentDef special, IntegralSystemDef system) {
        List<Item> items = new ArrayList<>(3);
        if (special != null && special.activation() == SpecialActivation.UTILITY_SMOKE
                && special.smokeGrenadeSpec() != null) items.add(Item.SMOKE);
        if (special != null && special.isThrownGrenade()) items.add(Item.GRENADE);
        if (system != null && system.grantsMitigation()) items.add(Item.SHIELD);
        return List.copyOf(items);
    }

    /** What the button uses: {@code pick} while the list still holds it, otherwise its first item; null for an empty list. */
    public static Item resolve(List<Item> members, Item pick) {
        if (members.isEmpty()) return null;
        return pick != null && members.contains(pick) ? pick : members.get(0);
    }

    /** The item after {@code pick}, wrapping; the first when the list does not hold {@code pick}. */
    public static Item next(List<Item> members, Item pick) {
        if (members.isEmpty()) return null;
        int at = members.indexOf(resolve(members, pick));
        return members.get((at + 1) % members.size());
    }
}
