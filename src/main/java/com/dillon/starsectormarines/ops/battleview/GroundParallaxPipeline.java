package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.GlErrors;
import com.dillon.starsectormarines.render2d.ShaderProgram;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.lwjgl.opengl.Display;

import java.nio.ByteBuffer;
import java.util.Locale;

import static org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glColorMask;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushAttrib;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.GL_TEXTURE2;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;

/**
 * S2/S3 ground-relief orchestrator: redirects the
 * {@code RenderLayer#GROUND} drain into color, material-height, and normal FBOs instead of the
 * backbuffer, then composites them through a fullscreen offset-limited
 * parallax + event-light bump shader before the rest of the frame draws on
 * top. See {@code surface-relief-nouns.md}.
 *
 * <p>The FBO set is sized to the battle grid VIEWPORT (framebuffer px), not the
 * whole screen — {@link BattleCamera}'s {@code vpX/vpY/vpW/vpH} are already
 * the UI-space rect {@link GroundRenderSystem} and {@link GroundHeightPass}
 * emit their quads into (via {@code cellToScreenX/Y}), so the FBO's ortho is
 * set to that SAME UI-space rect ({@code (vpX, vpX+vpW, vpY, vpY+vpH)}, not
 * {@code (0, fboPxW)}) — the existing GROUND/height-pass draw calls need zero
 * coordinate translation to land correctly in the FBO. The composite quad then
 * draws over that same rect on the backbuffer, under whatever UI ortho is
 * already active (matching {@code DecalAccumulator.blit}'s pattern) — no extra
 * matrix setup needed there either.
 *
 * <p><strong>Fail-soft, mirrors {@link com.dillon.starsectormarines.render2d.DecalAccumulator}
 * and {@link ShaderProgram}.</strong> A shader compile/link failure or an
 * incomplete FBO logs once and flips {@link #broken}; every subsequent call —
 * and any mid-frame failure this frame — falls back to draining GROUND straight
 * to the backbuffer, i.e. pixel-identical to the pipeline never having run.
 */
public final class GroundParallaxPipeline {

    private static final Logger LOG = Global.getLogger(GroundParallaxPipeline.class);

    // ---- shader tuning (playtest-tunable; see surface-relief-nouns.md) -------

    /**
     * UV-space structural offset per <b>metre</b> of macro height and eye
     * direction.
     *
     * <p>Per metre, not per unit of a 0..1 scale: macro height is now measured
     * against a real datum, so the dial is a rate against a real quantity. The
     * default is set so a 3 m wall displaces exactly as far as it did under the
     * old dimensionless scale — what re-proportions is everything shorter,
     * which previously sat on invented numbers (a building floor was 30% of a
     * wall; it is now the 10% a slab's step actually is).
     */
    public static final float MIN_STRENGTH = 0f;
    public static final float MAX_STRENGTH = 0.667f;
    public static final float DEFAULT_STRENGTH = 0.0008f;
    /** Surface relief retains the same broad experiment range but has an independent live control. */
    public static final float MIN_SURFACE_STRENGTH = 0f;
    public static final float MAX_SURFACE_STRENGTH = 5.0f;
    public static final float DEFAULT_SURFACE_STRENGTH = 0.006f;
    /** Animated water displacement, expressed as a fraction of one world cell. */
    public static final float MIN_WATER_WAVE_AMPLITUDE = 0f;
    public static final float MAX_WATER_WAVE_AMPLITUDE = 0.5f;
    public static final float DEFAULT_WATER_WAVE_AMPLITUDE = 0.08f;
    /** Additive S3 bump-light multiplier. Zero disables event lighting without disabling S2. */
    public static final float MIN_LIGHTING_STRENGTH = 0f;
    public static final float MAX_LIGHTING_STRENGTH = 2f;
    public static final float DEFAULT_LIGHTING_STRENGTH = 1f;
    /** Fake-perspective eye height above the screen plane, in the same normalized units as the UV-space screen-center vector. */
    /** Package-visible so the headless pixel-reference test cannot drift from the shader uniform. */
    static final float EYE_HEIGHT = 1.2f;
    static final float WATER_MACRO_SCALE = 0.2f;
    static final float WATER_MICRO_SCALE = 0.35f;
    static final float WATER_INTERIOR_WAVE_SCALE = 0.25f;
    static final float WATER_FOAM_AMOUNT = 0.10f;
    /**
     * Occlusion samples the sun march takes per fragment.
     *
     * <p>Fixed rather than derived so the cost is the same at every sun angle.
     * Paired with {@link #SHADOW_STEP_MAX_CELLS} it also decides what a very low
     * sun does: the march covers {@code steps × step} cells and a longer
     * shadow is <em>truncated</em> rather than sampled coarsely, because a
     * shadow that stops short reads as a shadow while one sampled past its
     * Nyquist limit reads as dashes.
     */
    static final int SHADOW_STEPS = 20;

