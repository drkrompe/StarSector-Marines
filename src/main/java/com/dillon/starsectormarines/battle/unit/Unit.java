package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.drone.Drone;
import com.dillon.starsectormarines.battle.infantry.EquipmentDrop;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.mech.MechLoadoutState;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.command.objective.Objective;

import java.util.Random;

/**
 * One combatant in the battle simulation. Plain data — all behavior lives on
 * {@link BattleSimulation}. Fields are public for hot-path access from the
 * tick loop; the package keeps the surface narrow.
 *
 * <p>Position is split: {@link #getCellX}/{@link #getCellY} are the logical cell
 * (what pathfinding sees), while {@link #getRenderX}/{@link #getRenderY} are the
 * smooth-interpolated position inside the cell grid (in cell units, fractional)
 * that the renderer reads. The two coincide when the unit is at rest or has
 * just landed in a new cell. (Both pairs are SoA columns now — cell on the
 * registry, render on the {@link RenderPositionService} — reached through these
 * accessors, not direct fields.)
 *
 * <p>{@link #path} + {@link #pathIdx} + {@link #getMoveProgress} describe the
 * current movement step. The simulation rebuilds {@code path} when the unit is
 * between cells ({@code moveProgress == 0}) and lerps render position toward
 * {@code path[pathIdx]} as move-progress climbs from 0 to 1; on arrival
 * the logical cell advances and progress resets.
 */
public class Unit {

    /** Sentinel value for {@link #squadId} when the unit isn't part of a squad — defenders, solo combatants, anyone not deboarded from a marine shuttle. */
    public static final int NO_SQUAD = -1;

    /**
     * Monotonic entity id assigned by {@link com.dillon.starsectormarines.battle.unit.UnitRegistry}
     * on registration. {@code 0} means "not yet allocated" (matches the
     * registry's reserved sentinel); a non-zero value is stable for the
     * life of the unit and never recycled. Future SoA promotion will key
     * off this id rather than {@link #id} (the string label, retained for
     * logs / debug).
     */
    public long entityId = 0L;

    /**
     * Back-reference to the registry currently holding this unit, or
     * {@code null} when not allocated. Lets the per-unit accessors resolve
     * their SoA slot (by {@link #entityId}, via {@link #idx()}) without
     * threading the registry through every call site, and serves as the
     * allocation/liveness marker ({@code registry == null} ⇒ released or
     * pre-allocate). Set by {@link UnitRegistry#allocate}; cleared by release.
     * (The cached {@code denseIdx} field this used to sit beside is gone — the
     * slot is now always resolved through the registry's id→index map, the
     * single source of truth, so a held ref can never carry a stale index.)
     */
    public UnitRegistry registry;

    /**
     * The decomposed render-position service this unit's render coordinates
     * live in, keyed by {@link #entityId}. Set once by
     * {@link UnitRegistry#allocate} and — unlike {@link #registry} — <b>not</b>
     * cleared on release, so {@link #getRenderX()} / {@link #getRenderY()} keep
     * resolving the death-pose location for a released corpse. {@code null}
     * only in the pre-allocate window, where {@link #localRenderX} /
     * {@link #localRenderY} are the seed.
     */
    public RenderPositionService renderPositions;

