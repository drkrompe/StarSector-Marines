package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.ShipStationing;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The defending world's answer: its own ship over the battle, on the same mechanism the company
 * uses ({@link ShipOnStation#call}). The only differences are who supplies the ship and who
 * decides when to call it.
 *
 * <p><b>Who gets one.</b> Only a defended world (the target profile's defence level at or above
 * {@link #DEFENSE_LEVEL_THRESHOLD}) can call a ship at all ({@link #warrants}); which ship is the
 * launch's business, not the battle's: a campaign mission freezes the largest eligible hull of a
 * free fleet of the defending faction in the system, and any other mission declares one
 * ({@code HostileShipSelection}). Here the ship is simply a frozen {@link ShipSupportSource}. The
 * company's own stationed strength is not counted: it is the company's contribution, not the world's.
 *
 * <p><b>When.</b> The defending commander calls it once, the first time the marines threaten
 * ground it must hold (a marine within {@link #THREAT_RADIUS_CELLS} of a defender-held
 * {@code mustHold} node), or {@link #CONTACT_SECONDS} after the first marine is on the ground,
 * whichever is first. This is a battle-level observer rather than a hook in each mission's
 * command strategy: a mission strategy plans squad assignments and has no ship seam
 * ({@code warship-behaviours.md}), and the call needs only what the defender can see.
 *
 * <p><b>Warning.</b> The decision raises a story beat ({@link #BEAT_INBOUND}) and a ground marker
 * over the station, and the ship is called {@link #WARNING_SECONDS} later.
 */
public final class HostileShipSupport {

    private static final Logger LOG = Global.getLogger(HostileShipSupport.class);

    /** Target-profile defence level (about 0 to 7) at or above which a world answers with a ship. */
    public static final int DEFENSE_LEVEL_THRESHOLD = 4;
    /** A marine this close to a defender's must-hold node is a threat to it, in cells. */
    public static final float THREAT_RADIUS_CELLS = 30f;
    /** Seconds on the ground after the first marine lands before the defender calls regardless. */
    public static final float CONTACT_SECONDS = 180f;
    /** Warning ahead of the call, in seconds; the descent and glide follow. */
    public static final float WARNING_SECONDS = 6f;
    /** Radius of the ground marker, matching the ship's working area. */
    public static final float MARKER_RADIUS_CELLS = ShipOnStation.AREA_RADIUS_CELLS;
    /** How often the observer looks at the battle, in ticks. */
    static final int LOOK_INTERVAL_TICKS = 30;

    /** The story beat raised when the defender commits its ship. */
    public static final String BEAT_INBOUND = "ship_support.hostile_inbound";

    public enum Phase { WAITING, WARNED, CALLED }

    private final ShipSupportSource source;
    private final boolean atStart;
    private final Consumer<Warship> onCalled;
    private final StoryBeatLog beats;
    private Phase phase = Phase.WAITING;
    private int firstContactTick = -1;
    private int warnTick = -1;
    private int calledTick = -1;
    private float stationX;
    private float stationY;
    private Warship ship;

    /**
     * @param atStart  call at the first look rather than on the trigger (the briefing's debug pick)
     * @param onCalled applied to the fitted hull once called; may be null
     */
    public HostileShipSupport(ShipSupportSource source, boolean atStart, Consumer<Warship> onCalled,
                              StoryBeatLog beats) {
        if (source == null) throw new IllegalArgumentException("a hostile ship needs a source");
        this.source = source;
        this.atStart = atStart;
        this.onCalled = onCalled;
        this.beats = beats;
    }

    // ---- who gets one (pure) ----

    /** Whether the world is defended enough to call a ship at all; the fleet search decides which. */
    public static boolean warrants(TargetProfile profile) {
        return profile != null && profile.defenseLevel() >= DEFENSE_LEVEL_THRESHOLD;
    }

    // ---- when (pure) ----

    /**
     * Whether the defender decides to call now.
     *
     * @param marinesOnGround   a marine is alive on the map
     * @param groundTicks       ticks since the first marine was seen on the ground
     * @param nearestThreatCells distance from the nearest marine to a defender must-hold node
     */
    public static boolean shouldCall(boolean marinesOnGround, int groundTicks, float nearestThreatCells) {
        if (!marinesOnGround) return false;
        if (nearestThreatCells <= THREAT_RADIUS_CELLS) return true;
        return groundTicks >= Math.round(CONTACT_SECONDS / BattleSimulation.TICK_DT);
    }

    /** The held point nearest the marines' centre of mass, or null when there is none. */
    public static float[] nearestHeld(Iterable<float[]> held, float marineX, float marineY) {
        float[] best = null;
        float bestSq = Float.MAX_VALUE;
        for (float[] p : held) {
            float dx = p[0] - marineX;
            float dy = p[1] - marineY;
            float sq = dx * dx + dy * dy;
            if (sq < bestSq) {
                bestSq = sq;
                best = p;
            }
        }
        return best;
    }

    // ---- state ----

    public Phase phase() { return phase; }
    public ShipSupportSource source() { return source; }
    public int warnTick() { return warnTick; }
    public int calledTick() { return calledTick; }
    public Warship ship() { return ship; }
    public float stationX() { return stationX; }
    public float stationY() { return stationY; }

    /** Seconds until the ship is called, or 0 once it is. */
    public float warningRemainingSeconds(int tick) {
        if (phase != Phase.WARNED) return 0f;
        return Math.max(0f, (warnTick + warningTicks() - tick) * BattleSimulation.TICK_DT);
    }

    /** Whether the ground marker is up: from the warning until the ship has settled on station. */
    public boolean markerActive() {
        if (phase == Phase.WARNED) return true;
        return phase == Phase.CALLED && ship != null && !ship.destroyed() && !ship.departing()
                && !ship.departed() && ship.isMoving();
    }

    private static int warningTicks() { return Math.round(WARNING_SECONDS / BattleSimulation.TICK_DT); }

    /** Advances once per tick, before the warships update. */
    public void advance(BattleSimulation sim) {
        if (phase == Phase.CALLED) return;
        int tick = sim.getSimTickIndex();
        if (phase == Phase.WAITING) {
            if (tick % LOOK_INTERVAL_TICKS != 0) return;
            look(sim, tick);
            return;
        }
        if (tick - warnTick >= warningTicks()) call(sim, tick);
    }

    private void look(BattleSimulation sim, int tick) {
        float sumX = 0f;
        float sumY = 0f;
        int marines = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || sim.identity().type(unit) != UnitType.MARINE) continue;
            sumX += sim.world().x(unit);
            sumY += sim.world().y(unit);
            marines++;
        }
        if (marines > 0 && firstContactTick < 0) firstContactTick = tick;
        float cx = marines > 0 ? sumX / marines : 0f;
        float cy = marines > 0 ? sumY / marines : 0f;
        List<float[]> held = heldNodes(sim);
        float nearest = Float.MAX_VALUE;
        for (int i = 0; i < sim.liveUnitCount() && marines > 0; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || sim.identity().type(unit) != UnitType.MARINE) continue;
            for (float[] p : held) {
                nearest = Math.min(nearest, (float) Math.hypot(p[0] - sim.world().x(unit), p[1] - sim.world().y(unit)));
            }
        }
        int groundTicks = firstContactTick < 0 ? 0 : tick - firstContactTick;
        if (!atStart && !shouldCall(marines > 0, groundTicks, nearest)) return;
        float[] at = marines > 0 ? nearestHeld(held, cx, cy) : null;
        if (at == null && marines > 0) at = new float[]{cx, cy};
        if (at == null) {
            ShipStationing.Placement p = ShipStationing.place(Faction.DEFENDER, sim.shipAxis(),
                    sim.getGrid().getWidth(), sim.getGrid().getHeight(), 0f);
            at = new float[]{p.stationX(), p.stationY()};
        }
        stationX = at[0];
        stationY = at[1];
        warnTick = tick;
        phase = Phase.WARNED;
        if (beats != null) beats.emitOnce(tick, BEAT_INBOUND, StoryBeatLog.Audience.PLAYER);
    }

    private static List<float[]> heldNodes(BattleSimulation sim) {
        List<float[]> held = new ArrayList<>();
        TacticalMap map = sim.getTacticalMap();
        if (map == null) return held;
        for (TacticalNode node : map.all()) {
            if (node.mustHold && node.defaultGuard != null && Faction.DEFENDER.friendlyTo(node.defaultGuard)) {
                held.add(new float[]{node.centerX() + 0.5f, node.centerY() + 0.5f});
            }
        }
        return held;
    }

    private void call(BattleSimulation sim, int tick) {
        phase = Phase.CALLED;
        calledTick = tick;
        try {
            ship = ShipOnStation.call(source, Faction.DEFENDER, stationX, stationY, sim);
            if (onCalled != null && !source.tender()) onCalled.accept(ship);
        } catch (Exception e) {
            LOG.warn("HostileShipSupport: " + source.shipName() + " (" + source.hullId() + ") could not be fitted", e);
        }
    }

    @Override public String toString() {
        return String.format(Locale.ROOT, "HostileShipSupport[%s %s at %.0f,%.0f]", source.hullId(), phase, stationX, stationY);
    }
}
