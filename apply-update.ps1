$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

$mainActivity = Join-Path $root "app\src\main\java\io\betnet\smssender\MainActivity.java"
$gradleFile   = Join-Path $root "app\build.gradle.kts"
$resRoot      = Join-Path $root "app\src\main\res"
$updateRoot   = Join-Path $root "update-files"

if (!(Test-Path $mainActivity)) {
    Write-Host "ERROR: This update must be extracted in the root of the betnetsms repository." -ForegroundColor Red
    Write-Host "MainActivity.java was not found." -ForegroundColor Red
    exit 1
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# 1) Disable the forced notification-access blocking dialog.
$content = [System.IO.File]::ReadAllText($mainActivity)
$before = $content
$content = [regex]::Replace($content, '\s*enforceRequiredAccess\(\);', '')
[System.IO.File]::WriteAllText($mainActivity, $content, $utf8NoBom)

# 2) Update version.
if (Test-Path $gradleFile) {
    $gradle = [System.IO.File]::ReadAllText($gradleFile)
    $gradle = [regex]::Replace($gradle, 'versionCode\s*=\s*\d+', 'versionCode = 161')
    $gradle = [regex]::Replace($gradle, 'versionName\s*=\s*"[^"]+"', 'versionName = "1.6.1"')
    [System.IO.File]::WriteAllText($gradleFile, $gradle, $utf8NoBom)
}

# Replace visible version labels without touching logic.
Get-ChildItem $resRoot -Recurse -Filter *.xml -ErrorAction SilentlyContinue | ForEach-Object {
    $xml = [System.IO.File]::ReadAllText($_.FullName)
    $newXml = $xml.Replace("V 1.6", "V 1.6.1").Replace("v1.6", "v1.6.1")
    if ($newXml -ne $xml) {
        [System.IO.File]::WriteAllText($_.FullName, $newXml, $utf8NoBom)
    }
}

# 3) Install new vector/SVG launcher icon resources.
$targets = @(
    "app\src\main\res\drawable\ic_launcher_background.xml",
    "app\src\main\res\drawable\ic_launcher_foreground.xml",
    "app\src\main\res\drawable\ic_launcher_monochrome.xml",
    "app\src\main\res\drawable\ic_launcher_legacy.xml",
    "app\src\main\res\mipmap-anydpi\ic_launcher.xml",
    "app\src\main\res\mipmap-anydpi\ic_launcher_round.xml",
    "app\src\main\res\mipmap-anydpi-v26\ic_launcher.xml",
    "app\src\main\res\mipmap-anydpi-v26\ic_launcher_round.xml",
    "logo\betnet_sms_sender_logo.svg"
)

foreach ($relative in $targets) {
    $src = Join-Path $updateRoot $relative
    $dst = Join-Path $root $relative
    $dstDir = Split-Path -Parent $dst
    New-Item -ItemType Directory -Force -Path $dstDir | Out-Null
    Copy-Item -Force $src $dst
}

Write-Host ""
Write-Host "Betnet SMS Sender V1.6.1 update applied successfully." -ForegroundColor Green
Write-Host "- Forced notification access dialog disabled."
Write-Host "- New Grok-style blue mail vector icon installed."
Write-Host "- Adaptive, round, monochrome and legacy icons installed."
Write-Host "- Version updated to 1.6.1."
Write-Host ""
Write-Host "Now open GitHub Desktop, Commit to main, then Push origin." -ForegroundColor Cyan
