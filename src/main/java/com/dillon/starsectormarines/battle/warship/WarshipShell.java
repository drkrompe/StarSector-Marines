package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/**
 * One round in flight from a warship's mount to the ground.
 *
 * <p>The ship owns it from the muzzle to the landing, so there is one clock for
 * what the player watches and what the ground suffers: the round is drawn
 * crossing the distance, and lands — damage, breach and blast together — on the
 * tick it arrives. The weapon id is the simulation's fact about which gun fired;
 * what that gun's round looks like is the renderer's business.
 *
 * <p>The round follows a {@link MissilePath}: a plain line for a gun's shell, a
 * boosting, weaving or wobbling flight for a missile. Where it is, how fast and
 * which way it points are all read off that one function at the round's own
 * clock, by the renderer and by point defence alike. A multi-warhead missile
 * flies as a carrier to its split point; on arrival it releases its
 * {@link Warhead}s, each a detonation committed at fire time.
 */
public final class WarshipShell {

    /**
     * One submunition a carrier releases at its split point: the detonation it will
     * make, how long after the split it makes it, where, and how large the fireball drawn there is.
     */
    public record Warhead(PendingDetonation detonation, float delaySeconds, float visualCells,
                          float aimX, float aimY) {
    }

    public final VanillaWeapon weapon;
    /** The side that fired it. */
    public final Faction faction;
    public final float fromX;
    public final float fromY;
    /** Where it lands, or, for a multi-warhead missile, the centre of the ground its warheads are spread over. */
    public final float toX;
    public final float toY;
    public final float flightSeconds;
    /** The size of the fireball drawn at the landing, in cells. It is not the blast's damage footprint. */
    public final float blastCells;
    private final MissilePath path;
    private final PendingDetonation landing;
    private final List<Warhead> warheads;
    private float elapsed;
    /** Set when point defence has shot the round down; it is dropped without landing. */
    boolean intercepted;

    WarshipShell(VanillaWeapon weapon, Faction faction, float fromX, float fromY, float toX, float toY,
                 MissilePath path, float blastCells, PendingDetonation landing, List<Warhead> warheads) {
        this.weapon = weapon;
        this.faction = faction;
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
        this.path = path;
        this.flightSeconds = Math.max(path.totalSeconds(), 0.001f);
        this.blastCells = blastCells;
        this.landing = landing;
        this.warheads = warheads == null ? List.of() : List.copyOf(warheads);
    }

    /** How far along the flight, 0 at the muzzle to 1 on the ground. */
    public float progress() {
        return Math.min(1f, elapsed / flightSeconds);
    }

    /** Seconds the round has been in flight. */
    public float elapsed() {
        return elapsed;
    }

    /** The round's flight as a function of time, for whoever draws a trail behind it. */
    public MissilePath path() {
        return path;
    }

    /** World X of the round now. */
    public float x() {
        return path.x(elapsed);
    }

    /** World Y of the round now. */
    public float y() {
        return path.y(elapsed);
    }

    /** Which way the round's nose points now, in the hull-facing convention. */
    public float headingDegrees() {
        return path.headingDegrees(elapsed);
    }

    /** Cells per second along the flight now. */
    float speedCells() {
        return path.speed(elapsed);
    }

    /** The warheads released when the carrier arrives; empty for a round that simply lands. */
    public List<Warhead> warheads() {
        return warheads;
    }

    boolean advance(float dt) {
        elapsed += dt;
        return elapsed >= flightSeconds;
    }

    /** The single detonation a round makes on landing; {@code null} for a carrier, whose warheads make them. */
    PendingDetonation landing() {
        return landing;
    }
}
