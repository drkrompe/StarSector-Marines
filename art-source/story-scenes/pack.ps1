# Downsample full-frame masters to 1200x400 for runtime. Masters remain untouched.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$destination = Join-Path $repository 'mod/graphics/illustrations/wayline'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object MimeType -eq 'image/jpeg'
foreach ($master in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.master.png' | Sort-Object Name) {
    $name = $master.Name.Replace('.master.png', '')
    $image = [System.Drawing.Image]::FromFile($master.FullName)
    $parameters = [System.Drawing.Imaging.EncoderParameters]::new(1)
    $parameters.Param[0] = [System.Drawing.Imaging.EncoderParameter]::new([System.Drawing.Imaging.Encoder]::Quality, [long]92)
    $preview = [System.Drawing.Bitmap]::new(1200, 400)
    $graphics = [System.Drawing.Graphics]::FromImage($preview)
    $attributes = [System.Drawing.Imaging.ImageAttributes]::new()
    try {
        if ($image.Width -ne 3 * $image.Height) { throw "$name must have a 3:1 master; refusing to distort or crop it." }
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $attributes.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
        $graphics.DrawImage($image, [System.Drawing.Rectangle]::new(0, 0, 1200, 400),
            0, 0, $image.Width, $image.Height, [System.Drawing.GraphicsUnit]::Pixel, $attributes)
        $preview.Save((Join-Path $destination "$name.jpg"), $codec, $parameters)
    }
    finally {
        $attributes.Dispose(); $graphics.Dispose(); $preview.Dispose()
        $parameters.Dispose(); $image.Dispose()
    }
}