    /** Coarsest march step. Half a cell keeps a one-cell wall from being stepped over. */
    static final float SHADOW_STEP_MAX_CELLS = 0.5f;

    /**
     * Metres of occluder overshoot that take a fragment from lit to fully
     * shadowed. A hard test gives a shadow edge that crawls one cell at a time
     * as the camera pans; this is the penumbra that hides the height field's
     * own cell quantization.
     */
    static final float SHADOW_SOFTNESS_METERS = 0.55f;

    /** Longest march the sun is allowed to ask for, and therefore the widest the height target's margin can grow. */
    static final float MAX_SHADOW_RANGE_CELLS = 24f;

    /**
     * Height-target margin is rounded up to a multiple of this many cells.
     *
     * <p>The margin follows the sun's elevation, and the elevation is a live
     * dial. Quantizing means dragging that dial reallocates the target at a few
     * thresholds instead of on almost every frame of the drag.
     */
    static final int SHADOW_PAD_QUANTUM_CELLS = 4;

    private static final String[] LIGHT_POSITION_UNIFORMS =
            indexedUniformNames("lightPosRadius");
    private static final String[] LIGHT_COLOR_UNIFORMS =
            indexedUniformNames("lightColorIntensity");

    private static final String VERTEX_SRC = ""
            + "#version 120\n"
            + "varying vec2 vUv;\n"
            + "void main() {\n"
            + "    vUv = gl_MultiTexCoord0.xy;\n"
            + "    gl_FrontColor = gl_Color;\n"
            + "    gl_Position = ftransform();\n"
            + "}\n";

