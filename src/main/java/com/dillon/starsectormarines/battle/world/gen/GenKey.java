package com.dillon.starsectormarines.battle.world.gen;

/**
 * Identity-keyed, value-typed token for the {@link GenContext} blackboard.
 *
 * <p>Two properties matter. <b>Identity</b>: a key is equal only to itself —
 * the canonical instance is the {@code static final} field that declares it
 * (see e.g. the BSP generator's key holder), so look-ups are reference
 * compares, not string matches. <b>Type</b>: the {@code <T>} parameter binds
 * a key to its value type, so {@link GenContext#get(GenKey)} returns {@code T}
 * with no cast at the call site. The {@link #name} is for debug / preview
 * output only and plays no part in identity.
 *
 * <p>This is the seam that keeps the blackboard open for extension: a new map
 * domain declares its own keys and {@code put}s them without touching
 * {@link GenContext}.
 */
public final class GenKey<T> {

    public final String name;

    private GenKey(String name) {
        this.name = name;
    }

    /** Mint a key. Hold the result in a {@code static final} field — that field IS the key's identity. */
    public static <T> GenKey<T> of(String name) {
        return new GenKey<>(name);
    }

    @Override
    public String toString() {
        return "GenKey(" + name + ")";
    }
}
