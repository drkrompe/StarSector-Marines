# Conquest stories

Status: ACTIVE — front command, keep convergence, and defender mobilization are in implementation.

Written: 2026-08-23

The territorial model, compound loop, deliberate capture allocation, and
Conquest victory law are shipped. The remaining live validation concerns
reinforcement behavior exercised on Conquest maps and remains owned by the
Reinforcement feature:

| Story | State | Intent |
|---|---|---|
| `front-command-and-keep-convergence.md` | IN PROGRESS | Replace exclusive strips with inspectable tracks, cross-track support, and a culminating keep assault. |
| `defender-track-mobilization.md` | IN PROGRESS | Turn faction-honest first contact into bounded patrol mobilization along the shared Conquest tracks. |
| `progressive-reinforcement.md` | PARKED | Manually verify defender frontline response, safe delivery, and supply degradation across a Conquest push. |
| `biome-counterattack.md` | PARKED | Manually tune and verify the telegraphed defender counterattack as a territorial swing. |

Do not create duplicate Conquest stories for those systems. Their standing
contracts live in `reinforcement-nouns.md`; this feature supplies the
territorial context in `conquest-nouns.md`.
