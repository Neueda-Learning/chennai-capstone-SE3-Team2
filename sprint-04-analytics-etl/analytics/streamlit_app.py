# streamlit_app.py
#
# Run with:
#     streamlit run streamlit_app.py
# EDA calculations live in windowed_eda.py.

import streamlit as st
import pandas as pd
import plotly.graph_objects as go
from plotly.subplots import make_subplots

from windowed_eda import analyze_windows

# ============================================================
# PAGE CONFIG + THEME
# ============================================================
st.set_page_config(page_title="Trades Dashboard", page_icon="📈", layout="wide", initial_sidebar_state="expanded")

BG, PANEL, BORDER = "#0B1220", "#111A2E", "#243250"
TEXT, MUTED = "#F1F5F9", "#8DA0BE"
ACCENT, UP, DOWN = "#D4AF37", "#26A69A", "#EF5350"

st.markdown(f"""
<style>
  .stApp {{ background-color: {BG}; color: {TEXT}; }}
  .main .block-container {{ max-width: 1400px; padding-top: 2.5rem; padding-bottom: 3rem; }}
  section[data-testid="stSidebar"] {{ background-color: {PANEL}; border-right: 1px solid {BORDER}; }}
  h1, h2, h3, h4 {{ color: {TEXT} !important; }}

  div[data-testid="stMetric"] {{
      background: {PANEL}; border: 1px solid {BORDER}; border-radius: 10px;
      padding: 16px 18px; border-top: 2px solid {ACCENT};
  }}
  div[data-testid="stMetricLabel"] {{ color: {MUTED}; font-size: 11.5px; font-weight: 600;
      text-transform: uppercase; letter-spacing: 0.06em; }}
  div[data-testid="stMetricValue"] {{ color: {TEXT}; font-size: 24px; font-weight: 700; }}

  .stTabs [data-baseweb="tab-list"] {{ gap: 4px; border-bottom: 1px solid {BORDER}; }}
  .stTabs [data-baseweb="tab"] {{ color: {MUTED}; font-weight: 600; padding: 10px 16px; }}
  .stTabs [aria-selected="true"] {{ color: {ACCENT} !important; }}

  .notice {{ background: {PANEL}; border: 1px solid {BORDER}; border-left: 3px solid {ACCENT};
      border-radius: 6px; padding: 10px 16px; color: {MUTED}; font-size: 13px; margin-bottom: 12px; }}

  hr {{ border-color: {BORDER} !important; margin: 1.5rem 0 !important; }}
  [data-testid="stCaptionContainer"] {{ color: {MUTED}; }}
</style>
""", unsafe_allow_html=True)


# ============================================================
# SIDEBAR
# ============================================================
st.sidebar.markdown(f'<div style="font-size:22px;font-weight:800;color:{ACCENT};">Trades Dashboard</div>',
                     unsafe_allow_html=True)
st.sidebar.caption("Market analytics")
st.sidebar.write("")

default_symbols = ["INFY.NS", "RELIANCE.NS", "TATASTEEL.BO"]
symbols_input = st.sidebar.text_input("Symbols", value=", ".join(default_symbols),
                                       help="Comma-separated, e.g. INFY.NS, TCS.NS")
symbols = [s.strip().upper() for s in symbols_input.split(",") if s.strip()]

window_choice = st.sidebar.radio("Time period", ["1 Week", "1 Month", "1 Year", "All Available"], index=3)


# ============================================================
# DATA LOADING
# ============================================================
@st.cache_data(show_spinner="Loading market data...")
def get_results(symbols: tuple):
    return {s: analyze_windows(s) for s in symbols}


def resolve_window(result: dict, requested: str) -> tuple[str, dict, bool]:
    windows = result["windows"]
    all_label = next(k for k in windows if k.startswith("All Available"))
    if requested == "All Available":
        return all_label, windows[all_label], False
    if requested in windows:
        return requested, windows[requested], False
    for fallback in ["1 Year", "1 Month", "1 Week"]:
        if fallback in windows:
            return fallback, windows[fallback], True
    return all_label, windows[all_label], True


if not symbols:
    st.warning("Enter at least one stock symbol in the sidebar.")
    st.stop()

try:
    all_results = get_results(tuple(symbols))
except Exception as e:
    st.error(f"Could not load the requested stock data: {e}")
    st.stop()


