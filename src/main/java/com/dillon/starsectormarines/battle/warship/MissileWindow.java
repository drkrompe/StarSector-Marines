package com.dillon.starsectormarines.battle.warship;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * When a warship launches a missile at a hull: a missile is a resource, spent into a <em>window</em> its role can use
 * rather than rippled off the moment the target is in reach.
 *
 * <p>A missile's {@link Role} is read off the weapon table and the spec, never named per weapon: the table's
 * {@code primaryRoleStr} says what the base game's own fleets save a missile for. A window is a fact about the target
 * read at the moment of launch, from the side the missile leaves: its overload, its shield down or locked out, the
 * launcher outside its shield's arc, its pool running high, its hull worn, the pack's tank holding its attention, a
 * packmate's salvo already on its way. A role launches only into a window it can use:
 *
 * <ul>
 *   <li>a <b>torpedo</b> (Reaper, Hammer, Atropos) is a single slow blow a raised shield stops: overload, shield down,
 *       or from outside the shield's arc, or at a hull that carries no shield;</li>
 *   <li>a <b>finisher</b> (Harpoon) adds a worn hull to the torpedo's windows, and waits for one even against a hull
 *       with no shield, so an unshielded hull has its guns' time to answer first;</li>
 *   <li>an <b>anti-shield</b> missile (Sabot) is kinetic, doubled on a shield and halved on plate: it goes into a
 *       raised shield (or one resting that its flight will raise), best into a full pool, and is held while the
 *       shield is down;</li>
 *   <li>a <b>saturation</b> launcher (a MIRV carrier, a Typhoon's or Hammer barrage's deep magazine) is a volume
 *       weapon that beats point defence by numbers: it goes with a salvo or into any opening;</li>
 *   <li>anything else (a general-purpose rack, a long-range harasser) fires as it always did.</li>
 * </ul>
 *
 * <p>A launch into an opening opens a <b>salvo</b> on that target for the side's other launchers for a few seconds,
 * so packmates' racks join it and the missiles arrive together, which is what saturates point defence. How many
 * volleys a ship spends into one opening is {@link MissileOpenings}'.
 * {@code -Dbattle.warship.missileWindows=false} is the control: every missile launches as soon as it reaches, a
 * finisher still held for a worn or overloaded hull.
 */
public final class MissileWindow {

