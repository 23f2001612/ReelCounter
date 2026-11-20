# ReelCounter

An Android app experiment to count Instagram Reels and investigate native Realme Mini Capsule / Live Alerts integration.

## Current milestone: notification compatibility test

Version 0.1 starts a user-controlled foreground service and updates an ongoing notification every three seconds. This is only a compatibility probe. It does **not** count Instagram Reels yet, and a changing notification does not guarantee that Realme will display it in the Mini Capsule.

## Build an APK with GitHub Actions

1. Open the **Actions** tab.
2. Select **Build ReelCounter APK**.
3. Tap **Run workflow** (or wait for a build after a push).
4. Open the completed workflow run.
5. Download the **ReelCounter-debug-apk** artifact.

## Test on a Realme phone

1. Install the APK.
2. Open ReelCounter and grant notification permission.
3. Tap **Start test counter**.
4. Check whether the changing count appears in Realme Live Alerts / Mini Capsule.
5. Tap **Stop test counter** when finished.

## Important

Realme's Live Alerts implementation may restrict third-party apps. This project deliberately tests a normal Android ongoing notification first; it does not claim access to a private Realme API.
