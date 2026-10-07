@echo off
setlocal EnableDelayedExpansion
REM ============================================================
REM  Secount Windows app - the SAME shared UI as the Android APK.
REM  Builds Secount.jar (no install) + Secount.exe (installer).
REM ============================================================
call "%~dp0android\tools.bat"
if errorlevel 1 exit /b 1

call "!GRADLE_HOME!\bin\gradle.bat" -p "%~dp0android" :desktopApp:packageUberJarForCurrentOS :desktopApp:packageExe
if errorlevel 1 (
  echo DESKTOP BUILD FAILED
  exit /b 1
)
for /f "delims=" %%F in ('powershell -NoProfile -Command "Get-ChildItem '%~dp0android\desktopApp\build\compose\jars\*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName"') do copy /y "%%F" "%~dp0Secount.jar" >nul
:jar_copied
if not exist "%~dp0Secount.jar" (
  echo DESKTOP JAR WAS NOT PRODUCED
  exit /b 1
)
for /f "delims=" %%F in ('powershell -NoProfile -Command "Get-ChildItem '%~dp0android\desktopApp\build\compose\binaries\main\exe\*.exe' | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName"') do copy /y "%%F" "%~dp0Secount.exe" >nul
:exe_copied
if not exist "%~dp0Secount.exe" (
  echo WINDOWS EXE WAS NOT PRODUCED
  exit /b 1
)
echo.
echo BUILD OK - Secount.jar (same UI as the Android app) + Secount.exe installer.
