> **DRAFT — needs legal review before publish.**
> Adapted from the iOS Privacy Policy for the Android app. Items in [square brackets] must be confirmed by the owner. The Play Console Data Safety form must match this text.

# Privacy Policy

Last updated: [date of publication — set when published]

This policy explains what HoursTracker ("we") collects, why, where it is kept, and your rights under Israel's Protection of Privacy Law, 5741-1981. By using the app you agree to this policy.

## Data on your device

Your shifts, notes, workplace settings, marked days and payslips are kept on this phone, in the app's private storage. The app is excluded from Android's automatic cloud backup and device-to-device transfer, so this data does not leave the phone unless you choose to back it up or export it. Your national ID number is encrypted with a key held in the Android Keystore and is never sent anywhere. We don't sell your data and don't use it for advertising.

## Account and cloud backup

An account is optional. If you create one, we keep your email address, your name and a backup of your settings and shifts (without your national ID) on our server, hosted by Supabase at https://rocjprrvrvmtisxnvopg.supabase.co, so you can restore them on another Android phone. The hosting provider may keep the data outside Israel, with appropriate safeguards. Backup is opt-in and only adds data: it never overwrites newer data on your phone. You can delete your account and its backup at any time from the Account screen (Delete account).

## Notifications and the running-shift notification

While a shift is running, the app shows an ongoing notification with a timer (an Android foreground service of the `specialUse` type, which Android 14 and newer require apps to declare). It is needed so Android keeps the timer running and visible; it stops when you clock out. Android asks your permission before the app can show notifications, and you can turn them off at any time in the phone settings.

To send you reminders and in-app announcements, the app uses Firebase Cloud Messaging (FCM, provided by Google). The app registers a random install ID, a push token, its language, its version and whether a shift is running — never your shifts, pay, name or ID number. The on-shift flag is a yes/no value only.

## Smart features (AI)

The payslip scanner (and the assistant, if it ships) are optional. When you use them, the text of your question, or the text read from a timesheet or payslip, is sent to the AI provider set up in the app (Google Gemini). That provider handles the text under its own privacy policy. Figures about your pay are calculated on your phone. AI answers can be wrong — always check them.

## Support messages and announcements

When you contact support, we receive your message, the email you give us, the app and Android version and, only if you choose, the activity log. [Confirm the support channel and any messaging service used.]

## Location

The Android app does not use your location.

## Camera and photos

Used only when you scan or import a timesheet or payslip. Text is read on the phone; it is sent to an AI provider only if you use the smart scanner. [Confirm on-device text recognition for Android.]

## Tracking and ads

The app does not track you across apps or websites, does not show ads, and does not use third-party analytics SDKs.

## How long we keep data

Data on your phone stays until you delete it or uninstall the app. The account backup stays on our server until you ask us to delete the account. Support messages are kept only as long as needed to answer them. [Confirm any recently-deleted or automatic-backup retention on Android.]

## Your rights

Under Israeli law you may see the information we hold about you, ask us to correct it, or ask us to delete it. Most of this you can do yourself: export all your data, edit your details in Settings, and delete all your data. For anything on our server, write to us through Contact Support and we will answer as the law requires. You may also complain to the Privacy Protection Authority.

## Security

Your national ID is encrypted with the Android Keystore. Data sent to our server travels over encrypted connections, and each account can reach only its own backup. You can lock the app with your fingerprint or face (Android's biometric prompt). No system is perfectly secure; if a serious security incident affects your data, we will act as the law requires.

## Your controls

In Settings you can edit your details, turn notifications and reminders on or off, delete shifts, export all your data, or delete all data on this phone and in your account backup.

## Minors

If you are under 18, use HoursTracker with the consent of a parent or guardian.

## Changes to this policy

When we change this policy in a meaningful way, the app will show you the new version and ask you to agree again before you continue.

## Contact

The data controller is Hmam Kaadna, the developer of HoursTracker. For any privacy question or request, write to info.hourstracker@gmail.com or use Contact Support in Settings.
