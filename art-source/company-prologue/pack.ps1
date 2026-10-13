# Pack the full 3:2 bridge composition; retain its original beside this script.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$destination = Join-Path $repository 'mod/graphics/illustrations/company'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$image = [System.Drawing.Image]::FromFile((Join-Path $PSScriptRoot 'second-wind.master.png'))
$preview = [System.Drawing.Bitmap]::new(1200, 800)
$graphics = [System.Drawing.Graphics]::FromImage($preview)
$parameters = [System.Drawing.Imaging.EncoderParameters]::new(1)
$attributes = [System.Drawing.Imaging.ImageAttributes]::new()
try {
    if ($image.Width * 2 -ne $image.Height * 3) { throw 'Expected 3:2 master; refusing to distort or crop.' }
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $attributes.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
    $graphics.DrawImage($image, [System.Drawing.Rectangle]::new(0, 0, 1200, 800),
        0, 0, $image.Width, $image.Height, [System.Drawing.GraphicsUnit]::Pixel, $attributes)
    $parameters.Param[0] = [System.Drawing.Imaging.EncoderParameter]::new([System.Drawing.Imaging.Encoder]::Quality, [long]92)
    $codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object MimeType -eq 'image/jpeg'
    $preview.Save((Join-Path $destination 'second-wind.jpg'), $codec, $parameters)
} finally {
    $attributes.Dispose(); $parameters.Dispose(); $graphics.Dispose(); $preview.Dispose(); $image.Dispose()
}
