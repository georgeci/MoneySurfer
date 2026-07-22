// Seed data for Budgets module.
// Mirrors the kotlin Budget model (id, name, categoryIds, amount,
// period, startDate, rollover, alertPercent, isActive). The runtime
// adds derived fields: spent, periodLabel, daysLeft.

const BUDGETS = [
  {
    id: 'b1', name: 'Groceries', categoryIds: ['groceries'],
    categories: ['groceries'],
    amount: 400, spent: 312.40,
    period: 'MONTHLY',
    periodLabel: 'Apr 1 – Apr 30',
    startDate: '2026-04-01',
    rollover: true,
    alertPercent: 80,
    isActive: true,
    daysLeft: 12,
  },
  {
    id: 'b2', name: 'Eating out', categoryIds: ['dining'],
    categories: ['dining'],
    amount: 180, spent: 162.30,
    period: 'MONTHLY',
    periodLabel: 'Apr 1 – Apr 30',
    startDate: '2026-04-01',
    rollover: false,
    alertPercent: 80,
    isActive: true,
    daysLeft: 12,
  },
  {
    id: 'b3', name: 'Transport & fuel',
    categoryIds: ['transport', 'utilities'],
    categories: ['transport', 'utilities'],
    amount: 220, spent: 245.10,
    period: 'MONTHLY',
    periodLabel: 'Apr 1 – Apr 30',
    startDate: '2026-04-01',
    rollover: false,
    alertPercent: 75,
    isActive: true,
    daysLeft: 12,
  },
  {
    id: 'b4', name: 'Lifestyle',
    categoryIds: ['leisure', 'health', 'dining'],
    categories: ['leisure', 'health', 'dining'],
    amount: 350, spent: 88.50,
    period: 'MONTHLY',
    periodLabel: 'Apr 1 – Apr 30',
    startDate: '2026-04-01',
    rollover: true,
    alertPercent: 80,
    isActive: true,
    daysLeft: 12,
  },
  {
    id: 'b5', name: 'Total monthly cap',
    categoryIds: [],
    categories: [],
    amount: 2200, spent: 1408.30,
    period: 'MONTHLY',
    periodLabel: 'Apr 1 – Apr 30',
    startDate: '2026-04-01',
    rollover: false,
    alertPercent: 80,
    isActive: true,
    daysLeft: 12,
  },
  {
    id: 'b6', name: 'Holidays 2026', categoryIds: ['leisure'],
    categories: ['leisure'],
    amount: 1800, spent: 420,
    period: 'YEARLY',
    periodLabel: '2026',
    startDate: '2026-01-01',
    rollover: false,
    alertPercent: 80,
    isActive: false,
    daysLeft: 248,
  },
];

// Transactions feeding a budget — derived view used on details screen.
// In production this would be a query: tx where categoryId IN budget.categoryIds
// AND date BETWEEN budget.start AND budget.end.
const BUDGET_TX = {
  b1: [
    { id: 'b1t1', accountId: 'checking', categoryId: 'groceries', amount: 64.20, isExpense: true, note: 'Biedronka — weekly shop', date: 'Apr 18' },
    { id: 'b1t2', accountId: 'checking', categoryId: 'groceries', amount: 18.40, isExpense: true, note: 'Żabka — snacks',          date: 'Apr 17' },
    { id: 'b1t3', accountId: 'checking', categoryId: 'groceries', amount: 92.80, isExpense: true, note: 'Lidl — pantry run',       date: 'Apr 14' },
    { id: 'b1t4', accountId: 'checking', categoryId: 'groceries', amount: 23.10, isExpense: true, note: 'Carrefour Express',       date: 'Apr 11' },
    { id: 'b1t5', accountId: 'checking', categoryId: 'groceries', amount: 47.60, isExpense: true, note: 'Auchan',                  date: 'Apr 08' },
    { id: 'b1t6', accountId: 'checking', categoryId: 'groceries', amount: 31.20, isExpense: true, note: 'Local market',            date: 'Apr 05' },
    { id: 'b1t7', accountId: 'checking', categoryId: 'groceries', amount: 35.10, isExpense: true, note: 'Biedronka',               date: 'Apr 02' },
  ],
  b3: [
    { id: 'bt1', accountId: 'checking', categoryId: 'utilities', amount: 68.00, isExpense: true,  note: 'Electricity',          date: 'Apr 18' },
    { id: 'bt2', accountId: 'checking', categoryId: 'transport', amount: 12.80, isExpense: true,  note: 'Metro — 10-trip',      date: 'Apr 17' },
    { id: 'bt3', accountId: 'checking', categoryId: 'transport', amount: 54.20, isExpense: true,  note: 'Shell — fuel',         date: 'Apr 14' },
    { id: 'bt4', accountId: 'checking', categoryId: 'utilities', amount: 42.10, isExpense: true,  note: 'Internet',             date: 'Apr 10' },
    { id: 'bt5', accountId: 'checking', categoryId: 'transport', amount: 38.00, isExpense: true,  note: 'Uber — airport',       date: 'Apr 06' },
    { id: 'bt6', accountId: 'checking', categoryId: 'utilities', amount: 30.00, isExpense: true,  note: 'Water',                date: 'Apr 03' },
  ],
  b4: [
    { id: 'b4t1', accountId: 'checking', categoryId: 'leisure', amount: 24.00, isExpense: true, note: 'Cinema — Multikino',   date: 'Apr 16' },
    { id: 'b4t2', accountId: 'checking', categoryId: 'dining',  amount: 18.50, isExpense: true, note: 'Pizzeria — lunch',     date: 'Apr 13' },
    { id: 'b4t3', accountId: 'checking', categoryId: 'health',  amount: 22.00, isExpense: true, note: 'Pharmacy',             date: 'Apr 10' },
    { id: 'b4t4', accountId: 'checking', categoryId: 'leisure', amount: 12.00, isExpense: true, note: 'Spotify Family',       date: 'Apr 07' },
    { id: 'b4t5', accountId: 'checking', categoryId: 'dining',  amount: 12.00, isExpense: true, note: 'Coffee · Etno Cafe',   date: 'Apr 03' },
  ],
};

const BUDGET = Object.fromEntries(BUDGETS.map(b => [b.id, b]));

Object.assign(window, { BUDGETS, BUDGET, BUDGET_TX });
