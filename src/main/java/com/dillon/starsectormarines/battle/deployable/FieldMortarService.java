package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.combat.TargetMotion;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment.FireMission;
import com.dillon.starsectormarines.battle.fire.FireReach;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.DeployableEmplacementSpec;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerArray;

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
 *   <li><b>Loss and recovery.</b> If the gunner dies, the nearest living squadmate
 *       within {@link #RECREW_REACH_CELLS} who carries no special takes the tube up
 *       (see {@link #tick}); when nobody can, the tube is gone with him (taken off the
 *       field). If the tube is shot the gunner is released to fight.</li>
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
    /** How near a squadmate must stand to the tube to take it up when its gunner falls. UNTUNED. */
    public static final float RECREW_REACH_CELLS = 10f;
    /** How near a squadmate must stand to load for the gunner. UNTUNED. */
    public static final float LOADER_REACH_CELLS = 5f;
    /** Cycle multiplier for a gunner working the tube with nobody to load it. UNTUNED. */
    public static final float SOLO_COOLDOWN_MULT = 1.8f;
    /** Margin past the warhead's radius within which a friendly forbids a round. */
    public static final float FRIENDLY_MARGIN_CELLS = 1.2f;
    /** Cells within which a squadmate's sight of a target counts as a spotted round. UNTUNED. */
    public static final float SPOTTER_REACH_CELLS = 34f;

    /** Lowest belief confidence that counts as the side knowing where somebody is. */
    /** A mission's tube stands this far from any hostile structure: a nest reaches 24 cells, with a margin. */
    static final float MISSION_STANDOFF_CELLS = 26f;
    public static final float MISSION_MIN_CONFIDENCE = 0.3f;
    /** Ticks between a tube's looks for a fresh target on its mission. */
    public static final int MISSION_RELOOK_TICKS = 20;
    /** An incendiary round's heat: a little under a thrown thermite charge, spread by a shell's blast. */
    public static final int INCENDIARY_ROUND_HEAT = 2400;

    public enum Phase { SETTING_UP, SERVING, PACKING }

    /** One carrier's tube. */
    public static final class Tube {
        /** The gunner now serving the tube: the carrier it was set down by, or the squadmate who took it up. */
        public volatile long carrierId;
        /** The item the tube was carried as, which a squadmate taking it up is issued, or null when unknown (no re-crew). */
        final SpecialEquipmentDef item;
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
        volatile int roundsWithheld;
        /** Rounds fired at a body that stood on the tube's assigned fire mission, for evidence. */
        volatile int missionRoundsFired;
        /** The body the tube last chose on its mission, or 0, and the tick it last looked. */
        volatile long missionTarget;
        volatile int missionLookTick = Integer.MIN_VALUE;
        /** The incendiary heat of the round now going out, 0 for an ordinary shell; read when the round is built. */
        volatile int loadedHeat;
        /** Incendiary rounds this tube has fired, for evidence. */
        volatile int incendiaryRoundsFired;

        Tube(long carrierId, Faction faction, int cellX, int cellY, int squadId,
             DeployableEmplacementSpec spec, StructureDef structure, int rounds, SpecialEquipmentDef item) {
            this.carrierId = carrierId;
            this.item = item;
            this.faction = faction;
            this.cellX = cellX;
            this.cellY = cellY;
            this.squadId = squadId;
            this.spec = spec;
            this.structure = structure;
            this.phaseTimer = spec.deployDuration();
            this.rounds = rounds;
        }

        public long carrierId() { return carrierId; }
        public int cellX() { return cellX; }
        public int cellY() { return cellY; }
        public Phase phase() { return phase; }
        public long entityId() { return entityId; }
        public int rounds() { return rounds; }
        public int roundsFired() { return roundsFired; }
        /** Rounds the crew held back because a friendly stood under where the round would fall. */
        public int roundsWithheld() { return roundsWithheld; }
        /** One round held back for a friendly under it. */
        void noteWithheld() { roundsWithheld++; }
        public int missionRoundsFired() { return missionRoundsFired; }
        public long missionTarget() { return missionTarget; }
        public int loadedHeat() { return loadedHeat; }
        public int incendiaryRoundsFired() { return incendiaryRoundsFired; }
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

        /** Marks the round just spent as fired on the assigned mission. */
        public void noteMissionRound() { missionRoundsFired++; }

        /** Loads the next round: incendiary at this heat, or an ordinary shell at 0. */
        public void loadRound(int heat) {
            loadedHeat = Math.max(0, heat);
            if (loadedHeat > 0) incendiaryRoundsFired++;
        }
    }

    private final Map<Long, Tube> byCarrier = new ConcurrentHashMap<>();
    private final Map<Long, Tube> byEntity = new ConcurrentHashMap<>();
    /** Rounds left in a gunner's stash between deployments, so lifting a half-spent tube does not refill it. */
    private final Map<Long, Integer> stash = new ConcurrentHashMap<>();
    private final ThreadLocal<LongBucket> scratch = ThreadLocal.withInitial(LongBucket::new);
    /** The fire mission each squad's order carries now, read off the orders in the serial pass. */
    private final Map<Integer, FireMission> missions = new ConcurrentHashMap<>();
    /** Rounds fired and rounds held back for a friendly under them, by side, over the whole battle: {@code [fired, withheld]}. */
    private final Map<Faction, AtomicIntegerArray> roundsBySide = new ConcurrentHashMap<>();
    /** The sections working tubes now: the squad-level behaviour that halts a crew and leashes the rest of its squad. */
    private final MortarSections sections = new MortarSections();
    private int lost;
    private int recrewed;
    private int destroyed;
    private int setUps;
    private int packs;

    /** The mortar sections: crews halted to work a tube with the rest of their squad leashed to it. */
    public MortarSections sections() { return sections; }

    /** Tubes set down so far, for evidence. */
    public int setUps() { return setUps; }
    /** Tubes lifted again, for evidence. */
    public int packs() { return packs; }
    /** Tubes lost with their gunner, for evidence. */
    public int lostWithGunner() { return lost; }
    /** Tubes a squadmate took up after their gunner fell, for evidence. */
    public int recrewed() { return recrewed; }
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
        return beginSetup(carrier, faction, cellX, cellY, squadId, spec, null);
    }

    /**
     * As above, naming the item the gunner carries so that a squadmate may be issued it and take the tube up should the
     * gunner fall. A tube registered without its item cannot be re-crewed and is lost with its gunner.
     */
    public boolean beginSetup(long carrier, Faction faction, int cellX, int cellY, int squadId,
                              DeployableEmplacementSpec spec, SpecialEquipmentDef item) {
        StructureDef structure = TurretCatalogRegistry.requireStructure(spec.structureId());
        if (structure.mount.ammoCapacity <= 0) {
            throw new IllegalStateException("Crew-served structure '" + structure.id
                    + "' must declare a positive mount ammoCapacity");
        }
        int rounds = stashFor(carrier, structure);
        if (rounds <= 0) return false;
        Tube tube = new Tube(carrier, faction, cellX, cellY, squadId, spec, structure, rounds, item);
        return byCarrier.putIfAbsent(carrier, tube) == null;
    }

    /** Refills a tube to its mount's capacity (an ammunition truck's delivery); returns the rounds added. */
    public int rearm(Tube tube) {
        int full = tube.structure.mount.ammoCapacity;
        int added = Math.max(0, full - tube.rounds);
        tube.rounds = Math.max(tube.rounds, full);
        return added;
    }

    /**
     * Refills every tube a squad serves, standing or packed, to its mount's capacity (an ammunition truck's delivery to
     * the place the squad holds): a standing tube is rearmed, and a gunner's stash is emptied of its count, so the
     * next set-down is full. Returns the tubes and stashes refilled.
     */
    public int restockSquad(int squadId, BattleView sim) {
        int n = 0;
        for (Tube tube : tubes()) {
            if (tube.squadId != squadId) continue;
            rearm(tube);
            n++;
        }
        for (Long carrier : new ArrayList<>(stash.keySet())) {
            if (!inSquad(carrier, squadId, sim) || byCarrier.containsKey(carrier)) continue;
            stash.remove(carrier);
            n++;
        }
        return n;
    }

    /**
     * Rounds a squad's tubes hold, standing or packed, and what they hold full, {@code [rounds, full]}; {@code [0, 0]}
     * for a squad that has not set a tube down yet, whose gunner still carries a full one.
     */
    public int[] squadRounds(int squadId, BattleView sim) {
        int rounds = 0, full = 0;
        for (Tube tube : tubes()) {
            if (tube.squadId != squadId) continue;
            rounds += tube.rounds;
            full += tube.structure.mount.ammoCapacity;
        }
        StructureDef structure = null;
        for (Map.Entry<Long, Integer> e : stash.entrySet()) {
            if (!inSquad(e.getKey(), squadId, sim) || byCarrier.containsKey(e.getKey())) continue;
            if (structure == null) structure = TurretCatalogRegistry.requireStructure(FIELD_MORTAR_STRUCTURE);
            rounds += e.getValue();
            full += structure.mount.ammoCapacity;
        }
        return new int[]{rounds, full};
    }

    /** The field mortar's emplacement, whose capacity a packed tube is counted against. */
    static final String FIELD_MORTAR_STRUCTURE = "structure.deployable-field-mortar";

    private static boolean inSquad(long carrier, int squadId, BattleView sim) {
        long unit = sim.resolveUnit(carrier);
        if (unit == 0L) return false;
        Squad squad = sim.squadOf(unit);
        return squad != null && squad.id == squadId;
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
        refreshMissions(sim);
        tickTubes(dt, sim);
        sections.tick(dt, this, sim);
    }

    private void tickTubes(float dt, BattleControl sim) {
        if (byCarrier.isEmpty()) return;
        for (Tube tube : new ArrayList<>(byCarrier.values())) {
            long carrier = sim.resolveUnit(tube.carrierId);
            long entity = tube.entityId == 0L ? 0L : sim.resolveUnit(tube.entityId);
            if (carrier == 0L && recrew(tube, sim)) {
                carrier = sim.resolveUnit(tube.carrierId);
            }
            if (carrier == 0L) {
                // No gunner, and no squadmate near enough to take the baseplate up: the tube is lost.
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

    /**
     * A tube whose gunner has fallen is taken up by the nearest living squadmate within {@link #RECREW_REACH_CELLS} of it
     * who carries no special of his own: he is issued the item, holds the tube from then on, and the tube keeps its phase and
     * the rounds it has left. Returns whether somebody took it. Squadmates carrying something else are not asked to drop it,
     * and a tube registered without its item (or whose squad is gone) cannot be recovered.
     */
    private boolean recrew(Tube tube, BattleControl sim) {
        if (tube.item == null || tube.squadId == Squad.NO_SQUAD) return false;
        float reachSq = RECREW_REACH_CELLS * RECREW_REACH_CELLS;
        float tx = tube.cellX + 0.5f;
        float ty = tube.cellY + 0.5f;
        long best = 0L;
        float bestSq = Float.MAX_VALUE;
        for (int i = 0, n = sim.squadMemberCount(tube.squadId); i < n; i++) {
            long member = sim.squadMemberAt(tube.squadId, i);
            if (member == tube.carrierId || !sim.world().isAlive(member)) continue;
            if (sim.world().hasSecondaryWeapon(member)) continue;
            float dx = sim.world().x(member) - tx;
            float dy = sim.world().y(member) - ty;
            float dSq = dx * dx + dy * dy;
            if (dSq > reachSq || dSq >= bestSq) continue;
            best = member;
            bestSq = dSq;
        }
        if (best == 0L) return false;
        World world = sim.world();
        world.attachSpecialEquipment(best, tube.item, tube.rounds);
        world.setSecondaryCooldownTimer(best, 0f);
        world.setSecondaryActionTimer(best, 1f);
        world.setSecondaryAimTargetId(best, 0L);
        world.setSecondaryFired(best, false);
        stash.remove(tube.carrierId);
        byCarrier.remove(tube.carrierId, tube);
        tube.carrierId = best;
        byCarrier.put(best, tube);
        stash.put(best, tube.rounds);
        recrewed++;
        return true;
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

    // ---- fire missions ----

    /**
     * Reads the fire missions off the squads' orders. A mission is part of a squad's order and ends with it: the tube
     * serves the ground it was told to for as long as its squad's order says, and on its own belief after.
     */
    private void refreshMissions(BattleControl sim) {
        for (Squad squad : sim.getSquads()) {
            ObjectiveAssignment order = squad.assignmentForExecution();
            FireMission mission = order == null ? null : order.fireMission();
            if (mission == null) missions.remove(squad.id);
            else missions.put(squad.id, mission);
        }
    }

    /**
     * The heat of the round a tube fires at {@code target}: {@link #INCENDIARY_ROUND_HEAT} when the target is the
     * tube's incendiary mission's and the fire it would light could not come to the tube's own side
     * ({@link FireReach}), else 0, an ordinary shell.
     */
    public int roundHeat(Tube tube, long target, BattleControl sim) {
        FireMission mission = missionOf(tube.squadId);
        if (mission == null || !mission.incendiary() || target == 0L || target != tube.missionTarget) return 0;
        WeaponDef weapon = tube.structure.mount.weapon;
        if (FireReach.enabled() && FireReach.reachesSide(sim.fireField(), sim.world().cellX(target),
                sim.world().cellY(target), FireReach.litCells(weapon.aoeRadius, weapon.hitSpread), tube.faction, sim)) {
            return 0;
        }
        return INCENDIARY_ROUND_HEAT;
    }

    /** Counts one round of a tube's: fired, or held back because a friendly stood under where it would fall. */
    public void countRound(Tube tube, boolean withheld) {
        if (withheld) tube.noteWithheld();
        roundsBySide.computeIfAbsent(tube.faction, f -> new AtomicIntegerArray(2)).incrementAndGet(withheld ? 1 : 0);
    }

    /** Rounds {@code faction}'s tubes have fired this battle. */
    public int roundsFiredBy(Faction faction) {
        AtomicIntegerArray n = roundsBySide.get(faction);
        return n == null ? 0 : n.get(0);
    }

    /** Rounds {@code faction}'s tubes held back this battle for a friendly under them. */
    public int roundsWithheldBy(Faction faction) {
        AtomicIntegerArray n = roundsBySide.get(faction);
        return n == null ? 0 : n.get(1);
    }

    /** The fire mission {@code squadId}'s order carries, or null. */
    public FireMission missionOf(int squadId) {
        return squadId == Squad.NO_SQUAD ? null : missions.get(squadId);
    }

    /**
     * The body a tube should be firing at: its current target when that still stands on the mission, else the best body the
     * <em>side</em> knows to stand on the ground the squad's order names, in the tube's band. Zero when the squad has no
     * mission or nothing on it is known, in which case the tube fires on its own section's belief as it always did.
     * Looks again at most every {@link #MISSION_RELOOK_TICKS} ticks.
     */
    public long missionTarget(Tube tube, long current, int tick, BattleView sim) {
        FireMission mission = missionOf(tube.squadId);
        if (mission == null) {
            tube.missionTarget = 0L;
            return 0L;
        }
        long held = tube.missionTarget;
        if (held != 0L && held == current && sim.resolveUnit(held) != 0L && onMission(mission, held, sim)) return held;
        if ((long) tick - tube.missionLookTick < MISSION_RELOOK_TICKS) return 0L;
        tube.missionLookTick = tick;
        float range = tube.structure.mount.weapon.range;
        float minRange = tube.structure.mount.weapon.minRange;
        // Only a body the crew could call a round onto: one with nobody of the side's under where it would fall, so a
        // mission over ground its own mechs and riflemen stand near picks the target clear of them rather than none.
        long pick = pickMissionTarget(mission, tube.faction, tube.cellX + 0.5f, tube.cellY + 0.5f, minRange, range, sim,
                id -> !friendlyUnderRound(tube, id, sim));
        tube.missionTarget = pick;
        return pick;
    }

    private static boolean onMission(FireMission mission, long id, BattleView sim) {
        float dx = sim.world().x(id) - (mission.cellX() + 0.5f);
        float dy = sim.world().y(id) - (mission.cellY() + 0.5f);
        return dx * dx + dy * dy <= (float) mission.radiusCells() * mission.radiusCells();
    }

    /**
     * The best body standing on {@code mission} that a tube at {@code (originX, originY)} can reach and that the
     * {@code faction} may fire on: a structure before a man, then the nearest to the mission's centre, then the lowest id so
     * the answer repeats. Fair: a man no squad of the side holds a belief in is never chosen, whatever the order says. A
     * structure on the mission's ground is fired on without a sighting, because a mission names ground the side already
     * knows (a disclosed fort's works, which it is told of with the fort) and a structure does not move away from it.
     */
    public long pickMissionTarget(FireMission mission, Faction faction, float originX, float originY,
                                  float minRange, float maxRange, BattleView sim) {
        return pickMissionTarget(mission, faction, originX, originY, minRange, maxRange, sim, id -> true);
    }

    /** As {@link #pickMissionTarget(FireMission, Faction, float, float, float, float, BattleView)}, of the bodies {@code clear} admits. */
    public long pickMissionTarget(FireMission mission, Faction faction, float originX, float originY,
                                  float minRange, float maxRange, BattleView sim, LongPredicate clear) {
        float cx = mission.cellX() + 0.5f;
        float cy = mission.cellY() + 0.5f;
        LongBucket bucket = scratch.get();
        sim.getUnitIndex().gather(cx, cy, mission.radiusCells(), bucket);
        Set<Long> known = null;
        long best = 0L;
        boolean bestStructure = false;
        float bestGap = Float.MAX_VALUE;
        for (int i = 0, n = bucket.size; i < n; i++) {
            long id = bucket.ids[i];
            if (!sim.world().isAlive(id) || !faction.hostileTo(sim.identity().faction(id))) continue;
            float fromTube = (float) Math.hypot(sim.world().x(id) - originX, sim.world().y(id) - originY);
            if (fromTube < minRange + 0.5f || fromTube > maxRange) continue;
            if (!onMission(mission, id, sim)) continue;
            if (!clear.test(id)) continue;
            boolean structure = sim.identity().type(id).isTurret();
            if (!structure) {
                if (known == null) known = sideKnows(faction, sim);
                if (!known.contains(id)) continue;
            }
            float gap = (float) Math.hypot(sim.world().x(id) - cx, sim.world().y(id) - cy);
            boolean better = best == 0L || (structure && !bestStructure) || (structure == bestStructure
                    && (gap < bestGap || (gap == bestGap && id < best)));
            if (better) {
                best = id;
                bestStructure = structure;
                bestGap = gap;
            }
        }
        return best;
    }

    /**
     * Whether a hostile structure stands within {@link #MISSION_STANDOFF_CELLS} of {@code (x, y)}: a tube set down on a
     * mission there would be inside a gun's reach, which is what a standoff weapon is for avoiding. Works are disclosed with
     * their fort, so the structures are the side's own knowledge.
     */
    public boolean underHostileGuns(Faction faction, float x, float y, BattleView sim) {
        LongBucket bucket = scratch.get();
        sim.getUnitIndex().gather(x, y, MISSION_STANDOFF_CELLS, bucket);
        for (int i = 0, n = bucket.size; i < n; i++) {
            long id = bucket.ids[i];
            if (!sim.world().isAlive(id) || !faction.hostileTo(sim.identity().faction(id))) continue;
            if (sim.identity().type(id).isTurret() && gunned(sim.turretState().structure(id))) return true;
        }
        return false;
    }

    /**
     * Whether a structure carries a gun that hurts: a bore, a shield core or a fire-control console stands on a
     * placeholder mount that does no damage, so a tube may set down inside its "reach" (a lane's mortar team on its
     * bore's support line, twenty cells from the mouth).
     */
    static boolean gunned(StructureDef structure) {
        return structure != null && structure.mount != null && structure.mount.weapon != null
                && structure.mount.weapon.damage > 0f;
    }

    /** Every body any squad of {@code faction} holds a belief in: the side's picture, not one squad's. */
    private static Set<Long> sideKnows(Faction faction, BattleView sim) {
        Set<Long> known = new HashSet<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != faction) continue;
            for (BelievedContact contact : squad.believedContacts()) {
                if (contact.confidence() < MISSION_MIN_CONFIDENCE) continue;
                long id = sim.resolveUnit(contact.unitId());
                if (id != 0L) known.add(id);
            }
        }
        return known;
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
     * round is aimed, widened by the scatter the round can land with at that range. A mortar
     * does not choose whom its splash reaches, nor exactly where it falls, so the check is
     * the crew's: they will not call a round onto their own.
     */
    public boolean friendlyUnderRound(Tube tube, long target, BattleView sim) {
        WeaponDef weapon = tube.structure.mount.weapon;
        float targetX = sim.world().x(target);
        float targetY = sim.world().y(target);
        float range = (float) Math.hypot(targetX - (tube.cellX + 0.5f), targetY - (tube.cellY + 0.5f));
        // The round is led, so the ground to keep clear is where the target
        // will be when it lands, not where it stands now.
        float speed = weapon.projectileCellsPerSec();
        float leadTime = speed > 0f ? range / speed : weapon.flightSec;
        float x = targetX + TargetMotion.velocityX(sim.bodies(), sim.movement(), target) * leadTime;
        float y = targetY + TargetMotion.velocityY(sim.bodies(), sim.movement(), target) * leadTime;
        float scatter = weapon.hitSpread * Math.min(1f, range / Math.max(0.0001f, weapon.range));
        float radius = weapon.aoeRadius + scatter + FRIENDLY_MARGIN_CELLS;
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
