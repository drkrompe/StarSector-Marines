#!/usr/bin/env python3
"""The release and deploy builds' textures: each image fitted to how large it is drawn.

usage: optimize_pngs.py <mod-dir> <dest-root> [--record R] [--policy P] [--report DIR]

Reads every PNG under <mod-dir>/graphics and writes what ships under <dest-root>/graphics, plus
<dest-root>/data/config/marines_texture_fit.json naming every image whose shipped file differs
from its source in size or format. The tracked sources are never rewritten.

Per image:
  * measured (an entry in the usage record R, art-source/texture-usage/usage.json) and not
    excluded: downscaled, premultiplied-alpha Lanczos, to the smallest policy scale at or above
    (largest display / source size) x margin, never upscaled; then losslessly recompressed. A
    fully opaque one the code loads may ship as JPG instead (policy "jpg").
  * unmeasured, engine-loaded (named by data the game itself reads), never-fit, or sliced at
    load: shipped at full size, losslessly recompressed when that is smaller and pixel-identical.

The code addresses every image in source pixels; the manifest's logical sizes are what let a
fitted texture read the same (ModTextures). A warm run reuses every output whose source and
decision are unchanged. A report of what was measured, what was not, and what each fit saved,
on disk and decoded, goes to --report (default <dest-root>/../reports/texture-fit).

See roadmap/release/design/texture-fit.md.
"""
import argparse
import fnmatch
import hashlib
import io
import json
import os
import re
import shutil
import sys
from concurrent.futures import ProcessPoolExecutor

import numpy as np
from PIL import Image

MANIFEST = "data/config/marines_texture_fit.json"
INDEX = ".fit-index.json"
# Data the game itself reads draws its images at their native size, so those cannot shrink.
ENGINE_DATA_EXT = (".ship", ".skin", ".wpn", ".proj", ".faction", ".csv", ".system", ".variant")
ENGINE_DATA_FILES = ("data/config/settings.json", "data/config/planets.json",
                     "data/config/custom_entities.json")
ENGINE_DATA_DIRS = ("data/campaign/", "data/characters/", "data/world/", "data/hulls/",
                    "data/weapons/", "data/shipsystems/")
GRAPHICS_REF = re.compile(r"graphics/[^\"'\s,]+?\.(?:png|jpg|jpeg)", re.IGNORECASE)


def pot(n):
    p = 1
    while p < n:
        p *= 2
    return p


def match_any(rel, patterns):
    return any(fnmatch.fnmatch(rel, p) for p in patterns)


def engine_references(mod_dir):
    refs = set()
    data = os.path.join(mod_dir, "data")
    for d, _, files in os.walk(data):
        for f in files:
            p = os.path.join(d, f)
            rel = os.path.relpath(p, mod_dir).replace(os.sep, "/")
            engine = (f.lower().endswith(ENGINE_DATA_EXT) or rel in ENGINE_DATA_FILES
                      or any(rel.startswith(x) for x in ENGINE_DATA_DIRS))
            if not engine:
                continue
            try:
                with open(p, encoding="utf-8", errors="replace") as fh:
                    refs.update(m.replace("\\", "/") for m in GRAPHICS_REF.findall(fh.read()))
            except OSError:
                pass
    return refs


def trusted_need(entry, w, h, ignore, battle_zoom=16.0):
    """Largest screen-pixels-per-source-pixel over trusted sources, and the source that set it.

    The record measures world draws at the closest zoom, 16x; a policy that fits battle art to a
    lower zoom scales them by battle_zoom / 16 (closer than that, the art is magnified)."""
    best, by = 0.0, None
    for label, usage in (entry or {}).get("sources", {}).items():
        if match_any(label, ignore):
            continue
        k = battle_zoom / 16.0 if usage.get("domain") == "world" else 1.0
        need = k * max(usage.get("displayWidth", 0) / w, usage.get("displayHeight", 0) / h)
        if need > best:
            best, by = need, label
    return best, by


def choose_scale(need, w, h, policy):
    target = need * policy["margin"]
    scales = sorted(policy["scales"])
    if target >= 1.0:
        return 1.0
    scale = min(s for s in scales if s >= target)
    dims = lambda s: (max(1, round(w * s)), max(1, round(h * s)))
    padded = tuple(pot(n) for n in dims(scale))
    # Free quality: a larger scale that pads to the same power-of-two texture costs no memory.
    for s in scales:
        if s > scale and tuple(pot(n) for n in dims(s)) == padded:
            scale = s
    return scale