    public final String id;
    public final Faction faction;
    /** Archetype — drives sprite + base stat block. Set once at construction. */
    public final UnitType type;
    /** Squad identity. Set to a positive int when this unit deboarded as part of a fireteam; {@link #NO_SQUAD} for solo units. */
    public int squadId = NO_SQUAD;
    /**
     * Per-unit RNG owned by the thread processing this unit during UPDATE_UNITS.
     * Replaces sim-shared {@code BattleSimulation.rng} for parallel-decide-phase
     * call sites (weapon hit rolls, shot endpoint scatter, flee wander,
     * patrol jitter, drone swarm) so the fork-join dispatch has no Random
     * contention. Sim-global RNG keeps serving serial-phase callers
     * (death-pose pick in {@code DamageResolver.resolve}, map gen, setup). Seeded
     * with system time by default — we don't require bit-reproducible
     * battles.
     */
    public final Random rng = new Random();

    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> The logical death cell
     * used to have a post-release snapshot, but its three
     * post-release readers (the turret / hub demolition + mech wreck handlers)
     * now read the cell off the {@link DeathEvent} snapshot instead, so the cell
     * needs no shadow on the corpse — it's pure construction input now, like the
     * Group-S {@code seed*} stats. {@link UnitRegistry#allocate} copies these
     * into the SoA cell arrays and the registry is canonical from then on;
     * {@code release} does NOT snapshot them back. Once allocated, go through
     * {@link #getCellX} / {@link #getCellY} / {@link #setCellPos} (fail-loud on
     * an unregistered unit). Public for the same sibling-package seeding reason
     * as {@code seed*}.
     */
    public int seedCellX;
    public int seedCellY;

    /**
     * <b>Don't read directly.</b> Smooth render-position <em>pre-allocate seed
     * only</em>. {@link UnitRegistry#allocate} copies these into the
     * {@link RenderPositionService}, after which that service is canonical and
     * survives release — there is no post-release snapshot back to these fields.
     * Go through {@link #getRenderX} / {@link #getRenderY} /
     * {@link #setRenderPos}.
     */
    public float localRenderX;
    public float localRenderY;

    /**
     * Current path as a flat {@code int[]} of interleaved {@code x,y} pairs —
     * cell {@code i} sits at {@code (path[i*2], path[i*2+1])}. Empty
     * ({@link GridPathfinder#EMPTY_PATH}) when the unit has nothing scheduled.
     * Flattened from the old {@code List<int[]>} to drop the per-cell
     * {@code int[2]} allocations on each pathfind.
     */
    public int[] path = GridPathfinder.EMPTY_PATH;
    /** Index of the next cell along {@link #path} to step into — addresses cells, not raw int positions (i.e. path slots {@code [pathIdx*2, pathIdx*2+1]}). */
    public int pathIdx = 0;

    /** Convenience accessor — number of cells in {@link #path}. */
    public int pathCellCount() { return path.length >> 1; }
    /** Convenience accessor — x coordinate of the i-th cell along {@link #path}. */
    public int pathCellX(int i) { return path[i << 1]; }
    /** Convenience accessor — y coordinate of the i-th cell along {@link #path}. */
    public int pathCellY(int i) { return path[(i << 1) | 1]; }
    /** True when the unit has no path scheduled. Match for the old {@code path.isEmpty()} check. */
    public boolean pathEmpty() { return path.length == 0; }

    public void advanceAlongPath(float dt) {
        if (pathIdx >= pathCellCount()) return;
        // Per-tick movement step: resolve the dense slot once, then read/write
        // the cell + move-progress columns by index (not through the per-call
        // OO accessors, which would each re-probe the id→index map).
        int idx = idx();
        int nextX = pathCellX(pathIdx);
        int nextY = pathCellY(pathIdx);
        int curX = registry.getCellX(idx);
        int curY = registry.getCellY(idx);
        float dx = nextX - curX;
        float dy = nextY - curY;
        float cellDist = (float) Math.sqrt(dx * dx + dy * dy);
        if (cellDist < 0.0001f) { pathIdx++; return; }
        float mp = registry.getMoveProgress(idx) + (moveSpeed * dt) / cellDist;
        if (mp >= 1f) {
            registry.setCellPos(idx, nextX, nextY);
            setRenderPos(nextX, nextY);
            registry.setMoveProgress(idx, 0f);
            pathIdx++;
        } else {
            registry.setMoveProgress(idx, mp);
            setRenderPos(curX + dx * mp, curY + dy * mp);
        }
    }

    // Stats — initialized from UnitType, then mutable per-unit so captain traits
    // and mission modifiers can adjust an individual without changing the archetype.
    public float moveSpeed;
    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> Now identical in shape
     * to {@link #seedMaxHp} and the cell pair: {@link UnitRegistry#allocate}
     * copies this into the SoA hp array and the registry is canonical from then
     * on; {@code release} does NOT snapshot it back. Once allocated, go through
     * {@link #getHp} / {@link #setHp} (both fail-loud on an unregistered unit).
     *
     * <p>The post-release hp snapshot ({@code localHp}) that this replaced is
     * gone: every held-{@link Unit}-ref liveness check now goes through
     * {@link #isAlive()}, which short-circuits on the {@code registry == null}
     * release marker before touching the dense hp slot — so a released held ref
     * reads as dead without a corpse-window hp shadow. (The held refs that once
     * needed that shadow — mech salvo targets, the pending-mutation queue, a
     * wiped squad's leader — are id-resolved now anyway. See the
     * {@code entity-id-handle} story.)
     *
     * <p>Public so {@link UnitRegistry} (a sibling package) can seed the slot at
     * allocate time. Write-only construction input: the ctor archetype seed and
     * the subclass overrides (see {@code seed*}).
     */
    public float seedHp;
    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> The Group-S stat
     * columns (max HP + the three attack stats) differ from {@link #localHp}:
     * {@link UnitRegistry#allocate} copies these into the SoA arrays and the
     * registry is canonical from then on, and {@code release} does NOT snapshot
     * them back. All four accessors read the registry unconditionally (fail-loud
     * on an unregistered unit, like the mid-combat columns) since nothing reads
     * them post-release — the HUD that once read {@code getMaxHp} post-release now
     * snapshots the row by value at {@code update()} (see {@code SquadDetailPanel}).
     * These fields are write-only <em>construction</em> input: the ctor archetype seed,
     * the subclass overrides
     * ({@link com.dillon.starsectormarines.battle.drone.Drone} /
     * {@link com.dillon.starsectormarines.battle.drone.DroneHubUnit} /
     * {@link com.dillon.starsectormarines.battle.turret.MapTurret}), and the
     * shuttle/vehicle deboard loadout. Once allocated, go through
     * {@link #getMaxHp} / {@link #getAttackDamage} / {@link #getAttackRange} /
     * {@link #getAccuracy}.
     */
    public float seedMaxHp;
    public float seedAttackDamage;
    public float seedAttackRange;
    public float seedAccuracy;
    /** How far this unit can see (cells). Drives fog-of-war shadowcast radius. Initialized from {@link UnitType#visionRange}; 0 falls back to {@link #getAttackRange() attackRange}. */
    public float visionRange;
    public float attackCooldown;

    /**
     * Current-target entity id; {@code 0L} = no target. Canonical storage is
     * {@code registry.targetId[idx]} — resolve to a {@link Unit} via
     * {@link BattleSimulation#targetOf(Unit)}.
     *
     * <p>The long is generation-free dangling-ref hygiene: a released target id
     * resolves cleanly to {@code null} via the registry without the holder
     * needing its own {@code isAlive()} branch. Writes go through
     * {@link #setTarget(Unit)} so null-vs-instance is handled in one place.
     *
     * <p><b>Mid-combat-column contract.</b> Unlike hp/cell, these mid-combat
     * columns (targetId, cooldown/timers, burst/secondary/fallback/reposition/wander)
     * carry no {@code local*} shadow on {@link Unit} — their canonical storage is
     * the registry's SoA arrays and they are only meaningful for a registered, live
     * unit. The accessors read/write the registry unconditionally; calling them on
     * an unregistered unit is a programming error (fail-loud) now that the live
     * registry is the sole roster and no observable unit is ever unregistered.
     */
    public final long getTargetId() {
        return registry.getTargetId(idx());
    }

    public final void setTargetId(long v) {
        registry.setTargetId(idx(), v);
    }

    /**
     * Convenience setter for the target id: stores {@code t.entityId}, or
     * {@code 0L} when {@code t == null}. Single chokepoint so every writer
     * gets identical null-handling, and so a future {@code setTarget} that
     * also touches sibling state (attacker index hint, hit-streak counters)
     * only has to grow once.
     */
    public void setTarget(Unit t) {
        setTargetId((t == null) ? 0L : t.entityId);
    }

    /**
     * Close-wall radius for "air" line-of-sight, in cells. When &gt; 0, walls
     * within this many cells of this unit's position are treated as transparent
     * for LoS checks involving this unit — both as shooter and as target.
     * Models flying mounts that hover above building footprints: a drone can
     * fire OUT of the building it's directly above, and ground combatants can
     * fire UP at the drone through the same close walls. Both directions use
     * the same radius so the rule is symmetric. 0 (default) means standard
     * grid LoS; only {@link Drone} sets this today, but {@link
     * com.dillon.starsectormarines.battle.air.Shuttle}-mounted turrets pass
     * their own equivalent radius through {@code TurretAim.State}.
     */
    public float airLosRadius = 0f;

    /** Role drives behavior dispatch in the sim. Default {@link UnitRole#COMBATANT} matches pre-role behavior. */
    public UnitRole role = UnitRole.COMBATANT;
    /** Objective this unit is acting on, when the role requires one (charge site for a planter, exfil zone for a VIP, position to camp for an objective camper). Null for plain combatants. */
    public Objective assignedObjective;
    /** {@link UnitRole#KIT_RETRIEVER} target — the dropped kit this unit is heading to recover. Cleared when picked up or when the drop is consumed by someone else. */
    public EquipmentDrop equipmentDropTarget;

    /**
     * Break-contact fall-back state. {@code fallbackTimer} is sim-seconds
     * remaining in fall-back; &gt;0 means the unit is breaking contact toward
     * the cached fall-back cell ({@code fallbackCellX}/{@code fallbackCellY},
     * -1 = none). Canonical storage lives in the registry's SoA arrays.
     */
    public final float getFallbackTimer() {
        return registry.getFallbackTimer(idx());
    }

    public final void setFallbackTimer(float v) {
        registry.setFallbackTimer(idx(), v);
    }

    public final int getFallbackCellX() {
        return registry.getFallbackCellX(idx());
    }

    public final int getFallbackCellY() {
        return registry.getFallbackCellY(idx());
    }

    /** Every callsite writes the fall-back cell pair together (break-contact pick, inline fallback write), so the paired setter matches access and hits both SoA slots in one dispatch. */
    public final void setFallbackCell(int x, int y) {
        registry.setFallbackCell(idx(), x, y);
    }

    /**
     * Sim-seconds until this unit is next eligible to micro-reposition
     * between shots. Story G — replaces the prior per-shot 30% RNG roll with a
     * real cooldown so a setup machine gunner in heavy cover doesn't twitch
     * every burst, and the squad's individual marines visibly shift at
     * different times (cooldowns decorrelate as they reset on different shots).
     * Ticked down each tick by {@link com.dillon.starsectormarines.battle.infantry.InfantryUnitPrep#tickCooldowns}.
     * Canonical storage lives in {@code registry.repositionCooldown[idx]}.
     */
    public final float getRepositionCooldown() {
        return registry.getRepositionCooldown(idx());
    }

    public final void setRepositionCooldown(float v) {
        registry.setRepositionCooldown(idx(), v);
    }

    /**
     * {@link UnitRole#FLEE} idle pause between wander legs. While &gt;0 the
     * civilian stands at their current cell instead of picking a new
     * destination. Rolled fresh on arrival; ignored when a threat is in range.
     * Canonical storage lives in {@code registry.wanderDwellTimer[idx]}.
     */
    public final float getWanderDwellTimer() {
        return registry.getWanderDwellTimer(idx());
    }

    public final void setWanderDwellTimer(float v) {
        registry.setWanderDwellTimer(idx(), v);
    }

    /**
     * Sim-tick index of the last {@code rollReprioritizeOnHit} attempt
     * against this unit. Compared to {@link com.dillon.starsectormarines.battle.sim.BattleSimulation#simTickIndex}
     * to gate the reprio to one roll per tick — without the gate, a 4-marine
     * squad opening up in the same tick gives the mech a ~82% per-tick
     * reprio chance from the base 0.35 rate (1 − 0.65⁴), which produces
     * near-constant target twitching. Only mechs + turrets pay attention
     * to this field; infantry leaves it at the -1 sentinel. {@code -1}
     * before the first reprio attempt and stays at -1 for units that
     * never qualify (infantry, civilians, dead units).
     */
    public volatile int lastReprioTickIndex = -1;

    /**
     * Cell this unit returns to when nothing else is happening — the
     * "post" they were assigned at spawn. Used by {@link UnitRole#GARRISON}
     * for idle behavior: members path to their home and idle there while
     * their squad is UNAWARE. -1 sentinel = no home assigned (units that
     * roam, e.g. patrols and marines, don't set this).
     */
    public int homeCellX = -1;
    public int homeCellY = -1;

    /** Primary handheld weapon. Null for legacy / non-marine units — fire stats fall back to the {@link UnitType} defaults baked into {@link #getAttackRange() attackRange}/{@link #getAttackDamage() attackDamage}/etc. Assigned at deboard time for marines. */
    public MarineWeapon primaryWeapon;
    /** Optional secondary slot (rocket launcher, future grenades). Null = no secondary. */
    public MarineSecondary secondaryWeapon;
    /** Rounds remaining on the {@link #secondaryWeapon}. Decremented on each secondary shot; once zero the marine reverts to primary fire. */
    public int secondaryAmmo;
    /** Latched on launch within the current aim cycle so we only emit one shot per cycle, even though the trigger condition holds for several ticks past launch. */
    public boolean secondaryFiredThisAction = false;

    /**
     * Independent cooldown for the secondary weapon (sim-seconds) so it doesn't
     * share state with the primary's cooldown timer. Canonical storage lives in
     * {@code registry.secondaryCooldownTimer[idx]}.
     */
    public final float getSecondaryCooldownTimer() {
        return registry.getSecondaryCooldownTimer(idx());
    }

    public final void setSecondaryCooldownTimer(float v) {
        registry.setSecondaryCooldownTimer(idx(), v);
    }

    /**
     * Sim-seconds remaining in the secondary's aim-then-fire animation. While
     * &gt;0 the marine is locked in place and the renderer draws the
     * {@link MarineSecondary#aimSpritePath} pose; the actual shot launches when
     * this drops below {@link MarineSecondary#aimDuration}/2.
     */
    public final float getSecondaryActionTimer() {
        return registry.getSecondaryActionTimer(idx());
    }

    public final void setSecondaryActionTimer(float v) {
        registry.setSecondaryActionTimer(idx(), v);
    }

    /**
     * Entity id of the target locked at the start of the aim cycle. The rocket
     * fires at this entity even if the original target dies mid-aim — the
     * launcher's already committed. Resolve through
     * {@link BattleSimulation#resolveUnit(long)}; {@code 0L} = no aim target.
     * Writes go through {@link #setSecondaryAimTarget(Unit)}.
     */
    public final long getSecondaryAimTargetId() {
        return registry.getSecondaryAimTargetId(idx());
    }

    public final void setSecondaryAimTargetId(long v) {
        registry.setSecondaryAimTargetId(idx(), v);
    }

    /** Sets {@link #getSecondaryAimTargetId()} from a {@link Unit} ref (null → 0L). */
    public void setSecondaryAimTarget(Unit t) {
        setSecondaryAimTargetId((t == null) ? 0L : t.entityId);
    }

    /**
     * Burst rounds queued after the AI's initial primary shot; the sim emits
     * one per {@link MarineWeapon#burstSpacing} interval until exhausted.
     * 0 = single-shot mode. Canonical storage lives in
     * {@code registry.burstRemaining[idx]}.
     */
    public final int getBurstRemaining() {
        return registry.getBurstRemaining(idx());
    }

    public final void setBurstRemaining(int v) {
        registry.setBurstRemaining(idx(), v);
    }

    /**
     * Sim-seconds until the next queued burst round fires. Decremented in
     * {@code InfantryWeapons.tick}.
     */
    public final float getBurstTimer() {
        return registry.getBurstTimer(idx());
    }

    public final void setBurstTimer(float v) {
        registry.setBurstTimer(idx(), v);
    }

    /**
     * Entity id of the target captured when the burst was queued. Burst rounds
     * keep firing at this entity even if {@link #getTargetId()} drifts to someone
     * else, so a burst doesn't smear across multiple enemies. {@code 0L} when
     * idle (no burst active). Cleared along with {@link #getBurstRemaining}
     * when the burst ends or the target is released from the registry. Resolve
     * through {@link BattleSimulation#resolveUnit(long)}; writes go through
     * {@link #setBurstTarget(Unit)} (or {@link #beginBurst(Unit)}, which
     * sets it as part of the trigger).
     */
    public final long getBurstTargetId() {
        return registry.getBurstTargetId(idx());
    }

    public final void setBurstTargetId(long v) {
        registry.setBurstTargetId(idx(), v);
    }

    /** Sets {@link #getBurstTargetId()} from a {@link Unit} ref (null → 0L). */
    public void setBurstTarget(Unit t) {
        setBurstTargetId((t == null) ? 0L : t.entityId);
    }

    /**
     * Queue the burst follow-up rounds after the AI has already fired round 1.
     * No-op for single-shot weapons or units without a {@link #primaryWeapon}
     * profile (militia / aliens / turrets — those use their own burst paths or
     * are intrinsically single-shot). Centralizes the trigger pattern so every
     * fireShot callsite — stanced, moving, opportunity, garrison — gets bursts
     * consistently. Without this, the moving-stance callsites tap once while
     * stanced callsites rip a full burst, which reads as a bug after
     * PULSE_RIFLE became a 3-round BR.
     */
    public void beginBurst(Unit target) {
        if (primaryWeapon == null || primaryWeapon.burstCount <= 1) return;
        int idx = idx();
        registry.setBurstRemaining(idx, primaryWeapon.burstCount - 1);
        registry.setBurstTimer(idx, primaryWeapon.burstSpacing);
        registry.setBurstTargetId(idx, (target == null) ? 0L : target.entityId);
    }

    /** Random prone-pose index rolled on death. Drives which corpse frame the renderer picks from {@link UnitType#deadSpritePath} so a battlefield has pose variety rather than every body in the same slump. -1 sentinel = unit still alive. */
    public int deathPoseIdx = -1;

    /**
     * Mech chassis loadout. Non-null only on mech-class units ({@link UnitType#HEAVY_MECH}
     * today). When set, the unit fires three concurrent weapon tracks via the
     * mech-fire pass in {@code BattleSimulation} instead of the marine
     * primary/secondary path; the unit's base {@link #getAttackDamage() attackDamage} /
     * {@link #attackCooldown} are unused and {@link #getAttackRange() attackRange} only matters
     * for target acquisition (set wide on {@link UnitType#HEAVY_MECH} to match
     * the LRM's reach).
     */
    public MechLoadoutState mech;

    public Unit(String id, Faction faction, UnitType type, int cellX, int cellY) {
        this.id = id;
        this.faction = faction;
        this.type = type;
        this.seedCellX = cellX;
        this.seedCellY = cellY;
        this.localRenderX = cellX;
        this.localRenderY = cellY;
        this.moveSpeed = type.moveSpeed;
        // Pre-allocate seed; UnitRegistry.allocate will read these into the
        // SoA arrays. Use the field directly here because the registry-side
        // setters can't route yet (registry is null).
        this.seedHp = type.maxHp;
        this.seedMaxHp = type.maxHp;
        this.seedAttackDamage = type.attackDamage;
        this.seedAttackRange = type.attackRange;
        this.seedAccuracy = type.accuracy;
        this.visionRange = type.visionRange > 0f ? type.visionRange : type.attackRange;
        this.attackCooldown = type.attackCooldown;
    }

    /**
     * Liveness. Checks the {@code registry == null} release marker FIRST, then
     * the dense hp slot — so a held ref to a released (swap-and-popped) unit
     * reads as dead without ever touching the now-fail-loud {@link #getHp}. This
     * is what lets {@code getHp}/{@code setHp} drop the {@code localHp}
     * corpse-window shadow: nothing chains liveness through {@code getHp} on a
     * potentially-released unit anymore. (Also false in the pre-allocate window,
     * before {@link UnitRegistry#allocate} wires {@code registry} — a unit not
     * yet in the sim isn't alive in it.)
     */
    public boolean isAlive() {
        return registry != null && registry.isAliveById(entityId);
    }

    /**
     * Resolves this unit's current dense slot from {@link #entityId} via the
     * registry's id→index map — the single source of truth now that the cached
     * {@code denseIdx} field is gone. Fail-loud on an unregistered/released unit
     * (the OO accessors below are only valid on a live unit, exactly as before).
     * The probe is a fastutil primitive long→int lookup; <b>hot bulk loops never
     * come through here</b> — they iterate the dense columns by loop index
     * directly, so the cache-locality win is untouched. This routes only the
     * cold/per-event OO accessor callers (decision-cadence convenience setters,
     * the movement step, tests).
     */
    private int idx() {
        return registry.requireLiveIndex(entityId);
    }

    /**
     * Current HP — the registry's SoA hp slot. Fail-loud on an unregistered
     * unit (pre-allocate or post-release), exactly like {@link #getMaxHp}: every
     * direct reader is on a live, dense-iterated or {@code getOrNull}-resolved
     * unit. Pre-allocate seeding writes {@link #seedHp} directly.
     */
    // Final so CHA keeps the call monomorphic across all current Unit
    // subclasses (Drone, DroneHubUnit, MapTurret) — JIT inlines the
    // registry.getHp/setHp dispatch in one virtual call.
    public final float getHp() {
        return registry.getHp(idx());
    }

    public final void setHp(float v) {
        registry.setHp(idx(), v);
    }

    // Group-S seed-only stats (maxHp + the three attack stats): canonical
    // storage is the registry's SoA arrays, seeded once from the seed* fields
    // at allocate. Pre-allocate writers seed the seed* fields directly; all four
    // accessors read the registry unconditionally (fail-loud on an unregistered
    // unit) since nothing reads them post-release. (SquadDetailPanel used to be
    // a post-release maxHp reader — it now snapshots the row's hp/maxHp by value
    // at update() while the member is still live, so getMaxHp no longer needs the
    // seedMaxHp fallback that c33ba6c6 added.)
    public final float getMaxHp() {
        return registry.getMaxHp(idx());
    }

    public final void setMaxHp(float v) {
        registry.setMaxHp(idx(), v);
    }

    // Logical-cell accessors. Canonical storage is the registry's SoA arrays,
    // seeded once from seedCellX/seedCellY at allocate. Like the Group-N/S
    // columns these read/write the registry unconditionally (fail-loud on an
    // unregistered unit) — the seed* fields are the only pre-allocate channel,
    // and the post-release death cell now travels on the DeathEvent snapshot, so
    // no corpse reads cell off the released unit. Final for CHA monomorphism.
    public final int getCellX() {
        return registry.getCellX(idx());
    }

    public final int getCellY() {
        return registry.getCellY(idx());
    }

    /**
     * Set both cell coordinates in one call. Every callsite in the codebase
     * writes the pair together (movement step, drone-body sync), so the
     * paired setter matches the access pattern and lets the registry hit
     * both SoA slots without a second method dispatch.
     */
    public final void setCellPos(int x, int y) {
        registry.setCellPos(idx(), x, y);
    }

    public final float getCooldownTimer() {
        return registry.getCooldownTimer(idx());
    }

    public final void setCooldownTimer(float v) {
        registry.setCooldownTimer(idx(), v);
    }

    public final float getAttackDamage() {
        return registry.getAttackDamage(idx());
    }

    public final void setAttackDamage(float v) {
        registry.setAttackDamage(idx(), v);
    }

    public final float getAttackRange() {
        return registry.getAttackRange(idx());
    }

    public final void setAttackRange(float v) {
        registry.setAttackRange(idx(), v);
    }

    public final float getAccuracy() {
        return registry.getAccuracy(idx());
    }

    public final void setAccuracy(float v) {
        registry.setAccuracy(idx(), v);
    }

    public final float getMoveProgress() {
        return registry.getMoveProgress(idx());
    }

    public final void setMoveProgress(float v) {
        registry.setMoveProgress(idx(), v);
    }

    public final float getRenderX() {
        return (renderPositions != null) ? renderPositions.getX(entityId) : localRenderX;
    }

    public final float getRenderY() {
        return (renderPositions != null) ? renderPositions.getY(entityId) : localRenderY;
    }

    public final void setRenderX(float v) {
        if (renderPositions != null) renderPositions.setX(entityId, v);
        else localRenderX = v;
    }

    public final void setRenderY(float v) {
        if (renderPositions != null) renderPositions.setY(entityId, v);
        else localRenderY = v;
    }

    public final void setRenderPos(float x, float y) {
        if (renderPositions != null) {
            renderPositions.set(entityId, x, y);
        } else {
            localRenderX = x;
            localRenderY = y;
        }
    }
}
