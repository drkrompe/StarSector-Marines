# Broken Supply Route acceptance

Status: IN PROGRESS — campaign integration and headless checks implemented; live acceptance remains.
Written: 2026-10-10
Updated: 2026-10-10 — branch commitment, fitted named deployment, Thunder Run extends the freight branch; physical delivery and persistent cargo join acceptance.

## Standing model

`early-operation-nouns.md` owns the arc, two choices, site freeze, civilian
accounting, rewards and recurring arrangements. `company-view-nouns.md` owns
personnel, fitted boats and HQ projection. `narrative-nouns.md` and
`battle-radio-nouns.md` own correspondence and future presentation hooks.
The Junction, Last Lift and Thunder Run factories own story battles; Relay Nine
owns recurring Route Security and already-active legacy battles; normal personnel and boat losses remain ordinary settlement.
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
- Watch Kestrel cover the Junction advance and pause over settling militia;
  check the absence of a player gunship card or support cost. On Thunder Run,
  identify the returning callsign, visible flak and damage warning. Leave the
  guns alive for the crash; clear them early in another attempt for survival.
  Inspect falling fire, impact damage, and the charred hull after its smoke ends.
  Replay after a loss and verify fresh employer support. New Kestrel radio cues
  are text-only pending a voice-production pass; verify their live display and
  that urgent loss cues supersede stale arrival messages.
- Inspect both requests, Back out, reload, then explicitly commit one. Repeat
  input and reload must not switch the branch. Canceled deployment and Retreat
  preserve the commitment without awarding the ending.
- Last Lift: confirm full and partial boarding, escort/pickup loss, withdrawal,
  and a retry containing only stranded people. Save/load between attempts.
  Confirm one cumulative success payment; a closed failed cohort gets neither
  a fabricated victory nor recurring access.
- Freight commitment opens Thunder Run immediately, with its convoy illustration,
  terms and HQ guidance. Route Security after success uses Relay Nine's territorial
  battlefield. No separate relay story fee is awarded.
- Load an unstarted legacy Relay commitment: it should offer Thunder Run while
  retaining prior payments and site. Load an already active Relay battle: its old
  attempt settles once, then either outcome advances to Thunder Run. Existing
  freight receipts and completed recurring access must survive.
- Check the recovery beat and Corvus introduction against successful, partial
  and exhausted branch ledgers; no rescue or delivery from the unchosen path
  should be claimed.
- Audition the revised Thunder Run briefing in the live mix; it should introduce
  the open junction and match the displayed convoy terms.
- Thunder Run: clear the three road sites, observe the two staging stops and final
  road ambush, and escort surviving carriers through the exit. Withdraw after a
  delivery, save/reload, then verify only unresolved cargo retries. Cargo lost on
  a defeated attempt never returns. Confirm the cumulative delivery award once,
  Route Security after success, and no unlock after total loss. An older save
  already completed at Relay Nine must retain its prior recurring work.
- During Thunder Run's last-objective push, verify small side-road attacks reach
  both rear militia posts and their guns engage. Check that the technical holds
  East Crossing while the heavy escorts go forward, and no attack pool refills
  after a recapture. Verify the opening raiders spend only their issued crude
  rockets, and the first hauler's actual destruction produces a blast and
  lingering smoke without obstructing the road.
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
the shared campaign construction/roster-overlay seam for all three story missions and recurring Route Security
with eight and twenty-four named marines, fitted boats, physical arrival and
untouched employer/civilian craft. It observes arrivals for up to 300 seconds, not whole campaign
victory or an installed save. `createSnapshots -Psnapshot=supply-route` renders
the production broker and HQ documents at 1920x1080 / 100% through both branches.

The existing `junction`, `last-lift` , `relay-nine` and `thunder-run` evidence selectors own full
battle replay, timing, reserve, capture and rescue controls. Those factory
controls and the campaign bridge checks do not substitute for the live pass
above. Opening Wayline acceptance remains in `opening-ladder-live-acceptance.md`
and `first-contract-discovery.md`.
