package com.dillon.starsectormarines.battle.evacuation.flier;

import java.util.ArrayList;
import java.util.List;

/**
 * A pack of Harriers and the one decision it makes together: when to dive, and on what.
 *
 * <p>A pack {@linkplain Phase#LOITER loiters} on a ring out of reach of its prey's guns, on its own side of the
 * prey. Once it has circled a while and gathered, it {@linkplain Phase#DIVE dives} on the place it chose: each
 * member on its own body there ({@link HarrierFlight#prey}, or the pack's target), going in after its own
 * seeded delay from wherever on the ring it has reached, low and fast, biting once as it passes, and then the
 * pack {@linkplain Phase#PULL_UP pulls up},
 * flying out and climbing, and loiters again. That is the trade the pack offers the ground: a committed pack is
 * tight and low, so one anti-air burst catches several; a pack that has been mauled
 * ({@link #shouldSplit()}) splits and comes on in smaller pieces that bleed the line slowly.
 *
 * <p>Pure: it reads and writes its members' bodies and nothing else. The battle hands it the prey's position each
 * tick and lands the strikes it asks for ({@link Strikes}).
 */
public final class HarrierPack {

    public enum Phase { LOITER, DIVE, PULL_UP }

    /** Lands a member's strike on the target; the pack does not know what a hit does. */
    public interface Strikes {
        void strike(long harrier, long target);
    }

    public final int id;
    /** Members in slot order; the dead are removed by the battle before {@link #advance}. */
    public final List<HarrierFlight> members = new ArrayList<>();
    /** A body the pack escorts (its Matriarch), or 0; with no prey it loiters over the escort. */
    public long escortId;

    Phase phase = Phase.LOITER;
    float phaseSeconds;
    float loiterSeconds;
    /** The committed or intended prey, 0 for none. */
    long target;
    float targetX, targetY, targetRadius;
    /** Where it loiters with no prey: its release point, or over its escort. */
    float anchorX, anchorY;
    float loiterX, loiterY;
    /** Where the dive started from, so the pull-up flies away from the prey. */
    float diveFromX, diveFromY;
    /** Members at the start of the last dive, for the split rule. */
    int divedWith;
    boolean splitDue;
    int dives;
    /** Harriers still to climb out of the hatch ({@link SwarmFliers#hatch}); the pack commits only at none. */
    int unhatched;
    int hatched;
    int nextHatchTick;
    long hatchSource;
    float hatchX, hatchY;
    HarrierLook hatchLook;
    /** The circuit it flies off its spire's roost, or {@code null} for a pack that hunts. */
    HarrierPatrol patrol;
    /** Members that landed back on their spire this tick, for the battle to take off the field. */
    final List<HarrierFlight> landedNow = new ArrayList<>();
    private final float[] push = new float[2];

    public HarrierPack(int id, float anchorX, float anchorY) {
        this.id = id;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.loiterX = anchorX;
        this.loiterY = anchorY;
    }

    public Phase phase() { return phase; }
    public long target() { return target; }
    public int dives() { return dives; }
    /** Harriers of this pack still to hatch. */
    public int unhatched() { return unhatched; }
    public float loiterX() { return loiterX; }
    public float loiterY() { return loiterY; }
    /** The circuit the pack flies, or {@code null}. */
    public HarrierPatrol patrol() { return patrol; }

    /** Moves the no-prey loiter point (the escort's position, say). */
    public void anchorAt(float x, float y) {
        anchorX = x;
        anchorY = y;
    }

    /**
     * Sets what the pack means to dive on and where it is now. While loitering the prey may change freely; once
     * committed the pack keeps it, and a prey lost mid-dive ({@code id == 0}) breaks the dive off.
     */
    public void prey(long id, float x, float y, float radius) {
        if (phase == Phase.DIVE && id != target) {
            // The body the pack chose is gone: the members diving on it give up; the rest dive on.
            boolean allDone = true;
            for (HarrierFlight m : members) {
                if (m.prey == 0L || m.prey == target) m.struck = true;
                allDone &= m.struck;
            }
            if (allDone) breakOff();
            return;
        }
        if (phase != Phase.LOITER && id == 0L) return;
        target = id;
        targetX = x;
        targetY = y;
        targetRadius = radius;
    }

    /** Whether the last pull-up found the pack mauled enough to split. Cleared by {@link #takeSplit()}. */
    public boolean shouldSplit() { return splitDue; }

    /** Hands over the back half of the pack, slot order kept, and clears the split flag. */
    public List<HarrierFlight> takeSplit() {
        splitDue = false;
        int keep = (members.size() + 1) / 2;
        List<HarrierFlight> rest = new ArrayList<>(members.subList(keep, members.size()));
        members.subList(keep, members.size()).clear();
        reslot();
        return rest;
    }

    /** Re-numbers the loiter slots after members were lost or handed over. */
    public void reslot() {
        for (int i = 0; i < members.size(); i++) members.get(i).slot = i;
    }

