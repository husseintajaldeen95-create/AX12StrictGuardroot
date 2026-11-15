# AX12StrictGuardroot — 0.2.1-session-recovery

Latest source update: the foreground guard remains alive through router-session expiry/timeouts and creates a fresh authenticated session after a safety delay. Optional Android Keystore-backed AES-GCM credential storage enables service recovery after Android recreates the service. Saved credentials remain encrypted through recoverable session failures and are cleared on explicit stop or user deletion. See `SESSION_RECOVERY_AR.md` and `PROJECT_STATUS.md`.

Important: a globally administered MAC is not proof of factory authenticity and can still be spoofed. Sticky restart does not survive Android Force Stop or guarantee reboot recovery. A router firmware that permits only one administrator session may invalidate the app session when the browser logs in; simultaneous sessions must be tested on the actual router.

# AX12StrictGuardroot

Version 0.2.0 added read-only connected-device presence monitoring with Android arrival/departure notifications. Events require two consecutive complete router snapshots; the first complete snapshot is a silent baseline. These notifications do not change router settings. Version 0.2.1 adds session recovery while keeping the foreground service alive after recoverable router-session errors.
