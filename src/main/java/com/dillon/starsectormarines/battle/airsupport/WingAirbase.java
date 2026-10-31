package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.air.AirfieldService.Berth;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An air wing's airbase on the map (`air-nouns.md`, Air support pool): the field whose berths hold part of a side's
 * pool on the ground, so the pool's sorties roll off its strip and land back on it instead of crossing the map edge.
 *
 * <p>The pool stays the ledger: how many airframes of each kind the side holds, and which are out. The base says which
 * of them are standing here. A call is flown off the base when a berth of its kind is airworthy and the base is not
 * overrun, and from the side's own edge otherwise, as a wing's squadrons further back fly. Each berth holds one kind
 * ({@link AirPoolKind}): a sortie leaves on the airframe the berth holds, the one the call asked for when one of those
 * is ready.
 *
 * <p>The base is a real place and losing it is a cost, never a loss. An aircraft burned where it stands is written off
 * the pool ({@link #newlyBurned}) and its stand keeps the hulk; one lost in the air leaves an empty stand that the
 * pool's next replacement of that kind is ferried onto ({@link #restock}). Nothing launches while the side's enemies
 * stand on the base ({@link #overrun}).
 */
public final class WingAirbase {

    /** Cells past the base's bounds within which an enemy still counts as on it. */
    static final float OVERRUN_MARGIN = 4f;

    private final AirfieldService field;
    private final int[] bounds;
    private final List<Berth> berths;
    private final Map<Berth, AirPoolKind> kinds = new IdentityHashMap<>();
    private final Set<Berth> burned = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * The base over {@code field}, inside {@code bounds} ({x0, y0, x1, y1}, inclusive), with {@code berths}, each
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

    /** Whose base it is: the field's owner. */
    public Faction side() {
        return field.owner();
    }

    public AirfieldService field() {
        return field;
    }

    /** The base's bounds, {x0, y0, x1, y1} inclusive. */
    public int[] bounds() {
        return bounds.clone();
    }

    /** Every berth of the base, in registration order. */
    public List<Berth> berths() {
        return berths;
    }

    /** The kind the berth holds, or null for a berth not of this base. */
    public AirPoolKind kindOf(Berth berth) {
        return kinds.get(berth);
    }

    /**
     * The berth a sortie of {@code kind} rolls from: an airworthy one holding {@code wanted} when there is one, else any
     * airworthy one of the kind, nearest {@code (x, y)} either way; null when none is ready or the base has no strip.
     * Every berth of the base reaches the air down its strip, a stand's aircraft taxiing to it as a shed's does.
     */
    public Berth berthFor(AirPoolKind kind, FighterProfile wanted, float x, float y) {
        if (field.runway() == null) return null;
        Berth exact = null, any = null;
        float exactD = Float.MAX_VALUE, anyD = Float.MAX_VALUE;
        for (Berth berth : berths) {
            if (!berth.airworthy() || kinds.get(berth) != kind || !(berth.airframe instanceof FighterProfile)) continue;
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

    /**
     * Ferries a replacement of {@code kind} onto the first stand of that kind left empty by a loss in the air; answers
     * whether one was. A stand with a hulk on it takes none.
     */
    public boolean restock(AirPoolKind kind) {
        for (Berth berth : berths) {
            if (kinds.get(berth) == kind && field.restock(berth)) return true;
        }
        return false;
    }

    /** The kinds of the aircraft burned on their stands since the last call, one entry each. */
    public List<AirPoolKind> newlyBurned() {
        List<AirPoolKind> out = new ArrayList<>();
        for (Berth berth : berths) {
            if (berth.wreckOnPad && burned.add(berth)) out.add(kinds.get(berth));
        }
        return out;
    }
}
