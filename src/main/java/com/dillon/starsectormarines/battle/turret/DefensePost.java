package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.sim.BattleSetup;

import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;

import java.util.List;

/**
 * One placed manned turret emplacement — the side-effect of a single
 * {@link com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper}
 * stamp. Carries the data {@link com.dillon.starsectormarines.battle.sim.BattleSetup}
 * needs to (a) spawn the {@link MapTurret} units at the right cells with the
 * right kinds and (b) link the {@link com.dillon.starsectormarines.battle.tactical.TacticalNode}
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
    public final int anchorX;
    public final int anchorY;
    /** Turret cell positions + kinds. One entry for LIGHT/MEDIUM; 2-3 for LARGE. */
    public final List<TurretSpec> turrets;

    public DefensePost(DefensePostKind tier, int anchorX, int anchorY, List<TurretSpec> turrets) {
        this.tier = tier;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.turrets = turrets;
    }

    /** Where to spawn one MapTurret + what kind. Coupled here so the generator picks the kind alongside the position rather than the setup re-rolling per cell. */
    public static final class TurretSpec {
        public final TurretKind kind;
        public final int cellX;
        public final int cellY;

        public TurretSpec(TurretKind kind, int cellX, int cellY) {
            this.kind = kind;
            this.cellX = cellX;
            this.cellY = cellY;
        }
    }
}
