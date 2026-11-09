package com.dillon.starsectormarines.battle.world.gen.ruins;

/**
 * What a mission asks of a ruins site's ground ({@code ruins-ground.md}): how deep the delve is. A ruins site's map
 * is an abandoned settlement laid from a landing area at its west end to a vault at its east end through
 * {@link #layers} bands of ruined ground. Everything else (the map's size, how much of it the dead settlement
 * covered) follows from the depth, the way the ruins grade's table in {@code ruins-expedition.md} reads it.
 *
 * <p>The depth is the ruins grade's layer count, one for scattered ruins to four for vast; the plan is stated in
 * that number rather than in the campaign's grade so world generation stays free of campaign types. A mission
 * maps its grade with {@code RuinsGroundPlan.forLayers(grade.ordinal() + 1)}.
 *
 * @param layers  bands of ruined ground the route crosses between the landing area and the vault, 1 to 4
 * @param width   map width in cells
 * @param height  map height in cells
 * @param density how densely the dead settlement was built, on the 0..1 scale grown settlements take
 */
public record RuinsGroundPlan(int layers, int width, int height, float density) {

    /** The deepest delve: vast ruins. */
    public static final int MAX_LAYERS = 4;

    public RuinsGroundPlan {
        if (layers < 1 || layers > MAX_LAYERS) {
            throw new IllegalArgumentException("a ruins delve has 1 to " + MAX_LAYERS + " layers, not " + layers);
        }
        if (width < 96 || height < 56) throw new IllegalArgumentException("ruins ground needs at least 96x56");
    }

    /**
     * The ground for a delve of {@code layers} layers: scattered ruins on the first-contract map, vast ones on the
     * full-strength map. The sizes are the tier band's ({@code MapScale}) with one step between widespread and
     * vast, so a vast site's vault stands deep in the ruins. UNTUNED.
     */
    public static RuinsGroundPlan forLayers(int layers) {
        return switch (layers) {
            case 1 -> new RuinsGroundPlan(1, 112, 64, 0.30f);
            case 2 -> new RuinsGroundPlan(2, 144, 80, 0.45f);
            case 3 -> new RuinsGroundPlan(3, 200, 112, 0.60f);
            case 4 -> new RuinsGroundPlan(4, 280, 168, 0.75f);
            default -> throw new IllegalArgumentException("a ruins delve has 1 to " + MAX_LAYERS + " layers, not " + layers);
        };
    }

    /**
     * Ways through each layer that stay open: scattered ruins may be crossed by one, and every layer of a larger
     * site has at least two, so no single blocked way closes the route ({@code ruins-ground.md}, slice 3).
     */
    public int minOpenWays() {
        return layers == 1 ? 1 : 2;
    }
}
