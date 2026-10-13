package com.dillon.starsectormarines.battle.air;

/**
 * A hostile hull as a strike craft sees it: where its footprint is centred, how
 * far the footprint reaches, and the armour a round has to get through. It is a
 * snapshot, not a handle; a craft aimed at a hull is aimed at the point the
 * hull was at when the sortie was dispatched.
 *
 * @param x      centre of the footprint, in cells
 * @param y      centre of the footprint, in cells
 * @param radius half the hull's longest side, in cells
 * @param armour the hull table's armour rating
 */
public record HullTarget(float x, float y, float radius, float armour) {}
