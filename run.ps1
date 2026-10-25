$ErrorActionPreference = "Stop"

Set-Location $PSScriptRoot

$IntervalSeconds = 1
$ServerProcess = $null

# Load .env without overriding existing environment variables.
$envFile = Join-Path $PSScriptRoot ".env"

if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        $line = $line.Trim()

        if (-not $line -or $line.StartsWith("#")) {
            continue
        }

        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            $name = $Matches[1]
            $value = $Matches[2].Trim()

            if (
                $value.Length -ge 2 -and
                (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                 ($value.StartsWith("'") -and $value.EndsWith("'")))
            ) {
                $value = $value.Substring(1, $value.Length - 2)
            }

            if (-not (Test-Path "Env:$name")) {
                Set-Item -Path "Env:$name" -Value $value
            }
        }
    }
}

if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    Write-Error "JWT_SECRET is missing from .env or the environment."
    exit 1
}

$mavenWrapper = Join-Path $PSScriptRoot "mvnw.cmd"

if (-not (Test-Path $mavenWrapper)) {
    Write-Error "Maven wrapper mvnw.cmd was not found."
    exit 1
}

function Get-SourceSnapshot {
    $files = @()

    if (Test-Path "pom.xml") {
        $files += Get-Item "pom.xml"
    }

    if (Test-Path "src") {
        $files += Get-ChildItem "src" -File -Recurse |
            Where-Object {
                $_.Extension -in @(
                    ".java", ".properties", ".yml",
                    ".yaml", ".xml", ".html", ".css", ".js"
                )
            }
    }

    return (
        $files |
            Sort-Object FullName |
            ForEach-Object {
                "$($_.FullName)|$($_.LastWriteTimeUtc.Ticks)|$($_.Length)"
            }
    ) -join "`n"
}

function Stop-Application {
    if ($null -ne $script:ServerProcess) {
        $processId = $script:ServerProcess.Id

        try {
            & taskkill.exe /PID $processId /T /F 2>$null | Out-Null
        }
        catch {
            # The process may already have exited.
        }

        $script:ServerProcess = $null
    }
}

function Start-Application {
    Write-Host "`nStarting Spring Boot..."

    $script:ServerProcess = Start-Process `
        -FilePath $env:ComSpec `
        -ArgumentList @("/d", "/s", "/c", "`"`"$mavenWrapper`" spring-boot:run`"") `
        -WorkingDirectory $PSScriptRoot `
        -NoNewWindow `
        -PassThru

    Start-Sleep -Seconds 2

    if ($script:ServerProcess.HasExited) {
        Write-Warning "Application exited during startup."
        $script:ServerProcess = $null
        return $false
    }

    Write-Host "Application started. PID: $($script:ServerProcess.Id)"
    return $true
}

try {
    while ($true) {
        Write-Host "`nBuilding application..."

        & $mavenWrapper -B clean install

        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Build failed. Waiting for source changes."
            $previous = Get-SourceSnapshot

            while ($true) {
                Start-Sleep -Seconds $IntervalSeconds
                $current = Get-SourceSnapshot

                if ($current -ne $previous) {
                    break
                }
            }

            continue
        }

        if (-not (Start-Application)) {
            Start-Sleep -Seconds 2
            continue
        }

        $previous = Get-SourceSnapshot
        Write-Host "Watching source files. Press Ctrl+C to stop."

        while ($true) {
            Start-Sleep -Seconds $IntervalSeconds

            if (
                $null -eq $script:ServerProcess -or
                $script:ServerProcess.HasExited
            ) {
                Write-Warning "Application exited. Restarting..."
                Stop-Application
                break
            }

            $current = Get-SourceSnapshot

            if ($current -ne $previous) {
                Write-Host "`nSource changes detected."
                Stop-Application
                break
            }
        }
    }
}
finally {
    Stop-Application
}