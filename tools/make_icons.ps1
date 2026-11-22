# Generates the in-app avatar, launcher icons and adaptive-icon layers from assets/art/krishna_avatar.png.
# Run from anywhere: powershell -ExecutionPolicy Bypass -File tools\make_icons.ps1
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$src = Join-Path $root "assets\art\krishna_avatar.png"
$res = Join-Path $root "android\app\src\main\res"

function Save-Png([System.Drawing.Bitmap]$bmp, [string]$path) {
    $dir = Split-Path -Parent $path
    New-Item -ItemType Directory -Force $dir | Out-Null
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function Resize-Square([System.Drawing.Image]$img, [int]$size, [double]$zoom, [double]$yShift) {
    # zoom > 1 crops toward the face; yShift moves the crop window up (negative) or down (positive) as a fraction of height.
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $cropW = $img.Width / $zoom
    $cropH = $img.Height / $zoom
    $cx = ($img.Width - $cropW) / 2
    $cy = ($img.Height - $cropH) / 2 + ($yShift * $img.Height)
    $srcRect = New-Object System.Drawing.RectangleF $cx, $cy, $cropW, $cropH
    $dstRect = New-Object System.Drawing.RectangleF 0, 0, $size, $size
    $g.DrawImage($img, $dstRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    return $bmp
}

function Circle-Mask([System.Drawing.Bitmap]$square) {
    $size = $square.Width
    $out = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse(0, 0, $size, $size)
    $g.SetClip($path)
    $g.DrawImage($square, 0, 0, $size, $size)
    $g.Dispose()
    return $out
}

function Foreground-Layer([System.Drawing.Image]$img, [int]$size) {
    # Adaptive icon foreground: 108dp canvas, the face sits inside the 66dp safe circle.
    $out = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.Clear([System.Drawing.Color]::FromArgb(255, 17, 17, 21))
    $inner = [int]($size * 0.72)
    $off = [int](($size - $inner) / 2)
    $face = Resize-Square $img $inner 1.6 -0.12
    $circle = Circle-Mask $face
    # thin gold ring
    $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 207, 168, 107)), ([Math]::Max(2, $size / 54))
    $g.DrawImage($circle, $off, $off, $inner, $inner)
    $g.DrawEllipse($pen, $off, $off, $inner, $inner)
    $g.Dispose()
    return $out
}

$img = [System.Drawing.Image]::FromFile($src)

# In-app avatar (used in chat header, bubbles, onboarding). nodpi so it is never scaled by density buckets.
Save-Png (Resize-Square $img 768 1.0 0.0) (Join-Path $res "drawable-nodpi\krishna_avatar.png")
Save-Png (Resize-Square $img 1024 1.0 0.0) (Join-Path $res "drawable-nodpi\krishna_portrait.png")

$densities = @{ "mdpi" = 1.0; "hdpi" = 1.5; "xhdpi" = 2.0; "xxhdpi" = 3.0; "xxxhdpi" = 4.0 }
foreach ($d in $densities.Keys) {
    $scale = $densities[$d]
    $legacy = [int](48 * $scale)
    $fg = [int](108 * $scale)
    $square = Resize-Square $img $legacy 1.5 -0.12
    Save-Png $square (Join-Path $res "mipmap-$d\ic_launcher.png")
    Save-Png (Circle-Mask $square) (Join-Path $res "mipmap-$d\ic_launcher_round.png")
    Save-Png (Foreground-Layer $img $fg) (Join-Path $res "mipmap-$d\ic_launcher_foreground.png")
}
$img.Dispose()
"icons written under $res"
