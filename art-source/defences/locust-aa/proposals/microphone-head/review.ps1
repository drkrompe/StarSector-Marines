$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$source = [System.Drawing.Bitmap]::FromFile((Join-Path $PSScriptRoot 'shape-views.png'))
if ($source.Width -ne 1920 -or $source.Height -ne 1080) { throw 'Render must be 1920x1080.' }
$sheet = [System.Drawing.Bitmap]::new(1920,1080)
$g = [System.Drawing.Graphics]::FromImage($sheet)
$title = [System.Drawing.Font]::new('Segoe UI',30)
$label = [System.Drawing.Font]::new('Segoe UI',19)
$small = [System.Drawing.Font]::new('Segoe UI',16)
try {
    $g.Clear([System.Drawing.Color]::FromArgb(27,34,36))
    $g.DrawImage($source,[System.Drawing.Rectangle]::new(0,0,1920,1080),0,0,1920,1080,[System.Drawing.GraphicsUnit]::Pixel)
    $g.DrawString('LOCUST / ELONGATED ROCKET HEAD', $title, [System.Drawing.Brushes]::WhiteSmoke,55,35)
    $g.DrawString('Blender geometry only - awaiting shape review before ImageGen', $small, [System.Drawing.Brushes]::LightGray,58,97)
    $g.DrawString('OVERHEAD / LONGER TUBE BODY', $label, [System.Drawing.Brushes]::WhiteSmoke,100,184)
    $g.DrawString('ANGLED / SIDE CLIPS', $label, [System.Drawing.Brushes]::WhiteSmoke,740,184)
    $g.DrawString('SIDE / ELEVATION AXLE', $label, [System.Drawing.Brushes]::WhiteSmoke,1390,184)
    $g.DrawString('70% longer; same head diameter', $small, [System.Drawing.Brushes]::LightGray,155,865)
    $g.DrawString('Both arms meet the head at its axle', $small, [System.Drawing.Brushes]::LightGray,730,865)
    $g.DrawString('Limited tilt; floor turns in yaw', $small, [System.Drawing.Brushes]::LightGray,1400,865)
    $g.DrawString('Long launch tubes extend ahead of and behind the side axle; rounded port face and gimbal retained.', $label, [System.Drawing.Brushes]::WhiteSmoke,55,970)
    $g.DrawString('Shown at 20 degrees. Proposed elevation range 0-25 degrees. Geometry proposal only; no ImageGen pass.', $small, [System.Drawing.Brushes]::LightGray,58,1022)
    $sheet.Save((Join-Path $PSScriptRoot 'review.png'),[System.Drawing.Imaging.ImageFormat]::Png)
} finally { $source.Dispose(); $sheet.Dispose(); $g.Dispose(); $title.Dispose(); $label.Dispose(); $small.Dispose() }
