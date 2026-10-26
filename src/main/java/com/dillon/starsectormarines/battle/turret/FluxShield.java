package com.dillon.starsectormarines.battle.turret;

/**
 * One hardpoint's directional flux shield: the rules, with no battle in them ({@code ground-turret-tiers.md}).
 *
 * <ul>
 *   <li><b>Raise and lower.</b> The shield goes up while the gun is threatened and for
 *   {@link FluxShieldSpec#lingerSeconds} after, and folds away otherwise. Its arc grows in from the facing over
 *   {@link FluxShieldSpec#raiseSeconds} and folds back over {@link FluxShieldSpec#lowerSeconds}; while it grows it
 *   covers only as much as has grown.</li>
 *   <li><b>Facing.</b> The arc swings toward the threat at {@link FluxShieldSpec#turnDegPerSecond}, so a flank the
 *   gun was not watching is open until it comes round.</li>
 *   <li><b>Block.</b> A round whose bearing from the gun lies inside the arc is stopped whole, and its damage times
 *   {@link FluxShieldSpec#fluxPerDamage} goes onto the capacitors. Outside the arc, and while the shield is down,
 *   rounds go through.</li>
 *   <li><b>Drain.</b> The generator sheds {@link FluxShieldSpec#dissipationPerSecond} always; holding the shield up
 *   costs {@link FluxShieldSpec#upkeepPerSecond}, and every round the gun fires with it up costs
 *   {@link FluxShieldSpec#fluxPerRound}. Upkeep is under dissipation, so only blocking fills the capacitors.</li>
 *   <li><b>Overload.</b> Full capacitors break the shield at once (the round that filled them is still stopped).
 *   For {@link FluxShieldSpec#overloadSeconds} it is down, the gun is silent and the capacitors vent to empty;
 *   then it may go up again.</li>
 *   <li><b>Energy gun.</b> A gun that draws on the capacitors ({@link FluxShieldSpec#gunDraw}) charges them every
 *   round, shield up or down, and holds fire once they pass its high mark until they bleed down to its low one.</li>
 * </ul>
 *
 * <p>Bearings are degrees in map space ({@code atan2(dy, dx)}). Not thread-safe beyond this: the serial passes
 * tick, block and read it; the gun's own update may call {@link #noteRound} for itself.
 */
public final class FluxShield {

    /** How many recent blocks are remembered for presentation. */
    public static final int HIT_MEMORY = 8;
    /** How long a block is remembered, in seconds. */
    public static final float HIT_SECONDS = 0.9f;

    private final FluxShieldSpec spec;
    private float extent;
    private float flux;
    private float pendingRoundFlux;
    private float facingDegrees;
    private float overloadLeft;
    private float sinceThreat = Float.POSITIVE_INFINITY;
    private float sinceBreak = Float.POSITIVE_INFINITY;
    private final float[] hitBearing = new float[HIT_MEMORY];
    private final float[] hitAge = new float[HIT_MEMORY];
    private final float[] hitWeight = new float[HIT_MEMORY];
    private int hitCount;
    private boolean gunHeld;

    public FluxShield(FluxShieldSpec spec, float facingDegrees) {
        this.spec = spec;
        this.facingDegrees = facingDegrees;
    }

    /**
     * Advances the shield by {@code dt} seconds. {@code threatBearing} is the bearing of what threatens the gun this
     * tick, or {@code NaN} when nothing does.
     */
    public void tick(float dt, float threatBearing) {
        sinceBreak += dt;
        ageHits(dt);
        float roundFlux = pendingRoundFlux;
        pendingRoundFlux = 0f;
        boolean threatened = !Float.isNaN(threatBearing);
        if (threatened) {
            sinceThreat = 0f;
            facingDegrees = turnToward(facingDegrees, threatBearing, spec.turnDegPerSecond() * dt);
        } else {
            sinceThreat += dt;
        }
        if (overloadLeft > 0f) {
            overloadLeft = Math.max(0f, overloadLeft - dt);
            flux = spec.fluxCapacity() * overloadLeft / spec.overloadSeconds();
            gunHeld = false;
            return;
        }
        boolean wantUp = sinceThreat <= spec.lingerSeconds();
        if (wantUp) extent = Math.min(1f, extent + dt / spec.raiseSeconds());
        else extent = Math.max(0f, extent - dt / spec.lowerSeconds());
        flux += roundFlux;
        if (extent > 0f) flux += spec.upkeepPerSecond() * dt;
        flux = Math.max(0f, flux - spec.dissipationPerSecond() * dt);
        if (flux >= spec.fluxCapacity()) overload();
        FluxShieldSpec.GunDraw draw = spec.gunDraw();
        if (draw != null) {
            float share = fluxShare();
            if (share >= draw.holdAbove()) gunHeld = true;
            else if (share <= draw.resumeBelow()) gunHeld = false;
        }
    }

