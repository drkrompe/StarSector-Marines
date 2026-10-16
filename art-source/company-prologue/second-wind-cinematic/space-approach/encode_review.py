"""Encode the finite silent review and record actual media properties."""
from pathlib import Path
import subprocess,json,hashlib
OUT=Path(__file__).resolve().parent
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
manifest_path=OUT/'render-manifest.json';manifest=json.loads(manifest_path.read_text())
assert sha(OUT/'space-approach.blend')==manifest['scene_sha256']
assert len(manifest['frames'])==96
assert [r['frame'] for r in manifest['frames']]==list(range(1,97))
assert len({r['sha256'] for r in manifest['frames']})==96
for row in manifest['frames']:
    assert sha(OUT/'build/frames'/f"{row['frame']:04d}.png")==row['sha256']
target=OUT/'review/jangala-space-approach.mp4'
subprocess.run(['ffmpeg','-y','-framerate','24','-i',str(OUT/'build/frames/%04d.png'),'-frames:v','96','-c:v','libx264','-crf','17','-preset','slow','-pix_fmt','yuv420p','-an','-movflags','+faststart',str(target)],check=True)
probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(target)]))
video=next(s for s in probe['streams'] if s['codec_type']=='video')
assert (video['width'],video['height'],video['nb_frames'],video['r_frame_rate'])==(1920,1080,'96','24/1')
assert not any(s['codec_type']=='audio' for s in probe['streams'])
assert abs(float(video['duration'])-4)<.0001
subprocess.run(['ffmpeg','-v','error','-xerror','-i',str(target),'-f','null','-'],check=True)
(OUT/'media-manifest.json').write_text(json.dumps({'movie':target.name,'bytes':target.stat().st_size,'sha256':sha(target),'render_manifest_sha256':sha(manifest_path),'normal_decode':'strict xerror passed','verified_unique_source_frames':96,'probe':probe},indent=2)+'\n',newline='\n')
