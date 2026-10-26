$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
Set-Location $PSScriptRoot

# Load .env without overriding variables already present in the environment.
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) { continue }

        $separator = $trimmed.IndexOf("=")
        if ($separator -lt 1) { continue }

        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()

        # Remove matching outer quotes, if present.
        if ($value.Length -ge 2) {
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }

        if (-not (Test-Path "Env:$name")) {
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    throw "JWT_SECRET is missing from .env or the environment."
}
if ([string]::IsNullOrWhiteSpace($env:SONAR_TOKEN)) {
    throw "SONAR_TOKEN is missing from .env or the environment."
}

$wrapper = Join-Path $PSScriptRoot "mvnw.cmd"
if (-not (Test-Path $wrapper)) {
    throw "Maven wrapper mvnw.cmd was not found in the repository root."
}

$hostUrl = if ($env:SONAR_HOST_URL) { $env:SONAR_HOST_URL } else { "http://localhost:9000" }
$projectKey = if ($env:SONAR_PROJECT_KEY) { $env:SONAR_PROJECT_KEY } else { "Auth-System" }
$projectName = if ($env:SONAR_PROJECT_NAME) { $env:SONAR_PROJECT_NAME } else { "Auth System" }

Write-Host "Running tests and SonarQube analysis..."
& $wrapper -B clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar `
    "-Dsonar.projectKey=$projectKey" `
    "-Dsonar.projectName=$projectName" `
    "-Dsonar.host.url=$hostUrl" `
    "-Dsonar.token=$($env:SONAR_TOKEN)"

if ($LASTEXITCODE -ne 0) {
    throw "Maven build or SonarQube analysis failed with exit code $LASTEXITCODE."
}

Write-Host "SonarQube analysis completed successfully."
