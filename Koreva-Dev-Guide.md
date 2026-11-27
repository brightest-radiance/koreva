# Koreva developer guide and handoff (v8)

Upload this file alone to any AI or chat to continue work on Koreva. It describes the app, its data, its algorithms, its code layout and how to add or change things without seeing the source. For real edits the AI should write an **append-only block** (section 11) that you paste at the end of `www/index.html`.

## 1. What Koreva is
Koreva is an offline-first health companion: daily routine, food and water tracking, workouts, alarms and a recipe finder, in one HTML file wrapped as an Android app (Capacitor). Data stays on the phone (localStorage key `bpV8`). Made by Kaustubh Niroula. It began as a personal tool for his own diet (weight gain, Nepali food), workouts (elbow problem) and routine, then was generalised so anyone can set it up with a short survey. Design principles from the owner: no gamification (XP and streaks were removed because they felt like a chore), reduce logging effort (quick log, usual meals, undo), honest numbers, works without internet, looks good in many themes.

## 2. Features by tab
- **Today**: coach card (next task, calories/protein/water left, "log usual breakfast/lunch/dinner" one tap, weight-goal progress and projected date, protein idea with tutorial link, last-7-days summary, shortcuts). Task list from the routine, rings, badges.
- **Alarms**: weekday alarms with snooze. On Android they are real alarm-clock alarms (full-screen Stop/Snooze, loud, survive reboot).
- **Food**: quick log (pinned and frequent foods, repeat yesterday, undo toast), water (cup size, custom ml), daily nutrition overview, 376-food guide (60 rows at a time, search, categories, Nepali heavy), recipe finder, meal log.
- **Train**: plan built from a survey (goal, sex, level, days, equipment, injuries) using movement patterns; 110 exercises with how-to steps and YouTube tutorial links; per-exercise timer (get-ready 5-15 s, rest, personal bests); weekly plan review; swap an exercise for an alternative.
- **Me**: weight and profile, nutrition targets with "Recalculate from my profile" and "Why these numbers?", health notes, Settings and connections (sounds, vibration, reduce motion, nudges, weekly auto-backup, redo setup, calendar .ics, CSV, help), Theme card with "More colours and themes", backup/restore.
- **Setup survey** (first run or Settings): about you, goal and diet, wake/sleep, work or study times, daily activity, training, obstacles. Builds routine tasks, a wake alarm, workout plan, targets and nudges.
- **Routines**: up to 10 presets (Weekday, Weekend, Gym day, Rest day, Busy day, Travel day, custom), assigned per weekday plus a "today only" override.
- **Native extras**: Today widget, Water widget (one tap, works without opening the app), weekly auto-backup to Documents/Koreva/koreva-auto-latest.json, backup sharing, local notifications for nudges and task reminders.

## 3. Stack and files
- `www/index.html`: whole app (vanilla JS, CSS, data). ~415 KB. No build step.
- Capacitor project: `package.json`, `capacitor.config.json` (appId com.koreva.app), `android-overrides/app/src/main/...` copied over the generated `android/app` by `SETUP-AND-BUILD.bat` (Windows) or `setup.sh` (Linux). `tools/qa.py` is the test suite.
- Build: run the setup script, then Android Studio, Build > Generate App Bundles or APKs > Generate APKs. After editing index.html: `npx cap sync android`, Build > Clean Project, build again (otherwise old web assets are reused). Debug APKs from the same PC share a key so updates install over the old app.
- Web/PC: `www` can be hosted (GitHub Pages) and installed as a PWA in Edge/Chrome/Chromium. KDE Connect, Syncthing or a cable move backups to a PC.

## 4. Code layout (important)
`index.html` has two `<script>` blocks. Script 1 is the original minified app and ends with the first `render()`. Script 2 is appended layers in order: v1 training module and native alarm bridge, v2 persistent nav and tab transitions and exercise how-to data, v3 timer, progress, water, native alarm sync, v4 exercise engine, v5 survey and exports, v6 routine-by-day and plan review, v7 sheets/back button/themes/quick log/presets, v8 targets, dish engine, recipe finder, food-guide paging. Later layers **wrap or override** earlier functions (`const _x=fn; fn=function(){...}` or a redeclared function). All layers share one global scope.
Patched original text: `hideS` (calls `window.kxShut`), `closeSheet` (calls `kCloseFast`), `tasksFor` (renamed `tasksFor0`, new `tasksFor` filters by routine preset), `#hd` close button, `Pin to quick log` button in the food sheet, nav build moved to `kNav`.

