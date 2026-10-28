# Security

## Reporting a problem

Please report security problems privately through GitHub: on this repository, go to **Security → Report a vulnerability**. Don't open a public issue for them.

Include what you found, the Memora version (Settings → Updates), the Android version, and the steps to reproduce it. You'll get a reply within 7 days, and a fix or a plan within 30 days for anything serious. You'll be credited in the release notes unless you'd rather not be.

Only the latest release gets security fixes.

## What Memora protects, and what it doesn't

Memora keeps everything on the phone, in the app's private storage. There is no server or account to attack. What it does protect:

- **Updates.** The in-app updater downloads only from this repository's GitHub releases over HTTPS. It checks the file's SHA-256, and installs it only if it is the same app, a newer version, and signed by the same key as the Memora already installed. Release APKs are signed with a key kept out of the repository; the debug key in the repository can't sign a release build.
- **Backups.** A password-protected backup is AES-256-GCM encrypted, with the key derived by PBKDF2-SHA256 (210,000 iterations). The header is authenticated too. A backup saved without a password is plain JSON that anyone with the file can read.
- **Links from other apps.** Screens can be opened only by Memora's own notifications and widget, which carry a random per-install token. Links without it are dropped.

What it doesn't protect against:

- **App lock is a screen lock, not encryption.** It keeps Memora's screens away from someone holding your unlocked phone. The database itself isn't encrypted beyond Android's own storage encryption, so it doesn't stop someone who can read the app's private files (a rooted phone, say).
- **Debug builds** (`Memora Debug`) can be inspected with developer tools. Use the release app for your real data.