    /** Whether a round arriving on {@code bearing} from the gun meets the shield. */
    public boolean covers(float bearing) {
        if (overloadLeft > 0f || extent <= 0f) return false;
        return Math.abs(angleBetween(bearing, facingDegrees)) <= halfArcDegrees();
    }

    /**
     * A round of {@code damage} arriving on {@code bearing}: stopped and charged to the capacitors when the shield
     * covers that bearing, true; otherwise left to go through, false.
     */
    public boolean absorb(float bearing, float damage) {
        if (!covers(bearing)) return false;
        float added = Math.max(0f, damage) * spec.fluxPerDamage();
        flux += added;
        rememberHit(bearing, Math.min(1f, 0.35f + added / (0.08f * spec.fluxCapacity())));
        if (flux >= spec.fluxCapacity()) overload();
        return true;
    }

    /**
     * The gun fired a round, charged next tick: with the shield up it costs {@link FluxShieldSpec#fluxPerRound}, and an
     * energy gun's draw ({@link FluxShieldSpec#gunDraw}) is charged on top, shield up or down.
     */
    public void noteRound() {
        if (overloadLeft > 0f) return;
        if (extent > 0f) pendingRoundFlux += spec.fluxPerRound();
        if (spec.gunDraw() != null) pendingRoundFlux += spec.gunDraw().perRound();
    }

    /**
     * A gun sharing the capacitors fired a round costing {@code flux}, charged next tick shield up or down: a boat's
     * guns and shield draw on one pool. Nothing is charged while overloaded, when the guns are silent.
     */
    public void noteRound(float flux) {
        if (overloadLeft > 0f || !(flux > 0f)) return;
        pendingRoundFlux += flux;
    }

    /** Whether an energy gun is holding fire to let the capacitors bleed down ({@link FluxShieldSpec#gunDraw}). */
    public boolean gunHeld() {
        return gunHeld;
    }

    private void overload() {
        flux = spec.fluxCapacity();
        extent = 0f;
        overloadLeft = spec.overloadSeconds();
        sinceBreak = 0f;
    }

    /** Whether the shield is overloaded: down, and its gun silent. */
    public boolean overloaded() {
        return overloadLeft > 0f;
    }

    /** Share of the overload still to run, {@code [0, 1]}; zero when not overloaded. */
    public float overloadShare() {
        return overloadLeft / spec.overloadSeconds();
    }

    /** How far the arc has grown in, {@code [0, 1]}: zero folded away, one fully raised. */
    public float extent() {
        return extent;
    }

    /** Half the width of the arc as it stands now, in degrees. */
    public float halfArcDegrees() {
        return 0.5f * spec.arcDegrees() * extent;
    }

    /** Share of the capacitors filled, {@code [0, 1]}. */
    public float fluxShare() {
        return Math.min(1f, flux / spec.fluxCapacity());
    }

    public float flux() {
        return flux;
    }

    /** The bearing the arc faces, in degrees. */
    public float facingDegrees() {
        return facingDegrees;
    }

    /** Seconds since the shield last broke; infinite if it never has. */
    public float sinceBreak() {
        return sinceBreak;
    }

    public FluxShieldSpec spec() {
        return spec;
    }

    /** Recent blocks, newest last: {@link #hitBearing}, {@link #hitAge} and {@link #hitWeight} index them. */
    public int hitCount() {
        return hitCount;
    }

    public float hitBearing(int i) {
        return hitBearing[i];
    }

    public float hitAge(int i) {
        return hitAge[i];
    }

    /** How hard block {@code i} struck, {@code [0, 1]}, by the share of the capacitors it filled. */
    public float hitWeight(int i) {
        return hitWeight[i];
    }

    private void rememberHit(float bearing, float weight) {
        if (hitCount == HIT_MEMORY) {
            System.arraycopy(hitBearing, 1, hitBearing, 0, HIT_MEMORY - 1);
            System.arraycopy(hitAge, 1, hitAge, 0, HIT_MEMORY - 1);
            System.arraycopy(hitWeight, 1, hitWeight, 0, HIT_MEMORY - 1);
            hitCount--;
        }
        hitBearing[hitCount] = bearing;
        hitAge[hitCount] = 0f;
        hitWeight[hitCount] = weight;
        hitCount++;
    }

    private void ageHits(float dt) {
        int kept = 0;
        for (int i = 0; i < hitCount; i++) {
            float age = hitAge[i] + dt;
            if (age > HIT_SECONDS) continue;
            hitBearing[kept] = hitBearing[i];
            hitAge[kept] = age;
            hitWeight[kept] = hitWeight[i];
            kept++;
        }
        hitCount = kept;
    }

    /** Signed difference {@code a - b} in degrees, in {@code (-180, 180]}. */
    static float angleBetween(float a, float b) {
        float d = (a - b) % 360f;
        if (d > 180f) d -= 360f;
        if (d <= -180f) d += 360f;
        return d;
    }

    private static float turnToward(float from, float to, float maxStep) {
        float d = angleBetween(to, from);
        if (Math.abs(d) <= maxStep) return to;
        return from + Math.signum(d) * maxStep;
    }
}
