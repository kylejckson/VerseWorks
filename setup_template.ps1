Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-PythonCommand {
    if (Test-Path '.venv/Scripts/python.exe') {
        return @('.venv/Scripts/python.exe')
    }

    $candidates = @(
        @('py', '-3.13'),
        @('py', '-3'),
        @('python')
    )

    foreach ($candidate in $candidates) {
        $command = $candidate[0]
        try {
            Get-Command $command | Out-Null
            return $candidate
        }
        catch {
        }
    }

    throw 'Python 3 was not found. Install Python 3.13+ and rerun setup.'
}

function Ensure-Java21 {
    $javaCommand = 'java'
    if ($env:JAVA_HOME) {
        $javaFromHome = Join-Path $env:JAVA_HOME 'bin/java.exe'
        if (Test-Path $javaFromHome) {
            $javaCommand = $javaFromHome
        }
    }

    try {
        $versionOutput = & $javaCommand -version 2>&1 | Out-String
    }
    catch {
        Write-Warning 'Java was not found on PATH and JAVA_HOME is unset. If VS Code is configured with Java 21 internally, continue and verify Java import/run targets inside VS Code.'
        return
    }

    if ($versionOutput -notmatch 'version "21(\.|")' -and $versionOutput -notmatch 'openjdk version "21(\.|")') {
        Write-Warning "Java 21 was not detected from PATH/JAVA_HOME. Current output:`n$versionOutput"
        Write-Warning 'If VS Code is configured with Java 21 internally, continue and confirm the project imports and Forge run targets appear.'
        return
    }

    Write-Host 'Java check passed:'
    Write-Host $versionOutput.Trim()
}

function Ensure-Venv {
    if (-not (Test-Path '.venv/Scripts/python.exe')) {
        $pythonCommand = Resolve-PythonCommand
        $pythonExe = $pythonCommand[0]
        $pythonArgs = @()
        if ($pythonCommand.Length -gt 1) {
            $pythonArgs = $pythonCommand[1..($pythonCommand.Length - 1)]
        }

        Write-Host 'Creating .venv...'
        & $pythonExe @pythonArgs -m venv .venv
    }

    if (-not (Test-Path '.venv/Scripts/python.exe')) {
        throw '.venv creation failed.'
    }
}

Write-Host 'Verifying Java 21...'
Ensure-Java21

Write-Host 'Ensuring local Python environment...'
Ensure-Venv

Write-Host 'Installing Python requirements into .venv...'
& '.venv/Scripts/python.exe' -m pip install --upgrade pip
& '.venv/Scripts/python.exe' -m pip install -r requirements.txt

Write-Host 'Setup complete.'
Write-Host 'Next checks:'
Write-Host '  1. Confirm Java language support finished indexing in VS Code.'
Write-Host '  2. Confirm VS Code exposed the Forge run targets through Gradle/Slime Launcher.'
Write-Host '  3. Start runClient from VS Code when the import is ready.'
Write-Host '  4. Use `.venv/Scripts/python.exe gen_textures.py ...` for placeholder PNG assets.'