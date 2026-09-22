package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.AssignmentReason;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.Phase;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.SquadDirective;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.TrackState;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.precinct.LaneRoute;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Marine-side strategic commander for CONQUEST — the land-war pattern
 * (total map control along the {@link TraversalAxis}, push enemies toward
 * the far side). The command evaluates these layers each slow tick:
 *
 * <ol>
 *   <li><b>Deliberate compound capture (bounded to the home track and its
 *       neighbours).</b> Conquest is won
 *       only when every supply compound is {@code MARINE_HELD}, so capture
 *       is treated as the objective it is rather than an accident of the
 *       front line washing over a building. A <em>measured detachment</em>
 *       (one squad, two for a multi-room keep) is peeled off to
 *       {@link AssignmentKind#SECURE_COMPOUND} a compound the moment it is
 *       <em>uncontested</em> — squads without actionable front work go first,
 *       and at least one executable actionable squad remains on the front.
 *       The reserve budget is global across compounds; the <em>pairing</em> is
 *       not. A fresh distant detachment reaches its own track and one
 *       neighbour, own track first (see
 *       {@link #HOME_TRACK_CAPTURES_PROPERTY}). The two convergence phases
 *       below stay map-global for a reason no measurement can move: once the
 *       keep or one contested compound is the whole remaining objective there
 *       is no other front to hold. A squad already holding or standing at a
 *       capture keeps it whatever track it is on. A compound that
 *       still holds defenders is only assigned to a squad already in/adjacent
 *       to it (commit incidental presence; never feed a lone squad into a
 *       defended building). "Contested" is judged over the compound's
 *       {@code GarrisonArea} room set — the AABB-gated rooms — so a
 *       defender merely loitering in the open street nearby never blocks a
 *       capture order, and the unbounded outdoor flood never counts as "in"
 *       the compound. See {@code conquest-nouns.md}.</li>
 *   <li><b>Track front push.</b> Every squad not pulled for capture
 *       keeps a sticky preferred track, but may support one neighboring track
 *       when its own has no actionable target. Tracks coordinate the front;
 *       they are not ownership fences. A discrete hostile-contact zone wins;
 *       otherwise open-ground belief may produce a bounded own-force staging
 *       marker behind the hostile line.</li>
 *   <li><b>Keep convergence.</b> Once the canonical command post is the only
 *       uncaptured compound, every mobile assault squad converges on its
 *       {@link AssignmentKind#SECURE_COMPOUND} objective. Born-holding
 *       garrisons remain excluded.</li>
 *   <li><b>Final-compound convergence.</b> If the keep is already held and a
 *       contested non-keep compound is the sole remaining objective, capture
 *       quota remains deliberate while every other mobile squad receives
 *       room-clear support across any track. More than one recapture reopens
 *       the ordinary front.</li>
 * </ol>
 *
 * <p>Distinct partition strategy from {@link SabotageCommand}'s
 * objective-cluster shape. SabotageCommand has N sectors centered on named
 * targets (charge sites); ConquestCommand has N lateral strips and compound
 * objectives, with the forward edge of known hostile presence as its lane
 * target. Migrated Conquest planning emits proposals from a frozen command
 * frame; the assignment arbiter alone applies accepted directives.
 *
 * <p><b>Command shape — fixed preferred tracks, soft support.</b> Tracks
 * are equal-width along the lateral axis, computed once at first plan from
 * frozen public topology. A squad's preferred track is fixed at first
 * observation (by its centroid's lateral coordinate) and doesn't change
 * even if the squad drifts laterally during the battle. The effective track
 * can temporarily be either adjacent track when the preferred track is idle;
 * the command snapshot publishes both identities and the belief-derived
 * pressure/progress picture that explains the order.
 *
 * <p>When the preferred and adjacent tracks have no actionable defender
 * zone, open-ground resistance ahead may yield an {@code ADVANCE_TRACK}
 * staging order. Without that faction-local belief the assignment is cleared
 * (set to {@code null}) and the squad falls through to
 * {@code EliminateEnemiesGoal}. The
 * mission's {@code ConquestObjective} closes the battle when every
 * defender supply compound (COMMAND_POST / BARRACKS / ARMORY) is
 * MARINE_HELD — not "last defender drops"; reinforcement keeps
 * spawning fresh militia from intact compounds, so the win condition
 * is now about dismantling supply infrastructure
 * (see {@code conquest-nouns.md}).
 */
public final class ConquestCommand implements ConquestFrontCommand,
        AutonomousMissionCommand<ConquestCommandFrame, ConquestFrontSnapshot> {

    /**
     * Fixed strip count regardless of squad count. Three is reasonable for
     * the current CONQUEST shuttle counts (3–5 marine squads) — gets visible
     * lateral spread without making strips so thin that a 1-squad strip
     * can't hold its lane. Tunable; map-size-driven derivation queues
     * behind playtest.
     */
    public static final int STRIP_COUNT = ConquestTrackLayout.DEFAULT_TRACK_COUNT;

    /**
     * Garrison-zone room count at or above which a compound rates a two-squad
     * capture detachment instead of one — the "scale by size" rule. A
     * standalone ARMORY/BARRACKS resolves to one or two rooms (one squad); a
     * multi-chamber central keep resolves to several (two squads, so a single
     * counter-drop mid-capture doesn't lose the take). Tunable.
     */
    public static final int LARGE_COMPOUND_ROOMS = 3;

    /** Keep at least one executable, actionable squad on a live front. */
    public static final int MIN_FRONT_RESERVE_SQUADS = 1;

    /**
     * How far ahead of its track's friendly lead a compound may sit and still
     * earn a distant capture detachment. Roughly one lane bound: the front is
     * about to arrive there, rather than being somewhere else entirely.
     *
     * <p>Without this bound every uncaptured compound on the map is a standing
     * target for every squad at every pulse, because the distant fill ranks
     * candidates by distance from the <em>squad</em> and never consults the
     * front at all. A fourteen-compound map then offers more capture slots
     * than the attacker has squads, and the whole force is continuously
     * detached to walk at unscouted objectives — measured at 89% of published
     * marine directives before this gate existed.
     */
    public static final int CAPTURE_FRONT_REACH_CELLS = 24;

    /**
     * How many squads beyond a zone's own capture quota may be ordered to clear
     * it before the next forward zone in the strip is offered instead.
     *
     * <p>A zone's quota is what taking it is believed to want — one squad, or
     * {@link #LARGE_COMPOUND_ROOMS two} for a compound of several rooms — and a
     * zone that is not a compound's capture room is worth one. The overflow on
     * top of it is the honest slack: a clear order is contested ground rather
     * than a queue ticket, squads are lost on the way, and a cap set exactly at
     * the quota would leave a place under-assaulted every time somebody died.
     * Two is a squad in reserve behind each of a compound's own.
     *
     * <p><b>Without the cap the force does not divide at all.</b> The zone
     * picker ranks by distance along the traversal axis and consults nothing
     * about who is already going there, and {@link #TRACK_LINE_LEAD_CELLS}
     * bounds staging, so the surplus queues in depth behind the same target:
     * measured at 89–93% of the live force assigned to one {@code CLEAR_ZONE}
     * target on {@code full-strength-west}, with a third more marines landed
     * changing peak presence inside a capture zone from 46 to 48 and doubling
     * the deaths in the one 40x40 block they were queued in. The useful slots
     * are 5–9 and fixed; adding force without dividing it adds casualties.
     *
     * <p>The cap is a preference and never a refusal. A strip whose every
     * defender zone is at cap still hands one back — there is nowhere else to
     * send anybody, and a squad with no target is worse than a crowded one —
     * but it hands back the least crowded rather than the nearest, so a
     * surplus larger than every cap in the strip put together still divides.
     * Measured on {@code full-strength-west}, where three zones took three
     * squads each and thirteen more fell back onto one of them.
     */
    public static final int ZONE_TARGET_OVERFLOW_SQUADS = 2;

    /**
     * {@code -Dbattle.conquest.zoneTargetCap=true} divides the front push
     * across a strip's zones instead of ranking every squad onto the nearest
     * one. <b>Off</b>, and the switch exists because the cap has to be
     * measurable apart from everything else shipped beside it — a matrix run
     * that moves two layers at once measures neither.
     */
    public static final String ZONE_TARGET_CAP_PROPERTY =
            "battle.conquest.zoneTargetCap";

    /** Read once from the property above; see {@link #HOME_TRACK_CAPTURES_ENABLED}. */
    static boolean ZONE_TARGET_CAP_ENABLED = Boolean.parseBoolean(
            System.getProperty(ZONE_TARGET_CAP_PROPERTY, "false"));

    /**
     * How far outside a compound's own footprint a squad may stand and still
     * count as having <em>arrived</em> at it — the bound on phase 2's "already
     * there, commit the capture" gate.
     *
     * <p>That gate deliberately applies no track bound and no front-reach gate,
     * because a squad standing in the building has not been sent anywhere. It
     * read the squad's zone against the compound's garrison zones, and for an
     * <b>open compound</b> those are the outdoors: an airfield has no walls, so
     * its capture room resolves to the exterior flood and every squad in the
     * open on the whole map answered the question yes. Measured on
     * {@code full-strength-west}, squads held {@code SECURE_COMPOUND} on an
     * airbase three hundred cells east for the whole battle without arriving —
     * 16 of 45 secure-travel episodes ended in the squad's destruction and 24
     * of 45 never reached the portal, against 6 of 33 on the southern fixture,
     * which has no open compound in the way.
     *
     * <p>A few cells rather than none: a squad settling against the wall of the
     * place it is taking has arrived, and the ring the geometry test already
     * used is one cell wide.
     */
    public static final int ADJACENT_COMMIT_CELLS = 6;

    /** {@link #friendlyLeadForward} sentinel: no living friendly holds this track. */
    private static final int NO_FRIENDLY_LEAD = Integer.MIN_VALUE;

    /**
     * {@code -Dbattle.command.emptyTrackAdvance=false} restores the behaviour
     * where a track holding no believed hostile at all receives no staging
     * order, which is the control this layer's change has to be measured
     * against. Reaching that control by checking out an older commit measures
     * every other difference between the two trees at the same time.
     */
    public static final String EMPTY_TRACK_ADVANCE_PROPERTY =
            "battle.command.emptyTrackAdvance";

    static final boolean EMPTY_TRACK_ADVANCE_ENABLED = Boolean.parseBoolean(
            System.getProperty(EMPTY_TRACK_ADVANCE_PROPERTY, "true"));

    /**
     * {@code -Dbattle.command.conquest.homeTrackCaptures=false} restores the
     * map-global greedy nearest-pair capture fill, which is the control this
     * layer has to be measured against.
     *
     * <p>The track partition already limited <em>front</em> support to the
     * preferred track or one neighbour. The capture allocation never did: it
     * ranked every uncaptured compound on the map against every uncommitted
     * squad by straight-line distance and nothing else, and the preserve pass
     * then kept whatever that produced for the rest of the battle. Observed in
     * a live Conquest: a six-marine squad born on the top track, standing at
     * lateral 48, holding {@code SECURE_COMPOUND} on a barracks at lateral 157
     * — the bottom track, 110 cells away — while its own track was left to
     * three marines facing sixteen known contacts and the receiving track
     * already held nine squads. The walk is a squad out of the battle for about
     * a minute, and the track it left does not advance while it is gone.
     *
     * <p>So a fresh distant detachment reaches one track either side of home
     * and no further, and takes a neighbour's compound only while its own track
     * has nothing worth doing. "A neighbour" is the bound the front push
     * already runs on; two tracks over is not a neighbour on any reading of it.
     *
     * <p><b>The canonical matrix, on against off</b> — it costs nothing either
     * fixture is decided on:
     *
     * <ul>
     *   <li><i>reinforced-south</i> — 7 compounds captured and 2 held, both
     *       ways. 377 defenders killed against 370, 232 marines lost against
     *       228. Both timed out at 18000 ticks.</li>
     *   <li><i>full-strength-west</i> — 3 captured against 2, 0 held either
     *       way, 464 defenders killed against 457, 425 marines lost against
     *       426. Both end TERMINAL to the DEFENDER; the bound survives to
     *       12315 ticks where the control falls at 10812.</li>
     * </ul>
     *
     * <p><b>What the bound is now mostly buying is that the pathology cannot
     * come back.</b> On this tree the unbounded fill barely commits a far-track
     * pairing anyway — capture directives more than one track from home are 1
     * and 0 across the two fixtures with the bound off. That is the prosecution
     * fall-through fix upstream of it: a squad under HOLD/PROSECUTE with no
     * firing cell inside its leash used to freeze for as long as the contact
     * stayed visible, and a frozen squad is exactly the uncommitted, work-free
     * squad the distant fill reaches for. Fix the freeze and most of the far
     * pairings stop being offered. With the bound on the fill's own reason
     * never appears beyond one track at all; the 18 far pulses that remain are
     * one squad's <em>preserved</em> capture whose published effective track
     * drifted late in the battle, which is a held objective rather than a fresh
     * detachment and is not the allocation this governs.
     *
     * <p><b>The first measurement of this switch was taken before that fix and
     * read the opposite</b> — reinforced-south at 6 captures and 2 held with
     * the bound on against 12 and 11 off — which is why it briefly shipped off.
     * That number is an artifact of the freeze, not of the track bound; do not
     * re-derive a trade from it.
     */
    public static final String HOME_TRACK_CAPTURES_PROPERTY =
            "battle.command.conquest.homeTrackCaptures";

    /**
     * Read once from the property above. Not {@code final} so the control path
     * is reachable from a test as well as from an evidence run — a switch only
     * a whole JVM can flip is a switch whose off state nothing small ever
     * exercises. Nothing in the shipped command writes it.
     */
    static boolean HOME_TRACK_CAPTURES_ENABLED = Boolean.parseBoolean(
            System.getProperty(HOME_TRACK_CAPTURES_PROPERTY, "true"));

    /**
     * {@code -Dbattle.conquest.laneChain=false} puts the reading back to a
     * forward fraction of the map. On by default: a lane is a chain of places,
     * the only one worth assaulting is the first the marines do not hold, and a
     * staging order is derived along the road the map recorded between two of
     * them.
     *
     * <p>The reading it replaces is close to meaningless on a map grown from
     * places — a compound becomes assignable once the track's friendly lead is
     * within {@link #CAPTURE_FRONT_REACH_CELLS} of its depth, so a track reads
     * 0.8 advanced with its strongpoint still the defenders', and it cannot
     * record a place retaken at all.
     *
     * <p><b>It was off for a year of measurement, and what turned it on was a
     * map defect rather than anything in this class.</b> Read against a map
     * whose middle lane was silently one rung short, the chain cost
     * {@code reinforced-south} a held compound of seventeen and five captures
     * of twenty-five. Once every lane seated the whole ladder it asks for — see
     * "And what stands between the two" in {@code precincts.md} — the same code
     * measured on the same tree takes more and holds at least as much on both
     * canonical fixtures: 22 captures and 17 held on the south against the
     * fraction's 20 and 17, and 12 and 6 on the west against 7 and 5. A reading
     * that walks a ladder cannot be judged against a ladder with a rung missing.
     *
     * <p>The switch governs the whole reading rather than one side of it, so
     * either state is a whole battle. It is a switch rather than an older
     * commit because a commit-to-commit comparison measures every other
     * difference between the two trees at the same time.
     *
     * <p>A map with no lanes on it is unaffected either way: the chain is empty
     * and every compound falls through to the fraction, which is what every
     * mission but Conquest and every Conquest on an ungrown map does.
     */
    public static final String LANE_CHAIN_PROPERTY = "battle.conquest.laneChain";

    /** Read once from the property above; see {@link #HOME_TRACK_CAPTURES_ENABLED}. */
    static boolean LANE_CHAIN_ENABLED = Boolean.parseBoolean(
            System.getProperty(LANE_CHAIN_PROPERTY, "true"));

    /**
     * Whether the chain reading is in force, for the defender's own layer.
     *
     * <p>Both sides of the duel read the same map the same way — the attacker
     * to decide what to take next, the defender to decide what to hold and
     * retake — so one switch governs both, or a control run measures a
     * half-changed battle. This is not shared belief: the chain is map
     * geometry and compound ownership, both of which are neutral referee
     * facts either side may read.
     */
    public static boolean laneChainEnabled() {
        return LANE_CHAIN_ENABLED;
    }

    /** Stand this many cells behind the nearest believed hostile in a track. */
    static final int TRACK_LINE_STANDOFF_CELLS = 8;
    /** A staging marker may lead the current friendly line by at most this much. */
    static final int TRACK_LINE_LEAD_CELLS = 8;
    /** One published staging order cannot pull a rear squad farther than this. */
    static final int TRACK_LINE_MAX_STRIDE_CELLS = 24;
    /** Ignore marker changes too small to produce meaningful forward motion. */
    static final int TRACK_LINE_MIN_ADVANCE_CELLS = 3;
    /** Quantization keeps small belief jitter from rewriting a stable order. */
    static final int TRACK_LINE_BAND_CELLS = 4;
    /** How far a staging search may slide off the squad's own line of advance. */
    private static final int TRACK_LINE_SNAP_RADIUS = 12;
    /**
     * Lateral half-width of the corridor whose hostiles set a squad's standoff.
     * A track is a wide band — on a 160-cell map each of the three spans more
     * than fifty cells — so the nearest contact by forward coordinate alone can
     * sit at the far lateral edge: irrelevant to this squad's advance, yet close
     * enough on the forward axis to put the safe line behind where the squad
     * already stands. The lane stage then fails and the squad is left with no
     * assignment at all. Only contacts that could end up in front of a cell the
     * stage search might pick belong in the scan — the snap reach plus the
     * standoff.
     */
    static final int TRACK_LINE_STANDOFF_LATERAL_CELLS =
            TRACK_LINE_SNAP_RADIUS + TRACK_LINE_STANDOFF_CELLS;

    /**
     * Cells of slack added around a compound's footprint when resolving its
     * garrison zones (see {@code GarrisonArea.garrisonZones}). Small on purpose
     * — just enough to absorb the perimeter wall ring without dragging the open
     * exterior across the size gate.
     */
    public static final int GARRISON_MARGIN = 2;

    private final TraversalAxis axis;
    /** Shared production geometry; lazily synthesized only by the legacy axis constructor used in tests. */
    private ConquestTrackLayout trackLayout;

    /**
     * The routes the map recorded for its lanes, handed over at setup. Empty
     * for every mission that lays none, which is all of them but Conquest.
     */
    private final List<LaneRoute> laneRoutes;

    /**
     * The lanes read as chains of places, built once the compounds are known.
     *
     * <p>Not built in the constructor because a chain is lanes <em>and</em>
     * compounds together, and the compounds arrive with the first frozen
     * command frame. Topology and compound identity are static after spawn
     * settle, so this is read once and kept.
     */
    private ConquestLaneChain laneChain = ConquestLaneChain.NONE;
    private boolean laneChainRead = false;
    /** Per lane, this pulse: the front link, and how much of the ladder is held. */
    private int[] chainFront = new int[0];
    private int[] chainLinks = new int[0];
    private int[] chainHeld = new int[0];
    /** Capture zones the marines hold, refreshed with the compound targets. */
    private final IntOpenHashSet marineHeldZones = new IntOpenHashSet();
    /**
     * Capture zones standing on some lane's front place this pulse.
     *
     * <p>A set rather than a walk of the chain because {@link #frontHasReached}
     * is asked from inside the greedy pair loop — squads times compounds times
     * rounds — and the reading does not change within a pulse.
     */
    private final IntOpenHashSet chainFrontZones = new IntOpenHashSet();
    /** Per lane, this pulse: how far along its road the friendly line has come. */
    private int[] routeLead = new int[0];
    /** Per lane, this pulse: where believed hostiles stand along its road, sorted. */
    private int[][] routeContacts = new int[0][];

    /** Lazy: built from the first frozen command frame after battle setup settles. */
    private boolean initialized = false;
    /**
     * Per-strip zone lists, sorted forward-to-back (so the first
     * known hostile-contact zone in the list is the forward-most one).
     * Indices are zone ids. Zones whose centroid falls outside any
     * partition bucket are excluded entirely.
     */
    private List<List<Integer>> stripZones;
    /**
     * Per-zone forward-axis centroid (y for SOUTH_TO_NORTH, x for WEST_TO_EAST),
     * cached at strip-build time. Indexed directly by zone id — zone ids are
     * dense (0..zoneCount-1) per the existing zone-graph contract.
     * {@code fastutil-core} doesn't ship an
     * int-keyed float-valued map; a {@code float[]} skips both autobox and
     * hash entirely.
     */
    private float[] zoneForwardCoord;
    /** Per-zone lateral centroid used for adjacent-track target distance. */
    private float[] zoneLateralCoord;
    private float[] zoneCentroidX;
    private float[] zoneCentroidY;
    /** Walkable representative cells for debug/action explanation markers. */
    private int[] zoneMarkerX;
    private int[] zoneMarkerY;
    /** Sticky squad → strip-index assignment. First observation by centroid lateral coord wins; survives squad death-and-respawn since squad ids are monotonic. Sentinel-default {@code -1} stands in for "no assignment yet." */
    /**
     * Capture zones whose compound the front has reached at some point.
     *
     * <p>The gate is latched because <b>the front reaching a place is a fact
     * about the battle, not a reading of this pulse.</b> Computed live it
     * flickers: the lead is the foremost living squad on the track, so the
     * moment that squad dies the line "un-reaches" ground it had already taken
     * and every compound behind it closes again, pulling the detachments off
     * mid-approach. Unlatched, this gate tripled assignment churn — 68 marine
     * retargets became 638 on one fixture — and pushed the other past its tick
     * budget without resolving.
     */
    private final IntOpenHashSet frontReachedCaptureZones = new IntOpenHashSet();
    private final Int2IntOpenHashMap squadStripIdx = new Int2IntOpenHashMap();
    {
        squadStripIdx.defaultReturnValue(-1);
    }
    /** Lateral extent of the map cached at init time so {@link #stripFor} can classify squads without needing the grid. */
    private int lateralExtent = 0;
    /**
     * Zone id of the open exterior — the largest zone by cell count, cached at
     * init. Never handed out as a {@code CLEAR_ZONE} target: the exterior flood
     * spans the whole map, always holds a stray defender, and so reads as
     * "never clear" — a squad ordered to clear it charges the map forever
     * instead of doing focused area control. Outdoor defenders are engaged
     * ambiently via {@code EliminateEnemiesGoal} when in LoS; farther commander
     * belief may move an idle squad toward a safe lane staging line. We key on
     * largest-by-cells rather than id 0 because the flood-fill ids zones by
     * scan order, so id 0 can land on an indoor region. Only set when the
     * largest zone <em>dominates</em> — at least {@link #EXTERIOR_DOMINANCE_RATIO}×
     * the second-largest — so a map whose zones are all comparable in size
     * (no single open expanse) excludes nothing. {@code -1} until init / when
     * nothing dominates.
     */
    private int exteriorZoneId = -1;
    /**
     * The largest zone is only treated as the open exterior when it is at least
     * this many times bigger than the next-largest zone. The real outdoor flood
     * dwarfs every building; a map of similarly-sized rooms has no exterior to
     * exclude.
     */
    private static final float EXTERIOR_DOMINANCE_RATIO = 2.0f;
    /**
     * Per-compound capture targets, built once at init. Each caches the
     * compound's resolved capture room (the {@code SECURE_COMPOUND} push/hold
     * target matching where {@code CompoundCaptureSystem} samples occupancy), its
     * garrison zones (the AABB-gated rooms used for the contested test), and
     * the size-scaled squad quota. Topology is static after spawn settle, so
     * the garrison-zone set is frozen here; objective state and faction-local
     * contact evidence refresh each command frame.
     */
    private final List<CompoundTarget> compoundTargets = new ArrayList<>();

    /**
     * Squads this pulse's front push has already pointed at each zone, indexed
     * by zone id. Cleared at the top of the push and filled as it hands out
     * targets, so {@link #ZONE_TARGET_OVERFLOW_SQUADS} is read against the
     * plan being built rather than against last pulse's. Every uncommitted
     * squad is retargeted every pulse, so a per-pulse tally is the whole
     * picture.
     */
    private int[] zoneTargetSquads = new int[0];
    /** Presence only: rebuild once per plan instead of scanning contacts for every zone query. */
    private boolean[] hostileContactByZone = new boolean[0];
    private boolean hasUnzonedHostileContact;

    /** Once-per-command-tick explanation consumed by diagnostics and UI. */
    private volatile ConquestFrontSnapshot frontSnapshot;

    private record CompoundTarget(CompoundService.CompoundState state,
                                  TacticalNode node, int captureCellX,
                                  int captureCellY, int captureZoneId,
                                  int[] garrisonZones, int desiredSquads) {}

    /** Mutable working copy; never exposes or mutates a live {@code Squad}. */
    private static final class PlanningSquad {
        final int id;
        final Faction faction;
        final int aliveMembers;
        final float centroidX;
        final float centroidY;
        final int anchorCellX;
        final int anchorCellY;
        final int currentZoneId;
        final UnitRole role;
        final boolean localContact;
        final String executionSuspension;
        final int activePathMembers;
        final boolean underFireRecently;
        final boolean moraleBroken;
        final String currentGoal;
        final String currentAction;
        final int movingMembers;
        final int coveredFromPrimaryMembers;
        final int primaryEngageableMembers;
        final int primaryEngageableFireTeams;
        final String contactPosture;
        final String contactDoctrine;
        final String contactInitiative;
        final int coolingDownMembers;
        final int[] memberZoneIds;
        final int[] memberCellXs;
        final int[] memberCellYs;
        final ObjectiveAssignment originalAssignment;
        ObjectiveAssignment assignedObjective;

        PlanningSquad(CommandSquadState state) {
            id = state.squadId();
            faction = state.faction();
            aliveMembers = state.aliveMembers();
            centroidX = state.centroidX();
            centroidY = state.centroidY();
            anchorCellX = state.anchorCellX();
            anchorCellY = state.anchorCellY();
            currentZoneId = state.currentZoneId();
            role = state.role();
            localContact = state.localContact();
            executionSuspension = state.executionSuspension();
            activePathMembers = state.activePathMembers();
            underFireRecently = state.underFireRecently();
            moraleBroken = state.moraleBroken();
            currentGoal = state.currentGoal();
            currentAction = state.currentAction();
            movingMembers = state.movingMembers();
            coveredFromPrimaryMembers = state.coveredFromPrimaryMembers();
            primaryEngageableMembers = state.primaryEngageableMembers();
            primaryEngageableFireTeams = state.primaryEngageableFireTeams();
            contactPosture = state.contactPosture();
            contactDoctrine = state.contactDoctrine();
            contactInitiative = state.contactInitiative();
            coolingDownMembers = state.coolingDownMembers();
            memberZoneIds = state.memberZoneIds();
            memberCellXs = state.memberCellXs();
            memberCellYs = state.memberCellYs();
            originalAssignment = state.assignment();
            assignedObjective = state.assignment();
        }
    }

    public ConquestCommand(TraversalAxis axis) {
        this.axis = axis;
        this.laneRoutes = List.of();
        this.frontSnapshot = ConquestFrontSnapshot.empty(axis);
    }

    public ConquestCommand(ConquestTrackLayout trackLayout) {
        this(trackLayout, List.of());
    }

    /**
     * The production constructor: the track fence and the roads the map's own
     * lanes run on.
     *
     * @param laneRoutes what {@code MapResult.lanes} recorded, or empty for a
     *                   map that laid no lanes
     */
    public ConquestCommand(ConquestTrackLayout trackLayout,
                           List<LaneRoute> laneRoutes) {
        this.trackLayout = trackLayout;
        this.axis = trackLayout.axis();
        this.laneRoutes = laneRoutes == null ? List.of() : List.copyOf(laneRoutes);
        this.frontSnapshot = ConquestFrontSnapshot.empty(Faction.MARINE, axis);
    }

    public ConquestFrontSnapshot frontSnapshot() {
        return frontSnapshot;
    }

    @Override
    public Faction faction() {
        return Faction.MARINE;
    }

    @Override
    public String strategyId() {
        return "conquest-attacker";
    }

    @Override
    public CommandPlan<ConquestFrontSnapshot> plan(ConquestCommandFrame frame) {
        if (!initialized) {
            initializePartition(frame);
            initialized = true;
        }
        refreshCompoundTargets(frame);
        prepareRouteFrame(frame);
        indexHostileContactZones(frame);

        // Candidate squads for assignment: alive marines, minus any born-holding
        // garrison squad. Compound garrisons are NOT assigned here — the dedicated
        // holding squad is shipped in by CompoundGarrisonSystem born with HOLD_NODE
        // at deboard (see AirSystem.tryDeboardMarine), so it holds without the
        // commander pinning whichever assault squad happened to be standing in the
        // compound at capture. Skipping HOLD_NODE here leaves the garrison on
        // station and lets the capturing assault squad keep advancing.
        List<PlanningSquad> squads = new ArrayList<>();
        Map<Integer, PlanningSquad> allSquads = new TreeMap<>();
        Map<Integer, SquadDirective> directives = new TreeMap<>();
        for (CommandSquadState state : frame.squads()) {
            PlanningSquad squad = new PlanningSquad(state);
            allSquads.put(squad.id, squad);
            if (squad.aliveMembers <= 0) continue;
            if (state.directive() != null
                    && state.directive().authority().priority()
                    > CommandAuthority.MISSION_COMMAND.priority()) {
                int preferred = stripFor(squad);
                directives.put(squad.id, directive(squad, preferred, preferred,
                        squad.role == UnitRole.GARRISON
                                || (squad.originalAssignment != null
                                && squad.originalAssignment.kind()
                                == AssignmentKind.HOLD_NODE)
                                ? AssignmentReason.GARRISON_HOLD
                                : AssignmentReason.EXTERNAL_OWNERSHIP_PRESERVED));
                continue;
            }
            squads.add(squad);
        }

        IntOpenHashSet committed = new IntOpenHashSet();
        CompoundTarget keep = canonicalKeep();
        int remainingCompounds = remainingCompounds();
        CompoundTarget soleRemaining = remainingCompounds == 1
                ? soleRemainingCompound() : null;
        boolean keepConvergence = keep != null
                && keep.state != CompoundService.CompoundState.MARINE_HELD
                && remainingCompounds == 1;
        boolean finalCompoundConvergence = keep != null
                && keep.state == CompoundService.CompoundState.MARINE_HELD
                && soleRemaining != null
                && soleRemaining != keep
                && isContested(soleRemaining, frame);
        Phase phase = keepConvergence ? Phase.KEEP_CONVERGENCE
                : finalCompoundConvergence
                ? Phase.FINAL_COMPOUND_CONVERGENCE : Phase.LANE_ADVANCE;

        IntOpenHashSet deferredCaptures = new IntOpenHashSet();
        IntOpenHashSet farTrackCaptures = new IntOpenHashSet();
        if (keepConvergence) {
            for (PlanningSquad squad : squads) {
                if (reachableZone(squad, keep.captureZoneId, frame)) {
                    commitCapture(squad, keep, committed, directives,
                            AssignmentReason.KEEP_APPROACH);
                } else {
                    squad.assignedObjective = null;
                    int preferred = stripFor(squad);
                    directives.put(squad.id, directive(squad, preferred, preferred,
                            AssignmentReason.NO_REACHABLE_COMPOUND_TARGET));
                }
            }
        } else {
            // Pass 1: deliberate compound capture. Pulls a capped detachment
            // off the front while preserving ordinary compound quotas.
            assignCompoundCaptures(squads, committed, deferredCaptures,
                    farTrackCaptures, directives, frame);
        }

        if (!keepConvergence) {
            // Pass 2: preferred tracks remain sticky, but an idle track is a
            // coordination gap rather than an ownership fence. Borrow useful
            // work from one neighboring track without permanently re-homing.
            // The per-zone tally is the plan being built, so it starts empty.
            Arrays.fill(zoneTargetSquads, 0);
            // A squad already clearing a zone keeps it, and is counted before
            // anybody fresh chooses. Both halves matter: the cap's answer
            // depends on who asked first, so without stickiness a squad third
            // in line one pulse and fourth in the next is moved to another
            // zone for a reason that exists nowhere on the map. Counting the
            // standing squads first is what stops a fresh squad sizing its
            // choice against a tally that is still filling up.
            Int2IntOpenHashMap standingTargets = new Int2IntOpenHashMap();
            standingTargets.defaultReturnValue(-1);
            if (ZONE_TARGET_CAP_ENABLED && !finalCompoundConvergence) {
                for (PlanningSquad squad : squads) {
                    if (committed.contains(squad.id)) continue;
                    int zone = standingZoneTarget(squad, frame);
                    if (zone < 0) continue;
                    standingTargets.put(squad.id, zone);
                    noteZoneTarget(zone);
                }
            }
            for (PlanningSquad squad : squads) {
                if (committed.contains(squad.id)) continue;
                int preferredTrack = stripFor(squad);
                int standing = standingTargets.get(squad.id);
                TargetChoice choice = standing >= 0
                        ? new TargetChoice(
                                effectiveTrackFor(standing, preferredTrack), standing)
                        : finalCompoundConvergence
                        ? finalCompoundSupportChoice(squad, soleRemaining, frame)
                        : targetChoice(squad, preferredTrack, frame);
                if (choice.targetZoneId < 0) {
                    TrackStage stage = !finalCompoundConvergence
                            ? laneStageChoice(squad, preferredTrack, frame)
                            : null;
                    if (stage != null) {
                        ObjectiveAssignment cur = squad.assignedObjective;
                        if (cur == null
                                || cur.kind() != AssignmentKind.ADVANCE_TRACK
                                || cur.targetCellX() != stage.cellX()
                                || cur.targetCellY() != stage.cellY()) {
                            squad.assignedObjective = ObjectiveAssignment.advanceTrack(
                                    squad.id, stage.cellX(), stage.cellY());
                        }
                        SquadDirective planned = directive(squad,
                                preferredTrack, stage.trackIndex(),
                                trackFront(squad, stage.trackIndex(), frame)
                                        .believed()
                                        ? AssignmentReason.TRACK_LINE_ADVANCE
                                        : AssignmentReason
                                        .TRACK_LINE_SCOUT_ADVANCE);
                        if (deferredCaptures.contains(squad.id)) {
                            planned = planned.withDistantCaptureDeferred();
                        }
                        directives.put(squad.id, planned);
                        continue;
                    }
                    TrackStage attack = !finalCompoundConvergence
                            ? laneAttackChoice(squad, preferredTrack, frame)
                            : null;
                    if (attack != null) {
                        ObjectiveAssignment cur = squad.assignedObjective;
                        if (cur == null
                                || cur.kind() != AssignmentKind.ATTACK_MOVE
                                || cur.targetCellX() != attack.cellX()
                                || cur.targetCellY() != attack.cellY()) {
                            squad.assignedObjective = ObjectiveAssignment.attackMove(
                                    squad.id, attack.cellX(), attack.cellY());
                        }
                        SquadDirective planned = directive(squad,
                                preferredTrack, attack.trackIndex(),
                                AssignmentReason.TRACK_LINE_ATTACK);
                        if (deferredCaptures.contains(squad.id)) {
                            planned = planned.withDistantCaptureDeferred();
                        }
                        directives.put(squad.id, planned);
                        continue;
                    }
                    squad.assignedObjective = null;
                    SquadDirective planned = directive(squad, preferredTrack,
                            preferredTrack,
                            farTrackCaptures.contains(squad.id)
                                    ? AssignmentReason.CAPTURE_OUT_OF_TRACK_REACH
                                    : AssignmentReason.NO_ACTIONABLE_TRACK_TARGET);
                    if (deferredCaptures.contains(squad.id)) {
                        planned = planned.withDistantCaptureDeferred();
                    }
                    directives.put(squad.id, planned);
                    continue;
                }
                ObjectiveAssignment cur = squad.assignedObjective;
                if (cur == null
                        || cur.kind() != AssignmentKind.CLEAR_ZONE
                        || cur.targetZoneId() != choice.targetZoneId) {
                    squad.assignedObjective = ObjectiveAssignment.clearZone(
                            squad.id, choice.targetZoneId);
                }
                AssignmentReason reason = choice.trackIndex == preferredTrack
                        ? AssignmentReason.TRACK_ADVANCE
                        : AssignmentReason.ADJACENT_TRACK_SUPPORT;
                if (finalCompoundConvergence) {
                    reason = AssignmentReason.FINAL_COMPOUND_SUPPORT;
                    phase = Phase.FINAL_COMPOUND_CONVERGENCE;
                } else if (reason == AssignmentReason.ADJACENT_TRACK_SUPPORT) {
                    phase = Phase.FRONT_ADJUST;
                }
                SquadDirective planned = directive(squad, preferredTrack,
                        choice.trackIndex, reason);
                if (deferredCaptures.contains(squad.id)) {
                    planned = planned.withDistantCaptureDeferred();
                }
                directives.put(squad.id, planned);
                if (standing < 0) noteZoneTarget(choice.targetZoneId);
            }
        }

        ConquestFrontSnapshot detail = buildFrontSnapshot(frame, phase,
                remainingCompounds, keep, directives, allSquads);
        List<CommandProposal> proposals = buildProposals(
                frame, allSquads, directives, phase);
        return new CommandPlan<>(faction(), strategyId(), phase.name(), frame.tick(),
                frame.influence() != null ? frame.influence().updatedTick() : -1,
                squads.size(), 0,
                List.of("remaining compounds=" + remainingCompounds),
                proposals, detail);
    }

    @Override
    public CommanderSnapshot<ConquestFrontSnapshot> reconcile(
            CommanderSnapshot<ConquestFrontSnapshot> snapshot) {
        return snapshot.withDetail(snapshot.detail().reconcileStableDirectives(
                snapshot, frontSnapshot, strategyId()));
    }

    @Override
    public void publish(CommanderSnapshot<ConquestFrontSnapshot> snapshot) {
        frontSnapshot = snapshot.detail();
    }

    /**
     * Assign a measured capture detachment to each capturable compound.
     * Three phases over the candidate {@code squads}, marking each committed
     * squad in {@code committed} so the strip push skips it:
     *
     * <ol>
     *   <li><b>Preserve.</b> A squad already on {@code SECURE_COMPOUND} for a
     *       still-capturable compound keeps it (stability across replans) and
     *       fills one of that compound's slots — a squad mid-capture is by
     *       definition the "already adjacent" case.</li>
     *   <li><b>Adjacent commit.</b> Fill remaining slots with squads already
     *       in/adjacent to the compound. They have reached the objective, so
     *       they do not consume the distant-detachment allowance. This is the
     *       only way a fresh assignment enters a contested compound. No track
     *       bound applies here: a squad standing in the building has not been
     *       sent anywhere, and refusing it on a lateral coordinate would leave
     *       an objective it is already inside of unassaulted. What does apply
     *       is {@link #ADJACENT_COMMIT_CELLS}, because "already there" is a
     *       distance and an open compound's room is the whole outdoors.</li>
     *   <li><b>Uncontested distant fill.</b> Greedily assign nearest pairs up
     *       to the ordinary per-compound quotas, among compounds the front has
     *       reached or passed ({@link #frontHasReached}) and within
     *       {@link #captureTrackAllowed one track of home}. While an
     *       uncommitted squad can act on front resistance, fresh distant
     *       departures are globally bounded so at least one executable
     *       actionable squad remains on the front. With no actionable
     *       resistance the ordinary quotas apply.</li>
     * </ol>
     *
     * <p>The track bound is on the pairing rather than on the ranking, so the
     * nearest-pair order is unchanged among whatever survives it.
     */
    private void assignCompoundCaptures(List<PlanningSquad> squads,
                                        IntOpenHashSet committed,
                                        IntOpenHashSet deferredCaptures,
                                        IntOpenHashSet farTrackCaptures,
                                        Map<Integer, SquadDirective> directives,
                                        ConquestCommandFrame frame) {
        if (compoundTargets.isEmpty() || squads.isEmpty()) return;
        latchFrontReach(frame);

        int n = compoundTargets.size();
        int[] slots = new int[n];        // remaining capture slots per compound
        boolean[] contested = new boolean[n];
        for (int i = 0; i < n; i++) {
            CompoundTarget t = compoundTargets.get(i);
            if (t.state == CompoundService.CompoundState.MARINE_HELD) {
                slots[i] = 0;            // already ours — no detachment
                continue;
            }
            slots[i] = t.desiredSquads;
            contested[i] = isContested(t, frame);
        }

        // Phase 1: preserve in-flight captures even when today's front state
        // would not authorize starting the same order again.
        for (PlanningSquad squad : squads) {
            ObjectiveAssignment a = squad.assignedObjective;
            if (a == null || a.kind() != AssignmentKind.SECURE_COMPOUND) continue;
            int idx = targetIndexForCompound(a.targetNode());
            if (idx < 0 && a.targetNode() == null) {
                idx = targetIndexForCaptureZone(a.targetZoneId());
            }
            if (idx < 0 || slots[idx] <= 0) continue;
            if (!reachableZone(squad, compoundTargets.get(idx).captureZoneId, frame)) {
                continue;
            }
            slots[idx]--;
            commitCapture(squad, compoundTargets.get(idx), committed, directives,
                    AssignmentReason.COMPOUND_CAPTURE_PRESERVED);
        }

        // Phase 2: a squad that has physically reached a compound commits
        // before distant allocation, whether or not current belief sees a
        // defender there. Adjacent squads do not consume the distant cap.
        for (int i = 0; i < n; i++) {
            if (slots[i] <= 0) continue;
            CompoundTarget t = compoundTargets.get(i);
            for (PlanningSquad squad : squads) {
                if (slots[i] <= 0) break;
                if (committed.contains(squad.id)) continue;
                if (!squadAdjacentToCompound(squad, t, frame)) continue;
                if (!reachableZone(squad, t.captureZoneId, frame)) continue;
                commitCapture(squad, t, committed, directives,
                        AssignmentReason.COMPOUND_ASSAULT_ADJACENT);
                slots[i]--;
            }
        }

        IntOpenHashSet actionableFrontSquads = actionableFrontSquads(
                squads, committed, frame);
        int actionableRemaining = actionableFrontSquads.size();

        // Read once, from the slot state the preserve and adjacent passes
        // leave behind, rather than per candidate pair inside the greedy loop:
        // it is a per-squad property, and asking it there would run the zone
        // walk squads x compounds x rounds every pulse. A home compound that
        // somebody else takes during this loop simply frees the squad on the
        // next pulse, which is when its own track really has run out of work.
        IntOpenHashSet homeTrackWork = new IntOpenHashSet();
        if (HOME_TRACK_CAPTURES_ENABLED) {
            for (PlanningSquad squad : squads) {
                if (committed.contains(squad.id)) continue;
                if (homeTrackHasWork(squad, slots, contested, frame)) {
                    homeTrackWork.add(squad.id);
                }
            }
        }

        // Phase 3: greedy nearest-pair fill of uncontested compounds.
        // Non-actionable squads are preferred for capture. An actionable
        // squad may depart only while another executable actionable squad
        // remains, making the cap global across every compound and replan.
        while (true) {
            int bestSquad = -1, bestTarget = -1;
            int bestActionableRank = Integer.MAX_VALUE;
            float bestDist = Float.MAX_VALUE;
            for (PlanningSquad squad : squads) {
                if (committed.contains(squad.id)) continue;
                boolean actionable = actionableFrontSquads.contains(squad.id);
                if (actionable
                        && actionableRemaining <= MIN_FRONT_RESERVE_SQUADS) {
                    continue;
                }
                for (int i = 0; i < n; i++) {
                    if (slots[i] <= 0) continue;
                    if (contested[i] && !unattended(i, slots)) continue;
                    if (!eligibleForDistantCapture(compoundTargets.get(i))) continue;
                    if (!frontHasReached(compoundTargets.get(i))) continue;
                    if (!captureTrackAllowed(squad, i, homeTrackWork)) continue;
                    if (!reachableZone(squad, compoundTargets.get(i).captureZoneId,
                            frame)) continue;
                    float d = distSq(squad, compoundTargets.get(i));
                    int actionableRank = actionable ? 1 : 0;
                    if (actionableRank < bestActionableRank
                            || (actionableRank == bestActionableRank
                            && (d < bestDist || (d == bestDist
                            && (bestSquad < 0 || squad.id < bestSquad))))) {
                        bestActionableRank = actionableRank;
                        bestDist = d;
                        bestSquad = squad.id;
                        bestTarget = i;
                    }
                }
            }
            if (bestSquad < 0) break;
            commitCapture(squadById(squads, bestSquad),
                    compoundTargets.get(bestTarget), committed, directives,
                    AssignmentReason.COMPOUND_CAPTURE_UNCONTESTED);
            if (actionableFrontSquads.contains(bestSquad)) {
                actionableRemaining--;
            }
            slots[bestTarget]--;
        }

        // Explain squads that could have filled a still-open distant capture
        // slot but were retained because live front work exhausted the cap.
        if (actionableRemaining > 0
                && actionableRemaining <= MIN_FRONT_RESERVE_SQUADS) {
            for (PlanningSquad squad : squads) {
                if (committed.contains(squad.id)) continue;
                if (!actionableFrontSquads.contains(squad.id)) continue;
                for (int i = 0; i < n; i++) {
                    if (slots[i] <= 0) continue;
                    if (contested[i] && !unattended(i, slots)) continue;
                    CompoundTarget t = compoundTargets.get(i);
                    // Only a slot this squad could actually have filled counts
                    // as deferred. A compound the front has not reached, or one
                    // no distant detachment may be sent to at all, was never on
                    // offer, and reporting it as withheld for front resistance
                    // would misattribute the gate below.
                    if (!eligibleForDistantCapture(t)) continue;
                    if (!frontHasReached(t)) continue;
                    // Nor does a slot the track bound refused: that squad was
                    // not retained for front resistance, it was never offered
                    // the compound, and one flag saying both would explain
                    // neither.
                    if (!captureTrackAllowed(squad, i, homeTrackWork)) continue;
                    if (squadAdjacentToCompound(squad, t, frame)) continue;
                    if (!reachableZone(squad, t.captureZoneId, frame)) continue;
                    deferredCaptures.add(squad.id);
                    break;
                }
            }
        }

        // Explain the squad the track bound leaves with nothing: every slot it
        // could otherwise have filled is more than one track from home. Its
        // published reason would otherwise be the generic no-actionable-target
        // one, which reads identically to an empty map.
        if (!HOME_TRACK_CAPTURES_ENABLED) return;
        for (PlanningSquad squad : squads) {
            if (committed.contains(squad.id)) continue;
            if (deferredCaptures.contains(squad.id)) continue;
            for (int i = 0; i < n; i++) {
                if (slots[i] <= 0) continue;
                if (contested[i] && !unattended(i, slots)) continue;
                CompoundTarget t = compoundTargets.get(i);
                if (!eligibleForDistantCapture(t)) continue;
                if (!frontHasReached(t)) continue;
                if (tracksFromHome(squad, t) < 2) continue;
                if (!reachableZone(squad, t.captureZoneId, frame)) continue;
                farTrackCaptures.add(squad.id);
                break;
            }
        }
    }

    /**
     * Whether this squad may be paired with compound {@code index} by the
     * distant fill. Two tracks away is never a capture assignment — the design
     * says a squad may support <em>a neighbour</em>, and the far side of the
     * map is not one. One track away is support, and support waits: a squad
     * whose own track still has work takes its own track's compound or none.
     *
     * <p>A capture zone no track claims is left alone rather than refused. The
     * bound is a coordination preference and there is nothing here to prefer;
     * refusing on an unclassifiable coordinate would quietly strand the
     * compound instead.
     */
    private boolean captureTrackAllowed(PlanningSquad squad, int index,
                                        IntOpenHashSet homeTrackWork) {
        if (!HOME_TRACK_CAPTURES_ENABLED) return true;
        int away = tracksFromHome(squad, compoundTargets.get(index));
        if (away >= 2) return false;
        return away == 0 || !homeTrackWork.contains(squad.id);
    }

    /**
     * Tracks between the squad's sticky home and the compound's capture zone,
     * or {@code 0} when the capture zone falls in no track at all — see
     * {@link #captureTrackAllowed} for why an unclassifiable zone is treated
     * as home rather than as distant. Read through {@link #trackForZone} so
     * this and the {@code effectiveTrack} the directive publishes are the same
     * number.
     */
    private int tracksFromHome(PlanningSquad squad, CompoundTarget target) {
        int compoundTrack = trackForZone(target.captureZoneId);
        if (compoundTrack < 0) return 0;
        return Math.abs(compoundTrack - stripFor(squad));
    }

    /**
     * Whether the squad's own track still holds something worth doing: a
     * defender zone the front push would send it to, or an uncaptured compound
     * on that track it could take itself. Both halves are the questions the
     * allocation asks anyway — {@link #targetChoice}'s home leg, and phase 3's
     * own candidate filters — rather than a second private notion of "busy".
     *
     * <p>Deliberately not {@link #actionableFrontSquads}: that asks whether a
     * squad has front work <em>anywhere</em>, including a lane stage into an
     * adjacent track, and is the front reserve's question. This one is about
     * the squad's home track only, because that is the track a capture
     * detachment stops pushing.
     */
    private boolean homeTrackHasWork(PlanningSquad squad, int[] slots,
                                     boolean[] contested,
                                     ConquestCommandFrame frame) {
        int home = stripFor(squad);
        TargetChoice choice = targetChoice(squad, home, frame);
        if (choice.targetZoneId() >= 0 && choice.trackIndex() == home) {
            return true;
        }
        for (int i = 0; i < compoundTargets.size(); i++) {
            if (slots[i] <= 0) continue;
            if (contested[i] && !unattended(i, slots)) continue;
            CompoundTarget t = compoundTargets.get(i);
            if (trackForZone(t.captureZoneId) != home) continue;
            if (!frontHasReached(t)) continue;
            if (!reachableZone(squad, t.captureZoneId, frame)) continue;
            return true;
        }
        return false;
    }

    /**
     * Whether the front has come far enough along the compound's own track for
     * a distant detachment to be sending squads <em>with</em> the advance
     * rather than past it. True once the track's friendly lead has reached
     * within {@link #CAPTURE_FRONT_REACH_CELLS} of the capture cell, and
     * thereafter for everything the line has left behind.
     *
     * <p>This gate applies only to the distant fill. A squad already holding a
     * capture keeps it, and a squad standing at a compound commits to it,
     * whatever the front is doing — those two are about ground already won,
     * not about detaching somebody to walk at ground nobody has seen.
     *
     * <p>The lead is read from the compound's own track and its immediate
     * neighbours, because a squad one track over at the same depth is abreast
     * of the compound rather than somewhere else — the same neighbour-support
     * law the track partition already runs on. The intent of stopping there is
     * that an untouched flank should not become claimable merely because the
     * far side of the map advanced.
     *
     * <p><b>At the shipped three tracks that lateral rule is dormant, and this
     * is a depth test over the whole force.</b> A window of one track either
     * side spans every track there is, so the only way to fail the gate is for
     * no living marine squad anywhere to be within reach of the compound's
     * depth. Say so rather than describing a discrimination the code cannot
     * make: the rule would begin to bite at five tracks, and nothing
     * constructs that today.
     *
     * <p>Which leaves exactly one way for an uncaptured compound to starve
     * here — a front that stalls more than the reach short of it and never
     * closes. That is not a defect in the lateral reasoning and cannot be
     * fixed by widening it. It is the missing endgame distinction: a front
     * that has <em>finished</em> should release this gate, and a front that
     * has not yet <em>started</em> must not, and nothing here can currently
     * tell those apart.
     */
    /**
     * Whether nobody has been committed to this compound yet — every slot it
     * asked for is still open after the preserve and adjacent passes.
     *
     * <p>This is what re-opens a contested compound to distant allocation. The
     * exclusion exists so the commander does not feed squads piecemeal into a
     * defended place on top of the ones already going, and that reason
     * evaporates when nobody is going at all: a contested objective with no
     * squad on it is not being assaulted carefully, it is being ignored. A
     * battle was observed at thirteen compounds taken and one contested sitting
     * at zero percent, with fifty-nine squads and no reserve, because the only
     * path to a contested compound ran through squads that happened to be
     * standing beside it and none was.
     */
    private boolean unattended(int index, int[] slots) {
        return slots[index] >= compoundTargets.get(index).desiredSquads;
    }

    /**
     * Whether a distant detachment may be sent at this compound yet.
     *
     * <p><b>A compound on a lane answers with the chain, not with the
     * fraction.</b> A lane is taken in order, so the only place on it worth
     * detaching anybody to is the first the marines do not hold; everything
     * further up is behind a place still standing, and the forward-fraction
     * latch would have offered it the moment the line drew level with its
     * depth. That is the whole of "assault progress is ownership along the
     * chain" as the allocation sees it.
     *
     * <p>A compound on <em>no</em> lane — a settlement's supply hub, the
     * beachhead — keeps the latched depth reading. There is no ladder to place
     * it on, and refusing it outright would strand it.
     */
    private boolean frontHasReached(CompoundTarget t) {
        if (laneChainInForce() && laneChain.isOnChain(t.captureZoneId)) {
            return isChainFront(t.captureZoneId);
        }
        return frontReachedCaptureZones.contains(t.captureZoneId);
    }

    /**
     * Latches every compound the front now reaches. Evaluated once per pulse
     * for the whole set rather than lazily inside a candidate filter, because
     * the front's reach is a property of the battle rather than of whichever
     * allocation phase happened to ask. Lazy evaluation missed the case that
     * matters most: a compound taken by a squad already standing at it commits
     * in the adjacent phase, which never consults the gate, so the one
     * compound the front had provably arrived at was the one it never recorded.
     */
    private void latchFrontReach(ConquestCommandFrame frame) {
        for (CompoundTarget t : compoundTargets) {
            if (frontReachedCaptureZones.contains(t.captureZoneId)) continue;
            int track = trackLayout.trackForCell(t.captureCellX, t.captureCellY);
            if (track < 0 || track >= STRIP_COUNT) continue;
            int lead = NO_FRIENDLY_LEAD;
            for (int neighbour = track - 1; neighbour <= track + 1; neighbour++) {
                if (neighbour < 0 || neighbour >= STRIP_COUNT) continue;
                lead = Math.max(lead,
                        friendlyLeadForward(neighbour, NO_FRIENDLY_LEAD, frame));
            }
            if (lead == NO_FRIENDLY_LEAD) continue;
            int compoundForward = Math.round(trackLayout.forwardCoordinate(
                    t.captureCellX + 0.5f, t.captureCellY + 0.5f));
            if (compoundForward <= lead + CAPTURE_FRONT_REACH_CELLS) {
                frontReachedCaptureZones.add(t.captureZoneId);
            }
        }
    }

    private IntOpenHashSet actionableFrontSquads(List<PlanningSquad> squads,
                                                 IntOpenHashSet committed,
                                                 ConquestCommandFrame frame) {
        IntOpenHashSet actionable = new IntOpenHashSet();
        for (PlanningSquad squad : squads) {
            if (committed.contains(squad.id)) continue;
            if (squad.executionSuspension != null) continue;
            if (squad.localContact) {
                actionable.add(squad.id);
                continue;
            }
            int preferredTrack = stripFor(squad);
            // A scout advance into a track nobody has seen anything in is not
            // front resistance, and must not consume the front reserve that
            // holds squads back from distant captures. The reserve exists so
            // somebody stays on live work; staging blind is what a squad does
            // precisely when there is none.
            if (targetChoice(squad, preferredTrack, frame).targetZoneId >= 0
                    || (trackFront(squad, preferredTrack, frame).believed()
                    && laneStageChoice(squad, preferredTrack, frame) != null)) {
                actionable.add(squad.id);
            }
        }
        return actionable;
    }

    private void commitCapture(PlanningSquad squad, CompoundTarget t,
                               IntOpenHashSet committed,
                               Map<Integer, SquadDirective> directives,
                               AssignmentReason reason) {
        committed.add(squad.id);
        ObjectiveAssignment cur = squad.assignedObjective;
        if (cur == null
                || cur.kind() != AssignmentKind.SECURE_COMPOUND
                || cur.targetZoneId() != t.captureZoneId
                || !CommandFrameCopies.sameNodeIdentity(
                cur.targetNode(), t.node)) {
            squad.assignedObjective = ObjectiveAssignment.secureCompound(
                    squad.id, t.captureZoneId, t.node);
        }
        putCompoundDirective(squad, t, directives, reason);
    }

    /** True iff any of the compound's garrison rooms holds a live defender. The AABB-gated garrison-zone set excludes the open exterior, so a defender loitering in the street outside doesn't read as contesting the compound. */
    private boolean isContested(CompoundTarget t, ConquestCommandFrame frame) {
        CommanderInfluenceSnapshot influence = frame.influence();
        if (influence == null) return false;
        for (CommanderContact contact : influence.contacts()) {
            int zoneId = frame.topology().zoneIdAt(contact.cellX(), contact.cellY());
            if (containsZone(t.garrisonZones, zoneId)) return true;
        }
        return false;
    }

    /**
     * True iff the squad currently stands in, or immediately against, one of
     * the compound's garrison rooms — the "already there, commit the capture"
     * gate for contested compounds.
     *
     * <p>Bounded by {@link #ADJACENT_COMMIT_CELLS} whichever way it answers.
     * The zone test alone is a claim about being in the same <em>room</em>, and
     * an open compound's room is the outdoors; adjacency has to be a claim
     * about being in the same <em>place</em>, which is a distance.
     */
    private boolean squadAdjacentToCompound(PlanningSquad squad, CompoundTarget t,
                                            ConquestCommandFrame frame) {
        if (squad.anchorCellX < 0 || squad.anchorCellY < 0) return false;
        int outside = cellsOutsideCompound(squad, t);
        if (outside > ADJACENT_COMMIT_CELLS) return false;
        int cz = frame.topology().zoneIdAt(squad.anchorCellX, squad.anchorCellY);
        if (cz >= 0 && containsZone(t.garrisonZones, cz)) return true;
        return outside <= 1;
    }

    /**
     * Cells between the squad's anchor and the compound's authored footprint,
     * zero when it stands inside. Chebyshev, because the footprint is a
     * rectangle and a squad round its corner is as arrived as one at its wall.
     */
    private static int cellsOutsideCompound(PlanningSquad squad, CompoundTarget t) {
        int dx = Math.max(0, Math.max(t.node.compoundLeft() - squad.anchorCellX,
                squad.anchorCellX - t.node.compoundRight()));
        int dy = Math.max(0, Math.max(t.node.compoundTop() - squad.anchorCellY,
                squad.anchorCellY - t.node.compoundBottom()));
        return Math.max(dx, dy);
    }

    /**
     * Whether this compound may be handed to a squad that has to <em>walk</em>
     * to it. An open compound's capture room is the exterior flood, so
     * "converge on the capture zone" names ground the squad is already
     * standing on and the order can be held for a whole battle without ever
     * arriving. It remains capturable by a squad that actually reaches its
     * footprint — see {@link #squadAdjacentToCompound} — which is what taking
     * an airfield looks like anyway: the front arrives at it rather than
     * somebody being detached across the map for it.
     */
    private boolean eligibleForDistantCapture(CompoundTarget t) {
        return exteriorZoneId < 0 || t.captureZoneId != exteriorZoneId;
    }

    private int targetIndexForCaptureZone(int captureZoneId) {
        for (int i = 0; i < compoundTargets.size(); i++) {
            if (compoundTargets.get(i).captureZoneId == captureZoneId) return i;
        }
        return -1;
    }

    private int targetIndexForCompound(TacticalNode node) {
        if (node == null) return -1;
        for (int i = 0; i < compoundTargets.size(); i++) {
            if (CommandFrameCopies.sameNodeIdentity(
                    node, compoundTargets.get(i).node)) return i;
        }
        return -1;
    }

    private static float distSq(PlanningSquad squad, CompoundTarget t) {
        float dx = squad.centroidX - (t.node.anchorX + 0.5f);
        float dy = squad.centroidY - (t.node.anchorY + 0.5f);
        return dx * dx + dy * dy;
    }

    private static boolean containsZone(int[] zones, int zoneId) {
        for (int z : zones) if (z == zoneId) return true;
        return false;
    }

    private static PlanningSquad squadById(List<PlanningSquad> squads, int id) {
        for (PlanningSquad s : squads) if (s.id == id) return s;
        return null;
    }

    private CompoundTarget canonicalKeep() {
        CompoundTarget keep = null;
        for (CompoundTarget target : compoundTargets) {
            if (target.node.kind != TacticalNode.Kind.COMMAND_POST) continue;
            if (keep != null) return null;
            keep = target;
        }
        return keep;
    }

    private int remainingCompounds() {
        int remaining = 0;
        for (CompoundTarget target : compoundTargets) {
            if (target.state != CompoundService.CompoundState.MARINE_HELD) {
                remaining++;
            }
        }
        return remaining;
    }

    private CompoundTarget soleRemainingCompound() {
        CompoundTarget remaining = null;
        for (CompoundTarget target : compoundTargets) {
            if (target.state == CompoundService.CompoundState.MARINE_HELD) {
                continue;
            }
            if (remaining != null) return null;
            remaining = target;
        }
        return remaining;
    }

    private void putCompoundDirective(PlanningSquad squad, CompoundTarget target,
                                      Map<Integer, SquadDirective> directives,
                                      AssignmentReason reason) {
        int preferred = stripFor(squad);
        int effective = trackForZone(target.captureZoneId);
        directives.put(squad.id, directive(squad, preferred, effective, reason));
    }

    private SquadDirective directive(PlanningSquad squad, int preferredTrack,
                                     int effectiveTrack,
                                     AssignmentReason reason) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        int targetCellX = -1;
        int targetCellY = -1;
        int markerCellX = -1;
        int markerCellY = -1;
        if (assignment != null) {
            targetCellX = assignment.targetCellX();
            targetCellY = assignment.targetCellY();
            markerCellX = targetCellX;
            markerCellY = targetCellY;
            if (markerCellX < 0
                    && assignment.kind() == AssignmentKind.SECURE_COMPOUND) {
                CompoundTarget target = compoundTarget(
                        assignment.targetNode(), assignment.targetZoneId());
                if (target != null) {
                    markerCellX = target.captureCellX;
                    markerCellY = target.captureCellY;
                }
            }
            if (markerCellX < 0 && assignment.targetNode() != null) {
                markerCellX = assignment.targetNode().anchorX;
                markerCellY = assignment.targetNode().anchorY;
            } else if (markerCellX < 0 && assignment.targetZoneId() >= 0
                    && zoneMarkerX != null
                    && assignment.targetZoneId() < zoneMarkerX.length) {
                markerCellX = zoneMarkerX[assignment.targetZoneId()];
                markerCellY = zoneMarkerY[assignment.targetZoneId()];
            }
        }
        SquadDirective published = new SquadDirective(squad.id, preferredTrack,
                effectiveTrack, reason,
                assignment != null ? assignment.kind() : null,
                assignment != null ? assignment.targetZoneId() : -1,
                targetCellX, targetCellY, markerCellX, markerCellY);
        int[] chainTarget = chainTargetOf(assignment, effectiveTrack);
        return chainTarget == null ? published
                : published.withChainTarget(chainTarget[0], chainTarget[1]);
    }

    /**
     * Which place on which lane an order is about, or {@code null} for one
     * about no place at all.
     *
     * <p>A capture names its own compound, so it is keyed to the place that
     * compound stands on. A staging or attack order names ground rather than a
     * building, and the place it is about is its lane's front — which is the
     * thing the squad is being walked toward. A report keyed only by track can
     * say a squad was working in the middle third of the map and not what it
     * was sent to take.
     */
    private int[] chainTargetOf(ObjectiveAssignment assignment, int effectiveTrack) {
        if (!laneChainInForce() || assignment == null) return null;
        if (assignment.kind() == AssignmentKind.SECURE_COMPOUND) {
            int zone = assignment.targetZoneId();
            // The squad's own lane first, because the objective is the last
            // link of every chain and a lane-2 squad taking the keep is taking
            // its own lane's last place, not lane 0's.
            if (effectiveTrack >= 0 && effectiveTrack < laneChain.laneCount()) {
                int own = laneChain.linkIndexOn(effectiveTrack, zone);
                if (own >= 0) return new int[]{effectiveTrack, own};
            }
            for (int lane = 0; lane < laneChain.laneCount(); lane++) {
                int index = laneChain.linkIndexOn(lane, zone);
                if (index >= 0) return new int[]{lane, index};
            }
            return null;
        }
        if (effectiveTrack < 0 || effectiveTrack >= laneChain.laneCount()) return null;
        int front = chainFront[effectiveTrack];
        if (front < 0 || front >= laneChain.links(effectiveTrack).size()) return null;
        return new int[]{effectiveTrack, front};
    }

    /**
     * Lazy strip partition. Equal-width along the lateral axis (x for
     * SOUTH_TO_NORTH push, y for WEST_TO_EAST push); each zone is bucketed
     * by its centroid's lateral coordinate. Within each strip, zones are
     * sorted forward-to-back so {@link #nearestDefenderZoneInStrip} can short-
     * circuit on the first defender-occupied entry.
     */
    private void initializePartition(ConquestCommandFrame frame) {
        CommandTopology topology = frame.topology();
        if (trackLayout == null) {
            trackLayout = new ConquestTrackLayout(axis,
                    topology.width(), topology.height());
        }
        int gridW = topology.width();
        this.lateralExtent = trackLayout.lateralExtent();

        stripZones = new ArrayList<>(STRIP_COUNT);
        for (int i = 0; i < STRIP_COUNT; i++) stripZones.add(new ArrayList<>());
        zoneForwardCoord = new float[topology.zones().size()];
        zoneLateralCoord = new float[topology.zones().size()];
        zoneCentroidX = new float[topology.zones().size()];
        zoneCentroidY = new float[topology.zones().size()];
        zoneMarkerX = new int[topology.zones().size()];
        zoneMarkerY = new int[topology.zones().size()];
        zoneTargetSquads = new int[topology.zones().size()];
        Arrays.fill(zoneMarkerX, -1);
        Arrays.fill(zoneMarkerY, -1);
        Arrays.fill(zoneForwardCoord, 0f);

        int largestCells = -1, secondCells = -1, largestZone = -1;
        for (CommandTopology.Zone zone : topology.zones()) {
            int[] cells = zone.cells();
            if (cells.length == 0) continue;
            if (cells.length > largestCells) {
                secondCells = largestCells;
                largestCells = cells.length;
                largestZone = zone.id();
            } else if (cells.length > secondCells) {
                secondCells = cells.length;
            }
            float sumX = 0f, sumY = 0f;
            for (int cellIdx : cells) {
                sumX += (cellIdx % gridW);
                sumY += (cellIdx / gridW);
            }
            float cx = sumX / cells.length;
            float cy = sumY / cells.length;
            float lateral = (axis == TraversalAxis.SOUTH_TO_NORTH) ? cx : cy;
            float forward = (axis == TraversalAxis.SOUTH_TO_NORTH) ? cy : cx;
            if (zone.id() >= 0 && zone.id() < zoneForwardCoord.length) {
                zoneForwardCoord[zone.id()] = forward;
                zoneLateralCoord[zone.id()] = lateral;
                zoneCentroidX[zone.id()] = cx;
                zoneCentroidY[zone.id()] = cy;
                int marker = nearestCell(cells, gridW, cx, cy);
                zoneMarkerX[zone.id()] = marker % gridW;
                zoneMarkerY[zone.id()] = marker / gridW;
            }

            int stripIdx = stripIndexForLateral(lateral);
            if (stripIdx < 0 || stripIdx >= STRIP_COUNT) continue;
            stripZones.get(stripIdx).add(zone.id());
        }

        // Only flag an exterior when one zone dominates — the real outdoor
        // flood does; a map of comparable rooms has nothing to exclude.
        if (largestZone >= 0
                && (secondCells <= 0 || largestCells >= EXTERIOR_DOMINANCE_RATIO * secondCells)) {
            exteriorZoneId = largestZone;
        }

        Comparator<Integer> forwardDescending = (a, b) ->
                Float.compare(zoneForwardCoord[b], zoneForwardCoord[a]);
        for (List<Integer> strip : stripZones) {
            strip.sort(forwardDescending);
        }

    }

    private static int nearestCell(int[] cells, int gridWidth,
                                   float centerX, float centerY) {
        int best = cells[0];
        float bestDistance = Float.MAX_VALUE;
        for (int cell : cells) {
            float dx = cell % gridWidth - centerX;
            float dy = cell / gridWidth - centerY;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance
                    || (distance == bestDistance && cell < best)) {
                best = cell;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void refreshCompoundTargets(ConquestCommandFrame frame) {
        compoundTargets.clear();
        for (ConquestCommandFacts.Compound fact : frame.facts().compounds()) {
            int captureZone = fact.captureZoneId();
            if (captureZone < 0) continue;
            int[] garrisonZones = fact.garrisonZoneIds();
            if (garrisonZones.length == 0) garrisonZones = new int[]{captureZone};
            int desiredSquads = garrisonZones.length >= LARGE_COMPOUND_ROOMS ? 2 : 1;
            compoundTargets.add(new CompoundTarget(fact.state(), fact.node(),
                    fact.captureCellX(), fact.captureCellY(), captureZone,
                    garrisonZones, desiredSquads));
        }
        compoundTargets.sort(Comparator
                .comparingInt((CompoundTarget target) -> target.captureZoneId)
                .thenComparingInt(target -> target.node.anchorX)
                .thenComparingInt(target -> target.node.anchorY));
        readLaneChain();
    }

    /**
     * Reads the lanes as chains once, then re-reads each lane's front.
     *
     * <p>The chain itself is topology — which compound stands on which place —
     * and is settled the first time compounds are disclosed. The front is
     * ownership and moves, in both directions, so it is read every pulse from
     * the same frozen compound states the rest of the plan runs on.
     */
    private void readLaneChain() {
        if (compoundTargets.isEmpty()) {
            // No compounds disclosed is not "the front is where it was": clear
            // the pulse-local reading rather than steer off a frozen one.
            Arrays.fill(chainFront, 0);
            Arrays.fill(chainLinks, 0);
            Arrays.fill(chainHeld, 0);
            chainFrontZones.clear();
            return;
        }
        if (!laneChainRead && !laneRoutes.isEmpty()) {
            List<ConquestLaneChain.Compound> compounds =
                    new ArrayList<>(compoundTargets.size());
            for (CompoundTarget target : compoundTargets) {
                compounds.add(new ConquestLaneChain.Compound(target.captureZoneId,
                        target.node.anchorX, target.node.anchorY));
            }
            laneChain = ConquestLaneChain.of(laneRoutes, compounds);
            chainFront = new int[laneChain.laneCount()];
            chainLinks = new int[laneChain.laneCount()];
            chainHeld = new int[laneChain.laneCount()];
        }
        laneChainRead = true;
        if (laneChain.laneCount() == 0) return;
        marineHeldZones.clear();
        for (CompoundTarget target : compoundTargets) {
            if (target.state == CompoundService.CompoundState.MARINE_HELD) {
                marineHeldZones.add(target.captureZoneId);
            }
        }
        for (int lane = 0; lane < laneChain.laneCount(); lane++) {
            chainFront[lane] = laneChain.frontLink(lane, marineHeldZones::contains);
            int links = 0;
            int held = 0;
            for (ConquestLaneChain.Link link : laneChain.links(lane)) {
                if (!link.hasCompounds()) continue;
                links++;
                if (link.isHeld(marineHeldZones::contains)) held++;
            }
            chainLinks[lane] = links;
            chainHeld[lane] = held;
        }
        chainFrontZones.clear();
        for (int lane = 0; lane < laneChain.laneCount(); lane++) {
            List<ConquestLaneChain.Link> links = laneChain.links(lane);
            int front = chainFront[lane];
            if (front < 0 || front >= links.size()) continue;
            for (int zone : links.get(front).captureZoneIds()) {
                chainFrontZones.add(zone);
            }
        }
    }

    /** Whether the chain is the reading in force for this battle. */
    private boolean laneChainInForce() {
        return LANE_CHAIN_ENABLED && laneChain.laneCount() > 0
                && laneChain.hasChains();
    }

    /**
     * Whether this compound is the next place to take on a lane it stands on.
     *
     * <p>Asked of every lane rather than of one, because the objective stands
     * on all of them: the fortress is the last link of three chains, and
     * whichever finishes first is entitled to go for it.
     */
    private boolean isChainFront(int captureZoneId) {
        return chainFrontZones.contains(captureZoneId);
    }

    /**
     * Lateral coordinate {@code lateral} → strip index in {@code [0, STRIP_COUNT)}.
     * Equal-width buckets across the full lateral extent. The {@code Math.min}
     * clamp catches the right-edge boundary (a coord exactly at {@code lateralExtent}
     * would land in bucket {@code STRIP_COUNT}, which doesn't exist).
     */
    private int stripIndexForLateral(float lateral) {
        return trackLayout.trackForLateral(lateral);
    }

    /**
     * Squad → strip index. Sticky on first observation, looked up thereafter.
     * Returns the strip the squad's centroid currently falls in for the first
     * call, which is then memoized; lateral drift after first observation
     * doesn't move the squad to a new preferred track. Temporary support in
     * an adjacent effective track does not rewrite this sticky identity.
     */
    private int stripFor(PlanningSquad squad) {
        int cached = squadStripIdx.get(squad.id);
        if (cached >= 0) return cached;
        float lateral = (axis == TraversalAxis.SOUTH_TO_NORTH) ? squad.centroidX : squad.centroidY;
        int idx = stripIndexForLateral(lateral);
        if (idx < 0) idx = 0;
        if (idx >= STRIP_COUNT) idx = STRIP_COUNT - 1;
        squadStripIdx.put(squad.id, idx);
        return idx;
    }

    private int trackForZone(int zoneId) {
        if (zoneId < 0 || zoneId >= zoneLateralCoord.length) return -1;
        return stripIndexForLateral(zoneLateralCoord[zoneId]);
    }

    private record TargetChoice(int trackIndex, int targetZoneId) { }
    private record TrackStage(int trackIndex, int cellX, int cellY) { }

    /**
     * What this side believes about hostiles in one track, read for one squad.
     *
     * @param believed whether any contact at all is placed in the track
     * @param nearestHostileForward forward coordinate of the nearest contact
     *        within {@link #TRACK_LINE_STANDOFF_LATERAL_CELLS} of the squad's
     *        own line of advance, or {@link Integer#MAX_VALUE} when the track
     *        is believed but nothing stands in this squad's corridor
     */
    private record TrackFront(boolean believed, int nearestHostileForward) { }

    /**
     * Gives a contact-free rear squad an own-force destination behind its
     * preferred track's believed hostile frontier. Specific zone work is
     * selected before this fallback; local contact also suppresses it so the
     * tactical engagement planner owns the squad without a competing marker.
     *
     * <p>The frontier is read only from contacts within
     * {@link #TRACK_LINE_STANDOFF_LATERAL_CELLS} of the squad's own line of
     * advance. A contact at the far lateral edge of the same track is not in
     * front of this squad and must not set its standoff.
     *
     * <p>A believed front in the track with nothing in that corridor drops the
     * standoff bound rather than the stage: the squad knows where the lane is
     * contested and simply is not the one facing it, so the friendly line and
     * the stride cap size its step.
     *
     * <p><b>A track with no belief at all stages too, bounded by its
     * neighbours' lead.</b> Declining to was a deadlock rather than caution: a
     * track nobody advances through is a track nobody sights anything in, so
     * the belief that would authorize the advance can only be produced by the
     * advance itself. Observed as ten squads and ninety-eight marines standing
     * still for a whole battle in the one lateral band that happened to hold
     * no defenders, never firing a shot, while the commander correctly
     * reported {@code NO_ACTIONABLE_TRACK_TARGET} at every pulse. The
     * compound-capture path cannot relieve it either, because
     * {@link #frontHasReached} gates distant detachments on a front that this
     * same stall is what stops moving.
     *
     * <p>The objection the old rule was making is still right, and is now
     * answered by the bound instead of by refusal: an own-force push on no
     * intelligence must not become a lone flank out ahead of everybody. So an
     * unscouted track may lead {@link #neighbourLeadForward} by at most
     * {@link #TRACK_LINE_LEAD_CELLS}, which advances the whole line abreast
     * and pins a track that has run ahead until the rest come up.
     */
    private TrackStage laneStageChoice(PlanningSquad squad, int track,
                                       ConquestCommandFrame frame) {
        if (squad.localContact) return null;
        return laneForwardChoice(squad, track, frame, false);
    }

    /**
     * The in-contact half of the same lane derivation. A squad already fighting
     * on its track used to receive nothing at all — the stage refuses local
     * contact, so the pulse fell through to NO_ACTIONABLE_TRACK_TARGET and the
     * squad kept only whatever the engagement goals decided for themselves.
     * That is the single largest source of command-unassigned pulses in the
     * Conquest evidence, and it is not idleness: it is the commander declining
     * to say anything to the squads doing the actual fighting.
     *
     * <p>An attack move is what it should have been saying. The destination is
     * derived the same way, minus the standoff bound — a squad ordered to clear
     * forward is not staging behind the frontier it is being sent through.
     */
    private TrackStage laneAttackChoice(PlanningSquad squad, int track,
                                        ConquestCommandFrame frame) {
        if (!squad.localContact) return null;
        return laneForwardChoice(squad, track, frame, true);
    }

    private TrackStage laneForwardChoice(PlanningSquad squad, int track,
                                         ConquestCommandFrame frame,
                                         boolean attacking) {
        if (track < 0 || track >= STRIP_COUNT) return null;
        if (frame.influence() == null) return null;

        int squadLateral = Math.round(trackLayout.lateralCoordinate(
                squad.centroidX, squad.centroidY));
        TrackFront front = trackFront(squad, track, frame);
        // The relaxation below is for the squad that has nothing: no belief in
        // its track and no contact of its own. A squad in contact is not stuck
        // — the tactical layer has a real target for it — and an exterior
        // contact the commander has not yet placed must stay ambient rather
        // than becoming a fabricated forward order.
        //
        // The gate stands in front of the route derivation as well: a road to
        // the next place is a better line to walk than the axis, and it is not
        // a reason to issue an order the commander has decided not to issue.
        if (!front.believed()
                && (attacking || !EMPTY_TRACK_ADVANCE_ENABLED)) return null;

        TrackStage onRoute = laneRouteChoice(squad, track, frame, attacking);
        if (onRoute != null) return onRoute;

        int squadForward = Math.round(trackLayout.forwardCoordinate(
                squad.centroidX, squad.centroidY));
        int friendlyLead = friendlyLeadForward(track, squadForward, frame);
        int safeFront = attacking
                || front.nearestHostileForward() == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : front.nearestHostileForward() - TRACK_LINE_STANDOFF_CELLS;
        int supportedFront = friendlyLead + TRACK_LINE_LEAD_CELLS;
        if (!front.believed()) {
            int neighbourLead = neighbourLeadForward(track, frame);
            if (neighbourLead != NO_FRIENDLY_LEAD) {
                supportedFront = Math.min(supportedFront,
                        neighbourLead + TRACK_LINE_LEAD_CELLS);
            }
        }
        int strideFront = squadForward + TRACK_LINE_MAX_STRIDE_CELLS;
        int desiredForward = Math.min(safeFront,
                Math.min(supportedFront, strideFront));
        desiredForward = Math.max(0, Math.min(trackLayout.forwardExtent() - 1,
                desiredForward));
        desiredForward = desiredForward / TRACK_LINE_BAND_CELLS
                * TRACK_LINE_BAND_CELLS;
        if (desiredForward < squadForward + TRACK_LINE_MIN_ADVANCE_CELLS) {
            return null;
        }

        int lateral = Math.max(trackLayout.lateralStartInclusive(track),
                Math.min(trackLayout.lateralEndInclusive(track), squadLateral));
        // The loss-avoiding slide is built and tested but not wired in. A
        // control run at the commit before it returned TERMINAL on both
        // canonical fixtures and it returned TIMEOUT on both, so it costs the
        // attacker its advance somewhere this measurement does not localize —
        // most likely by moving the staging marker often enough that squads
        // spend their time restaging. Left switched off rather than shipped on
        // the strength of the idea: see awayFromOwnLosses.
        return reachableTrackStage(squad, track, lateral, desiredForward, frame);
    }

    /**
     * The same staging decision, taken along the road the map recorded.
     *
     * <p><b>The bounds are the axis version's, measured on the route rather
     * than on the map.</b> A squad may not stage past the next place on its
     * lane, may not lead the friendly line on that lane by more than
     * {@link #TRACK_LINE_LEAD_CELLS}, may not stride further than
     * {@link #TRACK_LINE_MAX_STRIDE_CELLS} in one order, must keep
     * {@link #TRACK_LINE_STANDOFF_CELLS} behind the nearest believed hostile
     * standing on the road ahead of it, and may not be pulled backwards. Each
     * of those used to be a forward coordinate; on a grown map the road between
     * two places is wherever interconnect put it, so a bound measured up the
     * map is a bound on a line the ground does not follow.
     *
     * <p>The destination is a cell <em>of the route</em>, walked back from the
     * one the bounds chose until one is walkable and reachable — so a stage is
     * on the road by construction rather than snapped toward it, which is the
     * acceptance this layer owes.
     *
     * <p>{@code null} when the lane has no route, no place left to take, or the
     * squad already stands at or past the next one. The axis derivation then
     * runs, which is also what the whole of a lane-less map gets.
     */
    private TrackStage laneRouteChoice(PlanningSquad squad, int track,
                                       ConquestCommandFrame frame,
                                       boolean attacking) {
        if (!laneChainInForce()) return null;
        if (track < 0 || track >= laneChain.laneCount()) return null;
        List<ConquestLaneChain.Link> links = laneChain.links(track);
        int front = chainFront[track];
        if (front < 0 || front >= links.size()) return null;
        int length = laneChain.routeLength(track);
        if (length <= 0) return null;
        int at = laneChain.routeIndexNear(track, squad.centroidX, squad.centroidY);
        if (at < 0) return null;
        int goal = Math.min(links.get(front).routeIndex(), length - 1);
        if (goal <= at) return null;

        int desired = Math.min(goal, at + TRACK_LINE_MAX_STRIDE_CELLS);
        desired = Math.min(desired, routeLead[track] + TRACK_LINE_LEAD_CELLS);
        if (!attacking) {
            int hostile = nearestHostileOnRoute(track, at);
            if (hostile != Integer.MAX_VALUE) {
                desired = Math.min(desired, hostile - TRACK_LINE_STANDOFF_CELLS);
            }
        }
        desired = desired / TRACK_LINE_BAND_CELLS * TRACK_LINE_BAND_CELLS;
        if (desired < at + TRACK_LINE_MIN_ADVANCE_CELLS) return null;

        int floor = Math.max(at + TRACK_LINE_MIN_ADVANCE_CELLS,
                desired - TRACK_LINE_SNAP_RADIUS);
        CommandTopology topology = frame.topology();
        if (!topology.inBounds(squad.anchorCellX, squad.anchorCellY)
                || !topology.isWalkable(squad.anchorCellX, squad.anchorCellY)) {
            return null;
        }
        for (int index = desired; index >= floor; index--) {
            LaneRoute.Cell cell = laneChain.routeCell(track, index);
            if (cell == null) continue;
            if (!topology.inBounds(cell.x(), cell.y())
                    || !topology.isWalkable(cell.x(), cell.y())) continue;
            boolean atTarget = squad.anchorCellX == cell.x()
                    && squad.anchorCellY == cell.y();
            if (!atTarget && !topology.reachable(squad.anchorCellX,
                    squad.anchorCellY, cell.x(), cell.y())) continue;
            return new TrackStage(track, cell.x(), cell.y());
        }
        return null;
    }

    /**
     * The nearest believed hostile standing on this lane's road ahead of a
     * squad at route index {@code at}, or {@link Integer#MAX_VALUE} for a road
     * with nobody on it.
     *
     * <p>Contacts are placed on the route once per pulse rather than once per
     * squad, and only those actually near the road are placed at all — a
     * contact off in the fields is not in front of anybody walking it. That is
     * the same reasoning as the corridor bound the axis derivation uses, asked
     * of the road instead of of a lateral band.
     */
    private int nearestHostileOnRoute(int track, int at) {
        int[] contacts = routeContacts[track];
        for (int index : contacts) {
            if (index > at) return index;
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Places this pulse's own squads and believed contacts on each lane's road.
     *
     * <p>Once per plan, not once per squad: finding where a body stands along a
     * route is a walk of the whole route, and asking it inside the per-squad
     * derivation would run that walk squads times contacts times lanes every
     * pulse for an answer that does not change within the pulse.
     */
    private void prepareRouteFrame(ConquestCommandFrame frame) {
        int lanes = laneChain.laneCount();
        if (!laneChainInForce()) {
            routeLead = new int[0];
            routeContacts = new int[0][];
            return;
        }
        routeLead = new int[lanes];
        routeContacts = new int[lanes][];
        Arrays.fill(routeLead, 0);
        List<List<Integer>> contacts = new ArrayList<>(lanes);
        for (int lane = 0; lane < lanes; lane++) contacts.add(new ArrayList<>());

        for (CommandSquadState other : frame.squads()) {
            if (other.aliveMembers() <= 0 || other.role() == UnitRole.GARRISON) continue;
            int lane = trackLayout.trackForLateral(trackLayout.lateralCoordinate(
                    other.centroidX(), other.centroidY()));
            if (lane < 0 || lane >= lanes) continue;
            // Bounded to the road for the same reason a contact is: a squad
            // off in the fields is not part of the line on this lane, and
            // taking its nearest route cell anyway would let one stray body
            // unlock a stage for everybody behind it.
            int index = laneChain.routeIndexWithin(lane, other.centroidX(),
                    other.centroidY(), TRACK_LINE_STANDOFF_LATERAL_CELLS);
            if (index > routeLead[lane]) routeLead[lane] = index;
        }
        CommanderInfluenceSnapshot influence = frame.influence();
        if (influence != null) {
            for (CommanderContact contact : influence.contacts()) {
                int lane = trackLayout.trackForCell(contact.cellX(), contact.cellY());
                if (lane < 0 || lane >= lanes) continue;
                int index = laneChain.routeIndexWithin(lane, contact.cellX() + 0.5f,
                        contact.cellY() + 0.5f, TRACK_LINE_STANDOFF_LATERAL_CELLS);
                if (index >= 0) contacts.get(lane).add(index);
            }
        }
        for (int lane = 0; lane < lanes; lane++) {
            List<Integer> found = contacts.get(lane);
            int[] out = new int[found.size()];
            for (int i = 0; i < out.length; i++) out[i] = found.get(i);
            Arrays.sort(out);
            routeContacts[lane] = out;
        }
    }

    /**
     * Slides the staging lateral toward the part of the track this side has not
     * recently lost people in.
     *
     * <p><b>This is the command layer's whole approach avoidance, and its limits
     * are the point.</b> It does not re-route a squad already moving and it
     * cannot invent a way around a lane that has only one — it moves the place
     * the <em>next</em> squad is told to go, within the track it was going to
     * use anyway. A commander choosing where to put people is the layer that
     * can act on "the last squad died there"; the squad itself is already
     * committed by the time it finds out.
     *
     * <p>The candidates are stepped at the loss field's own block size, because
     * a finer step samples the same block repeatedly and pretends to a precision
     * the memory does not have. Ties keep the lateral the squad would have used,
     * so a battle with no losses stages exactly where it did before, and the
     * slide is bounded to the track: avoiding a killing ground is not a licence
     * to abandon the lane.
     */
    private int awayFromOwnLosses(int track, int preferredLateral, int forward,
                                  ConquestCommandFrame frame) {
        CommanderInfluenceSnapshot influence = frame.influence();
        if (influence == null) return preferredLateral;
        int start = trackLayout.lateralStartInclusive(track);
        int end = trackLayout.lateralEndInclusive(track);
        int step = Math.max(1, influence.blockSize());
        int bestLateral = preferredLateral;
        float bestLosses = lossesAtTrackCell(influence, preferredLateral, forward);
        if (bestLosses <= 0f) return preferredLateral;
        for (int lateral = start; lateral <= end; lateral += step) {
            float losses = lossesAtTrackCell(influence, lateral, forward);
            if (losses < bestLosses
                    || (losses == bestLosses
                    && Math.abs(lateral - preferredLateral)
                    < Math.abs(bestLateral - preferredLateral))) {
                bestLosses = losses;
                bestLateral = lateral;
            }
        }
        return bestLateral;
    }

    private float lossesAtTrackCell(CommanderInfluenceSnapshot influence,
                                    int lateral, int forward) {
        return influence.lossesAtWorld(trackLayout.cellX(lateral, forward),
                trackLayout.cellY(lateral, forward));
    }

    /**
     * Reads one track's believed hostile front from the squad's own corridor.
     * A contact at the far lateral edge of the same track is not in front of
     * this squad and must not set its standoff, but it does still make the
     * track believed: somebody is there, and this squad simply is not the one
     * facing them.
     */
    private TrackFront trackFront(PlanningSquad squad, int track,
                                  ConquestCommandFrame frame) {
        CommanderInfluenceSnapshot influence = frame.influence();
        if (influence == null) return new TrackFront(false, Integer.MAX_VALUE);
        int squadLateral = Math.round(trackLayout.lateralCoordinate(
                squad.centroidX, squad.centroidY));
        boolean believed = false;
        int nearest = Integer.MAX_VALUE;
        for (CommanderContact contact : influence.contacts()) {
            if (trackLayout.trackForCell(contact.cellX(), contact.cellY())
                    != track) continue;
            believed = true;
            int contactLateral = Math.round(trackLayout.lateralCoordinate(
                    contact.cellX(), contact.cellY()));
            if (Math.abs(contactLateral - squadLateral)
                    > TRACK_LINE_STANDOFF_LATERAL_CELLS) continue;
            nearest = Math.min(nearest, Math.round(
                    trackLayout.forwardCoordinate(
                            contact.cellX(), contact.cellY())));
        }
        return new TrackFront(believed, nearest);
    }

    /**
     * Forward lead of the most advanced living mobile squad in either track
     * beside this one, or {@link #NO_FRIENDLY_LEAD} when neither holds
     * anybody. This is the line an unscouted track dresses on: the same
     * neighbour-support law {@link #latchFrontReach} reads the front's reach
     * through, and the bound that keeps a blind advance from becoming a lone
     * flank walking off ahead of the force that would have to support it.
     */
    private int neighbourLeadForward(int track, ConquestCommandFrame frame) {
        int lead = NO_FRIENDLY_LEAD;
        for (int neighbour = track - 1; neighbour <= track + 1; neighbour++) {
            if (neighbour == track) continue;
            if (neighbour < 0 || neighbour >= STRIP_COUNT) continue;
            lead = Math.max(lead,
                    friendlyLeadForward(neighbour, NO_FRIENDLY_LEAD, frame));
        }
        return lead;
    }

    private int friendlyLeadForward(int track, int fallback,
                                    ConquestCommandFrame frame) {
        int lead = fallback;
        for (CommandSquadState other : frame.squads()) {
            if (other.aliveMembers() <= 0 || other.role() == UnitRole.GARRISON) continue;
            int physicalTrack = trackLayout.trackForLateral(
                    trackLayout.lateralCoordinate(other.centroidX(), other.centroidY()));
            if (physicalTrack != track) continue;
            lead = Math.max(lead, Math.round(trackLayout.forwardCoordinate(
                    other.centroidX(), other.centroidY())));
        }
        return lead;
    }

    /** Searches only at or behind the safe line and fails closed. */
    private TrackStage reachableTrackStage(PlanningSquad squad, int track,
                                           int desiredLateral, int desiredForward,
                                           ConquestCommandFrame frame) {
        CommandTopology topology = frame.topology();
        for (int radius = 0; radius <= TRACK_LINE_SNAP_RADIUS; radius++) {
            for (int rear = 0; rear <= radius; rear++) {
                int lateralDelta = radius - rear;
                int forward = desiredForward - rear;
                TrackStage left = validTrackStage(squad, track,
                        desiredLateral - lateralDelta, forward, topology);
                if (lateralDelta == 0) {
                    if (left != null) return left;
                    continue;
                }
                TrackStage right = validTrackStage(squad, track,
                        desiredLateral + lateralDelta, forward, topology);
                // Both sides of the line are the same distance from where the
                // squad was going to stage, so distance cannot choose between
                // them and the search used to take the left one every time.
                // Recent own losses can choose: send the next squad up the side
                // that did not just cost us a squad.
                TrackStage chosen = cheaperByLosses(left, right, frame);
                if (chosen != null) return chosen;
            }
        }
        return null;
    }

    /**
     * Picks between two equidistant staging cells by what the side remembers
     * losing near each. Ties keep the left-hand candidate, which is the order
     * the search has always used, so a battle with no losses anywhere stages
     * exactly where it did before.
     *
     * <p>This is the whole of the command layer's approach avoidance: it does
     * not re-route a squad already moving, and it cannot invent a way around a
     * lane that has only one. It chooses, among places equally good by every
     * other measure, the one that has not just been paid for.
     */
    private TrackStage cheaperByLosses(TrackStage left, TrackStage right,
                                       ConquestCommandFrame frame) {
        if (left == null) return right;
        if (right == null) return left;
        CommanderInfluenceSnapshot influence = frame.influence();
        if (influence == null) return left;
        float leftLosses = influence.lossesAtWorld(left.cellX(), left.cellY());
        float rightLosses = influence.lossesAtWorld(right.cellX(), right.cellY());
        return rightLosses < leftLosses ? right : left;
    }

    private TrackStage validTrackStage(PlanningSquad squad, int track,
                                       int lateral, int forward,
                                       CommandTopology topology) {
        if (!topology.inBounds(squad.anchorCellX, squad.anchorCellY)
                || !topology.isWalkable(
                squad.anchorCellX, squad.anchorCellY)) return null;
        int squadForward = Math.round(trackLayout.forwardCoordinate(
                squad.centroidX, squad.centroidY));
        if (forward < squadForward + TRACK_LINE_MIN_ADVANCE_CELLS) return null;
        int x = trackLayout.cellX(lateral, forward);
        int y = trackLayout.cellY(lateral, forward);
        if (!topology.inBounds(x, y) || !topology.isWalkable(x, y)
                || trackLayout.trackForCell(x, y) != track) return null;
        boolean atTarget = squad.anchorCellX == x && squad.anchorCellY == y;
        if (!atTarget && !topology.reachable(
                squad.anchorCellX, squad.anchorCellY, x, y)) return null;
        return new TrackStage(track, x, y);
    }

    /**
     * When a recaptured non-keep compound is the sole territorial objective,
     * capture quota still owns SECURE_COMPOUND. Remaining mobile squads may
     * support the assault across any empty track by clearing an occupied,
     * reachable room in that compound's authored footprint.
     */
    private TargetChoice finalCompoundSupportChoice(PlanningSquad squad,
                                                     CompoundTarget target,
                                                     ConquestCommandFrame frame) {
        int bestZone = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int zoneId : target.garrisonZones) {
            if (!hasKnownHostileInZone(zoneId, frame)) continue;
            if (!reachableZone(squad, zoneId, frame)) {
                continue;
            }
            float dx = squad.centroidX - zoneCentroidX[zoneId];
            float dy = squad.centroidY - zoneCentroidY[zoneId];
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance
                    || (distance == bestDistance && zoneId < bestZone)) {
                bestDistance = distance;
                bestZone = zoneId;
            }
        }
        return new TargetChoice(bestZone >= 0 ? trackForZone(bestZone) : -1,
                bestZone);
    }

    private TargetChoice targetChoice(PlanningSquad squad, int preferredTrack,
                                      ConquestCommandFrame frame) {
        int home = nearestDefenderZoneInStrip(squad, preferredTrack, frame);
        if (home >= 0) return new TargetChoice(preferredTrack, home);

        int bestTrack = -1;
        int bestZone = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int track = Math.max(0, preferredTrack - 1);
             track <= Math.min(STRIP_COUNT - 1, preferredTrack + 1); track++) {
            if (track == preferredTrack) continue;
            int zone = nearestDefenderZoneInStrip(squad, track, frame);
            if (zone < 0) continue;
            float dx = squad.centroidX - zoneCentroidX[zone];
            float dy = squad.centroidY - zoneCentroidY[zone];
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance
                    || (distance == bestDistance && track < bestTrack)) {
                bestDistance = distance;
                bestTrack = track;
                bestZone = zone;
            }
        }
        return new TargetChoice(bestTrack, bestZone);
    }

    /**
     * Walk this strip's zones and return the nearest defender-occupied one
     * to the squad's current centroid on the forward axis, with a
     * positive-forward bias: if both forward and backward defender
     * positions exist, the forward one wins on ties (and is preferred
     * outright when forward positions exist).
     *
     * <p>A zone this pulse has already filled to its own quota plus
     * {@link #ZONE_TARGET_OVERFLOW_SQUADS} is passed over for the next forward
     * zone in the strip, so a force larger than the front's useful slots
     * divides across the strip's depth instead of queueing behind one target.
     * A strip whose zones are all at cap still hands one back — a squad with no
     * target is worse than a crowded one — but hands back the least crowded of
     * them rather than the nearest, so the surplus divides too.
     *
     * <p>Forward bias matters because CONQUEST is a directional push. A
     * squad that's already moved past a flanking defender shouldn't be
     * pulled back to clear them — the next strip-neighbor squad picks
     * them up if they're in their strip, or {@code EliminateEnemiesGoal}
     * handles them ambiently when in LoS.
     *
     * <p>{@code -1} when the strip has no defender-occupied zones, or
     * when the only defender zone is the squad's <em>current</em> zone
     * (in which case {@link
     * com.dillon.starsectormarines.battle.infantry.ClearAssignedZoneGoal}
     * returns relevance 0 anyway via its {@code currentZone == targetZone}
     * gate, so callers see consistent "no plan to execute" behavior and
     * the squad falls through to {@code EliminateEnemiesGoal} for in-zone
     * engagement, or receives a later lane-stage fallback when the believed
     * contact is farther ahead in the open exterior).
     */
    private int nearestDefenderZoneInStrip(PlanningSquad squad, int stripIdx,
                                           ConquestCommandFrame frame) {
        if (!ZONE_TARGET_CAP_ENABLED) {
            return nearestDefenderZoneInStrip(squad, stripIdx, frame, false);
        }
        int spare = nearestDefenderZoneInStrip(squad, stripIdx, frame, true);
        return spare >= 0 ? spare
                : nearestDefenderZoneInStrip(squad, stripIdx, frame, false);
    }

    /**
     * @param underCapOnly skip zones this pulse has already filled to
     *                     {@link #ZONE_TARGET_OVERFLOW_SQUADS} over their
     *                     quota, so the next forward zone in the strip is
     *                     offered instead. The caller above runs this pass
     *                     first and repeats without it, which is what makes
     *                     the cap a preference rather than a refusal.
     *                     <p>The repeat is not the old rule verbatim: it ranks
     *                     a candidate by how many squads are already on it
     *                     before it ranks it by distance. A strip whose zones
     *                     are all at cap has a surplus to place either way, and
     *                     putting all of it on the nearest zone is the queue
     *                     the cap exists to break — measured on
     *                     {@code full-strength-west}, where three zones took
     *                     their three squads each and thirteen more squads fell
     *                     back onto one of them.
     */
    private int nearestDefenderZoneInStrip(PlanningSquad squad, int stripIdx,
                                           ConquestCommandFrame frame,
                                           boolean underCapOnly) {
        if (stripIdx < 0 || stripIdx >= stripZones.size()) return -1;
        float squadForward = (axis == TraversalAxis.SOUTH_TO_NORTH) ? squad.centroidY : squad.centroidX;

        int bestForwardZone = -1;
        int bestForwardLoad = Integer.MAX_VALUE;
        float bestForwardDist = Float.MAX_VALUE;
        int bestBackwardZone = -1;
        int bestBackwardLoad = Integer.MAX_VALUE;
        float bestBackwardDist = Float.MAX_VALUE;
        for (int zoneId : stripZones.get(stripIdx)) {
            if (zoneId == exteriorZoneId) continue;
            if (underCapOnly && zoneTargetIsFull(zoneId)) continue;
            if (!hasKnownHostileInZone(zoneId, frame)) continue;
            if (!reachableZone(squad, zoneId, frame)) continue;
            // Under the cap every survivor is equally unburdened, so this is
            // the plain nearest rule; over it, the load is what separates them.
            int load = underCapOnly || !ZONE_TARGET_CAP_ENABLED
                    ? 0 : zoneTargetSquads[zoneId];
            float zoneForward = zoneForwardCoord[zoneId];
            float delta = zoneForward - squadForward;
            if (delta >= 0f) {
                if (load < bestForwardLoad
                        || (load == bestForwardLoad && delta < bestForwardDist)) {
                    bestForwardLoad = load;
                    bestForwardDist = delta;
                    bestForwardZone = zoneId;
                }
            } else {
                float absDelta = -delta;
                if (load < bestBackwardLoad
                        || (load == bestBackwardLoad && absDelta < bestBackwardDist)) {
                    bestBackwardLoad = load;
                    bestBackwardDist = absDelta;
                    bestBackwardZone = zoneId;
                }
            }
        }
        return bestForwardZone >= 0 ? bestForwardZone : bestBackwardZone;
    }

    /**
     * Whether this pulse has already pointed a zone's full complement at it:
     * its own capture quota plus {@link #ZONE_TARGET_OVERFLOW_SQUADS}. A zone
     * that is no compound's capture room is worth one squad — there is no
     * footprint to say otherwise, and a room is a room.
     */
    private boolean zoneTargetIsFull(int zoneId) {
        if (!ZONE_TARGET_CAP_ENABLED) return false;
        if (zoneId < 0 || zoneId >= zoneTargetSquads.length) return false;
        int quota = 1;
        int index = targetIndexForCaptureZone(zoneId);
        if (index >= 0) quota = compoundTargets.get(index).desiredSquads;
        return zoneTargetSquads[zoneId] >= quota + ZONE_TARGET_OVERFLOW_SQUADS;
    }

    /**
     * The {@code CLEAR_ZONE} target this squad already holds, or {@code -1}
     * when it holds none or the one it holds has stopped being worth holding:
     * the belief has gone, the zone is unreachable, or the squad's home track
     * has drifted more than a neighbour away from it. Those are the conditions
     * {@link #nearestDefenderZoneInStrip} would apply to the zone as a fresh
     * candidate, asked of the order the squad is already executing.
     *
     * <p>Only consulted under {@link #ZONE_TARGET_CAP_PROPERTY}. The plain
     * nearest-forward rule is a pure function of the squad's own position and
     * needs no stickiness; the capped rule depends on who chose first, so
     * without this a reordering of the queue is a retarget.
     */
    private int standingZoneTarget(PlanningSquad squad, ConquestCommandFrame frame) {
        ObjectiveAssignment held = squad.assignedObjective;
        if (held == null || held.kind() != AssignmentKind.CLEAR_ZONE) return -1;
        int zone = held.targetZoneId();
        if (zone < 0 || zone == exteriorZoneId) return -1;
        if (!hasKnownHostileInZone(zone, frame)) return -1;
        if (!reachableZone(squad, zone, frame)) return -1;
        int track = trackForZone(zone);
        return track >= 0 && Math.abs(track - stripFor(squad)) > 1 ? -1 : zone;
    }

    /** The track a kept target is published under: its own, or the squad's. */
    private int effectiveTrackFor(int zoneId, int preferredTrack) {
        int track = trackForZone(zoneId);
        return track >= 0 ? track : preferredTrack;
    }

    /** Records a front-push target so the zone's cap counts it. */
    private void noteZoneTarget(int zoneId) {
        if (zoneId < 0 || zoneId >= zoneTargetSquads.length) return;
        zoneTargetSquads[zoneId]++;
    }

    private boolean hasKnownHostileInZone(int zoneId, ConquestCommandFrame frame) {
        return zoneId == -1 ? hasUnzonedHostileContact
                : zoneId >= 0 && zoneId < hostileContactByZone.length
                && hostileContactByZone[zoneId];
    }

    private void indexHostileContactZones(ConquestCommandFrame frame) {
        int zoneCount = frame.topology().zones().size();
        if (hostileContactByZone.length != zoneCount) {
            hostileContactByZone = new boolean[zoneCount];
        } else {
            Arrays.fill(hostileContactByZone, false);
        }
        hasUnzonedHostileContact = false;
        CommanderInfluenceSnapshot influence = frame.influence();
        if (influence == null) return;
        for (CommanderContact contact : influence.contacts()) {
            int zone = frame.topology().zoneIdAt(contact.cellX(), contact.cellY());
            if (zone >= 0 && zone < zoneCount) hostileContactByZone[zone] = true;
            else if (zone == -1) hasUnzonedHostileContact = true;
        }
    }

    private boolean reachableZone(PlanningSquad squad, int zoneId,
                                  ConquestCommandFrame frame) {
        if (squad.anchorCellX < 0 || squad.anchorCellY < 0) return false;
        if (zoneId < 0 || zoneId >= zoneCentroidX.length) return false;
        int currentZone = frame.topology().zoneIdAt(
                squad.anchorCellX, squad.anchorCellY);
        return frame.topology().areZonesConnected(currentZone, zoneId);
    }

    private ConquestFrontSnapshot buildFrontSnapshot(
            ConquestCommandFrame frame, Phase phase,
            int remainingCompounds, CompoundTarget keep,
            Map<Integer, SquadDirective> directives,
            Map<Integer, PlanningSquad> allSquads) {
        int[] preferredSquads = new int[STRIP_COUNT];
        int[] effectiveSquads = new int[STRIP_COUNT];
        int[] effectiveMembers = new int[STRIP_COUNT];
        int[] preferredMembers = new int[STRIP_COUNT];
        float[] bodyProgressSum = new float[STRIP_COUNT];
        float[] leadProgress = new float[STRIP_COUNT];
        Arrays.fill(leadProgress, -1f);
        int[] targetZones = new int[STRIP_COUNT];
        Arrays.fill(targetZones, -1);

        int forwardExtent = axis == TraversalAxis.SOUTH_TO_NORTH
                ? frame.topology().height() : frame.topology().width();
        for (PlanningSquad squad : allSquads.values()) {
            if (squad.aliveMembers <= 0) continue;
            SquadDirective directive = directives.get(squad.id);
            if (directive != null
                    && directive.reason() == AssignmentReason.GARRISON_HOLD) {
                continue;
            }
            int preferred = directive != null
                    ? directive.preferredTrack() : stripFor(squad);
            if (preferred >= 0 && preferred < STRIP_COUNT) {
                preferredSquads[preferred]++;
                preferredMembers[preferred] += squad.aliveMembers;
                float forward = axis == TraversalAxis.SOUTH_TO_NORTH
                        ? squad.centroidY : squad.centroidX;
                float progress = normalizedProgress(forward, forwardExtent);
                bodyProgressSum[preferred] += progress * squad.aliveMembers;
                leadProgress[preferred] = Math.max(leadProgress[preferred], progress);
            }
            if (directive == null) continue;
            int effective = directive.effectiveTrack();
            if (effective < 0 || effective >= STRIP_COUNT) continue;
            effectiveSquads[effective]++;
            effectiveMembers[effective] += squad.aliveMembers;
            if (targetZones[effective] < 0 && directive.targetZoneId() >= 0) {
                targetZones[effective] = directive.targetZoneId();
            }
        }

        CommanderInfluenceSnapshot influence = frame.influence();
        float[] knownHostileFront = new float[STRIP_COUNT];
        Arrays.fill(knownHostileFront, -1f);
        int[] knownContacts = new int[STRIP_COUNT];
        float[] friendlyPressure = new float[STRIP_COUNT];
        float[] hostilePressure = new float[STRIP_COUNT];
        int influenceTick = -1;
        if (influence != null) {
            influenceTick = influence.updatedTick();
            for (CommanderContact contact : influence.contacts()) {
                int lateral = axis == TraversalAxis.SOUTH_TO_NORTH
                        ? contact.cellX() : contact.cellY();
                int track = stripIndexForLateral(lateral);
                if (track < 0 || track >= STRIP_COUNT) continue;
                int forward = axis == TraversalAxis.SOUTH_TO_NORTH
                        ? contact.cellY() : contact.cellX();
                float progress = normalizedProgress(forward, forwardExtent);
                knownContacts[track]++;
                if (knownHostileFront[track] < 0f) {
                    knownHostileFront[track] = progress;
                } else {
                    knownHostileFront[track] = Math.min(
                            knownHostileFront[track], progress);
                }
            }
            for (int by = 0; by < influence.height(); by++) {
                for (int bx = 0; bx < influence.width(); bx++) {
                    int worldX = influence.blockWorldX(bx)
                            + influence.blockWorldWidth(bx) / 2;
                    int worldY = influence.blockWorldY(by)
                            + influence.blockWorldHeight(by) / 2;
                    int lateral = axis == TraversalAxis.SOUTH_TO_NORTH
                            ? worldX : worldY;
                    int track = stripIndexForLateral(lateral);
                    if (track < 0 || track >= STRIP_COUNT) continue;
                    friendlyPressure[track] += influence.friendlyAt(bx, by);
                    hostilePressure[track] += influence.hostileAt(bx, by);
                }
            }
        }

        List<TrackState> tracks = new ArrayList<>(STRIP_COUNT);
        for (int track = 0; track < STRIP_COUNT; track++) {
            int lateralStart = trackLayout.lateralStartInclusive(track);
            int lateralEnd = trackLayout.lateralEndInclusive(track);
            float bodyProgress = preferredMembers[track] > 0
                    ? bodyProgressSum[track] / preferredMembers[track] : -1f;
            boolean onChain = laneChainInForce() && track < chainLinks.length;
            tracks.add(new TrackState(track, lateralStart, lateralEnd,
                    preferredSquads[track], effectiveSquads[track],
                    effectiveMembers[track], bodyProgress, leadProgress[track],
                    knownHostileFront[track], knownContacts[track],
                    friendlyPressure[track], hostilePressure[track],
                    targetZones[track], -1,
                    onChain ? chainLinks[track] : -1,
                    onChain ? chainHeld[track] : -1,
                    onChain ? chainFront[track] : -1));
        }
        return new ConquestFrontSnapshot(frame.tick(),
                influenceTick, axis, phase, remainingCompounds,
                keep != null ? keep.captureZoneId : -1,
                keep != null ? keep.state : null,
                tracks, squadStates(allSquads, directives, frame.topology()),
                new ArrayList<>(directives.values()));
    }

    private static List<ConquestFrontSnapshot.SquadState> squadStates(
            Map<Integer, PlanningSquad> squads,
            Map<Integer, SquadDirective> directives,
            CommandTopology topology) {
        List<ConquestFrontSnapshot.SquadState> states = new ArrayList<>(squads.size());
        for (PlanningSquad squad : squads.values()) {
            SquadDirective directive = directives.get(squad.id);
            int targetZone = directive != null ? directive.targetZoneId() : -1;
            states.add(new ConquestFrontSnapshot.SquadState(
                    squad.id, squad.aliveMembers, squad.centroidX,
                    squad.centroidY, squad.currentZoneId,
                    squad.executionSuspension, squad.localContact,
                    squad.activePathMembers,
                    membersInZone(squad.memberZoneIds, targetZone),
                    membersAtTargetPortal(squad, targetZone, topology),
                    squad.underFireRecently, squad.moraleBroken,
                    squad.currentGoal, squad.currentAction,
                    squad.movingMembers, squad.coveredFromPrimaryMembers,
                    squad.primaryEngageableMembers,
                    squad.primaryEngageableFireTeams,
                    squad.contactPosture, squad.contactDoctrine,
                    squad.contactInitiative,
                    squad.coolingDownMembers));
        }
        return states;
    }

    private static int membersInZone(int[] memberZoneIds, int targetZone) {
        if (targetZone < 0) return 0;
        int count = 0;
        for (int zoneId : memberZoneIds) if (zoneId == targetZone) count++;
        return count;
    }

    private static int membersAtTargetPortal(PlanningSquad squad,
                                             int targetZone,
                                             CommandTopology topology) {
        if (targetZone < 0) return 0;
        int count = 0;
        for (int i = 0; i < squad.memberCellXs.length; i++) {
            int x = squad.memberCellXs[i];
            int y = squad.memberCellYs[i];
            if (!topology.inBounds(x, y)) continue;
            int cell = y * topology.width() + x;
            if (!topology.isDoorwayCell(cell)) continue;
            if (topology.zoneIdAt(x - 1, y) == targetZone
                    || topology.zoneIdAt(x + 1, y) == targetZone
                    || topology.zoneIdAt(x, y - 1) == targetZone
                    || topology.zoneIdAt(x, y + 1) == targetZone) {
                count++;
            }
        }
        return count;
    }

    private List<CommandProposal> buildProposals(
            ConquestCommandFrame frame,
            Map<Integer, PlanningSquad> squads,
            Map<Integer, SquadDirective> directives,
            Phase phase) {
        List<CommandProposal> proposals = new ArrayList<>();
        for (Map.Entry<Integer, SquadDirective> entry : directives.entrySet()) {
            int squadId = entry.getKey();
            PlanningSquad planned = squads.get(squadId);
            CommandSquadState frozen = frame.squad(squadId);
            if (planned == null || frozen == null) continue;
            CommandDirective incumbent = frozen.directive();
            CommandStabilityBreak stabilityBreak = stabilityBreak(
                    frame, planned, incumbent, phase,
                    entry.getValue().reason());
            if (incumbent != null
                    && incumbent.authority().priority()
                    > CommandAuthority.MISSION_COMMAND.priority()) {
                proposals.add(CommandProposal.retain(squadId,
                        CommandAuthority.MISSION_COMMAND,
                        entry.getValue().reason().name()));
            } else if (planned.assignedObjective != null) {
                proposals.add(CommandProposal.assign(planned.assignedObjective,
                        CommandAuthority.MISSION_COMMAND,
                        entry.getValue().reason().name(), stabilityBreak));
            } else if (frozen.assignment() != null) {
                proposals.add(CommandProposal.release(squadId,
                        CommandAuthority.MISSION_COMMAND,
                        entry.getValue().reason().name(), stabilityBreak));
            } else {
                proposals.add(CommandProposal.retain(squadId,
                        CommandAuthority.MISSION_COMMAND,
                        entry.getValue().reason().name()));
            }
        }
        return proposals;
    }

    private CommandStabilityBreak stabilityBreak(
            ConquestCommandFrame frame, PlanningSquad squad,
            CommandDirective incumbent, Phase phase,
            AssignmentReason reason) {
        if (incumbent == null || incumbent.assignment() == null
                || incumbent.issuer() == null
                || !strategyId().equals(incumbent.issuer())
                || Objects.equals(incumbent.assignment(), squad.assignedObjective)) {
            return CommandStabilityBreak.NONE;
        }
        ObjectiveAssignment old = incumbent.assignment();
        if (old.kind() == AssignmentKind.SECURE_COMPOUND
                && squad.assignedObjective != null
                && squad.assignedObjective.kind()
                == AssignmentKind.SECURE_COMPOUND
                && CommandFrameCopies.sameNodeIdentity(old.targetNode(),
                squad.assignedObjective.targetNode())
                && old.targetZoneId()
                != squad.assignedObjective.targetZoneId()) {
            return CommandStabilityBreak.TOPOLOGY_REBOUND;
        }
        if (frontSnapshot != null && frontSnapshot.phase() != phase) {
            return CommandStabilityBreak.OBJECTIVE_COMPLETED;
        }
        if (old.kind() == AssignmentKind.SECURE_COMPOUND) {
            CompoundTarget target = compoundTarget(
                    old.targetNode(), old.targetZoneId());
            if (target == null
                    || target.state == CompoundService.CompoundState.MARINE_HELD) {
                return CommandStabilityBreak.OBJECTIVE_COMPLETED;
            }
            if (!reachableZone(squad, target.captureZoneId, frame)) {
                return CommandStabilityBreak.TARGET_UNREACHABLE;
            }
        } else if (old.kind() == AssignmentKind.CLEAR_ZONE) {
            if (!reachableZone(squad, old.targetZoneId(), frame)) {
                return CommandStabilityBreak.TARGET_UNREACHABLE;
            }
            if (!hasKnownHostileInZone(old.targetZoneId(), frame)
                    || reason == AssignmentReason.NO_ACTIONABLE_TRACK_TARGET
                    || reason == AssignmentReason.CAPTURE_OUT_OF_TRACK_REACH) {
                return CommandStabilityBreak.CONTEXT_INVALIDATED;
            }
        } else if (old.kind() == AssignmentKind.ADVANCE_TRACK
                || old.kind() == AssignmentKind.ATTACK_MOVE) {
            boolean keeps = old.kind() == AssignmentKind.ATTACK_MOVE
                    ? reason == AssignmentReason.TRACK_LINE_ATTACK
                    // A staging order does not become a different order
                    // because the track it crosses has since been sighted, or
                    // has gone quiet again. Both reasons publish the same
                    // ADVANCE_TRACK marker and differ only in what the
                    // commander knew when it chose the cell.
                    : reason == AssignmentReason.TRACK_LINE_ADVANCE
                    || reason == AssignmentReason.TRACK_LINE_SCOUT_ADVANCE;
            if (!keeps) {
                return CommandStabilityBreak.CONTEXT_INVALIDATED;
            }
            if (!frame.topology().inBounds(old.targetCellX(), old.targetCellY())
                    || !frame.topology().isWalkable(
                    old.targetCellX(), old.targetCellY())) {
                return CommandStabilityBreak.TARGET_UNREACHABLE;
            }
            boolean atTarget = squad.anchorCellX == old.targetCellX()
                    && squad.anchorCellY == old.targetCellY();
            if (!atTarget && !frame.topology().reachable(
                    squad.anchorCellX, squad.anchorCellY,
                    old.targetCellX(), old.targetCellY())) {
                return CommandStabilityBreak.TARGET_UNREACHABLE;
            }
        }
        if (reason == AssignmentReason.NO_REACHABLE_COMPOUND_TARGET) {
            return CommandStabilityBreak.TARGET_UNREACHABLE;
        }
        return CommandStabilityBreak.NONE;
    }

    private CompoundTarget compoundTarget(TacticalNode node, int zoneId) {
        if (node != null) {
            for (CompoundTarget target : compoundTargets) {
                if (CommandFrameCopies.sameNodeIdentity(
                        node, target.node)) return target;
            }
            return null;
        }
        for (CompoundTarget target : compoundTargets) {
            if (target.captureZoneId == zoneId) return target;
        }
        return null;
    }

    private static float normalizedProgress(float forward, int extent) {
        if (extent <= 1) return 0f;
        return Math.max(0f, Math.min(1f, forward / (extent - 1f)));
    }

    // ---- Test/debug accessors ----

    /** Strip the named squad is anchored to, or {@code -1} if it hasn't been observed yet. Public for tests + the future debug overlay. */
    public int stripIndexOf(int squadId) {
        return squadStripIdx.get(squadId);
    }

    /** Zone ids in the named strip, sorted forward-to-back. Returns an empty list for an out-of-range index. Test/debug only. */
    public List<Integer> zonesInStrip(int stripIdx) {
        if (stripZones == null || stripIdx < 0 || stripIdx >= stripZones.size()) {
            return List.of();
        }
        return List.copyOf(stripZones.get(stripIdx));
    }
}
