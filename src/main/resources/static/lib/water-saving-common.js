/* ===== 节水模块公共脚本：water-saving-scheme-list / water-saving-indicator-list 共用 =====
   唯一实现（两页此前的内联副本已收敛至此），修改此处即两页生效。
   页面契约：
     1. 本文件须在页面主脚本之前加载；
     2. 页面全局提供：API（接口前缀）、ICON（至少含 search / tick / empty）、S（分页需 page+size+total）；
     3. 页级差异经参数传入：emptyRow 列数（9 / 8）、jumpToPage / onPagerClick 的 refresh 回调。
   页面用法：主脚本顶部 `const { $, esc, showToast, ... } = WSC;` 解构别名，页面调用点保持原名。 ===== */
(function () {
  /* ---------- 基础 ---------- */
  const $ = id => document.getElementById(id);
  const esc = v => String(v === null || v === undefined ? '' : v)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');

  /* ---------- 提示条与请求 ---------- */
  function showToast(msg, isErr) {
    const t = $('toast');
    t.textContent = msg;
    t.classList.toggle('err', !!isErr);
    t.classList.add('show');
    clearTimeout(showToast.timer);
    showToast.timer = setTimeout(() => t.classList.remove('show'), 2600);
  }

  /** 统一请求：非 2xx 抛出后端 message（后端 IllegalArgumentException → 400 + message） */
  async function api(path, opts) {
    const init = Object.assign({ credentials: 'include' }, opts || {});
    if (init.body && typeof init.body !== 'string' && !(init.body instanceof FormData)) {
      init.headers = Object.assign({ 'Content-Type': 'application/json' }, init.headers || {});
      init.body = JSON.stringify(init.body);
    }
    const res = await fetch(API + path, init);
    const text = await res.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch (e) { data = null; }
    }
    if (!res.ok) {
      const fallback = res.status === 403 ? '无操作权限' : '请求失败（HTTP ' + res.status + '）';
      throw new Error((data && (data.message || data.error)) || fallback);
    }
    return data;
  }

  /** 数值格式化（去尾零；空返回 null） */
  function fmtNum(v) {
    if (v === null || v === undefined || v === '') return null;
    const n = Number(v);
    if (isNaN(n)) return String(v);
    return String(Number(n.toFixed(6)));
  }

  /* ---------- 下拉组件（单选；渲染依赖页面 ICON.search / ICON.tick） ---------- */
  function selShell(el) {
    el.innerHTML =
      '<div class="sel-box" tabindex="0"><span class="sel-text placeholder"></span><span class="sel-arrow"></span></div>' +
      '<div class="sel-panel"><div class="sel-search" hidden>' + ICON.search +
      '<input type="text" placeholder="搜索" autocomplete="off"></div><div class="sel-list"></div></div>';
    return {
      el: el,
      box: el.querySelector('.sel-box'),
      textEl: el.querySelector('.sel-text'),
      searchWrap: el.querySelector('.sel-search'),
      searchInput: el.querySelector('.sel-search input'),
      list: el.querySelector('.sel-list')
    };
  }

  function optHtml(o, active) {
    return '<div class="sel-opt' + (active ? ' active' : '') + '" data-value="' + esc(o.value) + '">' +
      '<span class="opt-name">' + esc(o.label) + '</span>' +
      (o.sub ? '<span class="opt-sub">' + esc(o.sub) + '</span>' : '') +
      (active ? ICON.tick : '') + '</div>';
  }

  function filterOpts(st, kw) {
    if (!kw) return st.options;
    const k = kw.trim().toLowerCase();
    return st.options.filter(o => (o.label + ' ' + (o.sub || '')).toLowerCase().indexOf(k) >= 0);
  }

  function createSelect(el) {
    const d = selShell(el);
    const st = { value: '', label: '', options: [], placeholder: '请选择', emptyText: '无匹配数据', searchable: false, disabled: false, onChange: null };

    function sync() {
      const hit = st.options.filter(o => o.value === st.value)[0];
      const label = st.value ? (hit ? hit.label : (st.label || st.value)) : '';
      d.textEl.textContent = label || st.placeholder;
      d.textEl.classList.toggle('placeholder', !label);
    }
    function render() {
      const opts = filterOpts(st, st.searchable ? d.searchInput.value : '');
      d.list.innerHTML = opts.length
        ? opts.map(o => optHtml(o, o.value === st.value)).join('')
        : '<div class="sel-empty">' + esc(st.emptyText) + '</div>';
    }
    function open() {
      if (st.disabled) return;
      closeAllSelects(el);
      el.classList.add('open');
      if (st.searchable) d.searchInput.value = '';
      render();
      if (st.searchable) setTimeout(() => { try { d.searchInput.focus(); } catch (e) { /* 忽略 */ } }, 0);
    }
    function close() { el.classList.remove('open'); }

    d.box.addEventListener('click', () => el.classList.contains('open') ? close() : open());
    d.searchInput.addEventListener('input', render);
    d.list.addEventListener('mousedown', e => {
      const opt = e.target.closest('.sel-opt');
      if (!opt) return;
      e.preventDefault();
      st.value = opt.dataset.value;
      st.label = '';
      sync(); render(); close();
      if (st.onChange) st.onChange(st.value);
    });
    el._close = close;

    return {
      getValue: () => st.value,
      setOptions(list) { st.options = list || []; sync(); render(); return this; },
      setValue(v, label) { st.value = v || ''; st.label = label || ''; sync(); render(); return this; },
      setPlaceholder(t) { st.placeholder = t; sync(); return this; },
      setEmptyText(t) { st.emptyText = t; render(); return this; },
      setSearchable(on) { st.searchable = !!on; d.searchWrap.hidden = !on; return this; },
      setDisabled(on) { st.disabled = !!on; el.classList.toggle('is-disabled', !!on); return this; },
      setError(on) { el.classList.toggle('is-err', !!on); return this; },
      onChange(fn) { st.onChange = fn; return this; }
    };
  }

  function closeAllSelects(except) {
    document.querySelectorAll('.sel.open').forEach(s => {
      if (s !== except && typeof s._close === 'function') s._close();
    });
  }

  /* ---------- 列表空行（colspan 页级传入：方案页 9 / 指标页 8；图标依赖页面 ICON.empty） ---------- */
  function emptyRow(text, plain, colspan) {
    return '<tr><td colspan="' + colspan + '" class="empty"><div class="empty-box">' +
      (plain ? '' : ICON.empty) + '<div>' + esc(text) + '</div></div></td></tr>';
  }

  /* ---------- 分页（依赖页面 S.page/size/total；跳页/翻页后的刷新由页面 refresh 回调决定） ---------- */
  function pageNumbers(cur, total) {
    if (total <= 7) {
      const all = [];
      for (let i = 1; i <= total; i++) all.push(i);
      return all;
    }
    const out = [1];
    const start = Math.max(2, cur - 1);
    const end = Math.min(total - 1, cur + 1);
    if (start > 2) out.push('...');
    for (let p = start; p <= end; p++) out.push(p);
    if (end < total - 1) out.push('...');
    out.push(total);
    return out;
  }

  function renderPager() {
    const totalPages = Math.max(1, Math.ceil(S.total / S.size));
    const sizes = [10, 20, 50, 100];
    let html = '<span class="pg-info">共 ' + S.total + ' 条</span><div class="pg-btns">' +
      '<button class="pg-btn" data-pg="prev"' + (S.page <= 1 ? ' disabled' : '') + '>‹</button>';
    pageNumbers(S.page, totalPages).forEach(p => {
      html += p === '...'
        ? '<span class="pg-ellipsis">···</span>'
        : '<button class="pg-btn' + (p === S.page ? ' active' : '') + '" data-pg="' + p + '">' + p + '</button>';
    });
    html += '<button class="pg-btn" data-pg="next"' + (S.page >= totalPages ? ' disabled' : '') + '>›</button></div>' +
      '<select class="pg-size" id="pg-size">' +
      sizes.map(n => '<option value="' + n + '"' + (n === S.size ? ' selected' : '') + '>' + n + ' 条/页</option>').join('') +
      '</select>' +
      '<span class="pg-jump">跳至<input type="text" id="pg-jump" inputmode="numeric" aria-label="跳至页码"' +
      ' value="' + S.page + '" autocomplete="off">页</span>';
    $('pg-bar').innerHTML = html;
  }

  function jumpToPage(val, refresh) {
    const totalPages = Math.max(1, Math.ceil(S.total / S.size));
    const n = parseInt(String(val).trim(), 10);
    const target = isNaN(n) ? S.page : Math.min(totalPages, Math.max(1, n));
    const box = $('pg-jump');
    if (box) box.value = String(target);
    if (target === S.page) return;
    S.page = target;
    refresh();
  }

  function onPagerClick(e, refresh) {
    const btn = e.target.closest('[data-pg]');
    if (!btn || btn.disabled) return;
    const totalPages = Math.max(1, Math.ceil(S.total / S.size));
    const v = btn.dataset.pg;
    if (v === 'prev') S.page = Math.max(1, S.page - 1);
    else if (v === 'next') S.page = Math.min(totalPages, S.page + 1);
    else S.page = Number(v);
    refresh();
  }

  /* ---------- 抽屉（主抽屉；#drawer2 年度水量抽屉为方案页私有，仍在页内） ---------- */
  function openDrawer() {
    $('drawer-mask').classList.add('show');
    $('drawer').classList.add('show');
    $('drawer').setAttribute('aria-hidden', 'false');
  }

  function closeDrawer() {
    $('drawer-mask').classList.remove('show');
    $('drawer').classList.remove('show');
    $('drawer').setAttribute('aria-hidden', 'true');
  }

  /* ---------- 确认弹窗（待确认回调为本模块内部状态，页面不直接读写） ---------- */
  let dlgOnOk = null;

  function showDlg(opt) {
    $('dlg-title').textContent = opt.title || '提示';
    $('dlg-text').textContent = opt.text || '';
    const alertBox = $('dlg-alert');
    if (opt.alert) {
      alertBox.textContent = opt.alert;
      alertBox.hidden = false;
    } else {
      alertBox.hidden = true;
    }
    $('dlg-ok').textContent = opt.okText || '确定';
    $('dlg-ok').className = 'btn ' + (opt.danger === false ? 'btn-primary' : 'btn-danger');
    dlgOnOk = opt.onOk || null;
    $('dlg-mask').classList.add('show');
  }

  function closeDlg() {
    $('dlg-mask').classList.remove('show');
    dlgOnOk = null;
  }

  async function onDlgOk() {
    const fn = dlgOnOk;
    if (typeof fn !== 'function') {
      closeDlg();
      return;
    }
    const btn = $('dlg-ok');
    btn.classList.add('is-loading');
    try {
      await fn();
      closeDlg();
    } catch (e) {
      showToast(e.message || '操作失败', true);
    } finally {
      btn.classList.remove('is-loading');
    }
  }

  /* ---------- 统一导出（页面按原名解构使用） ---------- */
  window.WSC = {
    $, esc, showToast, api, fmtNum,
    selShell, optHtml, filterOpts, createSelect, closeAllSelects,
    emptyRow, pageNumbers, renderPager, jumpToPage, onPagerClick,
    openDrawer, closeDrawer, showDlg, closeDlg, onDlgOk
  };
})();
