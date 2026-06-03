package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.mech.MechLoadoutState;

import com.dillon.starsectormarines.battle.turret.MapTurret;

/**
 * Tier / archetype of a {@link Entity}. Bundles its sprite sheet, whether it's a
 * combatant, and the base stat block applied at construction. {@link Faction}
 * stays orthogonal — a MARINE_RED unit can spawn as MARINE or DEFENDER faction
 * depending on which side hired the pirates this mission.
 *
 * <p>Sheets use one of two layouts, selected by {@link #frameLayout}:
 * <ul>
 *   <li>{@link FrameLayout#WNES_WEAPON_UP} (default) — 7 frames: 0=W idle, 1=N,
 *       2=E, 3=S, 4=W weapon-up, 5=E weapon-up, 6=N weapon-up (S reuses 6 +
 *       vertical flip). Non-combatant sheets supply matching frames but their
 *       weapon-up poses are interaction poses (clipboard, coffee) since these
 *       units never fire.</li>
 *   <li>{@link FrameLayout#EIGHT_WAY_NO_WEAPON_UP} — 7 frames covering 7 of 8
 *       compass octants, no separate weapon-up pose. Used by the heavy mech
 *       sheet, whose chaingun arms are always extended.</li>
 * </ul>
 */
public enum UnitType {
    /** Player faction's elite. Higher accuracy and HP than militia, expensive in lore. */
    MARINE     ("graphics/battle/marine.png",      "graphics/battle/marine-dead.png",      true,  25f, 2.0f, 2.0f, 0.35f, 1.0f, 24.0f, 36.0f, FrameLayout.WNES_WEAPON_UP, 1.0f, 1.0f),
    /** Generic regular soldier of an aligned faction (e.g., friendly sector navy on a joint op). Stat-equal to MARINE; sprite differs. */
    MARINE_BLUE("graphics/battle/blue-marine.png", "graphics/battle/blue-marine-dead.png", true,  25f, 2.0f, 2.0f, 0.35f, 1.0f, 24.0f, 36.0f, FrameLayout.WNES_WEAPON_UP, 1.0f, 1.0f),
    /** Pirate / opposed-faction regular. Stat-equal to MARINE; sprite differs. Often on the DEFENDER side, occasionally allied. */
    MARINE_RED ("graphics/battle/red-marine.png",  "graphics/battle/red-marine-dead.png",  true,  25f, 2.0f, 2.0f, 0.35f, 1.0f, 24.0f, 36.0f, FrameLayout.WNES_WEAPON_UP, 1.0f, 1.0f),
    /** Lightly armed local conscript. Less accurate, less HP, slower cooldown, shorter sight lanes than a regular. The bread-and-butter defender. Low morale impact — militia are not that intimidating to marines, so a squad can take a lot of militia fire before breaking. */
    MILITIA    ("graphics/battle/militia.png",     "graphics/battle/militia-dead.png",     true,  15f, 1.5f, 2.0f, 0.22f, 1.2f, 18.0f, 28.0f, FrameLayout.WNES_WEAPON_UP, 1.0f, 0.4f),
    /** Hostile fauna / xeno boarder. Aggressive brawler — high HP, high damage, slightly faster, mid accuracy. Above-baseline morale impact (animal panic). */
    ALIEN      ("graphics/battle/alien.png",       "graphics/battle/alien-dead.png",       true,  30f, 3.0f, 2.2f, 0.32f, 1.1f, 22.0f, 34.0f, FrameLayout.WNES_WEAPON_UP, 1.0f, 1.2f),
    /** Walker mech with chaingun arms and rocket-pod shoulders. Defender elite that shows up when the target planet has industries producing or demanding heavy armaments (Heavy Industry, Orbital Works, Ground Defenses, Heavy Batteries). High HP, three-weapon chassis loadout ({@link MechLoadoutState}) fired concurrently. Dead sheet has 4 prone hulks; the sim also spawns a {@link com.dillon.starsectormarines.battle.combat.fx.SmokingWreck} on death so the corpse smolders. Renders ~1.6× cell so it visually dominates infantry. Base {@code attackRange} is set to the LRM range so target acquisition reaches across the grid; base {@code attackDamage}/{@code attackCooldown} are unused on mechs (weapons read from {@link MechLoadoutState} instead) but kept non-zero as a defensive fallback. High morale impact — eating fire from a walker mech rattles a squad fast. */
    HEAVY_MECH ("graphics/battle/heavy-mech.png",  "graphics/battle/heavy-mech-dead.png",  true,  90f, 4.0f, 1.4f, 0.40f, 0.6f, 40.0f, 55.0f, FrameLayout.EIGHT_WAY_NO_WEAPON_UP, 1.6f, 1.5f),
    /** Random urban resident. Wanders the map and flees gunfire. Non-combatant; combat stats are unused but kept zero-safe. No corpse — civilian death just removes them from the map. */
    CIVILIAN   ("graphics/battle/civilian.png",    null,                                   false,  8f, 0f,   2.4f, 0f,    1f,   0f,    12.0f, FrameLayout.WNES_WEAPON_UP, 1.0f, 1.0f),
    /** Maintenance / industrial worker. Same role as civilian — wanders, flees. */
    ENGINEER   ("graphics/battle/engineer.png",    null,                                   false, 10f, 0f,   2.2f, 0f,    1f,   0f,    12.0f, FrameLayout.WNES_WEAPON_UP, 1.0f, 1.0f),
    /** Lab tech. Same role as civilian — wanders, flees. Lower HP than engineer; same speed. */
    SCIENTIST  ("graphics/battle/scientist.png",   null,                                   false,  8f, 0f,   2.2f, 0f,    1f,   0f,    12.0f, FrameLayout.WNES_WEAPON_UP, 1.0f, 1.0f),
    /** Static ground turret. Combatant so it targets and gets targeted, but its sprite + stats come from {@link MapTurret#kind} at construction — the values here are zero placeholders that {@link MapTurret} overwrites. */
    TURRET     ("",                                null,                                   true,   0f, 0f,   0f,   0f,    1f,   0f,    0f,    FrameLayout.WNES_WEAPON_UP, 1.0f, 1.0f),
    /** Drone launch hub — a static structure that periodically deploys aerial drones (see {@link com.dillon.starsectormarines.battle.drone.DroneHubUnit}). Combatant so marines target and damage it, but its role is {@link UnitRole#STRUCTURE} (no aim loop, no firing). Sprite path is empty because the hub uses a per-instance vanilla weapon sprite picked at construction, same convention as {@link #TURRET}. HP set on the instance, not here. */
    DRONE_HUB_STRUCTURE ("",                       null,                                   true,   0f, 0f,   0f,   0f,    1f,   0f,    8.0f,  FrameLayout.WNES_WEAPON_UP, 1.0f, 1.0f),
    /** Autonomous defensive drone launched from a {@link com.dillon.starsectormarines.battle.drone.DroneHubUnit}. Combatant so marines target it, but the sprite path is empty because the drone uses a per-instance vanilla drone sprite (see {@link com.dillon.starsectormarines.battle.drone.Drone#SPRITE_PATH}), same convention as {@link #TURRET} / {@link #DRONE_HUB_STRUCTURE}. HP / speed are set on the instance, not here. */
    DRONE      ("",                                null,                                   true,   0f, 0f,   0f,   0f,    1f,   0f,    0f,    FrameLayout.WNES_WEAPON_UP, 1.0f, 1.0f);

