package com.dillon.starsectormarines.battle.shield;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.WarshipSoundCue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Data owner for planetary shield domes: one per live shield generator, each a
 * flux pool over a circle of ground.
 *
 * <p><b>What a dome stops.</b> Ship-grade ordnance of a hostile side landing
 * inside it: a warship's round, warhead or lance beam, and an orbital barrage
 * ({@link PendingDetonation#isShipGrade}). The detonation is taken as flux and
 * never lands. It stops <em>incoming fire only</em>: the holder's own side fires
 * out through it freely, and people, small arms, mech weapons, rockets,
 * aircraft and their ordnance pass, as vanilla describes the planetary shield
 * as disrupting long-range bombardment rather than sealing the ground.
 *
 * <p><b>Pool and overload.</b> Each absorbed detonation adds its strength (the
 * larger of its damage and its wall damage) to the pool, which drains at the
 * dome's dissipation while it is up. A dome that fills drops for its overload
 * time, during which ordnance lands as if it were not there, and recovers empty.
 * The detonation that fills the pool is itself absorbed; the gap begins after it.
 * Concentrated fire overloads a dome and spaced fire never does, which is the
 * whole point: a ship can open the sky by mass, not by patience.
 *
 * <p><b>Who holds it.</b> A generator belongs to one side and its dome stops that
 * side's enemies. Either side may hold one: nothing is special about attacker or
 * defender. A side with combatants standing within the capture radius and none
 * of the holder's friends there takes the generator after the capture time; the
 * holder's friends alone there wind a started capture back. A taken generator's
 * dome reboots (down for {@link #REBOOT_SECONDS}) before it protects its new
 * holder. Destroying the generator drops the dome for good.
 *
 * <p>Registered as a structure with a dome spawns ({@code BattleSimulation.spawn}),
 * ticked serially with the rest of the field systems, read by the detonation
 * pipeline and the renderer. Deterministic: no random draws.
 */
public final class ShieldDomeService {

    /** Seconds a freshly taken generator's dome stays down while it reboots under its new holder. */
    public static final float REBOOT_SECONDS = 4f;
    /** How long an absorbed round's mark lingers for the renderer. */
    public static final float HIT_MARK_SECONDS = 0.6f;
    /** Seconds a hit or drop flash takes to fade. */
    public static final float FLASH_SECONDS = 0.35f;
    /** Sim-seconds between capture evaluations. */
    private static final float CAPTURE_PERIOD = 0.5f;
    /** Least gap between absorb cues from one dome, so a barrage of rounds is a roll rather than a buzz. */
    private static final float ABSORB_CUE_GAP_SECONDS = 0.25f;
    private static final float ABSORB_CUE_VOLUME = 1f;
    private static final float STATE_CUE_VOLUME = 1.2f;
    public static final String ABSORB_SOUND_ID = "hit_shield_heavy_gun";
    public static final String DROP_SOUND_ID = "shield_burnout";
    public static final String RISE_SOUND_ID = "shield_raise";

    /** Told when a taken generator's entity should change sides; set by the simulation. */
    private BiConsumer<Long, Faction> holderListener = (id, holder) -> { };

    private final List<Dome> domes = new ArrayList<>();
    private final List<HitMark> marks = new ArrayList<>();
    private final List<WarshipSoundCue> cues = new ArrayList<>();
    private final List<WarshipSoundCue> cuesView = Collections.unmodifiableList(cues);
    private volatile List<DomeView> snapshot = List.of();
    private volatile List<HitView> markSnapshot = List.of();

    /**
     * Registers the dome of a generator that just spawned. The pool starts
     * empty and the dome up: a world's generator is running when the battle
     * opens.
     */
    public synchronized void register(long entityId, Faction holder, float x, float y, DomeSpec spec) {
        domes.add(new Dome(entityId, holder, x, y, spec));
        publish();
    }

    /** Sets who is told, with the generator's entity id and its new holder, when a capture completes. */
    public synchronized void setHolderListener(BiConsumer<Long, Faction> listener) {
        this.holderListener = listener;
    }

    /** Drops the previous frame's cues. Called at the top of every advance. */
    public synchronized void beginFrame() {
        cues.clear();
    }

    /** Sounds the domes asked for this frame, for whoever presents the battle. */
    public List<WarshipSoundCue> cuesThisFrame() {
        return cuesView;
    }

    /**
     * Whether a dome takes this detonation. A dome takes it when it is
     * ship-grade, comes from a side hostile to the holder, lands inside the
     * dome, and the dome is up. The strength becomes flux; the round that fills
     * the pool is absorbed and then the dome drops.
     */
    public synchronized boolean absorbs(PendingDetonation det) {
        if (domes.isEmpty() || !det.isShipGrade()) return false;
        for (Dome dome : domes) {
            if (dome.overloadRemaining > 0f || !dome.holder.hostileTo(det.shooterFaction)) continue;
            float dx = det.endpointX - dome.x;
            float dy = det.endpointY - dome.y;
            float radius = dome.spec.radiusCells();
            if (dx * dx + dy * dy > radius * radius) continue;
            float cost = Math.max(det.damage, (float) det.wallDamage);
            dome.pool += cost;
            dome.absorbedFlux += cost;
            dome.hits++;
            dome.hitFlash = 1f;
            marks.add(new HitMark(dome.holder, det.endpointX, det.endpointY));
            if (dome.cueGap <= 0f) {
                cues.add(new WarshipSoundCue(WarshipSoundCue.Kind.DOME_ABSORB, ABSORB_SOUND_ID,
                        det.endpointX, det.endpointY, ABSORB_CUE_VOLUME));
                dome.cueGap = ABSORB_CUE_GAP_SECONDS;
            }
            if (dome.pool >= dome.spec.fluxCapacity()) {
                overload(dome, dome.spec.overloadSeconds());
            }
            return true;
        }
        return false;
    }

    /**
     * Advances every dome: drains its pool, runs the overload clock, evaluates
     * capture, and retires the dome of a generator that is gone.
     */
    public synchronized void tick(float dt, UnitRosterService roster) {
        for (int i = domes.size() - 1; i >= 0; i--) {
            Dome dome = domes.get(i);
            if (!roster.isAliveById(dome.entityId)) {
                cues.add(new WarshipSoundCue(WarshipSoundCue.Kind.DOME_DROP, DROP_SOUND_ID,
                        dome.x, dome.y, STATE_CUE_VOLUME));
                domes.remove(i);
                continue;
            }
            advance(dome, dt, roster);
        }
        for (int i = marks.size() - 1; i >= 0; i--) {
            HitMark mark = marks.get(i);
            mark.remaining -= dt;
            if (mark.remaining <= 0f) marks.remove(i);
        }
        publish();
    }

    private void advance(Dome dome, float dt, UnitRosterService roster) {
        dome.hitFlash = Math.max(0f, dome.hitFlash - dt / FLASH_SECONDS);
        dome.dropFlash = Math.max(0f, dome.dropFlash - dt / FLASH_SECONDS);
        dome.cueGap = Math.max(0f, dome.cueGap - dt);
        if (dome.overloadRemaining > 0f) {
            dome.overloadRemaining -= dt;
            if (dome.overloadRemaining <= 0f) {
                dome.overloadRemaining = 0f;
                dome.pool = 0f;
                cues.add(new WarshipSoundCue(WarshipSoundCue.Kind.DOME_RISE, RISE_SOUND_ID,
                        dome.x, dome.y, STATE_CUE_VOLUME));
            } else {
                // The overloaded pool vents down to empty over exactly the overload time.
                dome.pool = dome.spec.fluxCapacity() * (dome.overloadRemaining / dome.overloadTotal);
            }
        } else {
            dome.pool = Math.max(0f, dome.pool - dome.spec.dissipationPerSecond() * dt);
        }
        dome.captureClock += dt;
        if (dome.captureClock >= CAPTURE_PERIOD) {
            dome.captureClock -= CAPTURE_PERIOD;
            evaluateCapture(dome, roster);
        }
    }

    /** Takes the dome down for {@code seconds}; its pool vents from full to empty across them. */
    private static void startDown(Dome dome, float seconds) {
        dome.pool = dome.spec.fluxCapacity();
        dome.overloadRemaining = seconds;
        dome.overloadTotal = seconds;
        dome.dropFlash = 1f;
    }

    private void overload(Dome dome, float seconds) {
        startDown(dome, seconds);
        dome.overloads++;
        cues.add(new WarshipSoundCue(WarshipSoundCue.Kind.DOME_DROP, DROP_SOUND_ID,
                dome.x, dome.y, STATE_CUE_VOLUME));
    }

    /**
     * One capture evaluation. Combatants on their feet within the capture radius
     * decide it: a hostile side alone takes it, the holder's friends alone wind
     * it back, a mix pauses it. Static structures, drones and the neutral are
     * not people standing in the way.
     */
    private void evaluateCapture(Dome dome, UnitRosterService roster) {
        World world = roster.world();
        float reach = dome.spec.captureRadiusCells();
        float reachSq = reach * reach;
        boolean holderPresent = false;
        Faction raider = null;
        long[] live = roster.denseArray();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = live[i];
            UnitType type = roster.identity().type(u);
            if (!type.combatant || type.isStatic() || type.isDrone()) continue;
            Faction faction = roster.identity().faction(u);
            float dx = world.x(u) - dome.x;
            float dy = world.y(u) - dome.y;
            if (dx * dx + dy * dy > reachSq) continue;
            if (dome.holder.friendlyTo(faction)) {
                holderPresent = true;
            } else if (dome.holder.hostileTo(faction)) {
                if (raider == null || faction.ordinal() < raider.ordinal()) raider = faction;
            }
        }
        float step = CAPTURE_PERIOD / dome.spec.captureSeconds();
        if (raider != null && !holderPresent) {
            if (dome.raider != raider) {
                dome.raider = raider;
                dome.captureProgress = 0f;
            }
            dome.captureProgress += step;
            if (dome.captureProgress >= 1f) {
                dome.holder = raider;
                dome.raider = null;
                dome.captureProgress = 0f;
                dome.captures++;
                startDown(dome, REBOOT_SECONDS);
                holderListener.accept(dome.entityId, dome.holder);
            }
        } else if (raider == null && holderPresent && dome.captureProgress > 0f) {
            dome.captureProgress = Math.max(0f, dome.captureProgress - step);
            if (dome.captureProgress == 0f) dome.raider = null;
        }
    }

    private void publish() {
        List<DomeView> views = new ArrayList<>(domes.size());
        for (Dome d : domes) {
            views.add(new DomeView(d.entityId, d.holder, d.x, d.y, d.spec.radiusCells(),
                    d.pool, d.spec.fluxCapacity(), d.overloadRemaining > 0f,
                    d.overloadTotal > 0f ? d.overloadRemaining / d.overloadTotal : 0f,
                    d.hitFlash, d.dropFlash, d.captureProgress, d.raider,
                    d.hits, d.overloads, d.captures, d.absorbedFlux,
                    d.originalHolder, d.spec.captureRadiusCells()));
        }
        snapshot = List.copyOf(views);
        List<HitView> hits = new ArrayList<>(marks.size());
        for (HitMark m : marks) {
            hits.add(new HitView(m.holder, m.x, m.y, Math.max(0f, m.remaining / HIT_MARK_SECONDS)));
        }
        markSnapshot = List.copyOf(hits);
    }

    /**
     * The dome that would meet ship-grade fire from {@code shooter} landing at
     * {@code (x, y)}, as of the last tick: an up dome first (it absorbs the round),
     * else a dome that covers the point but is down, else null. For targeting
     * previews; the authority on absorption remains {@link #absorbs}.
     */
    public DomeView coverageAt(float x, float y, Faction shooter) {
        DomeView down = null;
        for (DomeView d : snapshot) {
            if (!d.holder().hostileTo(shooter)) continue;
            float dx = x - d.x();
            float dy = y - d.y();
            if (dx * dx + dy * dy > d.radius() * d.radius()) continue;
            if (!d.overloaded()) return d;
            if (down == null) down = d;
        }
        return down;
    }

    /** Live domes as of the last tick, for the renderer and for evidence. */
    public List<DomeView> domes() { return snapshot; }

    /** Rounds the domes took in the last moments, for the renderer. */
    public List<HitView> recentHits() { return markSnapshot; }

    /**
     * Read-only state of one dome.
     *
     * @param level         flux in the pool over capacity, {@code [0, 1]}
     * @param overloaded    whether the dome is down (an overload, or a reboot after capture)
     * @param recovery      share of the down time still to run, {@code [0, 1]}
     * @param raider        the side currently taking the generator, or null
     * @param originalHolder the side the generator was built for
     * @param captureRadius reach of the capture check around the generator, in cells
     */
    public record DomeView(long entityId, Faction holder, float x, float y, float radius,
                           float flux, float capacity, boolean overloaded, float recovery,
                           float hitFlash, float dropFlash, float captureProgress, Faction raider,
                           int hits, int overloads, int captures, float absorbedFlux,
                           Faction originalHolder, float captureRadius) {

        public float level() {
            return capacity > 0f ? Math.min(1f, flux / capacity) : 0f;
        }
    }

    /** One round a dome took, with how fresh the mark is ({@code 1} just now, {@code 0} gone). */
    public record HitView(Faction holder, float x, float y, float freshness) {}

    private static final class HitMark {
        final Faction holder;
        final float x;
        final float y;
        float remaining = HIT_MARK_SECONDS;

        HitMark(Faction holder, float x, float y) {
            this.holder = holder;
            this.x = x;
            this.y = y;
        }
    }

    private static final class Dome {
        final long entityId;
        final float x;
        final float y;
        final DomeSpec spec;
        final Faction originalHolder;
        Faction holder;
        Faction raider;
        float pool;
        float overloadRemaining;
        float overloadTotal;
        float hitFlash;
        float dropFlash;
        float cueGap;
        float captureClock;
        float captureProgress;
        int hits;
        int overloads;
        int captures;
        float absorbedFlux;

        Dome(long entityId, Faction holder, float x, float y, DomeSpec spec) {
            this.entityId = entityId;
            this.holder = holder;
            this.originalHolder = holder;
            this.x = x;
            this.y = y;
            this.spec = spec;
        }
    }
}
