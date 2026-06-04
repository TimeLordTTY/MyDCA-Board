-- Phase2 BOLL / KDJ 指标字段扩展
-- 仅扩展 indicator_daily 的指标列，不修改唯一键和历史数据。

ALTER TABLE `indicator_daily`
  ADD COLUMN `boll_middle` DECIMAL(18, 6) NULL COMMENT 'BOLL中轨' AFTER `ma60`,
  ADD COLUMN `boll_upper` DECIMAL(18, 6) NULL COMMENT 'BOLL上轨' AFTER `boll_middle`,
  ADD COLUMN `boll_lower` DECIMAL(18, 6) NULL COMMENT 'BOLL下轨' AFTER `boll_upper`,
  ADD COLUMN `boll_std` DECIMAL(18, 6) NULL COMMENT 'BOLL标准差' AFTER `boll_lower`,
  ADD COLUMN `boll_window` INT NULL COMMENT 'BOLL窗口天数' AFTER `boll_std`,
  ADD COLUMN `kdj_k` DECIMAL(18, 6) NULL COMMENT 'KDJ K值' AFTER `boll_window`,
  ADD COLUMN `kdj_d` DECIMAL(18, 6) NULL COMMENT 'KDJ D值' AFTER `kdj_k`,
  ADD COLUMN `kdj_j` DECIMAL(18, 6) NULL COMMENT 'KDJ J值' AFTER `kdj_d`,
  ADD COLUMN `kdj_rsv` DECIMAL(18, 6) NULL COMMENT 'KDJ RSV值' AFTER `kdj_j`,
  ADD COLUMN `kdj_window` INT NULL COMMENT 'KDJ窗口天数' AFTER `kdj_rsv`;