## 5. State object `S` (saved by `save()` into localStorage `bpV8`)
- `tasks[]`: `{id,t,n,m:'w'|'f'|'n',v,r:0|1,d:[0-6],p?}` (`w` = minutes after waking, `f` = 'HH:MM', `n` = no time, `r` = reminder, `p` = routine preset id).
- `done[date][taskId]=true`; `food[date][]`: `{n,s,m:0-3 (breakfast,lunch,snack,dinner),k,p,c,f,fi,su,na,naKnown}`; `wat[date]` ml; `cup` ml; `wt[]`: `{d,w,a?}`; `profile`: `{heightCm,age,milestone,target}`; `goals`/global `TG`: `{k,p,c,f,fi,w}`; `wake[date]`, `lw`.
- `al[]`: `{id,on,d:[0-6],h,m,l,sn,s,auto?}`.
- `wk`: `{p:{g,sx,l,n,e[],j[]}, plan:{dow:{t,ex:[{id,n,m,c,s:'3×12'|'3×35 s',r?,u?}]}}, cx[], log:{date:{exId:true}}, hist[{t,dk,n,sets,v,tm,dur,tot,pr}], prep, rest}`.
- `survey` (answers incl. `act` 0-3), `onb`, `onbSkip`, `prof2` (`sx,act,tr` overrides from the targets sheet), `set` (`beep,vib,rm,ab` use 0 for off; `th` theme index; `ac` accent), `nudge` (`on,t:[[HH:MM,msgIndex]]`), `pins[]` (food entry snapshots), `pantry[]` (ingredient keys), `cooked{dishName:ts}`, `presets[{id,name}]` (max 10), `wkmap{dow:id}`, `ov{date:id}`, `lastBk`, `lastRev`.

## 6. Key globals (original app)
`S, TG, NU, key(d)→'YYYY-MM-DD', p2, esc, $(id), tm('HH:MM')→min, tmin(task,date), tasksFor(date), totals(date)→{k,p,c,f,fi,su,na}, save(), render(enter), toast(msg), showSheet(html), closeSheet(), vib(), FOODS (array), foodList(q,cat), fcalc(), servingText(), FI/FQ/FU/FM, TD+taskHTML() (task editor), DN (day names), tab, TABS, RM (reduce motion), FLAWS (health notes).` Click routing: original uses `data-a="..."`, all later layers use `data-kx="..."` with their own `document.addEventListener('click')`.

## 7. Algorithms
- **Nutrition targets `kTargets({wt,tw,ht,age,sx,g,tr,act})`**: BMR Mifflin-St Jeor; activity factor `[1.2,1.3,1.4,1.55][act] + 0.04×workouts (max 6 days)` capped 1.9; direction from target weight vs weight (within 1 kg uses the goal: fat loss, muscle, strength); gain adds 15% of TDEE clamped 250-500 kcal (+100 if BMI under 18.5, gentle if weak signal); loss subtracts 20% clamped to 750 (12% if weak); floor max(1.05×BMR, 1500 male / 1200 female). Protein 1.8 g/kg (gain, muscle, strength), 2.0 (loss), 1.4 (maintain, endurance); fat max(0.8 g/kg, 27% kcal); carbs the rest (min 2 g/kg); fiber 14 g per 1000 kcal (min 25); water 35 ml/kg (+300 if training) in 250 steps, 1500-4000. `kProfObj()` reads profile, last weight, survey, `prof2`, plan days. Example: 60 kg, 170 cm, 18, target 75, on feet, 3 workouts gives 2750 kcal, 108 g protein.
- **Routine `kRoutine(o)`**: wake, move, breakfast, workout (on plan days at morning/afternoon/evening), lunch, snack, dinner, wind-down, sleep; `fx` (changing schedule) anchors everything to waking time (`m:'w'`).
- **Workout `kGen(p)`**: days 2-6 map to Upper/Lower/Full/PPL; each day = warm-up + movement-pattern slots + cool-down; exercises chosen by equipment tier, level, injury tags (`e,s,w,b,k`), variety across the week; sets/reps/rest by goal; fixed numbers (no ranges).
- **Dish engine**: `KING` 128 ingredients (category, kcal, protein, carbs, fat, fiber per 100 g). `KDS` ~1000 dishes generated by grammar (vegetable tarkari, protein+vegetable curries/stir-fries/pulao/salads, dal combos, Nepali dhido sets, breakfasts, fruit bowls, hand-written specials) with macros computed from ingredient grams. **Rule: a dish is offered only if every ingredient except oil, ghee, onion, tomato and spring onion is selected** (`d.req`; the QA audit checks this independently). Diet derived from ingredients. Score (0-100) = 35×protein density + 20×fiber + 20×vegetable grams + 15×food-group variety + 10×fit to calories left per remaining meal, minus a recency penalty (logged in last 3 days), plus pin bonus and tiny daily jitter. Sorts: best nutrition, most protein, fewest calories, best match; diversity cap of 3 per dish family in the first 30 results; portion small 0.75, regular 1, large 1.4.
- **Plan review**: weight trend (regression over 21 days), proposes ±100-150 kcal; adherence below 50% shortens sessions; hitting targets raises reps or seconds.
- **Sheets**: custom spring physics; close uses `kCloseFast` (200 ms ease). History stack (`kxOpen/kxClose`) makes the Android back button close sheets and overlays. Swipe-down works from the heading and from sheet content, but not inside `.kscroll` lists.

