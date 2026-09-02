/**
 * 模型接入公共客户端
 * 生产与本地代理均走 /hltgq-site 前缀
 */
(function (global) {
  const API = '/hltgq-site';

  function sleep(ms) {
    return new Promise(function (r) {
      setTimeout(r, ms);
    });
  }

  async function request(path, opts) {
    const options = opts || {};
    const headers = Object.assign({}, options.headers || {});
    let body = options.body;
    if (body && !(body instanceof FormData) && typeof body === 'object') {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(body);
    }
    var lastErr;
    // 文档建议网络抖动重试 2~3 次，间隔约 5s
    for (var attempt = 0; attempt < 3; attempt++) {
      try {
        return await fetch(API + path, Object.assign({}, options, { headers: headers, body: body }));
      } catch (e) {
        lastErr = e;
        if (attempt < 2) await sleep(5000);
      }
    }
    throw lastErr || new Error('网络异常');
  }

  function friendlyError(err, fallback) {
    if (!err) return fallback || '操作失败';
    var status = err.status;
    var raw = err.message || fallback || '操作失败';
    if (status === 502 || /模型服务不可达|模型服务暂不可用|模型服务异常/i.test(raw)) {
      return '模型服务暂不可用，请稍后重试';
    }
    if (status === 404) return '方案不存在';
    if (status === 400 && raw) return raw;
    return raw;
  }

  async function parseJson(res) {
    var text = await res.text();
    var data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch (_) {
      data = { message: text || res.statusText };
    }
    if (!res.ok) {
      var msg =
        (data && (data.message || data.errorMsg || data.error)) ||
        ('请求失败 HTTP ' + res.status);
      var err = new Error(msg);
      err.status = res.status;
      err.data = data;
      err.message = friendlyError(err, msg);
      throw err;
    }
    return data;
  }

  async function getJson(path) {
    return parseJson(await request(path, { method: 'GET' }));
  }

  async function postJson(path, body) {
    return parseJson(await request(path, { method: 'POST', body: body }));
  }

  async function putJson(path, body) {
    return parseJson(await request(path, { method: 'PUT', body: body }));
  }

  async function delJson(path) {
    return parseJson(await request(path, { method: 'DELETE' }));
  }

  async function postForm(path, formData) {
    return parseJson(await request(path, { method: 'POST', body: formData }));
  }

  async function download(path, filename) {
    var res = await request(path, { method: 'GET' });
    if (!res.ok) {
      var msg = '下载失败';
      try {
        var j = await res.json();
        msg = j.message || msg;
      } catch (_) {}
      var err = new Error(msg);
      err.status = res.status;
      if (res.status === 404) {
        err.message = '方案不存在或已删除，无法下载';
      } else {
        err.message = friendlyError(err, msg);
      }
      throw err;
    }
    var blob = await res.blob();
    var cd = res.headers.get('Content-Disposition') || '';
    var m = /filename\*?=(?:UTF-8''|")?([^";]+)/i.exec(cd);
    var name = filename || (m ? decodeURIComponent(m[1].replace(/"/g, '')) : 'download.bin');
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () {
      URL.revokeObjectURL(a.href);
    }, 2000);
  }

  async function waitCompleted(recordId, statusPathTemplate, timeoutMs, onTick) {
    var timeout = timeoutMs == null ? 330000 : timeoutMs;
    var start = Date.now();
    while (Date.now() - start < timeout) {
      try {
        var data = await getJson(statusPathTemplate.replace('{id}', recordId));
        if (typeof onTick === 'function') onTick(data);
        if (data.status === 'completed') return data;
        if (data.status === 'failed') {
          throw Object.assign(new Error(data.errorMsg || '计算失败'), { status: 0 });
        }
      } catch (e) {
        // 轮询期间网络抖动：继续等到超时，不立刻失败；404 则终止
        if (e && e.status === 404) throw new Error('方案不存在');
        if (e && e.status === 0) throw e;
        if (e && e.status && e.status !== 502) throw e;
        await sleep(5000);
        continue;
      }
      await sleep(3000);
    }
    throw new Error('计算超时，请稍后在历史方案中查看');
  }

  function statusLabel(status) {
    if (status === 'calculating') return '计算中';
    if (status === 'completed') return '已完成';
    if (status === 'failed') return '失败';
    return status || '';
  }

  async function health() {
    return getJson('/model/health');
  }

  var HEALTH_BANNER_ID = 'model-health-banner';

  function ensureHealthBanner() {
    if (typeof document === 'undefined') return null;
    var el = document.getElementById(HEALTH_BANNER_ID);
    if (el) return el;
    el = document.createElement('div');
    el.id = HEALTH_BANNER_ID;
    el.setAttribute('role', 'alert');
    el.style.cssText =
      'display:none;align-items:flex-start;gap:10px;padding:10px 14px;border-radius:8px;' +
      'background:#fff7e6;border:1px solid #ffd591;color:#ad6800;font-size:13px;line-height:1.55;';
    el.innerHTML =
      '<span style="flex:1" class="model-health-banner-text"></span>' +
      '<button type="button" aria-label="关闭" style="border:none;background:transparent;color:#ad6800;' +
      'cursor:pointer;font-size:16px;line-height:1;padding:0 2px">×</button>';
    el.querySelector('button').addEventListener('click', function () {
      el.style.display = 'none';
    });
    var wrap = document.querySelector('.page-wrap');
    var crumb = wrap && wrap.querySelector('.breadcrumb');
    if (crumb) {
      crumb.insertAdjacentElement('afterend', el);
    } else if (wrap) {
      wrap.insertBefore(el, wrap.firstChild);
    } else {
      document.body.insertBefore(el, document.body.firstChild);
    }
    return el;
  }

  /** 页面加载时探测模型健康；files 有 false 或服务异常时展示顶栏提示 */
  async function probeHealth() {
    try {
      var data = await health();
      var bad = [];
      var files = (data && data.files) || {};
      Object.keys(files).forEach(function (k) {
        if (files[k] === false) bad.push(k);
      });
      if (data && data.ok === false) bad.push('service');
      if (!bad.length) return data;
      var el = ensureHealthBanner();
      if (!el) return data;
      var text = el.querySelector('.model-health-banner-text');
      var tip =
        bad[0] === 'service'
          ? '模型服务状态异常，部分计算可能失败，请稍后重试。'
          : '模型依赖文件未就绪（' +
            bad.slice(0, 4).join('、') +
            (bad.length > 4 ? ' 等' : '') +
            '），相关计算链路可能暂不可用。';
      if (text) text.textContent = tip;
      el.style.display = 'flex';
      return data;
    } catch (e) {
      var el2 = ensureHealthBanner();
      if (el2) {
        var t2 = el2.querySelector('.model-health-banner-text');
        if (t2) {
          t2.textContent =
            '无法连接模型健康检查（' +
            friendlyError(e, '模型服务暂不可用') +
            '），提交计算前请确认服务可用。';
        }
        el2.style.display = 'flex';
      }
      return null;
    }
  }

  function moduleApi(base) {
    return {
      base: base,
      submit: function (body) {
        return postJson(base, body);
      },
      upload: function (formData) {
        return postForm(base + '/upload', formData);
      },
      template: function (filename) {
        return download(base + '/template', filename);
      },
      status: function (id) {
        return getJson(base + '/status/' + id);
      },
      list: function () {
        return getJson(base + '/list');
      },
      detail: function (id) {
        return getJson(base + '/' + id);
      },
      rename: function (id, name) {
        return putJson(base + '/' + id + '/name', { name: name });
      },
      remove: function (id) {
        return delJson(base + '/' + id);
      },
      wait: function (id, timeoutMs, onTick) {
        return waitCompleted(id, base + '/status/{id}', timeoutMs, onTick);
      }
    };
  }

  function fmtIsoDate(iso) {
    if (!iso) return '—';
    return String(iso).slice(0, 10);
  }

  function fmtDateTime(iso) {
    if (!iso) return '—';
    var s = String(iso).replace('T', ' ');
    return s.length > 16 ? s.slice(0, 16) : s;
  }

  function fmtNum(v, digits) {
    if (v == null || v === '' || Number.isNaN(Number(v))) return '—';
    var n = Number(v);
    return digits == null
      ? String(n)
      : n.toLocaleString(undefined, {
          minimumFractionDigits: 0,
          maximumFractionDigits: digits
        });
  }

  function isYes(flag) {
    return flag === '#1#' || flag === true || flag === '是' || flag === '已满足' || flag === '满足';
  }

  function completedOptions(list) {
    return (list || []).filter(function (x) {
      return x.status === 'completed';
    });
  }

  function fillSelect(selectEl, list, opts) {
    var o = opts || {};
    // 文档建议下拉仅选 completed；显式 onlyCompleted:false 才展示全部
    var items = o.onlyCompleted === false ? list || [] : completedOptions(list);
    var placeholder = o.placeholder || '请选择方案';
    var keepEmpty = o.keepEmpty !== false;
    selectEl.innerHTML =
      (keepEmpty ? '<option value="">' + placeholder + '</option>' : '') +
      items
        .map(function (it) {
          return (
            '<option value="' +
            it.id +
            '">' +
            (it.schemeName || it.id) +
            '</option>'
          );
        })
        .join('');
  }

  /** 历史查看前校验：仅 completed 可看详情 */
  function assertViewable(rec) {
    var status = rec && rec.status;
    if (status === 'calculating') {
      throw new Error('方案计算中，请稍候再查看');
    }
    if (status === 'failed') {
      throw new Error((rec && rec.errorMsg) || '该方案计算失败');
    }
    if (status && status !== 'completed') {
      throw new Error('该方案尚未计算完成，无法查看');
    }
  }

  function daysBetween(start, end) {
    var a = new Date(start + 'T00:00:00');
    var b = new Date(end + 'T00:00:00');
    return Math.round((b - a) / 86400000) + 1;
  }

  function zeros(n) {
    return Array.from({ length: n }, function () {
      return 0;
    });
  }

  var LOADING_ID = 'model-calc-loading-mask';

  function ensureLoadingMask() {
    if (typeof document === 'undefined') return null;
    var el = document.getElementById(LOADING_ID);
    if (el) return el;
    el = document.createElement('div');
    el.id = LOADING_ID;
    el.setAttribute('aria-live', 'assertive');
    el.style.cssText =
      'display:none;position:fixed;inset:0;z-index:20000;' +
      'background:rgba(15,23,42,.48);align-items:center;justify-content:center;';
    el.innerHTML =
      '<div style="min-width:220px;padding:28px 40px;border-radius:10px;background:#fff;' +
      'box-shadow:0 12px 40px rgba(0,0,0,.2);text-align:center">' +
      '<div style="width:36px;height:36px;margin:0 auto 14px;border-radius:50%;' +
      'border:3px solid #d6e4ff;border-top-color:#1677ff;' +
      'animation:model-calc-spin .7s linear infinite"></div>' +
      '<div class="model-calc-loading-text" style="font-size:14px;color:#334155;line-height:1.5">' +
      '模型计算中，请稍候…</div></div>';
    if (!document.getElementById('model-calc-spin-style')) {
      var st = document.createElement('style');
      st.id = 'model-calc-spin-style';
      st.textContent = '@keyframes model-calc-spin{to{transform:rotate(360deg)}}';
      document.head.appendChild(st);
    }
    document.body.appendChild(el);
    return el;
  }

  function showLoading(message) {
    var el = ensureLoadingMask();
    if (!el) return;
    var text = el.querySelector('.model-calc-loading-text');
    if (text) text.textContent = message || '模型计算中，请稍候…';
    el.style.display = 'flex';
  }

  function hideLoading() {
    var el = typeof document !== 'undefined' && document.getElementById(LOADING_ID);
    if (el) el.style.display = 'none';
  }

  /**
   * 紧凑页码序列，如 [1, '…', 4, 5, 6, '…', 53]
   * @param {number} current 当前页（从 1 起）
   * @param {number} totalPages 总页数
   * @param {number} [sibling=1] 当前页两侧各保留几页
   */
  function pageList(current, totalPages, sibling) {
    var total = Math.max(1, Number(totalPages) || 1);
    var cur = Math.min(Math.max(1, Number(current) || 1), total);
    var sib = sibling == null ? 1 : sibling;
    if (total <= 7) {
      return Array.from({ length: total }, function (_, i) {
        return i + 1;
      });
    }
    var set = {};
    set[1] = true;
    set[total] = true;
    for (var i = cur - sib; i <= cur + sib; i++) {
      if (i >= 1 && i <= total) set[i] = true;
    }
    // 靠近两端时多露几页，避免 1 … 2 3 这种难看形态
    if (cur <= 3) {
      set[2] = true;
      set[3] = true;
      set[4] = true;
    }
    if (cur >= total - 2) {
      set[total - 1] = true;
      set[total - 2] = true;
      set[total - 3] = true;
    }
    var nums = Object.keys(set)
      .map(Number)
      .sort(function (a, b) {
        return a - b;
      });
    var out = [];
    for (var j = 0; j < nums.length; j++) {
      if (j > 0 && nums[j] - nums[j - 1] > 1) out.push('…');
      out.push(nums[j]);
    }
    return out;
  }

  /** 生成带 ‹ › 与省略号的分页按钮 HTML */
  function pagerHtml(current, totalPages) {
    var total = Math.max(1, Number(totalPages) || 1);
    var cur = Math.min(Math.max(1, Number(current) || 1), total);
    var html =
      '<button class="pg-btn" data-page="' +
      (cur - 1) +
      '" ' +
      (cur <= 1 ? 'disabled' : '') +
      '>‹</button>';
    pageList(cur, total).forEach(function (item) {
      if (item === '…') {
        html += '<span class="pg-ellipsis">…</span>';
      } else {
        html +=
          '<button class="pg-btn' +
          (item === cur ? ' active' : '') +
          '" data-page="' +
          item +
          '">' +
          item +
          '</button>';
      }
    });
    html +=
      '<button class="pg-btn" data-page="' +
      (cur + 1) +
      '" ' +
      (cur >= total ? 'disabled' : '') +
      '>›</button>';
    return html;
  }

  var ModelApi = {
    API: API,
    sleep: sleep,
    request: request,
    getJson: getJson,
    postJson: postJson,
    putJson: putJson,
    delJson: delJson,
    postForm: postForm,
    download: download,
    waitCompleted: waitCompleted,
    health: health,
    probeHealth: probeHealth,
    friendlyError: friendlyError,
    statusLabel: statusLabel,
    assertViewable: assertViewable,
    showLoading: showLoading,
    hideLoading: hideLoading,
    pageList: pageList,
    pagerHtml: pagerHtml,
    fmtIsoDate: fmtIsoDate,
    fmtDateTime: fmtDateTime,
    fmtNum: fmtNum,
    isYes: isYes,
    completedOptions: completedOptions,
    fillSelect: fillSelect,
    daysBetween: daysBetween,
    zeros: zeros,
    short: moduleApi('/water-forecast/short'),
    long: moduleApi('/water-forecast/long'),
    loss: moduleApi('/water-forecast/loss'),
    demand: moduleApi('/water-forecast/demand'),
    allocation: moduleApi('/water-allocation'),
    decision: Object.assign(moduleApi('/water-decision'), {
      downloadExcel: function (recordId) {
        return download(
          '/water-decision/download?recordId=' + encodeURIComponent(recordId),
          '配水调度明细_' + recordId + '.xlsx'
        );
      }
    }),
    /** 运行管理决策汇总（智能决策接口第三章） */
    operation: {
      summary: function (startDate, endDate) {
        var q = [];
        if (startDate) q.push('startDate=' + encodeURIComponent(startDate));
        if (endDate) q.push('endDate=' + encodeURIComponent(endDate));
        return getJson(
          '/operation/decision-summary' + (q.length ? '?' + q.join('&') : '')
        );
      }
    },
    /** 旱情趋势（复用墒情监测） */
    soilMoisture: {
      sites: function () {
        return getJson('/soil-moisture/sites');
      },
      trend: function (stcd, startTime, endTime) {
        var q = ['stcd=' + encodeURIComponent(stcd)];
        if (startTime) q.push('startTime=' + encodeURIComponent(startTime));
        if (endTime) q.push('endTime=' + encodeURIComponent(endTime));
        return getJson('/soil-moisture/trend?' + q.join('&'));
      }
    }
  };

  global.ModelApi = ModelApi;
})(typeof window !== 'undefined' ? window : globalThis);
