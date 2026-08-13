package com.dillon.starsectormarines.assets.tilemaps;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Objects;

/**
 * Deterministic CPU derivation of a height map and an OpenGL-convention
 * tangent normal map from an albedo image: linear luminance -&gt; box blur -&gt;
 * percentile-windowed height -&gt; central-difference normals.
 *
 * <p>Vendored from MoonLightEngine's {@code TerrainMaterialDerivationKernel}
 * ({@code asset-pipeline/.../tools/assets/terrain/TerrainMaterialDerivationKernel.java}),
 * repackaged for this mod's build-time pipeline. AO and roughness outputs are
 * dropped — this mod doesn't consume them (see
 * {@code roadmap/surface-relief/stories/s1-derivation-pipeline.md}).
 *
 * <p>{@link EdgeMode} generalizes the source kernel's hardcoded wrap-at-edges
 * filtering: {@link #derive} (whole-image, faithful to the original kernel's
 * behavior) uses {@link EdgeMode#WRAP}; {@link AtlasTileMapDeriver} derives
 * per atlas cell with {@link EdgeMode#CLAMP} so sliced-sheet neighbors (not
 * spatial neighbors in the atlas) never bleed into each other.
 */
public final class TileMapDerivationKernel {

    private static final float[] SRGB_TO_LINEAR = srgbToLinearTable();

    private TileMapDerivationKernel() {
    }

    public enum HeightPolarity {
        BRIGHT_HIGH,
        DARK_HIGH
    }

    /** Boundary handling for the box blur and the normal central-difference. */
    public enum EdgeMode {
        WRAP,
        CLAMP
    }

    public record Recipe(
        HeightPolarity heightPolarity,
        int heightBlurRadius,
        float lowPercentile,
        float highPercentile,
        float normalStrength
    ) {

        public Recipe {
            Objects.requireNonNull(heightPolarity, "heightPolarity");
            if (heightBlurRadius < 0 || heightBlurRadius > 64) {
                throw new IllegalArgumentException("heightBlurRadius must be in [0, 64]");
            }
            if (!Float.isFinite(lowPercentile) || !Float.isFinite(highPercentile)
                || lowPercentile < 0.0f || highPercentile > 1.0f
                || lowPercentile >= highPercentile) {
                throw new IllegalArgumentException(
                    "height percentiles must be finite and satisfy 0 <= low < high <= 1");
            }
            if (!Float.isFinite(normalStrength) || normalStrength < 0.0f) {
                throw new IllegalArgumentException("normalStrength must be finite and nonnegative");
            }
        }
    }

    public record Result(BufferedImage height, BufferedImage normal) {

        public Result {
            Objects.requireNonNull(height, "height");
            Objects.requireNonNull(normal, "normal");
        }
    }

    /**
     * Whole-image derivation with wrap-at-edges filtering — faithful port of
     * the source kernel's behavior for a single tileable texture. Not used by
     * the atlas bake (see {@link AtlasTileMapDeriver}), kept so the vendored
     * algorithm is independently exercisable/testable.
     */
    public static Result derive(BufferedImage albedo, Recipe recipe) {
        Objects.requireNonNull(albedo, "albedo");
        Objects.requireNonNull(recipe, "recipe");
        int width = albedo.getWidth();
        int height = albedo.getHeight();
        if (width < 3 || height < 3) {
            throw new IllegalArgumentException("Terrain derivation input must be at least 3x3");
        }

        float[] luminance = linearLuminance(albedo);
        float[] smoothed = boxBlur(luminance, width, height, recipe.heightBlurRadius(), EdgeMode.WRAP);
        float[] range = percentileRange(smoothed, recipe.lowPercentile(), recipe.highPercentile());
        float[] heightField = normalizeWithRange(smoothed, range[0], range[1], recipe.heightPolarity());

        BufferedImage heightImage = grayImage(heightField, width, height);
        BufferedImage normalImg = normalImage(heightField, width, height, recipe.normalStrength(), EdgeMode.WRAP);
        return new Result(heightImage, normalImg);
    }

