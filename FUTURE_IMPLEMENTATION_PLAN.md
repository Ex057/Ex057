# Ex057 Future Implementation Plan

This document translates the notes in `futureIMP.docx` into an implementation-ready plan for work on branch `Capv2`.

## Objectives

1. Improve trade approval flexibility without removing risk controls.
2. Build a more reliable real-time feed architecture.
3. Add persistent demo trading continuity.
4. Improve UI transparency around connection, stale data, and trade decisions.
5. Replace heuristic sizing, charting, and signal validation with instrument-aware and replay-backed behavior.

## Prediction Analysis Rework Track

Preserved branch: `prediction-analysis-lab`

Current research branch: `kronos-research`

### Why This Track Exists

- Current lot sizing is not instrument-aware. The app maps `lotSize` to `stakeUsd` with a fixed multiplier, which cannot match Deriv index contract behavior, spread impact, or symbol-specific point value.
- Current chart rendering is clipped to a fixed candle window and painted into a fixed canvas width, so the chart cannot scroll through a deeper live history like MT5.
- Current signal evidence is still heuristic. It scores the latest market state, but it does not replay the strategy candle by candle and measure whether the entry logic actually performs.

### Immediate Findings In The Current Code

- `PriceViewModel` currently converts volume with a flat `lotSize * 10_000` model for both market and pending orders.
- `DerivWebSocketClient` stores only a short rolling tick buffer and replaces visible candle history with the latest loaded stack.
- `ChartCard` limits the Home chart to the latest `50` candles.
- `ChartsPanel` limits the full chart screen to the latest `120` candles.
- `PriceChart` compresses every candle into the current canvas width, so there is no real pan/scroll window.
- `AnalysisSupport` and the related engine classes still produce dynamic but heuristic evidence instead of replay-derived trade statistics.

### Rework Goals

- Define symbol specifications for each tradable instrument:
  - contract size
  - minimum volume
  - volume step
  - tick size
  - quoted spread handling
  - pip or point value
- Replace synthetic stake conversion with a position-sizing engine that calculates:
  - monetary risk from stop distance
  - estimated cost from spread and execution slippage
  - valid volume rounded to the instrument step
- Replace the fixed-width chart with a scrollable candle viewport that supports:
  - larger local history
  - pinch or zoom-ready spacing model
  - auto-follow latest candle only when the user has not scrolled away
- Add a replay engine that runs the same entry and exit rules over stored candles and produces:
  - win rate
  - expectancy
  - drawdown
  - setup-specific performance
  - regime-specific performance by symbol and timeframe

### Proposed Implementation Order

1. Build a `SymbolSpec` layer and move lot validation out of the UI-facing `ViewModel`.
2. Introduce a chart data repository with a larger retained candle window per symbol and timeframe.
3. Replace the current chart canvas with a viewport-based renderer backed by horizontal scroll state.
4. Build a replay and trade-labeling engine that can evaluate the current strategy on historical candles.
5. Use replay results to tune thresholds in `StrategyEvaluator` and replace heuristic evidence in the UI.

### Lessons From Kronos -> Changes For ex57

#### 1. Sequence forecasting, not only signal scoring

What Kronos teaches:

- Market direction should be inferred from a long sequence of candles, not only from the latest heuristic checks.
- The model reasons over a fixed historical context window before forecasting future candles.

What ex57 should change:

- Stop treating the current bias engine as the only source of truth for entries.
- Add a forecast-context layer that evaluates the next `N` candles from the last `256-512` candles of history.
- Use that forecast layer as a second opinion before promoting a setup to `Eligible`.

#### 2. Strict dataset shape and clean timestamps

What Kronos teaches:

- Prediction quality depends on consistent candle schema and consistent timestamps.
- Training and inference both expect ordered OHLC-style series with explicit time features.

What ex57 should change:

- Build a `DerivPredictionDataset` pipeline that stores:
  - symbol
  - timeframe
  - timestamps
  - open
  - high
  - low
  - close
- Add derived time features for:
  - minute
  - hour
  - weekday
  - day
  - month
- Ensure the same cleaned candle format is used by:
  - live analysis
  - offline replay
  - future Kronos experiments

#### 3. Normalize per window, not per raw market scale

What Kronos teaches:

- Each input window is normalized before inference so the model is not dominated by raw price scale.
- This matters when instruments have very different price ranges.

What ex57 should change:

