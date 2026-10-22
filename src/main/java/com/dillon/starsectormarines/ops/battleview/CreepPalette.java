package com.dillon.starsectormarines.ops.battleview;

import java.util.List;
import java.util.Locale;

/**
 * The creep's colours. The palette is data: one is chosen per run by
 * {@code -Dbattle.render.creepPalette=<id>} and every look (shader, CPU model,
 * fallback) reads the same six colours.
 *
 * @param base  the membrane between and around the plates
 * @param flesh the chitin plates
 * @param vein  the vein network
 * @param glow  the pulse running along the veins
 * @param rim   the crest of the beaded lip, and the raised veins on a wall
 * @param dead  withered growth
 * @param wilt  grass and crops dying ahead of the creep's edge
 */
public record CreepPalette(String id, float[] base, float[] flesh, float[] vein, float[] glow, float[] rim, float[] dead,
                           float[] wilt) {

    /** Control-run switch naming the palette; unknown ids fall back to {@link #HIVE}. */
    public static final String PROPERTY = "battle.render.creepPalette";

    /** The superseded red-tide direction, kept for comparison: oxblood film over bruised flesh. */
    public static final CreepPalette BLOOM = new CreepPalette("bloom",
            rgb(0x5a2a30), rgb(0x8a4a4c), rgb(0x1c0b0e), rgb(0xb04a50), rgb(0x9a5a56), rgb(0x4b4439), rgb(0x857a55));

    /**
     * The owner's chitin direction for the Rav Bloom (browns, creams, chitin; the swarm owns no red): grey-umber
     * ground that is the dullest thing on the map, ridges like shed plates, dull cream nodules and a dim amber pulse.
     * It sits between the near-black bodies and the tan paving, so a body still cuts out of it and infested ground
     * reads as turned.
     */
    public static final CreepPalette CHITIN = new CreepPalette("chitin",
            rgb(0x4c443c), rgb(0x6a5d4e), rgb(0x2e2822), rgb(0x96682c), rgb(0x5a5046), rgb(0x3a332c), rgb(0x857a55));

    /**
     * The hive palette, set beside the settled creatures (`rav-design-language.md`): their carapace is pale lilac
     * over deep magenta-crimson sinew, so the creep is the same family pushed dull and dark, a low-chroma
     * violet-grey (L about 45 to 70) the bodies cut out of. Plates a step lighter than the membrane, crests
     * toward a greyed lilac, veins and the pulse in a muted version of the sinew. Grass dying ahead of it turns a
     * dry, greyed straw.
     */
    public static final CreepPalette HIVE = new CreepPalette("hive",
            rgb(0x5a5560), rgb(0x736c78), rgb(0x46293a), rgb(0x84486a), rgb(0x9b949c), rgb(0x5e5c5a), rgb(0x837c5e));

    static final List<CreepPalette> ALL = List.of(HIVE, CHITIN, BLOOM);

    /** The palette this process draws with. */
    public static CreepPalette active() {
        String id = System.getProperty(PROPERTY, HIVE.id());
        for (CreepPalette p : ALL) if (p.id().equalsIgnoreCase(id)) return p;
        return HIVE;
    }

    static float[] rgb(int hex) {
        return new float[]{((hex >> 16) & 0xFF) / 255f, ((hex >> 8) & 0xFF) / 255f, (hex & 0xFF) / 255f};
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "CreepPalette[%s]", id);
    }
}
