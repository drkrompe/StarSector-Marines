# Salvo chains: hold a rack for a bigger spike

Status: IN PROGRESS — sim, AI rule, HUD and tests landed; the owner's read of the armorMatchup is open.

Written: 2026-10-23

Read `mechs-nouns.md` (Loadout, missile replenisher, flux) and `direct-control-nouns.md`
(Mouse buttons and weapon groups, the ammo strip) first.

## Model (owner, 2026-10-23)

- **Click:** one salvo, as before: a rack's trigger fires `projectilesPerTrigger` missiles at
  burst spacing out of a load of that many times its pack count (LRM-15 5 x 3, LRM-5 2 x 4,
  SRM-5 2 x 6, SRM-15 4 x 6).
- **Hold:** once the hold has fired a salvo, the next one follows as soon as the last has gone,
  without the rack's cycle, until the button is released or the rack is empty. The cycle then runs
  from the last salvo. A fresh press after release waits out the cycle. LRM-15: a click is 5, a
  short hold 10, a long hold 15.
- **Flux:** a chained salvo reserves half its trigger's flux (`SalvoChain.CHAINED_FLUX_SHARE`), so an
  LRM-15's whole load costs 80 of the stock 100. At the full share it could never empty inside the
  reservoir; flux, not ammunition, still bounds the deepest racks (an SRM-15's six salvos cost 147).
- **Reload:** unchanged. The replenisher restores one pack per cadence (SRM 12 s, LRM 18 s standard),
  so a spent spike comes back a salvo at a time and stays slower than a belt.
- **Vehicles:** the same hold rule on a turret launcher under manual fire (the LRM carrier's 60
  rounds in salvos of 10, the SRM pod's 32 in 4). A vehicle magazine is never replenished.
- **AI:** a fresh autonomous trigger chooses its count (`SalvoChain.autonomousSalvos`): one at a soft
  target, the whole load at a mech or at a target its own side's point defence covers, half the load
  (rounded up) at other armor. A vehicle crew chains at most three salvos. A chained salvo goes only
  to the body its chain was committed against.
- **HUD:** missiles (or folded salvo icons) the current hold has fired draw lit (`held` icons) next
  to the live ones, so 5 -> 10 -> 15 is seen building. The reticle tick keeps its colours.

`-Dbattle.salvoChain=false` is the control (`switches.md`).

## Acceptance

- Unit tests: hold chaining, release, cap at the load, per-salvo reload, the AI rule, the lit strip.
- `armorMatchup` with and without the switch, read by the owner (a reading, not acceptance).
- A 1920x1080 direct-control frame mid-hold on an LRM-15.

## Open

- Owner read of the matchup; then drop the switch and the one-salvo arm, fold this into the noun
  docs' standing text (already written there) and delete this story.
