from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Dict, List, Literal, Optional, Tuple

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field


Direction = Literal["Bullish", "Bearish", "Neutral"]


class Candle(BaseModel):
    epoch: int
    open: float
    high: float
    low: float
    close: float


class PredictRequest(BaseModel):
    pair: str
    timeframe: str
    mode: Optional[str] = "MODERATE"
    candles: List[Candle] = Field(default_factory=list)


class PredictResponse(BaseModel):
    direction: Direction
    confidence: int
    expectedMove: float
    entryPrice: float
    atr14: float
    blockers: List[str] = Field(default_factory=list)

class DeepDiveResponse(BaseModel):
    bias: Direction
    confidence_score: int
    top_down: Dict[str, Any]
    setup: Dict[str, Any]
    strict_entry: Dict[str, Any]
    blockers: List[str] = Field(default_factory=list)
    notes: str


app = FastAPI(title="EX57 Local Predict API", version="0.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def ema(values: List[float], period: int) -> float:
    if not values:
        return 0.0
    alpha = 2.0 / (period + 1)
    out = values[0]
    for value in values[1:]:
        out = value * alpha + out * (1.0 - alpha)
    return out


def atr(candles: List[Candle], period: int = 14) -> float:
    if len(candles) < 3:
        return 0.0
    trs: List[float] = []
    for i in range(1, len(candles)):
        prev = candles[i - 1]
        curr = candles[i]
        tr = max(
            curr.high - curr.low,
            abs(curr.high - prev.close),
            abs(curr.low - prev.close),
        )
        trs.append(tr)
    recent = trs[-period:] if len(trs) >= period else trs
    return sum(recent) / max(len(recent), 1)


def adx_proxy(candles: List[Candle], period: int = 14) -> float:
    if len(candles) < period + 2:
        return 15.0
    moves: List[float] = []
    start = len(candles) - period
    for i in range(start, len(candles)):
        prev = candles[i - 1]
        curr = candles[i]
        moves.append(curr.close - prev.close)
    directional = abs(sum(moves))
    volatility = sum(abs(v) for v in moves) or 1e-6
    score = (directional / volatility) * 100.0
    return max(0.0, min(100.0, score))


def aggregate_candles(candles: List[Candle], factor: int) -> List[Candle]:
    if factor <= 1 or len(candles) < factor:
        return candles
    out: List[Candle] = []
    for i in range(0, len(candles), factor):
        chunk = candles[i : i + factor]
        if len(chunk) < factor:
            continue
        out.append(
            Candle(
                epoch=chunk[-1].epoch,
                open=chunk[0].open,
                high=max(c.high for c in chunk),
                low=min(c.low for c in chunk),
                close=chunk[-1].close,
            )
        )
    return out


def get_topdown_factors(tf: str) -> Tuple[int, int]:
    # Approximates HTF structure for top-down checks when only one stream is provided.
    return {
        "1m": (5, 15),
        "5m": (3, 12),
        "15m": (4, 16),
        "30m": (2, 8),
        "1h": (4, 12),
        "4h": (3, 6),
        "1d": (2, 5),
    }.get(tf, (3, 10))


def check_engulfing(candles: List[Candle]) -> Tuple[bool, bool]:
    if len(candles) < 2:
        return False, False
    prev, curr = candles[-2], candles[-1]
    bull = (
        prev.close < prev.open
        and curr.close > curr.open
        and curr.open <= prev.close
        and curr.close >= prev.open
    )
    bear = (
        prev.close > prev.open
        and curr.close < curr.open
        and curr.open >= prev.close
        and curr.close <= prev.open
    )
    return bull, bear


def check_pinbar(last: Candle) -> Tuple[bool, bool]:
    rng = max(last.high - last.low, 1e-9)
    body = abs(last.close - last.open)
    upper = last.high - max(last.close, last.open)
    lower = min(last.close, last.open) - last.low
    bull = lower >= rng * 0.6 and body <= rng * 0.25 and upper <= rng * 0.25
    bear = upper >= rng * 0.6 and body <= rng * 0.25 and lower <= rng * 0.25
    return bull, bear


def mode_thresholds(mode: str) -> dict:
    m = (mode or "MODERATE").upper()
    if m == "CONSERVATIVE":
        return {"adx": 30.0, "core": 5}
    if m == "AGGRESSIVE":
        return {"adx": 22.0, "core": 3}
    if m == "LENIENT":
        return {"adx": 18.0, "core": 3}
    return {"adx": 25.0, "core": 4}


def session_label_utc(now: Optional[datetime] = None) -> str:
    ts = now or datetime.now(timezone.utc)
    hour = ts.hour
    if 7 <= hour <= 9:
        return "London Open"
    if 13 <= hour <= 16:
        return "London-NY Overlap"
    if 0 <= hour <= 5:
        return "Asia Quiet"
    return "Transition"


def session_allowed(pair: str, now: Optional[datetime] = None) -> bool:
    session = session_label_utc(now)
    p = pair.upper()
    if p == "XAUUSD":
        return session in ("London Open", "London-NY Overlap")
    if p.startswith("VOL"):
        return True
    return session in ("London Open", "London-NY Overlap", "Transition")


def news_blackout_active(pair: str, now: Optional[datetime] = None) -> Tuple[bool, str]:
    ts = now or datetime.now(timezone.utc)
    p = pair.upper()
    if p.startswith("VOL"):
        return False, ""
    mins = ts.hour * 60 + ts.minute
    high_impact = [12 * 60 + 30, 14 * 60, 18 * 60]
    for hm in high_impact:
        if abs(mins - hm) <= 30:
            return True, f"NewsBlackout@{hm // 60:02d}:{hm % 60:02d}UTC"
    return False, ""


def compute_signal(payload: PredictRequest) -> Dict[str, Any]:
    candles = payload.candles[-260:]
    if len(candles) < 30:
        return {
            "direction": "Neutral",
            "confidence": 20,
            "expectedMove": 0.0,
            "entryPrice": candles[-1].close if candles else 0.0,
            "atr14": 0.0,
            "blockers": ["NotEnoughCandles"],
            "extras": {},
        }

    closes = [c.close for c in candles]
    entry = closes[-1]
    ema20 = ema(closes[-220:], 20)
    ema200 = ema(closes[-220:], 200)
    ema20_prev = ema(closes[-221:-1] if len(closes) >= 221 else closes[:-1], 20)
    ema200_prev = ema(closes[-221:-1] if len(closes) >= 221 else closes[:-1], 200)
    atr14 = atr(candles[-220:], 14)
    adx14 = adx_proxy(candles[-220:], 14)
    last = candles[-1]

    tf_key = (payload.timeframe or "").lower()
    if tf_key == "topdown_all":
        # Base is expected as 5m stream from client.
        # Approx hierarchy from 5m: 15m(3), 30m(6), 1h(12), 4h(48), 1d(288), 1w(2016)
        tf_15m = aggregate_candles(candles, 3)
        tf_30m = aggregate_candles(candles, 6)
        tf_1h = aggregate_candles(candles, 12)
        tf_4h = aggregate_candles(candles, 48)
        tf_1d = aggregate_candles(candles, 288)
        tf_1w = aggregate_candles(candles, 2016)

        def trend_flag(series: List[Candle]) -> Tuple[bool, bool]:
            closes = [c.close for c in series][-220:]
            if len(closes) < 30:
                return False, False
            e20 = ema(closes, 20)
            e50 = ema(closes, 50)
            px = closes[-1]
            return px > e20 > e50, px < e20 < e50

        w_bull, w_bear = trend_flag(tf_1w)
        d_bull, d_bear = trend_flag(tf_1d)
        h4_bull, h4_bear = trend_flag(tf_4h)
        h1_bull, h1_bear = trend_flag(tf_1h)
        m30_bull, m30_bear = trend_flag(tf_30m)
        m15_bull, m15_bear = trend_flag(tf_15m)

        bull_votes = sum([w_bull, d_bull, h4_bull, h1_bull, m30_bull, m15_bull])
        bear_votes = sum([w_bear, d_bear, h4_bear, h1_bear, m30_bear, m15_bear])
        htf_bull = bull_votes >= 3 and bull_votes > bear_votes
        htf_bear = bear_votes >= 3 and bear_votes > bull_votes
    else:
        f1, f2 = get_topdown_factors(payload.timeframe)
        tf1 = aggregate_candles(candles, f1)
        tf2 = aggregate_candles(candles, f2)
        tf1_closes = [c.close for c in tf1][-160:]
        tf2_closes = [c.close for c in tf2][-160:]
        tf1_ema20 = ema(tf1_closes, 20) if tf1_closes else ema20
        tf1_ema50 = ema(tf1_closes, 50) if tf1_closes else ema200
        tf2_ema20 = ema(tf2_closes, 20) if tf2_closes else ema20
        tf2_ema50 = ema(tf2_closes, 50) if tf2_closes else ema200
        htf_bull = tf1_ema20 > tf1_ema50 and tf2_ema20 > tf2_ema50
        htf_bear = tf1_ema20 < tf1_ema50 and tf2_ema20 < tf2_ema50
    ema_aligned_bull = entry >= ema20 >= ema200 and ema20 >= ema20_prev and ema200 >= ema200_prev
    ema_aligned_bear = entry <= ema20 <= ema200 and ema20 <= ema20_prev and ema200 <= ema200_prev
    pullback_near_ema20 = abs(entry - ema20) <= max(atr14 * 0.35, entry * 0.00045)
    bull_engulf, bear_engulf = check_engulfing(candles)
    bull_pin, bear_pin = check_pinbar(last)
    ltf_bull_trigger = (last.close > last.open) and (bull_engulf or bull_pin)
    ltf_bear_trigger = (last.close < last.open) and (bear_engulf or bear_pin)
    session_ok = session_allowed(payload.pair)
    blackout_on, blackout_reason = news_blackout_active(payload.pair)

    if tf_key == "topdown_all":
        # Force a lenient review mode for broad top-down sweep.
        t = mode_thresholds("LENIENT")
    else:
        t = mode_thresholds(payload.mode or "MODERATE")
    adx_min = t["adx"]
    trend_ok = adx14 > adx_min
    bullish = htf_bull and ema_aligned_bull and pullback_near_ema20 and ltf_bull_trigger
    bearish = htf_bear and ema_aligned_bear and pullback_near_ema20 and ltf_bear_trigger
    core_count = sum(
        [
            1 if trend_ok else 0,
            1 if (htf_bull or htf_bear) else 0,
            1 if pullback_near_ema20 else 0,
            1 if (ltf_bull_trigger or ltf_bear_trigger) else 0,
            1 if (ema_aligned_bull or ema_aligned_bear) else 0,
            1 if session_ok else 0,
            1 if not blackout_on else 0,
        ]
    )

    if trend_ok and session_ok and (not blackout_on) and bullish:
        direction: Direction = "Bullish"
    elif trend_ok and session_ok and (not blackout_on) and bearish:
        direction = "Bearish"
    else:
        direction = "Neutral"

    confidence = 42
    confidence += 16 if trend_ok else -10
    confidence += 10 if (htf_bull or htf_bear) else -8
    confidence += 8 if pullback_near_ema20 else -6
    confidence += 12 if (bullish or bearish) else -8
    confidence += 6 if session_ok else -10
    confidence += 4 if not blackout_on else -14
    confidence += 6 if core_count >= t["core"] else -8
    confidence = max(20, min(90, confidence))

    blockers: List[str] = []
    if not trend_ok:
        blockers.append(f"ADX<={adx_min:g}")
    if not (htf_bull or htf_bear):
        blockers.append("HTFNotAligned")
    if not session_ok:
        blockers.append("SessionClosed")
    if blackout_on and blackout_reason:
        blockers.append(blackout_reason)
    if not pullback_near_ema20:
        blockers.append("NoPullbackEMA20")
    if not (ltf_bull_trigger or ltf_bear_trigger):
        blockers.append("NoLTFPatternTrigger")
    if core_count < t["core"]:
        blockers.append(f"Core<{t['core']}")
    if direction == "Neutral":
        blockers.append("NoDirectionalAlignment")

    return {
        "direction": direction,
        "confidence": confidence,
        "expectedMove": atr14 * 0.85,
        "entryPrice": entry,
        "atr14": atr14,
        "blockers": blockers,
        "extras": {
            "timeframeMode": tf_key,
            "sessionLabel": session_label_utc(),
            "sessionOk": session_ok,
            "newsBlackout": blackout_on,
            "htfBull": htf_bull,
            "htfBear": htf_bear,
            "emaAlignedBull": ema_aligned_bull,
            "emaAlignedBear": ema_aligned_bear,
            "ltfBullTrigger": ltf_bull_trigger,
            "ltfBearTrigger": ltf_bear_trigger,
            "pullbackNearEma20": pullback_near_ema20,
            "coreCount": core_count,
            "coreRequired": t["core"],
            "adx14": adx14,
            "adxMin": adx_min,
        },
    }


@app.get("/health")
def health() -> dict:
    return {"ok": True}


@app.post("/predict", response_model=PredictResponse)
def predict(payload: PredictRequest) -> PredictResponse:
    sig = compute_signal(payload)
    return PredictResponse(
        direction=sig["direction"],
        confidence=sig["confidence"],
        expectedMove=sig["expectedMove"],
        entryPrice=sig["entryPrice"],
        atr14=sig["atr14"],
        blockers=sig["blockers"],
    )


@app.post("/ai/deep-dive", response_model=DeepDiveResponse)
def ai_deep_dive(payload: PredictRequest) -> DeepDiveResponse:
    sig = compute_signal(payload)
    direction: Direction = sig["direction"]
    extras = sig["extras"]
    atr14 = sig["atr14"]
    entry = sig["entryPrice"]
    stop_dist = max(atr14 * 0.85, entry * 0.0012)
    target_dist = stop_dist * 2.67
    if direction == "Bullish":
        stop = entry - stop_dist
        target = entry + target_dist
    elif direction == "Bearish":
        stop = entry + stop_dist
        target = entry - target_dist
    else:
        stop = None
        target = None
    strict_entry = {
        "entry_tf": payload.timeframe if direction != "Neutral" else "",
        "entry_type": "market" if direction != "Neutral" else "none",
        "entry_level": entry if direction != "Neutral" else None,
        "stop": stop,
        "target": target,
        "no_trade_reason": "; ".join(sig["blockers"]) if direction == "Neutral" else "",
    }
    return DeepDiveResponse(
        bias=direction,
        confidence_score=sig["confidence"],
        top_down={
            "htf_bias": "Bullish" if extras.get("htfBull") else ("Bearish" if extras.get("htfBear") else "Neutral"),
            "session": extras.get("sessionLabel"),
            "session_ok": extras.get("sessionOk"),
            "news_blackout": extras.get("newsBlackout"),
        },
        setup={
            "ema_alignment": bool(extras.get("emaAlignedBull") or extras.get("emaAlignedBear")),
            "pullback_near_ema20": extras.get("pullbackNearEma20"),
            "ltf_trigger": bool(extras.get("ltfBullTrigger") or extras.get("ltfBearTrigger")),
            "adx14": extras.get("adx14"),
            "adx_min": extras.get("adxMin"),
            "core_count": extras.get("coreCount"),
            "core_required": extras.get("coreRequired"),
        },
        strict_entry=strict_entry,
        blockers=sig["blockers"],
        notes="Structured local deep dive (HTF bias -> MTF setup -> strict entry).",
    )
