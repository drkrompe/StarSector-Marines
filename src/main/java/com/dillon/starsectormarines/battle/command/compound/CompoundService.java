package com.dillon.starsectormarines.battle.command.compound;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.tactical.TacticalMap;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stateful registry for the compound-as-supply layer. Holds one
 * {@link Record} per defender compound ({@code COMMAND_POST} /
 * {@code BARRACKS} / {@code ARMORY}) tactical node, tracking its current
 * {@link CompoundState} and the hold-time / capture-progress accumulators
 * that the state machine in {@link CompoundCaptureSystem} drives.
 *
 * <p>Naming follows the {@code *Service} convention per
 * {@code memory/battle_services_systems.md} — state owner, constructor-
 * injected into {@link com.dillon.starsectormarines.battle.sim.BattleSimulation}.
 * The companion stateless tick consumer is {@link CompoundCaptureSystem};
 * v2's auto-garrison shuttle trigger and the slice-3 trigger/means gates
 * read this service without ever writing back.
 *
 * <p>V1 ships the forward transitions only (DEFENDER_HELD → CONTESTED →
 * MARINE_HELD). The reverse path is wired in
 * {@link CompoundCaptureSystem} so v2 lights up without re-architecting,
 * but no v1 production driver targets a marine-held compound so MARINE_HELD
 * is effectively absorbing in v1.
 */
public final class CompoundService {

    /**
     * Per-compound capture state. The state machine driving these flips
     * lives in {@link CompoundCaptureSystem} so a unit test can walk a
     * compound through transitions without standing up the whole sim
     * tick loop.
     */
    public enum CompoundState {
        /** Defenders occupy the zone (or it's empty in defender territory); supply means tied to this compound stays active. */
        DEFENDER_HELD,
        /** Mixed presence or transition in progress. Supply still active — the compound hasn't fully flipped. */
        CONTESTED,
        /** Marines hold the zone with no defenders for {@link #MARINE_HOLD_TIME}; supply tied to this compound is dead. */
        MARINE_HELD
    }

    /**
     * Sim-seconds marines must hold a contested zone (marines present, zero
     * defenders) before it flips to {@link CompoundState#MARINE_HELD}. Slow on
     * purpose — marines have to <em>commit</em> to the capture; a fleeting
     * walkthrough doesn't take a compound. Tuned in playtest; the asymmetry
     * with {@link #DEFENDER_HOLD_TIME} reflects the home-territory advantage.
     */
    public static final float MARINE_HOLD_TIME = 4.0f;

    /**
     * Sim-seconds defenders alone in a contested zone need to push it back to
     * {@link CompoundState#DEFENDER_HELD}. Faster than {@link #MARINE_HOLD_TIME}:
     * the defender has the home-territory advantage. Fires in v1 as the natural
     * "marines started the capture but got pushed off" recovery; v2's
     * AutoGarrisonTrigger drives the symmetric reverse from MARINE_HELD.
     */
    public static final float DEFENDER_HOLD_TIME = 1.5f;

    /**
     * Mutable per-compound record. Owned by the service; written only by
     * {@link CompoundCaptureSystem}. Public fields kept primitive so a future
     * SoA migration is a refactor over the records map, not a redesign of
     * the read sites.
     */
    public static final class Record {
        public final TacticalNode node;
        public CompoundState state = CompoundState.DEFENDER_HELD;
        /**
         * Accumulator toward the active state's threshold. Reset on
         * transition; reset to 0 when accumulation conditions break
         * (defenders push back into a marine-contesting zone, or marines
         * push back into a defender-contesting zone). Frozen at its current
         * value when both factions are present — a mid-firefight pause that
         * resumes when one side wipes.
         */
        public float holdTimer;
        /**
         * Capture progress in [0, 1] — {@link #holdTimer} normalized to
         * whichever hold-time threshold applies given {@link #state}.
         * Drives the world-anchored capture-arc marker the player reads in
         * slice 2.
         */
        public float captureProgress;

        Record(TacticalNode node) {
            this.node = node;
        }
    }

    /**
     * Registration order preserved so the renderer + HUD progress strip
     * iterate compounds in the same order across frames — avoids visual
     * jitter when a state flips.
     */
    private final Map<TacticalNode, Record> records = new LinkedHashMap<>();

    /**
     * Populate per-compound state from a {@link TacticalMap}'s COMMAND_POST /
     * BARRACKS / ARMORY nodes. Called once from
     * {@link com.dillon.starsectormarines.battle.sim.BattleSimulation#setTacticalMap}
     * so the service is ready before the first capture-system tick. Idempotent
     * — repeat calls re-seed every record at DEFENDER_HELD.
     */
    public void initFrom(TacticalMap map) {
        records.clear();
        if (map == null) return;
        for (TacticalNode node : map.all()) {
            if (isCompound(node.kind)) records.put(node, new Record(node));
        }
    }

    /** Manual registration for tests + future map shapes that surface compounds outside the TacticalMap. */
    public Record register(TacticalNode node) {
        if (!isCompound(node.kind)) {
            throw new IllegalArgumentException("not a compound kind: " + node.kind);
        }
        Record r = new Record(node);
        records.put(node, r);
        return r;
    }

    /** All compound records, in registration order. Read by {@link CompoundCaptureSystem} (write side), the slice-2 renderer, the slice-3 trigger/means gates, and the slice-4 win-condition objective. */
    public Collection<Record> getRecords() {
        return Collections.unmodifiableCollection(records.values());
    }

    public Record getRecord(TacticalNode node) {
        return records.get(node);
    }

    /**
     * True iff at least one compound of {@code kind} is in a state that lets
     * {@code faction} draw supply from it. Defender-side reads "still
     * defender-held or contested" — supply hasn't fully fallen yet, so the
     * trigger/means gate still allows reinforcement. Marine-side reads
     * "marine-held" — the captured-supply-line story for v2.
     *
     * <p>Slice 3's trigger/means {@code canFulfill} gates and slice 4's
     * win-condition objective are the consumers. Defined here in slice 1
     * so the read shape is stable before those land.
     */
    public boolean hasAliveCompound(TacticalNode.Kind kind, Faction faction) {
        for (Record r : records.values()) {
            if (r.node.kind != kind) continue;
            if (faction == Faction.DEFENDER) {
                if (r.state != CompoundState.MARINE_HELD) return true;
            } else if (faction == Faction.MARINE) {
                if (r.state == CompoundState.MARINE_HELD) return true;
            }
        }
        return false;
    }

    /** Compound kinds tracked by this layer. Mirrors {@link com.dillon.starsectormarines.battle.command.reinforcement.GarrisonDepletedTrigger}'s gate so a kind added here without updating that trigger (or vice versa) flags up at code-review time. */
    public static boolean isCompound(TacticalNode.Kind kind) {
        return kind == TacticalNode.Kind.COMMAND_POST
                || kind == TacticalNode.Kind.BARRACKS
                || kind == TacticalNode.Kind.ARMORY;
    }
}
