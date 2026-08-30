package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.fx.OrdnanceDelivery;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxDef;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

/**
 * Render-side flyweight: what a delivered round looks and sounds like, as a
 * composition of opt-in effects keyed on the {@link OrdnanceDelivery} rather
 * than on what carried it.
 *
 * <p>Sibling of {@link ShotFx} and built to the same rule — the sweeps that
 * consume this key on the <em>effects</em>, so a second carrier that delivers
 * shells draws and sounds like shells with no edit here. Nothing in this file
 * knows an aircraft exists.
 *
 * <p><b>The three are meant to be told apart at a glance and with your eyes
 * shut.</b> A gun run is a fast stream of hot streaks that arrive short of
 * where they were aimed and kick up dirt; a beam is a single narrow line that
 * is simply <em>there</em>, dragged across the ground under a held tone; a
 * stick of bombs is a handful of bodies that fall visibly and then take the
 * ground apart. Scaling one effect by calibre would collapse exactly the
 * distinction the three loads exist to make.
 *
 * <p>The particle compositions are authored here rather than in
 * {@code mod/data} because the loads they belong to are code — three named
 * presets on {@code AirOrdnance}, not a catalog anybody edits from outside.
 * Same reasoning, and the same {@link WeaponFxDef#parse} entry point, as
 * {@code WeaponFxRuntime}'s legacy-rifle composition.
 *
 * @param trace           how the round is drawn between muzzle and ground
 * @param fx              muzzle and impact particle layers, or {@code null}
 *                        when the delivery draws neither (a bomb is released
 *                        rather than fired, and its arrival is the heavy-blast
 *                        recipe instead of an authored one)
 * @param fireSoundId     the weapon's own cue, registered in {@code sounds.json}
 * @param fireLoops       true when the cue is a held loop re-armed while firing
 *                        rather than a clip per round
 * @param fireCueMinGap   seconds between fire cues — a rotary cannon releases
 *                        fourteen rounds a second and playing fourteen clips is
 *                        noise, not a gun
 * @param heavyImpact     true when arrival uses the shared heavy-blast recipe
 */
public record OrdnanceFx(Trace trace, WeaponFxDef fx,
                         String fireSoundId, float fireVolume, float fireCueMinGap,
                         boolean fireLoops,
                         String impactSoundId, float impactVolume, float impactCueMinGap,
                         boolean heavyImpact) {

    // ---- registered sound ids (see the AIR ORDNANCE block in sounds.json) ----

    public static final String SFX_GUN_RUN       = "marines_air_gun_run";
    public static final String SFX_GUN_IMPACT    = "marines_air_gun_impact";
    public static final String SFX_BEAM_LOOP     = "marines_air_beam_loop";
    public static final String SFX_BEAM_IMPACT   = "marines_air_beam_impact";
    public static final String SFX_BOMB_RELEASE  = "marines_air_bomb_release";
    public static final String SFX_BOMB_IMPACT   = "marines_air_bomb_impact";

    /** Vanilla high-explosive bomb body, reused for the falling stick. */
    public static final String BOMB_SPRITE_PATH = "graphics/missiles/bomb_HE.png";

    /** How the round is drawn on its way to the ground. */
    public sealed interface Trace permits Streak, Line, Falling {
        /** Seconds between release and arrival. */
        float flightSeconds();
        /** Seconds the mark lingers after arrival before it is gone. */
        float fadeSeconds();
    }

    /** A travelling streak: a short bright segment chasing its own head down to the ground. */
    public record Streak(Color color, float lengthCells, float widthPx,
                         float flightSeconds, float fadeSeconds) implements Trace {}

    /** A line that covers its whole path the instant it is released, then fades. */
    public record Line(Color color, float widthPx, float fadeSeconds) implements Trace {
        @Override public float flightSeconds() { return 0f; }
    }

    /** A body falling from the rail to the ground, drawn from its own sprite. */
    public record Falling(String spritePath, float visualCells,
                          float flightSeconds, float fadeSeconds) implements Trace {}

    /** Total seconds this composition occupies the screen for one round. */
    public float lifetimeSeconds() {
        return trace.flightSeconds() + trace.fadeSeconds();
    }

    private static final OrdnanceFx SHELL = new OrdnanceFx(
            new Streak(new Color(0xFF, 0xC8, 0x58), 2.6f, 3.4f, 0.12f, 0.10f),
            parse("fx.ordnance-shell", """
                    {"muzzle":[
                       {"kind":"glow","radius":0.80,"lifetime":0.09,"color":"FFD878"},
                       {"kind":"fire","radius":0.34,"lifetime":0.16}
                     ],
                     "impact":[
                       {"kind":"glow","radius":0.62,"lifetime":0.12,"color":"FFE0A0"},
                       {"kind":"fire","radius":0.40,"lifetime":0.26},
                       {"kind":"dust","radius":0.75,"lifetime":0.42},
                       {"kind":"smoke","radius":[0.40,0.66],"lifetime":[0.70,1.05],"count":2,"jitter":0.45}
                     ]}
                    """),
            SFX_GUN_RUN, 0.95f, 0.11f, /*fireLoops*/ false,
            SFX_GUN_IMPACT, 0.55f, 0.20f, /*heavyImpact*/ false);

    private static final OrdnanceFx BEAM = new OrdnanceFx(
            new Line(new Color(0x8C, 0xEC, 0xFF), 2.6f, 0.20f),
            parse("fx.ordnance-beam", """
                    {"muzzle":[
                       {"kind":"glow","radius":0.42,"lifetime":0.07,"color":"BFF4FF"}
                     ],
                     "impact":[
                       {"kind":"glow","radius":0.55,"lifetime":0.15,"color":"CFF6FF"},
                       {"kind":"dust","radius":0.30,"lifetime":0.24}
                     ]}
                    """),
            SFX_BEAM_LOOP, 0.80f, 0f, /*fireLoops*/ true,
            SFX_BEAM_IMPACT, 0.40f, 0.15f, /*heavyImpact*/ false);

    private static final OrdnanceFx BOMB = new OrdnanceFx(
            new Falling(BOMB_SPRITE_PATH, 0.85f, 0.35f, 0.05f),
            /*fx*/ null,
            SFX_BOMB_RELEASE, 0.85f, 0.04f, /*fireLoops*/ false,
            SFX_BOMB_IMPACT, 1.0f, 0.04f, /*heavyImpact*/ true);

    /** The composition for a delivery. Never null. */
    public static OrdnanceFx of(OrdnanceDelivery delivery) {
        return switch (delivery) {
            case SHELL -> SHELL;
            case BEAM -> BEAM;
            case BOMB -> BOMB;
        };
    }

    /** Distinct trace textures to load, derived from the effects rather than from carriers. */
    public static Set<String> spritePaths() {
        Set<String> paths = new HashSet<>();
        for (OrdnanceDelivery delivery : OrdnanceDelivery.values()) {
            if (of(delivery).trace() instanceof Falling falling) paths.add(falling.spritePath());
        }
        return Set.copyOf(paths);
    }

    private static WeaponFxDef parse(String id, String json) {
        try {
            return WeaponFxDef.parse(id, new JSONObject(json));
        } catch (JSONException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
