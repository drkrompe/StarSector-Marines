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
    $g.DrawString('LOCUST / PROPOSED LAUNCHER SHAPE', $title, [System.Drawing.Brushes]::WhiteSmoke,55,35)
    $g.DrawString('Blender geometry only - awaiting shape review before ImageGen', $small, [System.Drawing.Brushes]::LightGray,58,97)
    $g.DrawString('OVERHEAD / GAME VIEW', $label, [System.Drawing.Brushes]::WhiteSmoke,260,184)
    $g.DrawString('ANGLED / SHAPE INSPECTION', $label, [System.Drawing.Brushes]::WhiteSmoke,1135,184)
    $g.DrawString('Rounded microphone-style head; small bores all run forward in parallel.', $label, [System.Drawing.Brushes]::WhiteSmoke,55,970)
    $g.DrawString('Proposed 25-degree elevation. Port count and proportions are adjustable. No finished texture or ground pedestal shown.', $small, [System.Drawing.Brushes]::LightGray,58,1022)
    $sheet.Save((Join-Path $PSScriptRoot 'review.png'),[System.Drawing.Imaging.ImageFormat]::Png)
} finally { $source.Dispose(); $sheet.Dispose(); $g.Dispose(); $title.Dispose(); $label.Dispose(); $small.Dispose() }
