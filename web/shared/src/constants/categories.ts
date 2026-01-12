/**
 * 分类常量
 * 参考 sql/V1/DML.sql 中的分类数据
 */

export interface Category {
  id: number
  entryType: 'expense' | 'income'
  categoryL1: string
  categoryL2?: string
  displayOrder: number
}

export interface CategoryGroup {
  categoryL1: string
  categories: Category[]
}

// 支出分类
export const expenseCategories: Category[] = [
  { id: 1, entryType: 'expense', categoryL1: '其他', categoryL2: undefined, displayOrder: 0 },
  { id: 2, entryType: 'expense', categoryL1: '购物消费', categoryL2: '日常家具', displayOrder: 1 },
  { id: 3, entryType: 'expense', categoryL1: '购物消费', categoryL2: '个护美妆', displayOrder: 2 },
  { id: 4, entryType: 'expense', categoryL1: '购物消费', categoryL2: '手机数码', displayOrder: 3 },
  { id: 5, entryType: 'expense', categoryL1: '购物消费', categoryL2: '虚拟充值', displayOrder: 4 },
  { id: 6, entryType: 'expense', categoryL1: '购物消费', categoryL2: '生活电器', displayOrder: 5 },
  { id: 7, entryType: 'expense', categoryL1: '购物消费', categoryL2: '配饰腕表', displayOrder: 6 },
  { id: 8, entryType: 'expense', categoryL1: '购物消费', categoryL2: '母婴玩具', displayOrder: 7 },
  { id: 9, entryType: 'expense', categoryL1: '购物消费', categoryL2: '服饰运动', displayOrder: 8 },
  { id: 10, entryType: 'expense', categoryL1: '购物消费', categoryL2: '宠物用品', displayOrder: 9 },
  { id: 11, entryType: 'expense', categoryL1: '购物消费', categoryL2: '办公用品', displayOrder: 10 },
  { id: 12, entryType: 'expense', categoryL1: '购物消费', categoryL2: '装修装饰', displayOrder: 11 },
  { id: 13, entryType: 'expense', categoryL1: '食品餐饮', categoryL2: '水果', displayOrder: 20 },
  { id: 14, entryType: 'expense', categoryL1: '食品餐饮', categoryL2: '早餐', displayOrder: 21 },
  { id: 15, entryType: 'expense', categoryL1: '食品餐饮', categoryL2: '午餐', displayOrder: 22 },
  { id: 16, entryType: 'expense', categoryL1: '食品餐饮', categoryL2: '晚餐', displayOrder: 23 },
  { id: 17, entryType: 'expense', categoryL1: '食品餐饮', categoryL2: '饮料酒水', displayOrder: 24 },
  { id: 18, entryType: 'expense', categoryL1: '食品餐饮', categoryL2: '休闲零食', displayOrder: 25 },
  { id: 19, entryType: 'expense', categoryL1: '食品餐饮', categoryL2: '生鲜食品', displayOrder: 26 },
  { id: 20, entryType: 'expense', categoryL1: '出行交通', categoryL2: '公共交通', displayOrder: 30 },
  { id: 21, entryType: 'expense', categoryL1: '出行交通', categoryL2: '打车租车', displayOrder: 31 },
  { id: 22, entryType: 'expense', categoryL1: '出行交通', categoryL2: '共享单车', displayOrder: 32 },
  { id: 23, entryType: 'expense', categoryL1: '出行交通', categoryL2: '加油', displayOrder: 33 },
  { id: 24, entryType: 'expense', categoryL1: '出行交通', categoryL2: '停车', displayOrder: 34 },
  { id: 25, entryType: 'expense', categoryL1: '出行交通', categoryL2: '机票', displayOrder: 35 },
  { id: 26, entryType: 'expense', categoryL1: '出行交通', categoryL2: '火车', displayOrder: 36 },
  { id: 27, entryType: 'expense', categoryL1: '休闲娱乐', categoryL2: '电影唱歌', displayOrder: 40 },
  { id: 28, entryType: 'expense', categoryL1: '休闲娱乐', categoryL2: '游戏', displayOrder: 41 },
  { id: 29, entryType: 'expense', categoryL1: '休闲娱乐', categoryL2: '旅行度假', displayOrder: 42 },
  { id: 30, entryType: 'expense', categoryL1: '休闲娱乐', categoryL2: '运动健身', displayOrder: 43 },
  { id: 31, entryType: 'expense', categoryL1: '休闲娱乐', categoryL2: '足浴按摩', displayOrder: 44 },
  { id: 32, entryType: 'expense', categoryL1: '休闲娱乐', categoryL2: '棋牌桌游', displayOrder: 45 },
  { id: 33, entryType: 'expense', categoryL1: '休闲娱乐', categoryL2: '酒吧', displayOrder: 46 },
  { id: 34, entryType: 'expense', categoryL1: '休闲娱乐', categoryL2: '演出', displayOrder: 47 },
  { id: 35, entryType: 'expense', categoryL1: '居家生活', categoryL2: '话费宽带', displayOrder: 50 },
  { id: 36, entryType: 'expense', categoryL1: '居家生活', categoryL2: '电费', displayOrder: 51 },
  { id: 37, entryType: 'expense', categoryL1: '居家生活', categoryL2: '水费', displayOrder: 52 },
  { id: 38, entryType: 'expense', categoryL1: '居家生活', categoryL2: '燃气费', displayOrder: 53 },
  { id: 39, entryType: 'expense', categoryL1: '居家生活', categoryL2: '物业费', displayOrder: 54 },
  { id: 40, entryType: 'expense', categoryL1: '居家生活', categoryL2: '房租还贷', displayOrder: 55 },
  { id: 41, entryType: 'expense', categoryL1: '居家生活', categoryL2: '车位费', displayOrder: 56 },
  { id: 42, entryType: 'expense', categoryL1: '居家生活', categoryL2: '家政清洁', displayOrder: 57 },
  { id: 43, entryType: 'expense', categoryL1: '文化教育', categoryL2: '学费', displayOrder: 60 },
  { id: 44, entryType: 'expense', categoryL1: '文化教育', categoryL2: '培训考试', displayOrder: 61 },
  { id: 45, entryType: 'expense', categoryL1: '文化教育', categoryL2: '书报杂志', displayOrder: 62 },
  { id: 46, entryType: 'expense', categoryL1: '送礼人情', categoryL2: '红包礼金', displayOrder: 70 },
  { id: 47, entryType: 'expense', categoryL1: '送礼人情', categoryL2: '礼物', displayOrder: 71 },
  { id: 48, entryType: 'expense', categoryL1: '送礼人情', categoryL2: '孝敬长辈', displayOrder: 72 },
  { id: 49, entryType: 'expense', categoryL1: '健康医疗', categoryL2: '医院', displayOrder: 80 },
  { id: 50, entryType: 'expense', categoryL1: '健康医疗', categoryL2: '体检保险', displayOrder: 81 },
  { id: 51, entryType: 'expense', categoryL1: '健康医疗', categoryL2: '买药', displayOrder: 82 },
  { id: 52, entryType: 'expense', categoryL1: '理财投资', categoryL2: '基金定投', displayOrder: 90 },
  { id: 53, entryType: 'expense', categoryL1: '理财投资', categoryL2: '定期理财', displayOrder: 91 },
  { id: 54, entryType: 'expense', categoryL1: '理财投资', categoryL2: '基金补仓', displayOrder: 92 },
  { id: 70, entryType: 'expense', categoryL1: '转账', categoryL2: '转出', displayOrder: 100 },
  { id: 74, entryType: 'expense', categoryL1: '理财投资', categoryL2: '赎回持仓减少', displayOrder: 93 },
]

