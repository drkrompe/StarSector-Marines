"""Background sound effects for the Jangala arrival, generated with ElevenLabs (sfx-cues.md).

  python art-source/company-prologue/second-wind-cinematic/arrival-audio/make_sfx.py plan [--cue ID ...]
  python .../make_sfx.py generate --cue ID [--cue ID ...] [--takes N]
  python .../make_sfx.py check
  python .../make_sfx.py pick CUE TAKE

The cue sheet (`sfx-cues.md`) says what each sound is, when it plays and how loud it sits;
CUES below holds the generation side of the same ids: prompt, length, loop and prompt influence.

generate  one paid sound-generation request per take, never retried; existing takes are kept and
          new ones numbered after them. Generations cost credits, so ask for named cues only.
check     measures every take (length, peak, integrated loudness) into the manifest.
pick      records which take of a cue the mixes use.

Takes are sources, like the hail takes and the ImageGen plates: a paid generation cannot be
rebuilt, so each take and its receipt are kept in `sfx-takes/` beside this script and
`sfx-manifest.json` lists them. Mixes are output and go under build/.
Requires ffmpeg/ffprobe on PATH and ELEVENLABS_API_KEY in the environment (never printed).
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.request

HERE = Path(__file__).resolve().parent
TAKES = HERE / "sfx-takes"
MANIFEST = HERE / "sfx-manifest.json"
MODEL = "eleven_text_to_sound_v2"

# id: (prompt, seconds, loop, prompt_influence). A bed loops; spots and transitions do not.
# Prompts describe the sound only: no music, no speech unless the cue is walla.
CUES = {
    # C1: outside, but the ear stays in the passenger cabin with the open intercom.
    "c1_cabin_bed": (
        "Interior of an old cargo spaceship passenger compartment, steady low engine hum carried "
        "through the hull, air recycler ventilation hiss, faint electrical buzz, calm, no voices, no music",
        12.0, True, 0.4),
    "c1_hull_creaks": (
        "Sparse deep metal groans and creaks of an old spaceship hull flexing, slow, heavy, "
        "isolated, long pauses between them",
        8.0, False, 0.5),
    "c1_drive_passby": (
        "Deep spaceship engine rumble swelling slowly as it passes close, low frequency drone rising "
        "then fading away, heavy and muffled, no high frequencies",
        10.0, False, 0.4),
    "c1_sparks": (
        "Faint intermittent electrical crackle and fizzing sparks from a damaged power conduit, small, dry",
        6.0, False, 0.5),
    "c1_rcs_puffs": (
        "Two short bursts of a gas thruster heard muffled through a metal hull, a soft thump and hiss each, "
        "with a pause between",
        4.0, False, 0.5),
    "c1_hound_flyby": (
        "A small spacecraft passing close under thrust, muffled low engine whoosh by, heard from inside "
        "another ship, rising and falling",
        6.0, False, 0.4),
    # C2: on the bridge as the Legion passes; the de-orbit burn starts at 9.6 s.
    # The new C2 camera (c2-camera, 2026-10-19) moves on the bridge: it leans over the forward console
    # at 1.5, steps down off the dais at 5.0 and takes four paces, squeezes past Castle at 5.55, and
    # leans over the comms bank at 6.6.
    "c2_steps": (
        "First-person footsteps on a starship bridge: one heavy boot step down off a low metal step, then "
        "four unhurried paces across a hard metal deck plate, close, interior, quiet room tone, no music",
        2.0, False, 0.6),
    "c2_brush_past": (
        "Close, soft brush of a canvas flight jacket against another person's shoulder as someone squeezes "
        "past them in a tight space, fabric rustle, interior, no voices",
        1.0, False, 0.6),
    "c2_console_lean": (
        "Close, a gloved palm and forearm settling onto a metal ship console, a soft creak and faint plastic "
        "knock, interior, no beeps",
        1.0, False, 0.6),
    "c2_bridge_bed": (
        "Small spaceship bridge room tone, quiet electronics hum, cooling fans in consoles, faint distant "
        "engine drone, calm, no beeps, no voices, no music",
        12.0, True, 0.4),
    "c2_console_beeps": (
        "Sparse soft spaceship console beeps and short chirps, a proximity tone, quiet sci-fi cockpit "
        "instrument sounds with gaps between",
        6.0, False, 0.4),
    "c2_switch_clicks": (
        "A few chunky mechanical toggle switch clicks on a metal control panel, close, dry, with pauses",
        4.0, False, 0.5),
    "c2_legion_presence": (
        "A massive warship passing slowly very close outside, deep sub-bass rumble swelling to a peak "
        "and slowly receding, felt more than heard, ominous, low frequencies only",
        12.0, False, 0.4),
    "c2_deorbit_burn": (
        "Spaceship main engine igniting inside the hull, a deep thump then a rising heavy rumble, "
        "whole structure vibrating, metal creaking as the ship pitches",
        8.0, False, 0.4),
    # Entry: heat glow then cloud, about 3 s, under Castle's "Cleared. Everybody breathe."
    "entry_reentry_roar": (
        "Atmospheric re-entry heard from inside a spacecraft cabin, a muffled roar building quickly, "
        "heavy rumble, buffeting, plasma hiss through the hull",
        6.0, False, 0.4),
    "entry_buffet": (
        "Airframe buffeting in heavy turbulence inside a spacecraft cabin, loose fittings rattling, "
        "heavy thumps against the hull",
        5.0, False, 0.4),
    # C3: first open sky through the cabin window; no speech.
    "c3_cabin_flight_bed": (
        "Inside an aircraft cabin in steady flight, smooth air rushing over the fuselage, soft ventilation, "
        "low engine drone, calm, no voices, no music",
        12.0, True, 0.4),
    "c3_wind_easing": (
        "Loud wind roar and buffeting against an aircraft fuselage gradually easing to a smooth quiet airflow",
        8.0, False, 0.5),
    "c3_frame_settle": (
        "A last soft rattle of cabin fittings settling, then a single quiet creak of a metal window frame",
        3.0, False, 0.5),
    # C4: the sky camera over farmland and port; the landing at Kilo Nine.
    "c4_high_wind_bed": (
        "High altitude open air wind, soft steady breeze, airy, calm, no gusts, no birds",
        12.0, True, 0.4),
    "c4_ground_distant": (
        "Distant ground activity heard from high above, faint farm machinery drone, heavy trucks on a far "
        "highway, a slow freight train rumbling, soft and far away",
        8.0, False, 0.4),
    "c4_port_ambience": (
        "Distant busy industrial spaceport ambience, heavy cranes, machinery, faint reversing beepers, "
        "metal clanks, far engine whine, outdoors",
        12.0, True, 0.4),
    "c4_ship_hover": (
        "Heavy spaceship hovering with powerful downward thrusters, deep steady thrust roar, slowly descending",
        6.0, False, 0.4),
    "c4_touchdown": (
        "Very heavy spacecraft landing gear touching down on concrete, deep thump, metal clank and groan",
        3.0, False, 0.5),
    "c4_dust_hiss": (
        "Dust and grit blasted outward by engine downwash, sand hissing and rattling across concrete, fading",
        4.0, False, 0.4),
    "c4_spool_down": (
        "Large spaceship engines winding down after landing, turbine whine falling in pitch to silence",
        5.0, False, 0.5),
    # C5: the yard after touchdown, the families walk off, the rise, the title.
    "c5_yard_bed": (
        "Humid jungle edge outdoors, insects and cicadas in a nearby tree line, a few distant birds, "
        "light breeze, faint far machinery, no voices, no music",
        12.0, True, 0.4),
    "c5_port_wide": (
        "Wide busy spaceport heard from above, distant cranes, a far lifter engine rumble, trucks, "
        "industrial activity spread over a large area, outdoors, no horns",
        12.0, True, 0.4),
    "c5_hull_ticks": (
        "Hot metal hull cooling after landing, sparse ticks, pings and small creaks",
        8.0, False, 0.5),
    "c5_steam_vent": (
        "Steam venting from a vehicle hull, two pressurised hiss bursts, outdoors",
        5.0, False, 0.5),
    "c5_vehicles_arrive": (
        "Heavy utility vehicles pulling up and stopping on a concrete yard, low engine idle, air brake hiss",
        6.0, False, 0.4),
    "c5_car_doors": (
        "Car doors opening and closing, two doors, then boots stepping out on concrete",
        3.0, False, 0.5),
    "c5_pa_click": (
        "Loudspeaker public address switched on, a single clean switch click and a soft speaker thump, "
        "no static, no beep",
        1.5, False, 0.6),
    "c5_ramp_drop": (
        "Heavy hydraulic cargo ramp lowering from a ship, hydraulic whine, then a heavy metal clunk "
        "as it meets the ground",
        4.0, False, 0.5),
    "c5_footsteps_crowd": (
        "Many people walking calmly on gravel and concrete, footsteps of families carrying bags, "
        "outdoors, unhurried",
        10.0, True, 0.4),
    "c5_crowd_murmur": (
        "Small crowd of families murmuring quietly outdoors, unintelligible walla, soft relieved voices, "
        "a child, no clear words",
        10.0, True, 0.3),
    # Owner review 2026-10-19: more weight on the ships, swishes for the passes, nature in C3,
    # a heavy port and a rocket landing for the joined C4-C5 shot.
    "c1_sw_engine_loop": (
        "Deep bassy spaceship engine drone, heavy low throbbing rumble, steady and powerful, sub bass, "
        "no high frequencies",
        10.0, True, 0.5),
    "c1_sw_passby_swish": (
        "Big heavy spaceship fly-by whoosh, a deep cinematic swish passing close and receding, rumbling",
        5.0, False, 0.5),
    "c1_hound_swish": (
        "Fast small spaceship fly-by, sharp engine roar and whoosh swish passing close left to right",
        4.0, False, 0.5),
    "c2_fighter_swish": (
        "Very fast fighter craft fly-by, a quick sharp whoosh swish passing close, short",
        3.0, False, 0.5),
    "c2_legion_engines_a": (
        "Massive warship engines, several deep layered bass engine hums, huge reactor drone, throbbing, "
        "sub bass, steady",
        12.0, True, 0.5),
    "c2_legion_engines_b": (
        "Enormous capital ship engine thrum, slow pulsing low bass, heavy machinery hum, distant and huge",
        12.0, True, 0.5),
    "c3_nature_muffled": (
        "Tropical jungle nature ambience heard muffled through a sealed window, distant birds, insects, "
        "soft and far away",
        10.0, True, 0.4),
    "c3_river_muffled": (
        "Distant river water rushing over rocks heard muffled from far away, soft steady flow",
        10.0, True, 0.4),
    "c4_freeway": (
        "Busy freeway traffic from a distance, heavy trucks and haulers passing continuously, "
        "tyre roar and diesel engines",
        12.0, True, 0.4),
    "c4_port_heavy": (
        "Heavy industrial cargo port, container cranes clanking, metal containers banging, heavy machinery, "
        "forklifts beeping, busy, no horns, no ship horns",
        12.0, True, 0.4),
    # Owner, 2026-10-19: "bass with burning rocket fuel like a shuttle launch", not compressed air.
    "c4_launch_burn": (
        "Space shuttle launch heard from nearby: thunderous deep bass rocket engine roar, burning rocket "
        "fuel, violent crackling exhaust, low rumble shaking the ground, sustained full thrust, no hiss, "
        "no voices",
        9.0, False, 0.6),
    "c4_landing_rocket": (
        "Huge rocket engine landing burn, deep bass roar, rumbling intense thrust, ground shaking, "
        "descending slowly",
        10.0, False, 0.5),
}

BEDS = {cue for cue, spec in CUES.items() if spec[2]}


def credential():
    key = os.environ.get("ELEVENLABS_API_KEY")
    if not key and sys.platform == "win32":
        import winreg
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, "Environment") as env:
                key = winreg.QueryValueEx(env, "ELEVENLABS_API_KEY")[0]
        except OSError:
            pass
    if not key:
        raise SystemExit("ELEVENLABS_API_KEY is unavailable; no request sent.")
    return key


def probe(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                          "-of", "default=nw=1:nk=1", str(path)], check=True, capture_output=True, text=True)
    return round(float(out.stdout.strip()), 2)


def levels(path):
    """Peak and integrated loudness, from one ffmpeg pass."""
    err = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(path),
                          "-af", "volumedetect,ebur128=peak=true", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    peak = float(re.search(r"max_volume: (-?[\d.]+) dB", err).group(1))
    lufs = re.findall(r"I:\s+(-?[\d.]+) LUFS", err)
    return peak, float(lufs[-1]) if lufs else None


def takes(cue):
    return sorted(TAKES.glob(cue + "_t[0-9][0-9].mp3"))


def load_manifest():
    return json.loads(MANIFEST.read_text(encoding="utf-8")) if MANIFEST.exists() else {"model": MODEL, "cues": {}}


def save_manifest(manifest):
    MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


def entry(manifest, cue):
    prompt, seconds, loop, influence = CUES[cue]
    row = manifest["cues"].setdefault(cue, {"chosen": None, "takes": {}})
    row.update(prompt=prompt, seconds=seconds, loop=loop, promptInfluence=influence)
    return row


def generate(cues, count):
    key = credential()
    TAKES.mkdir(parents=True, exist_ok=True)
    manifest = load_manifest()
    for cue in cues:
        prompt, seconds, loop, influence = CUES[cue]
        row = entry(manifest, cue)
        payload = {"text": prompt, "model_id": MODEL, "duration_seconds": seconds,
                   "prompt_influence": influence, "loop": loop}
        start = len(takes(cue)) + 1
        for n in range(start, start + count):
            audio = TAKES / ("%s_t%02d.mp3" % (cue, n))
            request = urllib.request.Request(
                "https://api.elevenlabs.io/v1/sound-generation?output_format=mp3_44100_128",
                data=json.dumps(payload).encode(), headers={"xi-api-key": key, "Content-Type": "application/json"})
            try:
                with urllib.request.urlopen(request, timeout=180) as response:
                    data = response.read()
                    meta = {"requestId": response.headers.get("request-id"),
                            "characterCost": response.headers.get("character-cost")}
            except urllib.error.HTTPError as error:
                save_manifest(manifest)
                raise SystemExit("Generation failed: HTTP " + str(error.code) + "; no retry made.") from None
            except (OSError, urllib.error.URLError):
                save_manifest(manifest)
                raise SystemExit("Generation connection failed; no retry made.") from None
            audio.write_bytes(data)
            peak, lufs = levels(audio)
            meta.update(modelId=MODEL, request={k: v for k, v in payload.items() if k != "model_id"},
                        audioSha256=hashlib.sha256(data).hexdigest(), durationSeconds=probe(audio),
                        peakDb=peak, integratedLufs=lufs)
            audio.with_suffix(".json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
            row["takes"][audio.stem] = {k: meta[k] for k in
                                        ("durationSeconds", "peakDb", "integratedLufs", "characterCost")}
            save_manifest(manifest)
            print("Saved %s %5.2fs peak %5.1f dB  %s LUFS  cost %s" % (
                audio.name, meta["durationSeconds"], peak, lufs, meta["characterCost"]), flush=True)


def check():
    manifest = load_manifest()
    for cue in CUES:
        for audio in takes(cue):
            peak, lufs = levels(audio)
            receipt = json.loads(audio.with_suffix(".json").read_text(encoding="utf-8"))
            entry(manifest, cue)["takes"][audio.stem] = dict(
                durationSeconds=probe(audio), peakDb=peak, integratedLufs=lufs,
                characterCost=receipt.get("characterCost"))
            print("%-26s %5.2fs peak %6.1f dB  %6s LUFS" % (audio.name, probe(audio), peak, lufs), flush=True)
    save_manifest(manifest)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("stage", choices=["plan", "generate", "check", "pick"])
    parser.add_argument("args", nargs="*")
    parser.add_argument("--cue", action="append", choices=list(CUES))
    parser.add_argument("--takes", type=int, default=2)
    args = parser.parse_args()
    if args.stage == "plan":
        for cue in args.cue or CUES:
            prompt, seconds, loop, _ = CUES[cue]
            print("%-22s %5.1fs %-4s have %d" % (cue, seconds, "loop" if loop else "", len(takes(cue))))
    elif args.stage == "generate":
        if not args.cue:
            raise SystemExit("Name the cues to generate (--cue); nothing is generated by default.")
        generate(args.cue, args.takes)
    elif args.stage == "check":
        check()
    else:
        cue, take = args.args
        manifest = load_manifest()
        if take not in entry(manifest, cue)["takes"]:
            raise SystemExit("No take %s for %s." % (take, cue))
        manifest["cues"][cue]["chosen"] = take
        save_manifest(manifest)


if __name__ == "__main__":
    main()
