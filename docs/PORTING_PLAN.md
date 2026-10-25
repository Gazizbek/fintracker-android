# FinTracker iOS → Android porting plan

Source of truth: `../FinTracker` (SwiftUI + ObjectBox). Sync contract: `../FinTracker/FinTracker/Features/Sync/SYNC_FEATURE_ANALYSIS.md`
and `../fintracker-backend/src/types/dtos.ts`.

## Stack
Kotlin, Jetpack Compose + Material 3, MVVM (ViewModel + StateFlow), manual DI (`AppContainer`),
ObjectBox (same DB engine as iOS), Retrofit + OkHttp + kotlinx.serialization, Firebase Auth / Messaging,
Credential Manager (Google sign-in), Play Billing, WorkManager, androidx.biometric, AppCompat per-app locales.

## Compatibility rules (sync!)
- Stored raw values are identical to iOS:
  - `OperationType`: `expense`, `income`, `transfer` (server ints 0/1/2)
  - `CategoryType`: `expenseCategory`, `incomeCategory`, `mixedCategory` (server ints 0/1/2)
  - `AccountType`: `debitCard`, `creditCard`, `deposit`, `credit`, `cash`, `savings`, `debt` (lossy server mapping 0/1/2)
  - `AccountIcons`, `CategoryIcons`, `CategoryGroupIcons`: enum case names (`creditcard`, `basket`, `forkknife`, …) sent as `iconName`
- Colors stored as ARGB `Int` (= wire `colorArgb`, no conversion needed).
- `remoteId` UUID generated client-side on first local write; `updatedAt` ms stamped on every local write; tombstones on delete.
- `mainBalance` syncs, `balance` is derived locally (recalculated after pull/conflicts/restore).
- Push registration uses `"platform": "android"`; subscription verification uses `POST /subscription/android {productId, purchaseToken}`.

## Steps
1. [x] Analyze iOS project.
2. [x] Build setup: plugins/dependencies, `google-services.json`, manifest, `Application`.
3. [x] Resources: strings en/ru/kk (generated from `Localizable.xcstrings`), privacy HTML, launcher icon.
4. [x] Domain: enums, ObjectBox entities, `FinanceStore` (all ObjectBoxStore logic incl. balances), preferences.
5. [x] Sync: DTOs, mapper, API, AuthManager, SubscriptionManager, SyncEngine, FCM service, WorkManager, network monitor.
6. [x] UI foundation: theme, 5-tab navigation, shared components (icon chip, calculator, icon/color pickers,
       entity pickers, period picker, discard-changes dialog, sync icon).
7. [x] Features: currencies, category groups & categories, accounts, operations (list/edit/filter/rate),
       planning, reports (linear, pie, measures), database (import/export/reset), settings
       (interface, security, privacy, contacts, cloud sync, auth, subscription), passcode/biometric lock.
8. [x] Build, unit tests (sync compatibility, calculator, CSV, dates), lint clean-up.

## iOS → Android UI mapping
| iOS | Android |
|---|---|
| TabView | `NavigationBar` + Navigation Compose |
| `.sheet` edit forms | full-screen routes with TopAppBar (close / save) |
| swipeActions | swipe-left reveal (`SwipeableListItem`, from the previous Android app): Edit + Delete; operations Copy + Delete |
| confirmationDialog | `AlertDialog` |
| Menu picker (forms) | `ExposedDropdownMenuBox` |
| Menu picker (settings) | list preference: row + radio-button dialog |
| `.searchable` | rounded search field at the top of the list |
| Calculator sheet | read-only `OutlinedTextField` opening a calculator bottom sheet (precedence, live result, backspace) — ported from the previous Android app |
| Editors (all) | previous Android app layout (`EditScaffold`): record/type color fills top third behind a transparent bar (← / Save), centered icon, white `EditorCard`s on the Settings background; pickers are tap-to-open outlined fields (`PickerField`) |
| Colored report rows | `ListItem` with color dot, share %, amount; months use theme colors |
| Drag-reorder list | drag handle via `sh.calvin.reorderable` (no Material 3 equivalent) |
| "+" in toolbar | FAB; operations use a speed-dial FAB with scrim |
| ColorPicker | palette + HSV sliders dialog |
| Swift Charts donut | Compose `Canvas` donut |
| Face ID | `BiometricPrompt` |

## Status
All iOS features are ported with native Android UI (no iOS look-alikes); `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passes.
Not yet run on a device/emulator.

Android-only differences (deliberate):
- Sign in with Apple is omitted (Google + email/password + anonymous remain).
- Swipe reveals two actions like the previous Android app; Copy for accounts/categories/groups/currencies is not offered there.
- Security settings (passcode + biometrics) are reachable from Settings (the iOS screen exists but isn't linked).
- Fixed iOS quirks: deleting a category now checks operations that use it (iOS checked the wrong column);
  "does not contain" comment filter includes operations without a comment; uploads only mark rows
  synced if they weren't edited mid-upload; report drill-down keeps the report's period/types.

## Before release
1. Firebase console → project `fintracker-budget` → Android app `kz.alem.corporation.fintracker`:
   add the debug and release SHA-1/SHA-256 fingerprints (needed for Google sign-in), then
   re-download `app/google-services.json` (current file has no Android OAuth client).
2. Play Console: create subscriptions `kz.alem.corporation.fintracker.sync.monthly` and
   `kz.alem.corporation.fintracker.sync.yearly` (one base plan each).
3. Backend: `verifyPlaySubscription` in `fintracker-backend/src/services/google-play.service.ts`
   still throws. Until it is implemented, Android purchases are not recorded server-side, so
   Android-only subscribers get 403 from `/sync/*`. (Subscriptions bought on iOS already work on Android.)
4. Release signing config (see the old project's `local.properties` keys) and a release build check.

## Maintenance
- `app/objectbox-models/default.json` holds ObjectBox UIDs: keep it under version control.
- Strings: regenerate with `tools/generate_strings.py`; Android-only strings live in `strings_android.xml`.
