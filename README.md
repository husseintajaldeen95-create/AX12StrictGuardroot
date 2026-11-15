# AX12StrictGuardroot — 0.2.2-presence-isolation

Latest source update: failures in the optional `admin/status` connected-device endpoint are isolated from the core Allow List guard. The app now reports empty or unsupported responses without logging raw router payloads, pauses only device-presence polling for five minutes, and continues Allow List enforcement when the admin session remains valid. If a failed signed request invalidates the session, the service discards it and reconnects under the existing safety policy. See `PROJECT_STATUS.md` and `CHANGELOG_AR.md`.

Important: a globally administered MAC is not proof of factory authenticity and can still be spoofed. Sticky restart does not survive Android Force Stop or guarantee reboot recovery. A router firmware that permits only one administrator session may invalidate the app session when the browser logs in; simultaneous sessions must be tested on the actual router.

# AX12StrictGuardroot

Version 0.2.0 added read-only connected-device presence monitoring with Android arrival/departure notifications. Events require two consecutive complete router snapshots; the first complete snapshot is a silent baseline. These notifications do not change router settings. Version 0.2.1 adds session recovery; version 0.2.2 isolates unsupported/empty device-status responses while keeping the foreground service alive after recoverable router-session errors.