    /** Where the members flying with the pack are, on average (its carriers away fetching are not). */
    public float centreX() {
        float s = 0f;
        int n = 0;
        for (HarrierFlight m : members) if (m.carry == null) { s += m.body.x; n++; }
        return n == 0 ? anchorX : s / n;
    }

    public float centreY() {
        float s = 0f;
        int n = 0;
        for (HarrierFlight m : members) if (m.carry == null) { s += m.body.y; n++; }
        return n == 0 ? anchorY : s / n;
    }

    /** Members away fetching a body ({@link HarrierCarry}). */
    public int carriersOut() {
        int n = 0;
        for (HarrierFlight m : members) if (m.carry != null) n++;
        return n;
    }

    /** Members flying with the pack. */
    private int flying() {
        return members.size() - carriersOut();
    }

    /** One tick of the pack: the phase decision, then every member's flight. */
    public void advance(float dt, Strikes strikes) {
        if (members.isEmpty()) return;
        phaseSeconds += dt;
        switch (phase) {
            case LOITER -> loiter(dt);
            case DIVE -> dive(dt, strikes);
            case PULL_UP -> pullUp(dt);
        }
        float folding = dt / HarrierFlight.FOLD_SECONDS;
        for (HarrierFlight m : members) {
            m.wingClock += dt;
            // Folding is for going in: a member still circling out its stagger keeps its wings spread.
            boolean diving = phase == Phase.DIVE && m.carry == null && !m.struck && phaseSeconds >= m.diveDelay;
            m.fold = diving ? Math.min(1f, m.fold + folding) : Math.max(0f, m.fold - folding);
            // The head locks on what it is diving at, and otherwise looks about the prey the pack has in mind.
            boolean ownPrey = m.prey != 0L;
            float lookX = ownPrey ? m.preyX : target != 0L ? targetX : Float.NaN;
            float lookY = ownPrey ? m.preyY : target != 0L ? targetY : Float.NaN;
            m.rig.advance(m, HarrierWingbeat.phase(m, m.carry != null ? Phase.LOITER : phase), lookX, lookY, diving, dt);
            HarrierAltitude.follow(m, dt);
        }
    }

    private void loiter(float dt) {
        loiterSeconds += dt;
        if (target != 0L) {
            // Out of reach on the pack's own side of its prey.
            float cx = centreX(), cy = centreY();
            float dx = cx - targetX, dy = cy - targetY;
            float d = (float) Math.hypot(dx, dy);
            if (d < 1e-3f) { dx = 0f; dy = 1f; d = 1f; }
            loiterX = targetX + dx / d * HarrierRules.STANDOFF;
            loiterY = targetY + dy / d * HarrierRules.STANDOFF;
        } else {
            loiterX = anchorX;
            loiterY = anchorY;
        }
        if (target == 0L && patrol != null && patrol.homing()) {
            land(dt);
            return;
        }
        for (HarrierFlight m : members) if (m.carry == null) circle(m, loiterSeconds, dt);
        if (target != 0L && unhatched <= 0 && loiterSeconds >= HarrierRules.MIN_LOITER_SECONDS && gathered()) commit();
    }

    /** A patrol home over its spire: each member drops onto the crown and lands there, one by one. */
    private void land(float dt) {
        loiterX = patrol.homeX;
        loiterY = patrol.homeY;
        for (int i = members.size() - 1; i >= 0; i--) {
            HarrierFlight m = members.get(i);
            if (m.carry != null) continue;
            HarrierSteering.separation(m, members, push);
            float d = m.body.distanceTo(patrol.homeX, patrol.homeY);
            HarrierSteering.fly(m.body, patrol.homeX, patrol.homeY, push[0] * 0.3f, push[1] * 0.3f,
                    Math.min(HarrierRules.LOITER_SPEED, 1f + d), HarrierRules.LOITER_TURN_DEG_PER_SEC, dt);
            float low = Math.max(HarrierRules.ROOST_ALTITUDE, Math.min(1f, d / HarrierRules.DIVE_GLIDE_CELLS * 2f));
            m.altitude = HarrierSteering.ease(m.altitude, low, dt);
            if (d <= HarrierPatrol.LAND_REACH && m.altitude <= HarrierRules.ROOST_ALTITUDE + 0.05f) {
                members.remove(i);
                landedNow.add(m);
                patrol.landed++;
            }
        }
        if (!landedNow.isEmpty()) reslot();
    }

    /** Flies one member round the loiter ring, at its slot, {@code seconds} into the orbit. */
    private void circle(HarrierFlight m, float seconds, float dt) {
        int n = members.size();
        float ring = HarrierRules.ringRadius(n);
        double a = Math.toRadians(seconds * HarrierRules.LOITER_ORBIT_DEG_PER_SEC + 360.0 * m.slot / n);
        float gx = loiterX + (float) Math.cos(a) * ring;
        float gy = loiterY + (float) Math.sin(a) * ring;
        HarrierSteering.separation(m, members, push);
        HarrierSteering.fly(m.body, gx, gy, push[0], push[1], HarrierRules.LOITER_SPEED,
                HarrierRules.LOITER_TURN_DEG_PER_SEC, dt);
        m.altitude = HarrierSteering.ease(m.altitude, 1f, dt);
    }