- Normalize signal inputs per symbol and per rolling window before scoring.
- Stop relying only on absolute move thresholds that behave differently across Deriv forex pairs and synthetic indices.
- Re-express more of the analysis in relative terms:
  - return
  - range expansion
  - wick ratio
  - distance from local mean
  - stop distance in normalized units

#### 4. Predict a path, then derive a trade

What Kronos teaches:

- The useful output is not just a label like bullish or bearish.
- It predicts the shape of the next candles, then downstream logic can decide what that means.

What ex57 should change:

- Separate the system into:
  - forecast generation
  - trade interpretation
  - execution filtering
- Derive entry quality from forecast structure such as:
  - expected next-candle direction
  - expected range expansion or contraction
  - expected pullback depth before continuation
  - expected invalidation level
- Use the forecasted path to improve:
  - entry price placement
  - stop placement
  - take-profit realism

#### 5. Confidence should come from forecast stability

What Kronos teaches:

- It supports multiple sampled forecasts and averages them.
- This creates a natural way to judge whether a view is stable or noisy.

What ex57 should change:

- Replace part of the current hand-tuned confidence logic with forecast-agreement logic.
- Estimate confidence from:
  - agreement across multiple forecast runs
  - consistency between forecast direction and current heuristic bias
  - replay win rate for similar regimes
- Downgrade setups when forecast paths disagree materially.

#### 6. Evaluation must be replay-based

What Kronos teaches:

- Prediction quality should be measured against held-out future candles, not assumed from live-looking charts.
- Regression and error testing are treated as first-class checks.

What ex57 should change:

- Build a rolling replay evaluator for every symbol and timeframe.
- Measure:
  - directional accuracy
  - MAE and MSE on next-candle close or range
  - entry hit rate
  - average adverse excursion
  - average favorable excursion
  - expectancy after spread and execution assumptions
- Stop trusting a new signal rule until it improves replay metrics over the current heuristic baseline.

#### 7. Batch research matters

What Kronos teaches:

- The same prediction engine can be evaluated across many series in parallel.

What ex57 should change:

- Research symbols in batches instead of guessing from one chart at a time.
- Rank Deriv symbols and timeframes by:
  - prediction stability
  - directional edge
  - replay expectancy
  - drawdown profile
- Only expose higher-confidence markets prominently in the app once this ranking exists.

### ex57 Signal Cleanup Plan

#### Phase A: Clean the current heuristic engine

- Reduce overconfident approvals from weak confluence.
- Rework thresholds so `Eligible` means both directional alignment and acceptable replay behavior.
- Add explicit downgrade reasons when the setup is blocked by:
  - poor forecast agreement
  - weak structural context
  - spread or stop-distance inefficiency
  - unstable recent regime

#### Phase B: Add a forecast research layer beside the current engine

- Keep the current heuristic engine as a baseline.
- Introduce an offline forecast module inspired by Kronos data handling.
- Compare:
  - heuristic-only bias
  - forecast-only direction
  - blended heuristic + forecast decision

#### Phase C: Promote only the parts that improve entries

- If Kronos-style forecasting improves directional timing, integrate it.
- If it only improves regime filtering, use it only as a market-selection filter.
- Do not replace the whole app engine unless replay shows consistent improvement after spread-adjusted evaluation.

### Guardrails For Applying Kronos Ideas

- Do not copy Kronos model code directly into the Android app.
- Do not assume stock-market training transfers cleanly to Deriv synthetic indices.
- Do not trust candle prediction accuracy alone; convert it into trading metrics first.
- Do not use forecast confidence without spread, stop-distance, and lot-sizing impact.

### External Reference Work

- Review the cloned sibling repository at `/Users/edwinarinda/StudioProjects/Kronos` for reusable architecture and model assumptions.
- Compare its data ingestion, feature extraction, prediction pipeline, and evaluation loop against this branch before lifting any logic.
- Do not merge prediction logic from `Kronos` directly until the contract model and evaluation metrics are aligned with Deriv instruments.

## Progress Tracker

