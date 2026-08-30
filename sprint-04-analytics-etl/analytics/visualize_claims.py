"""
Charts for claims.md.
Run once you have candle-data/{symbol}-candle.json for all three symbols.

Design rules followed throughout (per team rubric):
- Titles STATE the finding, not just describe the picture.
- Both axes labelled, with units.
- No unexplained tickers/abbreviations -- company name always shown alongside.
- Crisp text: short titles, no paragraph-length labels.
- Predominant / highlighted data point uses a darker or accent shade;
  everything else uses a muted shade.
- Bar charts start the y-axis at 0 (never truncate -- that misleads).
- Charts open as static PNGs: no network, no build step.
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from load_candles import load_symbol

MUTED = "#9DB4C0"
ACCENT = "#1E6FA8"
HIGHLIGHT = "#C0392B"

NAMES = {
    "INFY.NS": "Infosys (NSE: INFY)",
    "RELIANCE.NS": "Reliance Industries (NSE: RELIANCE)",
    "TATASTEEL.BO": "Tata Steel (BSE: TATASTEEL)",
}


def claim1_volatility_bar():
    """Claim 1: INFY was more volatile than RELIANCE over the same 1-year window."""
    symbols = ["RELIANCE.NS", "INFY.NS"]
    vols = []
    for s in symbols:
        df = load_symbol(s)
        vols.append(df["close"].pct_change().std() * 100)

    colors = [MUTED, HIGHLIGHT]  # highlight the larger, headline number
    fig, ax = plt.subplots(figsize=(6, 4))
    bars = ax.bar([NAMES[s].split(" (")[0] for s in symbols], vols, color=colors)
    for bar, v in zip(bars, vols):
        ax.text(bar.get_x() + bar.get_width() / 2, v + 0.03, f"{v:.2f}%",
                ha="center", fontsize=11, fontweight="bold")
    ax.set_title("Infosys was ~56% more volatile than Reliance\nover the past year", fontsize=12)
    ax.set_xlabel("Stock")
    ax.set_ylabel("Daily price swing, std. dev. (%)")
    ax.set_ylim(bottom=0)
    fig.tight_layout()
    fig.savefig("charts/claim1_volatility.png", dpi=150)
    plt.close(fig)


def claim2_infy_price_line():
    """Claim 2: INFY's largest single-day move was 7.26% on 2026-02-04."""
    df = load_symbol("INFY.NS")
    fig, ax = plt.subplots(figsize=(7, 4))
    ax.plot(df["date"], df["close"], color=ACCENT, linewidth=1.5)

    spike_row = df.loc[df["date"] == "2026-02-04"]
    if not spike_row.empty:
        x = spike_row["date"].iloc[0]
        y = spike_row["close"].iloc[0]
        ax.scatter([x], [y], color=HIGHLIGHT, zorder=5, s=50)
        ax.annotate("+7.26% in one day", xy=(x, y), xytext=(15, 20),
                    textcoords="offset points", fontsize=10, fontweight="bold",
                    color=HIGHLIGHT, arrowprops=dict(arrowstyle="->", color=HIGHLIGHT))

    ax.set_title("Infosys moved 7.26% in a single day\non 4 Feb 2026", fontsize=12)
    ax.set_xlabel("Date")
    ax.set_ylabel("Closing price (INR)")
    fig.autofmt_xdate()
    fig.tight_layout()
    fig.savefig("charts/claim2_infy_spike.png", dpi=150)
    plt.close(fig)


