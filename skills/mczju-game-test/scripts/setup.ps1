$ErrorActionPreference = 'Stop'
$setupArguments = $args
foreach ($name in @('python.exe', 'py.exe')) {
    $command = Get-Command $name -ErrorAction SilentlyContinue
    if ($command -and $command.Source -notlike '*WindowsApps*') {
        $python = & $command.Source -c 'import sys; print(sys.executable); sys.exit(sys.version_info < (3, 12))'
        if ($LASTEXITCODE -eq 0 -and $python -and (Test-Path -LiteralPath $python -PathType Leaf)) {
            # Run the selected interpreter directly so py.exe cannot pick another one from the shebang.
            & $python (Join-Path $PSScriptRoot 'mgc_test.py') setup @setupArguments
            exit $LASTEXITCODE
        }
    }
}

$arch = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
switch ($arch) {
    'X64' {
        $target = 'x86_64'
        $checksum = '9c22438c8f107dc88505394a87a61aab3504d33d1ce4e22eb177381e5e9b244f'
    }
    'Arm64' {
        $target = 'aarch64'
        $checksum = '2ade2f9c1b4fcb68dc0e54768ed94777516d7767fbff74e35bf04aaf40536b7a'
    }
    default { throw "No bundled Python for $arch" }
}

$cache = $env:MGC_TEST_CACHE
if (-not $cache) { $cache = Join-Path $env:LOCALAPPDATA 'mgc-game-test' }
$tag = '20261124'
$runtime = Join-Path $cache "python-$tag-$target-pc-windows-msvc"
$python = Join-Path $runtime 'python.exe'
if (-not (Test-Path -LiteralPath $python)) {
    $temporary = Join-Path $cache ([Guid]::NewGuid().ToString())
    New-Item -ItemType Directory -Force -Path $temporary | Out-Null
    try {
        $asset = "cpython-3.13.18+$tag-$target-pc-windows-msvc-install_only.tar.gz"
        $archive = Join-Path $temporary 'python.tar.gz'
        Invoke-WebRequest -UseBasicParsing -Uri "https://github.com/astral-sh/python-build-standalone/releases/download/$tag/$asset" -OutFile $archive -TimeoutSec 180
        if ((Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash -ne $checksum) { throw 'Python checksum mismatch' }
        & tar.exe -xzf $archive -C $temporary
        if ($LASTEXITCODE -ne 0) { throw 'Could not extract Python' }
        Move-Item -LiteralPath (Join-Path $temporary 'python') -Destination $runtime
    } finally {
        Remove-Item -LiteralPath $temporary -Recurse -Force
    }
}
& $python (Join-Path $PSScriptRoot 'mgc_test.py') setup @setupArguments
exit $LASTEXITCODE