def decide(rel, w, h, entry, policy, engine_refs):
    if rel in engine_refs or match_any(rel, policy.get("engineLoaded", [])):
        return {"action": "keep", "reason": "engine-loaded"}
    if match_any(rel, policy["neverFit"]):
        return {"action": "keep", "reason": "never-fit"}
    forced = policy.get("forceScale", {}).get(rel)
    if forced:
        # A proof run's override: shrink this image whatever was measured, to show it still reads right.
        return {"action": "fit", "reason": "forced", "scale": forced, "need": None, "by": "forceScale",
                "jpg": False, "size": [max(1, round(w * forced)), max(1, round(h * forced))]}
    need, by = trusted_need(entry, w, h, policy.get("ignoreSources", []),
                            float(policy.get("battleZoom", 16.0)))
    if entry is None or by is None:
        return {"action": "keep", "reason": "unmeasured"}
    jpg = policy.get("jpg", {})
    allow_jpg = bool(jpg.get("enabled")) and not match_any(rel, jpg.get("exclude", []))
    decision = {"need": round(need, 4), "by": by, "jpg": allow_jpg}
    if entry.get("sliced"):
        decision.update(action="keep", reason="sliced-at-load", jpg=False)
        return decision
    scale = choose_scale(need, w, h, policy)
    if scale >= 1.0:
        decision.update(action="fit", reason="needs-full", scale=1.0, size=[w, h])
    else:
        decision.update(action="fit", reason="fitted", scale=scale,
                        size=[max(1, round(w * scale)), max(1, round(h * scale))])
    return decision


def resized(im, size):
    if im.mode in ("RGB", "L"):
        return im.resize(size, Image.LANCZOS)
    rgba = im.convert("RGBA")
    # Premultiplied, so transparent texels' colour does not bleed into the edges.
    return rgba.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")


def opaque(im):
    if im.mode in ("RGB", "L"):
        return True
    if im.mode == "P" and "transparency" not in im.info:
        return True
    alpha = np.asarray(im.convert("RGBA"))[:, :, 3]
    return bool(alpha.min() == 255)


def encode_png(im):
    kw = {"optimize": True}
    if im.mode == "P" and "transparency" in im.info:
        kw["transparency"] = im.info["transparency"]
    buf = io.BytesIO()
    im.save(buf, "PNG", **kw)
    return buf.getvalue()


def convert(job):
    src, out_root, rel, decision, jpg_policy = job
    src_size = os.path.getsize(src)
    with Image.open(src) as im:
        im.load()
        w, h = im.size
        result = {"rel": rel, "w": w, "h": h, "srcBytes": src_size, "file": rel,
                  "outW": w, "outH": h}
        target = tuple(decision.get("size", (w, h)))
        if decision["action"] == "fit" and target != (w, h):
            out_im = resized(im, target)
            data = encode_png(out_im)
            result["outW"], result["outH"] = target
        else:
            out_im = im
            data = encode_png(im)
            same = False
            if len(data) < src_size:
                a = np.asarray(im.convert("RGBA"))
                with Image.open(io.BytesIO(data)) as back:
                    b = np.asarray(back.convert("RGBA"))
                same = a.shape == b.shape and bool((a == b).all())
            if not same:
                data = None
        if decision.get("jpg") and opaque(im):
            buf = io.BytesIO()
            out_im.convert("RGB").save(buf, "JPEG", quality=int(jpg_policy.get("quality", 92)),
                                       subsampling=0, optimize=True)
            png_bytes = len(data) if data is not None else src_size
            if len(buf.getvalue()) <= png_bytes * (1 - float(jpg_policy.get("minSavingFraction", 0.2))):
                data = buf.getvalue()
                result["file"] = rel[:-4] + ".jpg"
    dst = os.path.join(out_root, result["file"])
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if data is None:
        shutil.copyfile(src, dst)
    else:
        with open(dst, "wb") as f:
            f.write(data)
    result["outBytes"] = os.path.getsize(dst)
    return result


def load_json(path, default):
    if path and os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    return default


def mb(n):
    return n / 1e6


