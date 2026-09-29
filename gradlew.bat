@echo off
setlocal enabledelayedexpansion
set VER=9.6.0
set SHA256=bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set CACHE=%GRADLE_USER_HOME%\wrapper\dists\aquawiz-gradle-%VER%
set DIST=%CACHE%\gradle-%VER%
set ZIP=%CACHE%\gradle-%VER%-bin.zip

if not exist "%DIST%\bin\gradle.bat" (
  if not exist "%CACHE%" mkdir "%CACHE%"
  if not exist "%ZIP%" powershell -NoProfile -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%VER%-bin.zip' -OutFile '%ZIP%'"
  for /f "tokens=*" %%a in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 '%ZIP%').Hash.ToLower()"') do set ACTUAL=%%a
  if /I not "!ACTUAL!"=="%SHA256%" (
    echo Gradle archive checksum mismatch.
    del /q "%ZIP%"
    exit /b 1
  )
  powershell -NoProfile -Command "Expand-Archive -Force '%ZIP%' '%CACHE%'"
)

call "%DIST%\bin\gradle.bat" -p "%~dp0" %*
