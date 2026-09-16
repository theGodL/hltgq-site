/**
 * 监测页面「站点排序」抽屉（流量 / 闸门 / 水位 / 雨量 / 墒情 五个监测页共用）
 *
 * 用法：
 *   StationSort.mount({
 *     type: 'flow',                  // 监测类型（与 /station-sort?type= 值域一致）；
 *                                    // 可传数组（如 ['waterLevel', 'gate']）在一个抽屉里分两组列出、保存时按组分别落库；
 *                                    // 也可传函数（每次打开抽屉时求值）按上下文动态返回
 *     groupLabels: { waterLevel: '水位站' },   // 分组标题（可选，仅多类型抽屉需要，未配置时显示类型名）
 *     api: API,                      // 接口前缀（默认空）
 *     tip: '…',                      // 抽屉提示文案（可选，支持函数按当前类型返回不同说明）
 *     onSaved: async () => { ... },  // 保存成功后的刷新回调（重载站点下拉与列表）
 *   });
 *
 * 行为：
 *   1. 在 .tab-bar（Tab 同行）最右侧追加「站点排序」按钮（用独立类名，不参与页面 Tab 切换；样式与页面 Tab 一致）；
 *   2. 点击从右侧滑出抽屉，列出该监测类型下全部站点（序号 + 站点名称）；
 *   3. 支持鼠标拖拽（按住站点行实时换位）与序号输入两种方式设置展示顺序；
 *      多类型抽屉按组呈现，各组顺序互相独立（拖拽与序号输入均限在组内，组内序号各自从 1 起）；
 *   4. 保存后对该类型的所有接口与页面生效（站点下拉、监测列表、图表站点轴等）。
 *
 * 排序键：站点管理主键（站点档案表 id），清单中的 siteId 由后端从站点标识解析；
 *   档案中无对应记录的站点 sortable=false（置灰、不占序号、不可拖拽、不提交）。
 */
