// Goals — sample data & tone maps.
// Load BEFORE goals-components.jsx / any goals screen.
// Consumed by GoalCard, StatusPill, ForecastChip and every goals screen.

// ── Sample data (used across screens) ────────────────────────
const GOALS = [
  {
    id: 'g1', title: 'Lisbon trip',           emoji: '🏖️', hue: 258,
    target: 2400, current: 1620, currency: 'EUR',
    startDate: 'Jan 4, 2026', targetDate: 'Jul 12, 2026',
    accountId: 'savings', status: 'ACTIVE',
    autopay: { amount: 200, cadence: 'Every 1st of month', nextOn: 'May 1' },
    forecast: 'on-track', etaLabel: 'Jul 4 · 8 days early',
  },
  {
    id: 'g2', title: 'Emergency fund',         emoji: '🛟', hue: 35,
    target: 6000, current: 6000, currency: 'EUR',
    startDate: 'Sep 2025', targetDate: 'Apr 30, 2026',
    accountId: 'savings', status: 'COMPLETED',
    forecast: 'done', etaLabel: 'Reached Apr 22',
  },
  {
    id: 'g3', title: 'New laptop',             emoji: '💻', hue: 162,
    target: 1800, current: 540, currency: 'EUR',
    startDate: 'Mar 1, 2026', targetDate: 'Dec 1, 2026',
    accountId: 'checking', status: 'PAUSED',
    forecast: 'paused', etaLabel: 'Paused on Apr 12',
  },
  {
    id: 'g4', title: 'Anna birthday gift',     emoji: '🎁', hue: 303,
    target: 250, current: 90, currency: 'EUR',
    startDate: 'Apr 1, 2026', targetDate: 'May 18, 2026',
    accountId: 'cash', status: 'ACTIVE',
    forecast: 'behind', etaLabel: 'May 24 · 6 days late',
  },
];

const CONTRIBUTIONS = [
  { id: 'c1', goalId: 'g1', amount: 200,  date: 'Apr 1',   note: 'Auto · monthly',     auto: true  },
  { id: 'c2', goalId: 'g1', amount: 120,  date: 'Mar 22',  note: 'Sold old camera',    auto: false },
  { id: 'c3', goalId: 'g1', amount: 200,  date: 'Mar 1',   note: 'Auto · monthly',     auto: true  },
  { id: 'c4', goalId: 'g1', amount: 50,   date: 'Feb 14',  note: '',                   auto: false },
  { id: 'c5', goalId: 'g1', amount: 200,  date: 'Feb 1',   note: 'Auto · monthly',     auto: true  },
  { id: 'c6', goalId: 'g1', amount: 200,  date: 'Jan 4',   note: 'Auto · monthly',     auto: true  },
];

const STATUS_TONE = {
  ACTIVE:    { label: 'Active',    hue: 162, neutral: false },
  COMPLETED: { label: 'Completed', hue: 162, filled: true   },
  PAUSED:    { label: 'Paused',    hue: 68,  neutral: false },
  ARCHIVED:  { label: 'Archived',  hue: 0,   neutral: true  },
};

const FORECAST_TONE = {
  'on-track': { label: 'On track', hue: 162, icon: 'TrendUp' },
  'ahead':    { label: 'Ahead',    hue: 162, icon: 'ArrowUp' },
  'behind':   { label: 'Behind',   hue: 35,  icon: 'ArrowDown' },
  'paused':   { label: 'Paused',   hue: 68,  icon: 'Clock' },
  'done':     { label: 'Reached',  hue: 162, icon: 'Check' },
};

Object.assign(window, {
  GOALS, CONTRIBUTIONS, STATUS_TONE, FORECAST_TONE,
});
