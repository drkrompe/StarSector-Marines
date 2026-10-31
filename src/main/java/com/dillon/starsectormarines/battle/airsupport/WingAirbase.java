package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.air.AirfieldService.Berth;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One airbase a side's air pool flies from (`air-nouns.md`, Airbase): the bays of one field on the map, each holding
 * one kind of the pool's airframes ({@link AirPoolKind}), and the ground the base stands on.
 *
 * <p>For a based side the bays are the pool's ledger ({@link AirPoolService}): what it has available is what stands
 * airworthy in them. A call rolls off the nearest base's bay of its kind, on the airframe the call asked for when one
 * of those is ready, down that base's own strip, and lands back on it. Nothing launches from a base while the side's
 * enemies stand on it ({@link #overrun}).
 *
 * <p>The base is a real place and losing it is a cost, never a loss. An aircraft lost in the air leaves its bay empty
 * and the field's crew builds a replacement on it, which takes their work ({@code AirfieldService.beginRebuild}); one
 * burned where it stands leaves its hulk and the bay is gone for the battle ({@link #newlyBurned}).
 */
public final class WingAirbase implements AirBase {

    /** Cells past the base's bounds within which an enemy still counts as on it. */
    static final float OVERRUN_MARGIN = 4f;

    private final AirfieldService field;
    private final int[] bounds;
    private final List<Berth> berths;
    private final Map<Berth, AirPoolKind> kinds = new IdentityHashMap<>();
    private final Set<Berth> burned = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * The base of {@code berths} on {@code field}, inside {@code bounds} ({x0, y0, x1, y1}, inclusive), each berth
     * holding an airframe of the kind at the same index of {@code kinds}.
     */
    public WingAirbase(AirfieldService field, int[] bounds, List<Berth> berths, List<AirPoolKind> kinds) {
        if (berths.size() != kinds.size()) throw new IllegalArgumentException("a kind for every berth");
        this.field = field;
        this.bounds = bounds.clone();
        this.berths = List.copyOf(berths);
        for (int i = 0; i < berths.size(); i++) this.kinds.put(berths.get(i), kinds.get(i));
    }

    /** The kind of pool airframe {@code airframe} is: a bomber's, or else a fighter's. */
    public static AirPoolKind kindOf(FighterProfile airframe) {
        return airframe == AirPoolKind.BOMBER.profile() ? AirPoolKind.BOMBER : AirPoolKind.FIGHTER;
    }

    /** Whose base it is: its bays' field's owner. */
    public Faction side() {
        return berths.isEmpty() ? field.owner() : field.ownerOf(berths.get(0));
    }

    public AirfieldService field() {
        return field;
    }

    /** The base's bounds, {x0, y0, x1, y1} inclusive. */
    @Override
    public int[] bounds() {
        return bounds.clone();
    }

    /** Every bay of the base, in registration order. */
    public List<Berth> berths() {
        return berths;
    }

    /** The kind the bay holds, or null for a berth not of this base. */
    public AirPoolKind kindOf(Berth berth) {
        return kinds.get(berth);
    }

    /** Bays of {@code kind} that could put an aircraft in the air now. */
    public int airworthy(AirPoolKind kind) {
        int n = 0;
        for (Berth berth : berths) if (berth.airworthy() && kinds.get(berth) == kind) n++;
        return n;
    }

    /**
     * The bay a sortie of {@code kind} rolls from: an airworthy one holding {@code wanted} when there is one, else any
     * airworthy one of the kind, nearest {@code (x, y)} either way; null when none is ready. Only a bay whose field has
     * a strip: every bay reaches the air down its own field's strip, a stand's aircraft taxiing to it as a shed's does.
     */
    public Berth berthFor(AirPoolKind kind, FighterProfile wanted, float x, float y) {
        Berth exact = null, any = null;
        float exactD = Float.MAX_VALUE, anyD = Float.MAX_VALUE;
        for (Berth berth : berths) {
            if (!berth.airworthy() || kinds.get(berth) != kind || !(berth.airframe instanceof FighterProfile)) continue;
            if (field.stripOf(berth) == null) continue;
            float d = (berth.centerX - x) * (berth.centerX - x) + (berth.centerY - y) * (berth.centerY - y);
            if (d < anyD) {
                anyD = d;
                any = berth;
            }
            if (berth.airframe == wanted && d < exactD) {
                exactD = d;
                exact = berth;
            }
        }
        return exact != null ? exact : any;
    }

    @Override
    public int ready(AirPoolKind kind) {
        return airworthy(kind);
    }

    @Override
    public float reach(BattleSimulation sim, AirPoolKind kind, FighterProfile wanted, float x, float y) {
        Berth b = berthFor(kind, wanted, x, y);
        if (b == null || overrun(sim)) return Float.MAX_VALUE;
        return (b.centerX - x) * (b.centerX - x) + (b.centerY - y) * (b.centerY - y);
    }

    @Override
    public FighterProfile airframeFor(AirPoolKind kind, FighterProfile wanted, float x, float y) {
        Berth b = berthFor(kind, wanted, x, y);
        return b == null ? wanted : (FighterProfile) b.airframe;
    }

    /**
     * Puts the bay's aircraft in the air for a call at {@code (x, y)}: the hull that was on the stand, damage and all,
     * taxiing out to the strip and owed back to that stand, as a garrison's shed sortie is ({@code AirStrikeSystem}).
     * Nothing goes while the enemy stands on the base.
     */
    @Override
    public long launch(BattleSimulation sim, AirPoolKind kind, FighterProfile airframe, float x, float y) {
        if (overrun(sim)) return 0L;
        Berth berth = berthFor(kind, airframe, x, y);
        if (berth == null) return 0L;
        FighterProfile flown = (FighterProfile) berth.airframe;
        Runway strip = field.stripOf(berth);
        float standX = berth.centerX + 0.5f;
        float standY = berth.centerY + 0.5f;
        long craft = sim.spawnSortie(flown, side(), x, y, standX, standY, standX, standY, 0f);
        ShuttleMission mission = sim.world().mission(craft);
        sim.world().setHp(craft, field.launch(berth));
        mission.homeBerth = berth;
        sim.world().kinematics(craft).teleport(standX, standY, berth.facingDegrees);
        mission.departFromRunway(strip, standX, standY, x, y);
        return craft;
    }

    /** A bay's loss is made good by the field's crew rebuilding it, never on a clock. */
    @Override
    public float nextReplacementSeconds(AirPoolKind kind) {
        return -1f;
    }

    /** Whether a live combatant hostile to the base's side stands on it, within {@link #OVERRUN_MARGIN} of its bounds. */
    public boolean overrun(BattleSimulation sim) {
        Faction side = side();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.identity().type(unit).combatant || !side.hostileTo(sim.identity().faction(unit))) continue;
            if (within(sim.world().x(unit), sim.world().y(unit))) return true;
        }
        return false;
    }

    /** Whether {@code (x, y)} is on the base, its margin included. */
    boolean within(float x, float y) {
        return x >= bounds[0] - OVERRUN_MARGIN && x <= bounds[2] + 1 + OVERRUN_MARGIN
                && y >= bounds[1] - OVERRUN_MARGIN && y <= bounds[3] + 1 + OVERRUN_MARGIN;
    }

    /** The kinds of the aircraft burned on their bays since the last call, one entry each. */
    @Override
    public List<AirPoolKind> newlyBurned() {
        List<AirPoolKind> out = new ArrayList<>();
        for (Berth berth : berths) {
            if (berth.wreckOnPad && burned.add(berth)) out.add(kinds.get(berth));
        }
        return out;
    }
}
