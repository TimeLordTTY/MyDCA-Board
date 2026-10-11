import type { UserInfo } from '@wealth-hub/shared'

// Only existing, authenticated business routes belong in the wealth navigation.
export const navItems = [
  { name: 'Dashboard', path: '/dashboard', label: '总览', icon: 'House', group: '生活空间' },
  { name: 'GoalBudgetCenter', path: '/goal-budget-center', label: '目标与预算', icon: 'Flag', group: '生活空间' },
  { name: 'Ledger', path: '/ledger', label: '流水', icon: 'Tickets', group: '日常收支' },
  { name: 'DraftInbox', path: '/drafts', label: '草稿箱', icon: 'EditPen', group: '日常收支' },
  { name: 'Orders', path: '/orders', label: '订单与结算', icon: 'Document', group: '投资记录' },
  { name: 'Settlements', path: '/settlements', label: '待结算与审计', icon: 'Checked', group: '投资记录' },
  { name: 'Products', path: '/products', label: '产品', icon: 'Goods', group: '我的资产' },
  { name: 'Accounts', path: '/accounts', label: '账户', icon: 'Wallet', group: '我的资产' },
  { name: 'Holdings', path: '/holdings', label: '持仓管理', icon: 'Collection', group: '我的资产' },
  { name: 'StrategyLab', path: '/strategy-lab', label: '策略实验室', icon: 'DataAnalysis', group: '观察研究' },
  { name: 'RiskCenter', path: '/risk-center', label: '风险观察', icon: 'Warning', group: '观察研究' },
  { name: 'RebalanceCenter', path: '/rebalance-center', label: '配置观察', icon: 'Operation', group: '观察研究' },
  { name: 'DataReadiness', path: '/data-readiness', label: '数据就绪检查', icon: 'CircleCheck', group: '来源与管理' },
  { name: 'Settings', path: '/settings', label: '设置', icon: 'Setting', group: '来源与管理' },
]

export function identityContext(user: UserInfo | null) {
  if (!user) return { account: '身份未确认', scope: '身份读取中或不可用；权限以页面校验为准' }
  return {
    account: user.nickname ? `${user.nickname}（${user.username}）` : user.username,
    // UserInfo carries familyId, but no role or active page scope. Never infer ADMIN.
    scope: user.familyId ? `家庭 #${user.familyId} · 当前作用域以页面选择为准` : '个人账号 · 无家庭关联',
  }
}
