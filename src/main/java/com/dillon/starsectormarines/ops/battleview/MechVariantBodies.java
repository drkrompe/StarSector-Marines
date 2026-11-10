package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechVariant;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The bodies of the hardware variants that keep another line's frame (`mech-lines.md`, law 2; {@link MechVariant#frame}):
 * one sprite each, drawn in place of the frame's chassis by {@link LayeredMechComposer}, with the frame's legs, mounts
 * and weapons round it. A body is the frame's chassis square registered at its own size inside a larger canvas, so
 * parts the variant hangs past the frame's outline (a blade, a crane) draw past it and the frame part lands exactly
 * where the frame's chassis would (art-source/mech-modular-topdown/sources/lowtech/variants/bodies.py). A variant
 * wears its body in every livery: its paint is part of what it is.
 */
public final class MechVariantBodies {

    static final String ROOT = "graphics/battle/mech-modular-topdown/variants/";

    private MechVariantBodies() { }

    /** The body's texture path for a variant, or null for a line's own chassis. */
    public static String path(MechVariant variant) {
        return variant != null && variant.isVariant() ? ROOT + variant.id + ".png" : null;
    }

    /** Every variant body that loads; a variant whose body is missing draws as its frame. */
    public static Map<MechVariant, LayeredSpriteCache> load(Function<String, LayeredSpriteCache> loader) {
        Map<MechVariant, LayeredSpriteCache> bodies = new EnumMap<>(MechVariant.class);
        for (MechVariant variant : MechVariant.values()) {
            String path = path(variant);
            if (path == null) continue;
            LayeredSpriteCache body = loader.apply(path);
            if (body != null) bodies.put(variant, body);
        }
        return bodies;
    }
}
