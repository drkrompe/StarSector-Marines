# The Dust Ledger: dialogue and character notes

Status: ACTIVE — authored script; text-only until recordings are approved.

Written: 2026-10-25

## How to read this script

The chronological register for **The Dust Ledger** at Saltwell Cross on Asharu:
Mara and Castle's briefing, field calls, alarm and ledger barks, and the debrief
variants. `dust-ledger-crossing.md` owns the mission rules; this file owns what
is said. Mara is Wayline's tired, businesslike acting head; Castle is dry and
terse and treats quiet as a resource. Sabine Hale speaks only in field calls
about ground she can see. Tone and scale follow `shepherd-recovery-dialogue.md`:
a small, local job on a world nobody governs, not a war.

Every line is catalogued in `wayline_narration.json` under mission
`story_corvus_dust_ledger`, with `textKey` into `strings.json` (`corvusDustLedger*`)
and `voiced: false`. The catalog is the runtime source; nothing here is wired.

## Names settled

- **Saltwell Cross** — the trading post (kept from the placeholder).
- **Oskar Teague** — Sable Wake's broker (the placeholder "Vey" read too close to Venn and Vale).
- **Anneke Harrow** — leads the Harrow crew, a Wayline account stranded on the crawler road. (Not "Pell", too close to Prell.)
- **Tobias Ankrum** — Saltwell Cross's market warden. Speaks for the town only.

## Facts every variant keeps

Why nobody interferes: Asharu is Independent; the Hegemony claims it but patrols
only to chase pirates or collect tariffs; Wayline lands freight there lawfully;
the warden's indifference is bought with a season of freight credit, price: the
fight stays at the pump house and the market is untouched. Prell's dispatch
authority and the Warion listening post are named in every victory debrief.

## Briefing (`briefing.01`–`briefing.12`, mission `story_corvus_dust_ledger`)

Mara (01, 03, 05, 07, 09, 11) and Castle (02, 04, 06, 08, 10, 12) alternate:
the post and the broker; who might stop us; the Hegemony's indifference; the
warden's price; the broker's lift-off; the burn rule; the two favours; the
ledger and strongbox; orders. Keys `corvusDustLedgerBrief01`–`Brief12`.

## Field calls (events `corvus.dust_ledger.*`)

| Event | Speaker | Trigger |
|---|---|---|
| `harrow_crate` | Mara | the coupling crate is picked up |
| `harrow_delivered` | Mara | the crate reaches the Harrow yard |
| `warden_approach` | Castle | a squad leader nears the well house |
| `warden_nod` | Mara | the stay at the well house completes |
| `clock_short` | Castle | lift-off clock nearly out, once |
| `alarm_shot` | Castle | alarm by a shot |
| `alarm_sighted` | Castle | alarm by sentry sighting inside the ring |
| `alarm_clock` | Mara | alarm by lift-off; broker gone |
| `cantina_joins` | Sabine | alarm, warden's nod NOT earned |
| `cantina_held` | Sabine | alarm, warden's nod earned |
| `ledger_burning` | Castle | burn starts (at alarm) |
| `ledger_half` | Castle | burn reaches half |
| `ledger_seized` | Castle | ledger room seized with books remaining |
| `ledger_burned` | Mara | burn completes unseized |
| `salvage_team` | Castle | alarm, coupling delivered |
| `spotters` | Sabine | alarm, warden's nod earned (yard revealed before alarm) |
| `broker_flees` | Castle | alarm, broker still on site |
| `broker_surrendered` | Sabine | company unit reaches broker |
| `broker_lost` | Castle | held broker killed |
| `broker_boarded` | Mara | broker reaches the skimmer pad |
| `broker_apron` | Mara | held broker reaches the apron alive |

Keys are `corvusDustLedgerField<Name>`.

## Debrief (victory only)

Three turns: Castle `opening.<grade>`, Mara `finding.<grade>_<broker>`, Castle
`close.<broker>`. Grade is the unburned share at seizure: `full` (little or none
burned), `partial` (some burned, ledger seized), `strongbox` (ledger fully
burned, strongbox only). Broker is `caught` (held and alive at the apron) or
`escaped` (boarded, departed on the clock, or died in custody). All six
`finding` lines name Teodor Prell's dispatch authority, the last batch dated the
week he left, and the Warion listening post. Keys `corvusDustLedgerDebrief*`.
