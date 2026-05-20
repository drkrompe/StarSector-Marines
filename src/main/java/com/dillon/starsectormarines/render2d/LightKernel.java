package com.dillon.starsectormarines.render2d;

/**
 * Selects which vanilla radial-falloff sprite a {@link Light} samples
 * for its glow. Three kernels cover the V1 emitters: a tight bright pop
 * for rifle muzzle flashes, a medium warm bloom for HE bursts, and a
 * long soft falloff for persistent wreck fires.
 *
 * <p>All three are game-shipped textures, so the
 * {@code sprite_lazy_load} NPE gotcha doesn't apply — but the
 * {@link LightAccumulator} still calls {@code loadTexture} once per
 * kernel before sampling to stay consistent with the rest of the mod.
 */
public enum LightKernel {

    /**
     * Small, tight kernel for rifle / chaingun muzzle flashes. Uses
     * {@code hit_glow_small.png} rather than an {@code engineglow} sprite
     * — engine glows have horizontal core bias (exhaust trail) that
     * smears the muzzle flash wider than the shooter's footprint, while
     * the hit-glow sprite is a clean compact bloom.
     */
    MUZZLE_FLASH("graphics/fx/hit_glow_small.png"),

    /** Medium kernel for HE explosions (heavy mortar, SRM, LRM). */
    HE_BURST("graphics/fx/glow64.png"),

    /** Long soft falloff for persistent wreck fires. */
    WRECK_FIRE("graphics/fx/fog_circle2.png"),

    /**
     * Soft halo for air-vehicle engine nozzles. Shares a sprite with
     * {@link #HE_BURST} since both want a clean radial bloom at a point;
     * separate enum entry so call sites read intent and batches stay
     * scoped to the emitter type.
     */
    ENGINE_GLOW("graphics/fx/glow64.png");

    public final String spritePath;

    LightKernel(String spritePath) {
        this.spritePath = spritePath;
    }
}
