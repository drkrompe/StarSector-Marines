package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.setup.SmallWarForces.Group;
import com.dillon.starsectormarines.battle.setup.SmallWarForces.SplitSide;
import com.dillon.starsectormarines.battle.setup.SmallWarSequence;
import com.dillon.starsectormarines.battle.story.SmallWarProgress;
import com.dillon.starsectormarines.battle.story.SmallWarProgress.Phase;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.BlackSluice;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Black Sluice duties. The company works the disclosed stages and flanks the causeway through the
 * galleries; holders occupy only sites the company has actually taken; the custody party moves
 * only under company cover; Greyhook's groups hold their posts, commit reserves on their declared
 * triggers and, after Voss's order, either walk out along the service road or hold dispatch.
 */
public final class SmallWarCommand implements AutonomousMissionCommand<SmallWarCommand.Frame, String> {
    public static final class Frame extends CommandFrame {
        final Phase phase;
        final int custody;
        final List<Integer> holders;
        final boolean[] visited;
        final boolean companyCover, split, formingUp;
        final Map<Integer, Group> groups;
        final Set<Integer> withdrawing;
        public Frame(CommandFrame base, Phase phase, int custody, List<Integer> holders, boolean[] visited, boolean companyCover,
                     boolean split, boolean formingUp, Map<Integer, Group> groups, Set<Integer> withdrawing) {
            super(base.tick(), base.perspective(), base.squads(), base.influence(), base.topology(), base.assignments());
            this.phase = phase; this.custody = custody; this.holders = List.copyOf(holders); this.visited = visited.clone();
            this.companyCover = companyCover; this.split = split; this.formingUp = formingUp; this.groups = Map.copyOf(groups); this.withdrawing = Set.copyOf(withdrawing);
        }
    }

    public static CommandFrameDisclosure<Frame> disclosure(SmallWarSequence sequence) {
        return (view, faction, topology, assignments) -> {
            var base = CommandFrame.freeze(view, faction, topology, assignments);
            var p = sequence.progress();
            boolean covered = false;
            if (faction == Faction.ALLY) {
                var site = BlackSluice.SITES.get(SmallWarProgress.FUEL_CONTROL);
                var company = CommandFrame.freeze(view, Faction.MARINE, topology, assignments);
                // The custody party hears a bounded friendly alarm, never hostile identities or cells.
                boolean alarm = company.influence() != null
                        && WorkingPassageCommand.constructionThreat(company.influence().contacts(), site.x(), site.y()) != null;
                covered = !alarm && company.squads().stream().anyMatch(s -> s.aliveMembers() > 0
                        && Math.hypot(s.centroidX() - site.x(), s.centroidY() - site.y()) < 20);
            }
            boolean[] visited = {p.visited(0), p.visited(1), p.visited(2)};
            // Defenders know their own outfits and Voss's order; other perspectives get neither.
            boolean own = faction == Faction.DEFENDER;
            return new Frame(base, p.phase(), sequence.custodySquadId(), sequence.holderSquadIds(), visited, covered,
                    own && p.split(), faction == Faction.MARINE && sequence.formingUp(), own ? sequence.squadGroups() : Map.of(), own ? sequence.withdrawingSquads() : Set.of());
        };
    }

    private final Faction faction;
    public SmallWarCommand(Faction faction) { this.faction = faction; }
    public static String issuer(Faction faction) { return "small-war:" + faction; }
    public Faction faction() { return faction; }
    public String strategyId() { return issuer(faction); }
    public void publish(CommanderSnapshot<String> snapshot) { }

