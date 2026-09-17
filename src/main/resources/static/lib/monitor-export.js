/**
 * 监测页通用导出：字段勾选弹窗 + Excel（xlsx.full.min.js）生成
 * 依赖：页面需先引入 ./lib/xlsx.full.min.js
 *
 * 用法一（详情导出：弹窗勾选字段，勾哪个表头就带哪个）：
 *   MonitorExport.open({ // 详情导出
 *     title: '导出闸门监测数据',
 *     tip: '勾选需要的字段，导出表头只包含勾选字段',
 *     fields: [{ key: 'tm', label: '监测时间' }, { key: 'upZ', label: '闸前水位 (m)' }],
 *     fetchRows: async (checked) => [{ tm: '2026-09-17 08:00', upZ: 23.45 }],  // 全量数据（内部自行分页），入参为已勾选字段
 *     filename: () => '闸门监测_渠首进水闸_2026-09-01~2026-09-17',       // 扩展名可省略
 *     sheetName: '闸门监测',
 *   });
 *
 * 用法二（列表导出：不弹窗，直接导出全部字段）：
 *   MonitorExport.download({ fields, rows, filename: '流量监测', sheetName: '流量监测' });
 *
 * 用法三（动态列表头导出：日雨情等透视表，aoa[0] 为表头）：
 *   MonitorExport.downloadAoa({ aoa, filename: '灌区日雨情', sheetName: '日雨情' });
 *
 * 取值口径：null/undefined/'' → 空白单元格；-9991（设备异常）→ '--'；-999（设备不存在）→ 空白；
 * 纯数字长码（站点编号等）按文本写入，避免 Excel 显示成科学计数法。
 */
