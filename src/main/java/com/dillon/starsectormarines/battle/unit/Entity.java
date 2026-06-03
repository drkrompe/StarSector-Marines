package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.drone.Drone;
import com.dillon.starsectormarines.battle.infantry.EquipmentDrop;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.command.objective.Objective;

import java.util.Random;

/**
 * One combatant in the battle simulation. Plain data — all behavior lives on
 * {@link BattleSimulation}. Fields are public for hot-path access from the
 * tick loop; the package keeps the surface narrow.
 *
 * <p><b>No registry back-pointer.</b> A {@code Entity} carries its {@link #entityId}
 * (its identity) plus immutable archetype + a handful of POJO fields, but it no
 * longer self-routes into the simulation's mutable state. Every mutable per-unit
 * column (hp, cell, the combat timers, target/burst/fallback ids) lives in the
 * {@link UnitRegistry}'s dense SoA and is reached <em>by id</em> through the
 * {@code World} facade ({@code world.hp(id)}, {@code world.cellX(id)}, …) or the
 * registry's by-index API — never through this object. This is the access shape
 * the {@code world-facade} endgame settled on; the next step renames {@code Entity}
 * → {@code Entity} to match.
 *
 * <p>Position is split: the logical cell (what pathfinding sees) is a registry
 * SoA column reached by id ({@code world.cellX(id)} / {@code world.cellY(id)}),
 * while {@link #getRenderX}/{@link #getRenderY} are the smooth-interpolated
 * position inside the cell grid (in cell units, fractional) that the renderer
 * reads — kept on {@code Entity} because they route through the
 * {@link RenderPositionService}, which survives release so a corpse still draws
 * where it fell. The two coincide when the unit is at rest or has just landed in
 * a new cell.
 *
 * <p>{@link #path} + {@link #pathIdx} + the move-progress registry column
 * describe the current movement step. {@link #advanceAlongPath(UnitRegistry, float)}
 * rebuilds nothing but lerps render position toward {@code path[pathIdx]} as
 * move-progress climbs from 0 to 1; on arrival the logical cell advances and
 * progress resets.
 */
public class Entity {

    /** Sentinel value for {@link #squadId} when the unit isn't part of a squad — defenders, solo combatants, anyone not deboarded from a marine shuttle. */
    public static final int NO_SQUAD = -1;

    /**
     * Monotonic entity id assigned by {@link com.dillon.starsectormarines.battle.unit.UnitRegistry}
     * on registration. {@code 0} means "not yet allocated" (matches the
     * registry's reserved sentinel); a non-zero value is stable for the
     * life of the unit and never recycled. This is the unit's identity — all
     * mutable state is keyed off it in the registry / component stores, reached
     * by id rather than through this object.
     */
    public long entityId = 0L;

    /**
     * Null-safe entity id of a {@link Entity} ref: {@code u.entityId}, or
     * {@code 0L} (the "no entity" sentinel) when {@code u == null}. The single
     * chokepoint for the "ref → id, null → 0L" convention every cross-reference
     * setter used to hide inside its own convenience overload — now that those
     * overloads are gone, callers write {@code world.setTargetId(self.entityId,
     * Entity.idOf(target))}. Survives the eventual {@code Entity} → {@code Entity}
     * rename unchanged.
     */
    public static long idOf(Entity u) {
        return (u == null) ? 0L : u.entityId;
    }

