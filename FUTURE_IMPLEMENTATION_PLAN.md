# EX57 Capital: Competitive Implementation Plan

## 1) Product Thesis

Most retail signal apps fail in one of three ways:

1. They optimize for attractive signals, not measurable edge.
2. They do not separate prediction quality from execution quality.
3. They are not auditable: users cannot verify why a signal happened.

EX57 should compete by being:

- **Measurable**: every mode has transparent walk-forward metrics.
- **Explainable**: each decision exposes setup, trigger, invalidation, and blockers.
- **Risk-native**: position size and eligibility are tied to risk constraints, not guesswork.
- **Adaptive**: strategy and thresholds are tuned from evidence, not static defaults.

---

## 2) Strategic Advantage Targets

To be better than common alternatives, EX57 must outperform on:

### A. Validation Quality
- Built-in walk-forward testing per mode and strategy.
- Symbol-specific scorecards (not one global accuracy number).
- Regime-aware stats (trend/range/high-vol/low-vol).

### B. Decision Explainability
- Every signal exposes:
  - directional thesis
  - trigger condition
  - invalidation condition
  - risk notes
  - top blockers when not eligible

### C. Execution Discipline
- Risk-based sizing only.
- No candidate shown as executable if risk/structure gates fail.
- Post-entry management guidance tied to structure changes.

### D. AI Utility (Not AI Decoration)
- AI output must be symbol/timeframe-specific and trader-grade.
- AI is an interpretation layer, never source-of-truth for signals.
- AI must state uncertainty and wait conditions clearly.

---

## 3) Core Product Architecture (Target State)

### Engine Layers
1. **Feature Layer**: candles -> multi-timeframe structured features.
2. **Forecast Layer**:
   - heuristic forecast
   - sequence model forecast
3. **Decision Layer**:
   - setup scoring
   - mode-specific gates
   - hard-block rules
4. **Execution Layer**:
   - entry/SL/TP planning
   - risk-based lot sizing
5. **Validation Layer**:
   - one-shot checks
   - walk-forward replay
   - scenario stress suite

### Product Layers
1. **Home**: compact decision-centric workflow.
2. **Charts**: structure + levels + trigger context.
3. **Validation**: evidence and diagnostics.
4. **Settings**: strategy profile, risk profile, AI provider, theme.

---

## 4) Roadmap by Phase

## Phase 1: Reliability and UX Discipline (Now -> Short Term)

### Objectives
- Reduce visual overload on Home.
- Improve dark mode readability and action clarity.
- Ensure all key flows are deterministic and testable.

### Deliverables
- Keep Home as:
  - controls
  - prediction summary
  - optional deep sections (collapsed by default)
- Validation readability pass (especially dark mode).
- Unified “Analyse Market” reliability checks and loading states.
- Add small glossary/legend in Validation (`WIN`, `LOSS`, `NO HIT`, `NOT TRIGGERED`).

### Success Criteria
- Users can interpret signal state in < 10 seconds.
- No major visibility issues in dark mode.

---

## Phase 2: Validation as Product Moat

### Objectives
- Make validation the strongest module in the app.
- Turn outcomes into actionable tuning feedback.

### Deliverables
- Walk-forward enhancements:
  - configurable horizon
  - symbol/timeframe filter
  - date-window filter
- Metric suite per mode:
  - resolved win rate
  - precision/recall proxy by decision bucket
  - TP-before-SL probability
  - average adverse excursion / favorable excursion
- Regime segmentation:
  - trend vs range
  - low vs high volatility
- Export results:
  - CSV/JSON output for external analysis.

### Success Criteria
- Users can compare modes quantitatively, not visually.
- Strategy updates can be justified from validation data.

---

## Phase 3: Strategy Framework

### Objectives
- Move from one generic pipeline to strategy profiles.

### Deliverables
- Strategy interface:
  - `name`
  - feature requirements
  - gate thresholds
  - entry/exit rules
- Initial strategy pack:
  - trend pullback
  - breakout retest
  - range rejection
  - volatility expansion breakout
- Strategy selector in Settings.
- Per-strategy validation scorecards.

### Success Criteria
- Users can choose strategy based on evidence per symbol/regime.

---

## Phase 4: Model Quality Upgrade

### Objectives
- Increase prediction quality while preserving explainability.

### Deliverables
- Feature set expansion:
  - richer multi-timeframe structure labels
  - volatility regime markers
  - liquidity sweep and imbalance persistence features
