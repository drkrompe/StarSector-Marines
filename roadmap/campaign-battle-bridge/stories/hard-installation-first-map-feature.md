# First hard-installation map feature

Status: PLANNED — select one installation type before implementation.
Written: 2026-08-23

Read `campaign-battle-bridge-nouns.md` before changing this story.

## Problem

The profile's composite defense level can scale generic overwatch, but it
cannot tell map generation whether the target specifically has an orbital
station, ground batteries, or a planetary shield. Those campaign facts should
eventually become concrete terrain the player fights around, not another
numeric multiplier.

## First vertical

Choose one installation type with a clear ground-scale expression, then:

1. add the smallest stable, vanilla-decoupled profile signal that identifies
   it at the campaign boundary;
2. carry Neutral behavior unchanged when the installation is absent;
3. place one bounded map feature through the owning mapgen recipe; and
4. make its walkability, cover, sight, deployment, and connectivity effects
   true in the navigation model.

Contract the remaining installation types separately after the first vertical
establishes the vocabulary and placement seam.

## Acceptance

- A target with the selected installation can produce its map feature; a
  target without it cannot receive the feature from this consumer.
- No vanilla campaign type crosses into battle or generation code.
- The feature survives ordinary generation validation and cannot partition
  required paths or invalidate deployment sites.
- The feature's tactical purpose is readable from geometry, not only art or a
  label.
- Neutral and unrelated target profiles retain their prior seeded behavior.

## Out of scope

- Shipping all station, battery, and shield variants together.
- Replacing force balance, overwatch intensity, or defender roster authority.
- Economic district content or campaign outcome writeback.
