package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.List;

/**
 * What a ruins site was built round, as its ground wears it ({@code ruins-expedition.md}, The vault and its prize):
 * the props that furnish the vault room and the interior's chambers. Stated in prop ids rather than the campaign's
 * theme so world generation stays free of campaign types; the battle maps a theme to its dressing.
 *
 * <p>The dressing draws nothing from the layout's random stream, so the same seed lays the same ground whatever it
 * wears, and it never closes a way: a prop stands against a room's wall, never in a doorway, and only where the room
 * stays one open floor.
 *
 * @param vault    props along the vault room's long walls
 * @param chambers props along the interior chambers' walls
 */
public record RuinsDressing(List<String> vault, List<String> chambers) {

    /** Plain ground: the ruins as they stand. */
    public static final RuinsDressing NONE = new RuinsDressing(List.of(), List.of());

    public RuinsDressing {
        vault = List.copyOf(vault);
        chambers = List.copyOf(chambers);
    }

    public boolean none() { return vault.isEmpty() && chambers.isEmpty(); }
}