    private static final String FRAGMENT_SRC = ""
            + "#version 120\n"
            + "uniform sampler2D colorTex;\n"
            + "uniform sampler2D heightTex;\n"
            + "uniform sampler2D normalTex;\n"
            + "uniform vec2 screenCenter;\n"
            + "uniform vec2 heightCellUv;\n"
            + "uniform float macroDatum;\n"
            + "uniform float macroMetersSpan;\n"
            + "uniform vec2 sunDir;\n"
            + "uniform float sunRisePerCell;\n"
            + "uniform float shadowRangeCells;\n"
            + "uniform float shadowStrength;\n"
            + "uniform float shadowSoftnessMeters;\n"
            + "uniform vec3 shadowTint;\n"
            + "uniform float eyeHeight;\n"
            + "uniform float structureStrength;\n"
            + "uniform float surfaceStrength;\n"
            + "uniform float microScale;\n"
            + "uniform float waterWaveAmplitude;\n"
            + "uniform float waterMacroScale;\n"
            + "uniform float waterMicroScale;\n"
            + "uniform float waterInteriorWaveScale;\n"
            + "uniform float waterFoamAmount;\n"
            + "uniform float waveTime;\n"
            + "uniform vec2 worldCenter;\n"
            + "uniform vec2 visibleCells;\n"
            + "uniform vec2 cellUv;\n"
            + "uniform float aspect;\n"
            + "uniform float lightingStrength;\n"
            + "uniform vec4 lightPosRadius[" + GroundLightService.MAX_SHADER_LIGHTS + "];\n"
            + "uniform vec4 lightColorIntensity[" + GroundLightService.MAX_SHADER_LIGHTS + "];\n"
            + "varying vec2 vUv;\n"
            // The height target is WIDER than the view: it carries a margin of
            // off-screen cells so an occluder just outside the viewport still
            // casts into it. So height is addressed by world position, not by
            // the composite's own UV -- the two spaces are no longer the same.
            + "vec2 worldOf(vec2 uv) {\n"
            + "    return worldCenter + (uv - vec2(0.5)) * visibleCells;\n"
            + "}\n"
            + "vec2 heightUvOf(vec2 world) {\n"
            + "    return vec2(0.5) + (world - worldCenter) * heightCellUv;\n"
            + "}\n"
            + "float macroMetersAt(vec2 world) {\n"
            + "    return (texture2D(heightTex, heightUvOf(world)).r - macroDatum) * macroMetersSpan;\n"
            + "}\n"
            + "void main() {\n"
            + "    vec2 worldCell = worldOf(vUv);\n"
            + "    vec4 meta = texture2D(heightTex, heightUvOf(worldCell));\n"
            + "    float macroMeters = (meta.r - macroDatum) * macroMetersSpan;\n"
            + "    float water = meta.b;\n"
            + "    float shore = meta.a;\n"
            + "    float macroMaterial = mix(1.0, waterMacroScale, water);\n"
            + "    float microMaterial = mix(1.0, waterMicroScale, water);\n"
            + "    float relief = macroMeters * structureStrength * macroMaterial\n"
            + "            + (meta.g - 0.5) * microScale * surfaceStrength * microMaterial;\n"
            // Eye vector in an isotropic (aspect-corrected) space, so equal
            // screen-pixel distances from center pull equally hard on both axes;
            // the offset converts back to UV space before sampling.
            + "    vec2 d = (screenCenter - vUv) * vec2(aspect, 1.0);\n"
            + "    vec3 eye = normalize(vec3(d, eyeHeight));\n"
            // Offset-limited form (Welsh 2004) -- no divide by eye.z, so shallow
            // eye vectors at screen edges can't explode into shimmer.
            + "    vec2 baseOff = relief * eye.xy * vec2(1.0 / aspect, 1.0);\n"
            + "    vec2 waves = vec2(\n"
            + "            sin(dot(worldCell, vec2(2.15, 0.65)) + waveTime * 1.35),\n"
            + "            cos(dot(worldCell, vec2(-0.45, 2.40)) - waveTime * 1.10));\n"
            + "    float shoreWaveScale = mix(waterInteriorWaveScale, 1.0, shore);\n"
            + "    vec2 waterOff = waves * cellUv * waterWaveAmplitude * shoreWaveScale;\n"
            + "    vec2 totalOff = baseOff + waterOff * water;\n"
            + "    vec2 offsetUv = clamp(vUv + totalOff, 0.0, 1.0);\n"
            // Water samples may move within water, but never borrow a land
            // texel. Backtracking makes the shoreline stable rather than
            // allowing tiles to vanish as the camera or wave phase moves.
            + "    if (water > 0.5 && texture2D(heightTex, heightUvOf(worldOf(offsetUv))).b < 0.5) {\n"
            + "        vec2 halfUv = clamp(vUv + totalOff * 0.5, 0.0, 1.0);\n"
            + "        offsetUv = texture2D(heightTex, heightUvOf(worldOf(halfUv))).b >= 0.5 ? halfUv : vUv;\n"
            + "    }\n"
            + "    vec4 color = texture2D(colorTex, offsetUv);\n"
            + "    float crest = sin(dot(worldCell, vec2(1.70, 0.80)) - waveTime * 2.20) * 0.5 + 0.5;\n"
            + "    float foam = water * shore * smoothstep(0.72, 1.0, crest)\n"
            + "            * waterFoamAmount * step(0.0001, waterWaveAmplitude);\n"
            + "    color.rgb = mix(color.rgb, vec3(0.76, 0.90, 1.0), foam);\n"
            + "    vec3 normal = texture2D(normalTex, offsetUv).rgb * 2.0 - 1.0;\n"
            // Derived maps use image-space +Y down; the composed ground uses
            // world/screen +Y up, so invert the decoded green component.
            + "    normal = normalize(vec3(normal.x, -normal.y, normal.z));\n"
            // Cast shadow: walk toward the sun over the height field, raising a
            // ray by tan(elevation) per cell. Anything standing above that ray
            // is between this ground and the sun. Both sides are metres and one
            // cell is one metre, so there is no scale factor to get wrong.
            + "    float shadow = 0.0;\n"
            + "    if (shadowStrength > 0.0) {\n"
            + "        float stepCells = min(shadowRangeCells / float(" + SHADOW_STEPS + "), "
            + glsl(SHADOW_STEP_MAX_CELLS) + ");\n"
            + "        for (int i = 1; i <= " + SHADOW_STEPS + "; i++) {\n"
            + "            float t = float(i) * stepCells;\n"
            + "            float occluder = macroMetersAt(worldCell + sunDir * t);\n"
            + "            float ray = macroMeters + t * sunRisePerCell;\n"
            + "            shadow = max(shadow,\n"
            + "                    clamp((occluder - ray) / shadowSoftnessMeters, 0.0, 1.0));\n"
            + "        }\n"
            + "    }\n"
            // Shadow multiplies the sunlit image; event lights are added AFTER,
            // so a muzzle flash still lights the ground it is standing on.
            + "    color.rgb *= mix(vec3(1.0), shadowTint, shadow * shadowStrength);\n"
            + "    vec3 addedLight = vec3(0.0);\n"
            + "    for (int i = 0; i < " + GroundLightService.MAX_SHADER_LIGHTS + "; i++) {\n"
            + "        vec4 pr = lightPosRadius[i];\n"
            + "        vec4 ci = lightColorIntensity[i];\n"
            + "        vec2 toLight = pr.xy - worldCell;\n"
            + "        float radial = clamp(1.0 - length(toLight) / max(pr.w, 0.001), 0.0, 1.0);\n"
            + "        vec3 lightDir = normalize(vec3(toLight, pr.z));\n"
            + "        float lambert = max(dot(normal, lightDir), 0.0);\n"
            + "        addedLight += ci.rgb * ci.a * radial * radial * lambert;\n"
            + "    }\n"
            + "    color.rgb += addedLight * (vec3(0.18) + color.rgb * 0.82) * lightingStrength;\n"
            + "    gl_FragColor = color * gl_Color;\n"
            + "}\n";

