# AX12StrictGuardroot — 0.2.7-live-network-status

Latest source update: adds a manual read-only diagnostic for four candidate Smart Network/Patrol forms (`patrol_enable`, `patrol_limit`, `patrol_devices`, and `patrol_owner_list`). The app reports whether each endpoint responds and whether schedule-related fields are returned. It sends only `read`/`load` operations and does not modify Parental Controls or any router setting. These candidate forms are not yet verified against the user's firmware; see `PARENTAL_CONTROL_DIAGNOSTIC_AR.md`, `PROJECT_STATUS.md`, and `CHANGELOG_AR.md`.

Important: a globally administered MAC is not proof of factory authenticity and can still be spoofed. Sticky restart does not survive Android Force Stop or guarantee reboot recovery. A router firmware that permits only one administrator session may invalidate the app session when the browser logs in; simultaneous sessions must be tested on the actual router.

# AX12StrictGuardroot

Version 0.2.7 replaces cumulative traffic statistics with a compact live per-device connection/activity view. Version 0.2.0 added read-only connected-device presence monitoring with Android arrival/departure notifications. Events require two consecutive complete router snapshots; the first complete snapshot is a silent baseline. These notifications do not change router settings. Version 0.2.1 adds session recovery; version 0.2.2 isolates unsupported/empty device-status responses while keeping the foreground service alive after recoverable router-session errors; version 0.2.3 adds the read-only Parental Controls API diagnostic.
