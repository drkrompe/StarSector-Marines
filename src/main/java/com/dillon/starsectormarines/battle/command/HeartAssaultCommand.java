package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * The frozen Marine picture: the common frame, the bores the company knows of, and how many of its side stand within
     * {@link #PUSH_RADIUS} of each living mother.
     */
    public static final class Frame extends CommandFrame {
        final List<KnownBore> bores;
        final Map<String, Integer> friendsNearMother;

        Frame(CommandFrame base, List<KnownBore> bores, Map<String, Integer> friendsNearMother) {
            super(base.tick(), base.perspective(), base.squads(), base.influence(), base.topology(), base.assignments());
            this.bores = List.copyOf(bores);
            this.friendsNearMother = Map.copyOf(friendsNearMother);
        }

        int friendsNear(KnownBore mother) {
            return friendsNearMother.getOrDefault(mother.key(), 0);
        }
    }

    /**
     * Discloses the open bores: they are great structures on open ground, seen from the start line. The fighting round
     * a mother is in plain sight of the side's own lanes, so the frame counts its own side there too.
     */
    public static final CommandFrameDisclosure<Frame> DISCLOSURE = (view, faction, topology, assignments) -> {
        List<KnownBore> bores = new ArrayList<>();
        Map<String, Integer> near = new HashMap<>();
        for (SwarmBore bore : view.getSwarmBores()) {
            bores.add(new KnownBore(bore.beatKey(), bore.mother(), bore.x(), bore.y(), !bore.collapsed()));
            if (bore.mother() && !bore.collapsed()) {
                near.put(bore.beatKey(), view.getUnitIndex().countHostileCombatants(bore.x() + 0.5f, bore.y() + 0.5f,
                        HeartAssaultCommand.PUSH_RADIUS, Faction.DEFENDER, 0L));
            }
        }
        return new Frame(CommandFrame.freeze(view, faction, topology, assignments), bores, near);
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
    /** Whether the company holds its taken ground while any satellite stands, rather than going on to a mother alone. */
    private boolean waitForSatellites;
    /** Where the company is holding, fixed when it starts to, so the order does not follow its own drift. */
    private int holdX = Integer.MIN_VALUE, holdY;
    private int fallBackX = Integer.MIN_VALUE, fallBackY;
    /** The mother the company has gone in on with the lanes, so a thinning of the lanes there does not call it back. */
    private String pushingOn;
    /** The allied lanes beside the company's: a satellite one of them lies nearer is that lane's work, not the company's. */
    private List<HeartLane> alongside = List.of();

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

    /**
     * Broad Front: its own satellite down, the company holds that ground until the lanes have taken theirs, and
     * goes for the mothers with them (`heart-nouns.md`, Broad Front, the bores fall in turn).
     */
    public HeartAssaultCommand waitingForSatellites() {
        waitForSatellites = true;
        return this;
    }

    /** As {@link #waitingForSatellites()}, falling back to {@code (x, y)} to wait rather than holding the ground it took. */
    public HeartAssaultCommand waitingForSatellites(int x, int y) {
        waitForSatellites = true;
        fallBackX = x;
        fallBackY = y;
        return this;
    }

    /**
     * Broad Front: the allied lanes either side of the company's. The bands overlap, and a satellite that lies nearer
     * an allied lane's line than the company's is that lane's (`heart-nouns.md`, Broad Front): the company leaves it
     * to them and holds once its own is down, rather than going on to a satellite beside the mothers alone.
     */
    public HeartAssaultCommand alongside(List<HeartLane> lanes) {
        alongside = List.copyOf(lanes);
        return this;
    }

    /** Whether {@code bore} is an allied lane's satellite: inside that lane's band and nearer its line than {@code own}'s. */
    static boolean theirs(KnownBore bore, HeartLane own, List<HeartLane> others) {
        if (bore.mother()) return false;
        int across = own.advancesAlongY() ? bore.x() : bore.y();
        int mine = Math.abs(across - own.across());
        for (HeartLane other : others) {
            int theirs = Math.abs(across - other.across());
            if (theirs <= other.halfWidth() && theirs < mine) return true;
        }
        return false;
    }

    /** Cells from where the company holds within which a squad has fallen back and holds. */
    static final int FALL_BACK_REACH = 12;

    /**
     * Whether a squad at {@code (x, y)} is still on its way back to where the company holds: further than
     * {@link #FALL_BACK_REACH}. A squad told to defend a cell far behind it fights where it stands instead (Broad Front
     * seed 5, 2026-10-24: the company stood 170 cells forward for six thousand ticks and lost half its marines there),
     * so one still on its way withdraws.
     */
    static boolean stillFallingBack(float x, float y, int holdX, int holdY) {
        return Math.hypot(x - holdX, y - holdY) > FALL_BACK_REACH;
    }

    /**
     * Cells round a mother within which the side's own bodies are counted as the push on her ({@link #ALLIES_TO_PUSH}).
     */
    static final float PUSH_RADIUS = 60f;
    /**
     * Bodies of the company's side (the lanes' militia, mechs and any Marines) that must stand within
     * {@link #PUSH_RADIUS} of a mother before the company goes in on her. Alone it was wiped seventy cells short
     * (frozen 5, 2026-10-25, with the lanes a third of the way there); held for them it kept seventeen of twenty-four
     * where going in alone as the last satellite fell left one (town arid 3). UNTUNED.
     */
    static final int ALLIES_TO_PUSH = 16;

    /**
     * Whether the company goes in on {@code mother} now: once {@link #ALLIES_TO_PUSH} of its side stand round her, and
     * from then on while she is the one it is pushing on.
     */
    static boolean goesIn(KnownBore mother, int friendsNear, String pushingOn) {
        return mother.key().equals(pushingOn) || friendsNear >= ALLIES_TO_PUSH;
    }

    /**
     * {@code -Dbattle.heart.helpStranded=false} is the control: its own satellites down, the company falls back to its
     * landing and holds there while any satellite stands. On, it goes to the nearest satellite still standing
     * ({@link #stranded}). Temporary: remove with the losing arm once the owner has seen it and the Broad Front pool has
     * been read against it (2026-10-25, the well-found company on four maps: the control stood at its landing unaware
     * from about tick 12000 to the cap while flank satellites stood; on, town arid 3 was won with all 24 marines, and
     * satellites down at the cap went from 15 of 20 to 16).
     */
    public static final String HELP_STRANDED_PROPERTY = "battle.heart.helpStranded";
    private static final boolean HELP_STRANDED = !"false".equalsIgnoreCase(System.getProperty(HELP_STRANDED_PROPERTY));

    /** The living satellite nearest {@code (x, y)}, whichever lane's it is, or {@code null} when none stands. */
    static KnownBore stranded(List<KnownBore> bores, float x, float y) {
        KnownBore best = null;
        double bestDistance = Double.MAX_VALUE;
        for (KnownBore bore : bores) {
            if (!bore.living() || bore.mother()) continue;
            double distance = Math.hypot(bore.x() - x, bore.y() - y);
            if (distance < bestDistance) {
                best = bore;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Whether any satellite still stands. */
    static boolean satelliteStanding(List<KnownBore> bores) {
        for (KnownBore bore : bores) if (bore.living() && !bore.mother()) return true;
        return false;
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
        boolean waiting = waitForSatellites && target != null && target.mother() && satelliteStanding(frame.bores);
        // Its own satellites down, the company takes one the lanes have left standing rather than wait at its landing.
        KnownBore help = waiting && HELP_STRANDED ? stranded(frame.bores, cx, cy) : null;
        if (help != null) {
            target = help;
            waiting = false;
        }
        // Every satellite down, the company goes in on a mother with the lanes, not ahead of them.
        if (waitForSatellites && !waiting && target != null && target.mother()) {
            if (goesIn(target, frame.friendsNear(target), pushingOn)) pushingOn = target.key();
            else waiting = true;
        }
        boolean held = target != null && screen != null && screen.beyond(screen.along(target.x(), target.y()), tick);
        if (!waiting) holdX = Integer.MIN_VALUE;
        else if (holdX == Integer.MIN_VALUE) {
            holdX = fallBackX != Integer.MIN_VALUE ? fallBackX : Math.round(cx);
            holdY = fallBackX != Integer.MIN_VALUE ? fallBackY : Math.round(cy);
        }
        String phase = target == null ? "HEART_DOWN" : waiting ? "HOLD_FOR_LANES"
                : (held ? "FOLLOW_" : "") + (target.mother() ? "MOTHER_" : "SATELLITE_") + target.key();
        company.sort(Comparator.comparingInt(CommandSquadState::squadId));
        int sign = target == null ? 0 : screen != null ? screen.sign()
                : Integer.signum(along(target) - Math.round(lane.advancesAlongY() ? cy : cx));
        for (int i = 0; i < company.size(); i++) {
            CommandSquadState squad = company.get(i);
            ObjectiveAssignment next;
            if (target == null) {
                next = ObjectiveAssignment.defendArea(squad.squadId(), Math.round(squad.centroidX()), Math.round(squad.centroidY()));
            } else if (waiting) {
                // Falling back, a squad breaks off and goes; there, it holds.
                next = stillFallingBack(squad.centroidX(), squad.centroidY(), holdX, holdY)
                        ? ObjectiveAssignment.withdraw(squad.squadId(), holdX, holdY)
                        : ObjectiveAssignment.defendArea(squad.squadId(), holdX, holdY);
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
        if (alongside.isEmpty()) return lane.target(bores, x, y);
        List<KnownBore> mine = new ArrayList<>(bores.size());
        for (KnownBore bore : bores) if (!theirs(bore, lane, alongside)) mine.add(bore);
        return lane.target(mine, x, y);
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