- Lightweight model calibration:
  - probability calibration for TP-before-SL outputs
  - confidence calibration by regime
- Auto-threshold tuning from walk-forward results (bounded, safe ranges).

### Success Criteria
- Improved out-of-sample calibration.
- Fewer high-confidence false positives.

---

## Phase 5: Execution and Trade Lifecycle

### Objectives
- Make post-entry management as strong as pre-entry prediction.

### Deliverables
- Trade lifecycle states:
  - setup candidate
  - armed
  - triggered
  - managed
  - closed
- Management policies:
  - dynamic stop tightening
  - partial exits
  - invalidation-first exits
- Journal + replay:
  - trade snapshot at entry
  - outcome review and lessons.

### Success Criteria
- Reduced drawdown volatility from management improvements.

---

## 4.1) Backtest-First Rebuild Plan (Module by Module)

Every module rebuild must follow this loop:

1. Define hypothesis and measurable KPI.
2. Implement module behind a feature flag.
3. Run walk-forward and scenario tests.
4. Compare against current baseline.
5. Promote only if metrics improve and remain stable across symbols/regimes.

### Module Sequence

1. **Data + Labeling Module**
   - Add triple-barrier-style labels (TP/SL/time barrier).
   - KPI: label consistency, lower ambiguity rate.
2. **Feature Module**
   - Add regime and volatility-normalized features.
   - KPI: feature stability and reduced drift across symbols.
3. **Forecast Module**
   - Upgrade sequence model and calibration.
   - KPI: improved TP-before-SL calibration error.
4. **Decision/Gating Module**
   - Retune thresholds by mode + strategy.
   - KPI: better precision at same or lower false-positive rate.
5. **Risk/Execution Module**
   - ATR/vol-target sizing and dynamic stop logic.
   - KPI: lower drawdown and improved expectancy distribution.
6. **AI Layer Module**
   - Better structured trader-style narrative.
   - KPI: lower generic-response rate, higher actionability score.

### Release Gate (Mandatory)

No module moves to default-on unless:

- Out-of-sample walk-forward improves vs baseline.
- Regime-segmented performance does not collapse in any major regime.
- PBO/overfit risk remains acceptable.
- UX remains interpretable (no added ambiguity in Home/Validation).

---

## 4.2) Borrowed Ideas from Existing Systems (And How We Use Them)

### A. Triple-Barrier + Meta-Labeling (Lopez de Prado)
- Borrow: realistic outcome labeling by first-hit barrier.
- Use in EX57: replace naive horizon labels in model training and validation.
- Validate with: hit-order accuracy and calibration by mode.

### B. Time-Series Momentum + Reversal Awareness
- Borrow: trend persistence on medium horizon with eventual reversal risk.
- Use in EX57: momentum feature bundle + reversal penalty in late-trend regimes.
- Validate with: win-rate split by trend-age bucket.

### C. Volatility Targeting Overlay
- Borrow: risk normalization using realized volatility.
- Use in EX57: position sizing and threshold scaling, not blind alpha claim.
- Validate with: reduced variance of risk-per-trade and smoother drawdowns.

### D. Purged/CPCV-Style Validation + Overfit Controls
- Borrow: leakage-safe validation and overfit probability controls.
- Use in EX57: purged splits for training workflows + PBO/DSR reporting.
- Validate with: lower performance decay from in-sample to out-of-sample.

### E. Regime Switching (HMM-Inspired)
- Borrow: explicit regime classification.
- Use in EX57: regime gate to activate/deactivate strategy profiles.
- Validate with: uplift in regime-specific scorecards vs regime-agnostic baseline.

### F. Modular Risk Models (Framework Pattern)
- Borrow: separate risk model stage from alpha stage.
- Use in EX57: pluggable drawdown cap, exposure cap, trailing-stop modules.
- Validate with: lower tail loss without over-cutting profitable trades.

### G. Hyperopt Discipline
- Borrow: constrained search spaces, reproducible seeds, early stopping.
- Use in EX57: controlled threshold tuning pipeline only.
- Validate with: lower parameter instability across re-runs.

---

## 4.3) Prediction Benchmarking Standard (Beyond Kronos)

Kronos is a strong candidate model, but EX57 should not depend on a single model family.
Prediction modules must beat a full benchmark ladder before promotion.

### Benchmark Ladder (Must Beat in Order)

