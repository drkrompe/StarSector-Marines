package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.mech.MechWeapon;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.ContrailStyle;

import java.awt.Color;
import java.util.EnumMap;
import java.util.function.Function;

/**
 * Render-side flyweight: a shot's FX as a <em>carrier-agnostic effect
 * composition</em>. FX is a property of the shot (composed from the weapon), not
 * of who fired it — a marine grenade launcher would arc + contrail exactly like a
 * turret mortar; a marine missile would boost exactly like a Locust. So this is a
 * record of opt-in effects, and <strong>consumers key on the effects, never on the
 * carrier</strong> (adding an effect to a future weapon is a one-line derivation
 * change, no sweep edits).
 *
 * <p>Keyed by the shot's single non-null weapon source ({@link ShotEvent}'s four
 * mutually-exclusive source fields). Per-source derivation reads the sim enum's
 * <em>data</em> into the uniform record — sim enums never gain render types, the
 * same boundary {@link RenderAppearance} keeps with {@code UnitType}. Built once
 * per enum value into per-enum tables; {@link #of(ShotEvent)} dispatches.
 *
 * <p><b>F2 (this slice)</b> stands up the record + derivation + its pinning test;
 * no pass change. The {@code ShotRenderService} sweeps that consume it — and the
 * path-keyed projectile-sprite cache {@link Sprite#spritePath} enables — land in
 * F3. See {@code roadmap/battle-render/stories/fx-shots-command-model.md}.
 *
 * @param body       projectile sprite vs. hitscan tracer
 * @param arcHeight  visual parabola peak in cells; {@code 0} = flat
 * @param boostRamp  accelerate-from-rest boost-then-cruise flight curve
 * @param engineTrail spawn a glowing engine trail in flight
 * @param smokeTrail  spawn a gray smoke puff in flight
 * @param contrail   ribbon style, or {@code null} for none
 */
public record ShotFx(Body body, float arcHeight, boolean boostRamp,
                     boolean engineTrail, boolean smokeTrail, ContrailStyle contrail) {

    /** A shot's body: a traveling projectile sprite, or a hitscan tracer line. */
    public sealed interface Body permits Sprite, Tracer {}

    /**
     * Projectile sprite identified by its <em>texture path</em> — carrier-agnostic,
     * so any weapon declaring the same path resolves the one loaded sprite (F3's
     * path-keyed cache). {@code visualCells} is the per-weapon long-axis size.
     */
    public record Sprite(String spritePath, float visualCells) implements Body {}

    /**
     * Hitscan tracer line. {@code color} {@code null} → the sweep resolves the
     * faction-default color from the shot via {@link #defaultTracerColor} (per-shot,
     * not type-flyweight).
     */
    public record Tracer(Color color) implements Body {}

    /** Faction-default hitscan tracer colors — what a null-color {@link Tracer} resolves to. */
    public static final Color MARINE_TRACER   = new Color(0xFF, 0xE0, 0x70);
    public static final Color DEFENDER_TRACER = new Color(0xFF, 0x70, 0x40);

    /**
     * The tracer color for a shot whose {@link Tracer#color} is null — the shot's
     * faction default (single source of truth for the tracer-line color and the
     * matching light-path stamp). Any non-marine faction reads as the defender hue.
     */
    public static Color defaultTracerColor(Faction faction) {
        return faction == Faction.MARINE ? MARINE_TRACER : DEFENDER_TRACER;
    }

    private static final EnumMap<TurretKind, ShotFx>      TURRET    = build(TurretKind.class,      ShotFx::deriveTurret);
    private static final EnumMap<MarineWeapon, ShotFx>    PRIMARY   = build(MarineWeapon.class,    ShotFx::derivePrimary);
    private static final EnumMap<MarineSecondary, ShotFx> SECONDARY = build(MarineSecondary.class, ShotFx::deriveSecondary);
    private static final EnumMap<MechWeapon, ShotFx>      MECH      = build(MechWeapon.class,      ShotFx::deriveMech);
    /** No weapon source (detonations / legacy callers) → a faction-default tracer. */
    private static final ShotFx NO_SOURCE = new ShotFx(new Tracer(null), 0f, false, false, false, null);

    /** The composition for a shot — never null; dispatches on the single non-null weapon source. */
    public static ShotFx of(ShotEvent s) {
        if (s.turretKind != null)      return TURRET.get(s.turretKind);
        if (s.marineSecondary != null) return SECONDARY.get(s.marineSecondary);
        if (s.marineWeapon != null)    return PRIMARY.get(s.marineWeapon);
        if (s.mechWeapon != null)      return MECH.get(s.mechWeapon);
        return NO_SOURCE;
    }

    private static ShotFx deriveTurret(TurretKind k) {
        // The one render-side per-kind mapping: which turrets ribbon (and its
        // style). A future contrail-bearing weapon opts in here — the sweep stays
        // carrier-agnostic (it keys on contrail != null). Mirrors the boost decision,
        // which the weapon already owns via hasBoostRamp().
        boolean ribbon = k == TurretKind.LOCUST;
        return new ShotFx(
                new Sprite(k.projectileSpritePath, k.projectileVisualCells),
                k.arcHeight,
                k.hasBoostRamp(),
                false,                       // turrets carry no engine trail
                k.smokeTrail && !ribbon,     // ribbon kinds suppress the smoke puff
                ribbon ? ContrailStyle.MISSILE_SMOKE : null);
    }

    private static ShotFx derivePrimary(MarineWeapon w) {
        Body body = w.projectileSpritePath != null
                ? new Sprite(w.projectileSpritePath, w.projectileVisualCells)  // SMG
                : new Tracer(w.tracerColor);                                    // pulse rifle / DMR / drone pulse
        return new ShotFx(body, 0f, false, false, false, null);
    }

    private static ShotFx deriveSecondary(MarineSecondary w) {
        return new ShotFx(new Sprite(w.projectileSpritePath, w.projectileVisualCells),
                0f, false, false, false, null);
    }

    private static ShotFx deriveMech(MechWeapon w) {
        // Every mech weapon ships a projectile sprite today; the tracer arm is the
        // faithful fallback (faction default, matching the old renderer — mech
        // tracerColor was load-failure-only and unused in the shot pass).
        Body body = w.projectileSpritePath != null
                ? new Sprite(w.projectileSpritePath, w.projectileVisualCells)
                : new Tracer(null);
        return new ShotFx(body, w.arcHeight, false, w.engineTrail, false, null);
    }

    private static <E extends Enum<E>> EnumMap<E, ShotFx> build(Class<E> cls, Function<E, ShotFx> derive) {
        EnumMap<E, ShotFx> m = new EnumMap<>(cls);
        for (E e : cls.getEnumConstants()) m.put(e, derive.apply(e));
        return m;
    }
}
