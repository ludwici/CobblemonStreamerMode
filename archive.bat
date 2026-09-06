@echo off
setlocal

cd /d "%~dp0"

set "ARCHIVE=project.zip"
set "LIST=%TEMP%\git_archive_%RANDOM%_%RANDOM%.txt"

echo Creating %ARCHIVE%...

rem Get all tracked + untracked files, respecting .gitignore.
rem Also exclude this BAT file and the resulting archive.
git ls-files -co --exclude-standard ^
    | findstr /V /X /C:"%~nx0" ^
    | findstr /V /X /C:"%ARCHIVE%" > "%LIST%"

if errorlevel 1 (
    echo Failed to get file list.
    del "%LIST%" 2>nul
    pause
    exit /b 1
)

rem Remove previous archive if it exists
if exist "%ARCHIVE%" del "%ARCHIVE%"

rem Create ZIP archive
tar -a -c -f "%ARCHIVE%" -T "%LIST%"

if errorlevel 1 (
    echo Failed to create archive.
    del "%LIST%" 2>nul
    pause
    exit /b 1
)

del "%LIST%" 2>nul

echo.
echo Archive created:
echo %~dp0%ARCHIVE%
echo.
pause