- [x] Added a first implementation plan from `futureIMP.docx`.
- [x] Added structured feed state support (`Connecting`, `Waiting for market data`, `Live`, `Stale`, `Reconnecting`, `Feed error`).
- [x] Added reconnect behavior with backoff in the current feed client.
- [x] Added persistent local cache for last market session state.
- [x] Surfaced cached/live feed status and last tick time in the UI.
- [x] Started accessibility handling for large-font bottom navigation behavior.
- [x] Removed the chart trade-map toggle and its extra content.
- [x] Fixed oversized settings text and unstable mode preset buttons on large-font devices.
- [x] Exposed the demo simulator more clearly in the UI.
- [x] Started a broader visual cleanup for header scaling and key dashboard cards.
- [x] Renamed `Backtest` to `Demo View`.
- [x] Removed legacy backtesting content from that screen.
- [x] Reworked `Demo View` toward a simpler terminal-style running-trades screen.
- [x] Added explicit lot size / volume input for opening demo trades.
- [x] Showed live dollar profit/loss motion per running trade.
- [x] Added account metrics such as balance, equity, margin, free margin, and margin level.
- [x] Added first-pass trade management automation for scaling out and stop adjustment.
- [x] Allowed more than one demo entry on the same pair/timeframe.
- [x] Moved trade closing responsibility away from Home and into `Demo View`.
- [x] Added a safer guarded close interaction in `Demo View`.
- [x] Introduced a first side management panel for `Demo View`.
- [x] Shifted `Demo View` styling closer to an MT5-like light trade-terminal layout.
- [x] Removed the old `Markets` tab from the main navigation.
- [x] Renamed `Demo View` to `Trades` to match its actual purpose.
- [x] Split closed trades into a dedicated `History` screen.
- [x] Added MT5-style right-to-left swipe management actions on live trades.
- [x] Removed the left-side trade rail and rebuilt `Trades` as a full-width terminal surface.
- [x] Removed empty-state copy from `Trades` so the terminal stays visually clean when no positions are open.
- [x] Tightened `Trades` and `History` rows to fit more records on screen at once.
- [x] Restored the ability to add another trade on the same pair/timeframe from Home.
- [x] Reduced accidental auto-closes by requiring stop/target or management thresholds to be crossed by live movement, not merely rediscovered after reconnect.
- [x] Made swipe actions settle back after use so trade management feels less sticky.
- [x] Added clearer close context in `History` rows.
- [x] Reworked `History` toward an MT5-style terminal layout with period filtering and tap-for-details trade popups.
- [x] Refined `Trades` with a simpler MT5-style terminal layout, icon-based swipe actions, and close confirmation.
- [x] Removed the old Alerts screen from the main flow and replaced it with a chart-focused screen.
- [x] Expanded `Trades`, `History`, and `Charts` to use screen space more efficiently with less wasted header copy.
- [x] Centralize analysis thresholds into config.
- [x] Add explicit rejection-reason output.
- [x] Add fuller demo account continuity and management UX.
- [x] Add fuller swipe actions such as modify / partial close / chart jump.
- [x] Add explicit pending-order style secondary entries, not only stacked market entries.

## Primary Problems To Solve

### Trade evaluation is too strict

- Multiple hard gates cause too many rejections or watchlist outcomes.
- Thresholds are hard-coded across the analysis layer.
- Conservative and Moderate modes require too many aligned signals.
- Expectancy and noise/news gates can block otherwise usable setups.
- Users cannot see clearly why a setup was rejected.

### Feed reliability is too weak

- Analysis depends too directly on immediate live ticks.
- Reconnect and re-subscribe behavior is not robust enough.
- The app can feel dead when the socket is connected but no fresh ticks are arriving.
- There is no strong stale-data state or fallback behavior.

### Demo flow is not durable

- Demo positions should survive app restart and reconnect.
- Balance, floating PnL, and last known market state should be restored locally.
- The current trade-monitoring view is still too generic and does not yet feel like a live trade terminal.
- The current trade terminal still needs fuller management actions and clearer order/deal separation.

## Desired After-State

After this implementation:

- Cached chart and state load immediately on launch.
- Live feed reconnects automatically with visible status.
- Active subscriptions recover after reconnect.
- Tick buffering and local candle building keep analysis stable.
- Demo trades, balance, and last signal state survive app restarts.
- The old `Backtest` tab becomes a dedicated `Trades` tab for running trades.
- A dedicated `History` tab shows closed deals and exit outcomes.
- Open demo trades show live positive/negative dollar movement clearly.
- The demo screen feels like a trading terminal instead of a report page.
- Users can see why a trade was rejected, downgraded, or approved.
- Threshold tuning becomes easier and safer to iterate.

## Scope

## Workstream 1: Trade Evaluation Flexibility

### Goals

