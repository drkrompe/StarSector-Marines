# Reliable first-contract discovery

Status: READY FOR LIVE ACCEPTANCE
Written: 2026-10-10
Updated: 2026-10-10 — implementation and headless checks complete; campaign acceptance remains.

## Scope

Every eligible Independent broker offers Relief. Completing Relief unlocks
Counterattack at those brokers; both remain repeatable. Keep mission mechanics,
payment, reputation locks and the normal planet/client/briefing launch path.
HQ supplies a compact opening-work notice with the Independent crest, exact
mission name and payment, preparation guidance, and the route to the broker.
The notice follows roster completion and retires after both opening victories.
It does not create a contract, a news report, or a remote launch action.

Canonical model: `early-operation-nouns.md`; presentation boundaries:
`company-view-nouns.md`. Alpha gate: `v0.1-alpha.md`.

## Acceptance

- Fresh and veteran companies receive Relief independent of the planet seed.
- Counterattack stays locked until Relief succeeds, then recurs alongside it.
- Faction ownership and missing-roster exclusions remain intact.
- HQ distinguishes a company without squads, first work, unlocked follow-up,
  and completed guidance using existing roster facts.
- Verify the production HQ layout at 1920x1080 and 100% UI scale.
- Live campaign discovery/save-reload acceptance remains with
  `opening-ladder-live-acceptance.md`; headless evidence must not close that gate.

## Verification and remaining acceptance

The focused opening-operation, HQ and guidance suite passes 13 tests.
`createSnapshots -Psnapshot=first-contract` exercises the production HQ at
1920x1080 and 100% for no squads, one squad, Relief completed, and both missions
completed. It checks the notice stays inside its panel and clear of obligations.
The shipped-jar boundary and assembly pass.

Remaining live checks, coordinated with `opening-ladder-live-acceptance.md`:
start a normal new save, follow HQ directions at two populated planets, confirm
the Independent client's Relief entry, save/reload, complete Relief and confirm
Counterattack, then complete it and confirm the notice retires while both jobs
remain offered. Client access and briefing personnel/lift checks must still apply.
No live campaign was launched for the headless evidence above.
