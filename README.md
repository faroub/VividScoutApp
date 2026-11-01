# VividScout

VividScout is an Android application built with Jetpack Compose, Firebase, Room, and AI-powered spot discovery.

## Release Build Configuration & Environment Variables

To build and sign production release artifacts (e.g. `assembleRelease` or `bundleRelease`), release signing parameters must be supplied via environment variables.

### Required Environment Variables

| Variable | Description |
| :--- | :--- |
| `KEYSTORE_PATH` | Absolute path to the release `.jks` or `.keystore` file. |
| `STORE_PASSWORD` | Keystore store password. |
| `KEY_ALIAS` | Key alias in the keystore. |
| `KEY_PASSWORD` | Key password for the specified alias. |

### Example

```bash
export KEYSTORE_PATH="/path/to/release-key.jks"
export STORE_PASSWORD="your_store_password"
export KEY_ALIAS="your_key_alias"
export KEY_PASSWORD="your_key_password"

./gradlew :app:assembleRelease
```

> **Note:** If any required environment variable is missing or if the specified keystore file cannot be found when executing a release build task, the build will fail immediately with an explicit error message. Debug builds continue to use the default debug keystore automatically.
