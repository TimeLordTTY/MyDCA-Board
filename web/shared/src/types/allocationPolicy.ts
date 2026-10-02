/** All ratios are fractions; deviation is in percentage points. */
export interface AllocationConfig {
  scope: 'PERSONAL' | 'FAMILY'
  productId: number | null
  assetType: string | null
  target: number
  lowerBound: number
  upperBound: number
  returnThreshold: number | null
  takeProfitThresholds: number[]
  enabled: boolean
  note: string | null
}
export interface AllocationPolicy { id: string; config: AllocationConfig; createdAt: string; disclaimer: string }
export interface AllocationEvaluation {
  policyId: string
  status: 'IN_RANGE' | 'BELOW_BAND' | 'ABOVE_BAND' | 'TAKE_PROFIT_WATCH' | 'UNKNOWN'
  reason: string
  allocation: number | null
  returnRate: number | null
  reachedTakeProfitThresholds: number[]
  evaluatedAt: string
  disclaimer: string
}
export interface RebalanceScenario { selectedAdjustment: number; remainderAdjustment: number }
export interface RebalancePreview {
  policyId: string; status: string; description: string
  totalAssets: number | null; currentWeight: number | null; targetWeight: number
  lowerBound: number; upperBound: number; deviationPercentagePoints: number | null
  targetScenario: RebalanceScenario | null; bandScenario: RebalanceScenario | null
  dataDate: string | null
  prices: { productId: number; status: string; priceDate: string | null; valuationDate: string | null; priceSource: string }[]
  warnings: { code: string; message: string }[]
}
