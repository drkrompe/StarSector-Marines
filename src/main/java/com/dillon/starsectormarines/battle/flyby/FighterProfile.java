package com.dillon.starsectormarines.battle.flyby;

import com.dillon.starsectormarines.battle.air.AirHandling;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.AirOrdnance;
import com.dillon.starsectormarines.battle.air.engine.HullKinematicsResolver;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

/**
 * The fighters that exist: which hull each one is, how much killing it takes on
 * the ground, and what it carries. How much of it there is comes off the hull
 * itself and is not authored here. An {@link Airframe}, so a berth can hold one
 * and a sortie can fly one.
 *
 * <p>All sprite paths resolve against the vanilla install — Starsector's resource
 * loader walks core + enabled mods, so {@code "graphics/ships/wasp_ftr.png"} from
 * a mod jar pulls the core file. No redistribution required.
 *
 * <p>This is a roster and nothing more. How a fighter flies comes from its hull's
 * own maneuver spec, and how it delivers comes from its {@link AirOrdnance}
 * preset; neither is authored here. The tracer, burst and projectile tuning this
 * enum used to carry were the deleted flyby overlay's private fire resolution,
 * which resolved damage without cover, armour, roofs or wall damage — the
 * ordnance presets carry the equivalent facts and go through the detonation
 * pipeline instead.
 */
public enum FighterProfile implements Airframe {

    /** Talon — light autocannon, fast and twitchy. */
    TALON("graphics/ships/talon/talon.png", "talon", 30f, 1,
            new Color(0xFF, 0xE0, 0x70)),

    /** Wasp — small drone with a pulse laser. */
    WASP("graphics/ships/wasp_ftr.png", "wasp", 25f, 1,
            new Color(0x88, 0xFF, 0xFF)),

    /** Broadsword — heavy fighter, dual chaingun. The strafe of choice. */
    BROADSWORD("graphics/ships/broadsword.png", "broadsword", 45f, 2,
            new Color(0xFF, 0xE0, 0x70)),

    /** Thunder — interceptor with twin ion bolts. */
    THUNDER("graphics/ships/thunder.png", "thunder", 35f, 2,
            new Color(0x70, 0xC8, 0xFF)),

    /**
     * Longbow — high-tech missile support fighter. The third delivery class:
     * neither a gun that hits where the nose points nor a bomb that falls
     * behind, but a pod of powered rounds released from well outside gun range.
     */
    LONGBOW("graphics/ships/longbow_intg.png", "longbow", 38f, 1,
            new Color(0xC0, 0xE8, 0xFF)),

    /** Dagger — Tri-Tachyon torpedo bomber; a stick of bombs that flattens walls and chews into clusters. */
    DAGGER("graphics/ships/dagger_trp.png", "dagger", 40f, 1,
            new Color(0xFF, 0xB0, 0x60));

    /** Vanilla sprite path. Lazy-loaded once by the render tier. */
    public final String spritePath;
    /**
     * Vanilla hull id — keys
     * {@link com.dillon.starsectormarines.battle.air.engine.EngineSlotResolver}
     * so the engine-FX pass can scrape thruster positions from this
     * fighter's {@code .ship} spec at runtime. Same convention as
     * {@link com.dillon.starsectormarines.battle.air.ShuttleType#matchingHullIds}.
     */
    public final String hullId;
    /**
     * Structure on this airframe when it is standing on a berth.
     *
     * <p>An authored ladder rather than a scrape, the way
     * {@link com.dillon.starsectormarines.battle.air.ShuttleType#maxHp} is: a
     * hull's campaign HP is balanced against ship weapons and says nothing
     * about what a rifle section does to one parked on concrete.
     *
     * <p><b>And deliberately not a function of drawn size either.</b> It is a
     * role-and-toughness ladder: a heavy fighter is heavy because of what it is
     * built to survive, not because of how long its sprite is, so the
     * Broadsword parks as the toughest thing on any apron while the Thunder,
     * the longest hull on this list, is one of the softest. This field once
     * carried a second, authored "drawn length" that ranked identically to it,
     * which made the correspondence look like a law; the hulls the game
     * actually ships do not rank that way, and the number that says how much
     * aircraft there is lives in one place — the hull's own spec, through
     * {@code HullFootprintResolver}.
     *
     * <p>Drawn size is not idle. It is what decides how easily a shot finds
     * the aircraft, through {@code Airframe.targetRadiusCells}, which is a
     * different question from how much killing it takes.
     *
     * <p>The whole ladder sits below the lightest transport — a Hermes is 55 —
     * so a fire team walking onto an apron of fighters can burn several in the
     * time one transport would cost it.
     */
    public final float parkedHp;
    /**
     * Turret mounts this fighter carries in the air.
     *
     * <p>Authored to the hull's vanilla armament rather than counted off its
     * weapon slots, because the slot list is only readable with the game
     * loaded and a fighter that silently comes up unarmed headless is a strike
     * that quietly does nothing.
     */
    public final int mounts;

