import { apiClient } from './client'
import type { RiskConfig, RiskRule, RiskSnapshot, RiskEvent } from '../types/riskWatch'
const base = '/risk-watch-rules'
const path = (id: string) => `${base}/${encodeURIComponent(id)}`
export const riskWatchApi = {
  list: async (page = 0): Promise<RiskRule[]> => (await apiClient.get(base, { params: { page, size: 50 } })).data,
  create: async (config: RiskConfig): Promise<RiskRule> => (await apiClient.post(base, config)).data,
  edit: async (id: string, config: RiskConfig): Promise<RiskRule> => (await apiClient.patch(path(id), config)).data,
  evaluate: async (id: string): Promise<RiskSnapshot> => (await apiClient.post(`${path(id)}/evaluate`)).data,
  snapshots: async (id: string): Promise<RiskSnapshot[]> => (await apiClient.get(`${path(id)}/snapshots`, { params: { page: 0, size: 1 } })).data,
  events: async (id: string, page = 0): Promise<RiskEvent[]> => (await apiClient.get(`${path(id)}/events`, { params: { page, size: 50 } })).data,
  acknowledge: async (id: string, fingerprint: string): Promise<void> => { await apiClient.patch(`${path(id)}/events/${encodeURIComponent(fingerprint)}/acknowledge`) },
  mute: async (id: string, mutedUntil: string | null): Promise<void> => { await apiClient.patch(`${path(id)}/mute`, { mutedUntil }) },
}