    /**
     * The decomposed render-position service this unit's render coordinates
     * live in, keyed by {@link #entityId}. Set once by
     * {@link UnitRegistry#allocate} and — unlike the registry's dense slot —
     * <b>not</b> dropped on release, so {@link #getRenderX()} / {@link #getRenderY()}
     * keep resolving the death-pose location for a released corpse. {@code null}
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
     * {@code release} does NOT snapshot them back. Once allocated, the cell lives
     * in the registry SoA, reached by id ({@code world.cellX(id)} /
     * {@code world.cellY(id)}). Public for the same sibling-package seeding reason
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

    /**
     * Per-tick movement step. Resolves this unit's dense slot once off the passed
     * registry (single probe) and drives the cell + move-progress columns by
     * index. Takes the registry rather than {@link World} because the step touches
     * up to five columns per moving unit per tick — routing each through
     * {@code world.cellX(id)} etc. would re-probe the id→index map five times
     * where one suffices.
     */
    public void advanceAlongPath(UnitRegistry registry, float dt) {
        if (pathIdx >= pathCellCount()) return;
        int idx = registry.requireLiveIndex(entityId);
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
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> Same shape as
     * {@link #seedMaxHp} and the cell pair: {@link UnitRegistry#allocate} copies
     * this into the SoA hp array and the registry is canonical from then on;
     * {@code release} does NOT snapshot it back. Once allocated, hp lives in the
     * registry SoA, reached by id ({@code world.hp(id)} / {@code world.setHp(id, v)});
     * held-ref liveness goes through {@code world.isAlive(id)} /
     * {@code registry.isAliveById(id)}, which report a released id as dead without
     * a corpse-window hp shadow.
     *
     * <p>Public so {@link UnitRegistry} (a sibling package) can seed the slot at
     * allocate time. Write-only construction input: the ctor archetype seed and
     * the subclass overrides (see {@code seed*}).
     */
    public float seedHp;
    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> The Group-S stat
     * columns (max HP + the three attack stats): {@link UnitRegistry#allocate}
     * copies these into the SoA arrays and the registry is canonical from then
     * on, and {@code release} does NOT snapshot them back. Reached post-allocate
     * by id through the registry/World ({@code world.maxHp(id)},
     * {@code world.attackDamage(id)}, …) since nothing reads them post-release —
     * the HUD that once read max HP post-release now snapshots the row by value at
     * {@code update()} (see {@code SquadDetailPanel}). These fields are write-only
     * <em>construction</em> input: the ctor archetype seed, the subclass overrides
     * ({@link com.dillon.starsectormarines.battle.drone.Drone} /
     * {@link com.dillon.starsectormarines.battle.drone.DroneHubUnit} /
     * {@link com.dillon.starsectormarines.battle.turret.MapTurret}), and the
     * shuttle/vehicle deboard loadout.
     */
    public float seedMaxHp;
    public float seedAttackDamage;
    public float seedAttackRange;
    public float seedAccuracy;
    /** How far this unit can see (cells). Drives fog-of-war shadowcast radius. Initialized from {@link UnitType#visionRange}; 0 falls back to the unit's attack-range stat. */
    public float visionRange;
    public float attackCooldown;

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

    /** Primary handheld weapon. Null for legacy / non-marine units — fire stats fall back to the {@link UnitType} attack-stat defaults. Assigned at deboard time for marines. */
    public MarineWeapon primaryWeapon;
    /** Optional secondary slot (rocket launcher, future grenades). Null = no secondary. */
    public MarineSecondary secondaryWeapon;
    /** Rounds remaining on the {@link #secondaryWeapon}. Decremented on each secondary shot; once zero the marine reverts to primary fire. */
    public int secondaryAmmo;
    /** Latched on launch within the current aim cycle so we only emit one shot per cycle, even though the trigger condition holds for several ticks past launch. */
    public boolean secondaryFiredThisAction = false;

    /**
     * Queue the burst follow-up rounds after the AI has already fired round 1.
     * No-op for single-shot weapons or units without a {@link #primaryWeapon}
     * profile (militia / aliens / turrets — those use their own burst paths or
     * are intrinsically single-shot). Centralizes the trigger pattern so every
     * fireShot callsite — stanced, moving, opportunity, garrison — gets bursts
     * consistently. Reads this unit's own immutable {@link #primaryWeapon}
     * profile, then writes the three burst columns by id through {@code world}.
     * Called at most once per shot per unit (not a per-tick bulk path), so the
     * three by-id probes are fine.
     */
    public void beginBurst(World world, Entity target) {
        if (primaryWeapon == null || primaryWeapon.burstCount <= 1) return;
        world.setBurstRemaining(entityId, primaryWeapon.burstCount - 1);
        world.setBurstTimer(entityId, primaryWeapon.burstSpacing);
        world.setBurstTargetId(entityId, Entity.idOf(target));
    }

    /** Random prone-pose index rolled on death. Drives which corpse frame the renderer picks from {@link UnitType#deadSpritePath} so a battlefield has pose variety rather than every body in the same slump. -1 sentinel = unit still alive. */
    public int deathPoseIdx = -1;

    public Entity(String id, Faction faction, UnitType type, int cellX, int cellY) {
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
        // setters can't route yet (the unit isn't registered).
        this.seedHp = type.maxHp;
        this.seedMaxHp = type.maxHp;
        this.seedAttackDamage = type.attackDamage;
        this.seedAttackRange = type.attackRange;
        this.seedAccuracy = type.accuracy;
        this.visionRange = type.visionRange > 0f ? type.visionRange : type.attackRange;
        this.attackCooldown = type.attackCooldown;
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
