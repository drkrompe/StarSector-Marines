# Broken Supply Route acceptance

Status: IN PROGRESS — campaign integration and headless checks implemented; live acceptance remains.
Written: 2026-10-10
Updated: 2026-10-10 — branch commitment, fitted named deployment, retry settlement and recurring work connected.

## Standing model

`early-operation-nouns.md` owns the arc, two choices, site freeze, civilian
accounting, rewards and recurring arrangements. `company-view-nouns.md` owns
personnel, fitted boats and HQ projection. `narrative-nouns.md` and
`battle-radio-nouns.md` own correspondence and future presentation hooks.
The existing finite Junction, Last Lift and Relay Nine factories remain the
battle authority; normal personnel and boat losses remain ordinary settlement.
No vanilla economic or ownership mutation is part of this arc.

## Remaining acceptance

Run from a real campaign at 1920x1080 and 100% UI scale:

- Finish Wayline or load an older completed Wayline save. Verify the HQ lead,
  Mara's guaranteed Junction dossier and continuing unrelated/Wayline work.
  Introduce at Jangala and at the accessible-office fallback; after introduction,
  make the original site inaccessible and verify work pauses instead of moving.
- Launch Junction with actual issued gear and one or several fitted boats.
  Check named squads, acting NCOs, employer separation, boat and passenger losses.
  Verify the two-squad/four-ready-each minimum and larger companies.
- Inspect both requests, Back out, reload, then explicitly commit one. Repeat
  input and reload must not switch the branch. Canceled deployment and Retreat
  preserve the commitment without awarding the ending.
- Last Lift: confirm full and partial boarding, escort/pickup loss, withdrawal,
  and a retry containing only stranded people. Save/load between attempts.
  Confirm one cumulative success payment; a closed failed cohort gets neither
  a fabricated victory nor recurring access.
- Relay Nine: take ground, leave militia holding it, observe a recapture and
  restoration, and settle territorial victory with surviving raiders elsewhere.
  Defeat keeps only this branch retryable; success awards the story fee once.
- Revisit Results, dismiss and reopen HQ/broker, and reload after either ending.
  Verify the chosen recurring arrangement, new job identities, new rescue crews,
  repeatable fees, and the unchanged historical story record. Unchosen work is
  not undertaken, never a fictional failed battle or death toll.
- Measure recurring duration, losses and replacement cost before finalizing
  alpha rates. Neither branch should be a clearly superior income path.

## Evidence boundaries

The focused ledger/adapter tests exercise commitment, replay refusal, mismatched
site/source/payment, partial rescue, exhausted failure, Java campaign save and
primitive XStream round-trips. `commanderEvidence -Pmission=supply-route` checks
the shared campaign construction/roster-overlay seam for all three missions
with eight and twenty-four named marines, fitted boats, physical arrival and
untouched employer/civilian craft. It observes fifty seconds, not whole campaign
victory or an installed save. `createSnapshots -Psnapshot=supply-route` renders
the production broker and HQ documents at 1920x1080 / 100% through both branches.

The existing `junction`, `last-lift` and `relay-nine` evidence selectors own full
battle replay, timing, reserve, capture and rescue controls. Those factory
controls and the campaign bridge checks do not substitute for the live pass
above. Opening Wayline acceptance remains in `opening-ladder-live-acceptance.md`
and `first-contract-discovery.md`.