    public final String spritePath;
    /** Optional corpse sheet — 4 prone poses, auto-sliced like the alive sheets. Drawn for {@code !isAlive()} units in their own pre-pass so live units draw on top. Null = no corpse rendering (units just vanish on death). */
    public final String deadSpritePath;
    public final boolean combatant;
    public final float maxHp;
    public final float attackDamage;
    public final float moveSpeed;
    public final float accuracy;
    public final float attackCooldown;
    public final float attackRange;
    /** How far this unit can see, in cells. Strictly &ge; {@link #attackRange} so a unit always spots enemies before they enter weapon range. Drives the fog-of-war shadowcast radius; 0 means the unit inherits {@code attackRange} as its vision range (used by turrets whose range is set per-instance). */
    public final float visionRange;
    /** Frame indexing convention of {@link #spritePath}. Drives the renderer's facing→frame mapping. */
    public final FrameLayout frameLayout;
    /** Multiplier on the renderer's per-cell sprite size. 1.0 = sprite height fills one cell; &gt;1 = unit visually overhangs adjacent cells (used by {@link #HEAVY_MECH} so the mech reads as bigger than infantry). */
    public final float renderScale;
    /** Scales the morale drain this unit's shots inflict on targets. 1.0 = baseline (a marine shooting a marine). MILITIA = 0.4 so a horde of conscripts doesn't insta-break a marine squad; HEAVY_MECH = 1.5 so mech fire rattles fast; ALIEN = 1.2. Applied to both hit drain (via {@code BattleSimulation.applyDamage}'s {@code moraleImpact} param) and near-miss drain (via {@code ShotEvent.moraleImpact}). */
    public final float moraleImpact;

    UnitType(String spritePath, String deadSpritePath, boolean combatant,
             float maxHp, float attackDamage, float moveSpeed,
             float accuracy, float attackCooldown, float attackRange,
             float visionRange,
             FrameLayout frameLayout, float renderScale, float moraleImpact) {
        this.spritePath = spritePath;
        this.deadSpritePath = deadSpritePath;
        this.combatant = combatant;
        this.maxHp = maxHp;
        this.attackDamage = attackDamage;
        this.moveSpeed = moveSpeed;
        this.accuracy = accuracy;
        this.attackCooldown = attackCooldown;
        this.attackRange = attackRange;
        this.visionRange = visionRange;
        this.frameLayout = frameLayout;
        this.renderScale = renderScale;
        this.moraleImpact = moraleImpact;
    }

    /**
     * Sprite-sheet frame indexing convention. Each layout names what indices 0..N
     * represent on its source PNG. The renderer's facing→frame lookup branches on
     * this; adding a new convention is "add an enum value + a switch arm."
     */
    public enum FrameLayout {
        /**
         * Standard marine convention used by every infantry sheet:
         * 0=W idle, 1=N idle, 2=E idle, 3=S idle, 4=W weapon-up, 5=E weapon-up,
         * 6=N weapon-up (S weapon-up reuses 6 + vertical flip). Seven frames total.
         */
        WNES_WEAPON_UP,
        /**
         * Heavy-mech convention — 8-way facing minus N (the mech sheet just doesn't
         * have one), no separate weapon-up pose: 0=W, 1=NW, 2=SE, 3=S, 4=SW,
         * 5=NE, 6=E. When the mech happens to face N, the renderer falls back to
         * the closest neighbor (NW or NE) rather than synthesizing a missing frame.
         */
        EIGHT_WAY_NO_WEAPON_UP
    }
}
