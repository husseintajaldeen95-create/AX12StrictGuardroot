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
  'this::testConnection' \
  'this::probeTrafficMonitor' \
  'renderTrafficUsage()' \
  'TrafficUsageStore.isSamplingEnabled(this)' \
  'client.readTrafficMonitorReadOnly()'; do
  grep -Fq "$token" "$UI" || { echo "FAIL: missing UI feature hook: $token"; exit 1; }
done
printf '%s\n' 'PASS: dashboard/navigation/feature-preservation and traffic-monitor hooks'
