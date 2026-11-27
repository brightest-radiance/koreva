#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
command -v node >/dev/null || { echo "Install Node.js first (Fedora: sudo dnf install nodejs)"; exit 1; }
npm install
[ -d android ] || npx cap add android
cp -r android-overrides/app/. android/app/
npx cap sync android
echo "Done. Open the android folder in Android Studio, then Build > Generate APKs."
echo "Command line build: cd android && ./gradlew assembleDebug (needs JDK 17+ and the Android SDK)."
