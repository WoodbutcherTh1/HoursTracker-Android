# Secret rotation policy

A secret is any value that gives access: keys, tokens, passwords, signing keys. **No secret is ever committed**; gitleaks checks every push. Rotate on the schedule below, immediately after any suspected exposure, and when a person who knew it leaves.

| Secret | Where it lives | Rotate | How | Check afterwards |
|---|---|---|---|---|
| Supabase `anon` / publishable key (*planned, M4*) | Build config, not the repo | 90 days, or on exposure (it is public by design but protects nothing alone: row level security does) | Create a new key in the Supabase dashboard, ship an app release that uses it, retire the old one after the old versions are phased out | Old key refused; RLS tests pass |
| Supabase `service_role` (*planned*) | Supabase function secrets and CI secrets only | 90 days | Rotate in the dashboard, update the function secret, redeploy | Functions work; old key refused; no copy in any repo or app |
| Supabase JWT secret (*planned*) | Supabase | Only on exposure (it signs out everyone) | Follow Supabase's current procedure | Sessions re-issued |
| FCM service account key (*planned*) | Supabase function secret | 180 days | New key in Google Cloud, update the secret, delete the old key | Test push delivered |
| Android **upload keystore** | `~/.hourstracker-signing/` on the owner's machine, mirrored in the untracked `local.properties` | **Never rotated on a schedule**; replaced only on compromise | Back it up in two places (password manager and an offline copy). On compromise, ask Google Play support to reset the upload key (Play App Signing keeps the app signing key) | New upload key registered; old one revoked |
| GitHub personal access tokens, deploy keys | GitHub | 90 days; prefer fine-grained tokens and short expiry | Create new, update CI secret, delete old | CI green |
| CI secrets | GitHub Actions secrets | 180 days | As above | Workflows pass |
| Gemini API key | The **user's own** key (*planned, M5*); the app never ships one | n/a | n/a | n/a |

## Process (any secret)

1. Create the new secret; do not delete the old yet.
2. Deploy it everywhere it is used.
3. Verify the new one works.
4. Revoke the old one; confirm it is refused.
5. Search the repository and its history for the old value (`gitleaks detect`) and for the new one (must not appear).
6. Write the line in the log below.

If the old secret may have been misused, treat it as an incident: see `docs/BREACH_RESPONSE.md`.

## Rotation log

| Date | Secret | Reason | By | Verified |
|---|---|---|---|---|
| | none yet (no server secrets exist) | | | |
