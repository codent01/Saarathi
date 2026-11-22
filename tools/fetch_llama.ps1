# Fetches a pinned llama.cpp checkout into android/app/src/main/cpp/llama.cpp.
# Usage: powershell -ExecutionPolicy Bypass -File tools\fetch_llama.ps1 [-Tag b6700]
param([string]$Tag = "b6700")

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$dest = Join-Path $root "android\app\src\main\cpp\llama.cpp"
$git = (Get-Command git -ErrorAction SilentlyContinue).Source
if (-not $git) { $git = "C:\Program Files\Git\cmd\git.exe" }

if (Test-Path (Join-Path $dest "CMakeLists.txt")) {
    "llama.cpp already present at $dest"
    & $git -C $dest describe --tags --always
    exit 0
}
New-Item -ItemType Directory -Force (Split-Path -Parent $dest) | Out-Null
& $git clone --depth 1 --branch $Tag https://github.com/ggml-org/llama.cpp.git $dest
if ($LASTEXITCODE -ne 0) { throw "git clone failed" }
"fetched llama.cpp $Tag into $dest"
