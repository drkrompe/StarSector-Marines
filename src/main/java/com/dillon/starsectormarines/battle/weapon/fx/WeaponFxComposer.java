package com.dillon.starsectormarines.battle.weapon.fx;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure expansion of authored layers into resolved particle commands. Random
 * choices are seeded from definition id, slot, stable event time, and layer
 * index, so previews and runtime consumers can reproduce the same composition
 * without sharing mutable RNG state or a rendering API.
 */
public final class WeaponFxComposer {

    /** Authoring guard against an accidentally unbounded aftermath emitter. */
    public static final int MAX_COMMANDS_PER_LAYER = 4096;

    private static final Color SPARK = new Color(0xFF, 0xE0, 0x80);
    private static final Color FLOOR_DUST = new Color(0xB4, 0xA0, 0x70);
    private static final Color WALL_DUST = new Color(0xA0, 0xA0, 0xA0);
    private static final Color SMOKE = new Color(0xC8, 0xC8, 0xC8);
    private static final Color CANNON = new Color(0xFF, 0xD0, 0x88);

    private WeaponFxComposer() {}

    /** Resolves one slot. Missing slots compose to an immutable empty list. */
    public static List<FxParticleCommand> compose(
            WeaponFxDef definition, FxSlot slot, FxCompositionContext context) {
        if (definition == null || slot == null || context == null) {
            throw new IllegalArgumentException("definition, slot, and context are required");
        }
        List<FxLayerDef> layers = definition.layers(slot);
        if (layers.isEmpty()) return List.of();

        List<FxParticleCommand> commands = new ArrayList<>();
        long baseSeed = seedFor(definition.id, slot, context.seedTimeSeconds());
        for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
            FxLayerDef layer = layers.get(layerIndex);
            Random rng = new Random(mix64(baseSeed + 0x9E3779B97F4A7C15L * (layerIndex + 1L)));
            composeLayer(commands, layer, context, rng);
        }
        return List.copyOf(commands);
    }

    /** Stable seed contract exposed for preview diagnostics and parity tests. */
    public static long seedFor(String definitionId, FxSlot slot, float seedTimeSeconds) {
        if (definitionId == null || slot == null || !Float.isFinite(seedTimeSeconds)) {
            throw new IllegalArgumentException("definition id, slot, and finite seed time are required");
        }
        long hash = 0xcbf29ce484222325L;
        byte[] bytes = (definitionId + "\u0000" + slot.key).getBytes(StandardCharsets.UTF_8);
        for (byte value : bytes) {
            hash ^= value & 0xffL;
            hash *= 0x100000001b3L;
        }
        hash ^= Float.floatToIntBits(seedTimeSeconds) & 0xffffffffL;
        hash *= 0x100000001b3L;
        return mix64(hash);
    }

    private static void composeLayer(List<FxParticleCommand> commands, FxLayerDef layer,
                                     FxCompositionContext context, Random rng) {
        float delay = layer.delay().sample(rng);
        float duration = layer.emissionDuration().sample(rng);
        if (duration <= 0f) {
            emit(commands, layer, context, rng, delay);
            return;
        }

        float elapsed = 0f;
        int scheduled = 0;
        while (elapsed <= duration + 1e-6f) {
            int before = commands.size();
            emit(commands, layer, context, rng, delay + elapsed);
            scheduled += commands.size() - before;
            if (scheduled > MAX_COMMANDS_PER_LAYER) {
                throw new IllegalStateException("validated FX layer exceeded command bound");
            }
            elapsed += layer.emissionInterval().sample(rng);
        }
    }

    private static void emit(List<FxParticleCommand> commands, FxLayerDef layer,
                             FxCompositionContext context, Random rng, float delay) {
        int count = layer.count().sample(rng);
        for (int i = 0; i < count; i++) {
            float angle = rng.nextFloat() * (float) (Math.PI * 2.0);
            float distance = (float) Math.sqrt(rng.nextFloat()) * layer.jitter();
            float x = context.x() + (float) Math.cos(angle) * distance;
            float y = context.y() + (float) Math.sin(angle) * distance;
            ParticleDefaults defaults = defaults(layer.kind(), context.wallImpact(), rng);
            LocalVector offset = localVector(layer.offsetForward(), layer.offsetLateral(),
                    context.bearingDegrees(), rng);
            LocalVector velocity = localVector(layer.velocityForward(), layer.velocityLateral(),
                    context.bearingDegrees(), rng);
            boolean authoredVelocity = layer.velocityForward() != null
                    || layer.velocityLateral() != null;
            commands.add(new FxParticleCommand(
                    layer.kind(), delay, x + offset.x(), y + offset.y(),
                    authoredVelocity ? velocity.x() : defaults.velocityX,
                    authoredVelocity ? velocity.y() : defaults.velocityY,
                    layer.radius().sample(rng), defaults.growth,
                    layer.lifetime().sample(rng),
                    layer.color() != null ? layer.color() : defaults.color,
                    defaults.blend, defaults.angleDegrees, defaults.variantIndex));
        }
    }

    /** Rotates authored local motion into the shared north-based, CCW world frame. */
    private static LocalVector localVector(FxFloatRange forwardRange,
                                           FxFloatRange lateralRange,
                                           float bearingDegrees, Random rng) {
        float forward = forwardRange != null ? forwardRange.sample(rng) : 0f;
        float lateral = lateralRange != null ? lateralRange.sample(rng) : 0f;
        double radians = Math.toRadians(bearingDegrees);
        float sin = (float) Math.sin(radians);
        float cos = (float) Math.cos(radians);
        return new LocalVector(-sin * forward + cos * lateral,
                cos * forward + sin * lateral);
    }

    private static ParticleDefaults defaults(FxLayerKind kind, boolean wallImpact, Random rng) {
        return switch (kind) {
            case GLOW -> new ParticleDefaults(0f, 0f, 0f, SPARK, FxBlend.ADDITIVE, 0f, 0);
            case DUST -> new ParticleDefaults(0f, 0f, 0.4f,
                    wallImpact ? WALL_DUST : FLOOR_DUST, FxBlend.NORMAL, 0f, 0);
            case SMOKE -> new ParticleDefaults(
                    between(rng, -0.25f, 0.25f), between(rng, 0.35f, 0.70f),
                    between(rng, 0.35f, 0.60f), SMOKE, FxBlend.NORMAL,
                    between(rng, 0f, 360f), 0);
            case FIRE -> new ParticleDefaults(
                    between(rng, -0.30f, 0.30f), between(rng, 0.15f, 0.40f),
                    between(rng, 0.20f, 0.40f), Color.WHITE, FxBlend.ADDITIVE,
                    between(rng, 0f, 360f), 0);
            case EXPLOSION -> new ParticleDefaults(0f, 0f, 0.65f, CANNON,
                    FxBlend.ADDITIVE, between(rng, 0f, 360f), rng.nextInt(7));
            case RING -> new ParticleDefaults(0f, 0f, 2.8f, CANNON,
                    FxBlend.ADDITIVE, between(rng, 0f, 360f), 0);
        };
    }

    private static float between(Random rng, float min, float max) {
        return min + rng.nextFloat() * (max - min);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private record ParticleDefaults(float velocityX, float velocityY, float growth,
                                    Color color, FxBlend blend, float angleDegrees,
                                    int variantIndex) {}

    private record LocalVector(float x, float y) {}
}
