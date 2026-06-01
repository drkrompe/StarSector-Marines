package com.dillon.starsectormarines.battle.world.gen;

/**
 * Top-level entry point for battle-map procedural generators. Implementations
 * own their algorithm (BSP, striped grid, wilderness biome, etc.) and produce
 * a fully-populated {@link MapResult} that
 * {@link com.dillon.starsectormarines.battle.setup.BattleSetup} can hand straight to
 * the sim and renderer.
 *
 * <p>Stateless contract: the same instance can be invoked from multiple seeds
 * without carrying state between calls. {@link BattleSetup} typically holds a
 * single instance per session and re-uses it per battle.
 */
public interface MapGenerator {

    /**
     * Build a map of {@code width × height} cells, deterministic from
     * {@code seed}. Implementations are expected to complete in &lt;100ms
     * for tactical-scale dimensions (60–100 cells per axis).
     */
    MapResult generate(int width, int height, long seed);

    /**
     * Conquest-aware build: lay out biome bands along {@code axis} so the
     * map spans the full beach→port→city→fortress progression instead of
     * scattering themes uniformly. Default implementation ignores the axis
     * and delegates to {@link #generate(int, int, long)} — only generators
     * that opt into biome mode need to override this.
     */
    default MapResult generate(int width, int height, long seed, TraversalAxis axis) {
        return generate(width, height, seed);
    }

    /**
     * Campaign-aware build: the target world's {@link TargetProfile} (planetary
     * defenses, market size, spaceport — extracted at the launch boundary) is
     * carried in so generation can reflect <em>which</em> world the battle is
     * over. Default implementation ignores the profile and delegates to
     * {@link #generate(int, int, long, TraversalAxis)} — only generators that
     * read campaign signal need to override this. The campaign → battle bridge
     * passes {@link TargetProfile#NEUTRAL} when no market backs the battle, so
     * overriding generators keep their pre-bridge output byte-identical.
     */
    default MapResult generate(int width, int height, long seed, TraversalAxis axis, TargetProfile profile) {
        return generate(width, height, seed, axis);
    }
}
