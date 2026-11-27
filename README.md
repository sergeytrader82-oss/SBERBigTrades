# SBER Big Trades (prototype)

Android app (Kotlin/Compose) for monitoring recent SBER trades via T-Invest API, filtering by trade notional and displaying signed notional Delta/CVD.

## Build APK on GitHub (no Android Studio needed)
1. Create a GitHub account or sign in at https://github.com.
2. Create a new repository (e.g. `SBERBigTrades`).
3. Upload the contents of this project folder to the repository (including `.github/workflows/build-apk.yml`).
4. Open the repository's **Actions** tab and select **Build APK**.
5. Click **Run workflow**, select the `main` branch if asked, then confirm.
6. Wait until the workflow finishes with a green check mark.
7. Open the completed workflow run, scroll to **Artifacts**, and download `SBER-Big-Trades-debug-apk`.
8. Extract the downloaded ZIP; install `app-debug.apk` on Android. If Android blocks it, allow installation from this source in Settings when prompted.

## Configure
Enter your T-Invest API token in the app and use instrumentId `BBG004730N88` (SBER ordinary shares) or another supported instrument identifier.

## Caveats
- Prototype polls the recent-trades REST endpoint every 5 seconds; it is not a persistent streaming connection and can miss or repeat data depending on API behavior.
- Delta/CVD here are calculated in RUB, BUY positive and SELL negative, from newly observed trades during the current app run; they reset on reconnect/restart.
- API token is stored in app preferences on the device. Do not share it or use this prototype on a compromised phone.
- Notifications, background service, session persistence, native streaming, instrument UID lookup and robust deduplication are not implemented yet.
