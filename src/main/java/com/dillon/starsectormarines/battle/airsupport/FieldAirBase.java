package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.Runway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A side's air pool on an airfield of its own on the battle map ({@link AirBase}): one berth for each airframe the
 * pool's establishment holds, registered on the battle's {@link AirfieldService} (an airbase lot's apron stands and
 * sheds, and its strip), so the aircraft standing on them are the field's ordinary parked units, drawn, seen and
 * shootable, and every sortie is the field's ordinary based sortie (off its berth, handed back to it by every ending the
 * air system has).
 *
 * <p><b>The cycle.</b> A berth is ready while its aircraft is parked and turned round. A sortie off an apron stand lifts
 * straight off it and flies home to it; one kept in a shed taxis out, rolls down the strip, and lands on it coming home
 * and taxis back in. Home, the aircraft stands on its berth to be rearmed, refuelled and patched, which is the field's
 * turnaround law ({@link AirfieldService#recover}: a servicing every time, plus the patching its damage owes), worked off
 * by the field's ground crew: real technicians where the host posted them, else {@link #ABSTRACT_HANDS} a berth. An
 * aircraft shot down away from its berth is replaced on that berth after the pool's declared regeneration time for its
 * kind; one burned where it stood leaves its hulk and the berth is out for the battle.
 */
public final class FieldAirBase implements AirBase {

    /** Hands at work on each berth's turnaround where no crew stands on the field: about twenty-five seconds. UNTUNED. */
    public static final float ABSTRACT_HANDS = 1f;

    /** One berth, the kind it keeps, and the clock on a replacement for an aircraft it lost. */
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
        /** Whether this berth is a shed whose aircraft taxis out and rolls, rather than an apron stand it lifts off. */
        public boolean shed() { return berth.kind == AirfieldService.Kind.SHELTER; }
        /** Seconds until this berth's replacement arrives, or negative when none is owed. */
        public float replaceIn() { return replaceIn; }
    }

    private final Faction side;
    private final AirfieldService field;
    private final AirPoolAllowance allowance;
    private final List<Stand> stands = new ArrayList<>();
    private float hands = ABSTRACT_HANDS;

    /**
     * A field for {@code side} on {@code field}, replacing losses at {@code allowance}'s regeneration time for each
     * kind. Hands the airfield to {@code side}, so call it before anything stands on it.
     */
    public FieldAirBase(Faction side, AirfieldService field, AirPoolAllowance allowance) {
        this.side = side;
        this.field = field;
        this.allowance = allowance;
        field.setOwner(side);
    }

    /** Keeps an airframe of {@code kind} on apron stand {@code pad}, its nose on {@code facingDegrees}. */
    public Stand addPad(LandingPad pad, AirPoolKind kind, float facingDegrees) {
        Stand stand = new Stand(field.addBerth(pad, kind.profile(), facingDegrees), kind);
        stands.add(stand);
        return stand;
    }

    /** Lays an apron stand at {@code (x, y)} keeping an airframe of {@code kind}: a stand on open ground. */
    public Stand addStand(int x, int y, AirPoolKind kind, float facingDegrees) {
        return addPad(LandingPad.garrison(x, y, LandingPad.Approach.NORTH), kind, facingDegrees);
    }

    /** Keeps an airframe of {@code kind} in shed {@code shed}: it flies only once the field has a strip. */
    public Stand addShed(Gantry shed, AirPoolKind kind) {
        Stand stand = new Stand(field.addShelterBerth(shed, kind.profile()), kind);
        stands.add(stand);
        return stand;
    }

    /** Gives the field its strip, which its sheds' aircraft roll down. */
    public void installRunway(Runway strip) {
        field.installRunway(strip);
    }

    /** Whether real technicians work the field's turnarounds; else the abstract hands do. */
    public void crewed(boolean crewed) {
        hands = crewed ? 0f : ABSTRACT_HANDS;
    }

    public Faction side() { return side; }

    public List<Stand> stands() { return Collections.unmodifiableList(stands); }

    @Override
    public int ready(AirPoolKind kind) {
        int n = 0;
        for (Stand s : stands) if (s.kind == kind && usable(s)) n++;
        return n;
    }

    /** Whether the berth's aircraft could go now: parked and turned round, and a shed's only with a strip to roll on. */
    private boolean usable(Stand s) {
        return s.berth.airworthy() && (!s.shed() || field.runway() != null);
    }

    @Override
    public float reach(BattleSimulation sim, AirPoolKind kind, FighterProfile wanted, float x, float y) {
        Stand s = nearestReady(kind, x, y);
        if (s == null) return Float.MAX_VALUE;
        float dx = s.berth.centerX - x, dy = s.berth.centerY - y;
        return dx * dx + dy * dy;
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
        float homeX = stand.berth.centerX + 0.5f, homeY = stand.berth.centerY + 0.5f;
        long craft = sim.spawnSortie(airframe, side, x, y, homeX, homeY, homeX, homeY, 0f);
        // The hull that leaves is the hull on the berth, and it owes itself back to that berth.
        sim.world().setHp(craft, field.launch(stand.berth));
        ShuttleMission mission = sim.world().mission(craft);
        mission.homeBerth = stand.berth;
        sim.world().kinematics(craft).teleport(homeX, homeY, stand.berth.facingDegrees);
        if (stand.shed()) {
            // Out of the shed, down the taxiway and off the strip, as a garrison's fighters go.
            mission.departFromRunway(field.runway(), homeX, homeY, x, y);
        } else {
            sim.world().setAltitudeT(craft, 0f);
            mission.padPhaseElapsed = 0f;
            mission.state = ShuttleState.PAD_ASCENT;
        }
        return craft;
    }

    /** The ready berth of {@code kind} nearest {@code (x, y)}, or {@code null}. */
    Stand nearestReady(AirPoolKind kind, float x, float y) {
        Stand best = null;
        float bestD = Float.MAX_VALUE;
        for (Stand s : stands) {
            if (s.kind != kind || !usable(s)) continue;
            float dx = s.berth.centerX - x, dy = s.berth.centerY - y;
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /** One tick of the field: the abstract hands at work where no crew stands, and the clocks on its replacements. */
    public void advance(BattleSimulation sim, float dt) {
        if (hands > 0f) {
            for (Stand s : stands) {
                long parked = s.berth.airframeId;
                if (s.berth.state == AirfieldService.BerthState.REFITTING && parked != 0L
                        && sim.world().isAlive(parked)) {
                    float mended = Math.min(sim.world().maxHp(parked),
                            sim.world().hp(parked) + dt * hands * AirfieldService.HULL_PER_HAND_SECOND);
                    sim.world().setHp(parked, mended);
                }
            }
        }
        advanceStands(dt);
    }

    /**
     * The part of a tick that reads only the berths: turnaround work worked off by the abstract hands, and a lost
     * aircraft replaced on its berth once its kind's regeneration time has run. A berth with a hulk on it is never
     * replaced.
     */
    void advanceStands(float dt) {
        for (Stand s : stands) {
            AirfieldService.Berth b = s.berth;
            if (b.state == AirfieldService.BerthState.REFITTING && hands > 0f) {
                b.refitWork = Math.max(0f, b.refitWork - dt * hands);
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
                // A new airframe on the berth, whole and ready; the field puts it out on its next tick.
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
