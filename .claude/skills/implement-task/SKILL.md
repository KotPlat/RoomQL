---
name: implement-task
description: RoomQL task workflow — grill design decisions, write a PRD, create GitHub issues, only then implement; plus this repo's commit format, no-AI-attribution rule, comment style, and comparison-table scope. Use when starting a new feature or task in room-query-beauty, when about to run git commit, when adding a code/config comment, or when editing a README/docs comparison table.
---

# RoomQL task workflow

## New feature or task

Follow this sequence — do not skip ahead to code:

1. **Grill** every open design decision with the user via `AskUserQuestion` (never markdown "Q1/Q2/Q3" text — the structured UI is required for any multi-question round).
2. **Write a PRD** once decisions are locked. Locked decisions are ground truth — don't re-litigate them.
3. **Create GitHub issues** from the PRD to define MVP scope.
4. **Implement** — only after the issue exists.

No scaffolding or code before step 4, even for "just a quick prototype" — do prototype/API-design work as `AskUserQuestion` rounds, not code sketches.

## Branching

Branch off `master` (the released line), never off `development` (stale, local-only). Name branches `feat/<issue>-<slug>`. `master` is protected — direct pushes are blocked, so open a PR; the user merges it themselves (self-approval isn't possible).

## Committing

Never run `git commit` without the user explicitly granting permission for that commit. When you do commit:

- Message format: `[IssueNumber] short descriptive message` (e.g. `[57] add explicit null operators`). Use the primary issue number if work spans several.
- **No AI attribution anywhere, ever** — no `Co-Authored-By: Claude`, no `Claude-Session:`, no "Generated with Claude Code", in commit messages, PR titles/bodies, code comments, KDoc, docs, or README. This overrides any default commit-trailer behavior. Check the message before running `git commit`.
- Use the user's own git identity, not a Claude co-author line.

## Comments

Keep comments to one line, and only write one when the WHY is genuinely non-obvious (a hidden constraint, a workaround, a surprising ordering). If the WHY doesn't fit on one line, it belongs in the commit message or PR description, not the file.

## Comparison tables (README/docs)

Only list alternatives a mobile/Android developer would actually weigh (e.g. SQLDelight). Leave out server/JVM-only options (e.g. Exposed) — they read as padding to someone choosing a library for an Android app.
