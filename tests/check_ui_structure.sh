#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
UI="$ROOT/app/src/main/java/com/ax12/strictguard/MainActivity.java"
for token in \
  'buildDashboardPage(pageContent(dashboard))' \
  'buildDevicesPage(pageContent(devices))' \
  'buildProtectionPage(pageContent(protection))' \
  'buildSettingsPage(pageContent(settings))' \
  'selectTab(tab)' \
  'updateDashboard()' \
  'this::refreshInventory' \
  'this::refreshDeviceControlDiagnostics' \
  'this::startGuard' \
  'this::stopGuard' \
  'this::applySelectionToRouter' \
  'this::probeParentalControls' \
  'this::testConnection'; do
  grep -Fq "$token" "$UI" || { echo "FAIL: missing UI feature hook: $token"; exit 1; }
done
printf '%s\n' 'PASS: 13 dashboard/navigation/feature-preservation checks'
