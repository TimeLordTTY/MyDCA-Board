export type ThemeMode = 'light' | 'dark' | 'system' | 'legacy'
export type Palette = 'cream' | 'blue' | 'peach'
export interface Appearance { version: 1; mode: ThemeMode; palette: Palette }
export const APPEARANCE_KEY = 'wealthhub.appearance.v1'
export const DEFAULT_APPEARANCE: Appearance = { version: 1, mode: 'light', palette: 'cream' }

// Copy only whitelisted appearance fields, never arbitrary stored/user data.
export function parseAppearance(value: unknown): Appearance {
  if (!value || typeof value !== 'object') return { ...DEFAULT_APPEARANCE }
  const data = value as Record<string, unknown>
  if (data.version !== 1 || !['light', 'dark', 'system', 'legacy'].includes(String(data.mode)) ||
      !['cream', 'blue', 'peach'].includes(String(data.palette))) return { ...DEFAULT_APPEARANCE }
  return { version: 1, mode: data.mode as ThemeMode, palette: data.palette as Palette }
}

export function createAppearanceController(
  root: HTMLElement, storage: Pick<Storage, 'getItem' | 'setItem'> | undefined,
  media: Pick<MediaQueryList, 'matches' | 'addEventListener' | 'removeEventListener'>,
) {
  let current = { ...DEFAULT_APPEARANCE }
  try { current = parseAppearance(JSON.parse(storage?.getItem(APPEARANCE_KEY) || 'null')) } catch { /* default when storage is unavailable */ }
  const apply = () => {
    root.dataset.whTheme = current.mode === 'system' ? (media.matches ? 'dark' : 'light') : current.mode
    root.dataset.whPalette = current.palette
    root.classList.toggle('dark', root.dataset.whTheme === 'dark')
  }
  const onSystemChange = () => { if (current.mode === 'system') apply() }
  media.addEventListener('change', onSystemChange)
  apply()
  return {
    get: () => ({ ...current }),
    set(value: unknown) {
      current = parseAppearance(value)
      apply()
      try { storage?.setItem(APPEARANCE_KEY, JSON.stringify(current)) } catch { /* in-memory appearance remains usable */ }
    },
    dispose: () => media.removeEventListener('change', onSystemChange),
  }
}

export type AppearanceController = ReturnType<typeof createAppearanceController>
let controller: AppearanceController | undefined
export function initializeAppearance() {
  let storage: Storage | undefined
  try { storage = window.localStorage } catch { /* browser may deny storage access */ }
  controller = createAppearanceController(document.documentElement, storage, window.matchMedia('(prefers-color-scheme: dark)'))
}
export function getAppearanceController() {
  if (!controller) initializeAppearance()
  return controller!
}
