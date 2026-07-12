# Build and install through GitHub Actions

1. Push code to GitHub.
2. Open the repository.
3. Open the Actions tab.
4. Select Android CI.
5. Open the latest successful run.
6. Download `network-investigator-debug` from Artifacts.
7. Extract the ZIP.
8. Transfer the APK to an Android device.
9. Allow installation from the selected file-manager or browser source.
10. Install the APK.

CI checks out a clean clone, validates the wrapper, installs Temurin JDK 17 and Android API 37/Build Tools 36.0.0, then runs `testDebugUnitTest`, `lintDebug`, and `assembleDebug`. Test and lint/build reports upload with `always()` even if compilation fails; an APK can only exist if assembly reaches that output.

