package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.ObjectiveFactionPresence;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Cordon's story ({@code cordon-nouns.md}, Flow and Beats): the line's
 * redoubts moving through their states, the tides on the timetable, the beats
 * the battle is told in, and the end the company is held to.
 *
 * <p>The company's objective. The Line is won when the window closes with no
 * more redoubts fallen than the briefing allows, and lost the moment the line
 * breaks or the company is wiped out. The Road is decided on people, not the
 * line: the company holds Ain Tafrant while the convoys bring its townspeople
 * out; once the town is finished (everyone out, or abandoned when its staging
 * area is overrun) the company falls back behind the line, and the Road ends
 * there, won if enough townspeople came out. Read-only against the battle except
 * for the tide, which brings every living bore's surge forward and lets the
 * swarm out at the tide's strength until it ebbs, and the eruption, which
 * opens a new bore in front of the line.
 */
public final class CordonSequence implements Objective {

    /** A swarm body this close to a redoubt's wall is the line in contact. */
    static final int CONTACT_RANGE = 20;
    /** A Marine this close to a calling redoubt's wall has reached its gate. */
    static final int RELIEF_REACH = 3;

    /**
     * What the battle is held to: when the tides come and how long each runs,
     * the share of the bores' strength the swarm presses with between tides
     * and during one, when new bores erupt in front of the line, when the
     * Line's refugee groups set off and its allied columns come up, how long
     * the window, and how many redoubts may fall.
     */
    public record Timetable(List<Integer> tideSeconds, int tideLengthSeconds, float ordinaryPressure,
                            float tidePressure, List<Integer> eruptionSeconds, List<Integer> refugeeSeconds,
                            List<Integer> columnSeconds, int windowSeconds, int allowedFallen,
                            List<Integer> convoySeconds, int convoysNeeded) {
        /**
         * The briefing's ordinary timetable: three tides of a minute in a
         * fifteen-minute window, the swarm at a third of its strength between
         * them and two thirds in one, a new bore erupting between each pair of
         * tides, six refugee groups two minutes apart, a column up from the
         * rear two minutes before each tide (a column takes about that to march
         * up), one redoubt may fall. On the Road three convoys come on four minutes apart, the
         * last with time to be out before the window closes, and two of them must come back out.
         */
        public static final Timetable STANDARD = new Timetable(List.of(240, 480, 720), 60, 0.2f, 0.66f,
                List.of(360, 600), List.of(30, 150, 270, 390, 510, 630), List.of(120, 360, 600), 900, 1,
                List.of(60, 300, 540), 2);

        public Timetable {
            tideSeconds = List.copyOf(tideSeconds);
            eruptionSeconds = List.copyOf(eruptionSeconds);
            refugeeSeconds = List.copyOf(refugeeSeconds);
            columnSeconds = List.copyOf(columnSeconds);
            convoySeconds = List.copyOf(convoySeconds);
            if (convoysNeeded > convoySeconds.size())
                throw new IllegalArgumentException("more convoys needed than come: " + convoysNeeded);
            if (windowSeconds <= 0 || allowedFallen < 0 || tideLengthSeconds <= 0)
                throw new IllegalArgumentException("a Cordon needs a window");
            if (!(ordinaryPressure > 0f && ordinaryPressure <= tidePressure && tidePressure <= 1f))
                throw new IllegalArgumentException("pressures in (0, 1], a tide's no less than the ordinary: "
                        + ordinaryPressure + ", " + tidePressure);
        }
    }

    public enum Outcome { RUNNING, HELD, BROKEN }

