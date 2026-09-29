package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.PointFireAim;

/** Carrier-owned accepted throw point. Handback changes the caller, not this committed channel. */
public record SmokeThrowCommit(PointFireAim aim, boolean manual) { }
