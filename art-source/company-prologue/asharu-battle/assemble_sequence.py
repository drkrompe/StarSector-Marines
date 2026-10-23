"""Shot MP4s -> one sequence MP4, in the order given (the render queue's last job of a lit animatic).

    python assemble_sequence.py <out.mp4> <shot.mp4> [<shot.mp4> ...] [--wait-minutes 30] [--ffmpeg <path>]

The shots are assemble_shot.py's, all at one size, rate and encoding, so they join without
re-encoding. They may have been assembled on the other machine; this waits (up to --wait-minutes)
until Drive has every one here. Exits 1 if any are still missing, naming them.
"""
import subprocess
import sys
import tempfile
import time
from pathlib import Path


def main(argv):
    ffmpeg = argv[argv.index('--ffmpeg') + 1] if '--ffmpeg' in argv else 'ffmpeg'
    wait = float(argv[argv.index('--wait-minutes') + 1]) if '--wait-minutes' in argv else 30.0
    flags = {'--ffmpeg', '--wait-minutes'}
    plain = [a for i, a in enumerate(argv) if a not in flags and (i == 0 or argv[i - 1] not in flags)]
    out, shots = Path(plain[0]), [Path(p) for p in plain[1:]]
    deadline = time.time() + wait * 60
    while True:
        missing = [p.name for p in shots if not p.exists() or p.stat().st_size == 0]
        if not missing:
            break
        if time.time() > deadline:
            print(f'SEQUENCE: {len(missing)} of {len(shots)} shots missing: {missing}', flush=True)
            return 1
        print(f'SEQUENCE: waiting for {missing}', flush=True)
        time.sleep(60)
    with tempfile.NamedTemporaryFile('w', suffix='.txt', delete=False, encoding='utf-8') as fh:
        for p in shots:
            fh.write("file '" + str(p.resolve()).replace('\\', '/').replace("'", "'\\''") + "'\n")
        listing = fh.name
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_suffix('.partial.mp4')
    try:
        subprocess.check_call([ffmpeg, '-y', '-f', 'concat', '-safe', '0', '-i', listing, '-c', 'copy', str(tmp)])
    finally:
        Path(listing).unlink()
    tmp.replace(out)
    print(f'SEQUENCE: {len(shots)} shots -> {out}', flush=True)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