    static float[] linearLuminance(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        float[] result = new float[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                float red = SRGB_TO_LINEAR[(argb >>> 16) & 0xff];
                float green = SRGB_TO_LINEAR[(argb >>> 8) & 0xff];
                float blue = SRGB_TO_LINEAR[argb & 0xff];
                result[y * width + x] = 0.2126f * red + 0.7152f * green + 0.0722f * blue;
            }
        }
        return result;
    }

    /**
     * Low/high percentile pair from a pooled value distribution — a whole
     * image's smoothed luminance for {@link #derive}, or every atlas cell's
     * smoothed values concatenated for {@link AtlasTileMapDeriver}. Returned
     * as {@code {low, high}}.
     */
    static float[] percentileRange(float[] values, float lowPercentile, float highPercentile) {
        float[] sorted = values.clone();
        Arrays.sort(sorted);
        float low = sorted[Math.round(lowPercentile * (sorted.length - 1))];
        float high = sorted[Math.round(highPercentile * (sorted.length - 1))];
        return new float[] { low, high };
    }

    static float[] normalizeWithRange(float[] values, float low, float high, HeightPolarity polarity) {
        float range = high - low;
        float[] result = new float[values.length];
        if (!(range > 1.0e-8f)) {
            Arrays.fill(result, 0.5f);
            return result;
        }
        for (int index = 0; index < values.length; index++) {
            float normalized = clamp01((values[index] - low) / range);
            result[index] =
                polarity == HeightPolarity.BRIGHT_HIGH ? normalized : 1.0f - normalized;
        }
        return result;
    }

    static float[] boxBlur(float[] source, int width, int height, int radius, EdgeMode edgeMode) {
        if (radius == 0) {
            return source.clone();
        }
        int diameter = radius * 2 + 1;
        float[] horizontal = new float[source.length];
        float[] result = new float[source.length];
        for (int y = 0; y < height; y++) {
            int row = y * width;
            float sum = 0.0f;
            for (int offset = -radius; offset <= radius; offset++) {
                sum += source[row + index(offset, width, edgeMode)];
            }
            for (int x = 0; x < width; x++) {
                horizontal[row + x] = sum / diameter;
                sum -= source[row + index(x - radius, width, edgeMode)];
                sum += source[row + index(x + radius + 1, width, edgeMode)];
            }
        }
        for (int x = 0; x < width; x++) {
            float sum = 0.0f;
            for (int offset = -radius; offset <= radius; offset++) {
                sum += horizontal[index(offset, height, edgeMode) * width + x];
            }
            for (int y = 0; y < height; y++) {
                result[y * width + x] = sum / diameter;
                sum -= horizontal[index(y - radius, height, edgeMode) * width + x];
                sum += horizontal[index(y + radius + 1, height, edgeMode) * width + x];
            }
        }
        return result;
    }

    static BufferedImage grayImage(float[] values, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int index = 0; index < values.length; index++) {
            int value = unitByte(values[index]);
            image.setRGB(index % width, index / width, argb(value, value, value));
        }
        return image;
    }

    static BufferedImage normalImage(
            float[] heightField,
            int width,
            int height,
            float strength,
            EdgeMode edgeMode
    ) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            int previousY = index(y - 1, height, edgeMode);
            int nextY = index(y + 1, height, edgeMode);
            for (int x = 0; x < width; x++) {
                int previousX = index(x - 1, width, edgeMode);
                int nextX = index(x + 1, width, edgeMode);
                float dx = (heightField[y * width + nextX]
                    - heightField[y * width + previousX]) * 0.5f * strength;
                float dy = (heightField[nextY * width + x]
                    - heightField[previousY * width + x]) * 0.5f * strength;
                float inverseLength = inverseLength(-dx, -dy, 1.0f);
                int red = signedByte(-dx * inverseLength);
                int green = signedByte(-dy * inverseLength);
                int blue = signedByte(inverseLength);
                image.setRGB(x, y, argb(red, green, blue));
            }
        }
        return image;
    }

    private static int unitByte(float value) {
        return Math.round(clamp01(value) * 255.0f);
    }

    private static int signedByte(float value) {
        return Math.round((Math.max(-1.0f, Math.min(1.0f, value)) * 0.5f + 0.5f) * 255.0f);
    }

    private static int argb(int red, int green, int blue) {
        return 0xff000000 | red << 16 | green << 8 | blue;
    }

    /** {@code EdgeMode.WRAP} toroidally wraps (matches the source kernel); {@code EdgeMode.CLAMP} repeats the edge sample instead of crossing into the sheet's next atlas cell. */
    private static int index(int value, int size, EdgeMode edgeMode) {
        if (edgeMode == EdgeMode.CLAMP) {
            return Math.max(0, Math.min(size - 1, value));
        }
        int result = value % size;
        return result < 0 ? result + size : result;
    }

    private static float inverseLength(float x, float y, float z) {
        return (float) (1.0 / Math.sqrt(x * x + y * y + z * z));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float[] srgbToLinearTable() {
        float[] result = new float[256];
        for (int value = 0; value < result.length; value++) {
            double encoded = value / 255.0;
            result[value] = (float) (encoded <= 0.04045
                ? encoded / 12.92
                : Math.pow((encoded + 0.055) / 1.055, 2.4));
        }
        return result;
    }
}
