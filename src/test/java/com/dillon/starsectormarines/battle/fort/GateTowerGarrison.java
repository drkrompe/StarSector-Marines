package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortRoom;
import com.dillon.starsectormarines.battle.world.gen.fortress.GateTowers;

import java.util.ArrayList;
import java.util.List;

/**
 * A fort's first gate tower with a squad ordered to hold it and a file of targets out on the approach it overlooks:
 * the set-up the gate-tower scene judges and the look suite frames. The holders start in the yard behind the tower's
 * door, so holding it means walking in; the targets are unarmed and stand still, so the only fire is the tower's.
 */
public final class GateTowerGarrison {

    /** How far out in front of the tower's outer face the targets stand. */
    private static final int RANGE = 8;

    private GateTowerGarrison() {}

    /**
     * The tower and the squads put on it.
     *
     * @param floor   the tower's floor cells, world frame
     * @param stands  the cells its garrison stands on behind the slits
     * @param slits   the slit cells, world frame
     * @param aim     the middle of the approach in front of the outer face, where the targets stand
     */
    public record Manned(List<int[]> floor, List<int[]> stands, List<int[]> slits, int[] aim, long[] holders,
                         long[] targets) {

        /** Whether the world cell is in the tower: on its floor or standing in a slit, which is where a garrison fires from. */
        public boolean inside(int x, int y) {
            for (int[] c : floor) if (c[0] == x && c[1] == y) return true;
            for (int[] c : slits) if (c[0] == x && c[1] == y) return true;
            return false;
        }
    }

    /** The first tower of the fort's main gate, in the world frame, with nothing put on it yet. */
    public static Manned tower(FortFrame frame) {
        FortRoom room = frame.plan().towers().get(0);
        List<int[]> floor = new ArrayList<>();
        for (int x = room.x(); x < room.x() + room.width(); x++) {
            for (int y = room.y(); y < room.y() + room.height(); y++) floor.add(frame.cell(x, y));
        }
        List<int[]> stands = new ArrayList<>();
        for (int[] s : room.standCells()) stands.add(frame.cell(s[0], s[1]));
        List<int[]> slits = new ArrayList<>();
        // The outer face is the one with three slits: its middle one looks straight down the approach.
        int[] outer = null;
        List<int[]> local = GateTowers.slits(room, frame.plan()::isWorks);
        for (int[] s : local) {
            slits.add(frame.cell(s[0], s[1]));
            int same = 0;
            for (int[] t : local) if (t[2] == s[2] && t[3] == s[3]) same++;
            if (same == 3 && (outer == null || between(local, s))) outer = s;
        }
        if (outer == null) throw new IllegalStateException("the tower has no outer face");
        int[] aim = frame.cell(outer[0] + outer[2] * RANGE, outer[1] + outer[3] * RANGE);
        return new Manned(floor, stands, slits, aim, new long[0], new long[0]);
    }

    /** Whether {@code s} is the middle one of its face's three slits. */
    private static boolean between(List<int[]> slits, int[] s) {
        int neighbours = 0;
        for (int[] t : slits) {
            if (t[2] != s[2] || t[3] != s[3]) continue;
            if (Math.abs(t[0] - s[0]) + Math.abs(t[1] - s[1]) == 1) neighbours++;
        }
        return neighbours == 2;
    }

    /**
     * Spawns {@code holders} defenders ordered to hold the tower and {@code targets} unarmed, unmoving marines with
     * {@code targetHealth} each across the approach in front of it. Unless {@code pinned} the holders start in the yard
     * behind the tower's door, so holding it means walking in; pinned, each stands fixed on one of its stand cells
     * behind the slits.
     */
    public static Manned man(BattleSimulation sim, FortFrame frame, int holders, int targets, float targetHealth,
                             String weaponId, boolean pinned) {
        Manned tower = tower(frame);
        TacticalNode node = node(sim, tower);
        int[] door = yardBehindDoor(frame);
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE);
        long[] held = new long[holders];
        for (int i = 0; i < holders; i++) {
            int[] at = pinned ? tower.stands().get(i % tower.stands().size()) : door;
            EntitySpec spec = new EntitySpec("tower-" + i, Faction.DEFENDER, UnitType.MARINE, at[0], at[1]);
            new MarineLoadout(UnitRole.COMBATANT, null, weaponId, null, 0).seedInto(spec);
            spec.squad(squadId);
            if (pinned) spec.moveSpeed(0f);
            held[i] = sim.spawn(spec);
        }
        Squad squad = sim.getSquad(squadId);
        squad.originalSize = holders;
        squad.aliveMembers = holders;
        // Manned as a building's envelope, as a commander mans a garrisonable: the squad stands to at the slits.
        int[] middle = tower.floor().get(tower.floor().size() / 2);
        squad.assignedObjective = ObjectiveAssignment.holdEnvelope(squadId, node, middle[0], middle[1], 2);

        int targetSquad = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long[] shot = new long[targets];
        int[] across = across(tower);
        for (int i = 0; i < targets; i++) {
            int k = i - targets / 2;
            int x = tower.aim()[0] + across[0] * 2 * k, y = tower.aim()[1] + across[1] * 2 * k;
            EntitySpec spec = new EntitySpec("target-" + i, Faction.MARINE, UnitType.MARINE, x, y);
            spec.squad(targetSquad).moveSpeed(0f).health(targetHealth).attackDamage(0f).attackRange(0f);
            shot[i] = sim.spawn(spec);
        }
        Squad targetsSquad = sim.getSquad(targetSquad);
        targetsSquad.originalSize = targets;
        targetsSquad.aliveMembers = targets;
        return new Manned(tower.floor(), tower.stands(), tower.slits(), tower.aim(), held, shot);
    }

    /** The garrison place the fort stage seated in the tower, added to the battle's map when the map did not carry it. */
    private static TacticalNode node(BattleSimulation sim, Manned tower) {
        int[] first = tower.stands().get(0);
        for (TacticalNode n : sim.getTacticalMap().ofKind(TacticalNode.Kind.INNER_POSITION)) {
            if (n.anchorX == first[0] && n.anchorY == first[1]) return n;
        }
        List<TacticalNode.StandPosition> stands = new ArrayList<>();
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (int[] s : tower.stands()) {
            stands.add(new TacticalNode.StandPosition(s[0], s[1]));
            left = Math.min(left, s[0]);
            top = Math.min(top, s[1]);
            right = Math.max(right, s[0]);
            bottom = Math.max(bottom, s[1]);
        }
        TacticalNode node = new TacticalNode(TacticalNode.Kind.INNER_POSITION, first[0], first[1], left, top, right,
                bottom, Faction.DEFENDER, 40, stands.size(), stands);
        sim.getTacticalMap().add(node);
        return node;
    }

    /** The yard cell a step out of the tower's door. */
    private static int[] yardBehindDoor(FortFrame frame) {
        FortRoom room = frame.plan().towers().get(0);
        int[] door = room.doorCells().get(0);
        int outX = door[0] < room.x() ? -1 : door[0] >= room.x() + room.width() ? 1 : 0;
        int outY = door[1] < room.y() ? -1 : door[1] >= room.y() + room.height() ? 1 : 0;
        return frame.cell(door[0] + 2 * outX, door[1] + 2 * outY);
    }

    /** A unit step along the outer face, in the world frame. */
    private static int[] across(Manned tower) {
        int[] a = tower.stands().get(0), b = tower.stands().get(1);
        return new int[]{Integer.signum(b[0] - a[0]), Integer.signum(b[1] - a[1])};
    }
}
