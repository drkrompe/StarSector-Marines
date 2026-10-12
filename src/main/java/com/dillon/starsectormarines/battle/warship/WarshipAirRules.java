package com.dillon.starsectormarines.battle.warship;

import java.util.List;

/**
 * Which of a warship's weapons may fire at which kind of aircraft, and how well:
 * the ship-versus-air rules, in pure functions of a weapon's table hints, its
 * slot's size and the target's class.
 *
 * <p>The base game decides this by the weapon and not by the ship. A gun built
 * for battleships is "too slow" to track a fighter and does not try; flak,
 * anti-fighter missiles, point defence and the medium guns do. A transport is a
 * ship, slow and large, so anything that shoots at ships may shoot at it, and a
 * pure point-defence weapon, which in the game never fires at ships, may not.
 *
 * <h2>Weapon classes</h2>
 * Read from the weapon table's {@code hints} tokens and the size of the slot the
 * weapon sits in (not from the ship):
 * <ul>
 *   <li>{@link WeaponClass#POINT_DEFENCE}: a {@code PD} hint without
 *       {@code PD_ALSO}: flak, vulcans, PD lasers. Fighters only.</li>
 *   <li>{@link WeaponClass#FLAK}: {@code ANTI_FTR}, {@code PD_ALSO}, or a
 *       medium-or-smaller slot whose weapon is not {@code STRIKE}. Any aircraft.</li>
 *   <li>{@link WeaponClass#ASSAULT}: {@code STRIKE}, or a large slot whose
 *       weapon carries neither {@code ANTI_FTR} nor a {@code PD} hint. Transports
 *       only.</li>
 * </ul>
 * A {@code STRIKE} hint wins over everything else; an {@code ANTI_FTR} or PD hint
 * wins over size. A weapon table that names none of them is judged on size.
 *
 * <h2>Hit rule</h2>
 * One round of a burst hits with {@link #roundHitChance}: the point-defence rule
 * ({@link WarshipPointDefence#roundHitChance}), which reads an aircraft as fast
 * ordnance crossing at its cross-range speed, then a {@link #FIGHTER_EVASION}
 * share for a fighter, which jinks rather than flies a ballistic line. Whether a
 * given round hits is a pure hash of who fired what at which craft (see
 * {@link #unit}), never a draw from a shared random stream, so a gun shooting at
 * a fighter cannot shift another gun's draws. A projectile is aimed ahead of the
 * craft by its velocity times the flight time ({@link #leadPoint}); the chance
 * term is the residual error of a gun that cannot lead a turn.
 */
public final class WarshipAirRules {

    private WarshipAirRules() {}

    /** What a weapon is for, in the ship-versus-air rules. */
    public enum WeaponClass { POINT_DEFENCE, FLAK, ASSAULT }

    /** What an aircraft is, to a gun. The air layer has no finer class: a bomber is a fighter hull carrying ordnance. */
    public enum AirTargetClass { FIGHTER, TRANSPORT }

    /** The share of the point-defence chance a fighter leaves a gun, because it jinks instead of flying a straight line. */
    public static final float FIGHTER_EVASION = 0.6f;
    /** Seconds of damage a beam deals in one pulse at an aircraft, the shortest engagement a mount makes. */
    public static final float BEAM_PULSE_SECONDS = WarshipPointDefence.MIN_ENGAGEMENT_SECONDS;

    /**
     * The class of a weapon with these table hints in a slot of this size.
     *
     * @param hints         the weapon table's {@code hints} tokens, e.g. {@code [PD, PD_ALSO]}
     * @param slotSize      the hull slot's size: SMALL, MEDIUM or LARGE; anything else counts as large
     * @param pointDefenceOnly whether the weapon is a pure point-defence mount ({@link VanillaWeapon#pointDefenceOnly})
     */
    public static WeaponClass classify(List<String> hints, String slotSize, boolean pointDefenceOnly) {
        if (hints.contains("STRIKE")) return WeaponClass.ASSAULT;
        boolean antiFighter = hints.contains("ANTI_FTR");
        boolean pdAlso = hints.contains("PD_ALSO");
        boolean pd = hints.contains("PD") || hints.contains("PD_ONLY");
        if (pointDefenceOnly || (pd && !pdAlso)) return WeaponClass.POINT_DEFENCE;
        if (antiFighter || pdAlso) return WeaponClass.FLAK;
        boolean large = !"SMALL".equals(slotSize) && !"MEDIUM".equals(slotSize);
        return large ? WeaponClass.ASSAULT : WeaponClass.FLAK;
    }

