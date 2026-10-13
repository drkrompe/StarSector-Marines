# Broken Supply Route acceptance

Status: IN PROGRESS — campaign integration and headless checks implemented; live acceptance remains.
Written: 2026-10-10
Updated: 2026-10-13 — direct Junction-to-Thunder Run progression; evacuation shelved and old-save compatibility removed.

## Standing model

`early-operation-nouns.md` owns the arc, direct chapter progression, site freeze, freight
accounting, rewards and recurring arrangement. `company-view-nouns.md` owns
personnel, fitted boats and HQ projection. `narrative-nouns.md` and
`battle-radio-nouns.md` own correspondence and future presentation hooks.
The Junction and Thunder Run factories own story battles; Relay Nine
owns recurring Route Security; normal personnel and boat losses remain ordinary settlement.
No vanilla economic or ownership mutation is part of this arc.

## Remaining acceptance

Run from a real campaign at 1920x1080 and 100% UI scale:

- Finish Wayline and reload its completed record. Verify the HQ lead,
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
- Finish Junction and verify exactly one Thunder Run offer and HQ lead. Back out,
  inspect, cancel deployment and reload: none starts an attempt or grants a reward.
  No branch menu, Rill consequences or Crew Recovery posting should appear.
- Junction victory opens Thunder Run immediately, with its convoy illustration,
  terms and HQ guidance. Route Security after success uses Relay Nine's territorial
  battlefield. No separate relay story fee is awarded.
- Check the recovery beat and Corvus introduction against successful, partial
  and exhausted freight ledgers; no Rill rescue or forgone-operation consequence
  should be claimed.
- Audition the revised Thunder Run briefing in the live mix; it should introduce
  the open junction and match the displayed convoy terms.
- Thunder Run: clear the three road sites, observe the two staging stops and final
  road ambush, and escort surviving carriers through the exit. Withdraw after a
  delivery, save/reload, then verify only unresolved cargo retries. Cargo lost on
  a defeated attempt never returns. Confirm the cumulative delivery award once,
  Route Security after success, and no unlock after total loss.
- During Thunder Run's last-objective push, verify small side-road attacks reach
  both rear militia posts and their guns engage. Check that the technical holds
  East Crossing while the heavy escorts go forward, and no attack pool refills
  after a recapture. Verify the opening raiders spend only their issued crude
  rockets, and the first hauler's actual destruction produces a blast and
  lingering smoke without obstructing the road.
- Revisit Results, dismiss and reopen HQ/broker, and reload after closure.
  Verify the Route Security arrangement, new job identities,
  repeatable fees, and the unchanged historical story record.
- Measure recurring duration, losses and replacement cost before finalizing
  alpha rates. Keep Route Security distinct from replaying the historical convoy reward.

## Evidence boundaries

The focused ledger/adapter tests exercise direct progression, replay refusal, mismatched
site/source/payment, partial freight receipts, exhausted failure and campaign saves. `commanderEvidence -Pmission=supply-route` checks
the shared campaign construction/roster-overlay seam for both story missions and recurring Route Security
with eight and twenty-four named marines, fitted boats, physical arrival and
untouched employer craft. It observes arrivals for up to 300 seconds, not whole campaign
victory or an installed save. `createSnapshots -Psnapshot=supply-route` renders
the production broker and HQ documents at 1920x1080 / 100% through the direct freight sequence.

The `junction`, `relay-nine` and `thunder-run` evidence selectors own full
battle replay, timing, reserve and capture controls. `last-lift` remains detached
evidence for the reserved evacuation concept. Those factory
controls and the campaign bridge checks do not substitute for the live pass
above. Opening Wayline acceptance remains in `opening-ladder-live-acceptance.md`
and `first-contract-discovery.md`.
