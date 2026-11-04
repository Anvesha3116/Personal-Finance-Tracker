@echo off
cd /d "%~dp0"
if not exist target\personal-finance-tracker.jar (
  echo Building the project ^(first run only^)...
  call mvn -q -DskipTests package || goto :fail
)
java -jar target\personal-finance-tracker.jar %*
goto :eof
:fail
echo Build failed. Make sure JDK 17+ and Maven are installed and on your PATH.
pause
