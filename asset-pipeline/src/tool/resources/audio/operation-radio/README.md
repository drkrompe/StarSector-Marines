# Operation radio

`live/` contains the Marine Operations runtime bank: assignment handshakes and
combat reports with exchange-local casting. `mod/data/marines/operation_radio.json`
owns the runtime transcripts, voice IDs, durations and selection mappings. The
manifest applies the same offline comms recipe to every turn. Clean sources and
receipts stay here; only processed Ogg files ship.

Combat report mappings are pools of line IDs: three takes per actor for each of
the seven existing cues, giving nine recordings per event. Existing single-ID
mappings remain readable. Runtime exhausts each event's pool before repeating;
mission preemption preserves that history and a new battle resets it. New takes
keep the established cast and comms treatment. Their wording does not assert
unobserved directions, remaining enemy counts, successful maneuvers or support.

The older five-scene catalog and `ensemble/` retain authoring auditions. Their
bespoke destinations, covering maneuvers and transport reports are not enabled
by the runtime bank. They do not issue AI orders or establish mission events.

`catalog.json` owns stable line IDs, clean transcripts, synthesis directions,
speaker/recipient identities, voice IDs, take numbers, exchange order and the
facts each exchange would require. The freight yard, convoy and callsigns are
concrete audition bindings, not universal mission facts. Specific destinations and actions require disclosed operational evidence before
runtime playback; callsigns are local to a self-contained exchange.

## Cast and delivery

| Role | Casting | Status / direction |
| --- | --- | --- |
| Company Officer | Company Officer B, `cuwCdnt9CKAB9XrTA1j0` | Approved original Voice Design; measured British command-ship authority. |
| Kestrel Two | Liam, `TX3LPaxmHKxFdv7VOQHJ` | User accepted the strained, breathless combat audition. Young American infantry leader. |
| Hammer Three | Original military B, `HuAfwy9ycsANuERZQHLU` | Worn American baritone; compact, protective, firm. |
| Viper One | Original military B, `1I6DkREjhKUTww20MdUm` | Yorkshire infantry leader; forceful and impatient under pressure. |
| Rook One | Original military B, `gpYq8Tu9Py4uI902kHOw` | Australian transport pilot; weathered, brisk; retained for future aircraft hooks. |

The original company officer and three military B voices are Voice Design
creations; Kestrel uses the premade Liam voice. No game recording was uploaded
or cloned. `ensemble/cast.json` retains the selected voice IDs and source design
direction. The earlier five-scene catalog retains its original Sarah/Charlie
casting as historical audition provenance, not current runtime selection.

Squads do not acquire stable radio identities. Cast rotates between exchanges,
keeping one callsign and actor throughout each conversation. Maps with one to a
hundred squads use the same bounded global traffic budget.

`retained/` preserves the accepted four-turn Kestrel withdrawal exchange and its
provenance, including clean WAV edits and original source MP3s where applicable.
The callout uses the earlier approved callsign splice; its clean master is the
trimmed result, not a newly synthesized whole sentence. The new assignment
exchange reuses that callout and the accepted under-fire acknowledgement.
The two other accepted turns remain available as withdrawal references.

## Generate and audition

From the repository root, validate or generate the live bank:

```powershell
python tools/generate_wayline_narration.py --cast operation-radio
python tools/generate_wayline_narration.py --cast operation-radio --generate
.\gradlew.bat :asset-pipeline:processAudio
```

The live generator reuses unchanged input hashes and validates retained source
hashes. Changed takes require explicit `--replace`; builds never call the API.
Retained callouts/acknowledgements cannot be silently resynthesized by this path.

For the older five-scene audition catalog:

```powershell
python tools/operation_radio_audition.py
python tools/operation_radio_audition.py --generate --line viper_cover_crossing
python tools/operation_radio_audition.py --render build/operation-radio
```

Default execution only validates sources and reports missing takes; it neither
loads credentials nor makes requests. `--generate` is explicit paid authoring.
With no `--line` it generates only missing catalog takes; repeat `--line` to
select a subset. Input and audio hashes retain unchanged recordings. To revise
a line, increment its `take` and edit its synthesis text; the earlier take stays
on disk. There are no automatic retries. A pending-request file survives an
ambiguous failure and prevents another charge until the author investigates.
Credentials come from `ELEVENLABS_API_KEY` using the existing narration helper;
receipts contain only the request ID, cost, input, hashes and audio metadata.

Each independently spoken turn has its own clean source. Rendering is offline
and applies the approved company comms recipe without changing pitch or speed.
It retains the full squad performance, including breaths, rather than trimming
to the first and last word. WAVs and MP3s are produced outside the source tree.
`render.json` retains turn timing, the processing recipe and audition mix levels.

Individual previews cover assignment, contested status, a covered crossing,
reinforcement delivery, and capture/hold. `operation-radio-reel.mp3` places them
in a compressed sequence, with the pilot's first report overlapping the contested
status report at -13 dB and biased right. Command's answers remain sequential;
the same character never speaks two lines simultaneously. This is a mix
audition, not the intended frequency of chatter during play. No combat effects
are baked into the voices. Live combat masking and performance quality require
listening; decoding and peak checks alone do not establish either.

V4 voice direction follows the current provider guidance:
https://elevenlabs.io/docs/overview/capabilities/text-to-speech/best-practices

## Event and mix direction

Read `battle-radio-nouns.md` for the standing presentation boundary. Assignment
traffic observes an accepted own-force directive. Squad IDs validate that
context internally; they do not bind a voice or callsign to a roster entry. An arbiter refresh is not a new assignment; reaching an objective
is not capturing it. A radio acknowledgement does not delay the real AI order.
Two squads covering a crossing need evidence of that actual support relationship;
today's generic combat cues do not supply it. A completed transport request does
not prove passengers physically landed. These requirements are repeated beside
each audition exchange so a future integrator cannot mistake sample prose for
an existing trigger.

Prefer a full callout/acknowledgement/instruction/readback for a new task, then
shorter addressed reports while that conversation remains current. Routine
updates should be sparse, disposable and optional to follow. No mission-critical
information may depend solely on hearing this background traffic. Use quiet
periods; do not fill every available gap. Dry humor belongs in low-pressure
moments, never over a casualty or urgent warning.

Future playback should keep each exchange's turns ordered and protect its
recipient and instruction from competing foreground speech. At most one quiet
secondary net may overlap; duck it for names, instructions and warnings. Mara's
mission dialogue retains priority and must finish. Cancel obsolete queued turns
when assignments change, a unit is lost, or a newer fact invalidates the report.
Runtime uses a single foreground exchange, samples assignment changes into one
pending slot, and admits combat reports through the shared cue policy. No
secondary-net overlap or aircraft speakers are enabled yet.
