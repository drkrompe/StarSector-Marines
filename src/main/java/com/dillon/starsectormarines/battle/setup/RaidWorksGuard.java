package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The works' own guard ({@code raid-mission.md}, Security): on a Raid site, a squad stands at each of the works' guard
 * posts from the first tick, so the machines are held when the raid lands and the garrison beside the works answers
 * the alarm (owner pick {@code raid-works-guard} a). The market's machines stand at the works' machine posts, its
 * gates and its yard reserve, next ({@link #machinePosts}). A post is a guard room or a gun emplacement of the works; a
 * squad on an emplacement's post crews that gun (owner: "Yes, we should"). The squads come out of the garrison's
 * share: the defender allocation seats the works' posts before any other place, and its floor for the mobile pool is
 * untouched.
 */
final class RaidWorksGuard {

    private RaidWorksGuard() { }

    /** The works' guard posts among {@code nodes}: defender guard posts (rooms and emplacements) of the works precinct. */
    static List<TacticalNode> posts(List<TacticalNode> nodes) {
        List<TacticalNode> posts = new ArrayList<>();
        for (TacticalNode node : nodes) {
            if (node.kind == TacticalNode.Kind.GUARDPOST && node.defaultGuard == Faction.DEFENDER
                    && RaidSiteTemplate.WORKS.equals(node.place())) {
                posts.add(node);
            }
        }
        return posts;
    }

    /**
     * The works' machine posts among {@code nodes} ({@code raid-demolish-the-works.md}, slice 6): defender posts named
     * {@link RaidSiteTemplate#WORKS_MACHINES} at its gates and {@link RaidSiteTemplate#WORKS_RESERVE} in its yard.
     */
    static List<TacticalNode> machinePosts(List<TacticalNode> nodes) {
        List<TacticalNode> posts = new ArrayList<>();
        for (TacticalNode node : nodes) {
            if (node.defaultGuard == Faction.DEFENDER && (RaidSiteTemplate.WORKS_MACHINES.equals(node.place())
                    || RaidSiteTemplate.WORKS_RESERVE.equals(node.place()))) {
                posts.add(node);
            }
        }
        return posts;
    }

    /**
     * {@code nodes} in the order the defender allocation seats garrisons: the works' guard posts first, whatever
     * their priority, then the works' machine posts, then every other node highest priority first. Equal priorities
     * keep their order.
     */
    static List<TacticalNode> seatingOrder(List<TacticalNode> nodes) {
        List<TacticalNode> posts = posts(nodes);
        List<TacticalNode> machines = machinePosts(nodes);
        List<TacticalNode> forward = forwardPosts(nodes);
        List<TacticalNode> rest = new ArrayList<>(nodes);
        rest.removeAll(posts);
        rest.removeAll(machines);
        rest.removeAll(forward);
        rest.sort(Comparator.comparingInt((TacticalNode n) -> -n.priorityScore));
        List<TacticalNode> order = new ArrayList<>(posts);
        order.addAll(machines);
        order.addAll(forward);
        order.addAll(rest);
        return order;
    }

    /**
     * The squads standing at the works' yard reserve once the defenders are seated: those the defender command sends to
     * the hall under attack on the alarm ({@code raid-demolish-the-works.md}, slice 6).
     */
    static Set<Integer> reserveSquads(BattleSimulation sim) {
        Set<Integer> reserve = new TreeSet<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction == Faction.DEFENDER && squad.assignedNode != null
                    && RaidSiteTemplate.WORKS_RESERVE.equals(squad.assignedNode.place())) reserve.add(squad.id);
        }
        return reserve;
    }

    /** The works' forward posts among {@code nodes}: defender posts named {@link RaidSiteTemplate#WORKS_FORWARD}. */
    static List<TacticalNode> forwardPosts(List<TacticalNode> nodes) {
        List<TacticalNode> posts = new ArrayList<>();
        for (TacticalNode node : nodes) {
            if (node.defaultGuard == Faction.DEFENDER && RaidSiteTemplate.WORKS_FORWARD.equals(node.place())) {
                posts.add(node);
            }
        }
        return posts;
    }

    /** Who a node is held by when the allocation reaches it. */
    enum Holder { MACHINES, INFANTRY, NONE }

    /**
     * Who holds {@code node} ({@code worksPost}: a works guard post; {@code machinePost}: a works machine post) with
     * machines and infantry still to seat: a guard post is held by riflemen while any are left (a mech does not stand
     * in a guard room); a machine post only by the machines security adds for it ({@link RaidWorksMachines}), and
     * stays empty when none are left; any other place by
     * machines first, then infantry.
     */
    static Holder holder(boolean worksPost, boolean machinePost, boolean machinesLeft, boolean infantryLeft) {
        return holder(worksPost, machinePost, false, machinesLeft, infantryLeft);
    }

    /** As {@link #holder(boolean, boolean, boolean, boolean)}, where a forward post is held only by riflemen. */
    static Holder holder(boolean worksPost, boolean machinePost, boolean forwardPost, boolean machinesLeft,
                         boolean infantryLeft) {
        if (forwardPost) return infantryLeft ? Holder.INFANTRY : Holder.NONE;
        if (machinePost) return machinesLeft ? Holder.MACHINES : Holder.NONE;
        if (worksPost) return infantryLeft ? Holder.INFANTRY : machinesLeft ? Holder.MACHINES : Holder.NONE;
        return machinesLeft ? Holder.MACHINES : infantryLeft ? Holder.INFANTRY : Holder.NONE;
    }
}
