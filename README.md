# EX57 Capital

Android app built with Kotlin, Jetpack Compose, and Gradle.

## Documentation

- See [GUIDE.md](./GUIDE.md) for the full usage guide.
- Use this README mainly for setup, build, and project status.

## Requirements

- Android Studio Koala or newer
- Android SDK 35
- JDK 17
- Gradle 8.7 if you want to build from the terminal before the wrapper scripts are added

## Important note about `./gradlew`

This repository currently includes `gradle/wrapper/gradle-wrapper.properties` but does **not** include the wrapper launch scripts (`gradlew` and `gradlew.bat`).

That is why this fails:

```bash
./gradlew
```

You have two ways to run the project.

## Option 1: Run with Android Studio

1. Open the project folder in Android Studio.
2. Let Gradle sync finish.
3. Start an emulator or connect an Android device.
4. Click `Run` on the `app` configuration.

This is the simplest option if `gradlew` is missing.

## Option 2: Run from the terminal

If you have Gradle installed globally, use `gradle` instead of `./gradlew`.

Important: use Gradle `8.7` for this project. The Android Gradle Plugin here is `8.5.2`, and newer Gradle versions such as `9.x` can fail with broken intermediate outputs during `assembleDebug`.

### Build debug APK

```bash
gradle assembleDebug
```

After a successful build, the APK will be available at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

This project also copies a shareable APK to:

```text
app/build/outputs/shareable/EX57-Capital-debug.apk
```

### Install on a connected device or running emulator

```bash
gradle installDebug
```

### Clean build files

```bash
gradle clean
```

## Optional: add the Gradle wrapper scripts

If you want `./gradlew` to work in this repo, generate the wrapper files once:

```bash
gradle wrapper
chmod +x gradlew
```

After that, you can use:

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## Project info

- App name: `EX57 Capital`
- Application ID: `com.ex57.capital`
- Min SDK: `26`
- Target SDK: `35`
- Compile SDK: `35`
- Java target: `17`

## App workflow

This app is meant to be used in a simple loop:

1. Choose a market, timeframe, and confirmation mode.
2. Tap `Connect Feed` to start live data.
3. Tap `Analyze` to generate a bias and trade plan.
4. Review the `Signal Console`, `Trade Plan`, and chart.
5. If the setup is eligible, open the position from the `Trade Plan` card.
6. Later, analyze the same market again to get management guidance for the open trade.

## Current feature state

The app currently includes:

- explicit MTFA stack output:
  - `Macro`
  - `Structure`
  - `Setup`
  - `Trigger`
- chart overlays for:
  - fast trend
  - slow trend
  - bias line
  - entry, stop, and take-profit levels
- an expandable chart trade map
- a simple in-app pair monitor on the `Alerts` screen
- broader instrument coverage:
  - more forex pairs
  - more volatility indices
  - 1-second volatility indices
  - crash and boom indices
- four confirmation modes:
  - `Conservative`
  - `Moderate`
  - `Aggressive`
  - `Lenient`
- grouped confirmation logic:
  - direction engine
  - setup engine
  - risk engine

## Evidence tab status

The values shown in the `Evidence` card and `Backtest` tab are currently computed at runtime, but they are **not** yet true realized backtest statistics.

What that means:

- they are **not** hard-coded constants typed into the UI
- they are calculated dynamically from the currently loaded candles and recent prices
- they are currently based on an internal heuristic snapshot builder, not on a full replay engine over historical trades

Today, values such as:

- win rate
- profit factor
- expectancy
- drawdown
- robustness

are produced from the app's live or recently loaded market data inside the analysis engine, then shown in the UI.

That is better than hard-coded placeholders, but it is still **not the final form** if your requirement is real calculated performance from actual simulated or recorded trades.

If you do **not** want any heuristic evidence, the next required step is:

1. build a replay/backtest engine that runs the same strategy logic candle by candle
2. generate actual entries, exits, and R-multiples
3. compute expectancy, win rate, drawdown, and setup performance from those realized outcomes
4. feed those metrics into the `Evidence` tab instead of the current heuristic evidence snapshot

So the current answer is:

- `Evidence` is dynamic
- `Evidence` is not hard coded
- `Evidence` is not yet a full real-trade or replay-derived statistics engine

Also note:

- `Live Estimate` in the app is still heuristic and dynamic
- `Recorded Trades` in the app are actual locally tracked closed trades

## Recent changes

The recent work improved the internal system structure first, then added visible timeframe support.

### Engine changes

- The old analysis monolith was split into separate components:
  - `FeatureExtractor`
  - `StrategyEvaluator`
  - `RiskManager`
  - `PositionManager`
- `AnalysisStub` is now a thin orchestrator instead of one large file doing everything.
- This makes the system easier to test, easier to replace piece by piece, and easier to connect to a future replay engine.

### Data-source changes

- A `MarketDataSource` interface was added.
- `DerivWebSocketClient` now implements that interface.
- The app is no longer locked architecturally to one feed client at the ViewModel boundary.
- the data layer now loads explicit timeframe stacks for MTFA instead of relying only on synthetic aggregation

### Timeframe changes

Additional timeframes were added:

- `30m`
- `2h`
- `8h`

The timeframe list, Deriv granularity mapping, and analysis thresholds were updated to support them.

### MTFA changes

- the engine now uses explicit timeframe plans such as `4h / 1h / 15m / 5m`
- `Macro` bias is separated from `Structure`, `Setup`, and `Trigger`
- the Home and Alerts screens now show the MTFA stack explicitly

### Confirmation-engine changes