    private final ShaderProgram shader = new ShaderProgram("GroundParallax", VERTEX_SRC, FRAGMENT_SRC);
    private final GroundMicroHeightSampler materialSampler;
    private final GroundHeightPass heightPass;
    private final GroundNormalPass normalPass;
    private final GroundLightService lights;

    /** Runtime-tunable through the battle debug panel; read every composite. */
    private float structureStrength = DEFAULT_STRENGTH;
    private float surfaceStrength = DEFAULT_SURFACE_STRENGTH;
    private float waterWaveAmplitude = DEFAULT_WATER_WAVE_AMPLITUDE;
    private float lightingStrength = DEFAULT_LIGHTING_STRENGTH;
    /**
     * Where the sun is. Held rather than owned: the ground composite is one
     * caster among several and must not be the place a bearing lives.
     */
    private final SunLight sun;
    private float waveTimeSeconds;

    private boolean broken;

    private int colorFbo, colorTex;
    private int heightFbo, heightTex;
    private int normalFbo, normalTex;
    private int fboPxW, fboPxH;
    /** Height target size, which is the viewport plus {@link #heightPadCells} of shadow margin on every side. */
    private int heightPxW, heightPxH;
    private int heightPadCells;
    /** {@link #heightPadCells} in the UI-space units the FBO ortho and the ground passes are drawn in. */
    private float heightPadUi;

    /** UI-space rect the FBOs' ortho + the composite quad are drawn against — cached from the camera each call. */
    private float vpX, vpY, vpW, vpH;

    /** UI-space size of one world cell — cached alongside the viewport rect; the shadow margin is measured in cells. */
    private float cellPxUi = 1f;

    /** Sampled once, like {@code DecalAccumulator.uiFboBinding} — never a per-frame {@code glGet*} (async-renderer stall). */
    private int uiFboBinding = -1;
    private boolean uiFboSampled;

    /** Compatibility constructor; without a sprite registry sliced sheets remain macro-only/fallback. */
    public GroundParallaxPipeline() {
        this(new GroundMicroHeightSampler(() -> null, () -> null), new GroundLightService(),
                new SunLight());
    }

    public GroundParallaxPipeline(BattleSprites sprites, SunLight sun) {
        this(new GroundMicroHeightSampler(sprites), new GroundLightService(), sun);
    }

    GroundParallaxPipeline(BattleSprites sprites, GroundLightService lights, SunLight sun) {
        this(new GroundMicroHeightSampler(sprites), lights, sun);
    }

    private GroundParallaxPipeline(GroundMicroHeightSampler materialSampler,
                                   GroundLightService lights, SunLight sun) {
        this.materialSampler = materialSampler;
        this.heightPass = new GroundHeightPass(materialSampler);
        this.normalPass = new GroundNormalPass(materialSampler);
        this.lights = lights;
        this.sun = sun;
    }

    public float parallaxStrength() { return structureStrength; }

    public float surfaceStrength() { return surfaceStrength; }

    public float waterWaveAmplitude() { return waterWaveAmplitude; }

    public float lightingStrength() { return lightingStrength; }

    /** The scene's sun, for a caller that needs the same one this composite casts from. */
    public SunLight sun() { return sun; }

    /** Applies immediately to the next rendered frame. */
    public void setParallaxStrength(float strength) {
        if (Float.isNaN(strength)) return;
        this.structureStrength = clamp(strength, MIN_STRENGTH, MAX_STRENGTH);
    }

    /** Applies immediately to the next rendered frame. */
    public void setSurfaceStrength(float strength) {
        if (Float.isNaN(strength)) return;
        this.surfaceStrength = clamp(strength, MIN_SURFACE_STRENGTH, MAX_SURFACE_STRENGTH);
    }