- Externalize mode thresholds into one config model.
- Relax non-conservative gating where it is currently too strict.
- Reduce hard rejections in cases that should become warnings or risk flags.
- Expose clearer rejection reasons in the UI.

### Proposed Changes

- Create a `ModeConfig` model for confirmation thresholds.
- Replace scattered `when`-based thresholds in analysis helpers with config-driven lookups.
- Revisit these values per mode:
  - minimum core confirmations
  - bias activation threshold
  - directional strength threshold
  - setup quality threshold
  - required setup confirmations
  - confluence gate
  - directional edge threshold
  - maximum risk penalty
- Relax expectancy gating so it contributes to confidence/risk before it blocks approval.
- Rework noise/news hard-block behavior so non-conservative modes degrade more gracefully.
- Surface failed conditions in the analysis output shown to the user.

### Files Likely To Change

- `app/src/main/java/com/ex57/capital/analysis/AnalysisSupport.kt`
- `app/src/main/java/com/ex57/capital/analysis/StrategyEvaluator.kt`
- `app/src/main/java/com/ex57/capital/analysis/RiskManager.kt`
- `app/src/main/java/com/ex57/capital/analysis/FeatureExtractor.kt`
- `app/src/main/java/com/ex57/capital/analysis/PositionManager.kt`
- `app/src/main/java/com/ex57/capital/model/Models.kt`

### Deliverables

- Centralized threshold config for all confirmation modes.
- Updated analysis logic with softer non-conservative gating.
- Rejection/failure reason model that the UI can present clearly.
- Updated confirmation summaries and user-facing diagnostics.

## Workstream 2: Robust Real-Time Feed Architecture

### Goals

- Make the app reliable around market open, temporary disconnects, and app resume.
- Allow analysis to continue from buffered or cached market state.
- Distinguish connecting, live, waiting, stale, reconnecting, and error states.

### Target Pipeline

`WebSocket feed -> Connection Manager -> Tick Buffer -> Candle Builder -> Analysis Engine -> Demo Trade Engine -> UI`

### Core Components

#### 1. Connection Manager

Responsible for:

- socket open/close
- reconnect with exponential backoff
- re-subscribe after reconnect
- exposing current feed state

#### 2. Subscription Registry

Responsible for:

- tracking active symbols
- restoring subscriptions after reconnect
- preventing duplicate subscriptions

#### 3. Tick Buffer

Responsible for:

- rolling per-symbol tick history in memory
- supporting short-term analysis from recent market activity
- enabling local candle rebuilding

#### 4. Candle Builder

Responsible for:

- generating 1m candles from ticks
- aggregating to higher timeframes such as 5m, 15m, and 1h

#### 5. Persistent Cache

Responsible for:

- storing recent candles
- storing last known signal state
- storing last update time
- restoring usable chart state on launch

#### 6. Demo Trade Engine

Responsible for:

- opening simulated long/short positions
- tracking floating PnL
- closing on TP/SL or manual action
- updating local balance

### Files Likely To Create

- `app/src/main/java/com/ex57/capital/data/websocket/FeedConnectionManager.kt`
- `app/src/main/java/com/ex57/capital/data/websocket/SubscriptionRegistry.kt`
- `app/src/main/java/com/ex57/capital/data/repository/MarketFeedRepository.kt`
- `app/src/main/java/com/ex57/capital/data/repository/DemoTradeRepository.kt`
- `app/src/main/java/com/ex57/capital/domain/model/FeedState.kt`
- `app/src/main/java/com/ex57/capital/domain/model/Tick.kt`
- `app/src/main/java/com/ex57/capital/domain/model/Candle.kt`
- `app/src/main/java/com/ex57/capital/domain/model/DemoTrade.kt`
- `app/src/main/java/com/ex57/capital/domain/model/DemoAccount.kt`
- `app/src/main/java/com/ex57/capital/domain/engine/TickBuffer.kt`
- `app/src/main/java/com/ex57/capital/domain/engine/CandleBuilder.kt`
- `app/src/main/java/com/ex57/capital/domain/engine/DemoTradeEngine.kt`
- `app/src/main/java/com/ex57/capital/domain/engine/PositionSizingEngine.kt`
- `app/src/main/java/com/ex57/capital/data/local/AppDatabase.kt`
- `app/src/main/java/com/ex57/capital/data/local/dao/CandleDao.kt`
- `app/src/main/java/com/ex57/capital/data/local/dao/DemoTradeDao.kt`
- `app/src/main/java/com/ex57/capital/data/local/dao/DemoAccountDao.kt`

