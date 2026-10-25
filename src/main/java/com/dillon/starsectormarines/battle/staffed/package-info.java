/**
 * Category: battle feature domain.
 *
 * <p>Charter: staffed buildings ({@code staffed-buildings.md}). Which of a
 * populated map's ordinary buildings a civilian crew works, and the loading: a
 * crew carrying a building's stock out to a truck at its door while the company
 * guards them, the truck leaving the map once the count is aboard. Mission
 * setups decide what to staff and whether to run a loading; this package does
 * both on any map.
 *
 * <p>Boundary: the crew's loops, work points and yield are
 * {@code battle.ambient}'s; the truck and its driving are the convoy's. This
 * package chooses the buildings, parks the truck, hands the crew the loading
 * loop, counts what is set down and sends the truck off.
 */
package com.dillon.starsectormarines.battle.staffed;