(function (global) {
  'use strict';

  var STYLE_ID = 'station-sort-style';
  var CSS = [
    // 按钮与页面 Tab 同款卡片样式（白底、8px 圆角、同款阴影与字号，hover 变蓝）；margin-left:auto 把它贴到同一行最右侧
    '.stsort-btn{display:inline-flex;align-items:center;justify-content:center;gap:5px;margin-left:auto;padding:10px 20px;border:1px solid transparent;border-radius:8px;background:#fff;color:#666;font-size:14px;font-weight:400;font-family:inherit;white-space:nowrap;cursor:pointer;user-select:none;box-shadow:0 1px 2px rgba(0,0,0,.03);transition:color .2s,box-shadow .2s}',
    '.stsort-btn:hover{color:#1677ff}',
    '.stsort-mask{position:fixed;inset:0;z-index:2000;background:rgba(0,0,0,.35);opacity:0;visibility:hidden;transition:opacity .25s,visibility .25s}',
    '.stsort-root.open .stsort-mask{opacity:1;visibility:visible}',
    '.stsort-drawer{position:fixed;top:0;right:0;z-index:2001;display:flex;flex-direction:column;width:420px;max-width:92vw;height:100%;background:#fff;box-shadow:-6px 0 24px rgba(0,0,0,.12);transform:translateX(100%);transition:transform .28s cubic-bezier(.4,0,.2,1)}',
    '.stsort-root.open .stsort-drawer{transform:translateX(0)}',
    '.stsort-hd{display:flex;align-items:center;justify-content:space-between;flex-shrink:0;padding:14px 20px;border-bottom:1px solid #f0f0f0}',
    '.stsort-title{font-size:16px;font-weight:600;color:#1f1f1f;line-height:24px}',
    '.stsort-x{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;padding:0;border:none;border-radius:4px;background:none;color:#8c8c8c;font-size:20px;line-height:1;font-family:inherit;cursor:pointer;transition:color .2s,background .2s}',
    '.stsort-x:hover{color:#262626;background:#f5f5f5}',
    '.stsort-tip{padding:10px 20px 0;font-size:12px;color:#999;line-height:1.6}',
    '.stsort-head{display:flex;align-items:center;gap:10px;padding:12px 32px 6px;font-size:13px;color:#666}',
    '.stsort-head-no{width:56px;text-align:center;flex-shrink:0}',
    '.stsort-head-name{flex:1;padding-left:20px}',
    '.stsort-group{display:flex;align-items:center;gap:8px;margin:12px 0 8px;padding-top:8px;border-top:1px solid #f0f0f0;font-size:13px;font-weight:600;color:#1f1f1f}',
    '.stsort-group-n{font-size:12px;font-weight:400;color:#999}',
    '.stsort-list{flex:1;overflow-y:auto;padding:0 20px 8px}',
    '.stsort-row{display:flex;align-items:center;gap:10px;margin-bottom:8px;padding:8px 12px;border:1px solid #f0f0f0;border-radius:6px;background:#fff;user-select:none;transition:background .15s,border-color .15s}',
    '.stsort-row:hover{border-color:#91caff}',
    '.stsort-row.is-dragging{border-color:#1677ff;background:#e6f4ff;box-shadow:0 4px 12px rgba(22,119,255,.18)}',
    '.stsort-row.is-disabled{background:#fafafa;border-color:#f0f0f0}',
    '.stsort-row.is-disabled:hover{border-color:#f0f0f0}',
    '.stsort-row.is-disabled .stsort-grip,.stsort-row.is-disabled .stsort-name{color:#bfbfbf;cursor:not-allowed}',
    '.stsort-row.is-disabled .stsort-no{border-color:#f0f0f0;background:#fafafa;color:#bfbfbf;cursor:not-allowed}',
    '.stsort-tag{flex-shrink:0;padding:1px 6px;border-radius:3px;background:#f0f0f0;color:#999;font-size:12px}',
    '.stsort-no{width:56px;height:28px;flex-shrink:0;border:1px solid #d9d9d9;border-radius:4px;background:#fff;color:#333;font-size:13px;font-family:inherit;text-align:center;outline:none;appearance:textfield;-moz-appearance:textfield}',
    '.stsort-no:focus{border-color:#1677ff;box-shadow:0 0 0 2px rgba(22,119,255,.1)}',
    '.stsort-no::-webkit-outer-spin-button,.stsort-no::-webkit-inner-spin-button{-webkit-appearance:none;margin:0}',
    '.stsort-grip{display:inline-flex;align-items:center;flex-shrink:0;color:#bfbfbf;cursor:grab}',
    '.stsort-grip svg{display:block}',
    '.stsort-name{flex:1;min-width:0;font-size:14px;color:#1f1f1f;cursor:grab;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}',
    '.stsort-row.is-dragging .stsort-grip,.stsort-row.is-dragging .stsort-name{cursor:grabbing}',
    '.stsort-hint{padding:28px 0;text-align:center;font-size:13px;color:#bbb}',
    '.stsort-hint.is-error{color:#ff4d4f}',
    '.stsort-ft{display:flex;align-items:center;justify-content:flex-end;gap:10px;flex-shrink:0;padding:12px 20px;border-top:1px solid #f0f0f0}',
    '.stsort-cancel,.stsort-save{height:32px;padding:0 16px;border:1px solid transparent;border-radius:6px;font-size:14px;font-family:inherit;cursor:pointer;transition:all .2s}',
    '.stsort-cancel{background:#fff;color:#333;border-color:#d9d9d9}',
    '.stsort-cancel:hover{color:#1677ff;border-color:#1677ff}',
    '.stsort-save{background:#1677ff;color:#fff;border-color:#1677ff}',
    '.stsort-save:hover{background:#4096ff;border-color:#4096ff}',
    '.stsort-save:disabled{opacity:.7;cursor:not-allowed}',
    'body.stsort-body-lock{overflow:hidden}',
    '.stsort-toast{position:fixed;top:24px;left:50%;z-index:2200;padding:9px 18px;border:1px solid #d9d9d9;border-radius:6px;background:#fff;box-shadow:0 6px 16px rgba(0,0,0,.12);font-size:14px;color:#333;opacity:0;transform:translate(-50%,-16px);transition:opacity .25s,transform .25s;pointer-events:none}',
    '.stsort-toast.show{opacity:1;transform:translate(-50%,0)}',
    '.stsort-toast.is-error{color:#ff4d4f;border-color:#ffccc7;background:#fff2f0}',
  ].join('\n');

  var GRIP_SVG = '<svg width="10" height="14" viewBox="0 0 10 14" fill="currentColor" aria-hidden="true">'
    + '<circle cx="2.5" cy="2.5" r="1.2"/><circle cx="7.5" cy="2.5" r="1.2"/>'
    + '<circle cx="2.5" cy="7" r="1.2"/><circle cx="7.5" cy="7" r="1.2"/>'
    + '<circle cx="2.5" cy="11.5" r="1.2"/><circle cx="7.5" cy="11.5" r="1.2"/></svg>';

  function injectStyle() {
    if (document.getElementById(STYLE_ID)) return;
    var style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = CSS;
    document.head.appendChild(style);
  }

  function esc(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  /** 轻提示：保存成功/失败反馈（各页面 toast 实现不一，组件内自绘避免耦合） */
  function toast(message, isError) {
    var bar = document.createElement('div');
    bar.className = 'stsort-toast' + (isError ? ' is-error' : '');
    bar.textContent = message;
    document.body.appendChild(bar);
    requestAnimationFrame(function () { bar.classList.add('show'); });
    setTimeout(function () {
      bar.classList.remove('show');
      setTimeout(function () { bar.parentNode && bar.parentNode.removeChild(bar); }, 300);
    }, 2200);
  }

  function mount(options) {
    var opt = options || {};
    var typeOption = opt.type;
    if (!typeOption) throw new Error('StationSort.mount 缺少 type 参数');
    var api = opt.api || '';

    /**
     * 当前监测类型（一个或多个）：字符串 / 数组两种静态写法；函数形式每次打开抽屉时求值。
     * 多个类型呈现为「分组清单」：每组一个类型、组内各自成序，保存时按组分别落库。
     */
    function currentTypes() {
      var t = typeof typeOption === 'function' ? typeOption() : typeOption;
      if (!t || (Array.isArray(t) && !t.length)) throw new Error('StationSort 当前监测类型为空，请检查 type 配置');
      return Array.isArray(t) ? t.slice() : [t];
    }

    /** 分组标题：opt.groupLabels[type] 优先，未配置时回退为类型名本身 */
    function groupLabel(type) {
      var labels = opt.groupLabels || {};
      return labels[type] || type;
    }

    /** 抽屉提示文案：opt.tip 支持函数（按当前类型返回不同说明） */
    function currentTip() {
      var tip = typeof opt.tip === 'function' ? opt.tip(currentTypes()) : opt.tip;
      return tip || '按住站点行拖动，或直接修改序号，保存后对该类型的所有接口与页面生效';
    }

    /** 本次抽屉列表对应的类型（打开时求值并锁定，保存时提交同一批类型，避免清单与类型错配） */
    var loadedTypes = [];

    injectStyle();

    // 1. 「站点排序」按钮：追加到 Tab 栏，靠 margin-left:auto 固定在同行最右侧（样式与 Tab 一致）
    var anchor = document.querySelector(opt.anchor || '.tab-bar');
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'stsort-btn';
    btn.textContent = opt.buttonText || '站点排序';
    (anchor || document.body).appendChild(btn);

    // 2. 右侧抽屉
    var root = document.createElement('div');
    root.className = 'stsort-root';
    root.innerHTML =
      '<div class="stsort-mask"></div>'
      + '<aside class="stsort-drawer" role="dialog" aria-label="站点排序">'
      + '  <div class="stsort-hd"><span class="stsort-title">站点排序</span>'
      + '    <button type="button" class="stsort-x" title="关闭">×</button></div>'
      + '  <p class="stsort-tip"></p>'
      + '  <div class="stsort-head"><span class="stsort-head-no">序号</span><span class="stsort-head-name">站点名称</span></div>'
      + '  <div class="stsort-list"></div>'
      + '  <div class="stsort-ft">'
      + '    <button type="button" class="stsort-cancel">取消</button>'
      + '    <button type="button" class="stsort-save">保存</button></div>'
      + '</aside>';
    document.body.appendChild(root);

    var mask = root.querySelector('.stsort-mask');
    var listEl = root.querySelector('.stsort-list');
    var saveBtn = root.querySelector('.stsort-save');
    var tipEl = root.querySelector('.stsort-tip');
    tipEl.textContent = currentTip();

    function rows() {
      return Array.prototype.slice.call(listEl.querySelectorAll('.stsort-row'));
    }

    /** 指定分组的行（按 DOM 顺序）；type 传空表示全部分组 */
    function groupRows(type) {
      if (!type) return rows();
      return rows().filter(function (r) { return r.dataset.group === type; });
    }

    /** 分组内可排序行（档案缺失站点置灰，不参与序号计算、拖拽换位与提交） */
    function sortableRows(type) {
      return groupRows(type).filter(function (r) { return !r.classList.contains('is-disabled'); });
    }

    /** 序号按各组实际顺序重排（拖拽与序号输入后统一调用）：组内序号各自从 1 起 */
    function renumber() {
      var seen = {};
      rows().forEach(function (row) {
        var type = row.dataset.group || '';
        if (seen[type]) return;
        seen[type] = true;
        var list = sortableRows(type);
        for (var i = 0; i < list.length; i++) {
          var input = list[i].querySelector('.stsort-no');
          if (!input) continue;
          input.max = String(list.length);
          if (input.value !== String(i + 1)) input.value = String(i + 1);
        }
      });
    }

    /** 单行 HTML：data-group 标记所属类型（多类型抽屉按组渲染） */
    function rowHtml(item, type) {
      var siteId = item.siteId == null ? '' : String(item.siteId);
      var name = item.name == null ? '' : String(item.name);
      var off = item.sortable === false;
      var why = '该站点在站点档案中无对应记录，不参与排序（保持默认顺序）';
      return '<div class="stsort-row' + (off ? ' is-disabled' : '') + '"'
        + ' data-site-id="' + esc(siteId) + '" data-group="' + esc(type) + '"'
        + (off ? ' title="' + why + '"' : '') + '>'
        + '<input type="number" class="stsort-no" min="1"'
        + (off ? ' placeholder="—" disabled' : ' value="1"')
        + ' title="输入序号后回车">'
        + '<span class="stsort-grip" title="按住拖动排序">' + GRIP_SVG + '</span>'
        + '<span class="stsort-name" title="' + esc(name) + '">' + esc(name) + '</span>'
        + (off ? '<span class="stsort-tag" title="' + why + '">不参与排序</span>' : '')
        + '</div>';
    }

    /** groups: [{ type, items }]；单类型抽屉不显示分组标题，多类型抽屉按组显示 */
    function render(groups) {
      var multi = groups.length > 1;
      var html = '';
      var sortableTotal = 0;
      var rowTotal = 0;
      groups.forEach(function (group) {
        var items = group.items || [];
        rowTotal += items.length;
        // 可排序站点按接口返回顺序在前，档案缺失站点置灰排在组末（不占序号）
        var sortable = [];
        var unSortable = [];
        items.forEach(function (item) {
          (item.sortable === false ? unSortable : sortable).push(item);
        });
        sortableTotal += sortable.length;
        if (multi) {
          html += '<div class="stsort-group">' + esc(groupLabel(group.type))
            + '<span class="stsort-group-n">' + sortable.length + ' 站</span></div>';
        }
        html += sortable.concat(unSortable).map(function (item) {
          return rowHtml(item, group.type);
        }).join('');
      });
      if (!rowTotal) {
        listEl.innerHTML = '<div class="stsort-hint">该监测类型下暂无站点</div>';
        saveBtn.disabled = true;
        return;
      }
      listEl.innerHTML = html;
      saveBtn.disabled = sortableTotal === 0;
      renumber();
    }

    /** 逐个类型拉清单（串行，保证分组标题与页面配置的顺序一致） */
    function load() {
      loadedTypes = currentTypes();
      listEl.innerHTML = '<div class="stsort-hint">加载中…</div>';
      saveBtn.disabled = true;
      var groups = [];
      var chain = Promise.resolve();
      loadedTypes.forEach(function (type) {
        chain = chain
          .then(function () {
            return fetch(api + '/station-sort?type=' + encodeURIComponent(type), { credentials: 'include' });
          })
          .then(function (res) {
            if (!res.ok) throw new Error('HTTP ' + res.status);
            return res.json();
          })
          .then(function (data) {
            groups.push({ type: type, items: Array.isArray(data) ? data : [] });
          });
      });
      chain
        .then(function () { render(groups); })
        .catch(function (err) {
          listEl.innerHTML = '<div class="stsort-hint is-error">站点列表加载失败：'
            + esc(err.message || err) + '</div>';
        });
    }

    /* ----- 方式一：鼠标拖拽（按住行实时换位） ----- */
    var dragRow = null;
    listEl.addEventListener('pointerdown', function (e) {
      if (e.button !== 0 || e.target.closest('.stsort-no')) return;
      var row = e.target.closest('.stsort-row');
      if (!row || row.classList.contains('is-disabled')) return;
      e.preventDefault();
      dragRow = row;
      row.classList.add('is-dragging');
      if (listEl.setPointerCapture) listEl.setPointerCapture(e.pointerId);
    });
    listEl.addEventListener('pointermove', function (e) {
      if (!dragRow) return;
      // 只在所属分组内换位（多类型抽屉各组顺序互相独立）
      var list = sortableRows(dragRow.dataset.group || '');
      if (list.length < 2) return;
      var target = null;
      for (var i = 0; i < list.length; i++) {
        var r = list[i];
        if (r === dragRow) continue;
        var box = r.getBoundingClientRect();
        if (e.clientY < box.top + box.height / 2) { target = r; break; }
      }
      if (target) {
        if (target.previousElementSibling !== dragRow) listEl.insertBefore(dragRow, target);
      } else {
        // 落在本组所有可排序站点之后：插到本组最后一个可排序站点之后（组内置灰站点仍留在组末）
        var last = null;
        for (var j = list.length - 1; j >= 0; j--) {
          if (list[j] !== dragRow) { last = list[j]; break; }
        }
        if (last && last.nextElementSibling !== dragRow) {
          if (last.nextElementSibling) listEl.insertBefore(dragRow, last.nextElementSibling);
          else listEl.appendChild(dragRow);
        }
      }
      renumber();
    });
    function endDrag() {
      if (!dragRow) return;
      dragRow.classList.remove('is-dragging');
      dragRow = null;
      renumber();
    }
    listEl.addEventListener('pointerup', endDrag);
    listEl.addEventListener('pointercancel', endDrag);

    /* ----- 方式二：序号输入（回车/失焦生效，其余序号自动顺延） ----- */
    function commitSeq(input) {
      var row = input.closest('.stsort-row');
      if (!row || row.classList.contains('is-disabled')) return;
      // 序号含义为「组内位次」，与后端按类型独立成序一致
      var list = sortableRows(row.dataset.group || '');
      var n = list.length;
      var k = parseInt(input.value, 10);
      if (isNaN(k) || k < 1) k = 1;
      if (k > n) k = n;
      var cur = list.indexOf(row);
      var dest = k - 1;
      if (dest !== cur && dest >= 0) {
        if (dest < cur) listEl.insertBefore(row, list[dest]);
        else listEl.insertBefore(row, list[dest].nextElementSibling);
      }
      renumber();
    }
    listEl.addEventListener('keydown', function (e) {
      var input = e.target.closest('.stsort-no');
      if (!input) return;
      if (e.key === 'Enter') {
        e.preventDefault();
        input.blur();
      } else if (e.key === 'Escape') {
        e.preventDefault();
        renumber();
        input.blur();
      }
    });
    listEl.addEventListener('change', function (e) {
      var input = e.target.closest('.stsort-no');
      if (input) commitSeq(input);
    });

    /** 保存：按组收集站点标识，逐个类型提交（后端按类型整表覆盖，互不影响） */
    function save() {
      var types = loadedTypes.length ? loadedTypes : currentTypes();
      var submissions = [];
      types.forEach(function (type) {
        var siteIds = sortableRows(type).map(function (r) { return r.dataset.siteId; })
          .filter(function (id) { return !!id; });
        if (siteIds.length) submissions.push({ type: type, siteIds: siteIds });
      });
      if (!submissions.length) return;
      saveBtn.disabled = true;
      saveBtn.textContent = '保存中…';
      var saved = 0;
      var chain = Promise.resolve();
      submissions.forEach(function (item) {
        chain = chain.then(function () {
          return fetch(api + '/station-sort', {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ type: item.type, siteIds: item.siteIds }),
          });
        })
          .then(function (res) {
            if (!res.ok) throw new Error('HTTP ' + res.status);
            return res.json().catch(function () { return {}; });
          })
          .then(function (data) {
            saved += (data && data.count ? data.count : item.siteIds.length);
          });
      });
      chain
        .then(function () {
          close();
          toast('站点排序已保存（' + saved + ' 个站点）');
          if (typeof opt.onSaved === 'function') return opt.onSaved();
        })
        .catch(function (err) {
          toast('保存失败：' + (err.message || err), true);
        })
        .then(function () {
          saveBtn.disabled = false;
          saveBtn.textContent = '保存';
        });
    }

    function open() {
      root.classList.add('open');
      document.body.classList.add('stsort-body-lock');
      tipEl.textContent = currentTip();
      load();
    }

    function close() {
      root.classList.remove('open');
      document.body.classList.remove('stsort-body-lock');
    }

    btn.addEventListener('click', open);
    mask.addEventListener('click', close);
    root.querySelector('.stsort-x').addEventListener('click', close);
    root.querySelector('.stsort-cancel').addEventListener('click', close);
    saveBtn.addEventListener('click', save);
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && root.classList.contains('open')) close();
    });

    return { open: open, close: close };
  }

  global.StationSort = { mount: mount };
})(typeof window !== 'undefined' ? window : this);
