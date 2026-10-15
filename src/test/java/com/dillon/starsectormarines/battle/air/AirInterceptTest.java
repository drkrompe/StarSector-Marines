package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.HullKinematics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tail chase: which point a cover fighter steers for, and how long that keeps its guns on a prey that flies a
 * straight track. The kinematic runs use the fighter's real steering integrator over the ground-scale handling the
 * vanilla {@code ship_data.csv} numbers convert to (a Talon against a Dagger), so the window lengths are the lengths the
 * guns would have.
 */
class AirInterceptTest {

    private static final float DT = 1f / 30f;
    private static final AirHandling TALON = HullKinematics.fromSpec(325f, 400f, 300f, 150f);
    private static final AirHandling DAGGER = HullKinematics.fromSpec(175f, 350f, 300f, 90f);
    private static final AirHandling BROADSWORD = HullKinematics.fromSpec(200f, 400f, 300f, 90f);

    private record Chase(float windowSeconds, float expectedHits, float firstWindowSecond, boolean everBehind) {}

    /**
     * Flies {@code fighter} against a prey on a straight track from {@code (0, 0)} along +x for {@code seconds} and
     * measures what the guns were given. {@code tail} flies {@link AirIntercept}; otherwise the old head-on lead pursuit.
     */
    private static Chase fly(AirHandling fighter, AirHandling preyCraft, float fx, float fy, float facing, float seconds,
                             boolean tail) {
        AirBody f = new AirBody();
        f.x = fx;
        f.y = fy;
        f.facingDegrees = facing;
        double nose = Math.toRadians(facing + 90f);
        f.vx = (float) Math.cos(nose) * fighter.maxSpeed();
        f.vy = (float) Math.sin(nose) * fighter.maxSpeed();
        float preySpeed = preyCraft.maxSpeed();
        AirBody prey = new AirBody();
        prey.vx = preySpeed;
        float window = 0f;
        float hits = 0f;
        float first = -1f;
        boolean behind = false;
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            if (tail) {
                AirIntercept.Plan plan = AirIntercept.plan(f.x, f.y, f.vx, f.vy, fighter.maxSpeed(), prey.x, prey.y, prey.vx, prey.vy);
                behind |= plan.stage() == AirIntercept.Stage.PURSUE;
                AirSteeringSystem.steer(f, plan.x(), plan.y(), plan.mode(), fighter, DT);
            } else {
                float[] lead = AirToAirGun.leadPoint(f.x, f.y, f.speed(), prey.x, prey.y, prey.vx, prey.vy);
                AirSteeringSystem.steer(f, lead[0], lead[1], SteeringMode.CRUISE, fighter, DT);
            }
            prey.x += preySpeed * DT;
            float range = (float) Math.hypot(prey.x - f.x, prey.y - f.y);
            float off = AirToAirGun.offNoseDegrees(f, prey.x, prey.y);
            if (AirToAirGun.inWindow(range, off)) {
                window += DT;
                hits += AirToAirGun.hitChance(range, off) * AirToAirGun.ROUNDS_PER_SECOND * DT;
                if (first < 0f) first = i * DT;
            }
        }
        return new Chase(window, hits, first, behind);
    }

    @Test
    void theRearQuarterIsTheConeBehindThePreyAndNothingBeside() {
        assertTrue(AirIntercept.isRearQuarter(-20f, 0f, 0f, 0f, 20f, 0f));
        float c50 = (float) Math.cos(Math.toRadians(50));
        float s50 = (float) Math.sin(Math.toRadians(50));
        assertTrue(AirIntercept.isRearQuarter(-20f * c50, 20f * s50, 0f, 0f, 20f, 0f));
        float c70 = (float) Math.cos(Math.toRadians(70));
        float s70 = (float) Math.sin(Math.toRadians(70));
        assertFalse(AirIntercept.isRearQuarter(-20f * c70, 20f * s70, 0f, 0f, 20f, 0f));
        assertFalse(AirIntercept.isRearQuarter(0f, 20f, 0f, 0f, 20f, 0f));
        assertFalse(AirIntercept.isRearQuarter(20f, 0f, 0f, 0f, 20f, 0f));
        assertFalse(AirIntercept.isRearQuarter(-20f, 0f, 0f, 0f, 0.5f, 0f), "a hovering prey has no tail");
    }

    @Test
    void aHoveringPreyHasNoTailToGetBehindAndIsPursuedPlainly() {
        AirIntercept.Plan plan = AirIntercept.plan(0f, 0f, 20f, 0f, 35f, 40f, 0f, 0.5f, 0f);
        assertEquals(AirIntercept.Stage.TRACKLESS, plan.stage());
        assertTrue(plan.x() > 38f);
    }

    @Test
    void aPreyItCannotOutrunIsFlownLeadPursuitAtRatherThanChasedFromBehind() {
        // 21 cells per second against 20.7: a stern chase never closes.
        AirIntercept.Plan plan = AirIntercept.plan(60f, 0f, 21f, 0f, 21f, 100f, 0f, 20.7f, 0f);
        assertEquals(AirIntercept.Stage.MATCHED, plan.stage());
    }

    @Test
    void fromTheRearQuarterOfASlowerPreyTheSteerPointIsASlotInItsWakeLedAlongItsTrack() {
        AirIntercept.Plan plan = AirIntercept.plan(40f, 15f, 35f, 0f, 35f, 100f, 0f, 19f, 0f);
        assertEquals(AirIntercept.Stage.PURSUE, plan.stage());
        float preyAtArrival = 100f + 19f * AirIntercept.MAX_LEAD_SECONDS;
        assertTrue(plan.x() < preyAtArrival, "behind where the prey will be");
        assertEquals(0f, plan.y(), 1e-3f, "on its track");
        assertTrue(plan.x() > 100f, "led along that track");
    }

    @Test
    void closeInTheFighterBrakesOntoTheSlotInsteadOfFlyingThroughIt() {
        AirIntercept.Plan close = AirIntercept.plan(88f, 0f, 30f, 0f, 35f, 100f, 0f, 19f, 0f);
        assertEquals(AirIntercept.Stage.PURSUE, close.stage());
        assertEquals(SteeringMode.BRAKE_TO_STATION, close.mode());
        AirIntercept.Plan far = AirIntercept.plan(0f, 0f, 35f, 0f, 35f, 100f, 0f, 19f, 0f);
        assertEquals(SteeringMode.CRUISE, far.mode());
    }


    @Test
    void theFighterEasesOntoTheSlotAndStaysThereRatherThanOverrunningAndCirclingBack() {
        // Starting in the wake at 25 cells, twice the prey's speed: no pass through the prey, and the guns held to the end.
        AirBody f = new AirBody();
        f.x = -25f;
        f.facingDegrees = -90f;
        f.vx = TALON.maxSpeed();
        AirBody prey = new AirBody();
        prey.vx = DAGGER.maxSpeed();
        float closest = Float.MAX_VALUE;
        float window = 0f;
        for (int i = 0; i < Math.round(14f / DT); i++) {
            AirIntercept.Plan plan = AirIntercept.plan(f.x, f.y, f.vx, f.vy, TALON.maxSpeed(), prey.x, prey.y, prey.vx, prey.vy);
            AirSteeringSystem.steer(f, plan.x(), plan.y(), plan.mode(), TALON, DT);
            prey.x += prey.vx * DT;
            if (i * DT > 3f) closest = Math.min(closest, prey.x - f.x);
            if (AirToAirGun.inWindow((float) Math.hypot(prey.x - f.x, prey.y - f.y), AirToAirGun.offNoseDegrees(f, prey.x, prey.y))) window += DT;
        }
        assertTrue(closest > 2f, "never overran the prey: closest " + closest);
        assertTrue(window >= 10f, "held the window " + window + " s of 14");
    }
    @Test
    void fromAheadOrBesideTheFighterFliesLeadPursuitOfThePreyItself() {
        AirIntercept.Plan ahead = AirIntercept.plan(200f, 5f, -35f, 0f, 35f, 100f, 0f, 19f, 0f);
        assertEquals(AirIntercept.Stage.CLOSE, ahead.stage());
        AirIntercept.Plan beside = AirIntercept.plan(100f, 30f, 0f, -35f, 35f, 100f, 0f, 19f, 0f);
        assertEquals(AirIntercept.Stage.CLOSE, beside.stage());
    }

    @Test
    void aFasterFighterBehindThePreyHoldsItsGunsOnItThroughMostOfAChase() {
        // 30 cells behind and 10 off the tail axis, flying the prey's way.
        Chase tail = fly(TALON, DAGGER, -30f, 10f, -90f, 12f, true);
        assertTrue(tail.everBehind());
        assertTrue(tail.windowSeconds() >= 7f, "the guns' window held " + tail.windowSeconds() + " s of 12");
        assertTrue(tail.expectedHits() >= 30f, "expected hits " + tail.expectedHits());
    }

    @Test
    void aTailChaseGivesFarLongerAWindowThanMeetingThePreyHeadOnInOnePass() {
        // Nose to nose, 100 cells apart, a few cells off the track: the first window is a pass of about half a second.
        Chase met = fly(TALON, DAGGER, 100f, 3f, 90f, 2.2f, true);
        assertTrue(met.windowSeconds() < 1f, "a head-on pass is brief: " + met.windowSeconds());
        // The same fighter given the prey's tail instead.
        Chase behind = fly(TALON, DAGGER, -30f, 10f, -90f, 2.2f, true);
        assertTrue(behind.windowSeconds() > met.windowSeconds() * 2f,
                "tail " + behind.windowSeconds() + " s against head-on " + met.windowSeconds() + " s");
    }

    @Test
    void afterThePassTheFighterTurnsInBehindTheEgressingPreyAndGetsItsWindow() {
        Chase met = fly(TALON, DAGGER, 100f, 3f, 90f, 14f, true);
        assertTrue(met.everBehind(), "the fighter reaches the prey's rear quarter after it has gone by");
        assertTrue(met.windowSeconds() >= 4f, "window over the whole encounter " + met.windowSeconds());
    }

    @Test
    void anEquallyFastPreyIsNotChasedFromBehindSoTheOldPursuitStandsThere() {
        // A Broadsword after a Dagger: a seventh faster, below the overtaking margin once ground-scaled.
        assertTrue(BROADSWORD.maxSpeed() - DAGGER.maxSpeed() < AirIntercept.MIN_OVERTAKE_CELLS_PER_SECOND);
        AirIntercept.Plan plan = AirIntercept.plan(60f, 0f, BROADSWORD.maxSpeed(), 0f, BROADSWORD.maxSpeed(), 100f, 0f,
                DAGGER.maxSpeed(), 0f);
        assertEquals(AirIntercept.Stage.MATCHED, plan.stage());
    }
}
