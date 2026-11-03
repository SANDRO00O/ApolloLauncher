# ApolloLauncher release setup

The release workflow publishes a **signed** APK. Android requires every update to use the same signing certificate as the installed app, so create one release keystore and keep it for the lifetime of the project.

## 1. Create the release keystore

Run this on a trusted computer or Android environment with Java's `keytool` available:

```bash
keytool -genkeypair -v \
  -keystore apollo-release.jks \
  -storetype JKS \
  -alias apollo-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Choose strong passwords and store them in a password manager. Keep at least one encrypted offline backup of `apollo-release.jks`.

**Never commit the keystore or its passwords to this repository.** If the keystore is lost, future APKs signed with a new key will not update installations signed with the old key.

## 2. Add GitHub Actions secrets

Convert the keystore to one-line Base64:

```bash
base64 apollo-release.jks | tr -d '\r\n'
```

In the repository, open **Settings → Secrets and variables → Actions → New repository secret** and add these four secrets:

| Secret name | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | The one-line Base64 output from the command above |
| `ANDROID_KEYSTORE_PASSWORD` | The keystore password |
| `ANDROID_KEY_ALIAS` | `apollo-release` (or the alias you chose) |
| `ANDROID_KEY_PASSWORD` | The key password |

The workflow validates all four secrets and verifies the keystore before starting Gradle. It stops with a clear error if any are missing.

## 3. Publish a release

There are two supported ways to publish:

### Push a version tag

```bash
git tag v2.0.0
git push origin v2.0.0
```

Use a new semantic version for each release, for example `v2.0.1` or `v2.1.0`.

### Run manually

1. Open the repository's **Actions** tab.
2. Select **ApolloLauncher Release**.
3. Choose **Run workflow**.
4. Enter a tag such as `v2.0.0`.
5. Enable **prerelease** only for beta or testing builds.

The workflow creates the tag from `main` when a manually requested tag does not exist. A pushed tag is built from the commit that owns that tag.

## 4. What gets published

Each release contains:

- `ApolloLauncher-VERSION.apk` — signed release APK.
- `ApolloLauncher-VERSION.apk.sha256` — SHA-256 checksum.

The same files are also retained as a GitHub Actions artifact for 30 days. GitHub automatically generates release notes from the changes since the previous release.

The Android `versionName` comes from the tag without the leading `v`. The workflow assigns a monotonically increasing `versionCode` using its Actions run number.

## 5. Switching from a debug build

The existing CI debug APK and the new release APK are signed with different certificates. Android normally refuses to update an app when its signing certificate changes.

For the first move from the current debug build to the release build, uninstall the debug app, then install the signed release APK and choose ApolloLauncher as the Home app again. Uninstalling removes the app's local preferences and cached wallpaper. After that one-time switch, future releases made with the same keystore can be installed as updates without uninstalling.

Do not rotate or replace the release keystore for routine releases.