    /**
     * This fighter's identity colour — the tint anything drawn for it may key
     * on. The gun-run presentation is keyed on the <em>delivery</em> rather
     * than on the carrier ({@code OrdnanceFx}), so nothing reads this yet;
     * it is the one piece of the deleted overlay's visual block worth keeping,
     * because a per-hull tint is a fact about the fighter and not about the
     * renderer that was removed.
     */
    public final Color tracerColor;

    FighterProfile(String spritePath, String hullId, float parkedHp,
                   int mounts, Color tracerColor) {
        this.spritePath = spritePath;
        this.hullId = hullId;
        this.parkedHp = parkedHp;
        this.mounts = mounts;
        this.tracerColor = tracerColor;
    }

    // ---- Airframe: what standing on a berth needs -----------------------------

    @Override public String spritePath() { return spritePath; }

    /**
     * A fighter's own hull is what sizes it — unlike a transport, which may
     * borrow a match id, every profile here names the hull its sprite is.
     */
    @Override public String renderHullId() { return hullId; }

    /**
     * Off the hull's own maneuver spec, so an interceptor and a bomber fly
     * differently without either being tuned. Degrades to a flyable mid-tier
     * profile when there is no game to read a spec out of.
     */
    @Override public AirHandling flight() { return HullKinematicsResolver.resolve(hullId); }

    /**
     * Guns this fighter mounts, following its vanilla armament: one on the
     * light interceptors and the torpedo bomber, two on the heavier gun
     * fighters. Where they sit is the hull's own business.
     */
    @Override public int hardpoints() { return mounts; }

    /**
     * The armament this fighter runs in with, which is to say how it delivers:
     * a laser-pointer gun, the same delivery painted as a line, a pod of
     * powered rounds thrown from standoff, or a stick of bombs that falls.
     *
     * <p>Named per hull and exhaustively, so a fighter added to this list
     * cannot quietly inherit somebody else's weapon: what an airframe carries
     * is a decision about that airframe.
     */
    @Override
    public AirOrdnance ordnance() {
        return switch (this) {
            case DAGGER -> AirOrdnance.BOMBS;
            case LONGBOW -> AirOrdnance.MISSILES;
            case WASP, THUNDER -> AirOrdnance.BEAM;
            case TALON, BROADSWORD -> AirOrdnance.AUTOCANNON;
        };
    }

    @Override public float maxHp() { return parkedHp; }

    // ---- Faction → profile pool ----------------------------------------------

    private static final List<FighterProfile> LOWTECH  = Arrays.asList(BROADSWORD, TALON, DAGGER);
    private static final List<FighterProfile> HIGHTECH = Arrays.asList(WASP, THUNDER, DAGGER);
    private static final List<FighterProfile> MIDLINE  = Arrays.asList(TALON, BROADSWORD, THUNDER, LONGBOW, DAGGER);
    private static final List<FighterProfile> MIXED    = Arrays.asList(values()); // all profiles

    /**
     * Returns the fighter profiles a given faction would plausibly field, used
     * by mission generation to pick faction-appropriate wings. Mapping leans on
     * vanilla aesthetic — Hegemony / Luddic / Pirates run ballistic kit;
     * Tri-Tachyon / Remnant run energy; everyone else gets a mid-line mix.
     * Unknown faction ids fall back to the full pool so modded factions still
     * get some support. Dagger (torpedo bomber) shows up in every pool — every
     * faction fields some flavor of missile boat, and the AoE keeps it from
     * being lost in a swarm of chaingun fighters. Longbow is mid-line only: a
     * standoff missile pod is the one delivery class that never has to come
     * over the position it is attacking, and keeping it off both ends of the
     * tech ladder is what stops every field on the map fielding one.
     */
    public static List<FighterProfile> poolForFaction(String factionId) {
        if (factionId == null) return MIXED;
        switch (factionId) {
            case "hegemony":
            case "luddic_church":
            case "luddic_path":
            case "knights_of_ludd":
            case "pirates":
                return LOWTECH;
            case "tritachyon":
            case "remnant":
                return HIGHTECH;
            case "persean":
            case "diktat":
            case "independent":
                return MIDLINE;
            default:
                return MIXED;
        }
    }
}
