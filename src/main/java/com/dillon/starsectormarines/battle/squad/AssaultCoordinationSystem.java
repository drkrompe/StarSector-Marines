package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateless tick consumer that divides one contact between the squads
 * attacking it. Runs after contact pictures are published and before the
 * replan reads them, so every cooperating squad plans against the same answer.
 *
 * <p><b>Squads under one attack-move order are a maneuver group, the way fire
 * teams inside a squad are.</b> Without that, several squads converging on the
 * same defenders each independently reach the same conclusion — form a line
 * and shoot — and the result is three frontal lines against one position and
 * nobody moving. The fix is the pattern the squad already runs internally, one
 * level up: somebody holds the enemy's attention and somebody else moves.
 *
 * <p>The grouping is by <em>shared believed contact</em>, not by proximity. Two
 * squads a short distance apart looking at different enemies are not
 * cooperating and must not be told they are; two squads further apart that have
 * both identified the same hostile are. Belief is what makes it honest — a
 * squad that has never seen the enemy contributes nothing to a group formed
 * around it.
 *
 * <p>A squad whose contact picture has not caught up yet is grouped on its
 * published contact onset instead. That is the same honesty rule rather than an
 * exception to it: an onset is a member of that squad looking at that hostile
 * right now, which is a stronger claim to have seen it than the aggregate the
 * picture is still assembling. Without this a squad that has just walked into
 * the enemy is absent from the group forming around them for as long as its
 * picture takes to agree, and the sibling maneuvering past it is maneuvering
 * around a fixing squad that nobody has told to fix.
 *
 * <p><b>Role selection is deterministic.</b> The squad with the best firing
 * line fixes, everyone else maneuvers, and every tie breaks on squad id.
 * Deterministic replay is a hard requirement of the commander evidence
 * harness, and a role that depended on registry iteration order would make two
 * identical runs diverge.
 *
 * <p>This system never writes an assignment, a path, or a target. It publishes
 * a reading; {@code AttackMove} decides what to do with it. Sibling System to
 * {@link SquadAlertSystem} / {@link SquadMoraleSystem} / {@link SquadReplanSystem}.
 */
public final class AssaultCoordinationSystem {

    /**
     * A fixing squad only relieves a sibling if it can actually shoot. One
     * engageable fire team is the floor: below that the "base of fire" is a
     * single marine and the sibling is maneuvering against an enemy nobody is
     * holding.
     */
    static final int MIN_FIXING_FIRE_TEAMS = 1;

    private final UnitRosterService roster;

    public AssaultCoordinationSystem(UnitRosterService roster) {
        this.roster = roster;
    }

    /** Publishes one {@link SquadAssaultPicture} per live attack-move squad. */
    public void tick(int currentTick) {
        Map<Long, List<Squad>> byContact = null;
        for (Squad squad : roster.getSquads()) {
            if (!attacking(squad)) {
                if (squad.assaultPicture != SquadAssaultPicture.NONE) {
                    squad.assaultPicture = SquadAssaultPicture.NONE;
                }
                continue;
            }
            long contact = squad.contactPicture.primaryContactId();
            if (contact == 0L) contact = squad.onsetContactId;
            if (contact == 0L || !roster.isAliveById(contact)) {
                squad.assaultPicture = SquadAssaultPicture.NONE;
                continue;
            }
            if (byContact == null) byContact = new HashMap<>();
            byContact.computeIfAbsent(contact, c -> new ArrayList<>()).add(squad);
        }
        if (byContact == null) return;

        for (Map.Entry<Long, List<Squad>> entry : byContact.entrySet()) {
            assignGroup(entry.getKey(), entry.getValue(), currentTick);
        }
    }

    private void assignGroup(long contact, List<Squad> group, int currentTick) {
        if (group.size() < 2) {
            // A lone squad on a contact is not cooperating with anybody. It
            // keeps NONE and runs ordinary attack-move doctrine, which is the
            // degenerate case of this system rather than a special one.
            group.get(0).assaultPicture = SquadAssaultPicture.NONE;
            return;
        }
        group.sort(Comparator.comparingInt(s -> s.id));

        Squad fixing = null;
        for (Squad candidate : group) {
            if (candidate.contactPicture.primaryEngageableFireTeams()
                    < MIN_FIXING_FIRE_TEAMS) continue;
            if (fixing == null || better(candidate, fixing)) fixing = candidate;
        }
        if (fixing == null) {
            // Nobody in the group can shoot the shared contact yet. Everyone
            // keeps closing under ordinary doctrine; inventing a maneuver
            // around an enemy no one is holding is worse than converging.
            for (Squad squad : group) squad.assaultPicture = SquadAssaultPicture.NONE;
            return;
        }

        float axisX = 0f;
        float axisY = 0f;
        float dx = roster.world().x(contact) - fixing.centroidX;
        float dy = roster.world().y(contact) - fixing.centroidY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length > 1e-4f) {
            axisX = dx / length;
            axisY = dy / length;
        }

        for (Squad squad : group) {
            SquadAssaultPicture.Role role = squad == fixing
                    ? SquadAssaultPicture.Role.BASE_OF_FIRE
                    : SquadAssaultPicture.Role.MANEUVER;
            squad.assaultPicture = new SquadAssaultPicture(currentTick, role,
                    contact, fixing.id, axisX, axisY);
        }
    }

    /**
     * More engageable fire teams wins, then more engageable members. The list
     * is pre-sorted by id and this comparison is strict, so an exact tie keeps
     * the lower id — the same squad on every replay.
     */
    private static boolean better(Squad candidate, Squad incumbent) {
        int candidateTeams = candidate.contactPicture.primaryEngageableFireTeams();
        int incumbentTeams = incumbent.contactPicture.primaryEngageableFireTeams();
        if (candidateTeams != incumbentTeams) return candidateTeams > incumbentTeams;
        return candidate.contactPicture.primaryEngageableMembers()
                > incumbent.contactPicture.primaryEngageableMembers();
    }

    private static boolean attacking(Squad squad) {
        if (squad.aliveMembers <= 0) return false;
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        return assignment != null
                && assignment.kind() == AssignmentKind.ATTACK_MOVE;
    }
}
