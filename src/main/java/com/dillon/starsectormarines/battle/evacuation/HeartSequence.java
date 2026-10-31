package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveBoard;
import com.dillon.starsectormarines.battle.power.WalkingBarrage;
import com.dillon.starsectormarines.battle.airsupport.FieldAirBase;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.story.StoryBeatLog.Audience;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The heart's battle (`heart-nouns.md`, Flow): how the heart answers the
 * company, and its beats.
 *
 * <p>The mothers sleep until something wakes them: they grow, but send no
 * surge. On <b>Broad Front</b> the first satellite to fall wakes the heart and
 * every living mother surges at once. On <b>The Burn</b> a bore the fire, the
 * guns or the company hurts surges at once and the hurt mother wakes, so the
 * breakouts come where the burn line has just been. On both, a mother that falls
 * makes every surviving mother surge at once: the heart fights harder as it
 * dies. Nothing here collapses a bore or spawns a body; a surge is only brought
 * forward, and the director still releases it under the bores' capacity.
 *
 * <p>{@link #tick} is the simulation half, run after the bores and before the
 * director so a surge woken this tick can leave this tick; {@link #observe} is
 * the presentation half and the only writer of beats.
 */
public final class HeartSequence {

    public static final String BEAT_PREFIX = "heart.";
    /** A hurt bore surges at most this often, however long it is under fire. */
    static final int HURT_SURGE_COOLDOWN_TICKS = Math.round(12f / BattleSimulation.TICK_DT);

    private final Form form;
    private final StoryBeatLog log;
    private int tickCursor;
    private int observeCursor;
    private boolean woken;
    private boolean started;
    private boolean landed;
    private boolean ended;
    private final Map<SwarmBore, Float> bodies = new HashMap<>();
    private final Map<SwarmBore, Integer> lastHurtSurge = new HashMap<>();
    /** Beats the simulation half decided this tick, for the presentation half to write. */
    private final List<String> pending = new ArrayList<>();

    private final WalkingBarrage barrage;
    private int barrageCursor;
    private boolean lineLit;

    private final HeartLanes lanes;
    /** The guns and their trucks, or {@code null} when the heart has none stocked. */
    private HeartGuns guns;
    /** The Burn's bore growth, or {@code null} when no bores grow in the battle. */
    private HeartBoreGrowth growth;
    private final LaidLayout places;

    /**
     * @param barrage the guns' timetable (The Burn), or {@code null}
     * @param lanes   the allied lanes (Broad Front), or {@code null}
     * @param places  what the map's mission layout laid: the cordon's works and the beats' set pieces
     */
    public HeartSequence(Form form, StoryBeatLog log, WalkingBarrage barrage, HeartLanes lanes, LaidLayout places) {
        this.form = form;
        this.log = log;
        this.barrage = barrage;
        this.lanes = lanes;
        this.places = places == null ? LaidLayout.EMPTY : places;
    }

    /** The map's laid places by name ({@code HeartLayout}): what a beat asks "is this here" of. */
    public LaidLayout places() { return places; }

    /** The allied lanes, or {@code null} when the heart has none. */
    public HeartLanes lanes() { return lanes; }

    /** Stocks the heart's guns: they spend rounds and call trucks from now on. */
    public void guns(HeartGuns guns) { this.guns = guns; }

    /** The guns and their trucks, or {@code null}. */
    public HeartGuns guns() { return guns; }

    private FieldAirBase airBase;

    /** The allied wing's airbase (The Burn): its ground crew and replacements are worked off every tick from now on. */
    public void airBase(FieldAirBase airBase) { this.airBase = airBase; }

    /** Grows new bores in the battle from now on (The Burn, `heart-nouns.md`, Bore growth). */
    public void growBores(HeartBoreGrowth growth) { this.growth = growth; }

    /** The battle's bore growth, or {@code null}. */
    public HeartBoreGrowth boreGrowth() { return growth; }

    /** The walking barrage, or {@code null} when the heart has none. */
    public WalkingBarrage barrage() { return barrage; }

    public Form form() { return form; }

    /** Whether the mothers are awake. */
    public boolean woken() { return woken; }

    /** Puts every mother to sleep. Call once at setup, after the bores have opened. */
    public void install(BattleSimulation sim) {
        for (SwarmBore bore : sim.swarmBores().bores()) if (bore.mother()) sim.swarmBores().hold(bore);
    }

    public void tick(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        SwarmBores bores = sim.swarmBores();
        List<SwarmBores.Event> events = bores.events();
        for (; tickCursor < events.size(); tickCursor++) {
            SwarmBores.Event event = events.get(tickCursor);
            if (event.kind() != SwarmBores.EventKind.COLLAPSED) continue;
            if (event.bore().mother()) {
                surgeMothers(bores, tick);
            } else if (form == Form.BROAD_FRONT && !woken) {
                woken = true;
                surgeMothers(bores, tick);
                pending.add("heart_surge");
            }
        }
        if (form == Form.THE_BURN) hurtSurges(sim, bores, tick);
        if (growth != null) {
            int grown = growth.tick(sim, barrage);
            if (grown > 0) pending.add("bore_grown_" + grown);
        }
        if (barrage != null) barrage.tick(sim);
        if (lanes != null) lanes.tick(sim, pending);
        if (guns != null) guns.tick(sim, pending);
        if (airBase != null) airBase.advance(sim, BattleSimulation.TICK_DT);
        ObjectiveBoard objectives = sim.getOptionalObjectives();
        if (objectives != null) objectives.advance(tick);
    }

    /** Every living mother wakes and surges now. */
    private void surgeMothers(SwarmBores bores, int tick) {
        for (SwarmBore bore : bores.bores()) if (bore.mother()) bores.surgeNow(bore, tick);
    }

    /** The Burn: a bore that lost structure since last tick surges, once a cooldown. */
    private void hurtSurges(BattleSimulation sim, SwarmBores bores, int tick) {
        for (SwarmBore bore : bores.bores()) {
            if (bore.collapsed()) { bodies.remove(bore); continue; }
            long id = bore.entityId();
            if (sim.resolveUnit(id) == 0L) continue;
            float body = sim.world().hp(id) + sim.world().armor(id);
            Float before = bodies.put(bore, body);
            if (before == null || body >= before) continue;
            Integer last = lastHurtSurge.get(bore);
            if (!hurtSurgeDue(last, tick)) continue;
            lastHurtSurge.put(bore, tick);
            bores.surgeNow(bore, tick);
            if (bore.mother()) woken = true;
            pending.add("breakout_" + bore.beatKey());
        }
    }

    /** Whether a bore last hurt into a surge on {@code last} (or never) may surge again on {@code tick}. */
    static boolean hurtSurgeDue(Integer last, int tick) {
        return last == null || tick - last >= HURT_SURGE_COOLDOWN_TICKS;
    }

    public void observe(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        if (!started) { started = true; emit(tick, "start"); }
        if (!landed && marinesAshore(sim)) { landed = true; emit(tick, "landfall"); }
        for (String key : pending) {
            if (key.startsWith("breakout_") || key.startsWith("bore_grown_")) log.emit(tick, BEAT_PREFIX + key, Audience.OBSERVER_ONLY);
            else emit(tick, key);
        }
        pending.clear();
        List<SwarmBores.Event> events = sim.swarmBores().events();
        for (; observeCursor < events.size(); observeCursor++) {
            SwarmBores.Event event = events.get(observeCursor);
            SwarmBore bore = event.bore();
            switch (event.kind()) {
                case COLLAPSED -> emit(tick, (bore.mother() ? "mother_collapsed_" : "bore_collapsed_") + bore.number());
                case MATRIARCH -> emit(tick, "matriarch_emerged_" + bore.beatKey());
                default -> { }
            }
        }
        if (barrage != null) {
            List<WalkingBarrage.Event> shoot = barrage.events();
            for (; barrageCursor < shoot.size(); barrageCursor++) {
                WalkingBarrage.Event event = shoot.get(barrageCursor);
                switch (event.kind()) {
                    case OPENED -> emit(tick, "barrage_line_" + event.line());
                    case LIFTED -> emit(tick, "barrage_lifted_" + event.line());
                    case ENDED -> emit(tick, "barrage_ended");
                }
            }
            FireField fire = sim.fireFieldIfPresent();
            if (!lineLit && fire != null && fire.burningCount() > 0) { lineLit = true; emit(tick, "line_lit"); }
        }
        if (!ended && sim.isComplete()) {
            ended = true;
            if (sim.getWinner() == Faction.MARINE) emit(tick, "heart_down");
            else if (barrage != null && barrage.overrun(sim)) emit(tick, "battery_overrun");
            else emit(tick, "company_broken");
        }
    }

    private static boolean marinesAshore(BattleSimulation sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.MARINE && !sim.identity().type(unit).isTurret()) return true;
        }
        return false;
    }

    private void emit(int tick, String key) {
        log.emitOnce(tick, BEAT_PREFIX + key, Audience.PLAYER);
    }
}