    /** Whether enough of the pack is on the ring to go as one. */
    boolean gathered() {
        int near = 0;
        float r = HarrierRules.ringRadius(members.size()) + HarrierRules.GATHER_MARGIN;
        float r2 = r * r;
        for (HarrierFlight m : members) {
            if (m.carry != null) continue;
            float dx = m.body.x - loiterX, dy = m.body.y - loiterY;
            if (dx * dx + dy * dy <= r2) near++;
        }
        return near >= HarrierRules.GATHER_SHARE * flying();
    }

    private void commit() {
        phase = Phase.DIVE;
        phaseSeconds = 0f;
        divedWith = flying();
        diveFromX = centreX();
        diveFromY = centreY();
        dives++;
        for (HarrierFlight m : members) {
            if (m.carry != null) continue;
            m.struck = false;
            m.wentLow = false;
            m.prey = 0L;
            // Seeded off the id and the dive, so a replay staggers the same and each dive differently.
            long h = (m.id * 31L + dives) * 0x9E3779B97F4A7C15L;
            m.diveDelay = HarrierRules.DIVE_STAGGER_SECONDS * (float) ((h >>> 40) % 1000) / 1000f;
        }
    }

    private void dive(float dt, Strikes strikes) {
        boolean allDone = true;
        for (HarrierFlight m : members) {
            if (m.carry != null) continue;
            if (!m.struck && phaseSeconds < m.diveDelay) {
                // Not its turn yet: it circles on, and goes in later from further round the ring.
                allDone = false;
                circle(m, loiterSeconds + phaseSeconds, dt);
                continue;
            }
            HarrierSteering.separation(m, members, push);
            if (!m.struck) {
                allDone = false;
                long prey = m.prey != 0L ? m.prey : target;
                float px = m.prey != 0L ? m.preyX : targetX, py = m.prey != 0L ? m.preyY : targetY;
                float reach = HarrierRules.STRIKE_REACH + (m.prey != 0L ? m.preyRadius : targetRadius);
                float d = m.body.distanceTo(px, py);
                if (d <= reach) {
                    strikes.strike(m.id, prey);
                    m.struck = true;
                }
                float low = Math.max(HarrierRules.DIVE_FLOOR_ALTITUDE, Math.min(1f, d / HarrierRules.DIVE_GLIDE_CELLS));
                m.altitude = HarrierSteering.ease(m.altitude, low, dt);
                HarrierSteering.fly(m.body, px, py, push[0], push[1], HarrierRules.DIVE_SPEED,
                        HarrierRules.DIVE_TURN_DEG_PER_SEC, dt);
            } else {
                climbOut(m, dt);
            }
            if (m.altitude <= HarrierRules.LOW_ALTITUDE) m.wentLow = true;
        }
        if (allDone || phaseSeconds >= HarrierRules.MAX_DIVE_SECONDS + HarrierRules.DIVE_STAGGER_SECONDS) breakOff();
    }

    /** Ends the dive: every member pulls up, struck or not. */
    private void breakOff() {
        if (phase != Phase.DIVE) return;
        phase = Phase.PULL_UP;
        phaseSeconds = 0f;
        for (HarrierFlight m : members) m.struck = true;
    }

    private void pullUp(float dt) {
        for (HarrierFlight m : members) {
            if (m.carry != null) continue;
            HarrierSteering.separation(m, members, push);
            climbOut(m, dt);
        }
        if (phaseSeconds >= HarrierRules.PULL_UP_SECONDS) {
            phase = Phase.LOITER;
            phaseSeconds = 0f;
            loiterSeconds = 0f;
            int lost = divedWith - flying();
            splitDue = divedWith > 0 && lost >= HarrierRules.SPLIT_LOSS_SHARE * divedWith
                    && members.size() >= 2 * HarrierRules.SPLIT_MIN_HALF;
        }
    }

    /** Straight on along the nose, bending away from where the dive began, climbing. */
    private void climbOut(HarrierFlight m, float dt) {
        double rad = Math.toRadians(m.body.facingDegrees + 90f);
        float ax = m.body.x - diveFromX, ay = m.body.y - diveFromY;
        float ad = (float) Math.hypot(ax, ay);
        float gx = m.body.x + (float) Math.cos(rad) * 6f + (ad > 1e-3f ? ax / ad * 3f : 0f);
        float gy = m.body.y + (float) Math.sin(rad) * 6f + (ad > 1e-3f ? ay / ad * 3f : 0f);
        HarrierSteering.fly(m.body, gx, gy, push[0], push[1], HarrierRules.PULL_UP_SPEED,
                HarrierRules.LOITER_TURN_DEG_PER_SEC, dt);
        m.altitude = HarrierSteering.ease(m.altitude, 1f, dt);
    }
}
