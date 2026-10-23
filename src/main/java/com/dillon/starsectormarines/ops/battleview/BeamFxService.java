package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Presentation-owned lingering hitscan beams, independent of ballistic arrival timing. */
public final class BeamFxService {

    /** At most this many energy knots ride one beam, however long the beam or fine its grain. */
    static final int MAX_KNOTS = 96;

    private final List<LiveBeam> beams = new ArrayList<>();
    /** Presentation time the beam grain scrolls and throbs on; advanced only by {@link #advance}. */
    private float clockSec;

    /** Captures an authored beam at fire time. Ordinary short tracers stay in {@link ShotRenderService}. */
    public void spawn(ShotEvent shot) {
        if (shot == null || !(ShotFx.of(shot).body() instanceof ShotFx.Tracer tracer)) return;
        WeaponDef.BeamStyle style = tracer.style();
        if (style.lifetimeSec() <= 0f) return;
        Color core = tracer.color() != null
                ? tracer.color() : ShotFx.defaultTracerColor(shot.shooterFaction);
        beams.add(new LiveBeam(shot.fromX, shot.visualFromY(), shot.toX, shot.visualToY(),
                core, style, style.lifetimeSec(), style.lifetimeSec()));
    }

    public void advance(float dt) {
        if (dt <= 0f) return;
        clockSec += dt;
        for (int i = beams.size() - 1; i >= 0; i--) {
            LiveBeam beam = beams.get(i);
            beam.remaining -= dt;
            if (beam.remaining <= 0f) beams.remove(i);
        }
    }

    public void collect(BattleCamera camera, DrawList out, float alphaMult) {
        if (camera == null || out == null) return;
        for (LiveBeam beam : beams) {
            float lifeT = clamp01(beam.remaining / beam.lifetime);
            // Hold the heavy lance at full presence for the opening 60%, then let the
            // last 40% become the afterimage instead of washing the whole second away.
            float opacity = clamp01(lifeT / 0.40f);
            float pulse = pulse(beam.style.pulseCycles(), lifeT);
            WeaponDef.BeamStyle style = beam.style;
            if (style.texture() != null) {
                collectTextured(camera, out, beam, opacity * alphaMult);
                continue;
            }
            if (style.glowColor() != null) {
                Color glow = style.glowColor();
                out.addLine(RenderLayer.SHOTS,
                        camera.cellToScreenX(beam.fromX), camera.cellToScreenY(beam.fromY),
                        camera.cellToScreenX(beam.toX), camera.cellToScreenY(beam.toY),
                        style.glowWidthPx(),
                        channel(glow.getRed()), channel(glow.getGreen()), channel(glow.getBlue()),
                        opacity * (0.18f + 0.42f * pulse) * alphaMult);
            }
            Color core = beam.coreColor;
            out.addLine(RenderLayer.SHOTS,
                    camera.cellToScreenX(beam.fromX), camera.cellToScreenY(beam.fromY),
                    camera.cellToScreenX(beam.toX), camera.cellToScreenY(beam.toY),
                    style.coreWidthPx(),
                    channel(core.getRed()), channel(core.getGreen()), channel(core.getBlue()),
                    opacity * (0.75f + 0.25f * pulse) * alphaMult);
        }
    }

