package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A side's air pool on hardstands of its own on the battle map ({@link AirBase}): one stand for each airframe the pool's
 * establishment holds, registered as berths on the battle's {@link AirfieldService}, so the aircraft standing on them
 * are the field's ordinary parked units, drawn, seen and shootable, and every sortie is the field's ordinary based sortie
 * (off its berth, handed back to it by every ending the air system has).
 *
 * <p><b>The cycle.</b> A stand is ready while its aircraft is parked and turned round. A sortie lifts off it straight up
 * and flies home to it; the aircraft comes back onto the stand to be rearmed, refuelled and patched, which is the
 * field's turnaround law ({@link AirfieldService#recover}: a servicing every time, plus the patching its damage owes),
 * worked off by the stand's ground crew at {@link #GROUND_CREW_HANDS}. An aircraft shot down away from its stand is
 * replaced on that stand after the pool's declared regeneration time for its kind; one burned where it stood leaves its
 * hulk on the concrete and the stand is out for the battle.
 *
 * <p>The ground crew is abstract: hands a stand has, not technicians walking the apron. UNTUNED.
 */
public final class HardstandAirBase implements AirBase {

    /** Hands at work on each stand's turnaround: about twenty-five seconds for an undamaged airframe. UNTUNED. */
    public static final float GROUND_CREW_HANDS = 1f;

    /** One hardstand, the kind it keeps, and the clock on a replacement for an aircraft it lost. */
    public static final class Stand {
        private final AirfieldService.Berth berth;
        private final AirPoolKind kind;
        /** Seconds until a lost aircraft is replaced, or negative while none is owed. */
        private float replaceIn = -1f;

        Stand(AirfieldService.Berth berth, AirPoolKind kind) {
            this.berth = berth;
            this.kind = kind;
        }

        public AirfieldService.Berth berth() { return berth; }
        public AirPoolKind kind() { return kind; }
        public int x() { return berth.centerX; }
        public int y() { return berth.centerY; }
        /** Seconds until this stand's replacement arrives, or negative when none is owed. */
        public float replaceIn() { return replaceIn; }
    }

    private final Faction side;
    private final AirfieldService field;
    private final AirPoolAllowance allowance;
    private final List<Stand> stands = new ArrayList<>();

    /**
     * A field for {@code side} on {@code field}, replacing losses at {@code allowance}'s regeneration time for each
     * kind. Hands the airfield to {@code side}, so call it before anything stands on it.
     */
    public HardstandAirBase(Faction side, AirfieldService field, AirPoolAllowance allowance) {
        this.side = side;
        this.field = field;
        this.allowance = allowance;
        field.setOwner(side);
    }

    /** Lays one stand at {@code (x, y)} keeping an airframe of {@code kind}, its nose on {@code facingDegrees}. */
    public Stand addStand(int x, int y, AirPoolKind kind, float facingDegrees) {
        LandingPad pad = LandingPad.garrison(x, y, LandingPad.Approach.NORTH);
        Stand stand = new Stand(field.addBerth(pad, kind.profile(), facingDegrees), kind);
        stands.add(stand);
        return stand;
    }

    public Faction side() { return side; }

    public List<Stand> stands() { return Collections.unmodifiableList(stands); }

    @Override
    public int ready(AirPoolKind kind) {
        int n = 0;
        for (Stand s : stands) if (s.kind == kind && s.berth.airworthy()) n++;
        return n;
    }

    @Override
    public float nextReplacementSeconds(AirPoolKind kind) {
        float next = -1f;
        for (Stand s : stands) {
            if (s.kind != kind || s.replaceIn < 0f) continue;
            if (next < 0f || s.replaceIn < next) next = s.replaceIn;
        }
        return next;
    }

    @Override
    public long launch(BattleSimulation sim, AirPoolKind kind, FighterProfile airframe, float x, float y) {
        Stand stand = nearestReady(kind, x, y);
        if (stand == null) return 0L;
        float padX = stand.berth.centerX + 0.5f, padY = stand.berth.centerY + 0.5f;
        long craft = sim.spawnSortie(airframe, side, x, y, padX, padY, padX, padY, 0f);
        // The hull that leaves is the hull on the stand, and it owes itself back to that stand.
        sim.world().setHp(craft, field.launch(stand.berth));
        ShuttleMission mission = sim.world().mission(craft);
        mission.homeBerth = stand.berth;
        sim.world().kinematics(craft).teleport(padX, padY, stand.berth.facingDegrees);
        sim.world().setAltitudeT(craft, 0f);
        mission.padPhaseElapsed = 0f;
        mission.state = ShuttleState.PAD_ASCENT;
        return craft;
    }

    /** The ready stand of {@code kind} nearest {@code (x, y)}, or {@code null}. */
    Stand nearestReady(AirPoolKind kind, float x, float y) {
        Stand best = null;
        float bestD = Float.MAX_VALUE;
        for (Stand s : stands) {
            if (s.kind != kind || !s.berth.airworthy()) continue;
            float dx = s.berth.centerX - x, dy = s.berth.centerY - y;
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /** One tick of the field: the ground crew at work on what stands on it, and the clocks on its replacements. */
    public void advance(BattleSimulation sim, float dt) {
        for (Stand s : stands) {
            long parked = s.berth.airframeId;
            if (s.berth.state == AirfieldService.BerthState.REFITTING && parked != 0L && sim.world().isAlive(parked)) {
                float mended = Math.min(sim.world().maxHp(parked),
                        sim.world().hp(parked) + dt * GROUND_CREW_HANDS * AirfieldService.HULL_PER_HAND_SECOND);
                sim.world().setHp(parked, mended);
            }
        }
        advanceStands(dt);
    }

    /**
     * The part of a tick that reads only the stands: turnaround work worked off, and a lost aircraft replaced on its
     * stand once its kind's regeneration time has run. A stand with a hulk on it is never replaced.
     */
    void advanceStands(float dt) {
        for (Stand s : stands) {
            AirfieldService.Berth b = s.berth;
            if (b.state == AirfieldService.BerthState.REFITTING) {
                b.refitWork = Math.max(0f, b.refitWork - dt * GROUND_CREW_HANDS);
            }
            if (b.state != AirfieldService.BerthState.DESTROYED || b.wreckOnPad) {
                s.replaceIn = -1f;
                continue;
            }
            float regen = allowance.stock(s.kind).regenSeconds();
            if (regen <= 0f) continue;
            if (s.replaceIn < 0f) s.replaceIn = regen;
            s.replaceIn -= dt;
            if (s.replaceIn <= 0f) {
                // A new airframe on the stand, whole and ready; the field puts it out on its next tick.
                b.state = AirfieldService.BerthState.PARKED;
                b.hullHp = b.airframe.maxHp();
                b.refitWork = 0f;
                b.refitTarget = 0f;
                b.airframeId = 0L;
                s.replaceIn = -1f;
            }
        }
    }
}
