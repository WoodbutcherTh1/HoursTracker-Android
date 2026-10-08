# Google Play Data Safety form: draft

Answers for build **1.0.0-alpha1** (closed test). Checked against the code on 8 October 2026: the merged manifest has **no INTERNET permission**, no analytics or ads SDK, and `allowBackup="false"` with data-extraction rules that exclude the app's data.

Play counts data as *collected* only when it leaves the device. In this build nothing does.

## Form answers (this build)

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Shifts, pay, settings and the ID number stay on the phone. |
| Is all of the user data collected by your app encrypted in transit? | Not shown | Nothing is collected, so the question does not appear. |
| Do you provide a way for users to request that their data is deleted? | Not shown | Same. (In-app, shifts can be deleted, and Android's "Clear storage" or uninstall removes everything.) |
| Data shared with third parties | **None** | No third-party SDKs. |
| Ads | **No ads** | |
| Privacy policy URL | See `PLAY_CONSOLE_CHECKLIST.md` | `store/legal/PRIVACY_en.md` |

What the app does with data on the phone (for your own reference when Play asks):

| Data | Where | Protection |
|---|---|---|
| Shifts, breaks, notes, pay settings | Room database and preferences in the app's private storage | Android file-based encryption (needs a screen lock); excluded from Android backup |
| National ID number (optional) | Encrypted preferences | AES key in the Android Keystore; never in the database or backups |
| Exported report (PDF/CSV) | App cache, shared by the user through the Android share sheet | Old reports are deleted from the cache on the next export and Android can clear the cache at any time; the user chooses where a report goes |
| Notifications | Local only (shift timer, break reminder) | No server push in this build |

A report the user shares through the share sheet is a user-initiated transfer, which Play does not count as collection or sharing by the app.

## When the planned features ship, change the form first

Do **not** ship backup, account or push notifications until the form and the privacy policy are updated. Expected answers then:

| Data type | Collected | Shared | Purpose | Optional | Notes |
|---|---|---|---|---|---|
| Email address | Yes | No | Account management | Yes (account is optional) | Encrypted in transit |
| Name | Yes | No | Account management | Yes | |
| Other financial info (shift hours, pay, settings) | Yes, only if the user turns backup on | No | App functionality (backup and restore) | Yes | Never the ID number; encrypted in transit; deletable by the user |
| Device or other IDs (random install ID, push token) | Yes | No | App functionality (announcements, notifications) | No once push is on | Never linked to shifts or pay |
| App info (app version, language) | Yes | No | App functionality | No | |

Security practices to tick then: data encrypted in transit; users can request deletion (in-app "Delete account" plus a web page or email address).