    private final CordonVariant variant;
    private final List<CordonRedoubt> redoubts;
    private final Timetable timetable;
    private final StoryBeatLog log;
    /** On the Road, the redoubts beside the gap, by index; empty on the Line. */
    private final List<Integer> gap;
    /** Where each eruption on the timetable opens, in order; a {@code null} site is skipped. */
    private final int[][] eruptionSites;
    /** The number the first erupted bore takes: after the front's own. */
    private final int firstEruptedNumber;
    private int tides;
    private int eruptions;
    private CordonRefugees refugees;
    private AlliedColumns columns;
    private CordonArmour armour;
    private CordonOuterPicket outerPicket;
    private CordonStrikes strikes;
    private CordonLogistics logistics;
    private CordonAirBase airBase;
    private CordonConvoys convoys;
    /** The Road's cut-off town, or {@code null}. */
    private CordonTown town;
    private boolean townContact, townCalled;
    /** The townspeople the town started with, and how many must come out for the Road to be won. */
    private int population, peopleNeeded;
    /** Why the town is finished ({@code town_empty}, {@code town_abandoned}), or {@code null} while it is held. */
    private String townFinished;
    /** Where the company falls back to once the town is finished, and the line it falls back behind. */
    private int rallyX, rallyY;
    private float lineY;
    private int frontward;
    private boolean fallBackSet, withdrawn;
    /** What abandoning the town does to the convoys and townspeople; a seam until {@code CordonConvoys} carries it. */
    private Consumer<BattleSimulation> abandonTown = sim -> { };
    /** The Road's staging area inside the town's gate, or {@code null}. */
    private CordonStagingArea staging;
    private CordonLineCommand line;
    /** The tick the running tide ebbs on, or -1 between tides. */
    private int ebb = -1;
    private boolean landed, contact;
    /** The battle's optional objectives ({@code cordon-nouns.md}, Optional objectives), or {@code null}. */
    private CordonBeats beats;
    /** Bores the beats opened (L4, R5), numbered after the timetable's eruptions. */
    private int beatBores;
    /** The Road's line bores and final surge ({@code cordon-nouns.md}, Line bores), or {@code null}. */
    private CordonBoreEscalation escalation;
    /** What the next tide's pressure is scaled by: below one after the granary burns (beat L6). */
    private float nextTideScale = 1f;
    private Outcome outcome = Outcome.RUNNING;
    private String endBeat;

    public CordonSequence(CordonVariant variant, List<CordonRedoubt> redoubts, List<Integer> gap,
                          int[][] eruptionSites, int firstEruptedNumber, Timetable timetable, StoryBeatLog log) {
        this.variant = variant;
        this.eruptionSites = eruptionSites.clone();
        this.firstEruptedNumber = firstEruptedNumber;
        this.redoubts = List.copyOf(redoubts);
        this.gap = List.copyOf(gap);
        this.timetable = timetable;
        this.log = log;
    }

    public CordonVariant variant() { return variant; }
    public List<CordonRedoubt> redoubts() { return redoubts; }
    public List<Integer> gap() { return gap; }
    public Timetable timetable() { return timetable; }
    public int tides() { return tides; }
    /** Whether a tide is running now (heard on the radio when it comes, never foreseen). */
    public boolean inTide() { return ebb >= 0; }
    public int eruptions() { return eruptions; }
    /** The Line's refugees, or {@code null}. */
    public CordonRefugees refugees() { return refugees; }
    /** The allied columns walking in off the rear edge, or {@code null}. */
    public AlliedColumns columns() { return columns; }

    /** The allies' light armour behind the line, or {@code null}. */
    public CordonArmour armour() { return armour; }

    /** The allies' logistics base, or {@code null}. */
    public CordonLogistics logistics() { return logistics; }

    /** Gives the line its allied armour; at setup, before the first tick. */
    public void attachArmour(CordonArmour armour) {
        this.armour = armour;
    }

    /** The outer picket out in front of the line, or {@code null}. */
    public CordonOuterPicket outerPicket() { return outerPicket; }

    /** The company's raids at the bores, or {@code null} where nothing raids. */
    public CordonStrikes strikes() { return strikes; }

    /** The company's raids, whose commits and recalls this sequence tells as beats. */
    public void attachStrikes(CordonStrikes strikes) {
        this.strikes = strikes;
    }

    /** Puts the outer picket out in front of the line; at setup, before the first tick. */
    public void attachOuterPicket(CordonOuterPicket outerPicket) {
        this.outerPicket = outerPicket;
    }

    /** The allied line command's draw on its base (requests, ammunition, the radio's allied items), or {@code null}. */
    public CordonLineCommand line() { return line; }

    /** Gives the line its allied line command; at setup, after the armour and the base. */
    public void attachLine(CordonLineCommand line) {
        this.line = line;
    }

    /** The Road's relief convoys, or {@code null}. */
    public CordonConvoys convoys() { return convoys; }

    /** Gives the Road its relief convoys; at setup, before the first tick. */
    public void attachConvoys(CordonConvoys convoys) {
        this.convoys = convoys;
        population = convoys.town();
        peopleNeeded = peopleNeeded(population);
    }

