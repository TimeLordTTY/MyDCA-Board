import React, { useMemo, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  BarChart,
  Bar,
  CartesianGrid,
  Legend,
} from "recharts";
import {
  Plus,
  RefreshCw,
  Wallet,
  CalendarDays,
  Activity,
  Brain,
  AlertTriangle,
  FileText,
} from "lucide-react";

/**
 * 理财系统演示 UI
 * - 纯前台交互 Demo（无后端）
 * - 功能与我们讨论的架构对齐：资产台账/定投引擎/行情同步/分析复盘/目标/策略/AI扩展
 *
 * 用法：
 * 1) 直接作为 App 页面渲染即可
 * 2) 后续你可以把 mockData 替换为接口数据
 */

// ---------- Mock 基础数据 ----------
const todayStr = () => new Date().toISOString().slice(0, 10);
const DAYS = Array.from({ length: 30 }).map((_, i) => {
  const d = new Date();
  d.setDate(d.getDate() - (29 - i));
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${mm}-${dd}`;
});

const seedSeries = (base = 100000) =>
  DAYS.map((date, i) => ({
    date,
    total: Math.round(
      base + i * 180 + Math.sin(i / 3) * 800 - Math.max(0, i - 20) * 220
    ),
  }));

const initialAssets = [
  {
    id: "cash",
    name: "现金/活钱",
    category: "现金",
    amount: 28000,
    cost: 28000,
    color: "#f9a8d4",
  },
  {
    id: "fix",
    name: "固收/理财",
    category: "固收",
    amount: 52000,
    cost: 50000,
    color: "#fdba74",
  },
  {
    id: "equity",
    name: "权益(基金/ETF)",
    category: "权益",
    amount: 73500,
    cost: 70000,
    color: "#a7f3d0",
  },
  {
    id: "gold",
    name: "黄金",
    category: "黄金",
    amount: 18600,
    cost: 17000,
    color: "#fde68a",
  },
];

const initialFunds = [
  {
    id: "f1",
    code: "F-景顺长城",
    name: "景顺长城精选",
    category: "权益",
    plan: { freq: "WEEKLY", weekday: 1, amount: 200 }, // 1=周一
    nav: 1.023,
    shares: 2300,
    cost: 2400,
    maxDrawdown: 0.1507,
    ddRepairing: true,
    rank1m: 311,
  },
  {
    id: "f2",
    code: "F-兴全合润",
    name: "兴全合润混合",
    category: "权益",
    plan: { freq: "WEEKLY", weekday: 4, amount: 300 }, // 周四
    nav: 0.987,
    shares: 3800,
    cost: 3600,
    maxDrawdown: 0.092,
    ddRepairing: false,
    rank1m: 120,
  },
  {
    id: "f3",
    code: "ETF-纳指",
    name: "华夏纳指 ETF",
    category: "权益",
    plan: { freq: "MONTHLY", day: 10, amount: 500 },
    nav: 1.338,
    shares: 1600,
    cost: 1900,
    maxDrawdown: 0.18,
    ddRepairing: true,
    rank1m: 80,
  },
  {
    id: "f4",
    code: "ETF-黄金",
    name: "华安黄金 ETF",
    category: "黄金",
    plan: { freq: "WEEKLY", weekday: 2, amount: 150 },
    nav: 1.112,
    shares: 800,
    cost: 850,
    maxDrawdown: 0.07,
    ddRepairing: false,
    rank1m: 40,
  },
];

const initialInvestLogs = [
  {
    id: "l1",
    date: DAYS[26],
    fundId: "f1",
    planAmount: 200,
    actualAmount: 200,
    nav: 1.01,
  },
  {
    id: "l2",
    date: DAYS[27],
    fundId: "f4",
    planAmount: 150,
    actualAmount: 150,
    nav: 1.1,
  },
  {
    id: "l3",
    date: DAYS[28],
    fundId: "f2",
    planAmount: 300,
    actualAmount: 0,
    nav: 0.98,
    status: "MISSED",
  },
  {
    id: "l4",
    date: DAYS[29],
    fundId: "f1",
    planAmount: 200,
    actualAmount: 220,
    nav: 1.023,
    status: "EXTRA",
  },
];

const initialGoals = [
  {
    id: "g1",
    name: "3年 100万存款",
    target: 1000000,
    deadline: "2028-12-31",
  },
];

// ---------- 小组件 ----------
const Chip = ({ children, tone = "pink" }) => {
  const tones = {
    pink: "bg-pink-100 text-pink-700 border-pink-200",
    green: "bg-emerald-100 text-emerald-700 border-emerald-200",
    amber: "bg-amber-100 text-amber-700 border-amber-200",
    slate: "bg-slate-100 text-slate-700 border-slate-200",
    red: "bg-rose-100 text-rose-700 border-rose-200",
  };
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 text-xs border rounded-full ${
        tones[tone] || tones.slate
      }`}
    >
      {children}
    </span>
  );
};