1. **Naive baselines**
   - random walk / no-change forecast
   - fixed heuristic directional baseline
2. **Classical technical baseline**
   - EMA/RSI/volatility rule stack
3. **ML feature baseline**
   - gradient boosting / regularized linear model on engineered features
4. **Foundation-model baseline**
   - Kronos/Chronos-style sequence model
5. **Ensemble decision baseline**
   - feature model + foundation prior + regime gate

If a new model cannot outperform Level 3 consistently, it does not ship.

### Comparison Matrix (How EX57 Should Position)

- **Kronos**: finance-specific pretraining, strong transfer potential, heavier inference.
- **Chronos-like TSFM**: strong generic forecasting baseline, less finance-specific inductive bias.
- **DeepLOB-style**: excellent with order-book microstructure, not ideal when only OHLCV exists.
- **Feature ML**: fast, interpretable, robust baseline and fallback.
- **Rule-based momentum/vol overlay**: critical for sanity checks and regime control.

Target architecture is hybrid, not winner-takes-all.

### Required Prediction Metrics

Do not promote based on MAE alone. Require:

- directional hit rate
- RankIC / IC (when ranking signals)
- TP-before-SL probability
- expectancy after estimated costs/slippage
- confidence calibration error
- regime-segmented performance (trend/range/high-vol/low-vol)

### Data and Labeling Standard

- Use triple-barrier style labeling (TP/SL/time barrier).
- Keep label policy consistent between training and walk-forward validation.
- Track ambiguous/noise labels explicitly instead of silently discarding.

### Validation and Overfit Controls

Mandatory before production enablement:

- walk-forward out-of-sample performance
- purged/leakage-safe split logic for offline training workflows
- Probability of Backtest Overfitting (PBO/CSCV) monitoring
- Deflated Sharpe Ratio (DSR) reporting for strategy/model selection

### Promotion Gate for Prediction Modules

A prediction module is eligible for default-on only when:

1. It beats current production baseline on out-of-sample metrics.
2. It does not degrade materially in any major regime bucket.
3. It improves risk-adjusted outcomes after transaction-cost assumptions.
4. It passes overfit controls (PBO/DSR thresholds within policy).
5. It remains explainable enough for Home + Validation diagnostics.

### Implementation Sequence for Prediction Upgrades

1. Build and lock benchmark ladder.
2. Add triple-barrier labels + calibration tooling.
3. Add regime classifier and regime-aware scorecards.
4. Integrate Kronos/Chronos inference as optional feature prior.
5. Evaluate hybrid ensemble vs standalone model variants.
6. Promote only top candidate that passes all gates.

---

## 5) AI Roadmap (Practical, Not Cosmetic)

### Current Issue
AI feels generic when provider quality is low or prompts are broad.

### Target
AI should read like a desk note:
- context
- bias
- setup
- trigger
- invalidation
- what changes the view

### Improvements
- Per-action output templates with strict structure.
- Symbol-aware language tuning.
- “Wait/no trade” as first-class answer.
- Add provider quality indicator in UI (mock/local/remote confidence tier).

---

## 6) Metrics Dashboard (What to Track Internally)

### Signal Quality
- Eligible rate by mode
- Watchlist-to-eligible conversion rate
- False-positive rate by confidence bucket

### Outcome Quality
- TP-before-SL probability by mode + strategy + symbol
- Median time-to-outcome
- Max adverse excursion stats

### UX Quality
- Time-to-decision
- Analysis action completion rate
- Validation usage frequency

---

## 7) Prioritized Backlog (Execution Order)

1. Validation legend + filters + horizon control.
2. Strategy framework scaffolding in Settings + engine interface.
3. Per-strategy walk-forward scorecards.
4. Calibration pass for model confidence/probabilities.
5. Trade lifecycle management enhancements.
6. Export/reporting pipeline.

---

## 8) Non-Goals (For Now)

- No promise of guaranteed win-rate targets.
- No uncontrolled auto-trading loop.
- No opaque “AI-only” signal generation without structured engine support.

---

## 9) Definition of “Better Than Existing Systems”

EX57 is better only if users can:

1. Understand **why** a signal exists.
2. Verify **historical behavior** by mode/strategy/symbol.
3. Execute with **risk control** and clear invalidation.
4. Improve settings based on **evidence**, not guesswork.

If those four are consistently true, EX57 has a defensible edge over most signal-first retail apps.
