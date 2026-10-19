package com.dillon.starsectormarines.battle.warship;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a hull fight's hits look like, kept as presentation state beside the ships: one {@link Mark} for every round
 * a hostile hull or its shield took, one for every shield that collapsed or locked out, and the smoke an open breach
 * trails behind a moving ship ({@link Puff}).
 *
 * <p>A mark is held in the struck hull's own frame (about its sprite centre, unturned), so a flare on a shield's arc
 * or a fireball in a breach rides with a ship that is still coming about; a puff is held in the world, so a holed
 * ship under way leaves a trail. Everything is dated on the warships' own clock and drawn as a pure function of it.
 *
 * <p>Nothing here is read by the simulation, and nothing draws on its random stream: the jitter a renderer needs comes
 * from each mark's own {@link Mark#seed}. Damage, flux and tactics are decided before a mark is made.
 */
public final class WarshipHitFx {

    /** What a mark records. */
    public enum Kind {
        /** A round absorbed by a raised shield: a flare and a ripple at the point on the arc it met. */
        SHIELD,
        /** A round that reached the hull and did not open it. */
        HULL,
        /** A round that holed the hull through: a cell breached by this hit. */
        BREACH,
        /** A mount went with the plate under it. */
        MOUNT_LOST,
        /** The shield went down with an overload: it shatters. */
        SHIELD_COLLAPSE,
        /** An automatic shield dropped at its drop level and is locked down until it has vented: it gutters out. */
        SHIELD_LOCKOUT
    }

    /** The base game's damage types, which draw differently where it is cheap to. */
    public enum Character {
        KINETIC, HIGH_EXPLOSIVE, ENERGY, FRAGMENTATION, OTHER;

        static Character of(String damageType) {
            if (damageType == null) return OTHER;
            return switch (damageType) {
                case "KINETIC" -> KINETIC;
                case "HIGH_EXPLOSIVE" -> HIGH_EXPLOSIVE;
                case "ENERGY" -> ENERGY;
                case "FRAGMENTATION" -> FRAGMENTATION;
                default -> OTHER;
            };
        }
    }

    /**
     * One hit, in the struck hull's frame: {@code (localX, localY)} the point (across the nose, along it, from the
     * sprite centre), {@code (outX, outY)} a unit vector toward where the round came from, {@code sizeCells} the
     * radius of its flash at the hull's own scale, {@code colour} the weapon's blast colour (null for none),
     * {@code flux} the struck ship's flux share at the instant, and {@code beam} for a beam's pulse.
     */
    public record Mark(Warship ship, Kind kind, Character character, float localX, float localY, float outX,
                       float outY, float sizeCells, Color colour, float flux, boolean beam, int seed, float bornAt) {

        /** Seconds this mark is drawn for. */
        public float lifeSeconds() {
            return lifeOf(kind, character, beam);
        }
    }

    /** A puff of a breach's smoke, left in the world: where, its drift, when it was let go and how large it grows. */
    public record Puff(float x, float y, float driftX, float driftY, float sizeCells, float bornAt, boolean fire,
                       int seed) {

        public static final float LIFE_SECONDS = 5f;
    }

    /** No more marks than this are kept; the oldest go first. */
    static final int MAX_MARKS = 220;
    /** No more puffs than this are kept, across every ship. */
    static final int MAX_PUFFS = 360;
    /** Seconds between puffs from one smoking breach. */
    static final float PUFF_INTERVAL_SECONDS = 0.2f;
    /** The most places one hull smokes from, however holed it is. */
    static final int MAX_SMOKE_SOURCES = 5;
    /** The smallest and largest hit flash, as a radius in cells. */
    static final float MIN_SIZE_CELLS = 1.6f;
    static final float MAX_SIZE_CELLS = 8f;

    private final List<Mark> marks = new ArrayList<>();
    private final List<Puff> puffs = new ArrayList<>();
    // Insertion-ordered: the order sources are visited in must not depend on identity hash codes.
    private final Map<Warship, Watch> watched = new LinkedHashMap<>();
    private int counter;

    /** Every mark still drawn, oldest first. */
    public List<Mark> marks() {
        return Collections.unmodifiableList(marks);
    }

    /** Every smoke puff still drawn, oldest first. */
    public List<Puff> puffs() {
        return Collections.unmodifiableList(puffs);
    }

    static float lifeOf(Kind kind, Character character, boolean beam) {
        if (beam) return kind == Kind.SHIELD ? 0.45f : 0.6f;
        return switch (kind) {
            case SHIELD -> character == Character.KINETIC ? 0.8f : 0.7f;
            case HULL -> character == Character.ENERGY ? 1.6f : 1.3f;
            case BREACH -> 2.4f;
            case MOUNT_LOST -> 2f;
            case SHIELD_COLLAPSE -> 1.2f;
            case SHIELD_LOCKOUT -> 0.9f;
        };
    }

