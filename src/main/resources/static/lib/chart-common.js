/**
 * 图表公共能力（依赖 echarts 已加载）：
 * 1) x 轴真实时间：category 轴的时间标签自动转 time 轴，series.data 同步转 [时间戳, 值]
 * 2) 导出图片：统一注入 toolbox.saveAsImage（每张图右上角工具条）
 *
 * 用法：
 *   - 引入本文件即可：自动接管 echarts.init，之后所有 setOption 都会走 enhance
 *   - 也可手动：chart.setOption(ChartCommon.enhance(option, '图表名称'), true)
 *
 * 说明：
 * - 只有 category 轴且 data 全部可解析为时间的才转换（站点名、月份、旬等分类轴保持原样）
 * - 多 grid / 多 xAxis 按 series.xAxisIndex 对应转换
 * - 转换后自动包装 tooltip.formatter / valueFormatter / series.label.formatter，
 *   让页面里按标量取 p.value 的老代码继续可用
 * - 无年份标签（MM-dd HH:mm）按当年补齐；升序数据自动识别跨年/跨日，
 *   倒序数据不做推断（ECharts time 轴会按真实时间重排）
 * - 页面自定义 x 轴 axisLabel.formatter 会自动还原为原始类目标签
 */
(function (global) {
  var CN = {};

  /** 时间标签 → 毫秒时间戳；解析失败返回 NaN。兼容：
   *  yyyy-MM-dd[ HH:mm[:ss]] / yyyy/MM/dd[ HH:mm[:ss]] / yyyy-MM-ddTHH:mm:ss
   *  MM-dd[ HH:mm] / MM/dd[ HH:mm] / HH:mm[:ss] / 10|13 位时间戳 */
  function toTs(label) {
    if (label == null) return NaN;
    // 数值只认 10 位秒级 / 13 位毫秒级时间戳；年份、月份、序号等数字保持分类轴，
    // 否则会被当成 1970 年毫秒值，把整张图挤在时间轴起点
    if (typeof label === 'number') {
      if (label >= 1e12 && label < 1e14) return label;
      if (label >= 1e9 && label < 1e10) return label * 1000;
      return NaN;
    }
    var s = String(label).trim();
    if (!s) return NaN;
    if (/^\d{13}$/.test(s)) return +s;
    if (/^\d{10}$/.test(s)) return +s * 1000;
    // 纯数字短串（序号/月份/年份，如 "1"、"01"、"2026"）不当作时间，
    // 否则 Date.parse 会把 "1" 误解析成 2001-01-01，分类轴被错误转成时间轴
    if (/^\d{1,6}$/.test(s)) return NaN;
    var m = s.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?:[T ](\d{1,2}):(\d{2})(?::(\d{2}))?)?/);
    if (m) {
      return new Date(+m[1], +m[2] - 1, +m[3], +(m[4] || 0), +(m[5] || 0), +(m[6] || 0)).getTime();
    }
    var now = new Date();
    m = s.match(/^(\d{1,2})[-/](\d{1,2})(?:[T ](\d{1,2}):(\d{2}))?/);
    if (m) {
      return new Date(now.getFullYear(), +m[1] - 1, +m[2], +(m[3] || 0), +(m[4] || 0)).getTime();
    }
    m = s.match(/^(\d{1,2}):(\d{2})(?::(\d{2}))?$/);
    if (m) {
      return new Date(now.getFullYear(), now.getMonth(), now.getDate(), +m[1], +m[2], +(m[3] || 0)).getTime();
    }
    // 兜底解析只接受带时间分隔符的字符串，避免 "July 22" 之类被误当初时间
    if (!/[-/:]/.test(s)) return NaN;
    var t = Date.parse(s.replace(' ', 'T'));
    return isNaN(t) ? NaN : t;
  }

  /** 无年份（MM-dd / MM/dd）与纯时刻（HH:mm）标签 */
  var NO_YEAR_RE = /^\s*(\d{1,2})[-/](\d{1,2})(?![-/]\d)/;
  var TIME_ONLY_RE = /^\s*(\d{1,2}):\d{2}/;

  function bumpYear(ts) {
    var d = new Date(ts);
    d.setFullYear(d.getFullYear() + 1);
    return d.getTime();
  }

  /**
   * 全量解析类目标签为时间戳；任一标签无法解析返回 null（该轴保持 category）。
   * 无年份标签按当前年补齐，并对升序数据做跨年/跨日修正：
   *  - MM-dd 月份回退超过 6 个月 → 视为跨年（+1 年）
   *  - HH:mm 小时回退超过 12 小时 → 视为跨日（+1 天）
   * 倒序数据（最新在前）不做推断，避免把同年的 1 月误判成次年。
   */
  function resolveTimeList(labels) {
    var n = labels.length;
    var raw = new Array(n);
    for (var i = 0; i < n; i++) {
      raw[i] = toTs(labels[i]);
      if (isNaN(raw[i])) return null;
    }
    var out = raw.slice();
    if (n < 2) return out;
    var desc = 0;
    for (i = 1; i < n; i++) {
      if (raw[i] < raw[i - 1]) desc++;
    }
    if (desc > (n - 1) / 2) return out; // 整体倒序：不做跨年推断
    var prevMon = null, prevHour = null;
    for (i = 0; i < n; i++) {
      var s = String(labels[i] == null ? '' : labels[i]);
      var mNoYear = s.match(NO_YEAR_RE);
      var mTime = TIME_ONLY_RE.test(s) ? s.match(TIME_ONLY_RE) : null;
      if (i > 0) {
        if (mNoYear) {
          var mon = +mNoYear[1];
          if (prevMon != null && mon < prevMon - 6) out[i] = bumpYear(out[i]);
        } else if (mTime) {
          var hh = +mTime[1];
          if (prevHour != null && hh < prevHour - 12) out[i] += 86400000;
        }
      }
      prevMon = mNoYear ? +mNoYear[1] : null;
      prevHour = mTime ? +mTime[1] : null;
    }
    return out;
  }

  /** category 轴的 data 是否全部为时间标签 */
  function isTimeLabels(data) {
    if (!Array.isArray(data) || !data.length) return false;
    return resolveTimeList(data) !== null;
  }

  /** 包装 x 轴 axisLabel.formatter：time 轴刻度值是时间戳，尽量还原为原始类目标签 */
  function wrapAxisLabelFormatter(fn, labels, tsList) {
    if (typeof fn !== 'function' || fn.__ccAxisWrap) return fn;
    var map = {};
    for (var i = 0; i < tsList.length; i++) map[tsList[i]] = labels[i];
    var wrapped = function (value, index) {
      var v = value;
      if (typeof v === 'number' && map[v] != null) v = map[v];
      return fn.call(this, v, index);
    };
    wrapped.__ccAxisWrap = wrapped;
    return wrapped;
  }

  /** 单个 series 的数据项转 [时间戳, 值]；保留对象项（含 itemStyle 等）的其它字段 */
  function toTimePair(v, ts) {
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      var o = {};
      for (var k in v) {
        if (Object.prototype.hasOwnProperty.call(v, k)) o[k] = v[k];
      }
      if (Array.isArray(o.value) && o.value.length >= 2) {
        // 已是 [x, y] 形式：把 x 换成时间戳
        o.value = [ts, o.value[1]];
      } else if (o.value !== undefined) {
        o.value = [ts, o.value];
      }
      return o;
    }
    return [ts, v];
  }

  /** 把某个 category 时间轴及其系列转为 time 轴；未转换返回 null，成功返回时间戳数组 */
  function convertAxis(option, axis, axisIdx) {
    var labels = axis.data;
    if (!Array.isArray(labels) || !labels.length) return null;
    var tsList = resolveTimeList(labels);
    if (!tsList) return null;
    axis.type = 'time';
    delete axis.data;
    // time 轴不支持 category 的间隔/边界配置
    if (axis.axisLabel && axis.axisLabel.interval !== undefined) delete axis.axisLabel.interval;
    if (axis.boundaryGap === true) axis.boundaryGap = false;
    if (axis.axisLabel && typeof axis.axisLabel.formatter === 'function') {
      axis.axisLabel.formatter = wrapAxisLabelFormatter(axis.axisLabel.formatter, labels, tsList);
    }

    var series = option.series || [];
    for (var i = 0; i < series.length; i++) {
      var s = series[i];
      if (!s || !Array.isArray(s.data)) continue;
      if ((s.xAxisIndex || 0) !== axisIdx) continue;
      // 点数与类目数不一致时按较短的转换，避免产出 [undefined, 值] 这类坏点
      var len = Math.min(s.data.length, tsList.length);
      if (s.data.length !== tsList.length && global.console) {
        console.warn('[ChartCommon] series 数据点数(' + s.data.length + ')与类目数(' + tsList.length + ')不一致，已按较短的对齐');
      }
      var out = new Array(len);
      for (var j = 0; j < len; j++) out[j] = toTimePair(s.data[j], tsList[j]);
      s.data = out;
      s.__ccTime = true;
    }
    return tsList;
  }

  /** tooltip param 归一化：时间轴下 value 是 [ts, y]，axisValue 是时间戳，还原成老代码期望的标量/文案 */
  function normalizeParam(p) {
    if (!p || typeof p !== 'object') return;
    if (Array.isArray(p.value)) {
      p.value = p.value.length >= 2 ? p.value[p.value.length - 1] : p.value[0];
    }
    if (typeof p.axisValue === 'number' && p.axisValueLabel != null) {
      p.axisValue = p.axisValueLabel;
    }
    // time 轴下类目名 name 恒为空串，而老代码常把 name 当作时间标签用
    if ((p.name === '' || p.name == null) && p.axisValueLabel != null) {
      p.name = p.axisValueLabel;
    }
  }

  function wrapTooltipFormatter(fn) {
    if (typeof fn !== 'function') return fn;
    if (fn.__ccWrap) return fn.__ccWrap;
    var wrapped = function (params) {
      var arr = Array.isArray(params) ? params : [params];
      for (var i = 0; i < arr.length; i++) normalizeParam(arr[i]);
      return fn.apply(this, arguments);
    };
    fn.__ccWrap = wrapped;
    return wrapped;
  }

  function wrapValueFormatter(fn) {
    if (typeof fn !== 'function') return fn;
    if (fn.__ccWrap) return fn.__ccWrap;
    var wrapped = function (v, idx) {
      if (Array.isArray(v)) v = v.length >= 2 ? v[v.length - 1] : v[0];
      return fn(v, idx);
    };
    fn.__ccWrap = wrapped;
    return wrapped;
  }

  /** 转换后包装 tooltip / series label 的自定义格式化函数 */
  function normalizeFormatters(option) {
    var tooltip = option.tooltip;
    if (tooltip && typeof tooltip === 'object' && !Array.isArray(tooltip)) {
      if (typeof tooltip.formatter === 'function') {
        tooltip.formatter = wrapTooltipFormatter(tooltip.formatter);
      }
      if (typeof tooltip.valueFormatter === 'function') {
        tooltip.valueFormatter = wrapValueFormatter(tooltip.valueFormatter);
      }
    }
    var series = option.series || [];
    for (var i = 0; i < series.length; i++) {
      var s = series[i];
      if (!s || !s.__ccTime) continue;
      if (s.label && typeof s.label.formatter === 'function') {
        s.label.formatter = wrapTooltipFormatter(s.label.formatter);
      }
      if (s.tooltip && typeof s.tooltip === 'object' && !Array.isArray(s.tooltip)) {
        if (typeof s.tooltip.formatter === 'function') {
          s.tooltip.formatter = wrapTooltipFormatter(s.tooltip.formatter);
        }
        if (typeof s.tooltip.valueFormatter === 'function') {
          s.tooltip.valueFormatter = wrapValueFormatter(s.tooltip.valueFormatter);
        }
      }
    }
  }

  /** 注入保存图片工具（兼容已有的 toolbox 数组/隐藏配置，强制开启导出图片） */
  function ensureExport(option, title) {
    var name = title;
    if (!name) {
      var t = option.title;
      if (Array.isArray(t)) t = t[0];
      name = (t && t.text) || '图表';
    }
    var tb = option.toolbox;
    if (Array.isArray(tb)) tb = tb[0];
    if (!tb || typeof tb !== 'object') tb = {};
    option.toolbox = tb;
    tb.show = true;
    if (tb.right === undefined) tb.right = 8;
    if (tb.top === undefined) tb.top = 0;
    tb.feature = tb.feature || {};
    var sa = tb.feature.saveAsImage;
    if (!sa || typeof sa !== 'object') sa = {};
    sa.show = true;
    if (sa.title === undefined) sa.title = '下载图片';
    if (!sa.name) sa.name = String(name);
    if (sa.pixelRatio === undefined) sa.pixelRatio = 2;
    if (sa.backgroundColor === undefined) sa.backgroundColor = '#fff';
    tb.feature.saveAsImage = sa;
  }

  /**
   * 统一增强：x 轴转真实时间 + 注入导出图片。
   * @param option ECharts option（就地修改）
   * @param title  导出图片文件名（可选，默认取标题）
   */
  CN.enhance = function (option, title) {
    if (!option || typeof option !== 'object') return option;
    var converted = false;
    var axes = option.xAxis;
    if (Array.isArray(axes)) {
      for (var i = 0; i < axes.length; i++) {
        if (axes[i] && axes[i].type === 'category' && convertAxis(option, axes[i], i)) {
          converted = true;
        }
      }
    } else if (axes && axes.type === 'category' && convertAxis(option, axes, 0)) {
      converted = true;
    }
    if (converted) normalizeFormatters(option);
    ensureExport(option, title);
    return option;
  };

  /** 时间标签解析（供页面自定义时间轴时复用） */
  CN.toTs = toTs;
  CN.isTimeLabels = isTimeLabels;

  /**
   * 自动接管 echarts.init：页面引入本文件后无需改图表代码，
   * 所有 setOption 都会自动走 enhance（真实时间轴 + 导出图片）。
   * 兼容分步更新：先 setOption 全量、再只更新 series.data 时，按上次时间轴补 [ts, value]。
   */
  function applyToInstance(inst, option) {
    if (!option || typeof option !== 'object') return;
    var opts = Array.isArray(option) ? option : [option];
    for (var k = 0; k < opts.length; k++) {
      var o = opts[k];
      if (!o || typeof o !== 'object') continue;

      var axes = o.xAxis;
      var converted = false;
      if (Array.isArray(axes)) {
        for (var i = 0; i < axes.length; i++) {
          if (!axes[i] || axes[i].type !== 'category') continue;
          var axisTs = convertAxis(o, axes[i], i);
          if (axisTs) { inst.__ccAxes[i] = axisTs; converted = true; }
        }
      } else if (axes && axes.type === 'category') {
        var axisTs0 = convertAxis(o, axes, 0);
        if (axisTs0) { inst.__ccAxes[0] = axisTs0; converted = true; }
      }

      // 分步更新：只传 series.data（或 xAxis 仅带样式补丁）时，按上次时间轴补 [ts, value]
      var hasOwnCategory = false;
      if (Array.isArray(axes)) {
        for (var a = 0; a < axes.length; a++) {
          if (axes[a] && (axes[a].type === 'category' || Array.isArray(axes[a].data))) hasOwnCategory = true;
        }
      } else if (axes) {
        hasOwnCategory = axes.type === 'category' || Array.isArray(axes.data);
      }
      if (!converted && !hasOwnCategory && Array.isArray(o.series) && inst.__ccAxes) {
        for (var j = 0; j < o.series.length; j++) {
          var s = o.series[j];
          if (!s || !Array.isArray(s.data)) continue;
          var tsList = inst.__ccAxes[s.xAxisIndex || 0];
          if (!tsList || tsList.length !== s.data.length) continue;
          var out = new Array(s.data.length);
          for (var m = 0; m < s.data.length; m++) out[m] = toTimePair(s.data[m], tsList[m]);
          s.data = out;
          s.__ccTime = true;
          converted = true;
        }
      }

      if (converted) normalizeFormatters(o);
      ensureExport(o);
    }
  }

  function hookEcharts() {
    var ec = global.echarts;
    if (!ec || !ec.init) {
      if (global.console) console.warn('[ChartCommon] 未检测到 echarts，请把 chart-common.js 放在 echarts 之后加载');
      return false;
    }
    if (ec.__chartCommonHooked) return true;
    var origInit = ec.init;
    ec.init = function () {
      var inst = origInit.apply(this, arguments);
      if (inst && !inst.__ccWrapped) {
        inst.__ccWrapped = true;
        inst.__ccAxes = [];
        var origSet = inst.setOption;
        inst.setOption = function (option) {
          applyToInstance(this, option);
          return origSet.apply(this, arguments);
        };
        // clear() 后时间轴记忆失效，避免分步更新套用上一张图的时间
        var origClear = inst.clear;
        if (typeof origClear === 'function') {
          inst.clear = function () {
            this.__ccAxes = [];
            return origClear.apply(this, arguments);
          };
        }
      }
      return inst;
    };
    ec.__chartCommonHooked = true;
    return true;
  }

  if (!hookEcharts() && global.addEventListener) {
    // 脚本顺序异常时兜底：DOM 与其它同步脚本执行完再尝试一次
    global.addEventListener('load', hookEcharts);
  }

  global.ChartCommon = CN;
})(typeof window !== 'undefined' ? window : this);
