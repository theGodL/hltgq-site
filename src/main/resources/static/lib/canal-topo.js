/**
 * 渠系拓扑图共用：挂载 SVG 底图 + 拖拽缩放
 * 用法：
 *   CanalTopo.mount('#topo-mount').then(function (api) {
 *     // api.fitToScreen(); api.getSvg(); api.getVarLayer();
 *   });
 */
(function (global) {
  'use strict';

  var FRAGMENT_URL = 'canal-topo.fragment.html';
  var BASE_W = 1880;
  var BASE_H = 1020;

  function resolveUrl(rel) {
    try {
      var scripts = document.getElementsByTagName('script');
      for (var i = scripts.length - 1; i >= 0; i--) {
        var src = scripts[i].src || '';
        if (src.indexOf('canal-topo.js') >= 0) {
          return new URL(rel, src).href;
        }
      }
    } catch (e) { /* ignore */ }
    return './lib/' + rel.replace(/^\.\//, '');
  }

  function initPanZoom(root, opts) {
    opts = opts || {};
    var vp = root.querySelector('.topo-viewport') || root.querySelector('#topo-viewport');
    var canvas = root.querySelector('.topo-canvas') || root.querySelector('#topo-canvas');
    var svg = root.querySelector('.topo-svg') || root.querySelector('#topo-svg');
    if (!vp || !canvas || !svg) {
      return { fitToScreen: function () {}, getSvg: function () { return null; }, getVarLayer: function () { return null; } };
    }
    svg.setAttribute('viewBox', '0 0 ' + BASE_W + ' ' + BASE_H);
    var x = 0, y = 0, scale = 1;
    var dragging = false, sx = 0, sy = 0, ox = 0, oy = 0;

    function apply() {
      canvas.style.transform = 'translate(' + x + 'px,' + y + 'px) scale(' + scale + ')';
    }
    function fitToScreen() {
      var rect = vp.getBoundingClientRect();
      if (!rect.width || !rect.height) return;
      var pad = 16;
      var next = Math.min((rect.width - pad * 2) / BASE_W, (rect.height - pad * 2) / BASE_H);
      scale = Math.max(0.15, Math.min(2.4, next));
      x = (rect.width - BASE_W * scale) / 2;
      y = (rect.height - BASE_H * scale) / 2;
      apply();
    }

    fitToScreen();

    vp.addEventListener('pointerdown', function (e) {
      if (e.button !== 0) return;
      dragging = true;
      vp.classList.add('is-dragging');
      sx = e.clientX; sy = e.clientY; ox = x; oy = y;
      vp.setPointerCapture(e.pointerId);
    });
    vp.addEventListener('pointermove', function (e) {
      if (!dragging) return;
      x = ox + (e.clientX - sx);
      y = oy + (e.clientY - sy);
      apply();
    });
    function endDrag(e) {
      dragging = false;
      vp.classList.remove('is-dragging');
      try { vp.releasePointerCapture(e.pointerId); } catch (err) { /* ignore */ }
    }
    vp.addEventListener('pointerup', endDrag);
    vp.addEventListener('pointercancel', endDrag);
    vp.addEventListener('wheel', function (e) {
      e.preventDefault();
      var rect = vp.getBoundingClientRect();
      var mx = e.clientX - rect.left;
      var my = e.clientY - rect.top;
      var prev = scale;
      var next = Math.min(2.4, Math.max(0.15, scale * (e.deltaY < 0 ? 1.1 : 0.91)));
      x = mx - (mx - x) * (next / prev);
      y = my - (my - y) * (next / prev);
      scale = next;
      apply();
    }, { passive: false });

    window.addEventListener('resize', function () {
      if (typeof opts.shouldFit === 'function' && !opts.shouldFit()) return;
      fitToScreen();
    });
    document.addEventListener('fullscreenchange', function () {
      setTimeout(fitToScreen, 50);
      setTimeout(fitToScreen, 200);
    });

    return {
      fitToScreen: fitToScreen,
      getSvg: function () { return svg; },
      getVarLayer: function () { return svg.querySelector('#topo-var-layer'); },
      getWrap: function () { return root.querySelector('.topo-wrap') || root; }
    };
  }

  function mount(target, opts) {
    opts = opts || {};
    var el = typeof target === 'string' ? document.querySelector(target) : target;
    if (!el) return Promise.reject(new Error('CanalTopo mount target not found'));

    var url = opts.fragmentUrl || resolveUrl(FRAGMENT_URL);
    return fetch(url, { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) throw new Error('加载拓扑图失败: ' + res.status);
        return res.text();
      })
      .then(function (html) {
        el.innerHTML = html;
        return initPanZoom(el, opts);
      });
  }

  global.CanalTopo = {
    mount: mount,
    initPanZoom: initPanZoom,
    BASE_W: BASE_W,
    BASE_H: BASE_H
  };
})(typeof window !== 'undefined' ? window : globalThis);