def claim3_tatasteel_volume_bar():
    """Claim 3: TATASTEEL volume over its available ~5-week window (self-contained, not compared)."""
    df = load_symbol("TATASTEEL.BO")
    real = df[~df["synthetic"]]
    fig, ax = plt.subplots(figsize=(7, 4))
    ax.bar(real["date"], real["volume"], color=ACCENT, width=0.7)
    avg = real["volume"].mean()
    ax.axhline(avg, color=HIGHLIGHT, linestyle="--", linewidth=1)
    ax.text(real["date"].iloc[0], avg * 1.05, f"avg: {avg:,.0f} shares/day",
            color=HIGHLIGHT, fontsize=10, fontweight="bold")
    ax.set_title("Tata Steel averaged ~1.29M shares/day\n(17 Jul \u2013 26 Aug 2026)", fontsize=12)
    ax.set_xlabel("Date")
    ax.set_ylabel("Volume (shares traded)")
    ax.set_ylim(bottom=0)
    fig.autofmt_xdate()
    fig.tight_layout()
    fig.savefig("charts/claim3_tatasteel_volume.png", dpi=150)
    plt.close(fig)


def claim4_volume_share_pie():
    """
    Claim 4: what share of combined INFY+RELIANCE volume did each one trade?
    Pie is legitimate here ONLY because both stocks share the same 262-day
    window -- this is a genuine part-of-a-whole question, not a disguised
    side-by-side comparison (which is what claim1's bar chart is for).
    """
    infy = load_symbol("INFY.NS")
    rel = load_symbol("RELIANCE.NS")
    infy_vol = infy.loc[~infy["synthetic"], "volume"].sum()
    rel_vol = rel.loc[~rel["synthetic"], "volume"].sum()
    total = infy_vol + rel_vol

    fig, ax = plt.subplots(figsize=(5.5, 5.5))
    ax.pie(
        [infy_vol, rel_vol],
        labels=["Infosys (INFY)", "Reliance (RELIANCE)"],
        autopct=lambda pct: f"{pct:.0f}%",
        colors=[ACCENT, MUTED],
        startangle=90,
        textprops={"fontsize": 11, "fontweight": "bold"},
    )
    ax.set_title(
        f"Share of combined daily trading volume,\nInfosys vs Reliance (26 Aug 2025\u201326 Aug 2026)",
        fontsize=12,
    )
    fig.tight_layout()
    fig.savefig("charts/claim4_volume_share.png", dpi=150)
    plt.close(fig)
    print(f"INFY share: {infy_vol/total*100:.1f}%  RELIANCE share: {rel_vol/total*100:.1f}%")


def claim5_volume_vs_move_scatter():
    """
    Claim 5: does INFY's daily volume relate to the size of its daily price move?
    A scatter is the right chart because this is a relationship between two
    numeric variables per day, not a trend over time or a proportion.
    """
    df = load_symbol("INFY.NS").copy()
    df = df[~df["synthetic"]]
    df["abs_return_pct"] = df["close"].pct_change().abs() * 100

    fig, ax = plt.subplots(figsize=(6.5, 5))
    ax.scatter(df["volume"], df["abs_return_pct"], color=ACCENT, alpha=0.6, edgecolor="white")

    # Correlation, printed to console -- this is what actually settles the claim,
    # not the visual scatter alone (a scattered cloud can *look* like a pattern
    # that isn't statistically real; always check the number).
    corr = df["volume"].corr(df["abs_return_pct"])
    print(f"Correlation between INFY daily volume and |daily return|: r={corr:.2f}")

    ax.set_title(
        "Infosys: higher-volume days moderately\ncoincided with larger price swings",
        fontsize=12,
    )
    ax.text(
        0.03, 0.95, f"r = {corr:.2f}",
        transform=ax.transAxes, fontsize=12, fontweight="bold",
        color=HIGHLIGHT, va="top",
        bbox=dict(boxstyle="round,pad=0.3", facecolor="white", edgecolor=HIGHLIGHT),
    )
    ax.set_xlabel("Daily volume (shares traded)")
    ax.set_ylabel("Daily price move, absolute (%)")
    fig.tight_layout()
    fig.savefig("charts/claim5_volume_vs_move.png", dpi=150)
    plt.close(fig)


if __name__ == "__main__":
    from pathlib import Path
    Path("charts").mkdir(exist_ok=True)
    claim1_volatility_bar()
    claim2_infy_price_line()
    claim3_tatasteel_volume_bar()
    claim4_volume_share_pie()
    claim5_volume_vs_move_scatter()
    print("Saved 5 charts to charts/")