    /** {@code -Dbattle.warship.missileWindows=false} is the control: missiles launch whenever they reach. */
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("battle.warship.missileWindows"));
    /** A target this full of flux is near its shield's drop: a salvo now pushes it over. */
    static final float HIGH_FLUX = 0.7f;
    /** How long, in hull-fight seconds, a launch into an opening holds a salvo open for the side's other launchers. */
    static final float SALVO_SECONDS = 2.5f;
    /** A torpedo-role launcher with a magazine this deep is a barrage, flown for saturation. */
    static final int BARRAGE_MAGAZINE = 5;

    private MissileWindow() {}

    /** What a missile is for. */
    public enum Role {
        TORPEDO, FINISHER, ANTI_SHIELD, SATURATION, GENERAL;

        public String label() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /**
     * A fact about the target at launch, in the order a launch is labelled by: the first window the role accepts.
     * {@link #UNSHIELDED}, {@link #SHIELD_IDLE} and {@link #SHIELD_UP} are standing conditions, not openings.
     * {@link #SHIELD_DOWN} is a shield that cannot or will not rise for a missile (locked out by a full pool, or held
     * down by hand); an automatic shield lowered at rest is {@link #SHIELD_IDLE}, since the first hostile round on its
     * way raises it long before a missile arrives.
     */
    public enum Kind {
        OVERLOAD, SHIELD_DOWN, FLANK, HIGH_FLUX, WORN, STRIKE, SALVO, UNSHIELDED, SHIELD_IDLE, SHIELD_UP, NONE;

        /** Whether this is an opening a launcher waited for, rather than how the target always is. */
        public boolean opening() {
            return ordinal() <= SALVO.ordinal();
        }

        public String label() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /** The role of {@code w} at a hull, from its table role and spec. */
    public static Role role(VanillaWeapon w) {
        if (!w.missile() || w.flight() == null) return Role.GENERAL;
        String role = VanillaWeapons.role(w.id()).toLowerCase(Locale.ROOT);
        MissileFlightSpec.Mirv mirv = w.flight().mirv();
        if (role.equals("anti shield")
                || (mirv != null && "KINETIC".equalsIgnoreCase(mirv.damageType()))) return Role.ANTI_SHIELD;
        if (mirv != null) return Role.SATURATION;
        if (role.equals("torpedo")) {
            return VanillaWeapons.ammo(w.id()) >= BARRAGE_MAGAZINE ? Role.SATURATION : Role.TORPEDO;
        }
        if (role.equals("finisher")) return Role.FINISHER;
        return Role.GENERAL;
    }

    /**
     * Every window open on {@code target} for a launch by {@code shooter} from {@code (fromX, fromY)}; {@code salvo}
     * says whether a salvo of the shooter's side is open on it. Never empty: {@link Kind#NONE} when nothing applies.
     */
    public static Set<Kind> read(Warship shooter, Warship target, float fromX, float fromY, boolean salvo) {
        return read(shooter, target, fromX, fromY, salvo, true);
    }

    /**
     * {@link #read(Warship, Warship, float, float, boolean)}, with {@code idleClosed} false for the restraint control
     * ({@link MissileOpenings#ENABLED}): any shield not absorbing reads as {@link Kind#SHIELD_DOWN}, a resting one
     * included.
     */
    public static Set<Kind> read(Warship shooter, Warship target, float fromX, float fromY, boolean salvo,
                                 boolean idleClosed) {
        EnumSet<Kind> out = EnumSet.noneOf(Kind.class);
        if (target.overloaded()) out.add(Kind.OVERLOAD);
        boolean shielded = target.hasShield();
        if (shielded && !target.shieldAbsorbing()) {
            out.add(!idleClosed || MissileOpenings.forcedOpen(target) ? Kind.SHIELD_DOWN : Kind.SHIELD_IDLE);
        }
        if (shielded && target.shieldArcDegrees() < 359f && !target.arcCovers(fromX, fromY)) out.add(Kind.FLANK);
        if (shielded && target.fluxFraction() >= HIGH_FLUX) out.add(Kind.HIGH_FLUX);
        if (target.hullFraction() <= MissilePacing.FINISHER_HULL) out.add(Kind.WORN);
        Warship eye = target.hullTarget();
        if (shooter.manoeuvre().role == WarshipManoeuvre.Role.STRIKER && eye != null && eye != shooter
                && eye.faction == shooter.faction) out.add(Kind.STRIKE);
        if (salvo) out.add(Kind.SALVO);
        if (!shielded) out.add(Kind.UNSHIELDED);
        if (shielded && target.shieldAbsorbing()) out.add(Kind.SHIELD_UP);
        if (out.isEmpty()) out.add(Kind.NONE);
        return out;
    }

    /** Whether {@code role} may use window {@code k}. */
    public static boolean accepts(Role role, Kind k) {
        return switch (role) {
            case TORPEDO -> k == Kind.OVERLOAD || k == Kind.SHIELD_DOWN || k == Kind.FLANK || k == Kind.UNSHIELDED;
            case FINISHER -> k == Kind.OVERLOAD || k == Kind.SHIELD_DOWN || k == Kind.FLANK || k == Kind.WORN;
            case ANTI_SHIELD -> k == Kind.HIGH_FLUX || k == Kind.STRIKE || k == Kind.SALVO || k == Kind.UNSHIELDED
                    || k == Kind.SHIELD_IDLE || k == Kind.SHIELD_UP;
            case SATURATION -> k.opening() && k != Kind.WORN || k == Kind.UNSHIELDED;
            case GENERAL -> true;
        };
    }

    /**
     * The window {@code role} launches into, given the open windows: the first it accepts, in {@link Kind} order;
     * {@code null} when it should hold. An anti-shield missile holds while the shield it is for is down.
     */
    public static Kind launch(Role role, Set<Kind> open) {
        if (role == Role.ANTI_SHIELD && (open.contains(Kind.SHIELD_DOWN) || open.contains(Kind.OVERLOAD))) {
            return null;
        }
        for (Kind k : Kind.values()) {
            if (open.contains(k) && accepts(role, k)) return k;
        }
        return null;
    }

    /** The label a launch is read under when nothing gated it (the control): the first open window in order. */
    public static Kind label(Set<Kind> open) {
        for (Kind k : Kind.values()) if (open.contains(k)) return k;
        return Kind.NONE;
    }
}
