package com.dillon.starsectormarines.battle.perception;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/** Pure deterministic hearing probability and localization rules. */
public final class NoiseDetection {

    public static final float BASE_HEARING_RADIUS = 12f;
    public static final float HEARING_RADIUS_PER_MAGNITUDE = 12f;
    public static final float PROBABILITY_NUMERATOR = 90f;
    public static final float PROBABILITY_DISTANCE_FLOOR = 25f;
    public static final float MIN_LOCALIZATION_ERROR = 1.25f;
    public static final float MAX_LOCALIZATION_ERROR = 4f;
    public static final float MIN_AUDIO_CONFIDENCE = 0.4f;
    public static final float MAX_AUDIO_CONFIDENCE = 0.7f;

    private static final long DETECTION_SALT = 0x4D595DF4D0F33173L;
    private static final long ANGLE_SALT = 0x9E3779B97F4A7C15L;

    private NoiseDetection() {}

    public record Detection(int cellX, int cellY, float confidence) {}

    /** Returns a localized detection, or {@code null} when this squad misses it. */
    public static Detection detect(NoiseEvent event, int squadId,
                                   float listenerX, float listenerY,
                                   NavigationGrid grid) {
        float dx = event.x() - listenerX;
        float dy = event.y() - listenerY;
        float distanceSquared = dx * dx + dy * dy;
        float probability = detectionProbability(event.magnitude(), distanceSquared);
        if (probability <= 0f) return null;
        long key = eventKey(event, squadId);
        if (unitFloat(mix64(key ^ DETECTION_SALT)) >= probability) return null;

        float maxRadius = hearingRadius(event.magnitude());
        float distanceRatio = Math.min(1f,
                (float) Math.sqrt(distanceSquared) / maxRadius);
        float error = MIN_LOCALIZATION_ERROR
                + (MAX_LOCALIZATION_ERROR - MIN_LOCALIZATION_ERROR) * distanceRatio;
        float angle = unitFloat(mix64(key ^ ANGLE_SALT))
                * (float) (Math.PI * 2.0);
        int cellX = clamp((int) Math.floor(event.x() + Math.cos(angle) * error),
                0, grid.getWidth() - 1);
        int cellY = clamp((int) Math.floor(event.y() + Math.sin(angle) * error),
                0, grid.getHeight() - 1);
        float confidence = MAX_AUDIO_CONFIDENCE
                - (MAX_AUDIO_CONFIDENCE - MIN_AUDIO_CONFIDENCE) * distanceRatio;
        return new Detection(cellX, cellY, confidence);
    }

    public static float hearingRadius(float magnitude) {
        return BASE_HEARING_RADIUS + magnitude * HEARING_RADIUS_PER_MAGNITUDE;
    }

    public static float detectionProbability(float magnitude, float distanceSquared) {
        if (magnitude <= 0f) return 0f;
        float radius = hearingRadius(magnitude);
        if (distanceSquared > radius * radius) return 0f;
        return Math.min(1f, PROBABILITY_NUMERATOR * magnitude
                / (distanceSquared + PROBABILITY_DISTANCE_FLOOR));
    }

    private static long eventKey(NoiseEvent event, int squadId) {
        long key = ((long) squadId << 32) ^ event.sourceUnitId();
        key ^= (long) event.emittedTick() * 0xD6E8FEB86659FD93L;
        key ^= (long) Float.floatToIntBits(event.x()) << 16;
        key ^= Float.floatToIntBits(event.y());
        key ^= (long) event.kind().ordinal() * 0xA0761D6478BD642FL;
        return mix64(key);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static float unitFloat(long value) {
        return (float) ((value >>> 40) & 0xFFFFFFL) / 16_777_216f;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
