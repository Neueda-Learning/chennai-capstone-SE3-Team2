# generate_dashboard.py
import base64
import io
import sys
from datetime import datetime

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

from windowed_eda import analyze_windows

# ---- Yellow theme: amber as the single accent, everything else neutral so
# the accent actually stands out instead of competing with itself ----
AMBER = "#FFCB77"
AMBER_DARK = "#E3A94A"
INK = "#3A342B"
MUTED = "#E8DFCC"
BG = "#FFFDF8"
CARD_BG = "#FFFFFF"
BORDER = "#F1E9D8"
GREY_TEXT = "#8A8272"

SYMBOL_COLORS = [AMBER, "#9DC3B8", "#C9A0BE"]  # pastel amber, sage, mauve

plt.rcParams.update({
    "font.family": "sans-serif",
    "font.size": 11,
    "axes.edgecolor": BORDER,
    "axes.labelcolor": INK,
    "text.color": INK,
    "xtick.color": INK,
    "ytick.color": INK,
})


from matplotlib.ticker import FuncFormatter

def _volume_formatter(x, pos):
    if x >= 1_000_000:
        return f"{x/1_000_000:.1f}M".replace(".0M", "M")
    if x >= 1_000:
        return f"{x/1_000:.0f}K"
    return f"{x:,.0f}"


def _apply_volume_axis(ax):
    ax.yaxis.set_major_formatter(FuncFormatter(_volume_formatter))


