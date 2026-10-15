/*!
 * 灌区用水三页面共用规则（《水资源调度系统_三页面设计方案》2.4 规则同源机制）
 * ---------------------------------------------------------------------------
 * - 本文件（前端纯函数 + rules_version）与同目录 rules.json（后端 WaterUsageRules 读取）成对维护，
 *   两处 rules_version 必须一致；仅改其一即为不同源。
 * - 后端各页面 meta 接口返回 rulesVersion；页面加载后调用 WaterRules.assertVersion(meta.rulesVersion)：
 *   不一致 → 页面顶部常驻黄条提示 + 控制台告警（多为浏览器缓存了旧版 rules.js，强制刷新即可）。
 * - 规则值口径：存储 m³（2 位小数）、界面万m³（1 位小数）；换算 ÷10000 只发生在展示/录入层。
 * - 与后端 Java 同公式：WaterUsageDict.periodOf / cycleKey / cycleRange、WaterUsageSupport.calcWater。
 */
(function (global) {
  'use strict';

  /** 规则版本号（必须与 lib/rules.json 的 rules_version 一致） */
  var RULES_VERSION = '2026.10.14-1';

  /** 规则值快照（与 rules.json 同步；页面可直接读取，禁止散落硬编码） */
  var RULES = {
    rules_version: RULES_VERSION,
    units: { wan_factor: 10000, store_scale: 2, display_scale: 1, area_scale: 2, price_scale: 3 },
    numbering: { user_prefix: 'YH', user_seq_digits: 3, plan_prefix: 'YJ', plan_seq_digits: 3 }
  };

  /** 计划类型（与后端 WaterUsageDict.PLAN_TYPE_* 一致） */
  var PLAN_TYPE_YEAR = '#1#';
  var PLAN_TYPE_MONTH = '#2#';
  var PLAN_TYPE_XUN = '#3#';

  /** 统计周期（与后端 WaterUsageDict.SETTLE_CYCLE_* 一致） */
  var SETTLE_CYCLE_MONTH = '#1#';
  var SETTLE_CYCLE_QUARTER = '#2#';
  var SETTLE_CYCLE_YEAR = '#3#';

  /** 数值保留 n 位（四舍五入；null/NaN 返回 null） */
  function roundTo(value, scale) {
    if (value === null || value === undefined || value === '' || isNaN(value)) {
      return null;
    }
    var factor = Math.pow(10, scale);
    return Math.round(Number(value) * factor) / factor;
  }

  /** 测算水量 = 灌溉面积(亩) × 定额标准(m³/亩)，m³ 保留 2 位（与后端 calcWater 同公式） */
  function calcWater(area, quotaValue) {
    if (area === null || area === undefined || area === '' || isNaN(area)
      || quotaValue === null || quotaValue === undefined || quotaValue === '' || isNaN(quotaValue)) {
      return null;
    }
    return roundTo(Number(area) * Number(quotaValue), RULES.units.store_scale);
  }

  /** 万m³ 录入值 → m³ 存储值（×10000，2 位；与后端 toM3 同口径） */
  function toM3(wan) {
    var v = roundTo(wan, 6);
    if (v === null) {
      return null;
    }
    return roundTo(v * RULES.units.wan_factor, RULES.units.store_scale);
  }

  /** m³ 存储值 → 万m³ 展示值（÷10000，默认 1 位；与后端 toWan 同口径） */
  function toWan(m3, scale) {
    var v = roundTo(m3, 6);
    if (v === null) {
      return null;
    }
    var s = (scale === undefined || scale === null) ? RULES.units.display_scale : scale;
    return roundTo(v / RULES.units.wan_factor, s);
  }

  /** 万m³ 展示文案（1 位小数定长；null → '—'） */
  function fmtWan(m3) {
    var wan = toWan(m3);
    return wan === null ? '—' : wan.toFixed(RULES.units.display_scale);
  }

  /** 当月天数（与后端 YearMonth.atEndOfMonth 一致） */
  function daysInMonth(year, month) {
    return new Date(year, month, 0).getDate();
  }

  /** 两位补零 */
  function pad2(n) {
    return (n < 10 ? '0' : '') + n;
  }

  /** 日期文本（YYYY-MM-DD，本地时区） */
  function dateText(date) {
    return date.getFullYear() + '-' + pad2(date.getMonth() + 1) + '-' + pad2(date.getDate());
  }

  /** 旬名（1 上旬 / 2 中旬 / 3 下旬；非法返回空串） */
  function xunName(xun) {
    if (xun === 1) { return '上'; }
    if (xun === 2) { return '中'; }
    if (xun === 3) { return '下'; }
    return '';
  }

  /**
   * 按计划类型推导周期起止（与后端 WaterUsageDict.periodOf 同公式）：
   * 年度=自然年；月度=自然月；旬度=上旬 1-10 / 中旬 11-20 / 下旬 21-自然月末。
   * 返回 { start: 'YYYY-MM-DD', end: 'YYYY-MM-DD' }；参数非法返回 null。
   */
  function periodOf(planType, year, month, xun) {
    if (!planType || year === null || year === undefined || year < 2000 || year > 2100) {
      return null;
    }
    if (planType === PLAN_TYPE_YEAR) {
      return { start: year + '-01-01', end: year + '-12-31' };
    }
    if (month === null || month === undefined || month < 1 || month > 12) {
      return null;
    }
    if (planType === PLAN_TYPE_MONTH) {
      return { start: year + '-' + pad2(month) + '-01', end: year + '-' + pad2(month) + '-' + pad2(daysInMonth(year, month)) };
    }
    if (planType === PLAN_TYPE_XUN && (xun === 1 || xun === 2 || xun === 3)) {
      var endDay = xun === 1 ? 10 : (xun === 2 ? 20 : daysInMonth(year, month));
      return { start: year + '-' + pad2(month) + '-' + pad2(xun === 1 ? 1 : (xun === 2 ? 11 : 21)), end: year + '-' + pad2(month) + '-' + pad2(endDay) };
    }
    return null;
  }

  /**
   * 统计周期 → 当前周期信息（用水户表单实时展示；与后端 currentCycleKey / cycleRange / cycleLabel 同公式）：
   * 月度 → { key:'2026-10', label:'2026 年 10 月', start:'2026-10-01', end:'2026-10-31' }
   * 季度 → { key:'2026-Q4', label:'2026 年第 4 季度', ... }；年度 → { key:'2026', label:'2026 年度', ... }
   * 周期缺失/非法返回 null；date 缺省取当天。
   */
  function currentCycle(settleCycle, date) {
    if (!settleCycle) {
      return null;
    }
    var d = (date === null || date === undefined) ? new Date() : new Date(date);
    if (isNaN(d.getTime())) {
      d = new Date();
    }
    var year = d.getFullYear();
    var month = d.getMonth() + 1;
    if (settleCycle === SETTLE_CYCLE_MONTH) {
      return {
        key: year + '-' + pad2(month),
        label: year + ' 年 ' + month + ' 月',
        start: year + '-' + pad2(month) + '-01',
        end: year + '-' + pad2(month) + '-' + pad2(daysInMonth(year, month))
      };
    }
    if (settleCycle === SETTLE_CYCLE_QUARTER) {
      var quarter = Math.floor((month - 1) / 3) + 1;
      var startMonth = quarter * 3 - 2;
      var endMonth = quarter * 3;
      return {
        key: year + '-Q' + quarter,
        label: year + ' 年第 ' + quarter + ' 季度',
        start: year + '-' + pad2(startMonth) + '-01',
        end: year + '-' + pad2(endMonth) + '-' + pad2(daysInMonth(year, endMonth))
      };
    }
    if (settleCycle === SETTLE_CYCLE_YEAR) {
      return { key: String(year), label: year + ' 年度', start: year + '-01-01', end: year + '-12-31' };
    }
    return null;
  }

  /** 同类型周期重叠判定（生效校验第二层口径）：仅同类型参与，区间相交即重叠 */
  function sameTypeOverlap(typeA, startA, endA, typeB, startB, endB) {
    if (!typeA || !typeB || typeA !== typeB) {
      return false;
    }
    if (!startA || !endA || !startB || !endB) {
      return false;
    }
    return startA <= endB && endA >= startB;
  }

  /**
   * 差额策略读数校验（设计方案 3.1；后端保存时强制）：
   * 主闸口读数 − 扣减闸口读数合计 ≤ 0 → false（禁止保存）；读数缺失（null）→ null（无法判定，跳过）。
   */
  function diffReadingOk(mainReading, deductSum) {
    if (mainReading === null || mainReading === undefined || mainReading === ''
      || deductSum === null || deductSum === undefined || deductSum === '') {
      return null;
    }
    return (Number(mainReading) - Number(deductSum)) > 0;
  }

  /** 顶部常驻黄条（版本不一致提示；已存在则不重复插入） */
  function showBanner(text) {
    if (typeof document === 'undefined') {
      return;
    }
    var id = 'water-rules-version-banner';
    if (document.getElementById(id)) {
      return;
    }
    var bar = document.createElement('div');
    bar.id = id;
    bar.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:9999;background:#fffbe6;'
      + 'border-bottom:1px solid #ffe58f;color:#ad6800;font-size:13px;line-height:20px;'
      + 'padding:8px 16px;text-align:center;font-family:inherit;';
    bar.textContent = text;
    (document.body || document.documentElement).appendChild(bar);
  }

  /**
   * 版本比对（页面 meta 加载后调用）：
   * serverVersion 为空（后端未就绪）→ 跳过并告警；不一致 → 黄条常驻 + 控制台告警；一致 → true。
   */
  function assertVersion(serverVersion) {
    if (!serverVersion) {
      if (global.console) {
        console.warn('[水规则] 未获取到后端规则版本（meta.rulesVersion 为空），跳过版本比对');
      }
      return false;
    }
    if (serverVersion === RULES_VERSION) {
      return true;
    }
    if (global.console) {
      console.warn('[水规则] 版本不一致：页面 rules.js = ' + RULES_VERSION + '，后端 rules.json = ' + serverVersion
        + '；请强制刷新（Ctrl+F5）加载最新规则文件');
    }
    showBanner('规则文件版本不一致（前端 ' + RULES_VERSION + ' / 后端 ' + serverVersion
      + '），页面部分校验可能过期，请强制刷新（Ctrl+F5）');
    return false;
  }

  global.WaterRules = {
    version: RULES_VERSION,
    rules: RULES,
    roundTo: roundTo,
    calcWater: calcWater,
    toM3: toM3,
    toWan: toWan,
    fmtWan: fmtWan,
    periodOf: periodOf,
    xunName: xunName,
    currentCycle: currentCycle,
    sameTypeOverlap: sameTypeOverlap,
    diffReadingOk: diffReadingOk,
    assertVersion: assertVersion,
    showBanner: showBanner
  };
})(window);