    /** Applies immediately to the next rendered frame. */
    public void setWaterWaveAmplitude(float amplitude) {
        if (Float.isNaN(amplitude)) return;
        this.waterWaveAmplitude = clamp(amplitude,
                MIN_WATER_WAVE_AMPLITUDE, MAX_WATER_WAVE_AMPLITUDE);
    }

    /** Applies immediately to the next rendered frame. */
    public void setLightingStrength(float strength) {
        if (Float.isNaN(strength)) return;
        this.lightingStrength = clamp(strength, MIN_LIGHTING_STRENGTH, MAX_LIGHTING_STRENGTH);
    }

    /**
     * How far the sun's march has to reach, in cells, for the tallest surface
     * the installed mapping can place. Also the size of the height target's
     * margin, since an occluder has to be in the texture to cast out of it.
     */
    float shadowRangeCells() {
        if (!sun.casts()) return 0f;
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        float tallest = mapping != null ? mapping.tallestMacroHeightMeters()
                : GenMappingRegistry.DEFAULT_WALL_MACRO_HEIGHT_METERS;
        if (tallest <= 0f) return 0f;
        return clamp(sun.reachCells(tallest), 0f, MAX_SHADOW_RANGE_CELLS);
    }

    /**
     * Cells of margin the height target carries beyond the viewport. Collapses
     * to the ordinary geometry halo when shadows are off, so the dial at zero
     * costs neither fill nor memory.
     */
    int heightPadCells() {
        if (!sun.casts()) return VisibleCellRect.GEOMETRY_MARGIN_CELLS;
        int wanted = (int) Math.ceil(shadowRangeCells()) + VisibleCellRect.GEOMETRY_MARGIN_CELLS;
        int quantized = ((wanted + SHADOW_PAD_QUANTUM_CELLS - 1) / SHADOW_PAD_QUANTUM_CELLS)
                * SHADOW_PAD_QUANTUM_CELLS;
        return Math.max(VisibleCellRect.GEOMETRY_MARGIN_CELLS, quantized);
    }

    /**
     * Renders {@code RenderLayer#GROUND} through the parallax pipeline:
     * color, metadata-height, and normal FBOs, then the composite blit. {@code drainColor} is the
     * caller's {@code BattleRenderer.drainLayer(GROUND)} — invoked inside the
     * color FBO bracket on the happy path, or directly against the backbuffer
     * (its normal target) on ANY failure, so the layer always ends up drawn
     * exactly once regardless of which path ran.
     */
    void renderGround(RenderContext rc, Runnable drainColor) {
        if (broken || !shader.ensure()) {
            drainColor.run();
            return;
        }

        vpX = rc.camera.vpX();
        vpY = rc.camera.vpY();
        vpW = rc.camera.vpW();
        vpH = rc.camera.vpH();
        cellPxUi = Math.max(0.0001f, rc.camera.cellPxSize());
        if (vpW <= 0f || vpH <= 0f) {
            drainColor.run();
            return;
        }

        ensureFbo();
        if (broken) {
            drainColor.run();
            return;
        }

        if (!renderColorFbo(drainColor)) {
            drainColor.run();
            return;
        }
        if (!renderHeightFbo(rc)) {
            drainColor.run();
            return;
        }
        if (!renderNormalFbo(rc)) {
            drainColor.run();
            return;
        }
        waveTimeSeconds = (waveTimeSeconds + Math.max(0f, rc.realDt)) % 4096f;
        if (!composite(rc)) {
            drainColor.run();
        }
    }

    /** Releases GPU resources. Call from the owning screen's detach path — mirrors {@code DecalAccumulator.dispose}. */
    public void dispose() {
        releaseFbos();
        shader.dispose();
        heightPass.dispose();
        normalPass.dispose();
        materialSampler.invalidate();
    }

    private void releaseFbos() {
        if (colorFbo != 0) { glDeleteFramebuffers(colorFbo); colorFbo = 0; }
        if (colorTex != 0) { glDeleteTextures(colorTex); colorTex = 0; }
        if (heightFbo != 0) { glDeleteFramebuffers(heightFbo); heightFbo = 0; }
        if (heightTex != 0) { glDeleteTextures(heightTex); heightTex = 0; }
        if (normalFbo != 0) { glDeleteFramebuffers(normalFbo); normalFbo = 0; }
        if (normalTex != 0) { glDeleteTextures(normalTex); normalTex = 0; }
        fboPxW = 0;
        fboPxH = 0;
        heightPxW = 0;
        heightPxH = 0;
    }

    // ------------------------------------------------------------------

