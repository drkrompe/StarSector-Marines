package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Reactive manning for a mission defender commander (`garrisonables.md`, mission defenders): the Raid, Assault,
 * Sabotage and Extraction defenders man the garrisonables of their maps — building envelopes, fighting-position
 * stretches, compound aprons — in answer to their own believed pressure, through the same side-neutral assignment tier
 * Conquest's defender uses ({@link GarrisonDirector}) and under its laws.
 *
 * <p><b>The mission decides first.</b> The commander plans its own duties exactly as it would without this, then
 * {@link Offer offers} squads: a {@link Offer#reserve() roving} offer for a squad it would hold in reserve, a
 * {@link Offer#duty duty} offer for a squad on a stationary mission post, which may man only a place within
 * {@link #DUTY_RADIUS_CELLS} of that post (the post stays covered, from a window rather than the street). Nothing else
 * is offered, so authored garrisons, external owners, chasers and machines are never touched. The answer replaces the
 * commander's own proposal for the squads it names and leaves every other proposal as it was. A squad the commander
 * stops offering — its alarm took it, its duty moved away — is forgotten at once: the mission outranks manning.
 *
 * <p><b>Laws.</b> Belief only (the side's own influence and contacts); the director's start/release hysteresis and
 * dwell; pickets before massing; the reserve floor over roving offers alone, since a duty offer's post is already
 * covered by the squad standing on it; the useful-strength floor; the arrival bound; candidate places found through
 * the registry's block index; a bounded number sent per pulse. A building whose born garrison still stands in it counts
 * as manned. A squad falls back through layers: off an apron into the envelope of the building it fronts, out of an
 * envelope into its rooms (an area defence inside the building, held until the building has been quiet for
 * {@link #INSIDE_COOL_PLANS} plans), and with no layer left it is given back.
 *
 * <p>Pure planning over a frozen frame; one instance per commander, so per simulation. {@link #PROPERTY} switched off
 * is the control: nothing is offered, nothing is planned, and the battle is as it was.
 */
final class MissionGarrisons {

    /** {@code -Dbattle.mission.reactiveDefence=false} switches the mission defenders' reactive manning off. */
    static final String PROPERTY = "battle.mission.reactiveDefence";
    /** How far from its post (cells; a building's box, a stretch's centre) a duty squad may man a place. UNTUNED. */
    static final int DUTY_RADIUS_CELLS = 12;
    /** Largest circle of the area defence a squad driven out of an envelope holds in the building's rooms. UNTUNED. */
    static final int INSIDE_RADIUS_CELLS = 6;
    /** Plans a squad holding the rooms stays once its building is quiet. UNTUNED; Conquest's value. */
    static final int INSIDE_COOL_PLANS = 8;
    /** How near a squad in a fight a place must be (cells; as {@link #DUTY_RADIUS_CELLS} measures) to be sent there. UNTUNED. */
    static final int IN_FIGHT_REACH_CELLS = 8;
    /** How far outside a building's box an apron may stand and still front it. */
    static final int APRON_REACH_CELLS = CompoundGround.REACH_CELLS;

    static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true"));
    }

    /**
     * A squad the commander puts at this planner's disposal for one pulse. A roving one is a reserve squad; a duty one
     * stands on a post at {@code (dutyX, dutyY)} and may man only what is near it.
     */
    record Offer(boolean roving, int dutyX, int dutyY) {
        static Offer reserve() { return new Offer(true, -1, -1); }

        static Offer duty(int x, int y) { return new Offer(false, x, y); }
    }

    /** What this planner does with a squad this pulse. */
    enum Kind {
        /** Newly sent to man a place. */
        MAN,
        /** Kept on the place it mans (or moved along its run onto the stretch the attack now faces). */
        STAND,
        /** Driven off a place into the next layer in: the envelope behind an apron. */
        FALL_BACK,
        /** Driven out of an envelope, or still holding, its building's rooms. */
        ROOMS,
        /** A lance standing guard beside a place its side mans: no mission order, the guard is given after commit. */
        GUARD,
        /** A reserve squad waiting near the places a pressure area presses, still offered and free to man them. */
        STAGE,
        /** A reserve squad meeting a pressure area's attack in the street, short of the places it is coming for. */
        FORWARD;

        /** The proposal and directive reason a commander publishes for it. */
        String reason() { return "GARRISONABLE_" + name(); }
    }

    /** One squad's order from this planner. */
    record Manning(int squadId, Kind kind, ObjectiveAssignment order, int place, int cellX, int cellY) {
        /** A squad's manning order, at the order's own cell. */
        Manning(int squadId, Kind kind, ObjectiveAssignment order, int place) {
            this(squadId, kind, order, place, order.targetCellX(), order.targetCellY());
        }

        /** The kind of the order it carries, or {@code null} for a guard, which carries none. */
        AssignmentKind assignmentKind() { return order == null ? null : order.kind(); }
    }

    /** Running totals over a battle; for evidence. */
    static final class Stats {
        int pulses;
        int manOrders;
        int releases;
        int fallBacks;
        int roomsHolds;
        int shifts;
        int holderPulses;
        int peakHolders;
        int guardAttaches;
        int guardStandDowns;
        int peakGuards;
        /** Manning orders given to a squad the flow tier had staged for the place's area. */
        int manFromStage;
        /** Manning orders whose squad stood on its place before a believed contact reached the place. */
        int arrivedFirst;
        /** Manning orders whose squad was reached by the contact (at the place, or in a fight on the way) first. */
        int contactFirst;
        /** Manning orders that ended (given back, taken back, lost) before either. */
        int arrivalUnresolved;
        final Set<Integer> placesManned = new TreeSet<>();

        int distinctPlaces() { return placesManned.size(); }

        @Override public String toString() {
            return "pulses=" + pulses + " man=" + manOrders + " releases=" + releases + " fallBacks=" + fallBacks
                    + " rooms=" + roomsHolds + " shifts=" + shifts + " holderPulses=" + holderPulses
                    + " peak=" + peakHolders + " places=" + distinctPlaces();
        }
    }

    /** A squad holding a building's rooms after being driven out of its envelope. */
    private static final class Inside {
        final ObjectiveAssignment order;
        final TacticalNode building;
        int quietPlans;

        Inside(ObjectiveAssignment order, TacticalNode building) {
            this.order = order;
            this.building = building;
        }
    }

    private final GarrisonDirector director = new GarrisonDirector();
    private final Map<Integer, Inside> inside = new TreeMap<>();
    /** Squads this planner holds a place with (or has just sent): forgotten the moment they are no longer offered. */
    private final Set<Integer> tracked = new TreeSet<>();
    private final Stats stats = new Stats();
    private final MachineGuardDirector guards = new MachineGuardDirector();
    private final PlaceFlow flow = new PlaceFlow();
    /** Squads sent to man a place and not yet resolved as arriving before or after the attack: squad to place. */
    private final Map<Integer, Integer> arriving = new TreeMap<>();
    private List<MachineGuardDirector.Attach> guardsToOrder = List.of();
    private List<Integer> guardsToStandDown = List.of();
    private int[] contactScratch = new int[64];

    Stats stats() { return stats; }

    /** The flow tier's figures; for evidence. */
    PlaceFlow flow() { return flow; }

    /**
     * One pulse: which offered squads man, stand, fall back or hold rooms. Squads not in the answer keep the mission's
     * own proposal — including every holder given back this pulse.
     *
     * @param offers by squad id; squads absent are not this planner's to move
     * @param issuer the commander's strategy id, to recognise the places its own orders man
     */
    Map<Integer, Manning> plan(CommandFrame frame, GarrisonableRegistry places, Map<Integer, Offer> offers,
                               String issuer) {
        CommanderInfluenceSnapshot influence = frame.influence();
        if (!enabled() || !HeldGround.usable(places, influence)) return new TreeMap<>();
        int count = contacts(influence);
        CommandTopology topology = frame.topology();
        return plan(frame.squads(), places, (bx, by) -> influence.hostileAt(bx, by), contactScratch, count,
                (sx, sy, x, y) -> topology.inBounds(sx, sy) && topology.isWalkable(sx, sy)
                        && (sx == x && sy == y || topology.reachable(sx, sy, x, y)),
                (x, y) -> topology.inBounds(x, y) && topology.isWalkable(x, y), offers, issuer);
    }

    /** As the full seam, with every cell standable: the unit tests' grid-free form. */
    Map<Integer, Manning> plan(List<CommandSquadState> roster, GarrisonableRegistry places,
                               GarrisonDirector.Pressure field, int[] contactXY, int contactCount,
                               HeldGround.Reach reach, Map<Integer, Offer> offers, String issuer) {
        return plan(roster, places, field, contactXY, contactCount, reach, (x, y) -> true, offers, issuer);
    }

    /**
     * As the frame form, over a pressure field laid on the registry's own block lattice, packed believed contact cells
     * and a reachability test: the seam unit tests use.
     */
    Map<Integer, Manning> plan(List<CommandSquadState> roster, GarrisonableRegistry places,
                               GarrisonDirector.Pressure field, int[] contactXY, int contactCount,
                               HeldGround.Reach reach, GarrisonFlow.CellOk stand, Map<Integer, Offer> offers,
                               String issuer) {
        Map<Integer, Manning> out = new TreeMap<>();
        stats.pulses++;

        Map<Integer, CommandSquadState> squads = new TreeMap<>();
        Set<Integer> ownGarrisoned = new TreeSet<>();
        for (CommandSquadState squad : roster) {
            if (squad.aliveMembers() <= 0) continue;
            squads.put(squad.squadId(), squad);
            if (squad.role() == UnitRole.GARRISON && !offers.containsKey(squad.squadId())) {
                // A building its own born garrison still stands in is manned: declared holds stay held.
                int place = places.idAt((int) squad.centroidX(), (int) squad.centroidY());
                if (place >= 0 && places.isEnvelope(place)) ownGarrisoned.add(place);
            }
        }

        // The mission took these back, or they are gone: forget them before anything is priced.
        for (Integer id : new ArrayList<>(tracked)) {
            CommandSquadState squad = squads.get(id);
            if (squad == null || !offerable(squad, offers.get(id))) {
                tracked.remove(id);
                director.forget(id);
            }
        }
        inside.keySet().removeIf(id -> !squads.containsKey(id) || !offerable(squads.get(id), offers.get(id)));
        for (Integer id : new ArrayList<>(flow.posts().keySet())) {
            Offer offer = offers.get(id);
            if (offer == null || !offer.roving() || !offerable(squads.get(id), offer)) flow.forget(id);
        }
        boolean flowOn = PlaceFlow.enabled();
        List<GarrisonFlow.Area> areas = flowOn ? areas(places, field, contactXY, contactCount) : List.of();

        List<GarrisonDirector.Garrison> held = new ArrayList<>();
        Set<Integer> busy = new HashSet<>();
        for (Map.Entry<Integer, Offer> entry : offers.entrySet()) {
            CommandSquadState squad = squads.get(entry.getKey());
            if (!offerable(squad, entry.getValue())) continue;
            if (keepsRooms(squad, entry.getValue(), field, places.blockSize(), out)) {
                busy.add(squad.squadId());
                continue;
            }
            GarrisonDirector.Garrison garrison = holderOf(squad, entry.getValue(), places, issuer);
            if (garrison != null) {
                held.add(garrison);
                busy.add(squad.squadId());
            }
        }

        Set<Integer> release = new HashSet<>(director.evaluate(places, field, contactXY, contactCount, held));
        Set<Integer> fallingBack = new HashSet<>(director.fallingBack());
        Map<Integer, Integer> shiftedTo = new HashMap<>();
        for (GarrisonDirector.Shift shift : director.shifting()) shiftedTo.put(shift.squadId(), shift.to());
        Set<Integer> justReleased = new HashSet<>();
        for (GarrisonDirector.Garrison garrison : held) {
            int id = garrison.squadId();
            CommandSquadState squad = squads.get(id);
            int place = garrison.garrisonable();
            resolveArrival(id, place, garrison.arrived(), squad.localContact(), places, contactXY, contactCount);
            boolean breached = fallingBack.contains(id)
                    || !garrison.arrived() && director.lockedOut(places.centreX(place), places.centreY(place));
            if (breached) {
                director.forget(id);
                tracked.remove(id);
                Manning back = fallBack(squad, place, places);
                if (back != null) {
                    out.put(id, back);
                    if (back.kind() == Kind.FALL_BACK) tracked.add(id);
                    stats.fallBacks++;
                } else {
                    justReleased.add(id);
                    stats.releases++;
                }
                continue;
            }
            if (release.contains(id)) {
                director.forget(id);
                tracked.remove(id);
                justReleased.add(id);
                stats.releases++;
                continue;
            }
            int onto = shiftedTo.getOrDefault(id, place);
            if (onto != place) stats.shifts++;
            out.put(id, new Manning(id, Kind.STAND, GarrisonDirector.orderFor(id, onto, places), onto));
            tracked.add(id);
        }
        director.heldByOwnGarrison(ownGarrisoned);

        List<GarrisonDirector.Free> free = new ArrayList<>();
        for (Map.Entry<Integer, Offer> entry : offers.entrySet()) {
            int id = entry.getKey();
            CommandSquadState squad = squads.get(id);
            if (!offerable(squad, entry.getValue()) || busy.contains(id) || justReleased.contains(id)) continue;
            // A duty squad's post is covered by the squad itself, and a squad already fighting is not held in reserve:
            // neither is the roving reserve the floor protects.
            PlaceFlow.Post post = flow.post(id);
            // A forward squad meets the attack in the street: it is not sent to man.
            if (post != null && post.forward) continue;
            boolean staged = !entry.getValue().roving() || squad.localContact() || post != null;
            free.add(new GarrisonDirector.Free(id, squad.anchorCellX(), squad.anchorCellY(), staged,
                    squad.aliveMembers(), squad.originalMembers()));
        }
        List<GarrisonDirector.Man> sent = director.assign(free, (squadId, place) -> {
            Offer offer = offers.get(squadId);
            CommandSquadState squad = squads.get(squadId);
            if (offer == null || squad == null || !withinDuty(offer, place, places)) return false;
            // A squad in a fight takes the cover beside it, never a march away from it.
            if (squad.localContact() && gapTo(place, squad.anchorCellX(), squad.anchorCellY(), places)
                    > IN_FIGHT_REACH_CELLS) return false;
            return reach.to(squad.anchorCellX(), squad.anchorCellY(), places.centreX(place), places.centreY(place));
        }, areas.isEmpty() ? null : (squadId, place) -> {
            // The squads waiting for the place's own area are drawn on before any other.
            int own = PlaceFlow.areaOf(flow.post(squadId), areas);
            return own >= 0 && own == GarrisonFlow.matchingArea(areas, places.centreX(place), places.centreY(place));
        });
        for (GarrisonDirector.Man man : sent) {
            out.put(man.squadId(), new Manning(man.squadId(), Kind.MAN,
                    GarrisonDirector.orderFor(man.squadId(), man.garrisonable(), places), man.garrisonable()));
            tracked.add(man.squadId());
            if (flow.post(man.squadId()) != null) {
                flow.forget(man.squadId());
                stats.manFromStage++;
            }
            arriving.put(man.squadId(), man.garrisonable());
            stats.manOrders++;
            stats.placesManned.add(man.garrisonable());
        }
        stats.holderPulses += out.size();
        stats.peakHolders = Math.max(stats.peakHolders, out.size());
        for (Integer id : new ArrayList<>(arriving.keySet())) {
            if (tracked.contains(id)) continue;
            arriving.remove(id);
            stats.arrivalUnresolved++;
        }
        if (flowOn) {
            flowToPressure(areas, squads, offers, out, busy, justReleased, ownGarrisoned, places, reach, stand, field);
        }
        planGuards(squads, offers, out, ownGarrisoned, places);
        return out;
    }

    /**
     * The flow tier ({@link PlaceFlow}): gives back the squads whose area has gone quiet, stages free roving squads for
     * the areas that press a place, back at the places or forward to meet the attack, and keeps every waiting squad on
     * its post. Only roving offers are staged -- never a duty post, a squad fighting, one too weak to man, a machine --
     * and a staged squad is still offered: local assignment draws it first for its area's places.
     */
    private void flowToPressure(List<GarrisonFlow.Area> areas, Map<Integer, CommandSquadState> squads,
                                Map<Integer, Offer> offers, Map<Integer, Manning> out, Set<Integer> busy,
                                Set<Integer> justReleased, Set<Integer> ownGarrisoned, GarrisonableRegistry places,
                                HeldGround.Reach reach, GarrisonFlow.CellOk stand, GarrisonDirector.Pressure field) {
        flow.cool(areas);
        int[] heldIn = new int[areas.size()];
        for (Manning m : out.values()) {
            if (m.place() < 0) continue;
            int a = GarrisonFlow.matchingArea(areas, places.centreX(m.place()), places.centreY(m.place()));
            if (a >= 0) heldIn[a]++;
        }
        for (int place : ownGarrisoned) {
            int a = GarrisonFlow.matchingArea(areas, places.centreX(place), places.centreY(place));
            if (a >= 0) heldIn[a]++;
        }
        List<PlaceFlow.Candidate> free = new ArrayList<>();
        int pool = 0;
        for (Map.Entry<Integer, Offer> entry : offers.entrySet()) {
            int id = entry.getKey();
            CommandSquadState squad = squads.get(id);
            if (!entry.getValue().roving() || !offerable(squad, entry.getValue())) continue;
            pool++;
            if (out.containsKey(id) || busy.contains(id) || justReleased.contains(id) || flow.post(id) != null
                    || fighting(squad)
                    || squad.aliveMembers() < GarrisonDirector.usefulFloor(squad.originalMembers(), squad.aliveMembers())) {
                continue;
            }
            free.add(new PlaceFlow.Candidate(id, squad.anchorCellX(), squad.anchorCellY()));
        }
        int size = places.blockSize();
        flow.stage(areas, director.warmPlaces(), director.hotPlaces(), director::isManned, places, heldIn, free, pool,
                reach, stand, (x, y) -> field.hostileAt(Math.max(0, x / size), Math.max(0, y / size))
                        < GarrisonDirector.FLOW_PRESSURE);
        for (Map.Entry<Integer, PlaceFlow.Post> entry : flow.posts().entrySet()) {
            int id = entry.getKey();
            if (out.containsKey(id)) continue;
            PlaceFlow.Post post = entry.getValue();
            out.put(id, new Manning(id, post.forward ? Kind.FORWARD : Kind.STAGE, post.order, -1));
        }
    }

    /**
     * Whether a squad is in a fight rather than merely aware of one: shot at in the last moments or firing itself. A
     * squad's local contact is any hostile its side believes in and it could act on, which on a mission's small map is
     * nearly every squad as soon as a sentry has seen the attack; staging only spares the squads actually fighting.
     */
    private static boolean fighting(CommandSquadState squad) {
        return squad.underFireRecently() || squad.coolingDownMembers() > 0;
    }

    /** The pressure areas of this plan: the distinct blocks the believed contacts stand in, clustered. */
    private static List<GarrisonFlow.Area> areas(GarrisonableRegistry places, GarrisonDirector.Pressure field,
                                                 int[] contactXY, int contactCount) {
        int size = places.blockSize();
        int bw = places.blockWidth();
        int bh = places.blockHeight();
        Set<Integer> seen = new TreeSet<>();
        for (int i = 0; i < contactCount; i++) {
            int bx = Math.min(bw - 1, Math.max(0, contactXY[2 * i] / size));
            int by = Math.min(bh - 1, Math.max(0, contactXY[2 * i + 1] / size));
            seen.add(by * bw + bx);
        }
        int[] blocks = new int[seen.size()];
        float[] pressure = new float[seen.size()];
        int n = 0;
        for (int block : seen) {
            blocks[n] = block;
            pressure[n] = field.hostileAt(block % bw, block / bw);
            n++;
        }
        return GarrisonFlow.areas(blocks, pressure, n, bw, size);
    }

    /** How near a believed contact must stand to a place (cells, as {@link #gapTo} measures) to have reached it. */
    static final int ARRIVAL_CONTACT_CELLS = 3;

    /**
     * Resolves a manning order once: arrived first when the squad stands on its place while no believed contact has
     * reached the place; contact first when a contact reached it, or the squad is in a fight, before it got there.
     */
    private void resolveArrival(int id, int place, boolean arrived, boolean inFight, GarrisonableRegistry places,
                                int[] contactXY, int contactCount) {
        Integer sentTo = arriving.get(id);
        if (sentTo == null) return;
        // Shifted along its run: judge the stretch it now holds.
        if (sentTo != place) arriving.put(id, place);
        boolean reached = false;
        for (int i = 0; i < contactCount && !reached; i++) {
            reached = gapTo(place, contactXY[2 * i], contactXY[2 * i + 1], places) <= ARRIVAL_CONTACT_CELLS;
        }
        if (arrived && !reached) {
            stats.arrivedFirst++;
            arriving.remove(id);
        } else if (reached || inFight) {
            stats.contactFirst++;
            arriving.remove(id);
        }
    }

    /**
     * The machine guard's commander half for a mission defender (`garrisonables.md`, machine guard): reviews every
     * lance on guard and stands down those whose place is no longer manned, has cooled for the dwell, or that the
     * mission stopped offering (it took them for other work); then attaches a free lance — one the mission offers as
     * reserve, or one already fighting near the place — to a manned, pressed place nobody guards, within
     * {@link MachineGuardDirector}'s bounds. Guarding lances join the answer as {@link Kind#GUARD}, so the commander
     * gives them no order of its own; the guards themselves are given in {@link #afterCommit}.
     */
    private void planGuards(Map<Integer, CommandSquadState> squads, Map<Integer, Offer> offers,
                            Map<Integer, Manning> out, Set<Integer> ownGarrisoned, GarrisonableRegistry places) {
        Set<Integer> mannedIds = new TreeSet<>(ownGarrisoned);
        for (Manning m : out.values()) {
            int place = m.kind() == Kind.ROOMS ? places.idAt(m.cellX(), m.cellY()) : m.place();
            if (place >= 0) mannedIds.add(place);
        }
        List<MachineGuardDirector.Place> manned = MachineGuardDirector.manned(mannedIds, director.hotPlaces(), places);
        Map<Integer, MachineGuardDirector.Machine> guarding = new TreeMap<>();
        List<MachineGuardDirector.Machine> free = new ArrayList<>();
        int machineSquads = 0;
        for (CommandSquadState squad : squads.values()) {
            if (!squad.machine()) continue;
            machineSquads++;
            Offer offer = offers.get(squad.squadId());
            if (offer == null || !offer.roving()) continue;
            MachineGuardDirector.Machine machine = new MachineGuardDirector.Machine(squad.squadId(),
                    squad.anchorCellX(), squad.anchorCellY(), squad.aliveMembers(), squad.originalMembers(),
                    squad.localContact());
            if (guards.holds(squad.squadId())) guarding.put(squad.squadId(), machine);
            else free.add(machine);
        }
        List<Integer> down = guards.review(guarding, manned, places::idAt);
        stats.guardStandDowns += down.size();
        List<MachineGuardDirector.Attach> sent = guards.attach(free, manned, machineSquads, places::idAt);
        stats.guardAttaches += sent.size();
        for (Map.Entry<Integer, MachineGuardDirector.Machine> entry : guarding.entrySet()) {
            int id = entry.getKey();
            if (!guards.holds(id)) continue;
            out.put(id, new Manning(id, Kind.GUARD, null, -1, entry.getValue().x(), entry.getValue().y()));
        }
        for (MachineGuardDirector.Attach attach : sent) {
            out.put(attach.squadId(), new Manning(attach.squadId(), Kind.GUARD, null, attach.garrisonable(),
                    attach.cellX(), attach.cellY()));
        }
        stats.peakGuards = Math.max(stats.peakGuards, guards.count());
        guardsToOrder = sent;
        guardsToStandDown = down;
    }

    /** Gives the guards this pulse attached and stands down the ones it ended; call from the commander's afterCommit. */
    void afterCommit(CommandEffects effects, Faction side) {
        for (int squadId : guardsToStandDown) effects.standDownGuard(squadId);
        for (MachineGuardDirector.Attach attach : guardsToOrder) {
            if (effects.guardPlace(side, attach.squadId(), attach.cellX(), attach.cellY()) == 0) {
                guards.refused(attach.squadId(), attach.garrisonable());
            }
        }
        guardsToOrder = List.of();
        guardsToStandDown = List.of();
    }

    /** Lances on guard by a manned place; for tests and evidence. */
    int guardCount() {
        return guards.count();
    }

    /**
     * The commander's proposals with this pulse's manning in place of its own for the squads it names. A squad whose
     * order is unchanged keeps it without breaking its stability; any other change breaks it.
     */
    static List<CommandProposal> rewrite(List<CommandProposal> proposals, Map<Integer, Manning> manning,
                                         List<CommandSquadState> roster) {
        if (manning.isEmpty()) return proposals;
        Map<Integer, CommandSquadState> squads = new HashMap<>();
        for (CommandSquadState squad : roster) squads.put(squad.squadId(), squad);
        List<CommandProposal> out = new ArrayList<>(proposals.size() + manning.size());
        Set<Integer> done = new HashSet<>();
        for (CommandProposal proposal : proposals) {
            Manning m = manning.get(proposal.squadId());
            if (m == null) {
                out.add(proposal);
            } else if (done.add(proposal.squadId())) {
                out.add(proposalFor(m, squads.get(proposal.squadId())));
            }
        }
        for (Manning m : manning.values()) {
            if (done.add(m.squadId())) out.add(proposalFor(m, squads.get(m.squadId())));
        }
        return out;
    }

    private static CommandProposal proposalFor(Manning manning, CommandSquadState squad) {
        ObjectiveAssignment current = squad == null ? null
                : squad.directive() != null ? squad.directive().assignment() : squad.assignment();
        if (manning.kind() == Kind.GUARD) {
            // The lance holds no mission order while it guards: its guard is the machine tier's.
            return CommandProposal.claim(manning.squadId(), CommandAuthority.MISSION_COMMAND, manning.kind().reason(),
                    current == null ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED);
        }
        boolean same = Objects.equals(current, manning.order());
        return CommandProposal.assign(manning.order(), CommandAuthority.MISSION_COMMAND, manning.kind().reason(),
                same ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED);
    }

    /** Whether this squad may be planned at all: offered, alive, and infantry the commander can post. */
    private static boolean offerable(CommandSquadState squad, Offer offer) {
        return offer != null && squad != null && squad.aliveMembers() > 0
                && !squad.machine() && !squad.crew() && !squad.demolition() && !squad.moraleBroken();
    }

    /** Whether a duty squad may man this place: a building within the duty radius of its post, or a stretch centred within it. */
    static boolean withinDuty(Offer offer, int place, GarrisonableRegistry places) {
        return offer.roving() || gapTo(place, offer.dutyX(), offer.dutyY(), places) <= DUTY_RADIUS_CELLS;
    }

    /** Chebyshev cells from a cell to a place: to a building's box (0 inside it), or to a stretch's centre. */
    static int gapTo(int place, int x, int y, GarrisonableRegistry places) {
        if (places.isEnvelope(place)) {
            TacticalNode b = places.building(place);
            return Math.max(0, Math.max(Math.max(b.left - x, x - b.right), Math.max(b.top - y, y - b.bottom)));
        }
        return Math.max(Math.abs(places.centreX(place) - x), Math.abs(places.centreY(place) - y));
    }

    /**
     * The place this squad holds by an order of ours, or has just been sent to, or {@code null}. A place gone from the
     * registry, or (for a duty squad) moved out of its duty's reach, frees it.
     */
    private GarrisonDirector.Garrison holderOf(CommandSquadState squad, Offer offer, GarrisonableRegistry places,
                                               String issuer) {
        ObjectiveAssignment order = squad.assignment();
        CommandDirective directive = squad.directive();
        int cellX;
        int cellY;
        if (order != null && order.mansPlace() && directive != null && issuer.equals(directive.issuer())) {
            cellX = order.targetCellX();
            cellY = order.targetCellY();
            director.adopted(squad.squadId());
        } else {
            int[] cell = director.pendingCell(squad.squadId());
            if (cell == null) return null;
            cellX = cell[0];
            cellY = cell[1];
        }
        int place = places.idAt(cellX, cellY);
        if (place < 0 || !withinDuty(offer, place, places)) {
            director.forget(squad.squadId());
            tracked.remove(squad.squadId());
            return null;
        }
        int reach = places.radius(place);
        boolean arrived = Math.abs(squad.centroidX() - places.centreX(place)) <= reach
                && Math.abs(squad.centroidY() - places.centreY(place)) <= reach;
        tracked.add(squad.squadId());
        return new GarrisonDirector.Garrison(squad.squadId(), place, !squad.localContact(), arrived,
                squad.aliveMembers(), (int) squad.centroidX(), (int) squad.centroidY(), squad.originalMembers());
    }

    /**
     * Whether a squad driven into a building's rooms still holds them: it carries the order it was given there, the
     * building is within its duty, and it has not been quiet for {@link #INSIDE_COOL_PLANS} plans (never released in a
     * fight). A squad that stops is forgotten and the mission's own proposal stands.
     */
    private boolean keepsRooms(CommandSquadState squad, Offer offer, GarrisonDirector.Pressure field, int blockSize,
                               Map<Integer, Manning> out) {
        Inside held = inside.get(squad.squadId());
        if (held == null) return false;
        ObjectiveAssignment current = squad.assignment();
        if (!held.order.equals(current) || !offer.roving() && !nearDuty(offer, held.building)) {
            inside.remove(squad.squadId());
            return false;
        }
        boolean quiet = field.hostileAt(held.building.anchorX / blockSize, held.building.anchorY / blockSize)
                < GarrisonDirector.RELEASE_PRESSURE;
        held.quietPlans = quiet && !squad.localContact() ? held.quietPlans + 1 : 0;
        if (held.quietPlans >= INSIDE_COOL_PLANS) {
            inside.remove(squad.squadId());
            stats.releases++;
            return true;
        }
        out.put(squad.squadId(), new Manning(squad.squadId(), Kind.ROOMS, held.order, -1));
        return true;
    }

    private static boolean nearDuty(Offer offer, TacticalNode b) {
        int gap = Math.max(Math.max(b.left - offer.dutyX(), offer.dutyX() - b.right),
                Math.max(b.top - offer.dutyY(), offer.dutyY() - b.bottom));
        return gap <= DUTY_RADIUS_CELLS;
    }

    /**
     * The next layer in for a squad driven off {@code place}: the envelope of the building an apron fronts (unless that
     * envelope is itself locked out after a breach), the rooms of the building an envelope wraps, or {@code null} — given
     * back — for a run that fronts no building.
     */
    private Manning fallBack(CommandSquadState squad, int place, GarrisonableRegistry places) {
        int id = squad.squadId();
        if (places.isEnvelope(place)) {
            TacticalNode building = places.building(place);
            int radius = Math.max(2, Math.min(INSIDE_RADIUS_CELLS,
                    Math.min(building.right - building.left, building.bottom - building.top) / 2));
            ObjectiveAssignment rooms = ObjectiveAssignment.defendArea(id, building.anchorX, building.anchorY, radius);
            inside.put(id, new Inside(rooms, building));
            stats.roomsHolds++;
            return new Manning(id, Kind.ROOMS, rooms, -1);
        }
        int envelope = frontedEnvelope(place, places);
        if (envelope < 0 || director.lockedOut(places.centreX(envelope), places.centreY(envelope))) return null;
        return new Manning(id, Kind.FALL_BACK, GarrisonDirector.orderFor(id, envelope, places), envelope);
    }

    /**
     * The envelope of the building a run's stretch stands in front of: the nearest one whose box is within
     * {@link #APRON_REACH_CELLS} of the stretch's centre, found through the registry's block index, or -1. A fort's belt
     * fronts its works, not a building.
     */
    static int frontedEnvelope(int place, GarrisonableRegistry places) {
        if (places.isEnvelope(place) || places.fortId(place) >= 0) return -1;
        int x = places.centreX(place);
        int y = places.centreY(place);
        int size = places.blockSize();
        int bx = Math.min(places.blockWidth() - 1, Math.max(0, x / size));
        int by = Math.min(places.blockHeight() - 1, Math.max(0, y / size));
        int best = -1;
        int bestGap = Integer.MAX_VALUE;
        for (int g : places.near(bx, by)) {
            if (!places.isEnvelope(g)) continue;
            TacticalNode b = places.building(g);
            int gap = Math.max(Math.max(b.left - x, x - b.right), Math.max(b.top - y, y - b.bottom));
            if (gap > APRON_REACH_CELLS || gap > bestGap || gap == bestGap && g > best) continue;
            best = g;
            bestGap = gap;
        }
        return best;
    }

    /** Packs the believed contacts heavy enough to wake a place into the scratch array; returns how many. */
    private int contacts(CommanderInfluenceSnapshot influence) {
        int count = 0;
        for (CommanderContact contact : influence.contacts()) {
            if (contact.confidence() * contact.strength() < GarrisonDirector.MIN_CONTACT_WEIGHT) continue;
            if (2 * count + 2 > contactScratch.length) {
                contactScratch = Arrays.copyOf(contactScratch, contactScratch.length * 2);
            }
            contactScratch[2 * count] = contact.cellX();
            contactScratch[2 * count + 1] = contact.cellY();
            count++;
        }
        return count;
    }
}
