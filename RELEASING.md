# Releasing RoomQL

RoomQL publishes to Maven Central under `io.github.kotplat.roomql`. Publishing is
tag-triggered, but the last step is always a deliberate human action — a bad publish to
Central is effectively permanent, so nothing goes live without someone reviewing it first.

## Prerequisites (one-time)

- A Central Portal account for the `io.github.kotplat` namespace, with a publishing token
  generated at [central.sonatype.com](https://central.sonatype.com). Store the username and
  token as the `CENTRAL_PORTAL_USERNAME` and `CENTRAL_PORTAL_TOKEN` repo secrets.
- A GPG key dedicated to this project, stored as the `ORG_GPG_KEY` (armored private key) and
  `ORG_GPG_PASSPHRASE` repo secrets. The public key should be uploaded to a keyserver (e.g.
  `keyserver.ubuntu.com`) so Central can verify signatures.

Both are one-time setup; see issue [#65](https://github.com/KotPlat/RoomQL/issues/65).

## Cutting a release

1. Update `CHANGELOG.md`: move the relevant `## [Unreleased]` entries under a new
   `## [X.Y.Z] - YYYY-MM-DD` heading.
2. Merge that to `master`.
3. Tag the release commit and push the tag:

   ```bash
   git tag X.Y.Z
   git push origin X.Y.Z
   ```

   Pushing the tag triggers [`release.yml`](.github/workflows/release.yml), which builds,
   signs, and uploads all three artifacts to the Central Portal as version `X.Y.Z`.
4. Check the workflow run's job summary for the deployment link, or go directly to
   [central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments).
5. Review the deployment's validation report, then click **Release**. This step cannot be
   automated away — it is the point where the release becomes public and irreversible.
6. Once Central has synced (can take up to a few hours), confirm the artifacts resolve via
   plain `mavenCentral()` in a scratch project before announcing the release.

To re-run a publish without pushing a new tag (for example, to retry a failed upload), use
the workflow's `workflow_dispatch` trigger from the Actions tab, optionally overriding the
version.

## JitPack

JitPack remains available as a fallback during the migration to Maven Central. It is dropped
once a Maven Central release has been confirmed working — see issue
[#68](https://github.com/KotPlat/RoomQL/issues/68).
