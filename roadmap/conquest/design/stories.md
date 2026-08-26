# Conquest stories

Status: ACTIVE — paired Conquest command is implemented; live command-duel and reinforcement acceptance remain.

Written: 2026-08-23

Updated: 2026-08-26 — added defender-commanded convoy deployment and handoff.

The territorial model, compound loop, deliberate capture allocation, paired
track behaviors, and Conquest victory law are implemented. Remaining foundation
migration and live validation cover the full attacker/defender command duel
plus reinforcement behavior; reinforcement mechanics remain owned by the
Reinforcement feature:

| Story | State | Intent |
|---|---|---|
| `front-command-and-keep-convergence.md` | IN PROGRESS | Replace exclusive strips with inspectable tracks, cross-track support, and a culminating keep assault. |
| `defender-track-mobilization.md` | IN PROGRESS | Turn faction-honest first contact into bounded patrol mobilization along the shared Conquest tracks. |
| `defender-convoy-deployment-and-handoff.md` | IN PROGRESS | Give Conquest convoys commander-authored rear-edge deployment bands and transfer their delivered squads into the defender command plan. |
| `progressive-reinforcement.md` | PARKED | Manually verify defender frontline response, safe delivery, and supply degradation across a Conquest push. |
| `biome-counterattack.md` | PARKED | Manually tune and verify the telegraphed defender counterattack as a territorial swing. |

Do not create duplicate Conquest stories for those systems. Their standing
contracts live in `reinforcement-nouns.md`; this feature supplies the
territorial context in `conquest-nouns.md`.
