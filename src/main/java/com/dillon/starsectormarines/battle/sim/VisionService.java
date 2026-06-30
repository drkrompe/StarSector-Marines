package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code VISION} component — typed by-id access (read + mutate)
 * to a unit's sight stats in the archetype {@link EntityWorld}: {@code visionRange}
 * (fog-of-war shadowcast radius, cells) and {@code airLosRadius} (close-wall "air"
 * line-of-sight radius, cells; {@code 0} = standard grid LoS).
 *
 * <p>A <b>Service</b> (data owner) in this codebase's sense — see
 * {@link CombatService}: consumers are constructor-injected with it (or reach it
 * via {@code sim.vision()} / {@code roster.vision()}) and call
 * {@code vision.airLosRadius(id)} directly, no {@link World} hop. This is the
 * random-access / held-ref path — {@code FogOfWarService}'s per-contributor
 * shadowcast and the decision/combat LoS checks ({@code TacticalScoring},
 * {@code TurretAim}, …) read these by id off this Service. The per-component
 * Service mirroring {@code CombatService}/{@code MovementService}; the slice-3
 * field migration ({@code roadmap/ecs-migration/stories/entity-field-migration.md})
 * lands VISION here from the start rather than on the {@link World} god-facade.
 *
 * <p>{@code VISION} is <em>universal</em> (every live unit carries it — a ground
 * unit's {@code airLosRadius} just seeds to 0) but <b>live-only</b>: the death
 * transmute removes it, so {@link #has} is the presence check and the field
 * accessors are <b>fail-loud</b> on a corpse (the COMBAT precedent). Live decision
 * code reads alive refs, so no presence gate is needed there. Serial-only.
 */
public final class VisionService {

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    public VisionService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** Presence check — true iff {@code id} carries VISION (is a live unit). Gate field reads on this where a corpse id might reach them. */
    public boolean has(long id) { return entityWorld.has(id, components.VISION); }

    /** How far this unit can see, in cells — its fog-of-war shadowcast radius. Mutable (the planned night multiplier scales it); seeded from {@code UnitType.visionRange}. */
    public float visionRange(long id) { return entityWorld.getFloat(id, components.VISION, BattleComponents.VISION_RANGE); }
    public void setVisionRange(long id, float v) { entityWorld.setFloat(id, components.VISION, BattleComponents.VISION_RANGE, v); }

    /** Close-wall "air" line-of-sight radius in cells; {@code 0} = standard grid LoS, &gt;0 for fliers that see/shoot over the walls they hover above. */
    public float airLosRadius(long id) { return entityWorld.getFloat(id, components.VISION, BattleComponents.VISION_AIR_LOS_RADIUS); }
    public void setAirLosRadius(long id, float v) { entityWorld.setFloat(id, components.VISION, BattleComponents.VISION_AIR_LOS_RADIUS, v); }
}