- confirmations are no longer treated as one flat list of equal importance
- the engine now separates:
  - directional confirmations
  - setup-quality confirmations
  - risk / veto filters
- confidence and decisions now rely more on weighted strength than raw confirmation counts
- `Candlestick Engine` and `ICC Phase` now act more like setup-refinement signals than primary directional foundations
- `Noise Filter` and `News Pulse Filter` now behave more like downgrade / veto filters
- `Confluence Gate` is now used more as a meta-check than a duplicate directional score

### Alerts changes

- `Alerts` is now a simple pair monitor, not only a passive history list
- it can recheck one selected pair on a schedule while the app remains open
- it stores timestamped alert entries with trigger guidance

### Instrument coverage changes

The selectable market list now includes:

- more forex pairs
- more synthetic volatility indices
- crash and boom indices

## How the system is better now

The system is better mainly in architecture and extension-readiness, not because of a dramatic UI change yet.

The main improvements are:

- cleaner separation between feature extraction, strategy scoring, risk planning, and position management
- easier future migration from heuristic evidence to real replay-based evidence
- cleaner connector boundary for supporting more brokers or exchanges later
- broader timeframe coverage for analysis
- explicit multi-timeframe hierarchy instead of only inferred same-stream aggregation
- a more coherent direction / setup / risk decision pipeline
- clearer chart-based decision support
- better monitoring flow in Alerts
- less fragile build configuration

What has **not** changed yet:

- the app still does not have a full historical replay/backtest engine
- the Evidence tab still does not come from realized replay trades
- alerts are still not background OS notifications
- the monitor is still one-pair-at-a-time

## How the new trade-memory changes work

The app now remembers open trades locally on the device.

When you open a position, the app stores:

- symbol
- timeframe
- side (`Long` or `Short`)
- entry price
- stop loss
- take profit
- open time
- setup type
- analysis rationale

Because of that, when you come back later and analyze the same symbol/timeframe again, the app does not treat it as a brand new trade idea. Instead it switches to position-management guidance such as:

- `Hold`
- `Scale Out`
- `Exit`
- `Wait`

That is the reason the app no longer keeps “forgetting” that you already took a trade.

## Why `Open Short Position` may not be clickable

The `Open Short Position` button is only enabled when all of these are true:

- the current analysis bias is `Bearish`
- the setup is marked as a real trade candidate
- the decision is strong enough for execution
- there is no already-open position for that same symbol and timeframe

If the button is disabled, it usually means one of these:

- the analysis is `Neutral`
- the analysis is `Bullish`, so the app would only allow a long
- the setup is `Watchlist` or `Reject`, not `Eligible`
- you already have an open position, so the app switches to management mode instead of opening another one

In the UI this shows up as `Forfeit Candidate` instead of `Trade Candidate`.

## How to open and track a trade

### Opening a trade

1. Connect the feed.
2. Run `Analyze`.
3. Check that the `Trade Plan` card shows `Trade Candidate`.
4. If the bias is bearish, tap `Open Short Position`.
5. If the bias is bullish, tap `Open Long Position`.

### Tracking an open trade

After you open a trade, track it from these places:

- `Active Position` card on the Home screen
- `Trade Plan` card, which switches from fresh-entry behavior to management behavior
- `Signal Console`, after you analyze again

The `Active Position` card shows:

- symbol
- side
- timeframe
- open time
- entry price
- live price
- stop loss
- take profit
- unrealized profit/loss

## What to do after 10 minutes

If you come back after 10 minutes, use this workflow:

1. Keep the same symbol and timeframe selected.
2. Tap `Analyze` again.
3. Read the updated guidance in the Home screen.

If there is an open saved position, the app will give management advice instead of a fresh entry signal. Typical outcomes:

- `Hold`: keep the trade open if structure still supports it
- `Scale Out`: lock in some profit or tighten risk
- `Exit`: close the trade because the setup is weakening or invalidated
- `Wait`: do not add to the trade yet

You can then use the `Close Active Position` button if you decide to close it.

## Where closed trades are recorded

When you close a position, it is moved into local trade history.

You can review recent recorded outcomes in the `Backtest` tab under `Recorded Trades`.

That section gives you a simple log of recently closed trades for the selected symbol, including:

- result label
- realized P/L
- close time

## Important limitation

Trade memory is currently local to the app on that device. It is not synced to a broker account or cloud backend.

That means:

- if you reinstall the app, local trade memory may be lost
- if you place a trade outside this app, the app will not know about it automatically
- you must open and close the trade inside this app if you want the memory and tracking features to stay accurate

## Troubleshooting

### `zsh: no such file or directory: ./gradlew`

Cause: the wrapper script is missing from the repository.

Fix:

- Use Android Studio, or
- Run commands with global `gradle`, or
- Generate the wrapper with `gradle wrapper`

### Gradle command not found

Install Gradle locally, or open the project in Android Studio and run it there.

### `:app:mergeDebugResources` fails with `values_values.arsc.flat (No such file or directory)`

This project has also hit a flaky Android resource-merge failure where Gradle tries to compile:

```text
app/build/intermediates/merged_res/debug/mergeDebugResources/values_values.arsc.flat
```

and reports that it does not exist.

The two most common causes here are:

- using the wrong Gradle version instead of `8.7`
- stale incremental build outputs

Try this in order:

1. Make sure the build is running with Gradle `8.7` and JDK `17`.
2. Delete build outputs:

```bash
rm -rf app/build build .gradle
```

3. Sync or rebuild again.

This repo also disables Gradle parallelism and configuration cache in `gradle.properties` to make resource compilation more stable on small local builds.