    /** The class of a fitted mount: its weapon's table hints and its slot's size. */
    public static WeaponClass classify(Warship.Mount mount) {
        return classify(VanillaWeapons.hints(mount.weapon.id()), mount.slot.size(), mount.weapon.pointDefenceOnly());
    }

    /** Whether a weapon of this class may fire at an aircraft of that class. */
    public static boolean mayEngage(WeaponClass weapon, AirTargetClass target) {
        return switch (weapon) {
            case FLAK -> true;
            case POINT_DEFENCE -> target == AirTargetClass.FIGHTER;
            case ASSAULT -> target == AirTargetClass.TRANSPORT;
        };
    }

    /**
     * The chance one round of {@code weapon} hits an aircraft of this class at {@code distanceCells}
     * crossing the line of fire at {@code crossingSpeedCells} (cells per second). A beam is hitscan and
     * ignores the crossing.
     */
    public static float roundHitChance(VanillaWeapon weapon, AirTargetClass target, float distanceCells,
                                       float crossingSpeedCells) {
        float base = WarshipPointDefence.roundHitChance(weapon, distanceCells, crossingSpeedCells);
        return target == AirTargetClass.FIGHTER ? base * FIGHTER_EVASION : base;
    }

    /**
     * Seconds a round takes to reach the craft: a beam is instant, a projectile flies at its muzzle speed
     * (cells per second as {@link WarshipScale} converts the game's).
     */
    public static float flightSeconds(VanillaWeapon weapon, float distanceCells) {
        if (weapon.beam()) return 0f;
        return distanceCells / Math.max(1f, WarshipScale.cells(weapon.projectileSpeedPx()));
    }

    /**
     * Where to aim a projectile at a craft at {@code (x, y)} flying {@code (vx, vy)}: the craft's position
     * after the flight time to where it will be, solved twice (the second pass uses the first pass's
     * distance). A first-order lead: the craft is assumed to hold its velocity.
     *
     * @return {@code {aimX, aimY}}
     */
    public static float[] leadPoint(float fromX, float fromY, float x, float y, float vx, float vy,
                                    VanillaWeapon weapon) {
        if (weapon.beam()) return new float[]{x, y};
        float ax = x;
        float ay = y;
        for (int pass = 0; pass < 2; pass++) {
            float d = (float) Math.hypot(ax - fromX, ay - fromY);
            float t = flightSeconds(weapon, d);
            ax = x + vx * t;
            ay = y + vy * t;
        }
        return new float[]{ax, ay};
    }

    /** The craft's speed across the line of fire from {@code (fromX, fromY)}: the part a gun has to lead. */
    public static float crossingSpeed(float fromX, float fromY, float x, float y, float vx, float vy) {
        float dx = x - fromX;
        float dy = y - fromY;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) return (float) Math.hypot(vx, vy);
        return Math.abs(vx * (-dy / len) + vy * (dx / len));
    }

    /**
     * A deterministic number in [0, 1) from a seed and a salt: the same inputs always give the same
     * answer, and nothing about the shared random stream moves.
     */
    public static float unit(long seed, int salt) {
        long h = seed + 0x9E3779B97F4A7C15L * (salt + 1L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (float) ((h >>> 40) / (double) (1L << 24));
    }

    /** A stateless seed for one round: the mount, how many it has fired, the craft and the side. */
    public static long roundSeed(String slotId, int shotNumber, long craftId, int factionOrdinal) {
        long h = 0x9E3779B97F4A7C15L;
        h = (h ^ slotId.hashCode()) * 0xBF58476D1CE4E5B9L;
        h = (h ^ shotNumber) * 0x94D049BB133111EBL;
        h = (h ^ craftId) * 0xBF58476D1CE4E5B9L;
        h = (h ^ factionOrdinal) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