### Files Likely To Refactor

- `app/src/main/java/com/ex57/capital/data/DerivWebSocketClient.kt`
- `app/src/main/java/com/ex57/capital/data/MarketDataSource.kt`
- `app/src/main/java/com/ex57/capital/data/PriceViewModel.kt`
- `app/src/main/java/com/ex57/capital/MainActivity.kt`

### Deliverables

- Auto-reconnect with exponential backoff.
- Guaranteed re-subscribe flow after reconnect.
- Stale-data detection per active symbol.
- Tick buffering and local candle aggregation.
- Persistent local market/demo state.
- Feed state model suitable for Compose UI.

## Workstream 3: Persistent Demo Trading

### Goals

- Keep demo account state after app close and reopen.
- Track floating PnL from live ticks and restore open positions.
- Turn the old `Backtest` area into the main `Trades` terminal.
- Support a more broker-terminal-like experience for monitoring and managing demo positions.

### Needed Data

- demo balance
- equity
- used margin
- free margin
- margin level percent
- open positions
- closed positions
- entry price
- current price
- lot size / volume
- notional or stake
- stop loss
- take profit
- size / risk amount
- pnl dollars
- floating PnL
- updated timestamp

### Deliverables

- Persistent demo account storage.
- Persistent open/closed demo trade storage.
- Demo trade engine integrated with live and cached prices.
- `Trades` UI that restores and updates open demo trades.
- Live per-position P/L in both dollars and percent.
- Track/reconnect flow so a user can resume motion on a selected trade pair.
- Volume or lot-size selection when opening a demo trade.
- First-pass automated management rules for break-even and partial scale-out behavior.

### Trades Product Direction

The old `Backtest` tab should be repurposed into `Trades`.

This screen should no longer be a backtesting/report surface. Its job is:

- show the account health summary
- show all running demo trades
- show each trade's live movement in dollars and percent
- allow the user to reconnect or focus a trade pair quickly
- feel visually closer to an MT5-style trade terminal

### Trades Target Layout

#### Header / Account Summary

- dark, high-contrast terminal-like presentation
- floating total P/L centered and visually dominant
- account metrics clearly grouped:
  - balance
  - equity
  - margin
  - free margin
  - margin level percent

#### Running Trades List

Each row should show:

- symbol
- side
- lot size
- entry price
- current price
- individual P/L in dollars
- individual P/L in percent
- optional stop loss / take profit state

#### Interaction Model

Near-term implementation:

- tap or button-based actions are acceptable
- `Track Live` should reconnect the feed for that trade symbol
- close / modify actions can begin as explicit controls

Later polish:

- swipe actions for close / modify / chart
- long-press context actions

### Margin Model Requirements

The demo engine should begin calculating these in real time:

- `Equity = Balance + floating P/L`
- `Free Margin = Equity - Used Margin`
- `Margin Level % = (Equity / Used Margin) * 100`

The app should also define warning thresholds such as:

- margin call threshold
- stop out threshold

These do not have to fully auto-liquidate in the first pass, but the model should be designed so that can be added.

## Workstream 4: UX Transparency

### Goals

- Make the app explain what state it is in.
- Make analysis decisions understandable instead of opaque.
- Keep navigation usable on devices with large system font sizes.

### Needed UI States

- `Connecting`
- `Connected`
- `Waiting for market data`
- `Live`
- `Stale`
- `Reconnecting`
- `Feed error`

### UI Improvements

- Show visible feed status near the market/analysis area.
- Show last update timestamp for live data.
- Mark stale analysis so old advice is not presented as fresh.
- Prevent the bottom navigation bar from becoming oversized or unstable under large accessibility font scaling.
- Remove the old chart trade-map expansion block to reduce clutter.
- Rework settings controls so large-font devices do not break preset buttons or switches.
- Make the demo simulator visible without the user needing to infer it from trade buttons alone.
- Raise the overall visual quality of the interface instead of only patching functionality.
- Replace the current report-style demo/trade view with a denser terminal-style layout.
- Prioritize legibility of live P/L movement, equity, and margin health.
- Make margin level visually more urgent when it enters danger thresholds.
- Display rejection reasons like:
  - directional strength below threshold
  - confluence below threshold
  - expectancy weak
  - noise/news veto applied

### Files Likely To Change

- `app/src/main/java/com/ex57/capital/MainActivity.kt`
- `app/src/main/java/com/ex57/capital/ui/ControlsPanel.kt`
- other Compose screens tied to analysis, alerts, and positions

