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
  'renderLiveTraffic()' \
  'LiveTrafficStore.isMonitoringEnabled(this)' \
  'client.readTrafficMonitorReadOnly()' \
  'LiveTrafficStore.saveOnlineSnapshot(this, snapshot.devices, System.currentTimeMillis())'; do
  grep -Fq "$token" "$UI" || { echo "FAIL: missing UI feature hook: $token"; exit 1; }
done
if grep -Eq 'TrafficUsageStore|renderTrafficUsage|30 يوماً|آخر 7 أيام|إحصائيات الاستهلاك' "$UI"; then
  echo 'FAIL: cumulative traffic UI remains in MainActivity.java'
  exit 1
fi
printf '%s\n' 'PASS: dashboard/navigation/feature-preservation and live-traffic hooks'
