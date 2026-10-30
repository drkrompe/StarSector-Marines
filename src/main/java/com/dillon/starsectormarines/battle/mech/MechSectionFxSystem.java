package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.combat.fx.FxHash;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.engine.ecs.Query;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * What section loss looks like on a standing mech ({@code mechs-nouns.md}): a crippled
 * chassis smokes at its legs for as long as it stands, and a section that goes to zero bursts
 * into flame where it sat and then smoulders there. Emits into the effects service's per-frame
 * smoke and fire queues, which the live screen and the headless review feed both drain, so the
 * two draw the same thing.
 *
 * <p>Presentation only. It reads the sections and never writes the simulation, and it draws
 * nothing from the simulation's random stream: cadence and jitter are hashed from the mech's id
 * and the tick, so a frame is still a pure function of simulation state and adding this changes
 * no battle. The one thing it keeps is which sections it has already burst, so a loss bursts
 * once.
 */
public final class MechSectionFxSystem {

    /** Ticks between leg-smoke puffs on a crippled mech (about half a second at 30 Hz). */
    static final int LEG_SMOKE_PERIOD_TICKS = 16;
    /** Ticks between smoulder puffs at a lost section's stump. */
    static final int STUMP_SMOKE_PERIOD_TICKS = 28;

    private static final int SALT_LEGS = 0x51;
    private static final int SALT_STUMP = 0x73;
    private static final int SALT_BURST = 0x2b;

    private final EntityWorld world;
    private final BattleComponents components;
    private final EffectsService effects;
    private final Query mechs;
    /** Sections already burst, as a bit per {@link MechSection} ordinal, by mech id. */
    private final Long2IntOpenHashMap burst = new Long2IntOpenHashMap();

    public MechSectionFxSystem(EntityWorld world, BattleComponents components, EffectsService effects) {
        this.world = world;
        this.components = components;
        this.effects = effects;
        this.mechs = world.query(new ComponentType[]{components.MECH_LOADOUT,
                components.POSITION, components.HEALTH}, null);
    }

    public void tick(int simTick) {
        for (ArchetypeTable table : world.matched(mechs)) {
            Object[] loadouts = table.objects(components.MECH_LOADOUT,
                    BattleComponents.MECH_LOADOUT_STATE).array();
            float[] posX = table.floats(components.POSITION, BattleComponents.POSITION_X).array();
            float[] posY = table.floats(components.POSITION, BattleComponents.POSITION_Y).array();
            float[] hp = table.floats(components.HEALTH, BattleComponents.HEALTH_HP).array();
            for (int row = 0, n = table.rowCount(); row < n; row++) {
                if (hp[row] <= 0f) continue;
                MechLoadoutComponent loadout = (MechLoadoutComponent) loadouts[row];
                MechSections sections = loadout.sections();
                if (sections == null) continue;
                long id = table.entityAt(row);
                emit(id, simTick, posX[row], posY[row], loadout, sections);
            }
        }
    }

    private void emit(long id, int tick, float x, float y,
                      MechLoadoutComponent loadout, MechSections sections) {
        float hull = LayeredMechAppearance.hullWidthCells(loadout.variant.renderScale);
        float torso = loadout.torsoFacingDegrees;
        int seen = burst.get(id);
        for (MechSection section : MechSection.VALUES) {
            if (section == MechSection.TORSO || !sections.destroyed(section)) continue;
            int bit = 1 << section.ordinal();
            float ax = x + anchorX(section, torso, hull);
            float ay = y + anchorY(section, torso, hull);
            if ((seen & bit) == 0) {
                seen |= bit;
                // The moment it goes: a short flare and a puff off the section.
                effects.emitFireBurst(ax, ay, 0.55f * hull);
                effects.emitFireBurst(ax + 0.15f * hull * FxHash.jitter(id, tick, SALT_BURST),
                        ay + 0.15f * hull * FxHash.jitter(id, tick, SALT_BURST + 1), 0.4f * hull);
                effects.emitSmokePuff(ax, ay, 0.5f * hull);
            } else if (section != MechSection.LEGS
                    && FxHash.onCadence(id, tick, STUMP_SMOKE_PERIOD_TICKS, SALT_STUMP + section.ordinal())) {
                // The stump smoulders thinly; the legs have their own, heavier smoke below.
                effects.emitSmokePuff(ax + 0.08f * hull * FxHash.jitter(id, tick, SALT_STUMP),
                        ay + 0.08f * hull * FxHash.jitter(id, tick, SALT_STUMP + 1),
                        (0.22f + 0.06f * FxHash.jitter(id, tick, SALT_STUMP + 2)) * hull);
            }
        }
        if (seen != 0) burst.put(id, seen);
        if (sections.crippled() && FxHash.onCadence(id, tick, LEG_SMOKE_PERIOD_TICKS, SALT_LEGS)) {
            effects.emitSmokePuff(x + 0.3f * hull * FxHash.jitter(id, tick, SALT_LEGS),
                    y + 0.3f * hull * FxHash.jitter(id, tick, SALT_LEGS + 1),
                    (0.42f + 0.08f * FxHash.jitter(id, tick, SALT_LEGS + 2)) * hull);
        }
    }

    /** Where on the chassis a section sits, in cells from the body centre, in world x. */
    static float anchorX(MechSection section, float torsoFacingDegrees, float hullWidthCells) {
        float lx = localX(section) * hullWidthCells;
        float ly = localY(section) * hullWidthCells;
        double r = Math.toRadians(torsoFacingDegrees);
        return (float) (lx * Math.cos(r) - ly * Math.sin(r));
    }

    /** As {@link #anchorX}, world y; the same frame as {@link MechHardpointGeometry#muzzle}. */
    static float anchorY(MechSection section, float torsoFacingDegrees, float hullWidthCells) {
        float lx = localX(section) * hullWidthCells;
        float ly = localY(section) * hullWidthCells;
        double r = Math.toRadians(torsoFacingDegrees);
        return (float) (lx * Math.sin(r) + ly * Math.cos(r));
    }

    /** Hull widths across the torso: local -x is left. */
    private static float localX(MechSection section) {
        return switch (section) {
            case LEFT_SHOULDER -> -0.28f;
            case RIGHT_SHOULDER -> 0.28f;
            case LEFT_ARM -> MechMountSlot.LEFT_ARM.armLateral();
            case RIGHT_ARM -> MechMountSlot.RIGHT_ARM.armLateral();
            default -> 0f;
        };
    }

    /** Hull widths forward of the body centre: local +y is forward. */
    private static float localY(MechSection section) {
        return switch (section) {
            case ARMS -> 0.25f;
            case LEFT_ARM, RIGHT_ARM -> 0.15f;
            case HEAD -> 0.12f;
            default -> 0f;
        };
    }
}
