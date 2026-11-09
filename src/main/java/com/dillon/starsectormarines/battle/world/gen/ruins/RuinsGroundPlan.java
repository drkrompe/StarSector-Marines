package com.dillon.starsectormarines.battle.world.gen.ruins;

/**
 * What a mission asks of a ruins site's ground ({@code mapgen-nouns.md}, Ruins ground): how deep the delve is. A ruins site's map
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
     * The ground for a delve of {@code layers} layers, a step up the tier band ({@code MapScale}) from where it
     * began, so a ruins site sits with the maps the company fights on elsewhere (owner: "Map is too small"):
     * scattered ruins on the established map, widespread between it and the full-strength map, extensive on the
     * full-strength map, vast at the colony map's size. The dead town is dense enough to fill the route, so the
     * layers cross ruins rather than open country. UNTUNED.
     */
    public static RuinsGroundPlan forLayers(int layers) {
        return switch (layers) {
            case 1 -> new RuinsGroundPlan(1, 144, 80, 0.50f);
            case 2 -> new RuinsGroundPlan(2, 200, 112, 0.60f);
            case 3 -> new RuinsGroundPlan(3, 280, 168, 0.70f);
            case 4 -> new RuinsGroundPlan(4, 400, 240, 0.80f);
            default -> throw new IllegalArgumentException("a ruins delve has 1 to " + MAX_LAYERS + " layers, not " + layers);
        };
    }

    /**
     * Ways through each layer that stay open: scattered ruins may be crossed by one, and every layer of a larger
     * site has at least two, so no single blocked way closes the route ({@code mapgen-nouns.md}, Ruins ground).
     */
    public int minOpenWays() {
        return layers == 1 ? 1 : 2;
    }

    /**
     * Layers at the vault's end that are inside the site's megastructure ({@code mapgen-nouns.md}, Ruins ground):
     * none on scattered ground, where only a bunker encloses the vault; the last layer at widespread; the last two
     * at extensive and vast. The landing and the layers before the interior stay outside.
     */
    public int interiorLayers() {
        return layers == 1 ? 0 : layers == 2 ? 1 : 2;
    }
}
