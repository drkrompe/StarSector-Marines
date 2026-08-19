# Background Radio v1

## Goal

Add a quiet military-radio layer to ground battles using a small reviewed slice
of the supplied MW4 voice archive.

## Scope

- Register compact contact, fallback, and acknowledgement sound pools.
- Convert only the selected mono source clips into shipping Ogg assets.
- Detect marine-infantry squad contact and morale-break transitions in a pure,
  presentation-side controller.
- Emit occasional acknowledgement chatter only while at least one marine
  infantry squad is actively engaged.
- Enforce one global voice at a time through an initial delay and cooldown.
- Play each cue positionally at its source squad in both standalone and hybrid
  battles.
- Unit-test filtering, transition detection, priority, cooldown, and reset.

## Out of scope

- Defender/mech radio pools.
- Text subtitles or transcript attribution.
- Objective, reinforcement, casualty, command-power, or extraction cues.
- User-facing radio volume settings beyond the existing game mix controls.

## Acceptance

- A fresh contact can produce one quiet contact cue.
- A squad newly breaking morale queues a fallback cue with higher priority.
- Sustained engagements can produce sparse acknowledgement chatter.
- Pausing does not advance the chatter clock.
- No radio decision changes simulation RNG or state.
- Standalone and hybrid battle hosts use the same cue policy.