    public CommandPlan<String> plan(Frame frame) {
        var proposals = new ArrayList<CommandProposal>(); int count = 0;
        // The company's living squads in a fixed order, so the handover can post one on each site.
        var companySquads = new ArrayList<Integer>();
        if (faction == Faction.MARINE)
            for (var squad : frame.squads()) if (squad.aliveMembers() > 0) companySquads.add(squad.squadId());
        companySquads.sort(null);
        for (var squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            var old = squad.directive();
            boolean owned = old != null && (old.authority() == CommandAuthority.MISSION_COMMAND && strategyId().equals(old.issuer())
                    || old.authority() == CommandAuthority.PLAYER_INTERVENTION && old.leaseUntilTick() >= 0 && old.leaseUntilTick() < frame.tick());
            if (!owned) { proposals.add(CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND, "EXTERNAL_OWNERSHIP_PRESERVED")); continue; }
            int x, y, radius = 4;
            boolean advance = false, withdraw = false;
            int cx = Math.round(squad.centroidX()), cy = Math.round(squad.centroidY());
            if (faction == Faction.MARINE) {
                // Before the push on fuel control the company forms up at the staging ground while its lift is still landing.
                int[] target = frame.formingUp ? new int[]{BlackSluice.STAGING_X + 4, BlackSluice.STAGING_Y + 6}
                        : companyTarget(frame.phase, squad.squadId(), companySquads.indexOf(squad.squadId()), cx, frame.tick());
                x = target[0]; y = target[1]; radius = 3; advance = true;
            } else if (faction == Faction.ALLY) {
                if (squad.squadId() == frame.custody) {
                    var room = BlackSluice.SITES.get(SmallWarProgress.FUEL_CONTROL);
                    if (frame.phase == Phase.HANDOVER && frame.companyCover) { x = room.x(); y = room.y(); advance = true; radius = 2; }
                    else { x = cx; y = cy; radius = 2; }
                } else {
                    int duty = frame.holders.indexOf(squad.squadId());
                    if (duty >= 0 && frame.visited[duty]) { var site = BlackSluice.SITES.get(duty); x = site.x(); y = site.y(); advance = true; }
                    else { x = 34 + 4 * Math.max(0, duty); y = 52; }
                }
            } else {
                Group group = frame.groups.get(squad.squadId());
                if (group == null) { x = cx; y = cy; }
                else if (frame.withdrawing.contains(squad.squadId())) { x = BlackSluice.EXIT_X - 1; y = BlackSluice.EXIT_Y; withdraw = true; }
                else if (group.reserve()) { var site = BlackSluice.SITES.get(group.targetSite()); x = site.x(); y = site.y(); advance = true; }
                else if (frame.split && group.split() == SplitSide.HOLDS) {
                    // Berth Four refuses the order and holds dispatch out of grudge.
                    var site = BlackSluice.SITES.get(SmallWarProgress.DISPATCH); x = site.x(); y = site.y(); radius = 6;
                } else { x = group.x(); y = group.y(); radius = 5; }
            }
            int best = Integer.MAX_VALUE, bx = cx, by = cy;
            for (int sy = y - 5; sy <= y + 5; sy++) for (int sx = x - 5; sx <= x + 5; sx++) {
                int d = (sx - x) * (sx - x) + (sy - y) * (sy - y);
                if (d < best && frame.topology().isWalkable(sx, sy) && frame.topology().reachable(squad.anchorCellX(), squad.anchorCellY(), sx, sy)) {
                    best = d; bx = sx; by = sy;
                }
            }
            var next = withdraw ? ObjectiveAssignment.withdraw(squad.squadId(), bx, by)
                    : advance ? ObjectiveAssignment.attackMove(squad.squadId(), bx, by)
                    : ObjectiveAssignment.defendArea(squad.squadId(), bx, by, radius);
            if (old.assignment() != null && old.assignment().kind() == next.kind()
                    && old.assignment().targetCellX() == bx && old.assignment().targetCellY() == by
                    && old.assignment().targetRadiusCells() == next.targetRadiusCells()) next = old.assignment();
            proposals.add(CommandProposal.assign(next, CommandAuthority.MISSION_COMMAND, "SMALL_WAR_" + frame.phase,
                    next == old.assignment() ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED)); count++;
        }
        return new CommandPlan<>(faction, strategyId(), "SMALL_WAR_" + frame.phase, frame.tick(),
                frame.influence() == null ? -1 : frame.influence().updatedTick(), count, 0, List.of(frame.phase.name()), proposals, frame.phase.name());
    }

    /**
     * The company's stage target. Against fuel control, a squad still west of the north gallery's
     * mouth goes through the gallery, which comes out behind the house, instead of up the exposed
     * causeway under the gun. Inside a site the squads sweep it rather than stand on its centre:
     * each takes one of five points and the points rotate every {@link #SWEEP_TICKS}, so a holdout
     * in a side room is found rather than left contesting the site indefinitely. Through the handover
     * the squads, by {@code rank} among the company's living squads, stand on fuel control, dispatch
     * and customs in turn, so all three stay occupied even when the holders have been killed.
     */
    static int[] companyTarget(Phase phase, int squadId, int rank, int centroidX, long tick) {
        return switch (phase) {
            case CUSTOMS -> { var s = BlackSluice.SITES.get(SmallWarProgress.CUSTOMS); yield new int[]{s.x() + (squadId % 2 == 0 ? -2 : 2), s.y()}; }
            case FUEL_CONTROL -> centroidX < BlackSluice.NORTH_GALLERY_EXIT_X - 3
                    ? new int[]{BlackSluice.NORTH_GALLERY_EXIT_X, BlackSluice.NORTH_GALLERY_EXIT_Y}
                    : sweep(SmallWarProgress.FUEL_CONTROL, squadId, tick);
            case DISPATCH -> sweep(SmallWarProgress.DISPATCH, squadId, tick);
            default -> site(new int[]{SmallWarProgress.FUEL_CONTROL, SmallWarProgress.DISPATCH, SmallWarProgress.CUSTOMS}[Math.floorMod(rank, 3)]);
        };
    }
    static final int SWEEP_TICKS = 600;
    static int[] sweep(int index, int squadId, long tick) {
        var s = BlackSluice.SITES.get(index);
        int k = (int) Math.floorMod(squadId + tick / SWEEP_TICKS, 5L);
        return switch (k) {
            case 1 -> new int[]{s.left() + 3, s.bottom() + 3};
            case 2 -> new int[]{s.right() - 3, s.bottom() + 3};
            case 3 -> new int[]{s.left() + 3, s.top() - 3};
            case 4 -> new int[]{s.right() - 3, s.top() - 3};
            default -> new int[]{s.x(), s.y()};
        };
    }
    private static int[] site(int index) { var s = BlackSluice.SITES.get(index); return new int[]{s.x(), s.y()}; }
}
