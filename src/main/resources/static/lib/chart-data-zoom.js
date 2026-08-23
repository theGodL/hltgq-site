/**
 * 折线图底部数据集滑块（与雨量弹窗 chartDataZoom 一致）
 * 依赖：echarts 已加载
 */
(function (global) {
  var DATA_ZOOM_BG = '#F0F1F3';
  var ZOOM_LINE_COLOR = '#1890ff';

  function zoomAreaGradient() {
    return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
      { offset: 0, color: 'rgba(24,144,255,0.4)' },
      { offset: 1, color: 'rgba(24,144,255,0.1)' },
    ]);
  }

  function zoomSelectedGradient() {
    return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
      { offset: 0, color: 'rgba(24,144,255,0.55)' },
      { offset: 1, color: 'rgba(24,144,255,0.15)' },
    ]);
  }

  global.CHART_DATA_ZOOM_GRID_BOTTOM = 58;

  global.buildChartDataZoom = function (start, end) {
    start = start == null ? 0 : start;
    end = end == null ? 100 : end;
    return [
      {
        type: 'slider',
        xAxisIndex: 0,
        start: start,
        end: end,
        height: 24,
        bottom: 10,
        showDetail: false,
        backgroundColor: DATA_ZOOM_BG,
        borderColor: '#dcdfe3',
        fillerColor: 'rgba(24, 144, 255, 0.12)',
        dataBackground: {
          lineStyle: { color: ZOOM_LINE_COLOR, width: 1.2 },
          areaStyle: { color: zoomAreaGradient() },
        },
        selectedDataBackground: {
          lineStyle: { color: '#096dd9', width: 1.5 },
          areaStyle: { color: zoomSelectedGradient() },
        },
      },
      {
        type: 'inside',
        xAxisIndex: 0,
        start: start,
        end: end,
      },
    ];
  };
})(typeof window !== 'undefined' ? window : this);
