# Ex057 Future Implementation Plan

This document translates the notes in `futureIMP.docx` into an implementation-ready plan for work on branch `Capv2`.

## Objectives

1. Improve trade approval flexibility without removing risk controls.
2. Build a more reliable real-time feed architecture.
3. Add persistent demo trading continuity.
4. Improve UI transparency around connection, stale data, and trade decisions.

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
- [ ] Centralize analysis thresholds into config.
- [ ] Add explicit rejection-reason output.
- [ ] Add fuller demo account continuity and management UX.

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

## Desired After-State

After this implementation:

- Cached chart and state load immediately on launch.
- Live feed reconnects automatically with visible status.
- Active subscriptions recover after reconnect.
- Tick buffering and local candle building keep analysis stable.
- Demo trades, balance, and last signal state survive app restarts.
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
- Support later expansion into a stronger position-management screen.

### Needed Data

- demo balance
- open positions
- closed positions
- entry price
- stop loss
- take profit
- size / risk amount
- floating PnL
- updated timestamp

### Deliverables

- Persistent demo account storage.
- Persistent open/closed demo trade storage.
- Demo trade engine integrated with live and cached prices.
- Positions or Alerts UI that restores and updates open demo trades.

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

### Phase 7: UI Reliability and Explanations

- Add feed state presentation in Compose.
- Add stale/live labels and timestamps.
- Show trade decision reasons and downgrade causes.
- Ensure chart and positions screens remain informative while offline/stale.

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
7. Upgrade UI state visibility and rejection explanations.
8. Tune thresholds with observed behavior and tests.

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