    private boolean renderColorFbo(Runnable drainColor) {
        return withFboBound(colorFbo, fboPxW, fboPxH, 0f, () -> {
            glColorMask(true, true, true, true);
            glClearColor(0f, 0f, 0f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            drainColor.run();
        });
    }

    private boolean renderHeightFbo(RenderContext rc) {
        BattleSimulation sim = rc.sim;
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        // Rebuilt per frame rather than cached: a roof caves in and a wall is
        // breached mid-battle, and a field held across frames would keep
        // shadowing a building that is no longer there. It gathers from the
        // building registry and the barrier list, so the cost is the number of
        // roofed cells rather than the size of the map.
        MacroReliefField relief =
                new MacroReliefField(topology, grid, sim.getBuildings(), mapping);
        int margin = heightPadCells;
        // Clears to the ground datum, not to mid-channel: off-grid texels have
        // to read as flat ground or the margin would ring the map in a 16 m
        // cliff and shadow every edge.
        return withFboBound(heightFbo, heightPxW, heightPxH, heightPadUi, () -> {
            glColorMask(true, true, true, true);
            glClearColor(GroundHeightPass.MACRO_DATUM, 0.5f, 0f, 0f);
            glClear(GL_COLOR_BUFFER_BIT);
            heightPass.render(rc.camera, grid, topology, mapping, relief, margin);
        });
    }

    private boolean renderNormalFbo(RenderContext rc) {
        BattleSimulation sim = rc.sim;
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        return withFboBound(normalFbo, fboPxW, fboPxH, 0f, () -> {
            glColorMask(true, true, true, true);
            glClearColor(0.5f, 0.5f, 1f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            normalPass.render(rc.camera, grid, topology);
        });
    }

    private boolean composite(RenderContext rc) {
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        try {
            // Bind AFTER the push so glPopAttrib restores the caller's texture
            // bindings/active unit (GL_TEXTURE_BIT covers both) -- same order as
            // DecalAccumulator.blit.
            glActiveTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, normalTex);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, heightTex);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, colorTex);

            shader.use();
            shader.set1i("colorTex", 0);
            shader.set1i("heightTex", 1);
            shader.set1i("normalTex", 2);
            shader.set2f("screenCenter", 0.5f, 0.5f);
            shader.set1f("eyeHeight", EYE_HEIGHT);
            shader.set1f("structureStrength", structureStrength);
            shader.set1f("surfaceStrength", surfaceStrength);
            shader.set1f("microScale", GroundHeightPass.MICRO_SCALE);
            shader.set1f("waterWaveAmplitude", waterWaveAmplitude);
            shader.set1f("waterMacroScale", WATER_MACRO_SCALE);
            shader.set1f("waterMicroScale", WATER_MICRO_SCALE);
            shader.set1f("waterInteriorWaveScale", WATER_INTERIOR_WAVE_SCALE);
            shader.set1f("waterFoamAmount", WATER_FOAM_AMOUNT);
            shader.set1f("waveTime", waveTimeSeconds);
            shader.set2f("worldCenter", rc.camera.panCellX(), rc.camera.panCellY());
            shader.set2f("visibleCells", vpW / cellPxUi, vpH / cellPxUi);
            shader.set2f("cellUv", cellPxUi / vpW, cellPxUi / vpH);
            // Height UV per world cell. Its denominator is the PADDED extent,
            // which is what makes heightUvOf() land on the right texel now that
            // the height target is bigger than the view it composites into.
            shader.set2f("heightCellUv",
                    cellPxUi / (vpW + 2f * heightPadUi),
                    cellPxUi / (vpH + 2f * heightPadUi));
            shader.set1f("macroDatum", GroundHeightPass.MACRO_DATUM);
            shader.set1f("macroMetersSpan", GroundHeightPass.MACRO_METERS_SPAN);
            shader.set2f("sunDir", sun.dirX(), sun.dirY());
            shader.set1f("sunRisePerCell", sun.risePerCell());
            shader.set1f("shadowRangeCells", shadowRangeCells());
            shader.set1f("shadowStrength", sun.shadowStrength());
            shader.set1f("shadowSoftnessMeters", SHADOW_SOFTNESS_METERS);
            shader.set3f("shadowTint", SunLight.TINT_R, SunLight.TINT_G, SunLight.TINT_B);
            shader.set1f("aspect", fboPxW / (float) fboPxH);
            shader.set1f("lightingStrength", lightingStrength);
            uploadLights(rc.camera);

            glEnable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glColor4f(1f, 1f, 1f, rc.alphaMult);

            float x0 = vpX, y0 = vpY, x1 = vpX + vpW, y1 = vpY + vpH;
            glBegin(GL_QUADS);
            glTexCoord2f(0f, 0f); glVertex2f(x0, y0);
            glTexCoord2f(1f, 0f); glVertex2f(x1, y0);
            glTexCoord2f(1f, 1f); glVertex2f(x1, y1);
            glTexCoord2f(0f, 1f); glVertex2f(x0, y1);
            glEnd();
            return true;
        } catch (RuntimeException e) {
            LOG.error("GroundParallaxPipeline composite draw failed; disabling effect", e);
            broken = true;
            return false;
        } finally {
            // The program binding lives outside the attrib stack; texture
            // bindings/active unit are restored by the pop.
            ShaderProgram.useNone();
            glPopAttrib();
        }
    }

    /**
     * Runs {@code body} with {@code fbo} bound, viewport sized to that FBO
     * ({@code pxW}×{@code pxH}), and an ortho spanning the UI-space rect the
     * GROUND/height quads are already emitted into, grown by {@code padUi} on
     * every side ({@code (vpX-padUi, vpX+vpW+padUi, ...)} — see class doc; only
     * the height target pads, and it pads symmetrically so its centre stays the
     * camera's) — not {@code (0, fboPxW)} like {@code DecalAccumulator}, which owns
     * its own FBO-local coordinate space instead of replaying existing UI-space
     * draw calls. State-save pattern is otherwise identical to
     * {@code DecalAccumulator.withFboBound}. Returns {@code false} (and flips
     * {@link #broken}) if {@code body} throws.
     */
    private boolean withFboBound(int fbo, int pxW, int pxH, float padUi, Runnable body) {
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glMatrixMode(GL_PROJECTION); glPushMatrix();
        glMatrixMode(GL_MODELVIEW);  glPushMatrix();
        glMatrixMode(GL_TEXTURE);    glPushMatrix();

        if (!uiFboSampled) {
            uiFboBinding = glGetInteger(GL_FRAMEBUFFER_BINDING);
            uiFboSampled = true;
        }

        boolean ok = true;
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glViewport(0, 0, pxW, pxH);
            glDisable(GL_SCISSOR_TEST);
            glDisable(GL_DEPTH_TEST);
            glColorMask(true, true, true, true);
            glUseProgram(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

            glMatrixMode(GL_PROJECTION); glLoadIdentity();
            glOrtho(vpX - padUi, vpX + vpW + padUi, vpY - padUi, vpY + vpH + padUi, -1, 1);
            glMatrixMode(GL_MODELVIEW);  glLoadIdentity();

            body.run();
        } catch (RuntimeException e) {
            LOG.error("GroundParallaxPipeline FBO body failed; disabling effect", e);
            broken = true;
            ok = false;
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, uiFboBinding);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glUseProgram(0);
            glMatrixMode(GL_TEXTURE);    glPopMatrix();
            glMatrixMode(GL_MODELVIEW);  glPopMatrix();
            glMatrixMode(GL_PROJECTION); glPopMatrix();
            glPopAttrib();
        }
        return ok;
    }

    /**
     * Sanity bound on FBO side length. {@link #renderGround} is reachable with a
     * WORLD-UNIT {@link BattleCamera} (the vanilla-combat-bridge backdrop uses one —
     * see {@code combathybrid.bridge.GroundSceneBackdrop}), whose {@code vpW/vpH}
     * aren't UI pixels at all; the {@code sx/sy} scale below assumes they are. Rather
     * than special-case that camera, clamp: an absurd size degrades to the fallback
     * path exactly like any other failure, instead of a multi-GB allocation attempt.
     */
    private static final int MAX_FBO_DIM = 8192;

    private void ensureFbo() {
        float sx = Display.getWidth() / Math.max(1f, Global.getSettings().getScreenWidth());
        float sy = Display.getHeight() / Math.max(1f, Global.getSettings().getScreenHeight());
        int wantW = Math.max(1, Math.round(vpW * sx));
        int wantH = Math.max(1, Math.round(vpH * sy));
        // Only the height target pads. Padding colour and normal too would
        // widen the whole GROUND drain -- every tile, prop and decal drawn over
        // an area several times the view -- to feed a march that never reads
        // them. The shader addresses height by world position instead.
        int padCells = heightPadCells();
        float padUi = padCells * cellPxUi;
        int wantHeightW = Math.max(1, wantW + 2 * Math.round(padUi * sx));
        int wantHeightH = Math.max(1, wantH + 2 * Math.round(padUi * sy));
        if (wantW > MAX_FBO_DIM || wantH > MAX_FBO_DIM
                || wantHeightW > MAX_FBO_DIM || wantHeightH > MAX_FBO_DIM) {
            LOG.warn("GroundParallaxPipeline: refusing " + wantW + "x" + wantH
                    + " (height " + wantHeightW + "x" + wantHeightH
                    + ") FBO (over " + MAX_FBO_DIM + "px) -- camera isn't UI-space; disabling effect for this view");
            broken = true;
            return;
        }
        if (colorFbo != 0 && wantW == fboPxW && wantH == fboPxH
                && wantHeightW == heightPxW && wantHeightH == heightPxH) {
            heightPadUi = padUi;
            heightPadCells = padCells;
            return;
        }

        releaseFbos(); // shader program is independent of FBO size -- left alone, no recompile on resize
        fboPxW = wantW;
        fboPxH = wantH;
        heightPxW = wantHeightW;
        heightPxH = wantHeightH;
        heightPadUi = padUi;
        heightPadCells = padCells;

        int[] color = buildFbo(fboPxW, fboPxH);
        if (broken) return;
        colorFbo = color[0];
        colorTex = color[1];

        int[] height = buildFbo(heightPxW, heightPxH);
        if (broken) {
            glDeleteFramebuffers(colorFbo);
            glDeleteTextures(colorTex);
            colorFbo = 0;
            colorTex = 0;
            return;
        }
        heightFbo = height[0];
        heightTex = height[1];

        int[] normal = buildFbo(fboPxW, fboPxH);
        if (broken) {
            releaseFbos();
            return;
        }
        normalFbo = normal[0];
        normalTex = normal[1];

        LOG.debug("GroundParallaxPipeline FBOs (" + colorFbo + "/" + heightFbo + "/"
                + normalFbo + ") complete at " + fboPxW + "x" + fboPxH
                + " (height " + heightPxW + "x" + heightPxH
                + ", " + heightPadCells + "-cell sun margin)");
    }

    /** Builds one RGBA8 FBO + color-attachment texture at {@code pxW}x{@code pxH}. {@code {fbo, tex}}. */
    private int[] buildFbo(int pxW, int pxH) {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        GlErrors.clear();
        // Null, not a zeroed buffer: this sizes the texture storage without
        // uploading anything. LWJGL 2 supports it by design -- the ByteBuffer
        // overload skips its buffer check on null and passes address 0. Every
        // target here is cleared at the top of the frame that draws it, and to
        // a datum rather than to zero, so the uploaded pixels were never read.
        // Rebuilds follow the view size and the sun's height pad, which made
        // the discarded buffers worth hundreds of MB of direct memory mid-battle.
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, pxW, pxH, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GlErrors.check("glTexImage2D (ground parallax FBO)");
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        int prevFbo = glGetInteger(GL_FRAMEBUFFER_BINDING);
        int fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);

        if (status != GL_FRAMEBUFFER_COMPLETE) {
            LOG.error("GroundParallaxPipeline FBO incomplete: 0x" + Integer.toHexString(status)
                    + " (size " + pxW + "x" + pxH + ")");
            glDeleteFramebuffers(fbo);
            glDeleteTextures(tex);
            broken = true;
            return new int[]{0, 0};
        }
        return new int[]{fbo, tex};
    }

    private void uploadLights(BattleCamera camera) {
        int count = lightingStrength > 0f ? lights.selectNearest(camera) : 0;
        for (int i = 0; i < GroundLightService.MAX_SHADER_LIGHTS; i++) {
            GroundLightService.Light light = i < count ? lights.selected(i) : null;
            if (light == null) {
                shader.set4f(LIGHT_POSITION_UNIFORMS[i], 0f, 0f, 1f, 1f);
                shader.set4f(LIGHT_COLOR_UNIFORMS[i], 0f, 0f, 0f, 0f);
                continue;
            }
            shader.set4f(LIGHT_POSITION_UNIFORMS[i],
                    light.x, light.y, light.height, light.radius);
            shader.set4f(LIGHT_COLOR_UNIFORMS[i],
                    light.color.getRed() / 255f,
                    light.color.getGreen() / 255f,
                    light.color.getBlue() / 255f,
                    light.effectiveIntensity());
        }
    }

    private static String[] indexedUniformNames(String base) {
        String[] names = new String[GroundLightService.MAX_SHADER_LIGHTS];
        for (int i = 0; i < names.length; i++) names[i] = base + "[" + i + "]";
        return names;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * A Java float as a GLSL float literal. Locale-pinned because a comma
     * decimal separator compiles to a syntax error on the machine that has one
     * and nowhere else.
     */
    private static String glsl(float value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
