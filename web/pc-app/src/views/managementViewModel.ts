import type { Account } from '@wealth-hub/shared'

// Search keeps the original tree and balances intact, including siblings of a match.
export function filterAccountTree(tree: Account[], keyword: string, type: string): Account[] {
  const term = keyword.trim().toLocaleLowerCase()
  const matches = (account: Account): boolean =>
    (!type || account.accountType === type) &&
    (!term || `${account.accountName} ${account.accountCode || ''}`.toLocaleLowerCase().includes(term))
  const branchMatches = (account: Account): boolean => matches(account) || (account.children || []).some(branchMatches)
  return tree.filter(branchMatches)
}

export function knownNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

export function readError(error: unknown, subject: string): string {
  const status = (error as { response?: { status?: number } })?.response?.status
  return status === 401 || status === 403
    ? `暂时无法查看${subject}，请重新登录并确认访问权限。`
    : `${subject}读取失败，请检查网络后重试；这不表示没有数据。`
}

// Each read is bound to the identity and latest request, without storing credentials.
export function createReadGate(identity: () => string) {
  let sequence = 0
  let active = true
  return {
    begin() {
      const ticket = ++sequence, owner = identity()
      return () => active && ticket === sequence && owner === identity()
    },
    invalidate() { sequence++ },
    suspend() { active = false; sequence++ },
    activate() { active = true },
    get active() { return active },
  }
}
