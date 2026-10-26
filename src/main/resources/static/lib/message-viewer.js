/**
 * 监测页通用「查看报文」：历史数据行 → 库中该时刻入库的原始报文
 * 依赖：后端 GET /hltgq-site/monitor-messages（key + tm + before/after）
 *
 * 用法一（推荐，表格行内按钮，组件自动接管点击）：
 *   <button type="button" class="mv-act" data-msg-key="9000000001" data-msg-tm="2026-10-26 19:40:00"
 *           data-msg-name="渠首进水闸" data-msg-before="2" data-msg-after="2">查看报文</button>
 *
 * 用法二（手动打开）：
 *   MessageViewer.open({ key, tm, siteName, before, after, limit });
 *
 * data-msg-key 为站点标识（测站编码或站点 UUID，与后端 (stcd = key OR site = key) 口径一致）；
 * before / after 为查询窗口分钟数，缺省各 2 分钟（水质整点补齐行传 after=60）。
 * 弹窗自包含（自带样式与 DOM），不依赖页面已有弹窗样式。
 */
(function (global) {
  'use strict';

  var API = '/hltgq-site';
  var OVERLAY_ID = 'mv-overlay';
  var dom = null;
  var busy = false;

  var CSS = ''
    + '#' + OVERLAY_ID + '{display:none;position:fixed;inset:0;background:rgba(0,0,0,.45);z-index:3200;align-items:center;justify-content:center;padding:20px}'
    + '#' + OVERLAY_ID + '.show{display:flex}'
    + '#' + OVERLAY_ID + ' .mv-modal{width:920px;max-width:96vw;height:min(760px,90vh);background:#fff;border-radius:10px;display:flex;flex-direction:column;box-shadow:0 8px 32px rgba(0,0,0,.18)}'
    + '#' + OVERLAY_ID + ' .mv-modal.is-max{width:96vw;height:92vh;max-width:none;max-height:none}'
    + '#' + OVERLAY_ID + ' .mv-hd{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:14px 18px;border-bottom:1px solid #f0f0f0}'
    + '#' + OVERLAY_ID + ' .mv-hd span{font-size:15px;font-weight:600;color:#1f1f1f;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}'
    + '#' + OVERLAY_ID + ' .mv-actions{display:flex;align-items:center;gap:4px;flex-shrink:0}'
    + '#' + OVERLAY_ID + ' .mv-btn-ico{border:0;background:transparent;color:#999;font-size:18px;line-height:1;cursor:pointer;padding:2px 6px;border-radius:4px}'
    + '#' + OVERLAY_ID + ' .mv-btn-ico:hover{color:#262626;background:#f5f5f5}'
    + '#' + OVERLAY_ID + ' .mv-bd{padding:14px 18px 18px;overflow:auto;flex:1}'
    + '#' + OVERLAY_ID + ' .mv-tip{font-size:12px;color:#999;margin:0 0 12px;line-height:1.7}'
    + '#' + OVERLAY_ID + ' .mv-list{display:flex;flex-direction:column;gap:12px}'
    + '#' + OVERLAY_ID + ' .mv-item{border:1px solid #ececec;border-radius:8px;overflow:hidden}'
    + '#' + OVERLAY_ID + ' .mv-item-hd{display:flex;align-items:center;flex-wrap:wrap;gap:6px 12px;padding:8px 12px;background:#fafafa;border-bottom:1px solid #f0f0f0}'
    + '#' + OVERLAY_ID + ' .mv-tag{display:inline-block;padding:1px 8px;border-radius:4px;background:#e6f4ff;color:#1677ff;font-size:12px;font-weight:600;line-height:1.8}'
    + '#' + OVERLAY_ID + ' .mv-tag.frame{background:#fff7e6;color:#fa8c16}'
    + '#' + OVERLAY_ID + ' .mv-meta{color:#888;font-size:12px}'
    + '#' + OVERLAY_ID + ' .mv-copy{margin-left:auto;background:#fff;border:1px solid #d9d9d9;border-radius:4px;color:#666;font-size:12px;padding:2px 10px;cursor:pointer;transition:all .2s}'
    + '#' + OVERLAY_ID + ' .mv-copy:hover{border-color:#1677ff;color:#1677ff}'
    + '#' + OVERLAY_ID + ' .mv-text{margin:0;padding:10px 12px;font-family:Consolas,Monaco,monospace;font-size:12px;line-height:1.6;color:#333;white-space:pre-wrap;word-break:break-all;max-height:300px;overflow:auto;background:#fff}'
    + '#' + OVERLAY_ID + ' .mv-empty{padding:40px;text-align:center;color:#bbb;font-size:14px}'
    + '#' + OVERLAY_ID + ' .mv-toast{position:fixed;left:50%;top:80px;transform:translateX(-50%);background:rgba(0,0,0,.72);color:#fff;font-size:14px;padding:10px 20px;border-radius:6px;opacity:0;pointer-events:none;transition:opacity .25s;z-index:3300}'
    + '#' + OVERLAY_ID + ' .mv-toast.show{opacity:1}'
    // 表格行内触发按钮（各监测页历史数据表统一使用）
    + '.mv-act{border:0;background:none;color:#1677ff;font-size:13px;padding:0;cursor:pointer;white-space:nowrap}'
    + '.mv-act:hover{text-decoration:underline}';

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

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
      + '<div class="mv-modal">'
      + '  <div class="mv-hd">'
      + '    <span id="mv-title">报文详情</span>'
      + '    <div class="mv-actions">'
      + '      <button type="button" class="mv-btn-ico mv-max" title="最大化" aria-label="最大化">⤢</button>'
      + '      <button type="button" class="mv-btn-ico mv-close" title="关闭" aria-label="关闭">×</button>'
      + '    </div>'
      + '  </div>'
      + '  <div class="mv-bd" id="mv-body"></div>'
      + '</div>'
      + '<div class="mv-toast" id="mv-toast"></div>';
    document.body.appendChild(wrap);

    dom = {
      wrap: wrap,
      modal: wrap.querySelector('.mv-modal'),
      title: wrap.querySelector('#mv-title'),
      body: wrap.querySelector('#mv-body'),
      toast: wrap.querySelector('#mv-toast')
    };

    wrap.querySelector('.mv-close').addEventListener('click', close);
    wrap.querySelector('.mv-max').addEventListener('click', function () {
      dom.modal.classList.toggle('is-max');
      this.title = dom.modal.classList.contains('is-max') ? '还原' : '最大化';
      this.setAttribute('aria-label', this.title);
    });
    wrap.addEventListener('click', function (e) {
      if (e.target === wrap) close();
    });
    // 捕获阶段拦截 ESC：只关报文弹窗，不连带关闭下层的「站点详情」
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && wrap.classList.contains('show')) {
        e.stopPropagation();
        close();
      }
    }, true);
  }

  function toast(text) {
    if (!dom) return;
    dom.toast.textContent = text;
    dom.toast.classList.add('show');
    clearTimeout(toast._t);
    toast._t = setTimeout(function () { dom.toast.classList.remove('show'); }, 2000);
  }

  /** 报文 JSON 美化（解析失败保留原文；超长文本不美化，避免卡顿） */
  function prettyText(text) {
    if (text == null) return '';
    var s = String(text);
    if (s.length > 200000) return s;
    try { return JSON.stringify(JSON.parse(s), null, 2); } catch (e) { return s; }
  }

  /** 复制文本：优先 Clipboard API，非安全上下文降级 execCommand */
  function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text).then(function () { return true; }, function () { return fallbackCopy(text); });
    }
    return Promise.resolve(fallbackCopy(text));
  }

  function fallbackCopy(text) {
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.top = '-1000px';
    document.body.appendChild(ta);
    ta.select();
    var ok = false;
    try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
    document.body.removeChild(ta);
    return ok;
  }

  function itemText(m) {
    var head = '[' + (m.type === 'frame' ? '原始帧' : (m.tag || '报文')) + '] 入库 ' + (m.tm || '-');
    if (m.sendtm) head += ' 设备时间 ' + m.sendtm;
    if (m.afn != null && m.afn !== '') head += ' AFN ' + m.afn;
    return head + '\n' + (m.type === 'frame' ? (m.frame || '') : prettyText(m.msgText));
  }

  function render(list, cfg) {
    var box = dom.body;
    if (!list.length) {
      box.innerHTML = '<div class="mv-empty">监测时间 ' + esc(cfg.tm) + ' 前后窗口内无报文入库</div>';
      return;
    }
    box.innerHTML = '<div class="mv-tip">共 ' + list.length + ' 条。以下为数据库中该时刻入库的原始报文'
      + '（查询窗口：监测时间前 ' + cfg.before + ' 分钟 ~ 后 ' + cfg.after + ' 分钟），可复制提供给设备厂家核对。'
      + '<button type="button" class="mv-copy" data-copy-all style="margin-left:8px;vertical-align:middle">复制全部</button></div>'
      + '<div class="mv-list">' + list.map(function (m, i) {
        var isFrame = m.type === 'frame';
        var content = isFrame ? (m.frame || '') : prettyText(m.msgText);
        return '<div class="mv-item">'
          + '<div class="mv-item-hd">'
          + '<span class="mv-tag' + (isFrame ? ' frame' : '') + '">' + esc(isFrame ? '原始帧' : (m.tag || '报文')) + '</span>'
          + '<span class="mv-meta">入库 ' + esc(m.tm || '-') + '</span>'
          + (m.sendtm ? '<span class="mv-meta">设备时间 ' + esc(m.sendtm) + '</span>' : '')
          + (m.afn != null && m.afn !== '' ? '<span class="mv-meta">AFN ' + esc(m.afn) + '</span>' : '')
          + '<button type="button" class="mv-copy" data-copy="' + i + '">复制</button>'
          + '</div>'
          + '<pre class="mv-text">' + esc(content) + '</pre>'
          + '</div>';
      }).join('') + '</div>';

    box.querySelectorAll('[data-copy]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var m = list[+btn.getAttribute('data-copy')];
        if (!m) return;
        copyText(m.type === 'frame' ? (m.frame || '') : (m.msgText || '')).then(function (ok) {
          toast(ok ? '报文已复制' : '复制失败，请手动选择文本');
        });
      });
    });
    var all = box.querySelector('[data-copy-all]');
    if (all) {
      all.addEventListener('click', function () {
        copyText(list.map(itemText).join('\n\n')).then(function (ok) {
          toast(ok ? '全部报文已复制' : '复制失败，请手动选择文本');
        });
      });
    }
  }

  /** 数字参数归一化（data-* 取到的是字符串，非法值回退默认） */
  function toInt(v, dft) {
    var n = parseInt(v, 10);
    return isFinite(n) ? n : dft;
  }

  /** 时间归一化：兼容 'yyyy-MM-dd HH:mm' / ISO('T'、毫秒)；无法识别的返回空串（阻止无效请求） */
  function normalizeTm(v) {
    var s = String(v == null ? '' : v).trim().replace('T', ' ').replace(/\.\d+$/, '');
    var m = s.match(/^(\d{4}-\d{2}-\d{2})[ ](\d{2}:\d{2})(?::(\d{2}))?$/);
    if (!m) return '';
    return m[1] + ' ' + m[2] + ':' + (m[3] || '00');
  }

  function open(cfg) {
    cfg = cfg || {};
    // 各页面时间格式不一（部分页面截到分钟），统一补秒后再查，避免后端解析失败
    var tm = normalizeTm(cfg.tm);
    if (!cfg.key || !tm) {
      window.alert('缺少站点或监测时间，无法查询报文');
      return;
    }
    injectStyle();
    buildDom();
    if (busy) return;
    busy = true;

    var params = {
      key: cfg.key,
      tm: tm,
      before: toInt(cfg.before, 2),
      after: toInt(cfg.after, 2),
      limit: toInt(cfg.limit, 30)
    };
    dom.title.textContent = (cfg.siteName || '站点') + ' · 报文 ' + tm;
    dom.body.innerHTML = '<div class="mv-empty">加载中...</div>';
    dom.wrap.classList.add('show');

    var p = new URLSearchParams();
    Object.keys(params).forEach(function (k) { p.set(k, String(params[k])); });

    fetch(API + '/monitor-messages?' + p, { credentials: 'include' })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) {
        render(Array.isArray(data) ? data : [], params);
      })
      .catch(function (e) {
        console.error(e);
        dom.body.innerHTML = '<div class="mv-empty">报文加载失败，请重试</div>';
      })
      .then(function () { busy = false; });
  }

  function close() {
    if (!dom) return;
    dom.wrap.classList.remove('show');
    dom.modal.classList.remove('is-max');
    var maxBtn = dom.wrap.querySelector('.mv-max');
    if (maxBtn) { maxBtn.title = '最大化'; maxBtn.setAttribute('aria-label', '最大化'); }
    dom.body.innerHTML = '';
  }

  // 表格行内按钮统一接管：任一 [data-msg-tm] 按钮点击即打开对应时刻报文
  document.addEventListener('click', function (e) {
    var btn = e.target.closest ? e.target.closest('[data-msg-tm]') : null;
    if (!btn) return;
    // 阻止冒泡：历史表行若绑定行点击（打开详情等），点「查看报文」不应连带触发
    e.stopPropagation();
    open({
      key: btn.getAttribute('data-msg-key'),
      tm: btn.getAttribute('data-msg-tm'),
      siteName: btn.getAttribute('data-msg-name'),
      before: btn.getAttribute('data-msg-before'),
      after: btn.getAttribute('data-msg-after'),
      limit: btn.getAttribute('data-msg-limit')
    });
  });

  /**
   * 生成行内「查看报文」按钮 HTML（各监测页历史数据表渲染时调用，统一文案与 data 属性）
   *
   * @param {object} opt { key, tm, name, before, after }
   * @return {string} button HTML
   */
  function button(opt) {
    opt = opt || {};
    return '<button type="button" class="mv-act"'
      + ' data-msg-key="' + esc(opt.key) + '"'
      + ' data-msg-tm="' + esc(opt.tm) + '"'
      + ' data-msg-name="' + esc(opt.name) + '"'
      + (opt.before != null && opt.before !== '' ? ' data-msg-before="' + esc(opt.before) + '"' : '')
      + (opt.after != null && opt.after !== '' ? ' data-msg-after="' + esc(opt.after) + '"' : '')
      + '>查看报文</button>';
  }

  global.MessageViewer = { open: open, close: close, button: button };
})(window);
