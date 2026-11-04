#!/usr/bin/env bash
# Builds (first run only) and starts the Personal Finance Tracker.
set -e
cd "$(dirname "$0")"
if [ ! -f target/personal-finance-tracker.jar ]; then
  echo "Building the project (first run only)..."
  mvn -q -DskipTests package
fi
exec java -jar target/personal-finance-tracker.jar "$@"
