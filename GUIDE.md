# EX57 Capital Guide

## Purpose

`EX57 Capital` is a market-analysis and trade-planning app.

It currently helps you:

- connect to a live Deriv market feed
- analyze a symbol with multi-timeframe context
- read bias, setup quality, and trigger state
- open and manage a locally tracked trade
- monitor one pair over time from the `Alerts` screen
- review dynamic evidence estimates and recorded trade history

It does **not** currently place broker orders automatically.

## Core workflow

Use the app in this order:

1. Pick a symbol.
2. Pick a timeframe.
3. Pick a confirmation mode.
4. Tap `Connect Feed`.
5. Wait for live data and candles to load.
6. Tap `Analyze`.
7. Read the `MTFA Stack`, `Signal Console`, chart, and `Trade Plan`.
8. If the setup is strong enough, open the position.
9. Re-analyze later to manage the trade.

## What each screen does

### Home

This is the main decision screen.

Use it to:

- connect the market feed
- run analysis
- read the current directional bias
- inspect the MTFA stack
- view the chart overlays and trade map
- open or close a tracked trade

### Markets

Use this screen to switch the symbol you want to focus on.

It is not a full multi-market scanner yet. It is mainly a quick market selector.

### Alerts

This screen is now a simple in-app pair monitor.

It can:

- monitor the currently selected pair and timeframe
- recheck the market on a schedule
- refresh the analysis while the app stays open
- store alert entries when the bias is non-neutral

Important limitations:

- it monitors one pair at a time
- it works while the app is open
- it is not yet a background service or push-notification system

### Backtest

This screen currently shows two different things:

- `Live Estimate`
  - dynamic evidence calculated from loaded market data
  - not hard-coded
  - not yet a full replay-based backtest engine
- `Recorded Trades`
  - actual locally stored trades you opened and closed inside the app

### Settings

Use this screen to:

- switch confirmation mode
- enable auto-analysis
- keep or clear alert history
- disconnect the current feed

## Understanding MTFA

The app now uses explicit multi-timeframe analysis.

It separates the market read into:

- `Macro`
  - higher-timeframe directional bias
- `Structure`
  - intermediate market structure
- `Setup`
  - whether the selected timeframe is forming a pullback, alignment, or mixed condition
- `Trigger`
  - whether the lower timeframe is actually giving an entry-quality signal

Typical logic:

- `Macro: Bullish`
- `Structure: Bullish`
- `Setup: Pullback`
- `Trigger: Entry Ready`

That is stronger than:

- `Macro: Bullish`
- `Structure: Range`
- `Setup: Mixed`
- `Trigger: Early Reversal`

## How confirmations work now

The engine no longer treats every confirmation as equally important.

It now works in three layers:

### Direction engine

These checks answer:

- which side should even be considered
- bullish, bearish, or neutral

Main directional confirmations:

- `Higher-Timeframe Bias`
- `Trend Structure`
- `Momentum`
- `Market Structure`
- `Multi-TF Consensus`

### Setup engine

These checks answer:

- is this a good place to participate
- is the market offering a tradable moment

Main setup confirmations:

- `Pullback Quality`
- `Breakout Pressure`
- `Zone Context`
- `Liquidity Map`
- `Imbalance (FVG)`
- `Candlestick Engine`
- `ICC Phase`
- `Timeframe Fit`

### Risk engine

These checks answer:

- should this be downgraded or blocked even if the setup looks attractive

Main risk filters:

- `Noise Filter`
- `Confluence Gate`
- `News Pulse Filter`

## Confirmation behavior improvements

Several confirmations now behave more like market states than simple pass/fail labels.

Examples:

- `Momentum`
  - can read more like continuation, reversal impulse, or exhaustion risk
- `Pullback Quality`
  - can behave more like ideal, acceptable, stretched, or too deep
- `Breakout Pressure`
  - is treated more like clean, weak, or absent pressure
