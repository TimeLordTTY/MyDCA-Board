export type Quality = 'OK' | 'PARTIAL' | 'UNKNOWN'
export interface GoalConfig { name: string; targetValue: number | string; targetDate: string; currency: string; scope: 'PERSONAL' | 'FAMILY'; measure: 'TOTAL_ASSETS' | 'CASH' | 'POSITION_VALUE'; state: 'ACTIVE' | 'PAUSED' | 'ARCHIVED'; note: string }
export interface Goal { id: string; config: GoalConfig; createdAt: string }
export interface GoalProgress { goalId: string; quality: Quality; reason: string; currentValue: number | null; knownValue: number | null; completionRate: number | null; completed: boolean | null; asOfDate: string; daysRemaining: number; overdue: boolean }
export interface BudgetItem { name: string; kind: 'INCOME' | 'FIXED_EXPENSE' | 'FLEXIBLE_EXPENSE' | 'RESERVE'; categoryId: number | null; planned: number | string }
export interface BudgetConfig { name: string; month: string; currency: string; scope: 'PERSONAL' | 'FAMILY'; items: BudgetItem[] }
export interface Budget { id: string; config: BudgetConfig; createdAt: string }
export interface BudgetComparison { budgetId: string; quality: Quality; plannedIncome: number; plannedExpenses: number; plannedReserve: number; plannedSurplus: number; actualIncome: number | null; actualExpenses: number | null; actualSurplus: number | null; remainingBudget: number | null; overspent: boolean | null; unmatchedPostings: number; items: { item: BudgetItem; quality: Quality; actual: number | null; knownActual: number | null; remaining: number | null; overspent: boolean | null }[]; warnings: string[] }
