package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.marine.DeployableEmplacementSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data owner and serial lifecycle for crew-served tubes: the field mortar a
 * marine carries, sets down, serves and lifts again.
 *
 * <p>A <b>tube</b> is the pairing of one carrier with one emplacement. The
 * carrier is the gunner: the tube is only ever as alive as he is. The emplacement
 * is an ordinary {@link MapTurret} minted at the gunner's cell, so enemies can
 * see it, target it and kill it exactly as they would a nest, and the gun it
 * fires is the mount's own weapon through the ordinary turret pipeline. What
 * this service adds is everything a bolted-down turret never needed:
 * <ul>
 *   <li><b>A life cycle.</b> {@link Phase#SETTING_UP} for the authored deploy
 *       time (the tube does not exist yet, so nothing can fire or be shot);
 *       {@link Phase#SERVING}; {@link Phase#PACKING} for the authored pack time,
 *       during which the tube is still there to be shot and does not fire.</li>
 *   <li><b>A fire-control squad.</b> The turret acquires targets for the
 *       <em>gunner's squad</em>, so what a tube can engage is what that squad
 *       believes — its spotter's sightings included — and never the omniscient
 *       roster. A tube with nobody who has seen the target cannot fire at it.</li>
 *   <li><b>A magazine that survives packing.</b> Rounds fired are spent from the
 *       gunner's stash and a lifted tube is lifted with what is left.</li>
 *   <li><b>A crew.</b> A gunner alone works the tube at {@link #SOLO_COOLDOWN_MULT}
 *       times the cycle; a loader within {@link #LOADER_REACH_CELLS} of the tube
 *       gives the authored rate.</li>
 *   <li><b>Loss.</b> If the gunner dies the tube is gone with him (taken off the
 *       field), and if the tube is shot the gunner is released to fight.</li>
 * </ul>
 *
 * <p><b>Threading.</b> Carrier executors run inside the parallel unit dispatch
 * and only {@link #beginSetup register} a tube; {@link TurretBehavior} reads a
 * tube and spends its rounds, one worker per tube; {@link #tick} runs serially
 * and does every roster mutation. Tube fields are volatile because the three
 * phases hand a tube to one another, never share it.
 *
 * <p>Every number here is <b>UNTUNED</b>; none was set to reach a target.
 */
public final class FieldMortarService {

    /** Cells of hostile contact the gunner's squad believes within which the tube is lifted rather than served. UNTUNED. */
    public static final float DANGER_CELLS = 11f;
    /** Seconds with nothing to shoot at before the gunner lifts the tube. UNTUNED. */
    public static final float IDLE_PACK_SECONDS = 25f;
    /** How near a squadmate must stand to load for the gunner. UNTUNED. */
    public static final float LOADER_REACH_CELLS = 5f;
    /** Cycle multiplier for a gunner working the tube with nobody to load it. UNTUNED. */
    public static final float SOLO_COOLDOWN_MULT = 1.8f;
    /** Margin past the warhead's radius within which a friendly forbids a round. */
    public static final float FRIENDLY_MARGIN_CELLS = 1.2f;
    /** Cells within which a squadmate's sight of a target counts as a spotted round. UNTUNED. */
    public static final float SPOTTER_REACH_CELLS = 34f;

    public enum Phase { SETTING_UP, SERVING, PACKING }

    /** One carrier's tube. */
    public static final class Tube {
        public final long carrierId;
        public final Faction faction;
        volatile int cellX;
        volatile int cellY;
        public final int squadId;
        public final DeployableEmplacementSpec spec;
        public final StructureDef structure;
        volatile Phase phase = Phase.SETTING_UP;
        volatile float phaseTimer;
        volatile long entityId;
        volatile float idleSeconds;
        volatile boolean hadTarget;
        volatile int rounds;
        /** Rounds this tube has fired since it was first set down, for evidence. */
        volatile int roundsFired;

        Tube(long carrierId, Faction faction, int cellX, int cellY, int squadId,
             DeployableEmplacementSpec spec, StructureDef structure, int rounds) {
            this.carrierId = carrierId;
            this.faction = faction;
            this.cellX = cellX;
            this.cellY = cellY;
            this.squadId = squadId;
            this.spec = spec;
            this.structure = structure;
            this.phaseTimer = spec.deployDuration();
            this.rounds = rounds;
        }

        public int cellX() { return cellX; }
        public int cellY() { return cellY; }
        public Phase phase() { return phase; }
        public long entityId() { return entityId; }
        public int rounds() { return rounds; }
        public int roundsFired() { return roundsFired; }
        public float idleSeconds() { return idleSeconds; }

        /** Whether the tube may fire this tick: standing, served and holding a round. */
        public boolean mayFire() {
            return phase == Phase.SERVING && rounds > 0;
        }

        /** Marks that the tube had something to shoot at this tick, which holds the idle clock at zero. */
        public void noteTarget() { hadTarget = true; }

        /** Spends one round from the magazine. */
        public void spendRound() {
            rounds = Math.max(0, rounds - 1);
            roundsFired++;
        }
    }

    private final Map<Long, Tube> byCarrier = new ConcurrentHashMap<>();
    private final Map<Long, Tube> byEntity = new ConcurrentHashMap<>();
    /** Rounds left in a gunner's stash between deployments, so lifting a half-spent tube does not refill it. */
    private final Map<Long, Integer> stash = new ConcurrentHashMap<>();
    private final ThreadLocal<LongBucket> scratch = ThreadLocal.withInitial(LongBucket::new);
    private int lost;
    private int destroyed;
    private int setUps;
    private int packs;

    /** Tubes set down so far, for evidence. */
    public int setUps() { return setUps; }
    /** Tubes lifted again, for evidence. */
    public int packs() { return packs; }
    /** Tubes lost with their gunner, for evidence. */
    public int lostWithGunner() { return lost; }
    /** Tubes shot to pieces while standing, for evidence. */
    public int destroyedStanding() { return destroyed; }

    /** The tube {@code carrier} is serving, or null. */
    public Tube tubeOfCarrier(long carrier) { return byCarrier.get(carrier); }

    /** The tube whose emplacement is {@code entity}, or null for any other turret. */
    public Tube tubeOfEntity(long entity) { return byEntity.get(entity); }

    /** Every tube now registered, for evidence and rendering. */
    public List<Tube> tubes() { return new ArrayList<>(byCarrier.values()); }

    /** Rounds {@code carrier} would take to a fresh deployment. */
    public int stashFor(long carrier, StructureDef structure) {
        return stash.getOrDefault(carrier, structure.mount.ammoCapacity);
    }

    /**
     * Registers the start of a deployment. Called from the carrier's executor, so it
     * only records: the emplacement is minted at the next serial {@link #tick}, once
     * the set-up time has run.
     *
     * @return false when the carrier already has a tube or has no rounds left
     */
    public boolean beginSetup(long carrier, Faction faction, int cellX, int cellY, int squadId,
                              DeployableEmplacementSpec spec) {
        StructureDef structure = TurretCatalogRegistry.requireStructure(spec.structureId());
        if (structure.mount.ammoCapacity <= 0) {
            throw new IllegalStateException("Crew-served structure '" + structure.id
                    + "' must declare a positive mount ammoCapacity");
        }
        int rounds = stashFor(carrier, structure);
        if (rounds <= 0) return false;
        Tube tube = new Tube(carrier, faction, cellX, cellY, squadId, spec, structure, rounds);
        return byCarrier.putIfAbsent(carrier, tube) == null;
    }

    /** Asks the gunner to lift the tube now (the player's or a commander's pack order). */
    public void requestPack(long carrier) {
        Tube tube = byCarrier.get(carrier);
        if (tube != null && tube.phase == Phase.SERVING) beginPack(tube);
    }

    private void beginPack(Tube tube) {
        tube.phase = Phase.PACKING;
        tube.phaseTimer = tube.spec.packDuration();
    }

    /**
     * The serial pass: ages set-up and pack clocks, mints and retires emplacements,
     * decides when a served tube is lifted, and settles a tube whose gunner or
     * emplacement has been killed.
     */
    public void tick(float dt, BattleControl sim) {
        if (byCarrier.isEmpty()) return;
        for (Tube tube : new ArrayList<>(byCarrier.values())) {
            long carrier = sim.resolveUnit(tube.carrierId);
            long entity = tube.entityId == 0L ? 0L : sim.resolveUnit(tube.entityId);
            if (carrier == 0L) {
                // No gunner, no tube: nobody else carries the baseplate.
                if (entity != 0L) sim.takeOffTheField(entity);
                if (tube.entityId != 0L) lost++;
                forget(tube);
                continue;
            }
            if (tube.entityId != 0L && entity == 0L) {
                // The emplacement was shot out from under its gunner.
                destroyed++;
                stash.put(tube.carrierId, tube.rounds);
                forget(tube);
                sim.world().setSecondaryCooldownTimer(carrier, tube.spec.redeployCooldown());
                continue;
            }
            switch (tube.phase) {
                case SETTING_UP -> {
                    tube.phaseTimer -= dt;
                    if (tube.phaseTimer <= 0f) mint(tube, sim);
                }
                case SERVING -> serve(tube, dt, sim);
                case PACKING -> {
                    tube.phaseTimer -= dt;
                    if (tube.phaseTimer <= 0f) {
                        if (entity != 0L) sim.takeOffTheField(entity);
                        stash.put(tube.carrierId, tube.rounds);
                        packs++;
                        forget(tube);
                        sim.world().setSecondaryCooldownTimer(carrier, tube.spec.redeployCooldown());
                    }
                }
            }
        }
    }

    private void mint(Tube tube, BattleControl sim) {
        // Set down where the gunner stands now: soft collision may have nudged him since he began.
        long gunner = sim.resolveUnit(tube.carrierId);
        tube.cellX = sim.world().cellX(gunner);
        tube.cellY = sim.world().cellY(gunner);
        long entity = sim.spawn(MapTurret.create("field-mortar-" + (++setUps), tube.faction,
                tube.structure.id, tube.cellX, tube.cellY));
        tube.entityId = entity;
        tube.phase = Phase.SERVING;
        tube.idleSeconds = 0f;
        byEntity.put(entity, tube);
    }

    private void serve(Tube tube, float dt, BattleControl sim) {
        if (tube.hadTarget) {
            tube.hadTarget = false;
            tube.idleSeconds = 0f;
        } else {
            tube.idleSeconds += dt;
        }
        if (tube.rounds <= 0 || tube.idleSeconds >= IDLE_PACK_SECONDS || hostileInDangerZone(tube, sim)) {
            beginPack(tube);
        }
    }

    /** Whether the gunner's squad believes a hostile is inside the tube's own dead zone, where it is no use. */
    private boolean hostileInDangerZone(Tube tube, BattleView sim) {
        if (tube.squadId == Squad.NO_SQUAD) return false;
        Squad squad = sim.getSquad(tube.squadId);
        if (squad == null) return false;
        float limit = Math.max(DANGER_CELLS, 0f);
        float limitSq = limit * limit;
        float tx = tube.cellX + 0.5f;
        float ty = tube.cellY + 0.5f;
        for (BelievedContact contact : squad.believedContacts()) {
            long id = sim.resolveUnit(contact.unitId());
            if (id == 0L || !tube.faction.hostileTo(sim.identity().faction(id))) continue;
            if (sim.identity().type(id).isTurret()) continue;
            float dx = sim.world().x(id) - tx;
            float dy = sim.world().y(id) - ty;
            if (dx * dx + dy * dy <= limitSq) return true;
        }
        return false;
    }

    private void forget(Tube tube) {
        byCarrier.remove(tube.carrierId, tube);
        if (tube.entityId != 0L) byEntity.remove(tube.entityId, tube);
    }

    // ---- turret-behaviour seam (parallel; read-only on the roster) ----

    /** Cycle multiplier for this tick: the authored rate with a loader beside the tube, slower for a lone gunner. */
    public float cooldownScale(Tube tube, BattleView sim) {
        if (hasLoader(tube, sim)) return 1f;
        return SOLO_COOLDOWN_MULT;
    }

    private boolean hasLoader(Tube tube, BattleView sim) {
        if (tube.squadId == Squad.NO_SQUAD) return false;
        int count = sim.squadMemberCount(tube.squadId);
        float reachSq = LOADER_REACH_CELLS * LOADER_REACH_CELLS;
        float tx = tube.cellX + 0.5f;
        float ty = tube.cellY + 0.5f;
        for (int i = 0; i < count; i++) {
            long member = sim.squadMemberAt(tube.squadId, i);
            if (member == tube.carrierId || !sim.world().isAlive(member)) continue;
            float dx = sim.world().x(member) - tx;
            float dy = sim.world().y(member) - ty;
            if (dx * dx + dy * dy <= reachSq) return true;
        }
        return false;
    }

    /**
     * Whether a round at {@code target} would land on somebody the tube's side does not
     * want hurt: any friendly body within the warhead's radius plus a margin of where the
     * round is aimed. A mortar does not choose whom its splash reaches, so the check is
     * the crew's: they will not call a round onto their own.
     */
    public boolean friendlyUnderRound(Tube tube, long target, BattleView sim) {
        float radius = tube.structure.mount.weapon.aoeRadius + FRIENDLY_MARGIN_CELLS;
        float x = sim.world().x(target);
        float y = sim.world().y(target);
        LongBucket bucket = scratch.get();
        sim.getUnitIndex().gather(x, y, radius, bucket);
        float radiusSq = radius * radius;
        for (int i = 0, n = bucket.size; i < n; i++) {
            long id = bucket.ids[i];
            if (id == tube.entityId || !sim.world().isAlive(id)) continue;
            if (!tube.faction.friendlyTo(sim.identity().faction(id))) continue;
            if (sim.identity().type(id).isTurret()) continue;
            float dx = sim.world().x(id) - x;
            float dy = sim.world().y(id) - y;
            if (dx * dx + dy * dy <= radiusSq) return true;
        }
        return false;
    }

    /**
     * Whether a member of the gunner's squad has the target in direct sight right now: a
     * spotted round, fired with the tube's full accuracy. Otherwise the round goes out on
     * remembered ground, at the weapon's no-line-of-sight penalty.
     */
    public boolean spotted(Tube tube, long target, BattleView sim) {
        if (tube.squadId == Squad.NO_SQUAD) return false;
        int count = sim.squadMemberCount(tube.squadId);
        float reachSq = SPOTTER_REACH_CELLS * SPOTTER_REACH_CELLS;
        int tx = sim.world().cellX(target);
        int ty = sim.world().cellY(target);
        for (int i = 0; i < count; i++) {
            long member = sim.squadMemberAt(tube.squadId, i);
            if (!sim.world().isAlive(member)) continue;
            float dx = sim.world().x(member) - sim.world().x(target);
            float dy = sim.world().y(member) - sim.world().y(target);
            if (dx * dx + dy * dy > reachSq) continue;
            if (sim.getGrid().hasLineOfSight(sim.world().cellX(member), sim.world().cellY(member), tx, ty)) {
                return true;
            }
        }
        return false;
    }
}
