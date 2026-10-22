package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The company's commander in the heart (`heart-nouns.md`): it plays the battle
 * as Purge from the start. The company goes in together on one bore at a time,
 * first the satellites standing in its own lane, then the nearest living mother,
 * until none is left.
 *
 * <p>Its lane ({@link HeartLane}) is the band along the line it advances on
 * from its landing. Satellites outside it are other lanes' work (Broad Front's
 * allied columns); on The Burn the lane is the whole approach.
 *
 * <p>The squads go in on a <b>frontage</b>, each to its own cell spread across
 * the advance, never all to one: a bunched company is what a lobbed round
 * finds. Under a walking barrage ({@link BarrageScreen}) the company follows
 * the guns: while its bore lies past the line that is firing it forms on that
 * frontage a safe distance short of the line and comes up as each line lifts,
 * and goes for the bore only once the barrage has passed it.
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

    /** Cells between neighbouring squads on the frontage. */
    static final int FRONTAGE_SPACING = 9;
    /** {@code -Dbattle.heart.frontage=false} is the control: every squad to the one cell. */
    private static final boolean FRONTAGE = Boolean.parseBoolean(System.getProperty("battle.heart.frontage", "true"));
    /** How far from its frontage cell a squad's cell may be moved to find open ground. */
    static final int SLOT_SEARCH = 6;
    /** How far short of its bore, along the advance, a squad's own cell lies when it goes in. */
    static final int ASSAULT_STANDOFF = 5;

    private final HeartLane lane;
    private final BarrageScreen screen;

    /** @param lane the company's own lane */
    public HeartAssaultCommand(HeartLane lane) {
        this(lane, null);
    }

    /**
     * @param lane   the company's own lane
     * @param screen the barrage it follows, or {@code null} when it fights with none
     */
    public HeartAssaultCommand(HeartLane lane, BarrageScreen screen) {
        this.lane = lane;
        this.screen = screen;
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
        int tick = frame.tick();
        boolean held = target != null && screen != null && screen.beyond(screen.along(target.x(), target.y()), tick);
        String phase = target == null ? "HEART_DOWN"
                : (held ? "FOLLOW_" : "") + (target.mother() ? "MOTHER_" : "SATELLITE_") + target.key();
        company.sort(Comparator.comparingInt(CommandSquadState::squadId));
        int sign = target == null ? 0 : screen != null ? screen.sign()
                : Integer.signum(along(target) - Math.round(lane.advancesAlongY() ? cy : cx));
        for (int i = 0; i < company.size(); i++) {
            CommandSquadState squad = company.get(i);
            ObjectiveAssignment next;
            if (target == null) {
                next = ObjectiveAssignment.defendArea(squad.squadId(), Math.round(squad.centroidX()), Math.round(squad.centroidY()));
            } else {
                int offset = FRONTAGE ? Math.round((i - (company.size() - 1) / 2f) * FRONTAGE_SPACING) : 0;
                int along = held ? screen.holdAlong(tick) : along(target) - sign * ASSAULT_STANDOFF;
                int[] cell = FRONTAGE || held ? slot(frame.topology(), along, target, offset) : null;
                if (cell == null) cell = new int[]{target.x(), target.y()};
                next = ObjectiveAssignment.attackMove(squad.squadId(), cell[0], cell[1]);
                // Held short of the guns, a squad forms on its cell; going in, it breaches the bore from it.
                if (!held) next = next.withBreach(target.x(), target.y());
            }
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
     * A squad's cell on the frontage: {@code along} on the advance, {@code offset}
     * across it from the bore's line, moved to the nearest open ground; {@code null}
     * when none is near.
     */
    static int[] slot(CommandTopology topology, int along, KnownBore bore, int offset, boolean advancesAlongY) {
        int across = (advancesAlongY ? bore.x() : bore.y()) + offset;
        int x = advancesAlongY ? across : along, y = advancesAlongY ? along : across;
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int dy = -SLOT_SEARCH; dy <= SLOT_SEARCH; dy++) {
            for (int dx = -SLOT_SEARCH; dx <= SLOT_SEARCH; dx++) {
                int cx = x + dx, cy = y + dy, distance = dx * dx + dy * dy;
                if (distance >= bestDistance || !topology.inBounds(cx, cy) || !topology.isWalkable(cx, cy)) continue;
                bestDistance = distance;
                best = new int[]{cx, cy};
            }
        }
        return best;
    }

    private int[] slot(CommandTopology topology, int along, KnownBore bore, int offset) {
        return slot(topology, along, bore, offset, lane.advancesAlongY());
    }

    private int along(KnownBore bore) {
        return lane.advancesAlongY() ? bore.y() : bore.x();
    }

    /** The bore the company goes for from {@code (x, y)}: its lane's next, or none. */
    KnownBore target(List<KnownBore> bores, float x, float y) {
        return lane.target(bores, x, y);
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