const Card = ({ children, className = "" }) => (
  <div
    className={`bg-white/80 backdrop-blur rounded-2xl shadow-sm border border-pink-100 ${className}`}
  >
    {children}
  </div>
);

const SectionTitle = ({ icon: Icon, title, extra }) => (
  <div className="flex items-center justify-between mb-3">
    <div className="flex items-center gap-2">
      <div className="p-2 rounded-xl bg-pink-50 border border-pink-100">
        <Icon className="w-4 h-4 text-pink-600" />
      </div>
      <div className="font-semibold text-slate-800">{title}</div>
    </div>
    {extra}
  </div>
);

// ---------- 主页面 ----------
export default function FinanceSystemDemo() {
  const [tab, setTab] = useState("dashboard");
  const [assets, setAssets] = useState(initialAssets);
  const [funds, setFunds] = useState(initialFunds);
  const [logs, setLogs] = useState(initialInvestLogs);
  const [series, setSeries] = useState(seedSeries());
  const [goals] = useState(initialGoals);
  const [syncing, setSyncing] = useState(false);

  // 选中基金
  const [selectedFundId, setSelectedFundId] = useState(funds[0]?.id);
  const selectedFund = useMemo(
    () => funds.find((f) => f.id === selectedFundId),
    [funds, selectedFundId]
  );

  const totals = useMemo(() => {
    const total = assets.reduce((s, a) => s + a.amount, 0);
    const cost = assets.reduce((s, a) => s + a.cost, 0);
    const profit = total - cost;
    const profitRate = cost > 0 ? profit / cost : 0;
    return { total, cost, profit, profitRate };
  }, [assets]);

  const allocationData = useMemo(() => {
    const map = new Map();
    assets.forEach((a) =>
      map.set(a.category, (map.get(a.category) || 0) + a.amount)
    );
    return Array.from(map.entries()).map(([name, value]) => ({
      name,
      value,
    }));
  }, [assets]);

  const weeklyInvestSummary = useMemo(() => {
    const last7 = DAYS.slice(-7);
    return last7.map((d) => {
      const dayLogs = logs.filter((l) => l.date === d);
      const plan = dayLogs.reduce((s, l) => s + l.planAmount, 0);
      const actual = dayLogs.reduce((s, l) => s + l.actualAmount, 0);
      return { date: d.slice(5), plan, actual };
    });
  }, [logs]);

  // 同步行情（mock）
  const syncMarket = () => {
    setSyncing(true);
    setTimeout(() => {
      setFunds((prev) =>
        prev.map((f) => {
          const drift = 1 + (Math.random() - 0.5) * 0.02;
          const nav = Math.max(0.3, +(f.nav * drift).toFixed(3));
          return { ...f, nav };
        })
      );

      setAssets((prev) =>
        prev.map((a) => {
          if (a.category === "权益") {
            return {
              ...a,
              amount: +(a.amount * (1 + (Math.random() - 0.5) * 0.015)).toFixed(2),
            };
          }
          if (a.category === "黄金") {
            return {
              ...a,
              amount: +(a.amount * (1 + (Math.random() - 0.5) * 0.01)).toFixed(2),
            };
          }
          return a;
        })
      );

      setSeries((prev) => {
        const last = prev[prev.length - 1]?.total || 100000;
        const next = Math.round(last * (1 + (Math.random() - 0.5) * 0.008));
        return [...prev.slice(1), { date: todayStr(), total: next }];
      });

      setSyncing(false);
    }, 700);
  };

  // 记录一次定投（手动模拟）
  const addInvestLog = (fundId, amountOverride) => {
    const f = funds.find((x) => x.id === fundId);
    if (!f) return;
    const planAmount = f.plan.amount;
    const actualAmount = amountOverride ?? planAmount;
    const nav = f.nav;

    setLogs((prev) => [
      ...prev,
      {
        id: `l_${Date.now()}`,
        date: todayStr(),
        fundId,
        planAmount,
        actualAmount,
        nav,
        status:
          actualAmount === planAmount
            ? "OK"
            : actualAmount > planAmount
            ? "EXTRA"
            : "PARTIAL",
      },
    ]);

    setFunds((prev) =>
      prev.map((x) =>
        x.id === fundId
          ? {
              ...x,
              shares: +(x.shares + actualAmount / nav).toFixed(2),
              cost: +(x.cost + actualAmount).toFixed(2),
            }
          : x
      )
    );

    setAssets((prev) =>
      prev.map((a) =>
        a.category === f.category
          ? {
              ...a,
              amount: +(a.amount + actualAmount).toFixed(2),
              cost: +(a.cost + actualAmount).toFixed(2),
            }
          : a
      )
    );
  };

  const aiInsight = useMemo(() => {
    if (!selectedFund) return "";
    const msgs = [];
    if (selectedFund.ddRepairing)
      msgs.push("处于回撤修复期，可继续小额定投，避免情绪化停投。");
    if (selectedFund.maxDrawdown > 0.12)
      msgs.push("历史最大回撤偏大，适合作为长期仓位而非短线。");
    if (selectedFund.rank1m > 250)
      msgs.push("近1个月同类排名落后，短期可能承压但也可能处于低位区间。");
    if (msgs.length === 0) msgs.push("当前指标较平稳，可按既定计划执行。");
    return msgs.join(" ");
  }, [selectedFund]);

  const goalProgress = useMemo(() => {
    const g = goals[0];
    if (!g) return null;
    const pct = Math.min(1, totals.total / g.target);
    const remain = g.target - totals.total;
    return { ...g, pct, remain };
  }, [goals, totals.total]);

  // 复盘导出 markdown（避免 JSX 内嵌套模板字符串导致解析问题）
  const reviewMarkdown = useMemo(() => {
    const planSum = weeklyInvestSummary.reduce((s, d) => s + d.plan, 0);
    const actualSum = weeklyInvestSummary.reduce((s, d) => s + d.actual, 0);
    const completion = planSum > 0 ? ((actualSum / planSum) * 100).toFixed(1) : "0.0";
    const allocLines = allocationData
      .map((x) => `- ${x.name}: ¥ ${Number(x.value).toLocaleString()}`)
      .join("\n");
    const repairingLines = funds
      .filter((f) => f.ddRepairing)
      .map((f) => `- ${f.name}: 最大回撤 ${(
        f.maxDrawdown * 100
      ).toFixed(2)}%`)
      .join("\n");

    return [
      "# 本周理财复盘（Demo）",
      "",
      `- 时间：${DAYS.slice(-7)[0]} ~ ${DAYS.slice(-1)[0]}`,
      `- 计划定投：¥ ${planSum}`,
      `- 实际定投：¥ ${actualSum}`,
      `- 完成度：${completion}%`,
      "",
      "## 资产结构",
      allocLines || "- 无",
      "",
      "## 回撤修复",
      repairingLines || "- 无",
      "",
      "## 一句话建议",
      "保持定投节奏，避免情绪化操作。",
    ].join("\n");
  }, [weeklyInvestSummary, allocationData, funds]);

  return (
    <div className="min-h-screen bg-gradient-to-br from-pink-50 via-rose-50 to-amber-50 text-slate-800">
      <div className="max-w-6xl mx-auto px-4 py-6">
        {/* Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 mb-5">
          <div>
            <div className="text-xl font-bold tracking-tight">主人专属 · 理财系统 Demo</div>
            <div className="text-sm text-slate-500">
              资产台账 · 定投引擎 · 行情同步 · 分析复盘 · 策略/AI
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={syncMarket}
              className="inline-flex items-center gap-2 px-3 py-2 rounded-xl bg-white border border-pink-200 shadow-sm hover:bg-pink-50 active:scale-[0.98]"
            >
              <RefreshCw
                className={`w-4 h-4 ${syncing ? "animate-spin" : ""}`}
              />
              {syncing ? "同步中" : "同步行情"}
            </button>
            <button
              onClick={() => setTab("invest")}
              className="inline-flex items-center gap-2 px-3 py-2 rounded-xl bg-pink-600 text-white shadow-sm hover:bg-pink-700 active:scale-[0.98]"
            >
              <Plus className="w-4 h-4" /> 记录定投
            </button>
          </div>
        </div>

        {/* Tabs */}
        <div className="flex flex-wrap gap-2 mb-5">
          {[ 
            { key: "dashboard", label: "总览", icon: Wallet },
            { key: "invest", label: "定投", icon: CalendarDays },
            { key: "analysis", label: "分析", icon: Activity },
            { key: "strategy", label: "策略", icon: AlertTriangle },
            { key: "ai", label: "AI助手", icon: Brain },
            { key: "review", label: "复盘", icon: FileText },
          ].map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-xl border shadow-sm transition ${
                tab === key
                  ? "bg-pink-600 text-white border-pink-600"
                  : "bg-white/80 border-pink-100 hover:bg-pink-50"
              }`}
            >
              <Icon className="w-4 h-4" /> {label}
            </button>
          ))}
        </div>

        {/* Content */}
        <AnimatePresence mode="wait">
          {tab === "dashboard" && (
            <motion.div
              key="dashboard"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              className="grid grid-cols-1 lg:grid-cols-3 gap-4"
            >
              {/* 总资产 */}
              <Card className="p-4 lg:col-span-1">
                <SectionTitle icon={Wallet} title="总资产" />
                <div className="text-3xl font-bold">
                  ¥ {totals.total.toLocaleString()}
                </div>
                <div className="mt-2 text-sm text-slate-600 flex items-center gap-2">
                  <div>成本：¥ {totals.cost.toLocaleString()}</div>
                  <div>
                    收益：
                    <span
                      className={
                        totals.profit >= 0
                          ? "text-emerald-600 font-semibold"
                          : "text-rose-600 font-semibold"
                      }
                    >
                      ¥ {totals.profit.toLocaleString()} (
                      {(totals.profitRate * 100).toFixed(2)}%)
                    </span>
                  </div>
                </div>
                {goalProgress && (
                  <div className="mt-4">
                    <div className="flex items-center justify-between text-sm mb-1">
                      <span className="text-slate-600">
                        目标：{goalProgress.name}
                      </span>
                      <span>{(goalProgress.pct * 100).toFixed(1)}%</span>
                    </div>
                    <div className="h-2 rounded-full bg-pink-100 overflow-hidden">
                      <div
                        className="h-full bg-pink-500"
                        style={{ width: `${goalProgress.pct * 100}%` }}
                      />
                    </div>
                    <div className="text-xs text-slate-500 mt-1">
                      还差 ¥ {goalProgress.remain.toLocaleString()}
                    </div>
                  </div>
                )}
              </Card>

              {/* 资产配置 */}
              <Card className="p-4 lg:col-span-1">
                <SectionTitle icon={Activity} title="资产配置" />
                <div className="h-56">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={allocationData}
                        dataKey="value"
                        nameKey="name"
                        outerRadius={82}
                        innerRadius={50}
                        paddingAngle={3}
                      >
                        {allocationData.map((entry) => {
                          const color =
                            assets.find((a) => a.category === entry.name)
                              ?.color || "#ddd";
                          return <Cell key={entry.name} fill={color} />;
                        })}
                      </Pie>
                      <Tooltip
                        formatter={(v) => `¥ ${Number(v).toLocaleString()}`}
                      />
                      <Legend />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              </Card>

              {/* 资产曲线 */}
              <Card className="p-4 lg:col-span-1">
                <SectionTitle icon={Activity} title="资产趋势(30天)" />
                <div className="h-56">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={series} margin={{ left: 6, right: 6 }}>
                      <XAxis
                        dataKey="date"
                        tickFormatter={(d) => d.slice(5)}
                        fontSize={12}
                      />
                      <YAxis
                        fontSize={12}
                        tickFormatter={(v) => `${Math.round(v / 1000)}k`}
                      />
                      <Tooltip
                        formatter={(v) => `¥ ${Number(v).toLocaleString()}`}
                      />
                      <Line
                        type="monotone"
                        dataKey="total"
                        strokeWidth={2.5}
                        dot={false}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </Card>

              {/* 定投基金概览 */}
              <Card className="p-4 lg:col-span-2">
                <SectionTitle
                  icon={CalendarDays}
                  title="定投基金概览"
                  extra={<Chip tone="pink">自动记录/回撤修复</Chip>}
                />
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {funds.map((f) => {
                    const amount = +(f.shares * f.nav).toFixed(2);
                    const profit = amount - f.cost;
                    const profitRate = f.cost > 0 ? profit / f.cost : 0;
                    return (
                      <div
                        key={f.id}
                        className="p-3 rounded-xl bg-white border border-pink-100 flex items-center justify-between"
                      >
                        <div>
                          <div className="font-semibold">{f.name}</div>
                          <div className="text-xs text-slate-500">{f.code}</div>
                          <div className="mt-1 text-xs flex gap-1">
                            <Chip tone={f.ddRepairing ? "amber" : "green"}>
                              {f.ddRepairing ? "修复中" : "稳态"}
                            </Chip>
                            <Chip tone="slate">
                              最大回撤 {(f.maxDrawdown * 100).toFixed(2)}%
                            </Chip>
                          </div>
                        </div>
                        <div className="text-right">
                          <div className="text-sm">市值 ¥ {amount.toLocaleString()}</div>
                          <div
                            className={`text-sm font-semibold ${
                              profit >= 0
                                ? "text-emerald-600"
                                : "text-rose-600"
                            }`}
                          >
                            {profit >= 0 ? "+" : "-"}¥{" "}
                            {Math.abs(profit).toLocaleString()} (
                            {(profitRate * 100).toFixed(2)}%)
                          </div>
                          <button
                            onClick={() => {
                              setSelectedFundId(f.id);
                              setTab("ai");
                            }}
                            className="mt-1 text-xs text-pink-600 hover:underline"
                          >
                            AI解读
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </Card>

              {/* 近7天定投完成度 */}
              <Card className="p-4 lg:col-span-1">
                <SectionTitle icon={CalendarDays} title="近7天定投完成度" />
                <div className="h-56">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={weeklyInvestSummary} margin={{ left: 6, right: 6 }}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="date" fontSize={12} />
                      <YAxis fontSize={12} />
                      <Tooltip
                        formatter={(v) => `¥ ${Number(v).toLocaleString()}`}
                      />
                      <Legend />
                      <Bar dataKey="plan" name="计划" />
                      <Bar dataKey="actual" name="实际" />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </Card>
            </motion.div>
          )}

          {tab === "invest" && (
            <motion.div
              key="invest"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              className="grid grid-cols-1 lg:grid-cols-3 gap-4"
            >
              <Card className="p-4 lg:col-span-1">
                <SectionTitle
                  icon={CalendarDays}
                  title="定投计划"
                  extra={<Chip tone="pink">引擎</Chip>}
                />
                <div className="space-y-2">
                  {funds.map((f) => (
                    <button
                      key={f.id}
                      onClick={() => setSelectedFundId(f.id)}
                      className={`w-full text-left p-3 rounded-xl border transition ${
                        selectedFundId === f.id
                          ? "bg-pink-50 border-pink-300"
                          : "bg-white border-pink-100 hover:bg-pink-50"
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <div>
                          <div className="font-semibold">{f.name}</div>
                          <div className="text-xs text-slate-500">{f.code}</div>
                        </div>
                        <div className="text-right">
                          <div className="text-sm">¥ {f.plan.amount}/次</div>
                          <div className="text-xs text-slate-500">
                            {f.plan.freq === "WEEKLY"
                              ? `每周${["日", "一", "二", "三", "四", "五", "六"][
                                  f.plan.weekday
                                ]}`
                              : `每月${f.plan.day}日`}
                          </div>
                        </div>
                      </div>
                    </button>
                  ))}
                </div>
              </Card>

              <Card className="p-4 lg:col-span-2">
                <SectionTitle
                  icon={CalendarDays}
                  title="记录今天的定投"
                  extra={<Chip tone="amber">手动模拟</Chip>}
                />
                {selectedFund ? (
                  <div>
                    <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 p-3 rounded-xl bg-white border border-pink-100">
                      <div>
                        <div className="font-semibold text-lg">{selectedFund.name}</div>
                        <div className="text-sm text-slate-500">净值 NAV：{selectedFund.nav}</div>
                        <div className="mt-1 text-xs flex gap-1">
                          <Chip tone={selectedFund.ddRepairing ? "amber" : "green"}>
                            {selectedFund.ddRepairing ? "修复中" : "稳态"}
                          </Chip>
                          <Chip tone="slate">
                            最大回撤 {(selectedFund.maxDrawdown * 100).toFixed(2)}%
                          </Chip>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => addInvestLog(selectedFund.id)}
                          className="px-3 py-2 rounded-xl bg-pink-600 text-white hover:bg-pink-700 active:scale-[0.98]"
                        >
                          按计划定投 ¥{selectedFund.plan.amount}
                        </button>
                        <button
                          onClick={() => addInvestLog(selectedFund.id, selectedFund.plan.amount + 100)}
                          className="px-3 py-2 rounded-xl bg-white border border-pink-200 hover:bg-pink-50 active:scale-[0.98]"
                        >
                          额外加投 +100
                        </button>
                      </div>
                    </div>

                    <div className="mt-4">
                      <div className="font-semibold mb-2">定投记录（最近）</div>
                      <div className="divide-y divide-pink-100 rounded-xl border border-pink-100 bg-white">
                        {logs
                          .filter((l) => l.fundId === selectedFund.id)
                          .slice(-8)
                          .reverse()
                          .map((l) => (
                            <div key={l.id} className="p-3 flex items-center justify-between">
                              <div>
                                <div className="text-sm font-medium">{l.date}</div>
                                <div className="text-xs text-slate-500">
                                  计划 ¥{l.planAmount} ｜ 实际 ¥{l.actualAmount} ｜ NAV {l.nav}
                                </div>
                              </div>
                              <div>
                                {l.status === "MISSED" && <Chip tone="red">漏投</Chip>}
                                {l.status === "EXTRA" && <Chip tone="amber">加投</Chip>}
                                {(l.status === "OK" || l.status === "PARTIAL") && (
                                  <Chip tone="green">完成</Chip>
                                )}
                              </div>
                            </div>
                          ))}
                        {logs.filter((l) => l.fundId === selectedFund.id).length === 0 && (
                          <div className="p-4 text-sm text-slate-500">
                            还没有记录，点上面按钮模拟一笔吧~
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="text-sm text-slate-500">请先选择一只基金</div>
                )}
              </Card>
            </motion.div>
          )}

          {tab === "analysis" && (
            <motion.div
              key="analysis"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              className="grid grid-cols-1 lg:grid-cols-2 gap-4"
            >
              <Card className="p-4">
                <SectionTitle icon={Activity} title="单品类收益" />
                <div className="space-y-2">
                  {assets.map((a) => {
                    const profit = a.amount - a.cost;
                    const rate = a.cost > 0 ? profit / a.cost : 0;
                    return (
                      <div key={a.id} className="p-3 rounded-xl bg-white border border-pink-100 flex items-center justify-between">
                        <div>
                          <div className="font-semibold">{a.name}</div>
                          <div className="text-xs text-slate-500">{a.category}</div>
                        </div>
                        <div className="text-right">
                          <div className="text-sm">¥ {a.amount.toLocaleString()}</div>
                          <div className={`text-sm font-semibold ${profit >= 0 ? "text-emerald-600" : "text-rose-600"}`}>
                            {profit >= 0 ? "+" : "-"}¥ {Math.abs(profit).toLocaleString()} ({(rate * 100).toFixed(2)}%)
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </Card>

              <Card className="p-4">
                <SectionTitle icon={Activity} title="定投成本 vs 市值" />
                <div className="h-72">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart
                      data={funds.map((f) => ({
                        name: f.name.length > 6 ? f.name.slice(0, 6) + "…" : f.name,
                        cost: f.cost,
                        value: +(f.shares * f.nav).toFixed(2),
                      }))}
                      margin={{ left: 6, right: 6 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="name" fontSize={12} />
                      <YAxis fontSize={12} />
                      <Tooltip formatter={(v) => `¥ ${Number(v).toLocaleString()}`} />
                      <Legend />
                      <Bar dataKey="cost" name="成本" />
                      <Bar dataKey="value" name="市值" />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </Card>
            </motion.div>
          )}

          {tab === "strategy" && (
            <motion.div
              key="strategy"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              className="grid grid-cols-1 lg:grid-cols-2 gap-4"
            >
              <Card className="p-4">
                <SectionTitle
                  icon={AlertTriangle}
                  title="再平衡/止盈规则（Demo）"
                  extra={<Chip tone="slate">可配置</Chip>}
                />
                <div className="space-y-3 text-sm">
                  <div className="p-3 bg-white border border-pink-100 rounded-xl">
                    <div className="font-semibold">规则 1：权益占比上限</div>
                    <div className="text-slate-600 mt-1">若权益占比 &gt; 60%，提示减仓/提高固收。</div>
                  </div>
                  <div className="p-3 bg-white border border-pink-100 rounded-xl">
                    <div className="font-semibold">规则 2：单基金止盈</div>
                    <div className="text-slate-600 mt-1">单基金收益率 &gt; 15% 时提醒分批止盈。</div>
                  </div>
                  <div className="p-3 bg-white border border-pink-100 rounded-xl">
                    <div className="font-semibold">规则 3：回撤加仓</div>
                    <div className="text-slate-600 mt-1">回撤修复期允许额外加投（不超过计划 50%）。</div>
                  </div>
                </div>
              </Card>

              <Card className="p-4">
                <SectionTitle icon={AlertTriangle} title="当前风险提示" />
                <div className="space-y-2 text-sm">
                  {(() => {
                    const equity = allocationData.find((x) => x.name === "权益")?.value || 0;
                    const ratio = totals.total > 0 ? equity / totals.total : 0;
                    const warns = [];
                    if (ratio > 0.6) {
                      warns.push({
                        tone: "red",
                        text: `权益占比 ${(ratio * 100).toFixed(1)}% 偏高，建议提高固收或停投部分权益。`,
                      });
                    }
                    funds
                      .filter((f) => f.maxDrawdown > 0.12)
                      .forEach((f) => {
                        warns.push({
                          tone: "amber",
                          text: `${f.name} 历史回撤 ${(f.maxDrawdown * 100).toFixed(1)}%，请保持长期视角。`,
                        });
                      });
                    if (warns.length === 0) {
                      warns.push({ tone: "green", text: "当前风险指标平稳，按计划执行即可。" });
                    }

                    return warns.map((w, i) => (
                      <div key={i} className="p-3 bg-white border border-pink-100 rounded-xl flex items-start gap-2">
                        <Chip tone={w.tone}>{w.tone === "red" ? "高" : w.tone === "amber" ? "中" : "低"}</Chip>
                        <div className="text-slate-700">{w.text}</div>
                      </div>
                    ));
                  })()}
                </div>
              </Card>
            </motion.div>
          )}

          {tab === "ai" && (
            <motion.div
              key="ai"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              className="grid grid-cols-1 lg:grid-cols-3 gap-4"
            >
              <Card className="p-4 lg:col-span-1">
                <SectionTitle icon={Brain} title="选择基金" />
                <div className="space-y-2">
                  {funds.map((f) => (
                    <button
                      key={f.id}
                      onClick={() => setSelectedFundId(f.id)}
                      className={`w-full text-left p-3 rounded-xl border transition ${
                        selectedFundId === f.id
                          ? "bg-pink-50 border-pink-300"
                          : "bg-white border-pink-100 hover:bg-pink-50"
                      }`}
                    >
                      <div className="font-semibold">{f.name}</div>
                      <div className="text-xs text-slate-500">NAV {f.nav} · 近1月排名 {f.rank1m}</div>
                    </button>
                  ))}
                </div>
              </Card>

              <Card className="p-4 lg:col-span-2">
                <SectionTitle icon={Brain} title="AI 解读（Mock）" extra={<Chip tone="pink">未来接模型</Chip>} />
                {selectedFund ? (
                  <div className="space-y-3">
                    <div className="p-3 bg-white border border-pink-100 rounded-xl">
                      <div className="text-sm text-slate-500">基金</div>
                      <div className="text-lg font-semibold">{selectedFund.name}</div>
                    </div>
                    <div className="p-3 bg-white border border-pink-100 rounded-xl text-sm leading-relaxed">
                      {aiInsight}
                    </div>
                    <div className="p-3 bg-white border border-pink-100 rounded-xl grid grid-cols-1 md:grid-cols-3 gap-2 text-sm">
                      <div>净值：<b>{selectedFund.nav}</b></div>
                      <div>最大回撤：<b>{(selectedFund.maxDrawdown * 100).toFixed(2)}%</b></div>
                      <div>回撤状态：<b>{selectedFund.ddRepairing ? "修复中" : "非修复"}</b></div>
                    </div>
                    <div className="text-xs text-slate-500">
                      提示：接入真实模型后，这里会根据你自己的净值序列、资金流、宏观指标生成自然语言建议。
                    </div>
                  </div>
                ) : (
                  <div className="text-sm text-slate-500">请先选择一只基金</div>
                )}
              </Card>
            </motion.div>
          )}

          {tab === "review" && (
            <motion.div
              key="review"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              className="grid grid-cols-1 lg:grid-cols-2 gap-4"
            >
              <Card className="p-4">
                <SectionTitle icon={FileText} title="自动周复盘（Demo）" />
                <div className="text-sm leading-relaxed space-y-2">
                  <p>1) 本周计划定投总额：¥ {weeklyInvestSummary.reduce((s, d) => s + d.plan, 0)}</p>
                  <p>2) 本周实际定投总额：¥ {weeklyInvestSummary.reduce((s, d) => s + d.actual, 0)}</p>
                  <p>
                    3) 完成度：
                    <b>
                      {(() => {
                        const p = weeklyInvestSummary.reduce((s, d) => s + d.plan, 0);
                        const a = weeklyInvestSummary.reduce((s, d) => s + d.actual, 0);
                        return p > 0 ? ((a / p) * 100).toFixed(1) : "0.0";
                      })()}%
                    </b>
                  </p>
                  <p>4) 回撤修复中基金：{funds.filter((f) => f.ddRepairing).map((f) => f.name).join("、") || "无"}</p>
                  <p>
                    5) 资产结构：现金 {((allocationData.find((x) => x.name === "现金")?.value || 0) / totals.total * 100).toFixed(1)}%，
                    固收 {((allocationData.find((x) => x.name === "固收")?.value || 0) / totals.total * 100).toFixed(1)}%，
                    权益 {((allocationData.find((x) => x.name === "权益")?.value || 0) / totals.total * 100).toFixed(1)}%，
                    黄金 {((allocationData.find((x) => x.name === "黄金")?.value || 0) / totals.total * 100).toFixed(1)}%
                  </p>
                  <div className="p-3 bg-pink-50 border border-pink-200 rounded-xl">
                    <div className="font-semibold">本周一句话建议</div>
                    <div>保持定投节奏，权益若继续上升导致占比过高，则下周考虑提高固收。</div>
                  </div>
                </div>
              </Card>

              <Card className="p-4">
                <SectionTitle icon={FileText} title="复盘导出（Mock）" extra={<Chip tone="slate">Markdown/Obsidian</Chip>} />
                <textarea
                  className="w-full h-72 p-3 rounded-xl border border-pink-100 bg-white text-sm font-mono"
                  readOnly
                  value={reviewMarkdown}
                />
                <div className="text-xs text-slate-500 mt-2">
                  后续接入后端后，这里会一键下载为 YYYY-MM-DD_周复盘.md
                </div>
              </Card>
            </motion.div>
          )}
        </AnimatePresence>

        <div className="text-xs text-slate-400 mt-6">
          Demo 说明：当前为纯前端 mock，可交互。接后端接口后即可变为真实个人理财系统。
        </div>
      </div>
    </div>
  );
}