# ============================================================
# CHART BUILDERS
# ============================================================
def candlestick_figure(symbol: str, ohlc: pd.DataFrame, volumes: pd.DataFrame, window_label: str) -> go.Figure:
    fig = make_subplots(rows=2, cols=1, shared_xaxes=True, row_heights=[0.72, 0.28], vertical_spacing=0.05)
    fig.add_trace(go.Candlestick(
        x=ohlc["date"], open=ohlc["open"], high=ohlc["high"], low=ohlc["low"], close=ohlc["close"],
        increasing_line_color=UP, decreasing_line_color=DOWN,
        increasing_fillcolor=UP, decreasing_fillcolor=DOWN, showlegend=False,
    ), row=1, col=1)

    if len(volumes):
        indexed = ohlc.set_index("date").reindex(volumes["date"])
        bar_colors = [UP if c >= o else DOWN for o, c in zip(indexed["open"], indexed["close"])]
        fig.add_trace(go.Bar(x=volumes["date"], y=volumes["volume"], marker_color=bar_colors,
                              marker_line_width=0, opacity=0.65, showlegend=False), row=2, col=1)

    fig.update_layout(
        title=dict(text=f"{symbol} · {window_label}", font=dict(size=16)),
        template="plotly_dark", paper_bgcolor=PANEL, plot_bgcolor=PANEL, font=dict(color=TEXT),
        height=480, margin=dict(l=10, r=10, t=50, b=10),
        xaxis_rangeslider_visible=False, hovermode="x unified",
    )
    fig.update_yaxes(title_text="Price (₹)", row=1, col=1, gridcolor=BORDER)
    fig.update_yaxes(title_text="Volume", row=2, col=1, gridcolor=BORDER, tickformat="~s")
    fig.update_xaxes(gridcolor=BORDER)
    return fig


def growth_figure(series: dict, height: int = 340) -> go.Figure:
    fig = go.Figure()
    palette = [ACCENT, "#6FB6FF", "#C58FFF", "#7CE0B0"]
    for i, (symbol, (dates, closes)) in enumerate(series.items()):
        growth = (closes / closes.iloc[0] - 1) * 100
        fig.add_trace(go.Scatter(x=dates, y=growth, mode="lines", name=symbol,
                                  line=dict(color=palette[i % len(palette)], width=2)))
    fig.add_hline(y=0, line_dash="dot", line_color=MUTED, opacity=0.5)
    fig.update_layout(
        title="Performance from Start of Period", template="plotly_dark",
        paper_bgcolor=PANEL, plot_bgcolor=PANEL, font=dict(color=TEXT),
        height=height, margin=dict(l=10, r=140, t=50, b=10), hovermode="x unified",
        # Legend sits in its own column to the right of the plot area, clear of
        # the title and the lines themselves -- avoids the clutter of a legend
        # floating over the top of the chart.
        legend=dict(orientation="v", yanchor="top", y=1, xanchor="left", x=1.02,
                    bgcolor="rgba(0,0,0,0)"),
        yaxis_title="Change (%)",
    )
    fig.update_yaxes(gridcolor=BORDER)
    fig.update_xaxes(gridcolor=BORDER)
    return fig


def comparison_bar_figure(all_results: dict, metric_key: str, title: str, y_title: str, is_pct: bool):
    candidate_windows = ["1 Week", "1 Month", "1 Year"]
    windows = [w for w in candidate_windows if sum(w in r["windows"] for r in all_results.values()) >= 2]
    fig = go.Figure()
    palette = [ACCENT, "#6FB6FF", "#C58FFF", "#7CE0B0"]
    for i, (symbol, result) in enumerate(all_results.items()):
        present = [w for w in windows if w in result["windows"]]
        vals = [result["windows"][w][metric_key] for w in present]
        vals = [v if v is not None else 0 for v in vals]
        if vals:
            text = [f"{v:.2f}%" if is_pct else f"{v:,.0f}" for v in vals]
            fig.add_trace(go.Bar(x=present, y=vals, name=symbol, marker_color=palette[i % len(palette)],
                                  text=text, textposition="outside"))
    fig.update_layout(
        title=title, template="plotly_dark", barmode="group",
        paper_bgcolor=PANEL, plot_bgcolor=PANEL, font=dict(color=TEXT),
        height=380, margin=dict(l=10, r=10, t=50, b=60), yaxis_title=y_title,
        legend=dict(orientation="h", yanchor="top", y=-0.18, xanchor="center", x=0.5),
    )
    fig.update_yaxes(gridcolor=BORDER)
    return fig, len(windows) > 0


