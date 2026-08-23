# Vanilla Combat HUD and Time Policy

Status: PLANNED

Written: 2026-08-23

Read `vanilla-combat-bridge-nouns.md` before implementing this story.

## Current substrate

The spectator host starves deployment/command widgets and calls the supported
one-frame ship-info hide API. Combat offers neither a master HUD-off switch nor
an above-widget draw hook; the kill feed has no supported suppression lever.

## Goal

Settle the player-facing pause and time-flow policy after validating the
current ship-info suppression in a live bridge battle.

## Decisions

- Decide whether vanilla pause/speed controls remain available with their
  indicators or the host pins time and supplies its own controls.
- Verify whether the switch-ship prompt survives the ship-info hide call before
  adding per-ship control/selectability suppression.
- Accept or bound the vanilla kill feed; do not use reflection or undocumented
  UI internals.

## Acceptance

The chosen policy is explicit, uses supported APIs, restores host state on
exit, and leaves no accidental player-ship control path. Any unavoidable chrome
is documented as a host constraint rather than hidden by fragile rendering
hacks.
