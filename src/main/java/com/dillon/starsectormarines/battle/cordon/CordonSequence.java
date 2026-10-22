package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.List;

/**
 * The Cordon's story ({@code cordon-nouns.md}, Flow and Beats): the line's
 * redoubts moving through their states, the tides on the timetable, the beats
 * the battle is told in, and the end the company is held to.
 *
 * <p>The company's objective. It is won when the window closes with no more
 * redoubts fallen than the briefing allows, and lost the moment the line
 * breaks, or, on the Road, the gap closes. Read-only against the battle except
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
     * and during one, when new bores erupt in front of the line, how long the
     * window, and how many redoubts may fall.
     */
    public record Timetable(List<Integer> tideSeconds, int tideLengthSeconds, float ordinaryPressure,
                            float tidePressure, List<Integer> eruptionSeconds, int windowSeconds,
                            int allowedFallen) {
        /**
         * The briefing's ordinary timetable: three tides of a minute in a
         * fifteen-minute window, the swarm at a third of its strength between
         * them and two thirds in one, a new bore erupting between each pair of
         * tides, one redoubt may fall.
         */
        public static final Timetable STANDARD = new Timetable(List.of(240, 480, 720), 60, 0.33f, 0.66f,
                List.of(360, 600), 900, 1);

        public Timetable {
            tideSeconds = List.copyOf(tideSeconds);
            eruptionSeconds = List.copyOf(eruptionSeconds);
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
    /** The tick the running tide ebbs on, or -1 between tides. */
    private int ebb = -1;
    private boolean landed, contact;
    private Outcome outcome = Outcome.RUNNING;

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
    public int eruptions() { return eruptions; }
    public Outcome outcome() { return outcome; }

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
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(unit) || sim.isRiding(unit)) continue;
            Faction faction = sim.identity().faction(unit);
            UnitType type = sim.identity().type(unit);
            int x = sim.world().cellX(unit), y = sim.world().cellY(unit);
            boolean swarm = faction == Faction.DEFENDER && type.isSwarm();
            boolean company = faction == Faction.MARINE && isCompany(type);
            if (company && !landed) {
                landed = true;
                emit(tick, "landfall");
            }
            if (!swarm && !company) continue;
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
        while (tides < timetable.tideSeconds().size()
                && tick >= ticks(timetable.tideSeconds().get(tides))) {
            tides++;
            sim.swarmBores().tide(tick);
            sim.setSwarmPressureShare(timetable.tidePressure());
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
        if (ebb >= 0 && tick >= ebb) {
            ebb = -1;
            sim.setSwarmPressureShare(timetable.ordinaryPressure());
        }
        if (fallen() > timetable.allowedFallen()) {
            outcome = Outcome.BROKEN;
            emit(tick, "line_broken");
        } else if (variant == CordonVariant.ROAD && gapClosed()) {
            outcome = Outcome.BROKEN;
            emit(tick, "gap_closed");
        } else if (tick >= ticks(timetable.windowSeconds())) {
            outcome = Outcome.HELD;
            emit(tick, "cordon_held");
        }
    }

    /** One redoubt's state for this tick ({@code cordon-nouns.md}, Redoubt states). */
    private void step(BattleSimulation sim, int tick, CordonRedoubt redoubt, int swarmInside, boolean relief) {
        if (redoubt.state() == CordonRedoubt.State.FALLEN) return;
        int standing = 0;
        for (int squad : redoubt.teams()) standing += sim.squadMemberCount(squad);
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

    private boolean gapClosed() {
        if (gap.isEmpty()) return false;
        for (int index : gap) if (redoubts.get(index).state() != CordonRedoubt.State.FALLEN) return false;
        return true;
    }

    private static boolean isCompany(UnitType type) {
        return type == UnitType.MARINE || type == UnitType.MARINE_BLUE || type == UnitType.MARINE_RED;
    }

    static int ticks(int seconds) {
        return Math.round(seconds / BattleSimulation.TICK_DT);
    }

    private void emit(int tick, String beat) {
        log.emitOnce(tick, "cordon." + beat, StoryBeatLog.Audience.PLAYER);
    }

    @Override public Faction owningFaction() { return Faction.MARINE; }
    @Override public void tick(BattleView ignored) { }
    @Override public boolean isComplete() { return outcome == Outcome.HELD; }
    @Override public boolean isFailed() { return outcome == Outcome.BROKEN; }
    @Override public String displayName() {
        return switch (outcome) {
            case RUNNING -> Strings.get(variant == CordonVariant.ROAD ? "cordonHoldGap" : "cordonHoldLine");
            case HELD -> Strings.get("cordonHeld");
            case BROKEN -> Strings.get("cordonBroken");
        };
    }
}
