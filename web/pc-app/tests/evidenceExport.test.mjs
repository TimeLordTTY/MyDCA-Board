import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

test('evidence API rejects failed downloads and the PC keeps an error state', async () => {
  const { apiClient, backtestApi } = await import('../../shared/dist/index.js')
  const originalPost = apiClient.post
  apiClient.post = async () => { throw new Error('network failed') }
  try {
    await assert.rejects(backtestApi.evidence(['one', 'two'], {
      minSampleDays: 180, maxDrawdown: 0.3, minBaselineAnnualizedDelta: 0, minTradeCount: 3,
    }), /network failed/)
  } finally {
    apiClient.post = originalPost
  }
  const view = await readFile(new URL('../src/views/StrategyLab.vue', import.meta.url), 'utf8')
  assert.match(view, /v-if="exportError" role="alert"/)
  assert.match(view, /catch \{\s*exportError\.value = '研究证据导出失败/)
  assert.match(view, /finally \{ exporting\.value = false \}/)
})
