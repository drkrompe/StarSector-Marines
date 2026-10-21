package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.WorkMotion;
import java.util.Map;

/**
 * What a crew member holds while working ({@link WorkMotion}): the welding
 * torch and its sparks, a carried crate, a spanner, a knife, a ladle, a dumbbell.
 *
 * <p>Not specials and not anybody's issue: each is registered beside the
 * specials under its own key, the way the medic's treatment kit is, and drawn
 * only for as long as the motion that uses it. The torch and sparks are the
 * Mech Lab's own art, so welding reads the same in every room the bay's work is
 * seen from.
 */
final class WorkPropLayer {

    static final String TORCH = "work.welding-torch";
    static final String SPARKS = "work.welding-sparks";
    static final String CRATE = "work.crate";
    static final String WRENCH = "work.wrench";
    static final String KNIFE = "work.knife";
    static final String LADLE = "work.ladle";
    static final String DUMBBELL = "work.dumbbell";

    private static final String PROPS = "graphics/battle/crew-work-props/";

    /** Asset key to sprite path, for both sprite loaders. */
    static final Map<String, String> SPRITES = Map.of(
            TORCH, "graphics/battle/mech-lab/welding-torch.png",
            SPARKS, "graphics/battle/mech-lab/welding-sparks-sheet.png",
            CRATE, PROPS + "crate.png",
            WRENCH, PROPS + "wrench.png",
            KNIFE, PROPS + "knife.png",
            LADLE, PROPS + "ladle.png",
            DUMBBELL, PROPS + "dumbbell.png");

    /** The sparks sheet: eight frames, four across and two down, from first flash to embers. */
    static final int SPARK_COLUMNS = 4, SPARK_ROWS = 2, SPARK_FRAMES = 8;
    /** Spark burst size, in shoulder widths. */
    static final float SPARK_SIZE_SW = 1.3f;
    /** A curled dumbbell's length across the hand, in shoulder widths: 0.3 m against a 0.45 m shoulder. */
    static final float DUMBBELL_LENGTH_SW = 0.66f;
    /** A held tool leans inward across the body from the right hand, in degrees. */
    static final float TOOL_TILT_DEG = 12f;

    private WorkPropLayer() {}

    /** The key of the tool the right hand holds for {@code motion}, or null for none. */
    static String toolKey(WorkMotion motion) {
        return switch (motion) {
            case WELD -> TORCH;
            case WRENCH -> WRENCH;
            case CHOP -> KNIFE;
            case STIR -> LADLE;
            default -> null;
        };
    }

    /** How long the held tool is drawn, in shoulder widths. */
    static float toolLengthSw(WorkMotion motion) {
        return switch (motion) {
            case WELD -> 0.45f;
            case WRENCH -> 0.5f;
            case CHOP -> 0.4f;
            case STIR -> 0.46f;
            default -> 0f;
        };
    }
}
