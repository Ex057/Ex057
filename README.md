# EX57 Capital

Kotlin + Jetpack Compose Android app focused on market direction analysis, setup quality, and risk-aware trade planning.

## What This App Does

- Connects to live/cached market candles
- Runs a multi-factor analysis engine
- Produces `Bias`, `Decision`, confidence, and a structured trade plan (entry/SL/TP)
- Shows AI commentary from structured context (Mock/OpenAI/Ollama/LocalAI)
- Includes an in-app Validation tab for one-shot and walk-forward checks

## Tech Stack

- Kotlin
- Jetpack Compose (Material 3)
- Android Gradle Plugin `8.5.2`
- JDK `17`
- Compile SDK `35`

## Build and Run

### Android Studio (recommended)

1. Open this project in Android Studio.
2. Sync Gradle.
3. Start emulator/device.
4. Run the `app` configuration.

### Terminal

This repo may not include `gradlew` wrapper scripts. If missing, use global `gradle`:

```bash
gradle assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Shareable copy (if task runs successfully):

```text
app/build/outputs/shareable/
```

## Main Screens

- **Home**: controls, historical context, prediction summary, AI insights, signal console
- **Charts**: expanded chart with overlays and execution levels
- **Validation**: deterministic scenario suite + walk-forward evaluation
- **Settings**: mode, filters, AI provider, theme, strategy placeholder

## Analysis Pipeline (Current)

1. `FeatureExtractor` builds trend, momentum, structure, MTF, indicator features.
2. `ForecastResearchEngine` computes heuristic forecast support.
3. `ForecastModelEngine` computes sequence-style metrics:
   - bullish/bearish probabilities
   - direction confidence
   - forecast dispersion
   - expected move
   - target-before-stop score
4. `StrategyEvaluator` applies mode thresholds and gate logic.
5. `RiskManager` builds result summary, risk note, and trade setup.

Key files:

- [AnalysisStub.kt](./app/src/main/java/com/ex57/capital/analysis/AnalysisStub.kt)
- [FeatureExtractor.kt](./app/src/main/java/com/ex57/capital/analysis/FeatureExtractor.kt)
- [ForecastModelEngine.kt](./app/src/main/java/com/ex57/capital/analysis/ForecastModelEngine.kt)
- [StrategyEvaluator.kt](./app/src/main/java/com/ex57/capital/analysis/StrategyEvaluator.kt)
- [RiskManager.kt](./app/src/main/java/com/ex57/capital/analysis/RiskManager.kt)

## AI Insights

Providers:

- `MOCK`
- `LOCALAI`
- `OLLAMA`
- `OPENAI`

Deep Dive is instructed to do an independent market read from structured context, not blindly echo app labels.

Key files:

- [AiPromptComposer.kt](./app/src/main/java/com/ex57/capital/ai/AiPromptComposer.kt)
- [AiInsightsService.kt](./app/src/main/java/com/ex57/capital/ai/AiInsightsService.kt)

## Validation: How It Works

Validation tab has three runners:

1. **Run One On Current Data**
   - Uses current symbol/timeframe candles.
   - Runs `AnalysisStub` once with selected mode.
   - Good for quick logic sanity checks.

2. **Run Walk-Forward (All Modes)**
   - Replays recent history index-by-index.
   - At each index, runs analysis with past-only candles.
   - Checks forward candles for TP/SL sequence outcome.
   - Outputs per-mode scorecard + clickable prediction rows.

3. **Run Synthetic Scenario Suite**
   - Deterministic scenarios (uptrend/downtrend/range/noise/exhaustion).
   - Checks whether mode-specific expectations pass.

Important:

- Walk-forward outcomes are historical evaluation.
- Home predictions are forward projections, not realized outcomes.

Relevant code:

- [MainActivity.kt](./app/src/main/java/com/ex57/capital/MainActivity.kt) (Validation section)
- [AnalysisPipelineModelValidationTest.kt](./app/src/test/java/com/ex57/capital/analysis/AnalysisPipelineModelValidationTest.kt)

## Current Limitations

- No full broker-backed execution or cloud sync.
- Evidence is still partially heuristic; not a full institutional backtest engine.
- Local build via terminal may fail in some environments if native Gradle libraries mismatch.
- AI quality depends on chosen provider/model and context quality.

## Troubleshooting

### Missing `./gradlew`

Use Android Studio or global `gradle`.

### Gradle native library error

If terminal build fails with native-platform errors, use Android Studio build tasks on the same machine.

### Clear stale build outputs

```bash
rm -rf app/build build .gradle
```

Then re-sync/rebuild.

## Project Metadata

- App ID: `com.ex57.capital`
- Min SDK: `26`
- Target SDK: `35`
- Version: `0.1.0`
