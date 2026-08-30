/**
 * Category: feature domain — authored room contents.
 *
 * <p>Charter: hold the artifact that lets a shipboard room be <em>edited</em>
 * rather than programmed. A {@link
 * com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayout} is a
 * footprint plus a recorded script of {@link
 * com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor} calls in the
 * room's canonical frame, and {@link
 * com.dillon.starsectormarines.battle.world.gen.fit.layout.AuthoredFitting}
 * replays it through the ordinary fitting interface.
 *
 * <p>The premise this package rests on: a shipboard room's footprint is
 * <b>fixed, and only its pose varies</b>. The packer takes a recipe's shape
 * verbatim and records how it was turned, and {@code RoomFloor.toLocal} already
 * carries a canonical arrangement into a turned room because that is how the
 * procedural fittings survive rotation. Authored cells are therefore compatible
 * with packing as it stands, and nothing here may touch the placer — if it ever
 * needs to, the premise has failed and the design is wrong.
 *
 * <p>Boundary: this package owns what a room contains, never where the room
 * goes or how large it is. Placement is {@code RoomPacker}'s and the footprint
 * is {@code RoomRecipe}'s. It also owns no drawing: a deck is seen through the
 * battle renderer (law 17), so an authored room is compared against a
 * procedural one by generating both and rendering them, never by painting a
 * picture of a room on its own.
 *
 * <p>Empty is the normal state. With nothing installed every room keeps the
 * procedural fitting it has always had.
 *
 * <p>Pointer: {@code roadmap/ship-interiors/stories/room-authoring.md} for the
 * story, {@code ship-interiors-nouns.md} for the standing model.
 */
package com.dillon.starsectormarines.battle.world.gen.fit.layout;
