"""The local half of refreshing the Kit Ladder page (kit-ladder.md, Refresh runbook). The Artifact-tool steps between
these (uploading clips, publishing the page) are an agent's, and the runbook names them.

    python tools/kits/refresh.py reel [--only KEYS]      render the weapon reel (Gradle, minutes): build/weapon-reel/
    python tools/kits/refresh.py dolls                   render the machine dolls (Gradle): build/snapshots/machine-dolls/
    python tools/kits/refresh.py clips                   cut clips and posters into build/kits/clips/ and list the
                                                         clips to upload (new, or changed since their recorded upload)
    python tools/kits/refresh.py record-assets <json>    merge {"<clip id>": "<asset url>"} from the uploads into
                                                         tools/kits/review/clip-assets.json
    python tools/kits/refresh.py from-page <page.html>   no render: take the clip list, posters, asset urls and the
                                                         machine dolls from the published page (an Artifact read's file)
    python tools/kits/refresh.py build                   build/kits/kit-ladder.html (artifact form) and its manifest,
                                                         stats and seeds fresh from the repo
    python tools/kits/refresh.py record-published        after a successful publish: copy the build's manifest to
                                                         tools/kits/review/published.json (commit it)

Clip ids are the weapon reel's shot names (<category>-<key>), so a re-render keeps them and the notes keyed by them
survive. tools/kits/review/clip-assets.json holds, per clip id, the uploaded asset url and the sha1 of the mp4 that
was uploaded, so only a changed clip is uploaded again. Rendered media stays under build/, never in the repository.
"""
import base64
import hashlib
import json
import os
import platform
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ladder  # noqa: E402

ROOT = ladder.ROOT
REEL_DIR = ROOT / "build/weapon-reel"
DOLLS_DIR = ROOT / "build/snapshots/machine-dolls"
CLIPS = ROOT / "build/kits/clips"
PAGE = ROOT / "build/kits/kit-ladder.html"
ASSETS = ladder.REVIEW / "clip-assets.json"
PAGE_DOLLS = ROOT / "build/kits/page-machines.json"
DEFAULT_JAVA_HOME = r"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\jbr"
UPLOAD_BATCH = 25  # the Artifact tool's file_paths limit per call


def env():
    e = dict(os.environ)
    if platform.system() == "Windows" and not e.get("JAVA_HOME"):
        e["JAVA_HOME"] = DEFAULT_JAVA_HOME
    return e


def gradle(*args):
    exe = str(ROOT / ("gradlew.bat" if platform.system() == "Windows" else "gradlew"))
    cmd = ([exe] if platform.system() == "Windows" else ["sh", exe]) + list(args)
    print("running:", " ".join(cmd), flush=True)
    subprocess.run(cmd, cwd=ROOT, env=env(), check=True)


def sha1(path):
    return hashlib.sha1(Path(path).read_bytes()).hexdigest()


def load_assets():
    return json.loads(ASSETS.read_text(encoding="utf-8")) if ASSETS.exists() else {}


def save_assets(assets):
    ladder.REVIEW.mkdir(parents=True, exist_ok=True)
    ASSETS.write_text(json.dumps(dict(sorted(assets.items())), indent=1) + "\n", encoding="utf-8", newline="\n")


def cmd_reel(args):
    cmd = [sys.executable, str(ROOT / "tools/weapon_reel.py"), "--out", str(REEL_DIR)]
    if args and args[0] == "--only":
        cmd += ["--only", args[1]]
    subprocess.run(cmd, cwd=ROOT, env=env(), check=True)


def cmd_dolls(_):
    gradle("createSnapshots", "-Psnapshot=machine-dolls")


