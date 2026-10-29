# Koreva — Android build (Capacitor)
Needs: Node.js 20+, Android Studio (with SDK), JDK 17+.

1. Unzip, open a terminal in this folder.
2. `npm install`
3. `npx cap add android`
4. `npx cap sync android`
5. Open `android/app/src/main/AndroidManifest.xml` and add inside `<manifest>`:
   `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>`
   `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>`
   `<uses-permission android:name="android.permission.VIBRATE"/>`
6. `npx cap open android` -> Android Studio: wait for Gradle, plug in phone (USB debugging) and press Run, or Build > Build APK(s).
7. App icon: Android Studio > right-click `res` > New > Image Asset > pick `www/icon-512.png`.

Alarms: in the browser they only ring while the app is open. In the Android build, `kxSync` in index.html schedules your weekly alarms as native notifications (allow notifications + "Alarms & reminders" in Android settings; set battery to Unrestricted for Koreva).
After editing `www/index.html`, run `npx cap sync android` again.
Data is stored on-device; use Me > Download a backup before reinstalling.
