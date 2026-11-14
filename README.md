# AX12StrictGuardroot — 0.1.7-strict-mac-secure-restart

Latest source update: reject locally administered/private/randomized MAC addresses from trust and allow-list write paths. Optional Android Keystore-backed AES-GCM credential storage enables sticky-service recovery after Android recreates the service. Credential persistence is opt-in and cleared when monitoring stops or ends. See `SECURE_RESTART_AND_MAC_POLICY_AR.md` and `PROJECT_STATUS.md`.

Important: a globally administered MAC is not proof of factory authenticity and can still be spoofed. Sticky restart does not survive Android Force Stop or guarantee reboot recovery. Router firmware compatibility and on-device restart behavior remain unverified until tested.

# AX12StrictGuardroot