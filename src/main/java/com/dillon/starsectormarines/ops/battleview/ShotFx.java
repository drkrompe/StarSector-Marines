package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.SoftLaunch;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.render2d.ContrailStyle;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

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
 * <p>The {@code ShotRenderService} sweeps consume this composition directly;
 * the path-keyed projectile-sprite cache {@link Sprite#spritePath} enables is
 * shared by ordinary sprites and each {@link Bolt} style texture.
 *
 * @param body       projectile sprite vs. hitscan tracer
 * @param tracerTail optional short line attached behind a traveling projectile
 * @param arcHeight  visual parabola peak in cells; {@code 0} = flat
 * @param boostRamp  accelerate-from-rest boost-then-cruise flight curve
 * @param contrail   ribbon style, or {@code null} for none
 * @param softLaunch two-stage coast before the motor lights, or {@code null}
 */
public record ShotFx(Body body, TracerTail tracerTail, float arcHeight, boolean boostRamp,
                     ContrailStyle contrail, SoftLaunch softLaunch) {

    /** S3's soft white-base energy bolt, retained for the pulse-rifle family. */
    public static final String PULSE_BOLT_SPRITE_PATH = "graphics/fx/round_bolt.png";
    /** Vanilla railgun projectile reused as the DMR's stretched blue-white needle. */
    public static final String RAIL_NEEDLE_SPRITE_PATH = "graphics/missiles/shell_gauss_cannon.png";
    /** Nearly neutral vanilla flechette reused as the drone's compact cyan dart. */
    public static final String DRONE_DART_SPRITE_PATH = "graphics/missiles/flechette_sml.png";

    /** A shot's body: a traveling projectile sprite, traveling bolt, or hitscan tracer line. */
    public sealed interface Body permits Sprite, Bolt, Tracer {}

    /**
     * Projectile sprite identified by its <em>texture path</em> — carrier-agnostic,
     * so any weapon declaring the same path resolves the one loaded sprite (F3's
     * path-keyed cache). {@code visualCells} is the per-weapon long-axis size.
     */
    public record Sprite(String spritePath, float visualCells) implements Body {}

    /**
     * Traveling tinted streak. The path selects a reusable silhouette while
     * explicit length/width keep its ground-scale proportions independent of
     * source-texture dimensions. The sweep grows it out of the muzzle during
     * early flight.
     */
    public record Bolt(String spritePath, Color color,
                       float lengthCells, float widthCells) implements Body {}

    /**
     * Hitscan tracer line. {@code color} {@code null} → the sweep resolves the
     * faction-default color from the shot via {@link #defaultTracerColor} (per-shot,
     * not type-flyweight).
     */
    public record Tracer(Color color, WeaponDef.BeamStyle style) implements Body {
        public Tracer {
            if (style == null) style = WeaponDef.BeamStyle.DEFAULT;
        }
    }

    /** Short line following a projectile body; length is authored in world cells. */
    public record TracerTail(Color color, float lengthCells) {}

    /** Faction-default hitscan tracer colors — what a null-color {@link Tracer} resolves to. */
    public static final Color MARINE_TRACER   = new Color(0xFF, 0xE0, 0x70);
    public static final Color DEFENDER_TRACER = new Color(0xFF, 0x70, 0x40);

    /**
     * The tracer color for a shot whose {@link Tracer#color} is null — the shot's
     * faction default (single source of truth for the tracer-line color and the
     * matching light-path stamp). The reading is player-side against enemy, so
     * an allied militia's rounds are the friendly hue rather than an enemy's.
     */
    public static Color defaultTracerColor(Faction faction) {
        return Allegiance.of(faction).friendly() ? MARINE_TRACER : DEFENDER_TRACER;
    }

    /** No weapon source (detonations / legacy callers) → a faction-default tracer. */
    private static final ShotFx NO_SOURCE = new ShotFx(
            new Tracer(null, WeaponDef.BeamStyle.DEFAULT), null, 0f, false, null, null);

    /** The composition for a shot — never null; dispatches on the single non-null weapon source. */
    public static ShotFx of(ShotEvent s) {
        if (s.turretStructureDef != null) return deriveTurret(s.turretStructureDef.mount.weapon);
        if (s.specialEquipmentDef != null) return deriveSecondary(s.specialEquipmentDef);
        if (s.primaryWeaponDef != null) return derivePrimary(s.primaryWeaponDef);
        if (s.mechWeaponDef != null)   return deriveMech(s.mechWeaponDef);
        return NO_SOURCE;
    }

    /** True when the body advances on the shot clock and its impact FX belongs at arrival. */
    public boolean travels() {
        return body instanceof Sprite || body instanceof Bolt;
    }

    private static ShotFx deriveTurret(WeaponDef weapon) {
        ContrailStyle contrail = switch (weapon.contrailProfile) {
            case NONE -> null;
            case MISSILE_SMOKE -> ContrailStyle.MISSILE_SMOKE;
            case INTERCEPTOR_WISP -> ContrailStyle.INTERCEPTOR_WISP;
        };
        // A turret weapon with no projectile art and an authored beam is a hitscan beam, as on a mech; an energy
        // weapon with neither fires the pulse bolt in its tracer colour, as a marine's pulse rifle does; anything
        // else with no art travels unseen (the colony's spines, heard only where they break out).
        Body body = weapon.hitscanBeam()
                ? new Tracer(weapon.tracerColor, weapon.beamStyle)
                : firesTurretBolt(weapon) ? new Bolt(PULSE_BOLT_SPRITE_PATH, weapon.tracerColor,
                        TURRET_BOLT_LENGTH_CELLS, TURRET_BOLT_WIDTH_CELLS)
                : new Sprite(weapon.projectileSpritePath, weapon.projectileVisualCells);
        return new ShotFx(
                body,
                tracerTail(weapon),
                weapon.arcHeight,
                weapon.boostRamp,
                contrail,
                weapon.softLaunch);
    }

    /** A marine primary's rounds as they look in battle, for a preview with no shot to key on. */
    public static ShotFx ofPrimary(WeaponDef weapon) {
        return derivePrimary(weapon);
    }

    private static ShotFx derivePrimary(WeaponDef weapon) {
        Body body = weapon.projectileSpritePath != null
                ? new Sprite(weapon.projectileSpritePath, weapon.projectileVisualCells)
                : bolt(weapon);
        return new ShotFx(body, tracerTail(weapon), 0f, false, null, null);
    }

    private static Bolt bolt(WeaponDef weapon) {
        if (WeaponRegistry.PULSE_RIFLE_ID.equals(weapon.id)) {
            return new Bolt(PULSE_BOLT_SPRITE_PATH, weapon.tracerColor, 1.0f, 0.25f);
        }
        if (WeaponRegistry.DMR_ID.equals(weapon.id)) {
            return new Bolt(RAIL_NEEDLE_SPRITE_PATH, weapon.tracerColor, 1.8f, 0.16f);
        }
        if (WeaponRegistry.DRONE_PULSE_ID.equals(weapon.id)) {
            return new Bolt(DRONE_DART_SPRITE_PATH, weapon.tracerColor, 0.65f, 0.16f);
        }
        return new Bolt(PULSE_BOLT_SPRITE_PATH, weapon.tracerColor, 1.0f, 0.22f);
    }

    /** An energy turret's pulse bolt: a vehicle emitter's, a little longer and heavier than a rifle's. */
    static final float TURRET_BOLT_LENGTH_CELLS = 1.3f;
    static final float TURRET_BOLT_WIDTH_CELLS = 0.28f;

    /** Whether a turret weapon's rounds are drawn as a traveling bolt: an energy weapon with no projectile art or beam. */
    static boolean firesTurretBolt(WeaponDef weapon) {
        return weapon.energy && weapon.projectileSpritePath == null && !weapon.hitscanBeam();
    }

    /** Distinct traveling-bolt textures for cache loading; derived from effects, not carriers. */
    static Set<String> boltSpritePaths() {
        Set<String> paths = new HashSet<>();
        for (WeaponDef weapon : WeaponRegistry.installed().all()) {
            ShotFx fx;
            if (weapon.mount == MountClass.MARINE_PRIMARY) fx = derivePrimary(weapon);
            else if (weapon.mount == MountClass.TURRET_MOUNT) fx = deriveTurret(weapon);
            else continue;
            if (fx.body() instanceof Bolt bolt) paths.add(bolt.spritePath());
        }
        return Set.copyOf(paths);
    }

    private static ShotFx deriveSecondary(SpecialEquipmentDef w) {
        Body body = w.projectileSpritePath() != null
                ? new Sprite(w.projectileSpritePath(), w.projectileVisualCells())
                : new Bolt(RAIL_NEEDLE_SPRITE_PATH, w.tracerColor(), 2.2f, 0.20f);
        return new ShotFx(body, tracerTail(w.weaponDef()),
                w.weaponDef().arcHeight, w.weaponDef().boostRamp,
                w.activation() == SpecialActivation.DIRECT_EXPLOSIVE
                        ? ContrailStyle.MISSILE_SMOKE : null, null);
    }

    private static ShotFx deriveMech(WeaponDef weapon) {
        // Null projectile art is an authored hitscan beam; physical shells and
        // missiles continue to travel as sprites.
        Body body = weapon.projectileSpritePath != null
                ? new Sprite(weapon.projectileSpritePath, weapon.projectileVisualCells)
                : new Tracer(weapon.tracerColor, weapon.beamStyle);
        return new ShotFx(body, tracerTail(weapon), weapon.arcHeight, false, null, null);
    }

    private static TracerTail tracerTail(WeaponDef weapon) {
        return weapon.tracerTailCells > 0f
                ? new TracerTail(weapon.tracerColor, weapon.tracerTailCells)
                : null;
    }

}
