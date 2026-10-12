package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.combat.Interceptable;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/**
 * One body in flight from a warship's mount to the ground: a gun's shell, a
 * rocket, a homing missile, or the carrier of a multi-warhead missile.
 *
 * <p>The ship owns it from the muzzle to the landing, so there is one clock for
 * what the player watches and what the ground suffers: the body is drawn
 * crossing the distance, and lands — damage, breach and blast together — on the
 * tick it arrives. The weapon id is the simulation's fact about which gun fired;
 * what that gun's round looks like is the renderer's business.
 *
 * <p>The body follows a {@link MissilePath}: a plain line for a gun's shell, a
 * boosting, weaving or wobbling flight for a missile. Where it is, how fast and
 * which way it points are all read off that one function at the body's own
 * clock, by the renderer and by point defence alike. A multi-warhead missile
 * flies as a carrier to its split point; on arrival it releases its
 * {@link Warhead}s, each a detonation committed at fire time and a body of its
 * own ({@link WarheadStrike}).
 *
 * <p>Its damage and blast are a budget that may be fused: when the swarm is over
 * a cap a body absorbs the share of one it replaces ({@link #merge}), so what lands
 * is the sum of what was fired.
 */
public final class WarshipShell implements Interceptable {

    /**
     * One submunition a carrier releases at its split point: the detonation it will make, how long
     * after the split it makes it, where, how large the fireball drawn there is, the path it flies
     * from the split to the aim point, and how tough it is.
     */
    public record Warhead(PendingDetonation detonation, float delaySeconds, float visualCells,
                          float aimX, float aimY, BloomPath path, float hitpoints) {
    }

    /** The ground a volley or sheaf is scattered over, shared by every body of it, for the telegraph. */
    public record Footprint(float x, float y, float radius) {
    }

    public final VanillaWeapon weapon;
    /** The side that fired it. */
    public final Faction faction;
    /** The ship that fired it. */
    final Warship shooter;
    public final float fromX;
    public final float fromY;
    /** Where it lands, or, for a multi-warhead missile, the centre of the ground its warheads are spread over. */
    public final float toX;
    public final float toY;
    public final float flightSeconds;
    private final MissilePath path;
    private final List<Warhead> warheads;
    private final Footprint footprint;
    private final float hitpoints;
    private final String splitSoundId;
    private final float splitScale;
    private final float penetration;
    private float damage;
    private float damageRadius;
    private float blastCells;
    private float elapsed;
    /** Set when point defence has shot the round down; it is dropped without landing. */
    boolean intercepted;

    WarshipShell(VanillaWeapon weapon, Faction faction, Warship shooter, float fromX, float fromY, float toX,
                 float toY, MissilePath path, float blastCells, float damage, float penetration,
                 float damageRadius, List<Warhead> warheads, Footprint footprint, float hitpoints,
                 String splitSoundId, float splitScale) {
        this.weapon = weapon;
        this.faction = faction;
        this.shooter = shooter;
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
        this.path = path;
        this.flightSeconds = Math.max(path.totalSeconds(), 0.001f);
        this.blastCells = blastCells;
        this.damage = damage;
        this.penetration = penetration;
        this.damageRadius = damageRadius;
        this.warheads = warheads == null ? List.of() : List.copyOf(warheads);
        this.footprint = footprint;
        this.hitpoints = hitpoints;
        this.splitSoundId = splitSoundId;
        this.splitScale = splitScale;
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
    @Override
    public float x() {
        return path.x(elapsed);
    }

    /** World Y of the round now. */
    @Override
    public float y() {
        return path.y(elapsed);
    }

    /** Which way the round's nose points now, in the hull-facing convention. */
    public float headingDegrees() {
        return path.headingDegrees(elapsed);
    }

    /** Cells per second along the flight now. */
    @Override
    public float speedCells() {
        return path.speed(elapsed);
    }

    @Override
    public Faction faction() {
        return faction;
    }

    @Override
    public float hitpoints() {
        return hitpoints;
    }

    @Override
    public boolean isIntercepted() {
        return intercepted;
    }

    @Override
    public void intercept() {
        intercepted = true;
    }

    /** The size of the fireball drawn at the landing, in cells. It is not the blast's damage footprint. */
    public float blastCells() {
        return blastCells;
    }

    /** The damage the landing does (the sum of what has been fused into it); a carrier's is its strongest warhead's. */
    public float damage() {
        if (warheads.isEmpty()) return damage;
        float strongest = 0f;
        for (Warhead w : warheads) strongest = Math.max(strongest, w.detonation().damage);
        return strongest;
    }

    /** How hard the landing hits armour. */
    public float penetration() {
        return penetration;
    }

    /** The blast's damage radius, in cells. */
    public float damageRadius() {
        return damageRadius;
    }

    /** The warheads released when the carrier arrives; empty for a round that simply lands. */
    public List<Warhead> warheads() {
        return warheads;
    }

    /** Whether this is a carrier that releases warheads rather than a round that lands. */
    public boolean carrier() {
        return !warheads.isEmpty();
    }

    /** How many damage-bearing bodies this one stands for in the air: one, or the warheads it will release. */
    public int bodyCount() {
        return warheads.isEmpty() ? 1 : warheads.size();
    }

    /** The ground this body's volley is scattered over, or {@code null} when it is not part of a scattered one. */
    public Footprint footprint() {
        return footprint;
    }

    /** The game's sound id for the split, or {@code null}. */
    public String splitSoundId() {
        return splitSoundId;
    }

    /** The relative size of the split flash a carrier makes. */
    public float splitScale() {
        return splitScale;
    }

    /** Folds another round's damage and blast into this one: damage adds, and so does area. */
    void merge(float extraDamage, float extraDamageRadius, float extraVisualCells) {
        damage += extraDamage;
        damageRadius = SwarmBudget.mergeRadius(damageRadius, extraDamageRadius);
        blastCells = SwarmBudget.mergeRadius(blastCells, extraVisualCells);
    }

    boolean advance(float dt) {
        elapsed += dt;
        return elapsed >= flightSeconds;
    }

    /** The single detonation a round makes on landing; {@code null} for a carrier, whose warheads make them. */
    PendingDetonation landing() {
        if (!warheads.isEmpty()) return null;
        return WarshipSystem.detonation(faction, path.landX(), path.landY(), damage, penetration, damageRadius);
    }
}
