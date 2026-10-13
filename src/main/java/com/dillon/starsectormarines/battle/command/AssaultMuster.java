package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.AssignmentReason;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.CompanyState;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Family;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The muster and assault phases of a phased assault (`phased-assault.md`): when the next objective is one single
 * squads cannot take, the commander forms an <em>assault company</em> at a rally point, judges its readiness against
 * what it believes is in front of it, commits it as one body, and keeps it together on the approach.
 *
 * <p>Pure planning over frozen inputs, and symmetric: nothing here knows which side it plans for, so a defender's
 * counter-assault could use the same machinery. The first case is a disclosed fort; the objective and the believed
 * picture of it are the {@link Objective}, so another strong believed position is another objective, not another
 * mechanism.
 *
 * <h2>Phases, per objective</h2>
 * <ul>
 *   <li><b>Foothold.</b> While any company exists, one squad holds the drop zone. Probes elsewhere go on as they
 *       always did; this class never touches a squad it was not given.</li>
 *   <li><b>Muster.</b> Squads are recruited nearest-first to a rally point and ordered to it. The rally point is the
 *       drop zone when the objective is close, else a staging cell short of the objective on the approach, pulled back
 *       toward the drop zone while any believed hostile is near it. The company is {@link #judge ready} when the
 *       strength gathered at the rally point reaches what the objective is believed to take, or when a timeout
 *       passes, so a muster can never wait forever.</li>
 *   <li><b>Assault.</b> The whole company is ordered at the one aspect of the fort that {@link FortTactics} ranks
 *       first ({@link DefenceStructureTactics#planMassed}). A squad that runs well ahead of the company holds until
 *       it comes up, with the same bounds as a command group's rally, so the company reaches the fort as one body.
 *       A company reduced below {@link #BROKEN_FRACTION} of the strength it committed is broken: the attempt is
 *       counted, and the next muster believes the objective takes more.</li>
 * </ul>
 *
 * <h2>Laws</h2>
 * <ul>
 *   <li>Belief only. Readiness reads the objective's disclosed aspects and the hostile contacts the side holds near
 *       it, never the garrison's true size.</li>
 *   <li>Every figure here marked UNTUNED is a first pass to be set from play; none was chosen to hit a target.</li>
 * </ul>
 */
final class AssaultMuster {

    /** Weighted marines a fort is believed to take before any hostile is sighted near it. UNTUNED. */
    static final int FORT_BASE_STRENGTH = 24;
    /** Weighted marines added for each hostile contact believed near the objective. UNTUNED. */
    static final int PER_CONTACT_STRENGTH = 2;
    /** A contact within this many cells of the objective counts as in front of it. UNTUNED. */
    static final int CONTACT_RADIUS_CELLS = 40;
    /** One living member of a machine squad (a mech) counts as this many marines. UNTUNED. */
    static final float MACHINE_MEMBER_WEIGHT = 3f;
    /** Each broken attempt on an objective raises the strength it is believed to take by this fraction. UNTUNED. */
    static final float FAILURE_GROWTH = 0.25f;
    /** Ticks a muster waits from forming before it commits with what it has. UNTUNED. */
    static final int MUSTER_TIMEOUT_TICKS = 4500;
    /** Ticks allowed per cell of walk for a recruit to reach the rally point. UNTUNED. */
    static final int TICKS_PER_CELL = 12;
    /** Extra ticks a recruit is allowed on top of its walk. UNTUNED. */
    static final int ARRIVAL_SLACK_TICKS = 600;
    /**
     * Once every squad the company can get has arrived and it is still short, it waits this many more ticks for
     * anything yet to come before committing with what it has, rather than standing out the whole timeout. UNTUNED.
     */
    static final int STRAGGLER_GRACE_TICKS = 900;
    /** A squad within this many cells of the rally point has arrived. UNTUNED. */
    static final int RALLY_RADIUS_CELLS = 10;
    /** How far short of the objective the staging cell lies when the objective is far from the drop zone. UNTUNED. */
    static final int STAGING_STANDOFF_CELLS = 56;
    /** A staging cell with a believed hostile this close is unsafe and is pulled back. UNTUNED. */
    static final int SAFE_CONTACT_CELLS = 24;
    /** How far a staging cell may slide off its line to find walkable, reachable ground. */
    static final int RALLY_SNAP_CELLS = 12;
    /** Largest share of the side's free squads that companies may hold at once. UNTUNED. */
    static final float COMPANY_SHARE = 0.6f;
    /** A company left with less than this fraction of the strength it committed is broken. UNTUNED. */
    static final float BROKEN_FRACTION = 0.25f;
    /** Inside this many cells of the objective the company is in the fight and no longer waits for its stragglers. */
    static final int ENGAGE_RING_CELLS = 32;
    /** How far from the drop zone the foothold element may be chosen. UNTUNED. */
    static final int FOOTHOLD_RECRUIT_CELLS = 60;

    /** What a muster's gathered strength says about committing. */
    enum Readiness { GATHERING, READY, TIMED_OUT }

    private enum Stage { MUSTER, ASSAULT }

    /**
     * A strong believed position: a fort the side has been disclosed, with the hostile contacts it believes stand
     * near it.
     *
     * @param cellX            centre of the disclosed aspects
     * @param believedContacts hostile contacts the side's own belief places within {@link #CONTACT_RADIUS_CELLS}
     */
    record Objective(int fortId, Family family, int cellX, int cellY, List<DefenceStructureFact> aspects,
                     int believedContacts) { }

    /**
     * A squad the commander may put in a company.
     *
     * @param inContact engaged: under fire, broken, or with a member able to engage a contact. Not recruited, and not held back on the approach. A belief in a distant hostile is not contact
     */
    record Body(int squadId, int anchorX, int anchorY, int alive, boolean machine, boolean demolition,
                boolean inContact, ObjectiveAssignment incumbent) { }

    record Order(ObjectiveAssignment assignment, AssignmentReason reason) { }

    /** @param orders by squad id; only squads this class claims appear. */
    record Result(Map<Integer, Order> orders, List<CompanyState> companies, List<String> notes) { }

    private static final class Company {
        final int fortId;
        Stage stage = Stage.MUSTER;
        final int formedTick;
        int stageTick;
        int timeoutTick;
        final Set<Integer> members = new TreeSet<>();
        int rallyX;
        int rallyY;
        float strengthAtCommit;
        String reason = "";
        int waitStarted = -1;
        boolean boundReleased;
        /** The tick the company first had everything it can get at the rally and still fell short, or -1. */
        int fullSince = -1;

        int graceFrom(int tick) {
            if (fullSince < 0) fullSince = tick;
            return fullSince;
        }

        Company(int fortId, int tick) {
            this.fortId = fortId;
            this.formedTick = tick;
            this.stageTick = tick;
            this.timeoutTick = tick + MUSTER_TIMEOUT_TICKS;
        }
    }

    private final Map<Integer, Company> companies = new TreeMap<>();
    /** Broken attempts per objective: the side's memory that the objective took more than it believed. */
    private final Map<Integer, Integer> failures = new HashMap<>();
    private int footholdSquad = -1;

    /** Forgets every company and the foothold element; each squad returns to ordinary command next pulse. */
    void dissolveAll() {
        companies.clear();
        footholdSquad = -1;
    }

    boolean active() {
        return !companies.isEmpty();
    }

    /** The strength an objective is believed to take, in marine-equivalents. */
    static int required(Family family, int believedContacts, int failures) {
        int base = switch (family) {
            case BATTERY_FORT, SHIELD_FORT -> FORT_BASE_STRENGTH;
            case BARE -> FORT_BASE_STRENGTH / 4;
        };
        float believed = base + PER_CONTACT_STRENGTH * Math.max(0, believedContacts);
        return (int) Math.ceil(believed * (1f + FAILURE_GROWTH * Math.max(0, failures)));
    }

    static float weight(int alive, boolean machine) {
        return Math.max(0, alive) * (machine ? MACHINE_MEMBER_WEIGHT : 1f);
    }

    /**
     * Whether the company is ready to commit: the strength at the rally point reaches the strength believed needed,
     * or the muster has run out of time.
     */
    static Readiness judge(float arrivedStrength, int required, int tick, int timeoutTick) {
        if (arrivedStrength > 0f && arrivedStrength >= required) return Readiness.READY;
        if (tick >= timeoutTick) return Readiness.TIMED_OUT;
        return Readiness.GATHERING;
    }

    /** Whether a cell is walkable and reachable from the drop zone. */
    @FunctionalInterface
    interface Ground {
        boolean ok(int x, int y);
    }

    /**
     * The cell a company gathers at: the drop zone when the objective is near it, otherwise a staging cell
     * {@link #STAGING_STANDOFF_CELLS} short of the objective on the line from the drop zone, slid to ground that is
     * usable and with no believed hostile within {@link #SAFE_CONTACT_CELLS}; if the line is unsafe all the way back
     * the drop zone itself is the answer.
     */
    static int[] rallyCell(int dropX, int dropY, int objectiveX, int objectiveY, Ground ground, List<int[]> hostiles) {
        double dx = objectiveX - dropX;
        double dy = objectiveY - dropY;
        double distance = Math.hypot(dx, dy);
        if (distance <= STAGING_STANDOFF_CELLS + RALLY_RADIUS_CELLS) return new int[] {dropX, dropY};
        for (double back = distance - STAGING_STANDOFF_CELLS; back > RALLY_RADIUS_CELLS; back -= 16.0) {
            int x = (int) Math.round(dropX + dx / distance * back);
            int y = (int) Math.round(dropY + dy / distance * back);
            int[] cell = snap(x, y, ground);
            if (cell != null && safe(cell[0], cell[1], hostiles)) return cell;
        }
        return new int[] {dropX, dropY};
    }

    static boolean safe(int x, int y, List<int[]> hostiles) {
        for (int[] hostile : hostiles) {
            if (Math.hypot(hostile[0] - x, hostile[1] - y) < SAFE_CONTACT_CELLS) return false;
        }
        return true;
    }

    private static int[] snap(int x, int y, Ground ground) {
        if (ground.ok(x, y)) return new int[] {x, y};
        for (int ring = 1; ring <= RALLY_SNAP_CELLS; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) continue;
                    if (ground.ok(x + dx, y + dy)) return new int[] {x + dx, y + dy};
                }
            }
        }
        return null;
    }

    /**
     * One pulse of company planning.
     *
     * @param objectives the strong positions this side is disclosed, whatever their state
     * @param bodies     the squads that may be claimed, in or out of a company already
     * @param drop       the drop zone cell
     * @param hostiles   believed hostile contact cells
     */
    Result plan(int tick, Faction perspective, List<Objective> objectives, List<Body> bodies, int[] drop,
                List<int[]> hostiles, CommandTopology topology) {
        Map<Integer, Order> orders = new TreeMap<>();
        List<String> notes = new ArrayList<>();
        Map<Integer, Body> byId = new TreeMap<>();
        for (Body body : bodies) if (body.alive() > 0) byId.put(body.squadId(), body);

        Set<Integer> live = new TreeSet<>();
        for (Objective objective : objectives) live.add(objective.fortId());
        List<Integer> gone = new ArrayList<>();
        for (Company company : companies.values()) {
            company.members.removeIf(id -> !byId.containsKey(id));
            if (!live.contains(company.fortId)) {
                gone.add(company.fortId);
                notes.add("company for fort " + company.fortId + " dissolved: nothing left of it to take");
                continue;
            }
            if (company.stage == Stage.ASSAULT) {
                float strength = strengthOf(company, byId);
                if (company.members.isEmpty() || strength < BROKEN_FRACTION * company.strengthAtCommit) {
                    gone.add(company.fortId);
                    failures.merge(company.fortId, 1, Integer::sum);
                    notes.add("company for fort " + company.fortId + " broken at strength " + Math.round(strength)
                            + " of " + Math.round(company.strengthAtCommit) + " committed; the next muster believes the fort takes more");
                }
            }
        }
        for (Integer id : gone) companies.remove(id);

        if (objectives.isEmpty() || byId.isEmpty()) {
            footholdSquad = -1;
            return new Result(orders, List.of(), notes);
        }

        List<CompanyState> picture = new ArrayList<>();
        chooseFoothold(byId, drop);
        int claimedByCompanies = 0;
        for (Company company : companies.values()) claimedByCompanies += company.members.size();
        int cap = Math.max(1, (int) Math.ceil(COMPANY_SHARE * byId.size()));

        List<Objective> ordered = new ArrayList<>(objectives);
        ordered.sort(Comparator.<Objective>comparingDouble(o -> Math.hypot(o.cellX() - drop[0], o.cellY() - drop[1]))
                .thenComparingInt(Objective::fortId));
        Ground ground = (x, y) -> topology.inBounds(x, y) && topology.isWalkable(x, y)
                && topology.reachable(drop[0], drop[1], x, y);

        for (Objective objective : ordered) {
            Company company = companies.get(objective.fortId());
            if (company == null) {
                if (freeBodies(byId).isEmpty()) continue;
                company = new Company(objective.fortId(), tick);
                int[] rally = rallyCell(drop[0], drop[1], objective.cellX(), objective.cellY(), ground, hostiles);
                company.rallyX = rally[0];
                company.rallyY = rally[1];
                companies.put(objective.fortId(), company);
                notes.add("company formed for fort " + objective.fortId() + " "
                        + objective.family().name().toLowerCase(Locale.ROOT) + ": rally at (" + rally[0] + "," + rally[1]
                        + "), believes it takes " + required(objective.family(), objective.believedContacts(),
                        failures.getOrDefault(objective.fortId(), 0)) + ", commits by tick " + company.timeoutTick);
            }
            if (company.stage == Stage.MUSTER) {
                claimedByCompanies += muster(tick, objective, company, byId, drop, hostiles, ground, cap - claimedByCompanies, notes);
            }
            if (company.stage == Stage.ASSAULT) {
                assault(tick, perspective, objective, company, byId, topology, orders, notes);
            } else {
                gather(company, byId, orders);
            }
            if (companies.containsKey(objective.fortId())) {
                picture.add(stateOf(tick, objective, company, byId));
            }
        }
        if (footholdSquad >= 0 && byId.containsKey(footholdSquad)) {
            orders.put(footholdSquad, new Order(
                    ObjectiveAssignment.advanceTrack(footholdSquad, drop[0], drop[1]), AssignmentReason.MUSTER_FOOTHOLD));
            picture.add(new CompanyState(-1, "FOOTHOLD", List.of(footholdSquad), 1,
                    Math.round(weight(byId.get(footholdSquad).alive(), byId.get(footholdSquad).machine())), 0,
                    drop[0], drop[1], drop[0], drop[1], 0, -1, "holds the drop zone while a company musters"));
        }
        return new Result(orders, picture, notes);
    }

    /** The foothold element: one squad that holds the drop zone for as long as any company exists. Sticky. */
    private void chooseFoothold(Map<Integer, Body> byId, int[] drop) {
        if (footholdSquad >= 0 && byId.containsKey(footholdSquad) && !inAnyCompany(footholdSquad)) return;
        footholdSquad = -1;
        Body best = null;
        double bestGap = Double.MAX_VALUE;
        for (Body body : byId.values()) {
            if (body.machine() || body.inContact() || inAnyCompany(body.squadId())) continue;
            double gap = Math.hypot(body.anchorX() - drop[0], body.anchorY() - drop[1]);
            if (gap > FOOTHOLD_RECRUIT_CELLS) continue;
            if (gap < bestGap || (gap == bestGap && best != null && body.squadId() < best.squadId())) {
                best = body;
                bestGap = gap;
            }
        }
        if (best != null) footholdSquad = best.squadId();
    }

    private boolean inAnyCompany(int squadId) {
        for (Company company : companies.values()) if (company.members.contains(squadId)) return true;
        return false;
    }

    private List<Body> freeBodies(Map<Integer, Body> byId) {
        List<Body> free = new ArrayList<>();
        for (Body body : byId.values()) {
            if (body.squadId() == footholdSquad || body.inContact() || inAnyCompany(body.squadId())) continue;
            free.add(body);
        }
        return free;
    }

    private static float strengthOf(Company company, Map<Integer, Body> byId) {
        float sum = 0f;
        for (int id : company.members) {
            Body body = byId.get(id);
            if (body != null) sum += weight(body.alive(), body.machine());
        }
        return sum;
    }

    private static boolean arrived(Company company, Body body) {
        return Math.hypot(body.anchorX() - company.rallyX, body.anchorY() - company.rallyY) <= RALLY_RADIUS_CELLS;
    }

    /** Recruits, re-checks the rally point, judges readiness. Returns how many squads were newly recruited. */
    private int muster(int tick, Objective objective, Company company, Map<Integer, Body> byId, int[] drop,
                       List<int[]> hostiles, Ground ground, int roomLeft, List<String> notes) {
        int required = required(objective.family(), objective.believedContacts(),
                failures.getOrDefault(objective.fortId(), 0));
        if (!safe(company.rallyX, company.rallyY, hostiles)) {
            int[] rally = rallyCell(drop[0], drop[1], objective.cellX(), objective.cellY(), ground, hostiles);
            company.rallyX = rally[0];
            company.rallyY = rally[1];
            notes.add("company for fort " + company.fortId + " rally moved to (" + rally[0] + "," + rally[1]
                    + "): a hostile is believed near the old one");
        }
        int recruited = 0;
        while (strengthOf(company, byId) < required && recruited < roomLeft) {
            Body pick = null;
            double bestGap = Double.MAX_VALUE;
            for (Body body : freeBodies(byId)) {
                // A squad that cannot be shown to share the drop zone's ground is left to the lanes.
                if (!ground.ok(body.anchorX(), body.anchorY())) continue;
                double gap = Math.hypot(body.anchorX() - company.rallyX, body.anchorY() - company.rallyY);
                if (gap < bestGap || (gap == bestGap && pick != null && body.squadId() < pick.squadId())) {
                    pick = body;
                    bestGap = gap;
                }
            }
            if (pick == null) break;
            company.members.add(pick.squadId());
            recruited++;
            company.timeoutTick = Math.max(company.timeoutTick,
                    tick + (int) (TICKS_PER_CELL * bestGap) + ARRIVAL_SLACK_TICKS);
        }
        float arrivedStrength = 0f;
        int arrivedSquads = 0;
        for (int id : company.members) {
            Body body = byId.get(id);
            if (body != null && arrived(company, body)) {
                arrivedStrength += weight(body.alive(), body.machine());
                arrivedSquads++;
            }
        }
        // Everything this company can get is at the rally and it is still short: wait a grace for anything yet to
        // come, not the whole timeout.
        boolean exhausted = strengthOf(company, byId) < required
                && (freeBodies(byId).isEmpty() || roomLeft - recruited <= 0);
        if (exhausted && !company.members.isEmpty() && arrivedSquads == company.members.size()) {
            company.timeoutTick = Math.min(company.timeoutTick, company.graceFrom(tick) + STRAGGLER_GRACE_TICKS);
        } else {
            company.fullSince = -1;
        }
        Readiness readiness = judge(arrivedStrength, required, tick, company.timeoutTick);
        if (readiness != Readiness.GATHERING && !company.members.isEmpty()) {
            company.stage = Stage.ASSAULT;
            company.stageTick = tick;
            company.strengthAtCommit = strengthOf(company, byId);
            company.reason = (readiness == Readiness.READY ? "ready: " : "timed out: ") + Math.round(arrivedStrength)
                    + " at the rally of " + required + " believed needed, committing "
                    + Math.round(company.strengthAtCommit) + " in " + company.members.size() + " squads";
            notes.add("company for fort " + company.fortId + " commits (" + company.reason + ")");
        } else if (readiness != Readiness.GATHERING) {
            // Nobody to commit: let the muster form again from whatever arrives, with a fresh clock.
            companies.remove(company.fortId);
        }
        return recruited;
    }

    private void gather(Company company, Map<Integer, Body> byId, Map<Integer, Order> orders) {
        if (!companies.containsKey(company.fortId)) return;
        for (int id : company.members) {
            if (!byId.containsKey(id)) continue;
            orders.put(id, new Order(ObjectiveAssignment.advanceTrack(id, company.rallyX, company.rallyY),
                    AssignmentReason.MUSTER_GATHER));
        }
    }

    private void assault(int tick, Faction perspective, Objective objective, Company company, Map<Integer, Body> byId,
                         CommandTopology topology, Map<Integer, Order> orders, List<String> notes) {
        List<DefenceStructureTactics.Candidate> candidates = new ArrayList<>();
        for (int id : company.members) {
            Body body = byId.get(id);
            if (body == null) continue;
            candidates.add(new DefenceStructureTactics.Candidate(id, body.anchorX(), body.anchorY(), body.incumbent(),
                    body.demolition(), body.machine()));
        }
        List<DefenceStructureTactics.Task> tasks = DefenceStructureTactics.planMassed(
                perspective, objective.aspects(), candidates, topology);
        if (tasks.isEmpty()) {
            notes.add("company for fort " + company.fortId + " dissolved: no aspect of it can be reached");
            companies.remove(company.fortId);
            return;
        }
        Map<Integer, DefenceStructureTactics.Task> byTask = new TreeMap<>();
        for (DefenceStructureTactics.Task task : tasks) byTask.put(task.squadId(), task);
        company.members.removeIf(id -> !byTask.containsKey(id));

        Map<Integer, Double> remaining = new TreeMap<>();
        for (DefenceStructureTactics.Task task : tasks) {
            Body body = byId.get(task.squadId());
            remaining.put(task.squadId(), Math.hypot(body.anchorX() - task.structure().cellX(),
                    body.anchorY() - task.structure().cellY()));
        }
        Map<Integer, Boolean> holds = cohesion(tick, company, byId, remaining);
        for (DefenceStructureTactics.Task task : tasks) {
            Body body = byId.get(task.squadId());
            if (holds.getOrDefault(task.squadId(), false)) {
                orders.put(task.squadId(), new Order(
                        ObjectiveAssignment.advanceTrack(task.squadId(), body.anchorX(), body.anchorY()),
                        AssignmentReason.ASSAULT_COMPANY_RALLY));
            } else {
                orders.put(task.squadId(), new Order(task.assignment(), AssignmentReason.ASSAULT_COMMIT));
            }
        }
    }

    /**
     * Which company squads wait for the rest on the approach. The same bounds as a command group's rally
     * ({@link ConquestGroupCommand#BOUND_CELLS}, {@link ConquestGroupCommand#LATE_ARRIVAL_CELLS},
     * {@link ConquestGroupCommand#WAIT_TICKS}), measured as progress toward the objective rather than along a
     * lane: a squad more than the bound ahead of the company's rearward median waits, once per genuine regroup, for
     * at most the wait; a straggler more than the late-arrival distance behind the lead is not waited for; and a
     * squad in contact, or inside the engage ring, is never held.
     */
    private Map<Integer, Boolean> cohesion(int tick, Company company, Map<Integer, Body> byId,
                                           Map<Integer, Double> remaining) {
        Map<Integer, Boolean> holds = new TreeMap<>();
        List<Integer> quiet = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : remaining.entrySet()) {
            Body body = byId.get(entry.getKey());
            if (body.inContact() || entry.getValue() <= ENGAGE_RING_CELLS) continue;
            quiet.add(entry.getKey());
        }
        if (quiet.size() < 2) { company.waitStarted = -1; return holds; }
        double lead = quiet.stream().mapToDouble(id -> -remaining.get(id)).max().orElse(0);
        List<Integer> body = quiet.stream().filter(id -> lead - (-remaining.get(id))
                <= ConquestGroupCommand.LATE_ARRIVAL_CELLS).toList();
        if (body.size() < 2) { company.waitStarted = -1; return holds; }
        double[] positions = body.stream().mapToDouble(id -> -remaining.get(id)).sorted().toArray();
        double supported = positions[(positions.length - 1) / 2];
        if (lead - supported <= ConquestGroupCommand.BOUND_CELLS) {
            company.waitStarted = -1;
            company.boundReleased = false;
            return holds;
        }
        if (company.boundReleased) return holds;
        if (company.waitStarted < 0) company.waitStarted = tick;
        if (tick - company.waitStarted >= ConquestGroupCommand.WAIT_TICKS) {
            company.boundReleased = true;
            return holds;
        }
        for (int id : body) {
            if (-remaining.get(id) - supported > ConquestGroupCommand.BOUND_CELLS) holds.put(id, true);
        }
        return holds;
    }

    private CompanyState stateOf(int tick, Objective objective, Company company, Map<Integer, Body> byId) {
        int arrivedSquads = 0;
        for (int id : company.members) {
            Body body = byId.get(id);
            if (body == null) continue;
            if (company.stage == Stage.ASSAULT || arrived(company, body)) arrivedSquads++;
        }
        int required = required(objective.family(), objective.believedContacts(),
                failures.getOrDefault(objective.fortId(), 0));
        String reason = company.stage == Stage.MUSTER
                ? "gathering: " + arrivedSquads + " of " + company.members.size() + " squads at the rally, believes it takes "
                + required + ", commits by tick " + company.timeoutTick
                : company.reason;
        return new CompanyState(objective.fortId(), company.stage.name(), new ArrayList<>(company.members), arrivedSquads,
                Math.round(strengthOf(company, byId)), required, company.rallyX, company.rallyY,
                objective.cellX(), objective.cellY(), tick - company.stageTick,
                company.stage == Stage.MUSTER ? company.timeoutTick : -1, reason);
    }
}
