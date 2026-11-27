# Koreva architecture

Stack: one self-contained web app (www/index.html, vanilla JS, data in localStorage) wrapped by Capacitor for Android, with one native plugin (KorevaAlarmPlugin) for alarms, home-screen widgets and saving files.

Modules in index.html
- Data: FOODS (per 100 g), KINGR (128 ingredients per 100 g with category and fiber), KDS (about 1000 dishes computed from ingredient grams), KL (exercise library with how-to steps).
- Engines: nutrition targets (BMR, activity, goal), routine builder (survey), workout planner (movement patterns, injuries), dish recommender (every non-pantry ingredient must be selected; ranked by nutrition score), plan review (weight trend and results), routine presets (up to 10, assigned to days).
- UI: Today (coach), Alarms, Food (quick log, pins), Train, Me (appearance, settings).
- Native: exact alarms, full-screen alarm screen, Today widget, Water widget (one tap, works without opening the app), backup folder (Documents/Koreva).

Checks run before each release (tools/qa.py): recommender audit (no dish ever needs an ingredient you did not select), no script errors across the main flows, XML and brace checks on native files.