# ============================================================
# HEADER  (native Streamlit components -- no custom-div spacing bugs)
# ============================================================
st.title("Trades Dashboard")
st.caption(f"{window_choice}  ·  {len(symbols)} symbol(s)")
st.write("")


# ============================================================
# SYMBOL TABS
# ============================================================
tabs = st.tabs(symbols + ["Compare"])

for symbol, tab in zip(symbols, tabs[:-1]):
    with tab:
        result = all_results[symbol]
        label, m, downgraded = resolve_window(result, window_choice)

        st.subheader(symbol)
        st.caption(f"{m['start']} → {m['end']}")

        if downgraded:
            st.markdown(f'<div class="notice">"{window_choice}" isn\'t available for {symbol} — '
                        f'not enough history. Showing <b style="color:{ACCENT};">{label}</b> instead.</div>',
                        unsafe_allow_html=True)

        c1, c2, c3, c4 = st.columns(4)
        c1.metric("Avg. Price", f"₹{m['avg_close']:,.2f}")
        c2.metric("Volatility", f"{m['volatility_pct']:.2f}%" if m["volatility_pct"] is not None else "—",
                   help="Std. dev. of daily returns.")
        c3.metric("Avg. Daily Volume", f"{m['avg_daily_volume']:,.0f}" if m["avg_daily_volume"] is not None else "—",
                   help="Excludes synthetic candles.")
        c4.metric("Biggest Move", f"{m['biggest_move_pct']:.2f}%" if m["biggest_move_pct"] is not None else "—",
                   help=f"On {m['biggest_move_date']}" if m['biggest_move_date'] else None)

        st.divider()
        st.plotly_chart(candlestick_figure(symbol, m["ohlc"], m["volumes"], label), use_container_width=True)

        st.divider()
        st.plotly_chart(growth_figure({symbol: (m["prices"]["date"], m["prices"]["close"])}), use_container_width=True)

        with st.expander("All time windows for this symbol"):
            rows = [{
                "Period": wl,
                "Date range": f"{wm['start']} → {wm['end']}",
                "Volatility": f"{wm['volatility_pct']:.2f}%" if wm["volatility_pct"] is not None else "—",
                "Avg. volume": f"{wm['avg_daily_volume']:,.0f}" if wm["avg_daily_volume"] is not None else "—",
            } for wl, wm in result["windows"].items()]
            st.dataframe(pd.DataFrame(rows), use_container_width=True, hide_index=True)


# ============================================================
# COMPARE TAB
# ============================================================
with tabs[-1]:
    st.subheader("Compare")
    st.caption("Price performance, volatility, and volume across selected symbols.")

    st.divider()
    growth_series = {}
    for s, r in all_results.items():
        _, m, _ = resolve_window(r, window_choice)
        growth_series[s] = (m["prices"]["date"], m["prices"]["close"])
    st.plotly_chart(growth_figure(growth_series, height=380), use_container_width=True)

    st.divider()
    col1, col2 = st.columns(2, gap="large")
    with col1:
        vol_fig, has_vol = comparison_bar_figure(all_results, "volatility_pct", "Volatility by Period", "Volatility (%)", is_pct=True)
        if has_vol:
            st.plotly_chart(vol_fig, use_container_width=True)
        else:
            st.info("Not enough shared history to compare volatility.")
    with col2:
        volu_fig, has_volu = comparison_bar_figure(all_results, "avg_daily_volume", "Volume by Period", "Avg. shares traded", is_pct=False)
        if has_volu:
            st.plotly_chart(volu_fig, use_container_width=True)
        else:
            st.info("Not enough shared history to compare volume.")

    missing_pairs = []
    for w in ["1 Week", "1 Month", "1 Year"]:
        have = [s for s, r in all_results.items() if w in r["windows"]]
        if 0 < len(have) < len(all_results):
            lacking = [s for s in symbols if s not in have]
            missing_pairs.append((w, lacking))
    if missing_pairs:
        parts = [f"{w}: {', '.join(lacking)}" for w, lacking in missing_pairs]
        st.caption("Excluded from comparison due to insufficient history — " + "; ".join(parts))


st.divider()