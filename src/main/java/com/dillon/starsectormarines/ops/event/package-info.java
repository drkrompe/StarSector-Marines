/**
 * <b>Category:</b> presentation.
 *
 * <p><b>Charter:</b> push campaign decisions at the player. Everything here exists to
 * turn a {@code PlayerEventNotice} — a pure projection over persisted contract state —
 * into an unprompted modal card with our own chrome, and to route the player's answer
 * back into shipped domain operations.
 *
 * <p><b>Boundary:</b> no policy lives here. Writing an assignment off calls
 * {@code StationingLapseResolution}; deploying calls {@code StationingResponseLaunch},
 * the same seam the local Manage → Respond button uses. If a class in this package
 * starts mutating contract or personnel state directly, it is in the wrong package.
 *
 * <p><b>Pointer:</b> {@code contracts-live-acceptance.md}.
 */
package com.dillon.starsectormarines.ops.event;
