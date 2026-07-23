// Excerpt of `design-system/components/_lib/screen-chrome.jsx` from the MoneySurfer Claude
// Design project, pulled 2026-07-22. Only the two components the transactions list is built
// from are mirrored here — see ./README.md for why this folder is a partial mirror.
//
//   • PeriodPager  — < · Month YYYY · >  (also handles weeks, all-time)
//   • SummaryStrip — 3-up income/expenses/net stat strip
//
// Rendered standalone by `design-system/components/period-pager.html`, whose three stages are
// month (interactive), week (`Mar 25 – 31` / `W13 · 2025`), and all time (arrows disabled).

// ── PeriodPager ───────────────────────────────────────────────
// Compact pager pill for navigating month / week / etc.
//   [<]   Month label · sublabel   [>]
// Stateless — caller advances via onPrev / onNext. `sub` is optional.
function PeriodPager({ scheme, label, sub, onPrev, onNext, disabledPrev, disabledNext }) {
  const Arrow = ({ dir, disabled, onClick }) => (
    <div onClick={disabled ? undefined : onClick} style={{
      width: 32, height: 32, borderRadius: 16,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      color: disabled
        ? `color-mix(in oklab, ${scheme.onSurfaceVariant} 40%, transparent)`
        : scheme.onSurfaceVariant,
      cursor: disabled || !onClick ? 'default' : 'pointer',
    }}>
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
        stroke="currentColor" strokeWidth="2"
        strokeLinecap="round" strokeLinejoin="round"
        style={{ transform: dir === 'next' ? 'none' : 'scaleX(-1)' }}>
        <path d="M9 6l6 6-6 6"/>
      </svg>
    </div>
  );
  return (
    <div style={{
      height: 40,
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      background: scheme.surfaceContainerLow,
      borderRadius: 20, padding: '0 6px',
    }}>
      <Arrow dir="prev" disabled={disabledPrev} onClick={onPrev}/>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 6,
        ...type('titleSmall'), color: scheme.onSurface, fontWeight: 600 }}>
        <span>{label}</span>
        {sub && <span style={{ ...type('labelSmall'),
          color: scheme.onSurfaceVariant, fontWeight: 400 }}>{sub}</span>}
      </div>
      <Arrow dir="next" disabled={disabledNext} onClick={onNext}/>
    </div>
  );
}

// ── SummaryStrip ──────────────────────────────────────────────
// Three-up stat strip: Income · Expenses · Net.
// Items can be passed explicitly via `items` (array of { l, v, c })
// or computed from { income, expenses, currency }.
function SummaryStrip({ scheme, items, income, expenses, currency = '€' }) {
  if (!items) {
    const net = (income || 0) - (expenses || 0);
    const fmtAmt = (n, signed) => {
      const sign = n < 0 ? '−' : (signed ? '+' : '');
      const abs = Math.abs(n).toLocaleString('en-US', {
        minimumFractionDigits: 2, maximumFractionDigits: 2,
      });
      return `${sign}${currency}${abs}`;
    };
    items = [
      { l: 'Income',   v: fmtAmt(income, true),
        c: `oklch(${scheme.isDark ? 78 : 38}% 0.14 152)` },
      { l: 'Expenses', v: fmtAmt(-expenses), c: scheme.onSurface },
      { l: 'Net',      v: fmtAmt(net, true), c: scheme.onSurface },
    ];
  }
  return (
    <div style={{
      display: 'flex', gap: 8, padding: '12px 14px',
      background: scheme.surfaceContainerLow, borderRadius: 16,
    }}>
      {items.map((s, i, a) => (
        <React.Fragment key={s.l}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ ...type('labelSmall'), color: scheme.onSurfaceVariant,
              letterSpacing: 0.4, textTransform: 'uppercase' }}>{s.l}</div>
            <div style={{ ...type('titleMedium'), color: s.c,
              fontVariantNumeric: 'tabular-nums', marginTop: 2,
              overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{s.v}</div>
          </div>
          {i < a.length - 1 && (
            <div style={{ width: 1, background: scheme.outlineVariant, margin: '4px 0' }}/>
          )}
        </React.Fragment>
      ))}
    </div>
  );
}
