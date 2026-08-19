# Expanded Combat Events

## Goal

Broaden the successful background-radio layer without increasing its speaking
rate: add more voice variety to existing calls and connect four supplied MW4
line families to concrete combat events.

## Scope

- Expand visual-contact, fallback, copy, and roger-that lines across their
  standard pilot-voice variants.
- Add `checkfire*` for a marine damaging another marine.
- Add `fools*` as an alternate sparse chirp during sustained combat.
- Add `fulllance*` when marine infantry first spots an enemy mech force.
- Add `killbrag*` when an enemy combatant is downed.
- Exclude campaign/commander-specific `_cas` and `_spe` variants and keep the
  ordinary pilot roster.
- Preserve the existing positional playback, presentation-only RNG, and one
  global voice budget across every cue.
- Feed identical event facts to standalone and hybrid battle presentation.

## Event and priority policy

1. Friendly-fire warning — urgent; a real same-faction damage event.
2. Morale fallback — urgent squad-state transition.
3. Enemy mech spotted — first hostile mech contact per battle.
4. Ordinary hostile contact — squad alert transition.
5. Enemy down — confirmed hostile death.
6. Sparse combat/acknowledgement — ambient only while marines remain engaged.

Higher-priority facts may replace a lower-priority cue waiting for the voice
budget. No cue interrupts audio already playing.

## Acceptance

- Friendly-fire warnings require actual marine-on-marine damage, not merely a
  near miss.
- Mech contact fires at most once per battle and only after a marine squad has
  current contact with a live defender mech.
- Enemy-down calls require a defender combatant death.
- `fools` and acknowledgement clips share the existing sparse ambient cadence.
- All new voices remain excluded from defenders, mechs, drones, and wiped
  marine squads as speakers.
- Audio/event decisions cannot change simulation determinism.
