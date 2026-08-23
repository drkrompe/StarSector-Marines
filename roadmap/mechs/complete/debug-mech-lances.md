# DEBUG mech lances

**Status:** Shipped in `6764caf1`.

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

## Shipped details

`6764caf1` partitions the immutable randomized roster into ordered groups of
four. Each activation stamps that group onto its `ShuttleMission`, deboards the
members through the existing physical payload loop, and keeps all four in the
same commander-visible squad with the first chassis as leader. The last charge
can carry one to three mechs. The DEBUG picker now defaults to four, clamps at
100, adds +/-10 controls, and summarizes both lance count and chassis makeup.
Production's ordinary `MechSupport` constructor still supplies one Bulwark.
Focused tests exercise 4+1 partitioning and a real four-mech unload; the full
Gradle build passed.