## 8. Native layer (`KorevaAlarmPlugin`, plugin name `KorevaAlarm`)
Methods: `schedule({alarms:[{id,h,m,d[],l,sn}]})→{exact}`, `settings()`, `test()`, `saveBackup({name,data,mime,overwrite})→{path,uri}` (MediaStore Documents/Koreva), `shareFile({uri,mime})`, `widget({a,b,c,w,g,u})`, `takePending()→{water}` (ml added from the Water widget while the app was closed). Components: AlarmReceiver, AlarmService (foreground, USAGE_ALARM), AlarmActivity (full screen over lock), BootReceiver, KorevaWidget (Today), KorevaWaterWidget (one tap +cup). Permissions: notifications, exact alarm, vibrate, wake lock, full-screen intent, foreground service, boot, legacy storage (max SDK 28). Local notifications (Capacitor plugin) carry nudges (ids 9001-9004) and task reminders (ids 8000-8999).

## 9. Testing and performance
`python3 tools/qa.py <file:///path/to/index.html>` (Playwright, headless Chromium, 48 checks): fresh load and survey, no gamification, data quality (at least 900 dishes, 120 ingredients, unique names, sane macros), recommender audit over 500 random pantries x 4 diets, user-reported cases (rice + goat), sorts, targets for several body profiles, themes placement, finder panes, search, portions, drag guard, back and close buttons, quick log and undo, presets cap, food paging, render times. Measured in headless desktop Chromium: tabs render and lay out in 8-41 ms; recommender 0.2-5 ms; sheet close about 270 ms. The Food tab was 300 ms and 2,730 DOM nodes before paging and `content-visibility`; now about 45 ms and 940 nodes. Not testable here: Java compile, widgets, alarms on a locked phone, real-device scrolling.

## 10. Pitfalls learned
1. A duplicate top-level `const/let` name silently kills the entire second script. All layers share globals; wrap new code in an IIFE.
2. The first render happens before layers load; anything the original calls early must exist in script 1 or be guarded by `typeof`.
3. Never reference a later `const` from an earlier layer at load time (temporal dead zone).
4. `render(false)` at the end of a layer is needed so new UI shows on first paint.
5. Keep sheet content out of `touchmove` conflicts: scrollable lists inside sheets need class `kscroll`.
6. After web changes run `npx cap sync android` and Clean Project.
7. Food values are estimates; label them as such.

## 11. How to add or change something (for any AI)
Write one block and paste it before the final `</script>` of `www/index.html`:
```js
/* v9: <feature name> */
(function(){
  // wrap an existing view: const _v=viewFood; viewFood=function(){return _v().replace('</p>','</p><div>new</div>')};
  // add actions: <button data-kx="myact" data-k="1">; document.addEventListener('click',e=>{const b=e.target.closest('[data-kx]');if(!b)return;if(b.dataset.kx==='myact'){/* ... */save();render(false)}});
  // open UI: showSheet('<h3 class="sh">Title</h3>...'); close: closeSheet(); undo toast: kUndo(msg,fn)
})();
```
Rules: persist everything in `S` and call `save()`; escape user text with `esc()`; use CSS variables (`--bg --tx --mu --ac --on --cs --line --soft`) and classes `.card .btn .btn.t .kxw .mu .bar`; use `data-kx` not `data-a`; keep lists under 60 rows per render (use `content-visibility:auto` and paging); then run `tools/qa.py`.
Ask the AI to return: the block, any CSS, any new `S` fields, and test additions.

## 12. Known gaps and roadmap
1. Health Connect (steps, sleep, heart rate from watches) needs a different Android setup; not included. Notifications reach watches through the watch app.
2. Food and recipe values are estimates; no barcode or photo logging.
3. Release signing and Play Store; currently debug-signed.
4. Weekly meal planner and shopping list from pantry and targets.
5. Nepali and Hindi language, larger text mode.
6. Progress photos and body measurements.
7. Server sync and shared challenges (needs a backend).
