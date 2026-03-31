# Feature Ideas & Requests

Tracked feature ideas, requests, and improvements for Otto Launcher.

## In Progress

- **Activity Tracking** — Track app opens/closes per day, time spent per app, hourly distribution. Encrypted at rest with password-based decryption (salt-only, no stored passkey). Access via "Stats" chip.

## Ideas / Backlog

- **Website time tracking** — Track time spent on websites (requires integration with DNS VPN or accessibility service to detect URLs in browsers).
- **Geo-spoofing VPN** — Use Otto as a VPN to appear from a different country. Requires a remote server in the target country to route traffic through. Not feasible as a free on-device-only solution — would need a hosted proxy/VPN server. Could potentially integrate with existing free VPN services (Proton VPN, Windscribe) via their APIs.
- **Random unlock journaling prompts** — Every X unlocks (randomised interval), force a micro-journal entry before proceeding. Prompt for things like: what did you eat today, current mood, energy level, what are you doing right now, etc. Encrypted with the same salt-only approach as activity tracking. Builds a lightweight life log over time without requiring discipline to open a journal app.
- **Activity tracking export** — Export stats as CSV or share as text.
- **Weekly/monthly stats views** — Extend activity stats beyond daily to show weekly and monthly trends.
- **App usage limits** — Set daily time limits per app, warn or block when exceeded.
- **Screen time widget** — Show today's screen time summary on the launcher home screen.

## Completed

(None yet)