    /** The Road's cut-off town, or {@code null}. */
    public CordonTown town() { return town; }

    /** The townspeople the town started with. */
    public int population() { return population; }

    /** How many townspeople must come out for the Road to be won: the briefing's number. */
    public int peopleNeeded() { return peopleNeeded; }

    /**
     * The briefing's number for a town of {@code population}: half of them, rounded up (owner's call). The town needs
     * more than three trips to empty, so half asks for two full convoys and part of a third.
     */
    static int peopleNeeded(int population) {
        return Math.max(1, (population + 1) / 2);
    }

    /** Why the town is finished ({@code town_empty}, {@code town_abandoned}), or {@code null} while it is held. */
    public String townFinished() { return townFinished; }

    /** Whether the company is falling back behind the line to end the Road: the town is finished. */
    public boolean fallingBack() { return townFinished != null; }

    /** Where the company falls back to, {x, y}; {@code null} where none was set. */
    public int[] rally() { return fallBackSet ? new int[]{rallyX, rallyY} : null; }

    /** Whether the company is behind the line after the town was finished. */
    public boolean withdrawn() { return withdrawn; }

    /**
     * Where the company falls back to once the town is finished, and the line it must be behind ({@code lineY}, the
     * front at +{@code frontward}); at setup.
     */
    public void attachFallBack(int rallyX, int rallyY, float lineY, int frontward) {
        this.rallyX = rallyX;
        this.rallyY = rallyY;
        this.lineY = lineY;
        this.frontward = frontward;
        fallBackSet = true;
    }

    /** What abandoning the town does (every townsperson still trapped dies, no more convoys set out); at setup. */
    public void onAbandonTown(Consumer<BattleSimulation> abandon) {
        this.abandonTown = abandon;
    }

    /** Gives the Road its cut-off town; at setup, before the first tick. */
    public void attachTown(CordonTown town) {
        this.town = town;
    }

    /** The Road's staging area inside the town's gate, where the trapped townspeople wait and the trucks load; or {@code null}. */
    public CordonStagingArea staging() { return staging; }

    /** Gives the Road its staging area; at setup, before the first tick. */
    public void attachStaging(CordonStagingArea staging) {
        this.staging = staging;
    }

    /** Gives the line its logistics base: columns then march to it and are carried forward from it. */
    public void attachLogistics(CordonLogistics logistics) {
        this.logistics = logistics;
    }

    /** The allies' air base behind the line, or {@code null} where the rear had no room for one. */
    public CordonAirBase airBase() { return airBase; }

    /** Gives the line its air base: the allied wing's stands and its own sorties; at setup, before the first tick. */
    public void attachAirBase(CordonAirBase airBase) {
        this.airBase = airBase;
    }

    /** Gives the line its people on the move (the Line's; {@code null} on the Road) and its columns; at setup, before the first tick. */
    public void attach(CordonRefugees refugees, AlliedColumns columns) {
        this.refugees = refugees;
        this.columns = columns;
    }
    public Outcome outcome() { return outcome; }

    /** Gives the battle its optional objectives; at setup, before the first tick. */
    public void attachBeats(CordonBeats beats) {
        this.beats = beats;
    }

    /** The battle's optional objectives, or {@code null}. */
    public CordonBeats beats() { return beats; }

    /** Gives the Road its line bores and final surge; at setup, before the first tick. */
    public void attachEscalation(CordonBoreEscalation escalation) {
        this.escalation = escalation;
    }

    /** The Road's line bores and final surge, or {@code null}. */
    public CordonBoreEscalation escalation() { return escalation; }

    /** The pressure the swarm is held at between tides: the timetable's ordinary, or the surge's floor once it comes. */
    private float ordinaryShare() {
        return Math.max(timetable.ordinaryPressure(), escalation == null ? 0f : escalation.pressureFloor());
    }

    /**
     * Opens a bore for a beat (L4, R5) or the line's escalation through the ordinary seam, numbered after every eruption on the
     * timetable so the two never share a number. Returns the number; the bore may open on a later tick
     * when its ground is not clear yet.
     */
    public int openBeatBore(BattleSimulation sim, int x, int y) {
        int number = firstEruptedNumber + timetable.eruptionSeconds().size() + beatBores++;
        sim.swarmBores().open(sim, number, false, x, y);
        return number;
    }