    /**
     * The radius a hit of {@code w} flashes at against a hull: the game's own hit glow (or half its blast) read at the
     * hull's drawn scale, so a hit is as large against the ship as it is in the base game. A round with no weapon
     * (a ground gun's, a battery's) is sized by its damage.
     */
    static float sizeCells(VanillaWeapon w, float damage) {
        float size;
        if (w != null) {
            float px = Math.max(w.hitGlowRadiusPx(), Math.max(w.explosionRadiusPx() * 0.5f, 8f));
            size = WarshipScale.hullCells(px) * 0.5f;
        } else {
            size = 1.5f + (float) Math.sqrt(Math.max(0f, damage)) / 4f;
        }
        return Math.max(MIN_SIZE_CELLS, Math.min(MAX_SIZE_CELLS, size));
    }

    /** A round absorbed by {@code ship}'s shield at world point {@code (px, py)}, fired from {@code (fromX, fromY)}. */
    void shieldHit(Warship ship, float px, float py, float fromX, float fromY, String damageType, VanillaWeapon w,
                   float damage, float now) {
        add(ship, Kind.SHIELD, Character.of(damageType), px, py, fromX, fromY, sizeCells(w, damage),
                w == null ? null : colourOf(w), w != null && w.beam(), now);
    }

    /** A round that reached {@code ship}'s hull at world point {@code (px, py)}; {@code breached} when it holed it. */
    void hullHit(Warship ship, float px, float py, float fromX, float fromY, String damageType, VanillaWeapon w,
                 float damage, boolean breached, float now) {
        add(ship, breached ? Kind.BREACH : Kind.HULL, Character.of(damageType), px, py, fromX, fromY,
                sizeCells(w, damage), w == null ? null : colourOf(w), w != null && w.beam() && !breached, now);
    }

    /** A mount of {@code ship} at world point {@code (px, py)} went with the plate under it. */
    void mountLost(Warship ship, float px, float py, float now) {
        add(ship, Kind.MOUNT_LOST, Character.HIGH_EXPLOSIVE, px, py, Float.NaN, Float.NaN,
                Math.max(3f, ship.widthCells * 0.18f), null, false, now);
    }

    private static Color colourOf(VanillaWeapon w) {
        if (w.beam() && w.fringe() != null) return w.fringe();
        if (w.explosionColor() != null) return w.explosionColor();
        return w.fringe();
    }

    private void add(Warship ship, Kind kind, Character character, float px, float py, float fromX, float fromY,
                     float size, Color colour, boolean beam, float now) {
        double r = Math.toRadians(ship.facingDegrees);
        float cos = (float) Math.cos(r);
        float sin = (float) Math.sin(r);
        float dx = px - ship.centerX();
        float dy = py - ship.centerY();
        float lx = dx * cos + dy * sin;
        float ly = -dx * sin + dy * cos;
        // Toward the source; with none, out from the hull's middle through the point.
        float ox = Float.isFinite(fromX) ? fromX - px : dx;
        float oy = Float.isFinite(fromY) ? fromY - py : dy;
        float len = (float) Math.hypot(ox, oy);
        if (len < 1e-4f) {
            ox = 0f;
            oy = 1f;
            len = 1f;
        }
        float olx = (ox * cos + oy * sin) / len;
        float oly = (-ox * sin + oy * cos) / len;
        int seed = mix(++counter * 0x9E3779B9 ^ ship.serial() * 0x85EBCA6B);
        marks.add(new Mark(ship, kind, character, lx, ly, olx, oly, size, colour, ship.fluxFraction(), beam, seed,
                now));
        if (marks.size() > MAX_MARKS) marks.remove(0);
    }

    /**
     * Advances one tick at the warships' clock {@code now}: forgets marks and puffs that have played out, notes every
     * shield that collapsed or locked out since the last tick, and lets an open breach on a ship over the map smoke.
     */
    void tick(float now, float dt, List<Warship> ships) {
        marks.removeIf(m -> now - m.bornAt() > m.lifeSeconds() || m.ship().destroyed());
        puffs.removeIf(p -> now - p.bornAt() > Puff.LIFE_SECONDS);
        watched.keySet().removeIf(Warship::destroyed);
        for (Warship ship : ships) {
            if (ship.destroyed()) continue;
            Watch w = watched.computeIfAbsent(ship, s -> new Watch(s));
            if (ship.hasShield()) {
                WarshipShield shield = ship.shieldModel();
                if (shield.collapses() > w.collapses) {
                    shieldEvent(ship, Kind.SHIELD_COLLAPSE, now);
                } else if (shield.lockedOut() && !w.lockedOut && w.shieldLevel > 0f) {
                    shieldEvent(ship, Kind.SHIELD_LOCKOUT, now);
                }
                w.collapses = shield.collapses();
                w.lockedOut = shield.lockedOut();
                w.shieldLevel = shield.level();
            }
            smoke(ship, w, now, dt);
        }
        while (puffs.size() > MAX_PUFFS) puffs.remove(0);
    }

