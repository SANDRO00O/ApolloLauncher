# ApolloLauncher release signing setup

ApolloLauncher releases must use one permanent Android signing key. Android accepts an app update only when the update is signed by the same certificate as the installed app.

The repository includes a workflow that generates this key on GitHub Actions and returns it as an **encrypted artifact**. The repository is public, so the workflow never uploads an unencrypted keystore or prints its passwords.

## 1. Create a dedicated encryption key on Termux

Install OpenSSH and age:

```bash
pkg install openssh age
```

Create a dedicated Ed25519 key pair for decrypting the generated bundle. Do not reuse your GitHub SSH authentication key:

```bash
mkdir -p ~/.ssh
ssh-keygen -t ed25519 -f ~/.ssh/apollo-release-identity -N "" -C "ApolloLauncher signing-key encryption"
```

Display the **public** key:

```bash
cat ~/.ssh/apollo-release-identity.pub
```

It should begin with `ssh-ed25519`. Keep `~/.ssh/apollo-release-identity` private. Never paste or upload that private-key file.

Back up the private identity securely. Without it, the encrypted keystore artifact cannot be decrypted.

## 2. Run the key-generation workflow

1. Open the [ApolloLauncher repository](https://github.com/SANDRO00O/ApolloLauncher).
2. Open **Actions**.
3. Select **ApolloLauncher Create Signing Key**.
4. Press **Run workflow**.
5. Paste the complete one-line contents of `apollo-release-identity.pub` into **age_recipient**.
6. Enable **confirm_new_key** and run the workflow.

The workflow creates:
- `apollo-release.jks` — the Android release keystore.
- `GITHUB-SECRETS.txt` — the four values required by the release workflow.

Both files are placed inside an encrypted archive. The uploaded artifact is named **ApolloLauncher-signing-key-encrypted** and expires after one day.

The workflow refuses to generate another key if `ANDROID_KEYSTORE_BASE64` is already configured. This helps prevent accidental signing-key rotation.

## 3. Download and decrypt the artifact on Termux

Download the artifact ZIP from the workflow run's Artifacts section and extract it into your Termux home directory. Then enter the directory containing `ApolloLauncher-signing-key.tar.gz.age`.

Decrypt it using the dedicated private key:

```bash
age --decrypt \
  --identity ~/.ssh/apollo-release-identity \
  --output ApolloLauncher-signing-key.tar.gz \
  ApolloLauncher-signing-key.tar.gz.age
```

Extract the encrypted bundle:

```bash
mkdir -p ~/apollo-release-key
tar -xzf ApolloLauncher-signing-key.tar.gz -C ~/apollo-release-key
cd ~/apollo-release-key
```

You should now have:

```text
apollo-release.jks
GITHUB-SECRETS.txt
```

## 4. Add the four GitHub Actions secrets

Open:

**Repository → Settings → Secrets and variables → Actions → New repository secret**

Open `GITHUB-SECRETS.txt` locally. Create these four repository secrets, copying only the value after `=` for each line:

| Secret name | Description |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Private-key password |

Do not commit `GITHUB-SECRETS.txt`, the JKS file, or the SSH private key to the repository.

## 5. Publish your first release

After all four secrets have been added:

1. Open **Actions → ApolloLauncher Release → Run workflow**.
2. Enter a version such as `v2.0.0`.
3. Leave **prerelease** disabled for a stable release.
4. Run the workflow.

Alternatively, push a semantic version tag:

```bash
git tag v2.0.0
git push origin v2.0.0
```

The release workflow builds and signs the APK, creates a GitHub Release, and attaches the APK and its SHA-256 checksum.

## 6. Back up the signing key

Keep at least two secure offline backups of:
- `apollo-release.jks`
- `GITHUB-SECRETS.txt` or the four secret values stored in a password manager.
- `apollo-release-identity` (the private SSH identity used to decrypt the generated bundle).

The one-day artifact retention is only a temporary transfer mechanism. It is not a backup.

## Important: switching from a debug APK

The current GitHub debug APK is signed with a different certificate from the generated release key. Android will normally refuse an in-place update across those different certificates.

For the first switch, uninstall the debug build, install the signed release APK, and select ApolloLauncher as the Home app again. This removes the launcher's local preferences and cached wallpaper. Subsequent releases signed with the same permanent keystore can be installed as updates.

Never run the signing-key generator again for routine releases. Doing so would create a different certificate and break future updates.
