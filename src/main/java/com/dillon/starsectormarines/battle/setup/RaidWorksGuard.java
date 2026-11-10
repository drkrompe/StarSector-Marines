package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The works' own guard ({@code raid-mission.md}, Security): on a Raid site, a squad stands at each of the works' guard
 * posts from the first tick, so the machines are held when the raid lands and the garrison beside the works answers
 * the alarm (owner pick {@code raid-works-guard} a). A post is a guard room or a gun emplacement of the works; a
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
     * {@code nodes} in the order the defender allocation seats garrisons: the works' guard posts first, whatever
     * their priority, then every other node highest priority first. Equal priorities keep their order.
     */
    static List<TacticalNode> seatingOrder(List<TacticalNode> nodes) {
        List<TacticalNode> posts = posts(nodes);
        List<TacticalNode> rest = new ArrayList<>(nodes);
        rest.removeAll(posts);
        rest.sort(Comparator.comparingInt((TacticalNode n) -> -n.priorityScore));
        List<TacticalNode> order = new ArrayList<>(posts);
        order.addAll(rest);
        return order;
    }
}