// 收入分类
export const incomeCategories: Category[] = [
  { id: 55, entryType: 'income', categoryL1: '其他', categoryL2: undefined, displayOrder: 0 },
  { id: 56, entryType: 'income', categoryL1: '初始余额', categoryL2: undefined, displayOrder: 10 },
  { id: 57, entryType: 'income', categoryL1: '退款', categoryL2: undefined, displayOrder: 20 },
  { id: 58, entryType: 'income', categoryL1: '工资', categoryL2: undefined, displayOrder: 30 },
  { id: 59, entryType: 'income', categoryL1: '奖金', categoryL2: undefined, displayOrder: 40 },
  { id: 60, entryType: 'income', categoryL1: '兼职外快', categoryL2: undefined, displayOrder: 50 },
  { id: 61, entryType: 'income', categoryL1: '理财盈利', categoryL2: '利息收益', displayOrder: 60 },
  { id: 62, entryType: 'income', categoryL1: '理财盈利', categoryL2: '基金分红', displayOrder: 61 },
  { id: 63, entryType: 'income', categoryL1: '理财盈利', categoryL2: '产品赎回', displayOrder: 62 },
  { id: 64, entryType: 'income', categoryL1: '中奖', categoryL2: undefined, displayOrder: 70 },
  { id: 65, entryType: 'income', categoryL1: '礼金人情', categoryL2: undefined, displayOrder: 80 },
  { id: 66, entryType: 'income', categoryL1: '借入', categoryL2: undefined, displayOrder: 90 },
  { id: 67, entryType: 'income', categoryL1: '二手闲置', categoryL2: undefined, displayOrder: 100 },
  { id: 68, entryType: 'income', categoryL1: '补贴', categoryL2: undefined, displayOrder: 110 },
  { id: 69, entryType: 'income', categoryL1: '报销', categoryL2: undefined, displayOrder: 120 },
  { id: 71, entryType: 'income', categoryL1: '转账', categoryL2: '转入', displayOrder: 130 },
  { id: 72, entryType: 'income', categoryL1: '理财投资', categoryL2: '买入确认', displayOrder: 63 },
  { id: 73, entryType: 'income', categoryL1: '理财投资', categoryL2: '赎回确认', displayOrder: 64 },
]

/**
 * 获取分类的分组列表（按一级分类分组）
 */
export function getCategoryGroups(categories: Category[]): CategoryGroup[] {
  const groups = new Map<string, Category[]>()
  
  categories.forEach(cat => {
    if (!groups.has(cat.categoryL1)) {
      groups.set(cat.categoryL1, [])
    }
    groups.get(cat.categoryL1)!.push(cat)
  })
  
  return Array.from(groups.entries())
    .map(([categoryL1, categories]) => ({
      categoryL1,
      categories: categories.sort((a, b) => a.displayOrder - b.displayOrder),
    }))
    .sort((a, b) => {
      const orderA = a.categories[0]?.displayOrder || 0
      const orderB = b.categories[0]?.displayOrder || 0
      return orderA - orderB
    })
}

/**
 * 获取分类显示名称
 */
export function getCategoryDisplayName(category: Category): string {
  if (category.categoryL2) {
    return `${category.categoryL1} - ${category.categoryL2}`
  }
  return category.categoryL1
}

/**
 * 根据ID查找分类
 */
export function findCategoryById(
  categories: Category[],
  id: number
): Category | undefined {
  return categories.find(cat => cat.id === id)
}