    /**
     * A grained beam ({@link WeaponDef.BeamTexture}): one continuous lance (a soft outer fringe and a hot inner fringe
     * in the glow colour under the core, all swelling with the throb) with brighter knots of energy riding down it
     * from the muzzle. The lance is drawn whole so no seam shows; only the knots are cut out of it.
     */
    private void collectTextured(BattleCamera camera, DrawList out, LiveBeam beam, float alpha) {
        WeaponDef.BeamStyle style = beam.style;
        WeaponDef.BeamTexture texture = style.texture();
        float x0 = camera.cellToScreenX(beam.fromX);
        float y0 = camera.cellToScreenY(beam.fromY);
        float x1 = camera.cellToScreenX(beam.toX);
        float y1 = camera.cellToScreenY(beam.toY);
        float throb = throb(texture.throbHz(), clockSec);
        float fringeWidth = style.glowWidthPx() * (0.82f + 0.18f * throb);
        float coreWidth = style.coreWidthPx() * (0.85f + 0.15f * throb);
        float gr = channel(style.glowColor().getRed());
        float gg = channel(style.glowColor().getGreen());
        float gb = channel(style.glowColor().getBlue());
        Color core = beam.coreColor;
        out.addLine(RenderLayer.SHOTS, x0, y0, x1, y1, fringeWidth, gr, gg, gb, alpha * (0.16f + 0.12f * throb));
        out.addLine(RenderLayer.SHOTS, x0, y0, x1, y1, fringeWidth * 0.5f, gr, gg, gb,
                alpha * (0.34f + 0.26f * throb));
        out.addLine(RenderLayer.SHOTS, x0, y0, x1, y1, coreWidth,
                channel(core.getRed()), channel(core.getGreen()), channel(core.getBlue()),
                alpha * (0.72f + 0.28f * throb));

        float lengthCells = (float) Math.hypot(beam.toX - beam.fromX, beam.toY - beam.fromY);
        if (lengthCells <= 1e-4f || texture.roughness() <= 0f) return;
        float texel = texture.texelCells();
        float scroll = clockSec * texture.scrollCellsPerSec();
        float half = texel * 0.3f;
        int first = (int) Math.floor(-scroll / texel - 0.5f);
        int knots = knotCount(lengthCells, texel);
        for (int k = first; k <= first + knots; k++) {
            float weight = knotWeight(k, texture.roughness());
            if (weight <= 0.02f) continue;
            float centre = scroll + (k + 0.5f) * texel;
            float a = Math.max(0f, centre - half) / lengthCells;
            float b = Math.min(lengthCells, centre + half) / lengthCells;
            if (b <= a) continue;
            out.addLine(RenderLayer.SHOTS, x0 + (x1 - x0) * a, y0 + (y1 - y0) * a,
                    x0 + (x1 - x0) * b, y0 + (y1 - y0) * b, fringeWidth * 0.8f, gr, gg, gb,
                    alpha * weight * (0.45f + 0.35f * throb));
        }
    }

    /** Knots on a beam {@code lengthCells} long, one per texel, at most {@link #MAX_KNOTS}. */
    static int knotCount(float lengthCells, float texelCells) {
        int n = (int) Math.ceil(lengthCells / texelCells) + 1;
        return Math.max(1, Math.min(MAX_KNOTS, n));
    }

    /**
     * How bright knot {@code k} rides, in {@code [0, roughness]}: a fixed integer hash of the knot's own index, so a
     * knot keeps its brightness all the way down the beam and on every machine.
     */
    static float knotWeight(int k, float roughness) {
        return roughness * hash01(k);
    }

    /** The power throb in {@code [0, 1]} at {@code clockSec}; a steady 1 when the beam does not throb. */
    static float throb(float hz, float clockSec) {
        if (hz <= 0f) return 1f;
        return 0.5f + 0.5f * (float) Math.sin(clockSec * hz * Math.PI * 2.0);
    }

    private static float hash01(int i) {
        int h = i * 0x27D4EB2D;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65535f;
    }

    static float pulse(float cycles, float lifeT) {
        if (cycles <= 0f) return 1f;
        float ageT = 1f - clamp01(lifeT);
        return 0.55f + 0.45f * (float) Math.cos(ageT * cycles * Math.PI * 2.0);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float channel(int value) {
        return value / 255f;
    }

    private static final class LiveBeam {
        final float fromX;
        final float fromY;
        final float toX;
        final float toY;
        final Color coreColor;
        final WeaponDef.BeamStyle style;
        final float lifetime;
        float remaining;

        LiveBeam(float fromX, float fromY, float toX, float toY,
                 Color coreColor, WeaponDef.BeamStyle style,
                 float lifetime, float remaining) {
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.coreColor = coreColor;
            this.style = style;
            this.lifetime = lifetime;
            this.remaining = remaining;
        }
    }
}
