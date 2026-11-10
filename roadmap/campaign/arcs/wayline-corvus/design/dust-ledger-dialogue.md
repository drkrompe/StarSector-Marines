# The Dust Ledger: dialogue and character notes

Status: ACTIVE — authored script; text-only until recordings are approved.

Written: 2026-10-25

## How to read this script

The chronological register for **The Dust Ledger** at Saltwell Cross on Asharu:
Zoey and Castle's briefing, field calls, alarm and ledger barks, and the debrief
variants. `dust-ledger-crossing.md` owns the mission rules; this file owns what
is said. Zoey is Wayline's tired, businesslike acting head; Castle is dry and
terse and treats quiet as a resource. Morgan Hale speaks only in field calls
about ground she can see. Tone and scale follow `shepherd-recovery-dialogue.md`:
a small, local job on a world nobody governs, not a war.

Every line is catalogued in `wayline_narration.json` under mission
`story_corvus_dust_ledger`, with `textKey` into `strings.json` (`corvusDustLedger*`)
and `voiced: false`. The catalog is the runtime source; nothing here is wired.

## Names settled

- **Saltwell Cross** — the trading post (kept from the placeholder).
- **Oskar Teague** — Sable's broker (the placeholder "Vey" read too close to Venn and Vale).
- **Anneke Harrow** — leads the Harrow crew, a Wayline account stranded on the crawler road. (Not "Reed", too close to Prell.)
- **Tobias Ankrum** — Saltwell Cross's market warden. Speaks for the town only.

## Facts every variant keeps

Why nobody interferes: Asharu is Independent; the Hegemony claims it but patrols
only to chase pirates or collect tariffs; Wayline lands freight there lawfully;
the warden's indifference is bought with a season of freight credit, price: the
fight stays at the pump house and the market is untouched. Prell's dispatch
authority and the Warion listening post are named in every victory debrief.

## Briefing (`briefing.01`–`briefing.14`, mission `story_corvus_dust_ledger`)

Zoey (01, 03, 05, 07, 11) and Castle (02, 04, 06, 08, 12) alternate: the post
and the broker; who might stop us; the Hegemony's indifference; the warden's
price; the broker's lift-off and the chase for him once the alarm goes (the one
bonus, with Wayline's price on him); the burn rule; the ledger and strongbox;
orders. Keys `corvusDustLedgerBrief01`–`Brief14`, with 09 and 10 (the two
favours) cut. Turns 13 (Zoey: gun nests and manned pillboxes) and 14 (Castle:
rockets or the Hound answer the nests; pillboxes by hand) play after turn 08, so
catalog order is 01–08, 13, 14, 11, 12.

## Field calls (events `corvus.dust_ledger.*`)

| Event | Speaker | Trigger |
|---|---|---|
| `clock_short` | Castle | lift-off clock nearly out, once |
| `alarm_shot` | Castle | alarm by a shot |
| `alarm_sighted` | Castle | alarm by sentry sighting inside the ring |
| `alarm_clock` | Zoey | alarm by lift-off; broker gone |
| `ledger_burning` | Castle | burn starts (at alarm) |
| `ledger_half` | Castle | burn reaches half |
| `ledger_seized` | Castle | ledger room seized with books remaining |
| `ledger_burned` | Zoey | burn completes unseized |
| `nest_destroyed` | Castle | a gun nest is destroyed |
| `pillbox_contact` | Castle | first fire from a pillbox, once |
| `broker_flees` | Castle | alarm, broker still on site |
| `broker_surrendered` | Morgan | company unit reaches broker |
| `broker_lost` | Castle | held broker killed |
| `broker_boarded` | Zoey | broker reaches the skimmer pad |
| `broker_apron` | Zoey | held broker reaches the apron alive |

Keys are `corvusDustLedgerField<Name>`.

## Debrief (victory only)

Three turns: Castle `opening.<grade>`, Zoey `finding.<grade>_<broker>`, Castle
`close.<broker>`. Grade is the unburned share at seizure: `full` (little or none
burned), `partial` (some burned, ledger seized), `strongbox` (ledger fully
burned, strongbox only). Broker is `caught` (held and alive at the apron) or
`escaped` (boarded, departed on the clock, or died in custody). All six
`finding` lines name Jack Prell's dispatch authority, the last batch dated the
week he left, and the Warion listening post. Keys `corvusDustLedgerDebrief*`.