    /**
     * A shield's collapse or lockout, at its centre, facing (in the mark's {@code out}) where its last absorbed hit
     * struck it: the side it was holding when it went, which an overloaded ship's lost target no longer says.
     */
    private void shieldEvent(Warship ship, Kind kind, float now) {
        Mark last = lastShieldHit(ship);
        float fx = Float.NaN;
        float fy = Float.NaN;
        if (last != null) {
            double r = Math.toRadians(ship.facingDegrees);
            float cos = (float) Math.cos(r);
            float sin = (float) Math.sin(r);
            float px = ship.centerX() + last.localX() * cos - last.localY() * sin;
            float py = ship.centerY() + last.localX() * sin + last.localY() * cos;
            // A point beyond the bubble on that side stands in for the source.
            fx = ship.shieldCentreX() + (px - ship.shieldCentreX()) * 4f;
            fy = ship.shieldCentreY() + (py - ship.shieldCentreY()) * 4f;
        }
        add(ship, kind, Character.OTHER, ship.shieldCentreX(), ship.shieldCentreY(), fx, fy,
                ship.shieldRadiusCells(), null, false, now);
    }

    /** The newest mark of a round {@code ship}'s shield absorbed that is still drawn, or null. */
    public Mark lastShieldHit(Warship ship) {
        for (int i = marks.size() - 1; i >= 0; i--) {
            Mark m = marks.get(i);
            if (m.ship() == ship && m.kind() == Kind.SHIELD) return m;
        }
        return null;
    }

    /** An open breach on a flying hull lets go a puff every interval, a breath of fire now and then among the smoke. */
    private void smoke(Warship ship, Watch w, float now, float dt) {
        WarshipPlating p = ship.plating();
        if (p == null || p.breachCount() == 0 || ship.berthed()) return;
        if (w.revision != p.revision()) {
            w.revision = p.revision();
            w.sources = smokeSources(p);
        }
        if (w.sources.length == 0) return;
        w.untilPuff -= dt;
        if (w.untilPuff > 0f) return;
        w.untilPuff += PUFF_INTERVAL_SECONDS / Math.min(2f, w.sources.length * 0.5f + 0.5f);
        if (w.untilPuff < 0f) w.untilPuff = 0f;
        int cell = w.sources[w.next++ % w.sources.length];
        int seed = mix(++counter * 0x27D4EB2F ^ cell);
        float cx = ship.cellX(cell);
        float cy = ship.cellY(cell);
        // The smoke leans out of the hole: away from the hull's middle, slowly.
        float ox = cx - ship.centerX();
        float oy = cy - ship.centerY();
        float len = Math.max(1e-3f, (float) Math.hypot(ox, oy));
        float drift = 1.2f + 0.8f * unit(seed);
        float size = Math.max(1.8f, ship.widthCells * (0.1f + 0.06f * unit(seed >>> 7)));
        puffs.add(new Puff(cx, cy, ox / len * drift + 0.25f, oy / len * drift + 0.15f, size, now,
                (seed & 7) == 0, seed));
    }

    /** Up to {@link #MAX_SMOKE_SOURCES} breached cells on the rim of a hole, the earliest-opened first. */
    public static int[] smokeSources(WarshipPlating p) {
        List<int[]> rim = new ArrayList<>();
        for (int r = 0; r < p.rows; r++) {
            for (int c = 0; c < p.cols; c++) {
                if (!p.isHull(c, r) || !p.breached(c, r) || !nextToStanding(p, c, r)) continue;
                rim.add(new int[]{r * p.cols + c, Float.floatToIntBits(p.breachedAt(c, r))});
            }
        }
        if (rim.isEmpty()) return new int[0];
        // Spread across the holes: every n-th rim cell in the order they opened.
        rim.sort((a, b) -> Float.compare(Float.intBitsToFloat(a[1]), Float.intBitsToFloat(b[1])));
        int n = Math.min(MAX_SMOKE_SOURCES, rim.size());
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = rim.get(i * rim.size() / n)[0];
        return out;
    }

    private static boolean nextToStanding(WarshipPlating p, int c, int r) {
        return standing(p, c - 1, r) || standing(p, c + 1, r) || standing(p, c, r - 1) || standing(p, c, r + 1);
    }

    private static boolean standing(WarshipPlating p, int c, int r) {
        return c >= 0 && r >= 0 && c < p.cols && r < p.rows && p.isHull(c, r) && !p.breached(c, r);
    }

    /** A 0-1 figure from a seed's low bits. */
    public static float unit(int seed) {
        return (mix(seed) & 0xFFFF) / 65535f;
    }

    /** A well-mixed int from {@code h}: the renderer's and this class's only source of jitter. */
    public static int mix(int h) {
        h ^= h >>> 16;
        h *= 0x7feb352d;
        h ^= h >>> 15;
        h *= 0x846ca68b;
        h ^= h >>> 16;
        return h;
    }

    /** What was last seen of one ship's shield and smoke. */
    private static final class Watch {
        int collapses;
        boolean lockedOut;
        float shieldLevel;
        int revision = -1;
        int[] sources = new int[0];
        int next;
        float untilPuff;

        Watch(Warship ship) {
            if (ship.hasShield()) {
                collapses = ship.shieldModel().collapses();
                lockedOut = ship.shieldModel().lockedOut();
            }
        }
    }
}