(function () {
  'use strict';

  var OVERLAY_ID = 'me-overlay';
  var CSV_SAFE_CODE = /^\d{6,}$/; // 长数字串（站点编号等）：强制文本，避免科学计数法
  var NUM_LIKE = /^-?\d+(\.\d+)?$/; // 「看起来像数字」的文本：转数值，避免 Excel 串求和为 0

  var CSS = ''
    + '#' + OVERLAY_ID + '{display:none;position:fixed;inset:0;background:rgba(0,0,0,.45);z-index:3200;align-items:center;justify-content:center;padding:20px}'
    + '#' + OVERLAY_ID + '.show{display:flex}'
    + '#' + OVERLAY_ID + ' .me-modal{width:560px;max-width:96vw;max-height:88vh;background:#fff;border-radius:10px;display:flex;flex-direction:column;box-shadow:0 8px 32px rgba(0,0,0,.18)}'
    + '#' + OVERLAY_ID + ' .me-hd{display:flex;align-items:center;justify-content:space-between;padding:14px 18px;border-bottom:1px solid #f0f0f0}'
    + '#' + OVERLAY_ID + ' .me-hd span{font-size:15px;font-weight:600;color:#1f1f1f}'
    + '#' + OVERLAY_ID + ' .me-close{border:0;background:transparent;color:#999;font-size:20px;line-height:1;cursor:pointer;padding:0 4px}'
    + '#' + OVERLAY_ID + ' .me-close:hover{color:#262626}'
    + '#' + OVERLAY_ID + ' .me-bd{padding:14px 18px;overflow:auto;flex:1}'
    + '#' + OVERLAY_ID + ' .me-tip{color:#999;font-size:12px;margin-bottom:10px}'
    + '#' + OVERLAY_ID + ' .me-row{display:flex;align-items:center;gap:16px;margin-bottom:10px}'
    + '#' + OVERLAY_ID + ' .me-link{color:#1677ff;background:none;border:0;padding:0;font-size:12px;cursor:pointer}'
    + '#' + OVERLAY_ID + ' .me-count{color:#bbb;font-size:12px;margin-left:auto}'
    + '#' + OVERLAY_ID + ' .me-list{display:grid;grid-template-columns:1fr 1fr;gap:8px 12px}'
    + '#' + OVERLAY_ID + ' .me-item{display:flex;align-items:center;gap:6px;font-size:13px;color:#444;cursor:pointer;user-select:none}'
    + '#' + OVERLAY_ID + ' .me-ft{display:flex;align-items:center;justify-content:flex-end;gap:10px;padding:12px 18px;border-top:1px solid #f0f0f0}'
    + '#' + OVERLAY_ID + ' .me-status{font-size:12px;color:#999;margin-right:auto}'
    + '#' + OVERLAY_ID + ' .me-btn{border:1px solid #d9d9d9;background:#fff;color:#333;border-radius:6px;padding:7px 18px;font-size:13px;cursor:pointer}'
    + '#' + OVERLAY_ID + ' .me-btn:hover{color:#1677ff;border-color:#1677ff}'
    + '#' + OVERLAY_ID + ' .me-btn.primary{background:#1677ff;border-color:#1677ff;color:#fff}'
    + '#' + OVERLAY_ID + ' .me-btn.primary:hover{background:#4096ff;border-color:#4096ff;color:#fff}'
    + '#' + OVERLAY_ID + ' .me-btn:disabled{opacity:.6;cursor:not-allowed}';

  var dom = null;
  var state = { fields: [], checked: {}, cfg: null };

  function injectStyle() {
    if (document.getElementById(OVERLAY_ID + '-style')) return;
    var style = document.createElement('style');
    style.id = OVERLAY_ID + '-style';
    style.textContent = CSS;
    document.head.appendChild(style);
  }

  function buildDom() {
    if (dom) return;
    var wrap = document.createElement('div');
    wrap.id = OVERLAY_ID;
    wrap.innerHTML = ''
      + '<div class="me-modal">'
      + '  <div class="me-hd"><span id="me-title">导出</span><button type="button" class="me-close">&times;</button></div>'
      + '  <div class="me-bd">'
      + '    <p class="me-tip" id="me-tip"></p>'
      + '    <div class="me-row">'
      + '      <button type="button" class="me-link" data-me="all">全选</button>'
      + '      <button type="button" class="me-link" data-me="none">清空</button>'
      + '      <span class="me-count" id="me-count"></span>'
      + '    </div>'
      + '    <div class="me-list" id="me-list"></div>'
      + '  </div>'
      + '  <div class="me-ft">'
      + '    <span class="me-status" id="me-status"></span>'
      + '    <button type="button" class="me-btn" data-me="cancel">取消</button>'
      + '    <button type="button" class="me-btn primary" data-me="ok">导出</button>'
      + '  </div>'
      + '</div>';
    document.body.appendChild(wrap);

    dom = {
      wrap: wrap,
      title: wrap.querySelector('#me-title'),
      tip: wrap.querySelector('#me-tip'),
      list: wrap.querySelector('#me-list'),
      count: wrap.querySelector('#me-count'),
      status: wrap.querySelector('#me-status'),
      ok: wrap.querySelector('[data-me="ok"]'),
      cancel: wrap.querySelector('[data-me="cancel"]'),
      close: wrap.querySelector('.me-close'),
    };

    dom.list.addEventListener('change', function (e) {
      var input = e.target.closest('input[data-me-key]');
      if (!input) return;
      state.checked[input.dataset.meKey] = input.checked;
      renderCount();
    });
    dom.wrap.addEventListener('click', function (e) {
      var act = e.target.closest('[data-me]');
      if (act) {
        var kind = act.dataset.me;
        if (kind === 'all' || kind === 'none') {
          state.fields.forEach(function (f) { state.checked[f.key] = (kind === 'all'); });
          dom.list.querySelectorAll('input[data-me-key]').forEach(function (el) { el.checked = (kind === 'all'); });
          renderCount();
          return;
        }
        if (kind === 'cancel') { close(); return; }
        if (kind === 'ok') { doExport(); return; }
      }
      if (e.target === dom.wrap) close();
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && dom.wrap.classList.contains('show')) close();
    });
  }

  function renderCount() {
    var n = state.fields.filter(function (f) { return state.checked[f.key]; }).length;
    dom.count.textContent = '已选 ' + n + ' / ' + state.fields.length + ' 项';
  }

  function renderList() {
    dom.list.innerHTML = state.fields.map(function (f) {
      return '<label class="me-item"><input type="checkbox" data-me-key="' + esc(f.key) + '"'
        + (state.checked[f.key] ? ' checked' : '') + '>' + esc(f.label) + '</label>';
    }).join('');
    renderCount();
  }

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function setStatus(text) { dom.status.textContent = text || ''; }

  function setBusy(busy, text) {
    dom.ok.disabled = busy;
    dom.cancel.disabled = busy;
    dom.ok.textContent = busy ? '导出中…' : '导出';
    if (text !== undefined) setStatus(text);
  }

  function close() {
    if (!dom) return;
    dom.wrap.classList.remove('show');
    setBusy(false, '');
  }

  function open(cfg) {
    if (!window.XLSX) { window.alert('导出组件未加载，请刷新页面后重试'); return; }
    injectStyle();
    buildDom();

    var fields = (cfg.fields || []).filter(function (f) { return f && f.key && f.label; });
    if (!fields.length) { window.alert('没有可导出的字段'); return; }

    state.cfg = cfg;
    state.fields = fields;
    state.checked = {};
    fields.forEach(function (f) { state.checked[f.key] = (f.checked !== false); });

    dom.title.textContent = cfg.title || '导出数据';
    dom.tip.textContent = cfg.tip || '勾选需要导出的字段，导出表头只包含勾选字段';
    renderList();
    dom.wrap.classList.add('show');
  }

  /** 单元格取值：异常/缺失哨兵值统一转换，长数字码按文本输出 */
  function cellValue(v) {
    if (v === null || v === undefined) return '';
    if (typeof v === 'number') {
      if (v === -9991) return '--';
      if (v === -999) return '';
      if (Number.isInteger(v) && CSV_SAFE_CODE.test(String(Math.abs(v)))) return String(v);
      return v;
    }
    var s = String(v);
    if (s === '') return '';
    if (s === '-9991') return '--';
    if (s === '-999') return '';
    return s;
  }

  function buildBook(fields, rows, sheetName) {
    var aoa = [fields.map(function (f) { return f.label; })];
    rows.forEach(function (r) {
      aoa.push(fields.map(function (f) { return cellValue(r ? r[f.key] : ''); }));
    });
    var ws = window.XLSX.utils.aoa_to_sheet(aoa);
    ws['!cols'] = fields.map(function (f) {
      return { wch: Math.min(30, Math.max(10, String(f.label).replace(/[^\x00-\xff]/g, 'aa').length + 4)) };
    });
    var wb = window.XLSX.utils.book_new();
    window.XLSX.utils.book_append_sheet(wb, ws, String(sheetName || '数据').slice(0, 30));
    return wb;
  }

  function resolveFilename(cfg) {
    var name = (typeof cfg.filename === 'function') ? cfg.filename() : cfg.filename;
    name = String(name || '导出数据').replace(/[\\/:*?"<>|]/g, '_');
    return /\.xlsx$/i.test(name) ? name : name + '.xlsx';
  }

  function writeFile(fields, rows, cfg) {
    var wb = buildBook(fields, rows, cfg.sheetName);
    window.XLSX.writeFile(wb, resolveFilename(cfg));
  }

  /** 列表导出：不弹窗，直接按 fields 全量导出 */
  function download(cfg) {
    var fields = (cfg.fields || []).filter(function (f) { return f && f.key && f.label; });
    var rows = cfg.rows || [];
    if (!fields.length) { window.alert('没有可导出的字段'); return; }
    if (!rows.length) { window.alert('暂无数据可导出'); return; }
    if (!window.XLSX) { window.alert('导出组件未加载，请刷新页面后重试'); return; }
    writeFile(fields, rows, cfg);
  }

  /** 文本型数值转 number（长数字码保持文本）；非文本原样返回 */
  function toNumLike(v) {
    if (typeof v !== 'string') return v;
    var s = v.trim();
    if (s === '' || s === '-' || s === '--') return s;
    if (!NUM_LIKE.test(s) || CSV_SAFE_CODE.test(s)) return s;
    return Number(s);
  }

  /** 动态列表头导出（透视表等 aoa 数据）：aoa[0] 为表头，其余为数据行 */
  function downloadAoa(cfg) {
    var aoa = Array.isArray(cfg.aoa) ? cfg.aoa : [];
    if (!aoa.length || aoa.length <= 1) { window.alert('暂无数据可导出'); return; }
    if (!window.XLSX) { window.alert('导出组件未加载，请刷新页面后重试'); return; }
    var head = aoa[0] || [];
    var fields = head.map(function (h, i) { return { key: 'c' + i, label: String(h == null ? '' : h) }; });
    var rows = aoa.slice(1).map(function (row) {
      var obj = {};
      fields.forEach(function (f, i) { obj[f.key] = toNumLike(row ? row[i] : ''); });
      return obj;
    });
    writeFile(fields, rows, cfg);
  }

  /** 详情导出：弹窗勾选字段 → 取数 → 导出 */
  async function doExport() {
    var cfg = state.cfg;
    var checked = state.fields.filter(function (f) { return state.checked[f.key]; });
    if (!checked.length) { setStatus('请至少勾选一个字段'); return; }
    setBusy(true, '正在取数…');
    try {
      var rows = await cfg.fetchRows(checked);
      rows = Array.isArray(rows) ? rows : [];
      if (!rows.length) { setBusy(false, '暂无数据可导出'); return; }
      setBusy(true, '正在生成文件（' + rows.length + ' 行）…');
      writeFile(checked, rows, cfg);
      close();
    } catch (e) {
      console.error(e);
      setBusy(false, '导出失败：' + ((e && e.message) || e));
    }
  }

  window.MonitorExport = { open: open, download: download, downloadAoa: downloadAoa, close: close };
})();
