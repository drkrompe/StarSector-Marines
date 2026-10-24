package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.fort.FortTag;
import com.dillon.starsectormarines.battle.setup.BattleSetup;


import java.util.ArrayList;
import java.util.List;

/**
 * One placed manned turret emplacement — the side-effect of a single
 * {@link com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper}
 * stamp. Carries the data {@link com.dillon.starsectormarines.battle.setup.BattleSetup}
 * needs to (a) spawn the {@link MapTurret} units at the right cells with the
 * right kinds and (b) link the {@link com.dillon.starsectormarines.battle.decision.TacticalNode}
 * to its turret list so {@code GUARDPOST_PATROL} squads can detect when all
 * their turrets are dead and release into search-and-destroy.
 *
 * <p>Anchor is the post's geometric center — used by the tactical-node
 * emission and the squad patrol-radius reference point. Turret cells are the
 * actual {@link MapTurret} spawn positions (1 for LIGHT/MEDIUM, 2-3 for LARGE).
 *
 * <p>Immutable. Generators emit, {@link com.dillon.starsectormarines.battle.world.gen.MapResult}
 * carries, the battle setup consumes.
 */
public final class DefensePost {

    public final DefensePostKind tier;
    /** Authoritative data layout id; null only for legacy one-off tower stamps. */
    public final String layoutId;
    public final int anchorX;
    public final int anchorY;
    /** Turret cell positions + kinds. One entry for LIGHT/MEDIUM; 2-3 for LARGE. */
    public final List<TurretSpec> turrets;
    /** Optional occupied drone-hub platform cell authored by the layout. */
    public final Integer droneHubCellX;
    public final Integer droneHubCellY;
    /** The fort this post is part of and which part it is, or null for a post that belongs to no fort. */
    public final FortTag fort;
    /** How the post is built, which decides how its structures are drawn; {@link EmplacementStyle#BRUTALIST} unless a generator says otherwise. */
    public final EmplacementStyle style;

    public DefensePost(DefensePostKind tier, int anchorX, int anchorY, List<TurretSpec> turrets) {
        this(tier, null, anchorX, anchorY, turrets, null, null);
    }

    public DefensePost(DefensePostKind tier, String layoutId,
                       int anchorX, int anchorY, List<TurretSpec> turrets,
                       Integer droneHubCellX, Integer droneHubCellY) {
        this(tier, layoutId, anchorX, anchorY, turrets, droneHubCellX, droneHubCellY, null);
    }

    /** A post that is part of a fort: its structure is the fort's platform, one of its aspect cores, or a wall nest. */
    public DefensePost(DefensePostKind tier, int anchorX, int anchorY, List<TurretSpec> turrets, FortTag fort) {
        this(tier, null, anchorX, anchorY, turrets, null, null, fort);
    }

    private DefensePost(DefensePostKind tier, String layoutId,
                        int anchorX, int anchorY, List<TurretSpec> turrets,
                        Integer droneHubCellX, Integer droneHubCellY, FortTag fort) {
        this(tier, layoutId, anchorX, anchorY, turrets, droneHubCellX, droneHubCellY, fort, EmplacementStyle.BRUTALIST);
    }

    private DefensePost(DefensePostKind tier, String layoutId,
                        int anchorX, int anchorY, List<TurretSpec> turrets,
                        Integer droneHubCellX, Integer droneHubCellY, FortTag fort, EmplacementStyle style) {
        this.style = style;
        this.fort = fort;
        this.tier = tier;
        this.layoutId = layoutId;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.turrets = List.copyOf(turrets);
        this.droneHubCellX = droneHubCellX;
        this.droneHubCellY = droneHubCellY;
    }

    /**
     * This post built in {@code style}: each turret is the structure that style puts up for the one planned there
     * ({@link StructureDef#builtIn}), so a high-tech post's guns are lasers, and restyling it again starts from the plan.
     */
    public DefensePost withStyle(EmplacementStyle style) {
        if (style == this.style) return this;
        List<TurretSpec> built = new ArrayList<>(turrets.size());
        for (TurretSpec spec : turrets) built.add(spec.builtIn(style));
        return new DefensePost(tier, layoutId, anchorX, anchorY, built, droneHubCellX, droneHubCellY, fort, style);
    }

    /** Where to spawn one MapTurret + what kind. Coupled here so the generator picks the kind alongside the position rather than the setup re-rolling per cell. */
    public static final class TurretSpec {
        public final String structureId;
        public final int cellX;
        public final int cellY;
        /** The structure the generator planned here, before a style built it as another ({@link #structureId}). */
        public final String plannedId;

        public TurretSpec(String structureId, int cellX, int cellY) {
            this(structureId, cellX, cellY, structureId);
        }

        private TurretSpec(String structureId, int cellX, int cellY, String plannedId) {
            if (structureId == null || structureId.isBlank()) {
                throw new IllegalArgumentException("turret structure id may not be blank");
            }
            this.structureId = structureId;
            this.cellX = cellX;
            this.cellY = cellY;
            this.plannedId = plannedId;
        }

        /** This spec as a post built in {@code style} puts it up; a structure the catalog does not know is kept. */
        TurretSpec builtIn(EmplacementStyle style) {
            TurretCatalogRegistry catalog = TurretCatalogRegistry.installed();
            StructureDef planned = catalog != null ? catalog.getStructure(plannedId) : null;
            String built = planned != null ? planned.builtIn(style) : plannedId;
            return built.equals(structureId) ? this : new TurretSpec(built, cellX, cellY, plannedId);
        }

        public StructureDef structure() {
            return TurretCatalogRegistry.requireStructure(structureId);
        }
    }
}
