package com.dillon.starsectormarines.battle.tactical;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured tactical hint produced during map generation and consumed by the
 * battle AI. Distinct from {@link com.dillon.starsectormarines.battle.world.model.PointOfInterest}:
 * a POI answers <em>"what is the mission objective"</em> (extract that
 * scientist, blow that depot); a {@link TacticalNode} answers <em>"where would
 * a sane squad position itself"</em> — turret mounts, gates, barracks, fallback
 * positions. The two lists serve different consumers (mission scripts vs.
 * battle AI) and intentionally don't overlap.
 *
 * <h2>Identity and equality</h2>
 * Each node has reference identity — equality is by object reference, not by
 * position or kind. Two different gates at the same cell are different nodes.
 * The {@link Link} graph relies on this for clean cycle detection.
 *
 * <h2>Mutability</h2>
 * Core fields (kind, position, faction, priority, garrison) are final after
 * construction. The {@link #links} list is mutated only during the link-build
 * pass that runs after all nodes are emitted ({@link TacticalLinker}). Once
 * a {@link TacticalMap} is constructed and exposed, treat the graph as frozen.
 *
 * <h2>Default-guard semantics</h2>
 * {@link #defaultGuard} declares which faction would naturally hold this
 * position. {@link Faction#DEFENDER} for towers/gates/barracks;
 * {@link Faction#MARINE} for beachheads. The AI uses this as a hint —
 * possession can flip during play (a captured forward bunker becomes the
 * attacker's cover position), but defaultGuard tells the squad allocator
 * "this is what side wants to start on this."
 */
public final class TacticalNode {

    /**
     * Tactical role categories. Each category implies a default
     * {@link Faction} guard, a priority score, and a garrison size — the
     * static factory methods in {@link #of(Kind, int, int, int, int, int, int)}
     * apply per-kind defaults.
     */
    public enum Kind {
        // Defender-leaning — emitted by FortressWallStamper + MilitaryBaseFiller.
        /** 3×3 wall tower with mounted heavy turret. Holds firing arc over a wide front. */
        HEAVY_TOWER,
        /** Single-cell crenellation MG mount on the wall line. Suppresses approach. */
        MG_NEST,
        /** Free-standing 3×3 tower in the kill-zone buffer ahead of the wall. */
        FORWARD_BUNKER,
        /** Wall gap, defender-defended from inside. Chokepoint. */
        GATE,
        /** MILITARY_BASE compound's command leaf. Highest-value hold-point. */
        COMMAND_POST,
        /** MILITARY_BASE compound's barracks leaf. Reinforcement supply source. */
        BARRACKS,
        /** MILITARY_BASE compound's armory leaf. Resupply / loadout swap point. */
        ARMORY,
        /** Defender airbase — LANDING_ZONE in fortress district. Reserved for v2. */
        AIRBASE,
        /** Manned turret emplacement scattered through BEACH/PORT/kill-zone biomes. Anchored by {@link com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper}; squad stays near the post until its turrets are destroyed, then releases to search-and-destroy. */
        GUARDPOST,
        /**
         * Tactical anchor for a garrison position that lives <em>inside</em> a parent compound's
         * footprint without itself constituting a separate compound. The defender allocator picks
         * it up via the same pass that handles every other kind, but the compound-as-supply layer
         * ({@link com.dillon.starsectormarines.battle.command.compound.CompoundService}) ignores it: no
         * record, no capture marker, no supply gate. {@link TacticalLinker} also skips it for the
         * compound-leaf FALLBACK_TO pass (interior fallback is goal-AI territory, not the link
         * graph). Emitted by {@link com.dillon.starsectormarines.battle.world.gen.bsp.KeepEntryChamberStamper}
         * for keep antechambers; reusable for any future "interior tactical position" need
         * (warehouse interior, port admin, dockmaster office) without re-inventing the abstraction.
         */
        INNER_POSITION,

        // Attacker-leaning / neutral — emission deferred to v2.
        /** Marine landing zone — attacker spawn area. */
        BEACHHEAD,
        /** Non-gate entry — pier, sewer, wall breach. */
        INFILTRATION_POINT,
        /** Generic mission objective — varies per mission. */
        OBJECTIVE
    }

    /**
     * Typed directed relationship between two nodes. The source owns the
     * outgoing link; the target is referenced. There is no "reverse link" —
     * if a backlink is meaningful, emit it explicitly as a separate
     * {@link Link} on the other node.
     */
    public enum LinkKind {
        /** Source has firing arc over target. Tower → gate is the canonical case. */
        OVERWATCHES,
        /** Source supplies reinforcements / ammo to target. Barracks → towers. */
        SUPPLIES,
        /** If source falls, defenders fall back to target. Gate → bunker → tower. */
        FALLBACK_TO,
        /** Source provides forward defense for target. Forward bunker → gate. */
        GUARDS
    }

    /** Directed edge owned by the source {@link TacticalNode}. */
    public static final class Link {
        public final LinkKind kind;
        public final TacticalNode target;

        public Link(LinkKind kind, TacticalNode target) {
            this.kind = kind;
            this.target = target;
        }
    }

    public final Kind kind;
    /** Single-cell anchor — turret mount for towers, gate-center for gates, doorway for compounds. AI uses this for "where to stand". */
    public final int anchorX;
    public final int anchorY;
    /** Bounding box. For single-cell nodes (MG), all four equal anchor. */
    public final int left, top, right, bottom;
    /** Faction that would naturally hold this node at battle start. */
    public final Faction defaultGuard;
    /** 0-100. Higher = more important to defend/take. Drives greedy squad allocation order. */
    public final int priorityScore;
    /** Suggested squad slot count at this node. Fixed per-kind for v1; future {@code requestedShare} ratio can supersede. */
    public final int garrisonSize;

    private final List<Link> links = new ArrayList<>();

    public TacticalNode(Kind kind, int anchorX, int anchorY,
                        int left, int top, int right, int bottom,
                        Faction defaultGuard, int priorityScore, int garrisonSize) {
        this.kind = kind;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.defaultGuard = defaultGuard;
        this.priorityScore = priorityScore;
        this.garrisonSize = garrisonSize;
    }

    public List<Link> links() {
        return Collections.unmodifiableList(links);
    }

    /** All targets linked via the given relationship kind. Empty if none. */
    public List<TacticalNode> linkedTo(LinkKind linkKind) {
        List<TacticalNode> out = new ArrayList<>();
        for (Link l : links) {
            if (l.kind == linkKind) out.add(l.target);
        }
        return out;
    }

    /** Package-private: called by {@link TacticalLinker} during the link-build pass. */
    void addLink(LinkKind linkKind, TacticalNode target) {
        links.add(new Link(linkKind, target));
    }

    public int centerX() { return (left + right) / 2; }
    public int centerY() { return (top + bottom) / 2; }
}
