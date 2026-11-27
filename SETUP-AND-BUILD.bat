@echo off
setlocal
cd /d "%~dp0"
echo === Koreva setup ===
where node >nul 2>nul
if errorlevel 1 goto nonode
call npm install
if errorlevel 1 goto fail
if not exist android call npx cap add android
if errorlevel 1 goto fail
xcopy /E /Y /I "android-overrides\app" "android\app" >nul
call npx cap sync android
if errorlevel 1 goto fail
echo.
echo Done. Android Studio will open. Wait for Gradle to finish, then use:
echo Build ^> Generate App Bundles or APKs ^> Generate APKs
call npx cap open android
pause
exit /b 0
:nonode
echo Node.js is not installed. Install it from https://nodejs.org and run this again.
pause
exit /b 1
:fail
echo Something failed. Scroll up to read the error and send it to Claude.
pause
exit /b 1
