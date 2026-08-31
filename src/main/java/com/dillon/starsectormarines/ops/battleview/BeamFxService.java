package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Presentation-owned lingering hitscan beams, independent of ballistic arrival timing. */
public final class BeamFxService {

    private final List<LiveBeam> beams = new ArrayList<>();

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
