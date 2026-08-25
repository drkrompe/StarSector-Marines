# Defender track mobilization

Status: IN PROGRESS — implementation is complete; paired live command-duel acceptance remains.

Written: 2026-08-24

Updated: 2026-08-25 — aligned live acceptance with the autonomous mission-command foundation.

Read `conquest-nouns.md`, `ai-nouns.md`, and
`autonomous-mission-command-foundation.md` before accepting this story.

## Story

As a Conquest defender, I want first contact on the advancing front to raise a
coarse faction alert and mobilize nearby patrol squads toward the threatened
track, so the defense reacts as a force without granting every squad exact
knowledge of the enemy.

## Acceptance

- Conquest installs a defender mission commander using the same three physical
  tracks as the marine commander.
- Only the defender influence picture can trigger mobilization. An unseen
  marine never appears in defender command state or changes an order.
- A reported contact identifies a track and coarse forward band, not an enemy
  identity or exact reported cell. Mobilized squads receive only a defensive
  rally cell derived from that coarse picture.
- Initial PATROL squads are the mobile command pool. Born GARRISON squads keep
  their node assignments, and later reinforcement squads remain owned by the
  reinforcement and counterattack systems.
- Mobilization is bounded: threatened tracks receive support before any track
  receives a second squad, and at least one mobile squad remains in reserve
  when the starting force is large enough to permit it.
- Home-track squads are preferred; an adjacent-track patrol may reinforce when
  necessary. Orders remain stable while the threat report is live and clear
  when faction knowledge expires, returning the squad to routine patrol.
- A squad's own direct or source-linked contact supersedes the strategic rally
  and feeds the ordinary engagement planner.
- Selected-squad diagnostics and the squad dump expose command perspective,
  phase, home/effective track, assignment reason, rally objective, and
  faction-honest track pressure without exposing hidden hostile positions.
- In a complete zero-input Conquest run, defender mobilization remains bounded
  while the marine command can progress the front; the two commands share
  physical track geometry but never perspective state or assignment authority.

## Boundaries

This story reallocates defenders already present at battle start. It does not
replace progressive reinforcement delivery, biome counterattacks, compound
recapture, or garrison last-stand behavior; those systems retain their existing
ownership and stories.
