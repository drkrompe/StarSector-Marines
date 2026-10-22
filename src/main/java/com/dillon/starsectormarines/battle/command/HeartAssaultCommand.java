package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The company's commander in the heart (`heart-nouns.md`): it plays the battle
 * as Purge from the start. The company goes in together on one bore at a time,
 * first the satellites standing in its own lane, then the nearest living mother,
 * until none is left.
 *
 * <p>A lane is ground, not a corridor: it is the band of the map within
 * {@code laneHalfWidth} cells of the line the company advances along from its
 * landing. Satellites outside it are other lanes' work (Broad Front's allied
 * columns); on The Burn the lane is the whole approach.
 */
public final class HeartAssaultCommand implements AutonomousMissionCommand<HeartAssaultCommand.Frame, String> {

    public static final String ISSUER = "heart-assault";

    /** The frozen Marine picture: the common frame and the bores the company knows of. */
    public static final class Frame extends CommandFrame {
        final List<KnownBore> bores;

        Frame(CommandFrame base, List<KnownBore> bores) {
            super(base.tick(), base.perspective(), base.squads(), base.influence(), base.topology(), base.assignments());
            this.bores = List.copyOf(bores);
        }
    }

    /** Discloses the open bores: they are great structures on open ground, seen from the start line. */
    public static final CommandFrameDisclosure<Frame> DISCLOSURE = (view, faction, topology, assignments) -> {
        List<KnownBore> bores = new ArrayList<>();
        for (SwarmBore bore : view.getSwarmBores()) {
            bores.add(new KnownBore(bore.beatKey(), bore.mother(), bore.x(), bore.y(), !bore.collapsed()));
        }
        return new Frame(CommandFrame.freeze(view, faction, topology, assignments), bores);
    };

    private final boolean advancesAlongY;
    private final int laneAcross;
    private final int laneHalfWidth;

    /**
     * @param advancesAlongY whether the attack runs along the map's y axis (start south or north)
     * @param laneAcross     the company's lane, as the cell coordinate across the advance
     * @param laneHalfWidth  cells either side of the lane a satellite may stand and still be the company's
     */
    public HeartAssaultCommand(boolean advancesAlongY, int laneAcross, int laneHalfWidth) {
        this.advancesAlongY = advancesAlongY;
        this.laneAcross = laneAcross;
        this.laneHalfWidth = laneHalfWidth;
    }

    @Override public Faction faction() { return Faction.MARINE; }
    @Override public String strategyId() { return ISSUER; }
    @Override public void publish(CommanderSnapshot<String> snapshot) { }

    @Override
    public CommandPlan<String> plan(Frame frame) {
        List<CommandSquadState> company = new ArrayList<>();
        List<CommandProposal> proposals = new ArrayList<>();
        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            if (!owned(squad, frame.tick()) || !infantry(squad.role())) {
                proposals.add(CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND,
                        "EXTERNAL_OWNERSHIP_PRESERVED"));
                continue;
            }
            company.add(squad);
        }
        float cx = 0f, cy = 0f;
        for (CommandSquadState squad : company) { cx += squad.centroidX(); cy += squad.centroidY(); }
        if (!company.isEmpty()) { cx /= company.size(); cy /= company.size(); }
        KnownBore target = target(frame.bores, cx, cy);
        String phase = target == null ? "HEART_DOWN" : (target.mother() ? "MOTHER_" : "SATELLITE_") + target.key();
        for (CommandSquadState squad : company) {
            ObjectiveAssignment next = target == null
                    ? ObjectiveAssignment.defendArea(squad.squadId(), Math.round(squad.centroidX()), Math.round(squad.centroidY()))
                    : ObjectiveAssignment.attackMove(squad.squadId(), target.x(), target.y()).withBreach(target.x(), target.y());
            CommandDirective incumbent = squad.directive();
            boolean same = incumbent != null && Objects.equals(incumbent.assignment(), next);
            proposals.add(CommandProposal.assign(same ? incumbent.assignment() : next, CommandAuthority.MISSION_COMMAND,
                    "HEART_" + phase, same ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED));
        }
        return new CommandPlan<>(Faction.MARINE, ISSUER, phase, frame.tick(),
                frame.influence() == null ? -1 : frame.influence().updatedTick(), company.size(), 0,
                List.of(phase), proposals, phase);
    }

    /**
     * The bore the company goes for from {@code (x, y)}: the nearest living
     * satellite in its lane, else the nearest living mother, else none.
     */
    KnownBore target(List<KnownBore> bores, float x, float y) {
        KnownBore best = nearest(bores, x, y, false);
        return best != null ? best : nearest(bores, x, y, true);
    }

    private KnownBore nearest(List<KnownBore> bores, float x, float y, boolean mothers) {
        KnownBore best = null;
        float bestDistance = Float.MAX_VALUE;
        for (KnownBore bore : bores) {
            if (!bore.living() || bore.mother() != mothers) continue;
            if (!mothers && Math.abs((advancesAlongY ? bore.x() : bore.y()) - laneAcross) > laneHalfWidth) continue;
            float dx = bore.x() - x, dy = bore.y() - y, distance = dx * dx + dy * dy;
            if (distance < bestDistance) { bestDistance = distance; best = bore; }
        }
        return best;
    }

    /** Squads this command landed, or whose player lease has run out. */
    private boolean owned(CommandSquadState squad, int tick) {
        CommandDirective old = squad.directive();
        return old != null && (old.authority() == CommandAuthority.MISSION_COMMAND && ISSUER.equals(old.issuer())
                || old.authority() == CommandAuthority.PLAYER_INTERVENTION && old.leaseUntilTick() >= 0 && old.leaseUntilTick() < tick);
    }

    private static boolean infantry(UnitRole role) {
        return role != UnitRole.TURRET && role != UnitRole.STRUCTURE
                && role != UnitRole.DRONE_HUB && role != UnitRole.DRONE_PATROL;
    }
}
