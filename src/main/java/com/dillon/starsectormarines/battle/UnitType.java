package com.dillon.starsectormarines.battle;

/**
 * Tier / archetype of a {@link Unit}. Bundles its sprite sheet, whether it's a
 * combatant, and the base stat block applied at construction. {@link Faction}
 * stays orthogonal — a MARINE_RED unit can spawn as MARINE or DEFENDER faction
 * depending on which side hired the pirates this mission.
 *
 * <p>All sheets follow the 7-frame convention: 0=W idle, 1=N idle, 2=E idle,
 * 3=S idle, 4=W weapon-up, 5=E weapon-up, 6=N weapon-up (S reuses 6 + vertical
 * flip). Non-combatant sheets supply matching frames but their weapon-up poses
 * are interaction poses (clipboard, coffee) since these units never fire.
 */
public enum UnitType {
    /** Player faction's elite. Higher accuracy and HP than militia, expensive in lore. */
    MARINE     ("graphics/battle/marine.png",      "graphics/battle/marine-dead.png",      true,  25f, 2.0f, 2.0f, 0.35f, 1.0f, 24.0f),
    /** Generic regular soldier of an aligned faction (e.g., friendly sector navy on a joint op). Stat-equal to MARINE; sprite differs. */
    MARINE_BLUE("graphics/battle/blue-marine.png", "graphics/battle/blue-marine-dead.png", true,  25f, 2.0f, 2.0f, 0.35f, 1.0f, 24.0f),
    /** Pirate / opposed-faction regular. Stat-equal to MARINE; sprite differs. Often on the DEFENDER side, occasionally allied. */
    MARINE_RED ("graphics/battle/red-marine.png",  "graphics/battle/red-marine-dead.png",  true,  25f, 2.0f, 2.0f, 0.35f, 1.0f, 24.0f),
    /** Lightly armed local conscript. Less accurate, less HP, slower cooldown, shorter sight lanes than a regular. The bread-and-butter defender. */
    MILITIA    ("graphics/battle/militia.png",     "graphics/battle/militia-dead.png",     true,  15f, 1.5f, 2.0f, 0.22f, 1.2f, 18.0f),
    /** Hostile fauna / xeno boarder. Aggressive brawler — high HP, high damage, slightly faster, mid accuracy. */
    ALIEN      ("graphics/battle/alien.png",       "graphics/battle/alien-dead.png",       true,  30f, 3.0f, 2.2f, 0.32f, 1.1f, 22.0f),
    /** Random urban resident. Wanders the map and flees gunfire. Non-combatant; combat stats are unused but kept zero-safe. No corpse — civilian death just removes them from the map. */
    CIVILIAN   ("graphics/battle/civilian.png",    null,                                   false,  8f, 0f,   2.4f, 0f,    1f,   0f),
    /** Maintenance / industrial worker. Same role as civilian — wanders, flees. */
    ENGINEER   ("graphics/battle/engineer.png",    null,                                   false, 10f, 0f,   2.2f, 0f,    1f,   0f),
    /** Lab tech. Same role as civilian — wanders, flees. Lower HP than engineer; same speed. */
    SCIENTIST  ("graphics/battle/scientist.png",   null,                                   false,  8f, 0f,   2.2f, 0f,    1f,   0f),
    /** Static ground turret. Combatant so it targets and gets targeted, but its sprite + stats come from {@link MapTurret#kind} at construction — the values here are zero placeholders that {@link MapTurret} overwrites. */
    TURRET     ("",                                null,                                   true,   0f, 0f,   0f,   0f,    1f,   0f);

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

    UnitType(String spritePath, String deadSpritePath, boolean combatant,
             float maxHp, float attackDamage, float moveSpeed,
             float accuracy, float attackCooldown, float attackRange) {
        this.spritePath = spritePath;
        this.deadSpritePath = deadSpritePath;
        this.combatant = combatant;
        this.maxHp = maxHp;
        this.attackDamage = attackDamage;
        this.moveSpeed = moveSpeed;
        this.accuracy = accuracy;
        this.attackCooldown = attackCooldown;
        this.attackRange = attackRange;
    }
}