def _fig_to_base64(fig) -> str:
    buf = io.BytesIO()
    fig.savefig(buf, format="png", dpi=150, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    buf.seek(0)
    return base64.b64encode(buf.read()).decode("ascii")


def _price_chart(symbol: str, window_label: str, prices_df) -> str:
    fig, ax = plt.subplots(figsize=(7, 2.8))
    ax.plot(prices_df["date"], prices_df["close"], color=AMBER_DARK, linewidth=1.6)
    ax.fill_between(prices_df["date"], prices_df["close"], prices_df["close"].min(),
                     color=AMBER, alpha=0.18)
    ax.set_title(f"Closing Price — {window_label}", fontsize=12, loc="left")
    ax.set_xlabel("Date")
    ax.set_ylabel("Close (INR)")
    fig.autofmt_xdate()
    fig.tight_layout()
    return _fig_to_base64(fig)


def _volume_chart(symbol: str, window_label: str, volumes_df) -> str:
    fig, ax = plt.subplots(figsize=(7, 2.8))
    if len(volumes_df):
        # A year of daily bars is unreadable at this width -- resample to weekly
        # totals once there's more than ~60 bars worth of data, so the chart
        # stays legible instead of turning into a solid block of color.
        plot_df = volumes_df
        if len(volumes_df) > 60:
            plot_df = (volumes_df.set_index("date")["volume"]
                       .resample("W").sum().reset_index())
            bar_label = f"Daily Volume, weekly totals — {window_label}"
        else:
            bar_label = f"Daily Volume — {window_label}"

        ax.bar(plot_df["date"], plot_df["volume"], color=AMBER,
               width=6 if len(plot_df) < len(volumes_df) else 0.7)
        avg = plot_df["volume"].mean()
        ax.axhline(avg, color=INK, linestyle="--", linewidth=1)
        ax.text(0.99, 0.97, f"avg {_volume_formatter(avg, None)}", transform=ax.transAxes,
                ha="right", va="top", color=INK, fontsize=9, fontweight="bold")
    else:
        bar_label = f"Daily Volume — {window_label}"
        ax.text(0.5, 0.5, "No reliable volume data\n(all candles synthetic)",
                ha="center", va="center", transform=ax.transAxes, color=GREY_TEXT)
    ax.set_title(bar_label, fontsize=12, loc="left")
    ax.set_xlabel("Date")
    ax.set_ylabel("Shares traded")
    ax.set_ylim(bottom=0)
    _apply_volume_axis(ax)
    fig.autofmt_xdate()
    fig.tight_layout()
    return _fig_to_base64(fig)


def _comparison_chart(all_results: list, metric_key: str, ylabel: str, title: str, fmt, is_volume=False):
    candidate_order = ["1 Week", "1 Month", "1 Year"]
    # A window only earns a spot on the chart if at least 2 symbols have it --
    # otherwise there's nothing to compare. Symbols that DO have the window
    # still get their bar even if a sibling symbol lacks it.
    windows = [w for w in candidate_order
               if sum(1 for r in all_results if w in r["windows"]) >= 2]
    if not windows:
        return None, []

    symbols = [r["symbol"] for r in all_results]
    x = np.arange(len(windows))
    width = 0.8 / max(len(symbols), 1)
    fig, ax = plt.subplots(figsize=(8, 3.4))

    missing = []  # (symbol, window) pairs excluded
    for i, r in enumerate(all_results):
        xs, vals = [], []
        for gi, w in enumerate(windows):
            if w in r["windows"]:
                v = r["windows"][w][metric_key]
                xs.append(gi + i * width)
                vals.append(v if v is not None else 0)
            else:
                missing.append((r["symbol"], w))
        if xs:
            bars = ax.bar(xs, vals, width, label=r["symbol"], color=SYMBOL_COLORS[i % len(SYMBOL_COLORS)])
            for bar, v in zip(bars, vals):
                ax.text(bar.get_x() + bar.get_width() / 2, v, fmt(v),
                        ha="center", va="bottom", fontsize=8)

    ax.set_xticks(x + width * (len(symbols) - 1) / 2)
    ax.set_xticklabels(windows)
    ax.set_title(title, fontsize=12, loc="left")
    ax.set_ylabel(ylabel)
    ax.set_ylim(bottom=0)
    if is_volume:
        _apply_volume_axis(ax)
    ax.legend(frameon=False, fontsize=9, loc="upper left", bbox_to_anchor=(1.0, 1.0))
    fig.tight_layout()
    return _fig_to_base64(fig), missing


def _fmt(val, kind="num"):
    if val is None:
        return "—"
    if kind == "money":
        return f"₹{val:,.2f}"
    if kind == "pct":
        return f"{val:.2f}%"
    if kind == "vol":
        return f"{val:,.0f}"
    return str(val)


def _metric_cards(m: dict) -> str:
    cards = [
        ("Avg. Close", _fmt(m["avg_close"], "money")),
        ("Volatility", _fmt(m["volatility_pct"], "pct")),
        ("Avg. Daily Volume", _fmt(m["avg_daily_volume"], "vol")),
        ("Biggest Move", f"{_fmt(m['biggest_move_pct'], 'pct')}" if m["biggest_move_pct"] is not None else "—"),
    ]
    return "".join(
        f'<div class="card"><div class="card-label">{label}</div>'
        f'<div class="card-value">{value}</div></div>'
        for label, value in cards
    )


def _window_table(windows: dict) -> str:
    rows = "".join(
        f"<tr><td>{label}</td><td>{m['start']} → {m['end']}</td>"
        f"<td>{_fmt(m['volatility_pct'], 'pct')}</td>"
        f"<td>{_fmt(m['avg_daily_volume'], 'vol')}</td></tr>"
        for label, m in windows.items()
    )
    return f"""
    <table class="window-table">
      <thead><tr><th>Window</th><th>Date Range</th><th>Volatility</th><th>Avg Volume</th></tr></thead>
      <tbody>{rows}</tbody>
    </table>"""


def build_dashboard(symbols: list, out_path: str = "eda_dashboard.html"):
    all_results = [analyze_windows(s) for s in symbols]

    tab_buttons = []
    tab_panels = []

    for i, result in enumerate(all_results):
        symbol = result["symbol"]
        windows = result["windows"]
        all_time_key = [k for k in windows if k.startswith("All Available")][0]
        headline = windows[all_time_key]

        price_b64 = _price_chart(symbol, all_time_key, headline["prices"])
        volume_b64 = _volume_chart(symbol, all_time_key, headline["volumes"])
        tab_id = f"tab-{i}"
        active = " active" if i == 0 else ""

        tab_buttons.append(f'<button class="tab-btn{active}" onclick="showTab(\'{tab_id}\')">{symbol}</button>')
        tab_panels.append(f"""
        <div id="{tab_id}" class="tab-panel{active}">
          <div class="cards">{_metric_cards(headline)}</div>
          <div class="chart-row">
            <div class="chart-card"><img src="data:image/png;base64,{price_b64}"></div>
            <div class="chart-card"><img src="data:image/png;base64,{volume_b64}"></div>
          </div>
          <h3>By Time Window</h3>
          {_window_table(windows)}
        </div>""")

    vol_chart, missing1 = _comparison_chart(
        all_results, "volatility_pct", "Volatility (%)",
        "Volatility by Window", lambda v: f"{v:.1f}%"
    )
    volume_chart, missing2 = _comparison_chart(
        all_results, "avg_daily_volume", "Avg. Daily Volume", "Average Daily Volume by Window",
        lambda v: _volume_formatter(v, None), is_volume=True
    )

    excluded_note = ""
    all_missing = missing1 + missing2
    if all_missing:
        by_symbol = {}
        for sym, w in all_missing:
            by_symbol.setdefault(sym, set()).add(w)
        parts = [f"{sym} ({', '.join(sorted(ws))})" for sym, ws in by_symbol.items()]
        excluded_note = (
            f'<p class="note">Not enough history for a fair comparison on every window: '
            f'{"; ".join(parts)} excluded from those windows above.</p>'
        )

    comparison_charts_html = ""
    if vol_chart:
        comparison_charts_html += f'<div class="chart-card wide"><img src="data:image/png;base64,{vol_chart}"></div>'
    if volume_chart:
        comparison_charts_html += f'<div class="chart-card wide"><img src="data:image/png;base64,{volume_chart}"></div>'
    if not comparison_charts_html:
        comparison_charts_html = '<p class="note">No time window is common across all selected instruments to compare fairly.</p>'

    tab_buttons.append('<button class="tab-btn" onclick="showTab(\'tab-compare\')">Comparison</button>')
    tab_panels.append(f"""
    <div id="tab-compare" class="tab-panel">
      {excluded_note}
      <div class="chart-row">{comparison_charts_html}</div>
    </div>""")

    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>EDA Dashboard</title>
<style>
  * {{ box-sizing: border-box; }}
  body {{ background: {BG}; color: {INK}; font-family: -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif; margin: 0; }}
  header {{ padding: 28px 40px 16px; border-bottom: 1px solid {BORDER}; }}
  header h1 {{ margin: 0 0 4px; font-size: 22px; font-weight: 700; }}
  header p {{ margin: 0; color: {GREY_TEXT}; font-size: 13px; }}
  .container {{ max-width: 980px; margin: 0 auto; padding: 24px; }}
  .tabs {{ display: flex; gap: 6px; padding: 0 40px; border-bottom: 1px solid {BORDER}; background: {CARD_BG}; }}
  .tab-btn {{ border: none; background: none; padding: 14px 18px; font-size: 14px; font-weight: 600;
              color: {GREY_TEXT}; cursor: pointer; border-bottom: 3px solid transparent; }}
  .tab-btn:hover {{ color: {INK}; }}
  .tab-btn.active {{ color: {AMBER_DARK}; border-bottom-color: {AMBER}; }}
  .tab-panel {{ display: none; }}
  .tab-panel.active {{ display: block; }}
  .cards {{ display: flex; gap: 12px; margin: 20px 0; flex-wrap: wrap; }}
  .card {{ flex: 1 1 150px; background: {CARD_BG}; border: 1px solid {BORDER}; border-radius: 10px;
           padding: 14px 16px; border-top: 3px solid {AMBER}; }}
  .card-label {{ font-size: 11px; color: {GREY_TEXT}; text-transform: uppercase; letter-spacing: 0.04em; }}
  .card-value {{ font-size: 20px; font-weight: 700; margin-top: 4px; color: {INK}; }}
  .chart-row {{ display: flex; gap: 16px; flex-wrap: wrap; }}
  .chart-card {{ flex: 1 1 420px; background: {CARD_BG}; border: 1px solid {BORDER}; border-radius: 10px; padding: 10px; }}
  .chart-card.wide {{ flex: 1 1 100%; }}
  .chart-card img {{ width: 100%; height: auto; display: block; }}
  h3 {{ font-size: 13px; color: {GREY_TEXT}; text-transform: uppercase; letter-spacing: 0.04em; margin: 26px 0 8px; }}
  table.window-table {{ width: 100%; border-collapse: collapse; font-size: 13px; background: {CARD_BG};
                         border: 1px solid {BORDER}; border-radius: 8px; overflow: hidden; }}
  table.window-table th {{ text-align: left; background: {AMBER}; color: {INK}; padding: 8px 12px; font-weight: 700; }}
  table.window-table td {{ padding: 8px 12px; border-top: 1px solid {BORDER}; }}
  .note {{ font-size: 12.5px; color: {GREY_TEXT}; background: #FFF8E8; border: 1px solid {MUTED};
           border-radius: 8px; padding: 10px 14px; margin: 16px 0; }}
  footer {{ text-align: center; color: {GREY_TEXT}; font-size: 11.5px; margin: 32px 0 8px; }}
</style>
</head>
<body>
  <header>
    <h1>Trades EDA Dashboard</h1>
    <p>{', '.join(symbols)} &nbsp;•&nbsp; Generated {datetime.now().strftime('%d %b %Y, %H:%M')}</p>
  </header>
  <div class="tabs">{"".join(tab_buttons)}</div>
  <div class="container">
    {"".join(tab_panels)}
  </div>
  <script>
    function showTab(id) {{
      document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      document.getElementById(id).classList.add('active');
      event.currentTarget.classList.add('active');
    }}
  </script>
</body>
</html>"""

    with open(out_path, "w", encoding="utf-8") as f:
        f.write(html)
    print(f"Dashboard written to {out_path}")
    return out_path


if __name__ == "__main__":
    symbols = sys.argv[1:] or ["INFY.NS", "RELIANCE.NS", "TATASTEEL.BO"]
    build_dashboard(symbols)