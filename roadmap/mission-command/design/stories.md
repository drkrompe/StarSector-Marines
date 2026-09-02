# Mission-command stories

Status: ACTIVE — shared architecture and mission-specific migrations are tracked here; each mission keeps its own strategy and prerequisites.

Written: 2026-08-27

Read `mission-command-nouns.md`, then the relevant mission design before an
implementation story.

## Shared architecture

| Story | State | Intent |
|---|---|---|
| `autonomous-mission-command-foundation.md` | IN PROGRESS | Close assignment-writer ownership and live acceptance around the shared frame/plan/commit contract. |
| `commander-trace-and-balance-harness.md` | IN PROGRESS | Replace construction-only Conquest evidence with launch-faithful fixtures and later compare bounded intervention. |
| `player-command-interventions.md` | PLANNED | Tactical squad orders lease through the arbiter and hand back (shipped 2026-09-01). Add the strategic priority/rally/reserve/fallback vocabulary, refuse a lease over a higher authority, and bound pacing. |
| `target-faction-command-doctrine.md` | PLANNED | Bias established legal choices with frozen faction profiles after paired baselines exist. |

## Conquest

Design: `conquest-command.md`

| Story | State | Intent |
|---|---|---|
| `front-command-and-keep-convergence.md` | IN PROGRESS | Live-accept tracks, cross-track support, front staging, and culminating convergence. |
| `defender-track-mobilization.md` | IN PROGRESS | Live-accept belief-honest bounded patrol mobilization on the shared tracks. |
| `defender-convoy-deployment-and-handoff.md` | IN PROGRESS | Live-accept rear-edge deployment and commander ownership of convoy relief squads. |
| `lane-chain-tug-of-war.md` | PROPOSED | A lane's front is ownership along its chain of places, staging follows the route, defenders hold the next link and retake the last; tracks stay as the lateral fence. Specify against the lanes measurement first. |

## Sabotage

Design: `sabotage-command.md`. The paired migration is shipped; its retired
stories are recorded in `shipped.md`. Add another story only for a new bounded
capability or evidence gap.

## Assault

Design: `assault-command.md`

| Story | State | Intent |
|---|---|---|
| `assault-search-sector-picture.md` | IN PROGRESS | Finish live and canonical-duration acceptance of attacker search. |
| `assault-area-defense-command.md` | IN PROGRESS | Finish live and canonical-duration acceptance of defender area security. |
| `opening-operation-objective-command.md` | DRAFT | Adapt the shared architecture to finite preserve/secure opening scenarios. |

## Raid

Design: `raid-command.md`

| Story | State | Intent |
|---|---|---|
| `raid-objective-and-command-contract.md` | IN PROGRESS | Canonical-duration and live-accept the shipped primary-target objective, paired command duel, and diagnostics. |

## Extraction

Design: `extraction-command.md`

| Story | State | Intent |
|---|---|---|
| `generic-extraction-corridor-command.md` | IN PROGRESS | Ship and accept stable Marine payload/escort/screen command for generic Extraction. |
| `generic-extraction-interdiction-command.md` | IN PROGRESS | Ship and accept bounded source-alarm security and belief-driven defender interdiction. |
| `silent-colony-expedition-branches.md` | IN PROGRESS | Add headless/live acceptance for the stable Marine archive and survivor branches before choosing the opposing director shape. |
