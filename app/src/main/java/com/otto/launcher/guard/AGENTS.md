# Guard

One job: device-owner, greyscale, policy, and friction controls.

May import core, domain, data, and guard. Root imports are limited to pinned Android components.

Provisional decision (2026-07-20): the VPN service and both receivers live in the root package solely because the manifest and device-owner binding pin them there.
