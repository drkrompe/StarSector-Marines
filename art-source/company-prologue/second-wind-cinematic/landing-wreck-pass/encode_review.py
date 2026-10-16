"""Encode the landing and a cut against the actual preceding port excerpt."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
from PIL import Image

OUT = Path(__file__).resolve().parent
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    render = json.loads((OUT/'render-manifest.json').read_text())
    assert sha(OUT/render['scene']) == render['scene_sha256']
    assert len(render['frames']) == 192
    for record in render['frames']:
        frame = OUT/'build/frames'/f"{record['output_frame']:04d}.png"
        assert sha(frame) == record['sha256']
        with Image.open(frame) as im:
            assert im.size == (1920,1080)
    ffmpeg, ffprobe = shutil.which('ffmpeg'), shutil.which('ffprobe')
    assert ffmpeg and ffprobe
    common = ['-c:v','libx264','-crf','18','-preset','medium','-pix_fmt','yuv420p','-movflags','+faststart']
    movie = OUT/'review/jangala-damaged-landing.mp4'
    subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-y','-framerate','24',
                    '-i',str(OUT/'build/frames/%04d.png'),'-frames:v','192',*common,str(movie)],check=True)
    previous = OUT.parent/'port-refinement/review/jangala-port-refined.mp4'
    cut = OUT/'review/port-to-damaged-landing.mp4'
    subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-y','-i',str(previous),'-i',str(movie),
                    '-filter_complex','[0:v]setpts=PTS-STARTPTS[a];[1:v]setpts=PTS-STARTPTS[b];[a][b]concat=n=2:v=1:a=0[v]',
                    '-map','[v]','-an','-r','24',*common,str(cut)],check=True)
    reports = []
    for path,count,duration in ((movie,192,8),(cut,288,12)):
        probe = json.loads(subprocess.check_output([ffprobe,'-v','error','-count_frames','-select_streams','v:0',
            '-show_entries','stream=codec_name,pix_fmt,width,height,nb_read_frames,r_frame_rate:format=duration','-of','json',str(path)],text=True))
        stream=probe['streams'][0]
        assert (stream['width'],stream['height'],stream['r_frame_rate'],stream['nb_read_frames']) == (1920,1080,'24/1',str(count))
        assert stream['codec_name']=='h264' and stream['pix_fmt']=='yuv420p'
        assert abs(float(probe['format']['duration'])-duration)<.001
        subprocess.run([ffmpeg,'-v','error','-i',str(path),'-f','null','-'],check=True)
        reports.append({'path':path.relative_to(OUT).as_posix(),'sha256':sha(path),'bytes':path.stat().st_size,'probe':probe,'full_decode':'PASS'})
    for name,index in [('start',1),('poster',97),('contact',157),('end',192)]:
        shutil.copyfile(OUT/'build/frames'/f'{index:04d}.png',OUT/'review'/f'{name}.png')
    report={'render_manifest_sha256':sha(OUT/'render-manifest.json'),'previous_port_sha256':sha(previous),
            'movies':reports,'unique_render_frames':len(set(r['sha256'] for r in render['frames'])),'audio':'none'}
    (OUT/'review/media-validation.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(report))
if __name__=='__main__':
    main()
