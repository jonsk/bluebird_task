# 注意：本文件必须保存为 UTF-8 with BOM，Windows PowerShell 5.1 依赖 BOM 才能正确解析中文。
# Slim sqlite-jdbc native libraries for the target platform.
# Runs after spring-boot:repackage; writes the shippable jar into target/dist.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File build/slim-sqlite.ps1
#   powershell ... -File build/slim-sqlite.ps1 -Platform linux -Arch x86_64
#
# Env fallbacks (used by Maven): SLIM_JAR, SLIM_VERSION, SLIM_PLATFORM, SLIM_ARCH, SLIM_OUT.

param(
    [string]$Jar = $env:SLIM_JAR,
    [string]$Version = $env:SLIM_VERSION,
    [string]$Platform = $env:SLIM_PLATFORM,
    [string]$Arch = $env:SLIM_ARCH,
    [string]$OutDir = $env:SLIM_OUT
)

$ErrorActionPreference = 'Stop'

function Fail([string]$Message) {
    Write-Host "[slim-sqlite] ERROR: $Message" -ForegroundColor Red
    exit 1
}

function Info([string]$Message) {
    Write-Host "[slim-sqlite] $Message"
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
if (-not $Jar) { $Jar = Join-Path $repoRoot 'backend\target\bluebird-task.jar' }
if (-not $OutDir) { $OutDir = Join-Path $repoRoot 'backend\target\dist' }

if (-not (Test-Path -LiteralPath $Jar)) {
    Fail "jar 不存在：$Jar（请先执行 spring-boot:repackage）"
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Fail "未找到 java，构建机需要 JDK 21（Maven 本身已依赖）"
}

# ---------- detect host ----------
$osDescription = [System.Runtime.InteropServices.RuntimeInformation]::OSDescription
$hostOs = switch -Regex ($osDescription) {
    'Windows' { 'windows'; break }
    'Linux' { 'linux'; break }
    'Darwin|macOS|OSX' { 'mac'; break }
    'FreeBSD' { 'freebsd'; break }
    default { $null }
}
if (-not $hostOs) { Fail "无法识别的构建机系统：$osDescription" }
if ($hostOs -eq 'linux' -and (Test-Path -LiteralPath '/etc/alpine-release')) {
    $hostOs = 'linux-musl'
}

$rawArch = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
$hostArch = $null
switch -Regex ($rawArch) {
    'X64|x86_64|AMD64' { $hostArch = 'x86_64'; break }
    'Arm64|aarch64|ARM64' { $hostArch = 'aarch64'; break }
}
if (-not $hostArch -and $env:PROCESSOR_ARCHITECTURE) {
    switch -Regex ($env:PROCESSOR_ARCHITECTURE) {
        'AMD64|X64' { $hostArch = 'x86_64'; break }
        'ARM64' { $hostArch = 'aarch64'; break }
    }
}
if (-not $hostArch) {
    Fail "不支持的构建机架构：$rawArch（仅支持 x86_64/amd64、aarch64/arm64）"
}

# ---------- resolve target ----------
$targetPlatform = if ($Platform -and $Platform.Trim()) { $Platform.Trim().ToLowerInvariant() } else { $hostOs }
$targetArch = if ($Arch -and $Arch.Trim()) { $Arch.Trim().ToLowerInvariant() } else { $hostArch }
switch ($targetArch) {
    'amd64' { $targetArch = 'x86_64' }
    'arm64' { $targetArch = 'aarch64' }
}

$supportedPlatforms = @('linux', 'linux-musl', 'windows', 'mac', 'freebsd')
if ($supportedPlatforms -notcontains $targetPlatform) {
    Fail "不支持的平台：$targetPlatform（可选 linux、linux-musl、windows、mac、freebsd）"
}
if (@('x86_64', 'aarch64') -notcontains $targetArch) {
    Fail "不支持的架构：$targetArch（仅支持 x86_64/amd64、aarch64/arm64）"
}

$platformDirs = @{
    'linux'      = 'Linux'
    'linux-musl' = 'Linux-Musl'
    'windows'    = 'Windows'
    'mac'        = 'Mac'
    'freebsd'    = 'FreeBSD'
}
$nativePlatform = $platformDirs[$targetPlatform]
$keepPrefix = "org/sqlite/native/$nativePlatform/$targetArch/"

$hostName = [System.Net.Dns]::GetHostName()
$isCi = $env:CI -and $env:CI -ne 'false' -and $env:CI -ne '0'
Info "host os     : $hostOs"
Info "host arch   : $rawArch -> $hostArch"
Info "hostname    : $hostName"
Info "ci          : $isCi"
Info "target      : $targetPlatform / $targetArch"
Info "keep        : $keepPrefix"

if ($isCi -and ($targetPlatform -ne 'linux' -or $targetArch -ne 'x86_64')) {
    Fail "CI 构建目标必须是 linux/x86_64，当前检测到 $targetPlatform/$targetArch；请检查 runner 架构或显式传平台参数"
}

# ---------- rewrite nested sqlite jar ----------
$javaArgs = @(
    (Join-Path $scriptDir 'SlimSqlite.java'),
    $Jar,
    $OutDir,
    "$Version",
    $targetPlatform,
    $targetArch
)
$outName = (& java @javaArgs) | Select-Object -Last 1
if ($LASTEXITCODE -ne 0 -or -not $outName) {
    Fail "重写 jar 失败（SlimSqlite.java 退出码 $LASTEXITCODE）"
}
$outName = "$outName".Trim()
$outPath = Join-Path $OutDir $outName

$aliasPath = Join-Path $OutDir 'bluebird-task.jar'
if (Test-Path -LiteralPath $aliasPath) { Remove-Item -Force -LiteralPath $aliasPath }
try {
    New-Item -ItemType SymbolicLink -Path $aliasPath -Target $outName -ErrorAction Stop | Out-Null
} catch {
    Copy-Item -Force -LiteralPath $outPath -Destination $aliasPath
}

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $outPath).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$outPath.sha256" -Value "$hash  $outName" -Encoding ASCII

$beforeMb = [math]::Round((Get-Item -LiteralPath $Jar).Length / 1MB, 1)
$afterMb = [math]::Round((Get-Item -LiteralPath $outPath).Length / 1MB, 1)
Info "output      : $outPath"
Info "alias       : $aliasPath"
Info "size        : $beforeMb MB -> $afterMb MB"
Info "sha256      : $hash"