def cmd_clips(_):
    index = REEL_DIR / "index.json"
    if not index.exists():
        raise SystemExit("no %s: run 'refresh.py reel' first" % index)
    subprocess.run([sys.executable, str(ROOT / "tools/kits/cut_weapon_clips.py"), str(index), str(CLIPS)], check=True)
    shutil.copyfile(index, CLIPS / "index.json")
    assets = load_assets()
    shots = [c["shot"] for r in json.loads(index.read_text(encoding="utf-8"))["reels"] for c in r["clips"]]
    pending = [s for s in shots if assets.get(s, {}).get("sha1") != sha1(CLIPS / (s + ".mp4"))]
    (CLIPS / "upload.json").write_text(json.dumps([str(CLIPS / (s + ".mp4")) for s in pending], indent=1), encoding="utf-8", newline="\n")
    gone = sorted(set(assets) - set(shots))
    print("%d clips, %d to upload (new or changed); %s" % (len(shots), len(pending), CLIPS / "upload.json"))
    for i in range(0, len(pending), UPLOAD_BATCH):
        print("upload batch %d: %s" % (i // UPLOAD_BATCH + 1, ", ".join(pending[i:i + UPLOAD_BATCH])))
    if gone:
        print("recorded clips no longer in the reel (their notes become orphans): %s" % ", ".join(gone))


def cmd_record_assets(args):
    if not args:
        raise SystemExit("record-assets <json>: {\"<clip id>\": \"<asset url exactly as the Artifact tool returned it>\"}")
    urls = json.loads(Path(args[0]).read_text(encoding="utf-8"))
    assets = load_assets()
    for shot, url in urls.items():
        mp4 = CLIPS / (shot + ".mp4")
        assets[shot] = {"url": url, "sha1": sha1(mp4) if mp4.exists() else None}
    save_assets(assets)
    print("recorded %d asset urls in %s" % (len(urls), ASSETS))


def cmd_from_page(args):
    """Rebuild the reel index, posters and asset urls from a published page, and keep its machine dolls."""
    if not args:
        raise SystemExit("from-page <page.html>")
    data = ladder.page_data(ladder.read(args[0]))
    CLIPS.mkdir(parents=True, exist_ok=True)
    reels, assets = {}, load_assets()
    for c in data["WEAPONREEL"]["cards"]:
        reels.setdefault(c["cat"], []).append({
            "shot": c["id"], "category": c["cat"], "weaponId": c["weaponId"], "name": c["name"], "mode": c["mode"],
            "shooter": c["shooter"], "rounds": c["rounds"], "role": c.get("role"), "fireSound": c.get("fire"),
            "impactSound": c.get("impact"), "loopSound": c.get("loop")})
        if c.get("poster", "").startswith("data:image/jpeg;base64,"):
            (CLIPS / (c["id"] + ".jpg")).write_bytes(base64.b64decode(c["poster"].split(",", 1)[1]))
        if c.get("url") and assets.get(c["id"], {}).get("url") != c["url"]:
            assets[c["id"]] = {"url": c["url"], "sha1": None}
    index = {"reels": [{"category": k, "clips": v} for k, v in reels.items()], "leftOut": data["WEAPONREEL"].get("leftOut", [])}
    (CLIPS / "index.json").write_text(json.dumps(index, indent=1, ensure_ascii=False), encoding="utf-8", newline="\n")
    save_assets(assets)
    PAGE_DOLLS.write_text(json.dumps(data.get("MACHINES", []), ensure_ascii=False), encoding="utf-8", newline="\n")
    print("from the page: %d clips (%d with an asset url), %d machine dolls -> %s, %s"
          % (sum(len(v) for v in reels.values()), sum(1 for c in data["WEAPONREEL"]["cards"] if c.get("url")),
             len(data.get("MACHINES", [])), CLIPS, PAGE_DOLLS))


def cmd_build(_):
    index = CLIPS / "index.json"
    if not index.exists():
        raise SystemExit("no %s: run 'refresh.py clips' (after a reel render) or 'refresh.py from-page <html>'" % index)
    (CLIPS / "assets.json").write_text(json.dumps({k: v["url"] for k, v in load_assets().items() if v.get("url")}, indent=1),
                                       encoding="utf-8", newline="\n")
    dolls = DOLLS_DIR if (DOLLS_DIR / "manifest.json").exists() else PAGE_DOLLS
    subprocess.run([sys.executable, str(ROOT / "tools/kits/build.py"), "--artifact", "--dolls", str(dolls), "--reel", str(index),
                    "--clips", str(CLIPS / "assets.json"), str(PAGE)], check=True)


def cmd_record_published(_):
    manifest = Path(str(PAGE) + ".manifest.json")
    if not manifest.exists():
        raise SystemExit("no %s: run 'refresh.py build' and publish it first" % manifest)
    ladder.REVIEW.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(manifest, ladder.PUBLISHED)
    print("recorded %s as the published page's manifest" % ladder.PUBLISHED)


COMMANDS = {"reel": cmd_reel, "dolls": cmd_dolls, "clips": cmd_clips, "record-assets": cmd_record_assets,
            "from-page": cmd_from_page, "build": cmd_build, "record-published": cmd_record_published}


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in COMMANDS:
        raise SystemExit(__doc__)
    COMMANDS[sys.argv[1]](sys.argv[2:])


if __name__ == "__main__":
    main()
