package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.List;

/**
 * What a ruins site was built round, as its ground wears it ({@code ruins-expedition.md}, The vault and its prize):
 * the props that furnish the vault room and the interior's chambers, and the flat patches on their floors. Stated in
 * prop ids rather than the campaign's theme so world generation stays free of campaign types; the battle maps a theme
 * to its dressing.
 *
 * <p>The dressing draws nothing from the layout's random stream, so the same seed lays the same ground whatever it
 * wears, and it never closes a way: a prop stands against a room's wall, never in a doorway, and only where the room
 * stays one open floor. A floor patch (frost, growth) is flat: it lies on open floor and closes nothing.
 *
 * @param vault    props along the vault room's long walls
 * @param chambers props along the interior chambers' walls
 * @param floor    walkable patches on the open floor of the vault room and the chambers
 */
public record RuinsDressing(List<String> vault, List<String> chambers, List<String> floor, String product) {

    /** Plain ground: the ruins as they stand. */
    public static final RuinsDressing NONE = new RuinsDressing(List.of(), List.of(), List.of(), null);

    public RuinsDressing {
        vault = List.copyOf(vault);
        chambers = List.copyOf(chambers);
        floor = List.copyOf(floor);
    }

    /**
     * Props and floor patches, with no product of the theme's own: the secure store's last cradle holds a stand-in.
     */
    public RuinsDressing(List<String> vault, List<String> chambers, List<String> floor) {
        this(vault, chambers, floor, null);
    }

    /** The same dressing whose product, the prize standing in its cradle, is {@code product}. */
    public RuinsDressing withProduct(String product) {
        return new RuinsDressing(vault, chambers, floor, product);
    }

    /** Props only, bare floors. */
    public RuinsDressing(List<String> vault, List<String> chambers) {
        this(vault, chambers, List.of());
    }

    public boolean none() { return vault.isEmpty() && chambers.isEmpty() && floor.isEmpty(); }
}
