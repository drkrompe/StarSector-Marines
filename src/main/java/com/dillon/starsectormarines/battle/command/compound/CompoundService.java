package com.dillon.starsectormarines.battle.command.compound;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
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
 * [[battle_services_systems]] — state owner, constructor-
 * injected into {@link com.dillon.starsectormarines.battle.sim.BattleSimulation}.
 * The companion stateless tick consumer is {@link CompoundCaptureSystem};
 * the marine garrison system and the trigger/means gates read this service
 * without ever writing back.
 *
 * <p><b>A compound is not necessarily the defender's.</b> The ground the
 * marines came ashore on is a {@code BEACHHEAD} node guarded by
 * {@link Faction#MARINE}, so it registers here like any other place and starts
 * at {@link CompoundState#MARINE_HELD} — the same capture rule then lets a
 * defender counterattack take it back, which is the whole reason it is a
 * compound rather than a scatter of berths. It carries no supply: a beachhead
 * is not a barracks, an armoury or a command post, so
 * {@code BattleResources} produces nothing from it and no reinforcement means
 * gates on it.
 *
 * <p>The same state machine handles capture and recapture:
 * DEFENDER_HELD → CONTESTED → MARINE_HELD, with defender re-entry from
 * MARINE_HELD returning through CONTESTED toward DEFENDER_HELD. A captured
 * compound receives a marine holding garrison while defender reinforcement
 * can drive the recapture path.
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
     * the defender has the home-territory advantage. It handles both a
     * capture that is pushed off before completion and a defender re-entry
     * into a marine-held compound.
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
        public CompoundState state;
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

        /**
         * Resolved capture cell — the walkable cell whose zone <em>is</em> this
         * compound's room. Starts unresolved ({@code -1}) and is filled in by
         * {@link CompoundCaptureSystem} on its first tick.
         *
         * <p>Not simply {@link TacticalNode#anchorX}: a node anchor is the
         * place's stable identity and is explicitly allowed to be a wall,
         * turret mount, or furnished cell, none of which belong to a zone. The
         * capture state machine needs a cell that does.
         */
        public int captureCellX = -1;
        public int captureCellY = -1;

        Record(TacticalNode node) {
            this.node = node;
            // Who would naturally hold this place at battle start, which is
            // what {@link TacticalNode#defaultGuard} has always meant: the
            // beachhead is the attacker's own ground and starts taken, every
            // other compound is the defender's and starts held.
            this.state = Faction.MARINE.friendlyTo(node.defaultGuard)
                    ? CompoundState.MARINE_HELD : CompoundState.DEFENDER_HELD;
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
     * — repeat calls re-seed every record at its node's own default guard.
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
     * Authoritative capture-room zone for {@code record}. The node anchor is
     * stable place identity, not guaranteed standable geometry, so every
     * command, execution, and diagnostic consumer must use this resolver
     * rather than independently reading the anchor's zone.
     *
     * <p>The resolved cell is cached on the record. Breaches can merge zones,
     * so the zone id itself is deliberately read fresh from the live graph.
     */
    public int captureZoneId(Record record, BattleView sim) {
        if (record == null || sim == null) return -1;
        if (record.captureCellX < 0) {
            int[] cell = resolveCaptureCell(record.node, sim.getGrid(),
                    sim.getZoneGraph());
            if (cell == null) return -1;
            record.captureCellX = cell[0];
            record.captureCellY = cell[1];
        }
        return sim.getZoneGraph().zoneIdAt(
                record.captureCellX, record.captureCellY);
    }

    /** Nearest zoned cell to the anchor, bounded to this structure's bbox. */
    private static int[] resolveCaptureCell(TacticalNode node,
                                            NavigationGrid grid,
                                            ZoneGraph zones) {
        int left = Math.max(0, node.left);
        int top = Math.max(0, node.top);
        int right = Math.min(grid.getWidth() - 1, node.right);
        int bottom = Math.min(grid.getHeight() - 1, node.bottom);
        if (left > right || top > bottom) return null;
        int width = right - left + 1;
        boolean[] visited = new boolean[width * (bottom - top + 1)];
        Deque<int[]> queue = new ArrayDeque<>();
        int startX = Math.min(right, Math.max(left, node.anchorX));
        int startY = Math.min(bottom, Math.max(top, node.anchorY));
        queue.add(new int[]{startX, startY});
        visited[(startY - top) * width + (startX - left)] = true;
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            if (zones.zoneIdAt(cell[0], cell[1]) >= 0) return cell;
            for (int[] step : CAPTURE_NEIGHBOURS) {
                int nx = cell[0] + step[0];
                int ny = cell[1] + step[1];
                if (nx < left || nx > right || ny < top || ny > bottom) continue;
                int index = (ny - top) * width + (nx - left);
                if (visited[index]) continue;
                visited[index] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return null;
    }

    private static final int[][] CAPTURE_NEIGHBOURS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    /**
     * Whether {@code faction} is standing on this compound's own ground.
     *
     * <p>Both halves of the test carry weight. The zone answers "is this the
     * room the compound is taken in", which keeps somebody on the far side of
     * a partition from counting. The compound's own footprint answers "is this
     * <em>here</em>", which the zone cannot: a zone is a connected walkable
     * region and nothing about it is bounded by a structure.
     *
     * <p>For a building the two agree — an enclosed compound's room lies
     * wholly inside its footprint — so the footprint clause costs a bounds
     * check and changes no outcome. For an <em>open</em> compound it is the
     * whole answer. An airfield is paved ground with no walls, so its room is
     * the outdoors: measured on a generated ward, one apron of 216 cells
     * resolved to a zone of 1341, nearly half the map's walkable area. Asking
     * the zone alone put the field in permanent CONTESTED from the first
     * marine to set foot outdoors anywhere, and froze it there because the
     * defenders outdoors were equally "present". The same reading would take
     * any building whose wall is breached into the street.
     */
    public static boolean occupiedBy(Record record, int zoneId,
                                     Faction faction, BattleView sim) {
        if (record == null || faction == null || sim == null || zoneId < 0) return false;
        TacticalNode node = record.node;
        ZoneGraph zones = sim.getZoneGraph();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            // Contest is a question about sides, not identities: an allied
            // militia standing in a defender compound contests it exactly as a
            // marine does, and reads as the marines' own presence here. Held is
            // the other question and is answered by identity — see the state
            // enum, which has no allied bucket and is not getting one.
            if (!faction.friendlyTo(sim.identity().faction(unit))) continue;
            int x = sim.world().cellX(unit);
            int y = sim.world().cellY(unit);
            if (x < node.left || x > node.right || y < node.top || y > node.bottom) continue;
            if (zones.zoneIdAt(x, y) == zoneId) return true;
        }
        return false;
    }

    /**
     * True iff at least one compound of {@code kind} is in a state that lets
     * {@code faction} draw supply from it. Defender-side reads "still
     * defender-held or contested" — supply hasn't fully fallen yet, so the
     * trigger/means gate still allows reinforcement. Marine-side reads
     * "marine-held" — the captured-supply ownership state used by the
     * marine resource and future marine delivery policies.
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
            } else if (Faction.MARINE.friendlyTo(faction)) {
                // A compound is only ever held by MARINE or DEFENDER, so an
                // ally asking about supply is asking about the marine side's.
                if (r.state == CompoundState.MARINE_HELD) return true;
            }
        }
        return false;
    }

    /** Compound kinds tracked by this layer. Mirrors {@link com.dillon.starsectormarines.battle.command.reinforcement.GarrisonDepletedTrigger}'s gate so a kind added here without updating that trigger (or vice versa) flags up at code-review time. */
    public static boolean isCompound(TacticalNode.Kind kind) {
        return kind == TacticalNode.Kind.COMMAND_POST
                || kind == TacticalNode.Kind.BARRACKS
                || kind == TacticalNode.Kind.ARMORY
                || kind == TacticalNode.Kind.AIRBASE
                || kind == TacticalNode.Kind.BEACHHEAD;
    }
}