def write_report(report_dir, results, decisions, record_path):
    os.makedirs(report_dir, exist_ok=True)
    rows = []
    for r in results:
        d = decisions[r["rel"]]
        rgba_in, rgba_out = r["w"] * r["h"] * 4, r["outW"] * r["outH"] * 4
        pot_in = pot(r["w"]) * pot(r["h"]) * 4
        pot_out = pot(r["outW"]) * pot(r["outH"]) * 4
        rows.append(dict(r, reason=d["reason"], need=d.get("need"), by=d.get("by"),
                         rgbaIn=rgba_in, rgbaOut=rgba_out, potIn=pot_in, potOut=pot_out))
    tot = lambda k: sum(x[k] for x in rows)
    counts = {}
    for x in rows:
        counts[x["reason"]] = counts.get(x["reason"], 0) + 1
    summary = {
        "images": len(rows),
        "diskMB": [round(mb(tot("srcBytes")), 1), round(mb(tot("outBytes")), 1)],
        "decodedRgbaMB": [round(mb(tot("rgbaIn")), 1), round(mb(tot("rgbaOut")), 1)],
        "paddedPotMB": [round(mb(tot("potIn")), 1), round(mb(tot("potOut")), 1)],
        "byReason": counts,
        "jpg": sum(1 for x in rows if x["file"].endswith(".jpg")),
    }
    with open(os.path.join(report_dir, "report.json"), "w", encoding="utf-8") as f:
        json.dump({"summary": summary, "images": rows}, f, indent=1)
    lines = ["# Texture fit report", "",
             "Usage record: `%s`. Reference: 1920x1080, closest battle zoom, largest UI scale." % record_path,
             "MB are 10^6 bytes. *Decoded* is width x height x 4; *padded* is the same at the "
             "power-of-two size the texture is uploaded at.", "",
             "| | before | after |", "|---|---:|---:|",
             "| on disk | %.1f MB | %.1f MB |" % tuple(summary["diskMB"]),
             "| decoded RGBA | %.1f MB | %.1f MB |" % tuple(summary["decodedRgbaMB"]),
             "| padded to POT | %.1f MB | %.1f MB |" % tuple(summary["paddedPotMB"]), "",
             "Images: %d. " % len(rows) + ", ".join("%s %d" % kv for kv in sorted(counts.items()))
             + ". Shipped as JPG: %d." % summary["jpg"], ""]
    saved = sorted((x for x in rows if x["rgbaOut"] < x["rgbaIn"] or x["file"] != x["rel"]),
                   key=lambda x: (x["rgbaIn"] - x["rgbaOut"]), reverse=True)
    lines += ["## Largest savings, by decoded memory", "",
              "| image | source | shipped | decoded saved | padded saved | disk saved | need | decided by |",
              "|---|---:|---:|---:|---:|---:|---:|---|"]
    for x in saved[:40]:
        lines.append("| `%s` | %dx%d | %dx%d%s | %.1f MB | %.1f MB | %.2f MB | %.3f | %s |" % (
            x["rel"], x["w"], x["h"], x["outW"], x["outH"], " jpg" if x["file"].endswith(".jpg") else "",
            mb(x["rgbaIn"] - x["rgbaOut"]), mb(x["potIn"] - x["potOut"]),
            mb(x["srcBytes"] - x["outBytes"]), x["need"] or 0, x["by"] or ""))
    unmeasured = sorted((x for x in rows if x["reason"] == "unmeasured"),
                        key=lambda x: x["rgbaIn"], reverse=True)
    lines += ["", "## Unmeasured: shipped at full size", "",
              "No run recorded a draw of these. Either nothing measured draws them yet (an engine "
              "draw, a GL pass with no hook, a screen no evidence run reaches) or they are unused. "
              "%d images, %.1f MB decoded." % (len(unmeasured), mb(sum(x["rgbaIn"] for x in unmeasured))),
              "", "| image | size | decoded |", "|---|---:|---:|"]
    for x in unmeasured:
        lines.append("| `%s` | %dx%d | %.1f MB |" % (x["rel"], x["w"], x["h"], mb(x["rgbaIn"])))
    for reason, title in (("engine-loaded", "Engine-loaded: named by data the game reads, kept"),
                          ("sliced-at-load", "Sliced at load: frames found from pixels, kept"),
                          ("needs-full", "Measured and needs every pixel"),
                          ("never-fit", "Never fit by policy")):
        group = sorted((x for x in rows if x["reason"] == reason), key=lambda x: x["rgbaIn"], reverse=True)
        lines += ["", "## %s" % title, "", "%d images, %.1f MB decoded." % (
            len(group), mb(sum(x["rgbaIn"] for x in group))), ""]
        for x in group[:60]:
            extra = " (need %.3f, %s)" % (x["need"], x["by"]) if x["need"] else ""
            lines.append("- `%s` %dx%d%s" % (x["rel"], x["w"], x["h"], extra))
        if len(group) > 60:
            lines.append("- ... %d more in report.json" % (len(group) - 60))
    with open(os.path.join(report_dir, "report.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    return summary


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mod_dir")
    ap.add_argument("dest_root")
    ap.add_argument("--record")
    ap.add_argument("--policy")
    ap.add_argument("--report")
    args = ap.parse_args()
    mod_dir = os.path.abspath(args.mod_dir)
    dest = os.path.abspath(args.dest_root)
    src_root = os.path.join(mod_dir, "graphics")
    policy = load_json(args.policy, {"margin": 1.15, "scales": [1.0], "neverFit": []})
    record = load_json(args.record, {"textures": {}}).get("textures", {})
    refs = engine_references(mod_dir)
    policy_key = hashlib.sha1(json.dumps(policy, sort_keys=True).encode()).hexdigest()[:12]
    index_path = os.path.join(dest, INDEX)
    index = load_json(index_path, {})

    jobs, decisions, results, produced = [], {}, [], set()
    for d, _, files in os.walk(src_root):
        for f in files:
            if not f.lower().endswith(".png"):
                continue
            p = os.path.join(d, f)
            rel = os.path.relpath(p, mod_dir).replace(os.sep, "/")
            with Image.open(p) as im:
                w, h = im.size
            decision = decide(rel, w, h, record.get(rel), policy, refs)
            decisions[rel] = decision
            st = os.stat(p)
            key = [st.st_mtime_ns, st.st_size, policy_key, decision]
            cached = index.get(rel)
            if cached and cached.get("key") == key and os.path.exists(os.path.join(dest, cached["result"]["file"])):
                results.append(cached["result"])
                produced.add(cached["result"]["file"])
                continue
            jobs.append((p, dest, rel, decision, policy.get("jpg", {})))
    if jobs:
        with ProcessPoolExecutor() as ex:
            for r in ex.map(convert, jobs, chunksize=2):
                results.append(r)
                produced.add(r["file"])
                st = os.stat(os.path.join(mod_dir, r["rel"]))
                index[r["rel"]] = {"key": [st.st_mtime_ns, st.st_size, policy_key, decisions[r["rel"]]],
                                   "result": r}
    # A source that is gone, or now ships under another name, must not ship its old output.
    stale = 0
    graphics_out = os.path.join(dest, "graphics")
    for d, _, files in os.walk(graphics_out):
        for f in files:
            rel = os.path.relpath(os.path.join(d, f), dest).replace(os.sep, "/")
            if rel not in produced:
                os.remove(os.path.join(d, f))
                stale += 1
    index = {k: v for k, v in index.items() if k in decisions}

    manifest = {"version": 1, "textures": {}}
    for r in sorted(results, key=lambda r: r["rel"]):
        if (r["outW"], r["outH"]) != (r["w"], r["h"]) or r["file"] != r["rel"]:
            manifest["textures"][r["rel"]] = {"w": r["w"], "h": r["h"], "file": r["file"],
                                              "sw": r["outW"], "sh": r["outH"]}
    manifest_path = os.path.join(dest, MANIFEST)
    os.makedirs(os.path.dirname(manifest_path), exist_ok=True)
    with open(manifest_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(manifest, f, indent=1, sort_keys=True)
    with open(index_path, "w", encoding="utf-8") as f:
        json.dump(index, f)
    report_dir = args.report or os.path.join(os.path.dirname(dest), "reports", "texture-fit")
    summary = write_report(report_dir, results, decisions, args.record)
    print("optimize_pngs: %d images (%d rebuilt, %d stale removed); %d fitted or re-encoded; "
          "disk %.1f -> %.1f MB, decoded %.1f -> %.1f MB, padded %.1f -> %.1f MB; report %s"
          % (len(results), len(jobs), stale, len(manifest["textures"]),
             summary["diskMB"][0], summary["diskMB"][1], summary["decodedRgbaMB"][0],
             summary["decodedRgbaMB"][1], summary["paddedPotMB"][0], summary["paddedPotMB"][1],
             os.path.join(report_dir, "report.md")))


if __name__ == "__main__":
    main()