### Accessibility Requirement

- Bottom navigation must remain visually stable with large system font settings.
- Navigation labels should adapt or compact before the bar grows enough to crowd content.
- Selected state must stay obvious even when labels are reduced or hidden.

## Implementation Phases

### Phase 1: Planning and Current-State Audit

- Audit the current analysis flow and extract all hard-coded thresholds.
- Audit the current feed lifecycle and identify state gaps.
- Confirm existing UI screens for analysis, alerts, and positions.
- Decide whether persistence uses Room only or Room plus DataStore.

### Phase 2: Config-Driven Analysis

- Introduce `ModeConfig` and central threshold access.
- Refactor analysis helpers to use config instead of scattered constants.
- Add structured rejection reasons to evaluation output.
- Keep current behavior first, then tune values in one place.

### Phase 3: Feed Reliability Foundation

- Split socket lifecycle from direct market consumption.
- Add connection manager and subscription registry.
- Add reconnect backoff and automatic re-subscribe.
- Add stale feed detection and surfaced feed state.

### Phase 4: Tick Buffer and Candle Builder

- Create in-memory rolling tick buffers per symbol.
- Build local 1m candles from ticks.
- Aggregate higher timeframes from local candles where possible.
- Route analysis through buffered/cached candles instead of raw latest tick only.

### Phase 5: Persistence Layer

- Add local storage for candles, demo account, and demo trades.
- Restore cached state on launch before live feed is ready.
- Persist last signal snapshot and last update metadata.

### Phase 6: Demo Trade Continuity

- Create demo trade repository and engine.
- Restore open trades on app launch.
- Resume floating PnL updates when feed reconnects.
- Support manual close plus TP/SL closure logic.
- Add lot-size-aware demo positions and account metric calculations.
- Move the running-trade terminal fully into `Trades`.

### Phase 7: UI Reliability and Explanations

- Add feed state presentation in Compose.
- Add stale/live labels and timestamps.
- Show trade decision reasons and downgrade causes.
- Ensure chart and positions screens remain informative while offline/stale.
- Redesign `Trades` with an MT5-style summary-and-positions hierarchy.
- Remove residual backtesting copy and labels from the repurposed tab.

### Phase 8: Validation

- Test reconnect around app background/foreground.
- Test launch with no network and with stale cache.
- Test demo trade restore after app restart.
- Compare approval counts before and after threshold tuning.
- Add deterministic tests for feed state transitions and candle aggregation.

## Requirements

### Technical Requirements

- Keep architecture local-first and low-cost.
- Use on-device persistence before any backend dependency.
- Avoid breaking the current analysis engine while refactoring.
- Preserve compatibility with current Compose app structure.

### Likely Libraries / Platform Needs

- Kotlin Coroutines
- `StateFlow`
- Room
- DataStore
- existing Deriv WebSocket integration

### Product Requirements

- Fast first render from cached state.
- Reliable reconnect behavior.
- Clear state messaging.
- Persistent demo account continuity.
- More flexible trade approval behavior without removing risk awareness.

## Risks

- Analysis tuning can increase bad trades if thresholds are relaxed blindly.
- Feed refactor can destabilize the existing live path if done in one large change.
- Persistence schema choices may require migration work later.
- Local-only demo persistence means no multi-device sync.

## Mitigations

- Refactor in phases behind clear interfaces.
- Add tests before tuning thresholds aggressively.
- Keep old behavior as default config first, then tune values incrementally.
- Build feed-state visibility early so failures are obvious during development.

## Recommended Order Of Execution

1. Create the planning and config foundation.
2. Refactor analysis thresholds into one config model.
3. Introduce feed state and reconnect architecture.
4. Add tick buffer and candle builder.
5. Add persistence for candles and demo state.
6. Add demo trade continuity.
7. Convert `Backtest` into `Trades` and implement the terminal-style trade screen.
8. Upgrade UI state visibility and rejection explanations.
9. Tune thresholds with observed behavior and tests.

## Definition Of Done

This work is done when:

- the app can recover from temporary feed drops without appearing dead
- subscriptions are restored after reconnect
- cached chart state is visible immediately on launch
- open demo trades survive restart and continue updating
- feed state is visible to the user at all times
- rejection reasons are visible and understandable
- analysis thresholds are centralized and tunable
- the app is materially more reliable without requiring paid infrastructure
