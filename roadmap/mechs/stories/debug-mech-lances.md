# DEBUG mech lances

**Status:** In progress.

## Goal

Make the DEBUG player-side drop power feel like a formation commitment rather
than four repetitions of a single-unit call-in.

## Slice

- One Mech Support activation delivers up to four configured chassis.
- All chassis from that activation share one marine-side mech squad.
- The configured total remains a mech count; it is partitioned into four-mech
  lances, with a partial final lance when necessary.
- The picker defaults to four, accepts up to 100 mechs, and adds +/-10 controls
  so stress rosters are practical to configure.
- Production's ordinary one-Bulwark Mech Support remains unchanged.

## Acceptance

1. Four configured mechs produce one charge, one Valkyrie, and one four-mech
   squad.
2. Five configured mechs produce two charges: a four-mech lance and a one-mech
   final drop.
3. Variant order follows the briefing's stable randomized roster across lance
   boundaries.
4. The debug picker clamps safely at 0 and 100.
5. Focused tests and the full Gradle build pass.

## Boundary

This does not establish the eventual campaign definition of a lance, its lift
requirements, or ownership/recovery. It is deliberately DEBUG battle-iteration
scaffolding.
