package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.DefenseFrontage;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.BreakContact;
import com.dillon.starsectormarines.battle.decision.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Squad posture: man the frontage.</b> Custom-plan action emitted by
 * {@link FrontageDefense} for a squad holding a place whose outer envelope has
 * apertures under believed threat. Each assigned member binds to one interior
 * stance cell — behind a window, beside a doorway — and holds it.
 *
 * <p>This is the posture between patrolling a compound and fighting inside it.
 * {@link GarrisonPatrol} walks the rooms with its back to the wall;
 * {@link GarrisonAmbush} needs a hostile already in the squad's own picture.
 * Neither covers the approach, which is the part of an assault a defender can
 * see coming and the part a player watches.
 *
 * <p>Fire is left to the shared dispatcher's opportunity pass rather than
 * authored here. The posts already face the apertures, so the squad's lines of
 * fire converge on the approach by construction — the same reasoning
 * {@link ChokePointHold} applies to a doorway, and there is no concentrated
 * burst to trigger because a frontage watches many openings at once rather
 * than one.
 *
 * <p>Perpetual — always {@link ActionStatus#RUNNING}. The squad-level replan is
 * what redistributes posts as believed threat shifts around the perimeter, and
 * what swaps the goal out entirely when the place is breached or morale breaks.
 * A member whose squad has gone to {@link Doctrine#DISENGAGE} breaks contact
 * instead of standing at its post, matching {@link GarrisonPatrol}.
 */
public final class ApertureHold implements Action {

    /**
     * One manned position. {@code watch} is the unheld cell the post covers —
     * the near end of its field of fire, kept for the debug overlay and to
     * explain the post's existence; a reserve post has no aperture and watches
     * its own cell.
     */
    public record Post(int standX, int standY, int watchX, int watchY,
                       DefenseFrontage.Kind kind) {

        /** Reserve post held back inside the place rather than bound to an aperture. */
        public static Post reserve(int x, int y) {
            return new Post(x, y, x, y, null);
        }

        public boolean isReserve() { return kind == null; }
    }

    private final List<Post> posts;

    public ApertureHold(List<Post> posts) {
        this.posts = List.copyOf(posts);
    }

    public List<Post> posts() { return posts; }

    @Override public String name() { return "ApertureHold[" + posts.size() + "]"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState state, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return Math.max(1, posts.size()); }

    /** Slot name encoding — used both when declaring slots and when resolving an assigned member's post. */
    public static String slotName(int idx) { return "post:" + idx; }

    /**
     * One slot per post, scored by negated distance so the nearest member
     * takes each post. Posts arrive threat-ordered from
     * {@link FrontageDefense}, and the role assigner orders slots by mean
     * score, so a squad walking onto its frontage fills the threatened side
     * first without the slots needing an explicit priority.
     */
    @Override
    public List<RoleAssigner.Slot<Long>> roles(Squad squad, BattleView sim) {
        List<RoleAssigner.Slot<Long>> slots = new ArrayList<>(posts.size());
        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            slots.add(new RoleAssigner.Slot<>(slotName(i), 1,
                    c -> -TacticalScoring.cellDistance(sim.world().x(c), sim.world().y(c),
                            post.standX() + 0.5f, post.standY() + 0.5f)));
        }
        return slots;
    }

    @Override
    public List<int[]> highlightCells(Squad squad, BattleView sim) {
        List<int[]> out = new ArrayList<>(posts.size() * 2);
        for (Post post : posts) {
            out.add(new int[]{post.standX(), post.standY()});
            if (!post.isReserve()) out.add(new int[]{post.watchX(), post.watchY()});
        }
        return out;
    }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        if (squad.contactPicture.doctrine() == Doctrine.DISENGAGE) {
            return BreakContact.INSTANCE.execute(member, squad, sim);
        }

        Post post = postFor(member, squad);
        if (post == null) return ActionStatus.RUNNING;

        if (!sim.movement().atCell(member, post.standX(), post.standY())) {
            if (sim.movement().mayRepath(member)) {
                sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                        sim.world().cellX(member), sim.world().cellY(member),
                        post.standX(), post.standY(), sim.getOccupancyMap()));
            }
            sim.advanceMovement(member);
            return ActionStatus.RUNNING;
        }

        // On post — pin in place. Fire intent is left empty so the dispatcher's
        // opportunity pass can take any legal shot through the aperture.
        if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
        return ActionStatus.RUNNING;
    }

    /** The post {@code member} is bound to this plan step, or null if it holds no slot of this action. */
    private Post postFor(long member, Squad squad) {
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null && !plan.isComplete() ? plan.currentStep() : null;
        String slot = step != null ? step.slotOf(member) : null;
        if (slot == null) return null;
        int idx = parseSlotIdx(slot);
        return idx >= 0 && idx < posts.size() ? posts.get(idx) : null;
    }

    /** Parse the trailing index off a {@code post:N} slot name; -1 on any other slot. */
    private static int parseSlotIdx(String slot) {
        int colon = slot.indexOf(':');
        if (colon < 0) return -1;
        try {
            return Integer.parseInt(slot.substring(colon + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
