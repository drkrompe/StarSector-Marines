"""Encode and verify the scene-bound native render set."""
from pathlib import Path
import json,hashlib,subprocess,shutil
from PIL import Image
OUT=Path(__file__).resolve().parent
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
manifest=json.loads((OUT/'render-manifest.json').read_text())
assert sha(OUT/manifest['scene'])==manifest['scene_sha256']
frames=OUT/'build/frames'
assert len(manifest['frames'])==192
for record in manifest['frames']:
    p=frames/f"{record['output_frame']:04d}.png"
    assert sha(p)==record['sha256'],p
    assert Image.open(p).size==(1920,1080)
movie=OUT/'review/jangala-landing-refined.mp4'
ffmpeg=shutil.which('ffmpeg');ffprobe=shutil.which('ffprobe');assert ffmpeg and ffprobe
subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-y','-framerate','24','-start_number','1','-i',str(frames/'%04d.png'),'-frames:v','192','-c:v','libx264','-crf','18','-preset','medium','-pix_fmt','yuv420p','-movflags','+faststart',str(movie)],check=True)
info=json.loads(subprocess.check_output([ffprobe,'-v','error','-count_frames','-select_streams','v:0','-show_entries','stream=codec_name,pix_fmt,width,height,nb_read_frames,r_frame_rate:format=duration','-of','json',str(movie)],text=True))
s=info['streams'][0]
assert (s['width'],s['height'],s['r_frame_rate'],s['nb_read_frames'],s['pix_fmt'],s['codec_name'])==(1920,1080,'24/1','192','yuv420p','h264')
assert abs(float(info['format']['duration'])-8)<.001
subprocess.run([ffmpeg,'-v','error','-i',str(movie),'-f','null','-'],check=True)
for name,index in [('start',1),('poster',97),('contact',157),('end',192)]:
    shutil.copyfile(frames/f'{index:04d}.png',OUT/'review'/f'{name}.png')
report={'movie':'review/'+movie.name,'sha256':sha(movie),'bytes':movie.stat().st_size,'probe':info,
 'render_manifest_sha256':sha(OUT/'render-manifest.json'),'unique_source_frames':len(set(r['sha256'] for r in manifest['frames'])),
 'full_decode':'PASS','audio':'none','visual_review':'Playback and sampled images recorded separately in review/motion-review.json.'}
(OUT/'review/media-validation.json').write_text(json.dumps(report,indent=2)+'\n', encoding='utf-8', newline='\n')
print(json.dumps(report))
