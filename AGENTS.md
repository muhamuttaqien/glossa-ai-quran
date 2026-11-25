# Glossa AI Qur'an — Project Instructions

## Scope and precedence

These instructions apply to this Android repository. Read them before changing the app, its content, documentation, or previews. Follow the user's current request and higher-priority instructions first. Use the general rules in `/home/muhamuttaqien/ChatGPT-Agent-Rules/AGENTS.md` and the relevant `CODING.md` when accessible. Treat the existing code, assets, and README as the current project baseline; verify details there before making claims.

## Project structure and workflow

- Work directly in this repository. Keep Android source under `app/`, the original brand artwork under `design/`, and user-facing preview images under `preview/`.
- Make focused changes. Preserve existing features and preferences unless the task calls for changing them. Update the README when behavior, setup, content provenance, or limitations change.
- Use `development` as the base and integration branch for ongoing work. For each new feature, create a fresh `feature/<nama-fitur>` branch from the latest `development`; for each bug fix, create a fresh `fix/<nama-fix>` branch from the latest `development`. Use a short, descriptive lowercase kebab-case name after the slash. Continue on the same task branch until that task is complete.
- Make and commit changes on the task branch in the connected GitHub repository. Do not commit feature or fix work directly to `development` or `main`. When the implementation and relevant checks are complete, review the changed files, merge the task branch into `development`, and verify the resulting `development` commit. Leave `main` unchanged unless the user requests a separate promotion.
- After the merge, tell the user that `development` is ready for a local pull, using `git switch development` and `git pull --ff-only origin development`. The user handles this local pull. If asked to synchronize the checkout directly, first check for uncommitted work and branch divergence; preserve local work and never overwrite it to force a pull. If direct GitHub editing or merging is unavailable, explain the limitation and agree on another route.
- Keep generated builds, caches, local SDK paths, and secrets out of Git. Review changed files and commit scope before updating GitHub.
- Do not invent milestone names. Describe work as implemented, verified, or validated on a device according to the evidence actually available.

## Qur'an content integrity

- Keep the three concepts distinct: **Uthmani rasm** describes orthography, **Madinah Mushaf** identifies the selected page layout, and **Hafs from Asim** identifies the reading transmission.
- The Read Arabic mode currently uses bundled fixed Madinah Mushaf page artwork for Hafs. Keep its page order, Arabic glyphs, verse markers, and printed line breaks intact. Never use an image generator or manually retyped Arabic as a replacement for authoritative page artwork.
- Any visual treatment, including dark mode, must preserve the page's text and geometry. Check that page numbering, verse selection, bookmarks, translations, and audio still refer to the correct surah and ayah, including pages that cross surah boundaries.
- Verify edition identifiers, page indices, and audio mappings against the actual data and code before changing them. Keep translations and recitations attributed to their sources. Check source rights before adding or redistributing new Qur'an assets.
- If a content discrepancy is found, identify its source and correct it there; do not silently patch or paraphrase the sacred text.

## Version previews

- For each new app version, create a preview in `preview/` and show it in the response. Use the title `Glossa AI - Qur'an Version (vX.Y)` with the actual version number.
- Put light and dark app screens in **one image**, under one shared title and one shared feature label. Show the same feature in both screens; the dark screen must reflect the app's real dark-mode behavior.
- Use the bundled page artwork when a preview contains Qur'an text. Preserve its Arabic exactly. Keep controls inside the phone screen and label a mockup as a preview, not as a verified device screenshot.
- Avoid accumulating redundant preview exports. Keep the final combined preview discoverable, and retain separate working images only when they serve a purpose.

## Verification and reporting

- Apply minimal testing with sufficient verification. For code changes, run the most relevant targeted check or Android build when the required JDK and SDK are available. For changes to Qur'an display or navigation, inspect representative pages and the affected interactions on a device or emulator when possible.
- State what was checked and what remains unverified. A generated preview or successful compilation does not prove that the Android UI or recitation works on a device.

## Maintaining this file

Update these instructions when the project's agreed conventions change. Keep durable rules here; put version-specific facts, dependencies, and source credits in the README or code. When a rule becomes outdated, revise it rather than appending a conflicting exception.