    /** The next tide comes at {@code scale} of the timetable's tide pressure (beat L6), never below the ordinary. */
    public void weakenNextTide(float scale) {
        nextTideScale = Math.min(nextTideScale, scale);
    }

    /**
     * The allied command gives a redoubt up (beat L3): its garrison is ordered out and it no longer
     * calls, falls or counts against the line. A fallen redoubt cannot be given up.
     */
    public boolean giveUp(CordonRedoubt redoubt) {
        if (!redoubt.manned()) return false;
        redoubt.setState(CordonRedoubt.State.GIVEN_UP);
        return true;
    }

    /** Redoubts fallen so far. */
    public int fallen() {
        int n = 0;
        for (CordonRedoubt r : redoubts) if (r.state() == CordonRedoubt.State.FALLEN) n++;
        return n;
    }

    /** One tick of the story; the battle calls it after its systems have moved. */
    public void advance(BattleSimulation sim) {
        if (outcome != Outcome.RUNNING || sim.isComplete()) return;
        int tick = sim.getSimTickIndex();
        int[] swarmInside = new int[redoubts.size()];
        boolean[] relief = new boolean[redoubts.size()];
        int townInside = 0, stagingInside = 0, companyAhead = 0, companyBehind = 0;
        boolean townHeld = false;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(unit) || sim.isRiding(unit)) continue;
            Faction faction = sim.identity().faction(unit);
            UnitType type = sim.identity().type(unit);
            int x = sim.world().cellX(unit), y = sim.world().cellY(unit);
            boolean swarm = faction == Faction.DEFENDER && type.isSwarm() && !type.isSwarmWorker();
            boolean company = faction == Faction.MARINE && isCompany(type);
            if (company && !landed) {
                landed = true;
                emit(tick, "landfall");
            }
            if (!swarm && !company) continue;
            if (company && fallBackSet) {
                if (behindLine(y, lineY, frontward)) companyBehind++;
                else companyAhead++;
            }
            if (town != null) {
                if (swarm && town.contains(x, y, 0)) townInside++;
                if (swarm && town.staging().contains(x, y)) stagingInside++;
                if (swarm && !townContact && town.contains(x, y, CONTACT_RANGE)) {
                    townContact = true;
                    emit(tick, "town_contact");
                }
                if (company && town.holdsFrom(x, y)) townHeld = true;
            }
            for (int r = 0; r < redoubts.size(); r++) {
                CordonRedoubt redoubt = redoubts.get(r);
                if (swarm) {
                    if (redoubt.contains(x, y, 0)) swarmInside[r]++;
                    if (!contact && redoubt.contains(x, y, CONTACT_RANGE)) {
                        contact = true;
                        emit(tick, "line_contact");
                    }
                } else if (redoubt.contains(x, y, RELIEF_REACH)) {
                    relief[r] = true;
                }
            }
        }
        for (int r = 0; r < redoubts.size(); r++) step(sim, tick, redoubts.get(r), swarmInside[r], relief[r]);
        if (town != null) stepTown(sim, tick, townInside, stagingInside, townHeld);
        if (variant == CordonVariant.ROAD && convoys != null && townFinished == null) {
            String finished = townFinished(population, convoys.town(), town != null && !town.holds());
            if (finished != null) {
                townFinished = finished;
                if (finished.equals("town_abandoned")) abandonTown.accept(sim);
                emit(tick, finished);
                emit(tick, "fall_back");
            }
        }
        if (townFinished != null && !withdrawn && companyBehind > 0 && companyAhead == 0) {
            withdrawn = true;
            emit(tick, "company_withdrawn");
        }
        if (variant == CordonVariant.ROAD && gapClosed()) emit(tick, "gap_closed");
        while (tides < timetable.tideSeconds().size()
                && tick >= ticks(timetable.tideSeconds().get(tides))) {
            tides++;
            sim.swarmBores().tide(tick);
            sim.setSwarmPressureShare(Math.max(ordinaryShare(), timetable.tidePressure() * nextTideScale));
            nextTideScale = 1f;
            ebb = tick + ticks(timetable.tideLengthSeconds());
            emit(tick, "tide_" + tides);
        }
        while (eruptions < timetable.eruptionSeconds().size()
                && tick >= ticks(timetable.eruptionSeconds().get(eruptions))) {
            int[] site = eruptions < eruptionSites.length ? eruptionSites[eruptions] : null;
            eruptions++;
            // A bore through the ordinary seam: it opens when its ground is clear and grows and
            // surges as any bore does, so the director's target rises with it.
            if (site != null) {
                sim.swarmBores().open(sim, firstEruptedNumber + eruptions - 1, false, site[0], site[1]);
                emit(tick, "eruption_" + eruptions);
            }
        }
        if (refugees != null) refugees.advance(sim, tick, beat -> emit(tick, beat));
        if (armour != null) armour.advance(sim, redoubts);
        if (outerPicket != null) outerPicket.advance(sim, tick, beat -> emit(tick, beat));
        if (strikes != null) for (String beat : strikes.drainBeats()) emit(tick, beat);
        if (logistics != null) logistics.advance(sim, redoubts, beat -> emit(tick, beat));
        if (line != null) line.advance(sim);
        if (airBase != null) airBase.advance(sim, redoubts, beat ->
                log.emitOnce(tick, "cordon." + beat, StoryBeatLog.Audience.OBSERVER_ONLY));
        if (convoys != null) convoys.advance(sim, tick, beat -> emit(tick, beat));
        // Columns muster at the base where carriers lift them up; in a city they walk the whole way.
        AlliedColumns.Orders orders = logistics != null && logistics.musters() ? logistics.columnOrders() : new ColumnOrders();
        if (columns != null) columns.advance(sim, tick, orders, (beat, observer) ->
                log.emitOnce(tick, "cordon." + beat, observer ? StoryBeatLog.Audience.OBSERVER_ONLY
                        : StoryBeatLog.Audience.PLAYER));
        if (beats != null) beats.advance(sim, tick);
        if (escalation != null) escalation.advance(sim, tick, this, beat -> emit(tick, beat));
        if (ebb >= 0 && tick >= ebb) {
            ebb = -1;
            sim.setSwarmPressureShare(ordinaryShare());
        }
    }

    /**
     * The end rules. The objective list ticks after {@link #advance}, beside the elimination check
     * that ends the battle when the company is gone, and reads the same company: so a wiped company
     * breaks the Cordon on the tick the battle ends, rather than leaving it running under the
     * elimination's verdict.
     */
    @Override
    public void tick(BattleView view) {
        if (outcome != Outcome.RUNNING) return;
        int tick = view.getSimTickIndex();
        boolean company = ObjectiveFactionPresence.anyInPlay(view, Faction.MARINE, unit -> true);
        Ending end = variant == CordonVariant.ROAD
                ? roadEnding(company, townFinished != null, withdrawn, convoysMoving(),
                        convoys != null ? convoys.peopleOut() : 0, peopleNeeded, tick >= ticks(ROAD_CAP_SECONDS))
                : ending(company, fallen(), timetable.allowedFallen(), tick >= ticks(timetable.windowSeconds()));
        if (end == null) return;
        outcome = end.outcome();
        endBeat = end.beat();
        if (outcome == Outcome.HELD && refugees != null) emit(tick, "evacuation_complete");
        emit(tick, end.beat());
    }

    /** How the Cordon ended: the outcome and the beat that says why. */
    public record Ending(Outcome outcome, String beat) { }

    /**
     * The Line's end rules ({@code cordon-nouns.md}, Flow, the end), first match wins, or {@code null} while it runs.
     * A wiped company loses before anything else is read: nobody is left to hold the line the window would otherwise
     * call held. A given-up redoubt (beat L3) is neither fallen nor manned, so it counts against no rule.
     */
    static Ending ending(boolean companyInPlay, int fallen, int allowedFallen, boolean windowOver) {
        if (!companyInPlay) return new Ending(Outcome.BROKEN, "company_lost");
        if (fallen > allowedFallen) return new Ending(Outcome.BROKEN, "line_broken");
        if (windowOver) return new Ending(Outcome.HELD, "cordon_held");
        return null;
    }

    /** The Road's hard cap: no window ends it, but a battle cannot run forever. Generous and untuned. */
    public static final int ROAD_CAP_SECONDS = 1800;
    /** Rows behind the line's mean row a Marine must stand to be behind the line. */
    static final int BEHIND_LINE = 4;

    /**
     * Whether the town is finished, and why ({@code cordon-nouns.md}, Cut-off town): {@code town_empty} once nobody
     * is left trapped in a town that had people, {@code town_abandoned} once its staging area is overrun (the company
     * has pulled out of it, or never held it, and its defenders are gone from round it); {@code null} while it is held.
     */
    static String townFinished(int population, int trapped, boolean stagingOverrun) {
        if (population > 0 && trapped <= 0) return "town_empty";
        if (stagingOverrun) return "town_abandoned";
        return null;
    }

    /** Whether a cell's row is behind the line: {@link #BEHIND_LINE} rows rearward of its mean row. */
    static boolean behindLine(int y, float lineY, int frontward) {
        return (y - lineY) * frontward <= -BEHIND_LINE;
    }

    /**
     * The Road's end rules ({@code cordon-nouns.md}, Flow, the end), first match wins, or {@code null} while it runs.
     * The win is people: a wiped company loses first; otherwise the Road ends once the town is finished, the company
     * is back behind the line and no convoy is still on the road with people aboard (or at the hard cap), and it is
     * won when at least {@code needed} townspeople came out ({@code cordon_held}), else lost ({@code people_short}).
     * The line, the gap and lost convoys decide nothing by themselves.
     */
    static Ending roadEnding(boolean companyInPlay, boolean townFinished, boolean withdrawn, boolean convoysMoving,
                             int peopleOut, int needed, boolean capReached) {
        if (!companyInPlay) return new Ending(Outcome.BROKEN, "company_lost");
        if (townFinished && withdrawn && !convoysMoving || capReached) {
            return peopleOut >= needed ? new Ending(Outcome.HELD, "cordon_held") : new Ending(Outcome.BROKEN, "people_short");
        }
        return null;
    }

    /** Whether a convoy is on its way to the town, loading there or driving out. */
    private boolean convoysMoving() {
        if (convoys == null) return false;
        for (int c = 1; c <= convoys.convoys(); c++) {
            String phase = convoys.phase(c);
            if ("inbound".equals(phase) || "loading".equals(phase) || "outbound".equals(phase)) return true;
        }
        return false;
    }

    /** The beat the Cordon ended on ({@code company_lost}, {@code line_broken}, ...), or {@code null} while it runs. */
    public String endBeat() { return endBeat; }

    /** One redoubt's state for this tick ({@code cordon-nouns.md}, Redoubt states). */
    private void step(BattleSimulation sim, int tick, CordonRedoubt redoubt, int swarmInside, boolean relief) {
        redoubt.observe(swarmInside, tick);
        if (!redoubt.manned()) return;
        int standing = standing(sim, redoubt);
        if (standing == 0 && swarmInside > 0) {
            redoubt.setState(CordonRedoubt.State.FALLEN);
            emit(tick, "redoubt_fell_" + redoubt.number());
            return;
        }
        switch (redoubt.state()) {
            case HOLDING -> {
                if (swarmInside > 0 || standing * 2 <= redoubt.garrison()) {
                    redoubt.setState(CordonRedoubt.State.CALLING);
                    emit(tick, "redoubt_calling_" + redoubt.number());
                }
            }
            case CALLING -> {
                if (relief) {
                    redoubt.setState(CordonRedoubt.State.RELIEVED);
                    emit(tick, "redoubt_relieved_" + redoubt.number());
                }
            }
            // A relieved redoubt calls again only when the swarm is inside its wall once more.
            case RELIEVED -> {
                if (swarmInside > 0 && !relief) redoubt.setState(CordonRedoubt.State.CALLING);
            }
            default -> { }
        }
    }

    /**
     * A column goes to the redoubt that needs it most, a calling one first and then the one with the least
     * garrison standing, and joins it as a wall team at its wall ({@code cordon-nouns.md}, Allied column).
     */
    private final class ColumnOrders implements AlliedColumns.Orders {
        @Override public int[] destination(BattleSimulation sim) {
            CordonRedoubt best = null;
            int bestStanding = Integer.MAX_VALUE;
            for (CordonRedoubt r : redoubts) {
                if (!r.manned()) continue;
                int standing = standing(sim, r) - (r.state() == CordonRedoubt.State.CALLING ? 1000 : 0);
                if (standing < bestStanding) {
                    bestStanding = standing;
                    best = r;
                }
            }
            return best == null ? null : new int[]{best.centreX(), best.centreY()};
        }

        @Override public boolean stillNeeded(BattleSimulation sim, int x, int y) {
            CordonRedoubt r = at(x, y);
            return r != null && r.manned();
        }

        @Override public boolean arrived(int x, int y, int destX, int destY) {
            CordonRedoubt r = at(destX, destY);
            return r != null && r.contains(x, y, RELIEF_REACH);
        }

        @Override public void joined(BattleSimulation sim, int number, List<Integer> squads, int destX, int destY) {
            CordonRedoubt r = at(destX, destY);
            if (r != null) join(sim, r, squads);
        }

        private CordonRedoubt at(int x, int y) {
            for (CordonRedoubt r : redoubts) if (r.contains(x, y, 0)) return r;
            return null;
        }
    }

    /** Squads that have come up to a redoubt join its garrison as wall teams. */
    static void join(BattleSimulation sim, CordonRedoubt r, List<Integer> squads) {
        int radius = Math.max(r.right() - r.left(), r.bottom() - r.top()) / 2 + 2;
        for (int squad : squads) {
            r.reinforce(squad, sim.squadMemberCount(squad));
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, r.centreX(), r.centreY(), radius),
                    CommandAuthority.GARRISON, CordonGarrisons.ISSUER, "hold redoubt " + r.number());
        }
    }

    static int standing(BattleSimulation sim, CordonRedoubt redoubt) {
        return standing(sim, redoubt.teams());
    }

    static int standing(BattleSimulation sim, List<Integer> teams) {
        int standing = 0;
        for (int squad : teams) standing += sim.squadMemberCount(squad);
        return standing;
    }

    /**
     * The town for this tick ({@code cordon-nouns.md}, Cut-off town): its staging area held by a Marine round it
     * ({@code marines}) or a defender standing round it, told as beats when it is first pressed or overrun, and the
     * town's call heard once the swarm is inside its wall or half its defenders are down.
     */
    private void stepTown(BattleSimulation sim, int tick, int inside, int inStaging, boolean marines) {
        boolean held = marines;
        int standing = 0;
        for (int squad : town.teams()) {
            for (int i = 0, n = sim.squadMemberCount(squad); i < n; i++) {
                long m = sim.squadMemberAt(squad, i);
                if (m == 0L || !sim.world().isAlive(m) || !sim.world().hasPosition(m)) continue;
                standing++;
                if (!held && town.holdsFrom(sim.world().cellX(m), sim.world().cellY(m))) held = true;
            }
        }
        if (!townCalled && (inside > 0 || town.garrison() > 0 && standing * 2 <= town.garrison())) {
            townCalled = true;
            emit(tick, "town_calling");
        }
        CordonTown.State before = town.observe(inside, inStaging, held, tick);
        if (town.state() == before) return;
        if (town.state() == CordonTown.State.PRESSED) emit(tick, "staging_pressed");
        if (town.state() == CordonTown.State.OVERRUN) emit(tick, "staging_overrun");
    }

    private boolean gapClosed() {
        if (gap.isEmpty()) return false;
        for (int index : gap) if (redoubts.get(index).state() != CordonRedoubt.State.FALLEN) return false;
        return true;
    }

    static boolean isCompany(UnitType type) {
        return type == UnitType.MARINE || type == UnitType.MARINE_BLUE || type == UnitType.MARINE_RED;
    }

    static int ticks(int seconds) {
        return Math.round(seconds / BattleSimulation.TICK_DT);
    }

    private void emit(int tick, String beat) {
        log.emitOnce(tick, "cordon." + beat, StoryBeatLog.Audience.PLAYER);
    }

    @Override public Faction owningFaction() { return Faction.MARINE; }
    @Override public boolean isComplete() { return outcome == Outcome.HELD; }
    @Override public boolean isFailed() { return outcome == Outcome.BROKEN; }
    @Override public String displayName() {
        return switch (outcome) {
            case RUNNING -> Strings.get(variant == CordonVariant.ROAD ? "cordonHoldTown" : "cordonHoldLine");
            case HELD -> Strings.get("cordonHeld");
            case BROKEN -> Strings.get("cordonBroken");
        };
    }
}
