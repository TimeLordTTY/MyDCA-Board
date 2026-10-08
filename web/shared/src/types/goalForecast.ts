import type { Quality, GoalProgress, BudgetComparison } from './goalBudget'
export interface ForecastRequest {
  startMonth: string; endMonth: string; mode: 'PLANNED' | 'ACTUAL_PLUS_REMAINING'
  months: { month: string; budgetId: string | null; cashflowCovered: boolean }[]
  monthlyExtraSavings: string | null; annualRate: string | null
}
export interface ForecastMonth {
  month: string; budgetId: string | null; quality: Quality; reason: string
  startingProgress: number | null; plannedIncome: number | null; plannedExpenses: number | null
  plannedReserve: number | null; actualReading: BudgetComparison | null; modeledIncome: number | null
  modeledExpenses: number | null; surplusUpperBound: number | null; contribution: number | null
  mathematicalReturn: number | null; cumulativeProgress: number | null; remainingGap: number | null
}
export interface Projection { annualRate: number; assumption: string; quality: Quality; outcome: string; achievedMonth: string | null; months: ForecastMonth[] }
export interface Forecast { goalId: string; currency: string; asOfDate: string; actualProgress: GoalProgress; fixedInputs: ForecastRequest; baseline: Projection; mathematicalScenario: Projection | null }
export interface ScenarioInput { name: string; cashflow: ForecastRequest; allocations: { goalId: string; monthlyAmount: string | null }[] }
export interface ScenarioResult {
  name: string; assumptions: ScenarioInput; warnings: string[]
  months: { month: string; sharedUpperBound: number | null; specifiedTotal: number; allocationStatus: string; overLimit: boolean | null }[]
  goals: { goalId: string; targetDate: string; allocationStatus: string; forecast: Forecast; deadlineStatus: string }[]
}
export interface ScenarioComparison { readStartedAt: string; readCompletedAt: string; source: string; scenarios: ScenarioResult[]; changes: { field: string; before: unknown; after: unknown }[] }
