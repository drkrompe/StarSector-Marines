# Operation radio auditions

These are authoring assets, not a runtime sound bank. Nothing in this folder is
registered in `audio.json` or shipped under `mod/`. The five exchanges audition
specific units coordinating an operation while the player oversees deployment
aboard ship. They do not issue AI orders or establish new mission events.

`catalog.json` owns stable line IDs, clean transcripts, synthesis directions,
speaker/recipient identities, voice IDs, take numbers, exchange order and the
facts each exchange would require. The freight yard, convoy and callsigns are
concrete audition bindings, not universal mission facts. They must be bound to
actual units and disclosed objectives before runtime playback.

## Cast and delivery

| Role | Casting | Status / direction |
| --- | --- | --- |
| Company Officer | Company Officer B, `cuwCdnt9CKAB9XrTA1j0` | Approved original Voice Design; measured British command-ship authority. |
| Kestrel Two | Liam, `TX3LPaxmHKxFdv7VOQHJ` | User accepted the strained, breathless combat audition. Young American infantry leader. |
| Viper One | Sarah, `EXAVITQu4vr4xnSDxMaL` | Two-line audition; American female supporting squad leader, forceful under fire. |
| Rook One | Charlie, `IKne3meq5aSn9XLyUdCD` | Two-line audition; Australian male transport pilot, brisk and alert with occasional levity. |

The three squad/pilot voices are ElevenLabs premade voices, verified against
the account on 2026-10-11. They are casting choices for fictional roles; no game
recording was uploaded or cloned. Callsigns identify units, not a rule that
every unit of that role gets the same voice. Preserve a selected leader's voice
throughout an operation rather than randomly recasting each transmission.

`retained/` preserves the accepted four-turn Kestrel withdrawal exchange and its
provenance, including clean WAV edits and original source MP3s where applicable.
The callout uses the earlier approved callsign splice; its clean master is the
trimmed result, not a newly synthesized whole sentence. The new assignment
exchange reuses that callout and the accepted under-fire acknowledgement.
The two other accepted turns remain available as withdrawal references.

## Generate and audition

From the repository root:

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
traffic should observe an accepted own-force directive with a stable squad and
destination. An arbiter refresh is not a new assignment; reaching an objective
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
Current runtime still uses its existing single-channel policy; this audition
does not enable overlap or new aircraft speakers in that controller.