- `Noise Filter`
  - works more like a veto or downgrade state
- `Multi-TF Consensus`
  - can behave more like aligned, aligned-with-pullback, mixed, or conflicted
- `News Pulse Filter`
  - behaves more like a risk adjustment tool than a simple extra confirmation

## Confirmation modes

Modes control how strict the engine is.

### Conservative

- requires the strongest alignment
- expects stronger directional quality
- expects cleaner setup quality
- uses risk filters more aggressively
- prefers continuation and well-structured pullbacks

### Moderate

- balanced behavior
- practical default mode
- allows solid setups without demanding perfect market conditions

### Aggressive

- allows earlier entries
- accepts more noise
- can act sooner when direction is not conflicting and one strong setup signal appears

### Lenient

- loosest mode currently available
- intended for cases where at least 3 credible confirmations exist
- still does **not** mean any random 3 checks are enough
- broader trend and confluence still matter
- often works better as a watchlist / scouting mode than a pure execution mode

## How modes differ internally

Modes do not use different confirmation lists.

They use the same confirmation families, but change:

- how much directional strength is required
- how much setup quality is required
- how much risk is tolerated
- how strict the veto filters are

That makes modes behave like different trading styles instead of only different thresholds.

## Reading the result

After you analyze, focus on these sections:

### Bias

Direction only:

- `Bullish`
- `Bearish`
- `Neutral`

### MTFA Stack

This explains where the direction is coming from:

- `Macro`
- `Structure`
- `Setup`
- `Trigger`

### Decision

This tells you how actionable the setup is:

- `Eligible`
  - strongest current candidate
- `Watchlist`
  - promising, but not ready
- `Reject`
  - not good enough

The decision is now driven more by:

- directional strength
- setup quality
- risk penalty

and less by raw confirmation counting alone.

### Trade Plan

This contains:

- entry
- stop loss
- take profit
- risk/reward
- trigger guidance

### Chart

The chart can show:

- candles
- fast trend line
- slow trend line
- bias line
- entry line
- stop line
- take-profit line

Use `View More On Chart` to expand the trade map.

## Opening and managing a trade

### Opening

Open a trade only when:

- the bias is not neutral
- the trade plan is a real candidate
- the decision is good enough
- there is no existing open position for that symbol and timeframe

### Managing

When a position is already open, the app shifts from fresh-entry logic to management logic.

Typical guidance:

- `Hold`
- `Scale Out`
- `Exit`
- `Wait`

## Evidence and backtest status

This is important:

- the evidence values are dynamic
- they are not hard-coded
- they are not yet true replay-derived backtest results

So:

- `win rate`, `profit factor`, `expectancy`, and `drawdown` in `Live Estimate` are calculated from loaded market data
- `Recorded Trades` are real local history from trades tracked inside the app

## Current engine strengths

The current system is stronger than before because it now includes:

- explicit MTFA stack logic
- weighted direction / setup / risk interpretation
- less reliance on flat confirmation counts
- better distinction between entry quality and veto conditions
- more intentional mode behavior

If you want true backtest metrics, the next engineering step is a candle-by-candle replay engine.

## Supported instruments

The app now supports a broader list of instruments, including:

- major forex pairs
- crypto pairs
- volatility indices
- 1-second volatility indices
- crash and boom indices

If a specific instrument fails to connect, its feed symbol may need adjustment.

## Build and run

Use Android Studio or Gradle `8.7` with JDK `17`.

Typical terminal build:

```bash
gradle assembleDebug
```

APK output:

```text
app/build/outputs/shareable/EX57-Capital-debug.apk
```

## Current limitations

- no automatic order execution
- no true replay backtest engine yet
- no cloud sync
- no broker-account sync
- alerts are not yet background notifications
- monitoring is still one-pair-at-a-time

## Recommended next improvements

1. true replay/backtest engine
2. multi-pair alert watchlist
3. background alerting / notifications
4. more explicit MTFA performance tracking in Backtest
