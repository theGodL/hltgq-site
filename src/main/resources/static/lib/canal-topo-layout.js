/**
 * 渠系拓扑标注布局（需水 / 配水调度等页共用）
 */
(function (global) {
  'use strict';
  var MAIN_CANAL_LAYOUT = [
    {
      name: '总干渠',
      // 文字起点 (190,286)，横排：数据贴在文字正下方
      cx: 216,
      cy: 286,
      dir: 'h',
      branches: [
        '城关支渠', '幸福支渠', '沙改支渠', '毕岭支渠', '甘庄支渠', '五星支渠', '红旗支渠',
        '叶湾支渠', '华丰支渠', '桥东支渠', '袁林支渠', '黄屋支渠', '金桥支渠', '联合(1)支渠',
        '杨树支渠', '中沟支渠', '郭塘支渠'
      ]
    },
    {
      name: '太怀干渠',
      // 竖排 rotate(-90 385 180)：文字自下往上，数据贴在文字下端
      cx: 385,
      cy: 180,
      dir: 'v',
      branches: [
        '朝阳支渠', '陈良支渠', '王居山支渠', '邵山支渠', '易潭支渠',
        '新仓支渠', '龙王山支渠', '一安支渠', '高旗支渠'
      ]
    },
    {
      name: '太宿干渠',
      cx: 575,
      cy: 430,
      dir: 'v',
      branches: [
        '杨岭支渠', '龙寨支渠', '小宫支渠', '胜利支渠', '姚圩支渠', '鲁元支渠', '荆安支渠',
        '五里支渠', '牌楼支渠', '新民支渠', '南岭支渠', '东方支渠', '新安支渠', '白马支渠',
        '万元支渠', '毛坝支渠', '明光支渠', '红雨支渠', '新前支渠', '木梓支渠',
        '程岭支渠', '杨肖支渠', '彭桥支渠', '矮脚支渠', '胜天支渠', '碎石支渠',
        '云集支渠', '立新支渠', '署光支渠', '金塘支渠'
      ]
    },
    {
      name: '北干渠',
      cx: 1830,
      cy: 286,
      dir: 'h',
      branches: [
        '雷埠支渠', '红星支渠', '三联支渠', '新坝支渠', '南川支渠',
        '红岭支渠', '武昌支渠', '赛口支渠', '南坂支渠'
      ]
    },
    {
      name: '南干渠',
      cx: 1765,
      cy: 556,
      dir: 'h',
      branches: [
        '姑塘支渠', '联合(2)支渠', '永兴支渠', '李桥支渠', '大桥支渠', '团山支渠',
        '乔方支渠', '横山支渠', '十里支渠', '白林支渠', '太寺支渠'
      ]
    }
  ];

  var BRANCH_LAYOUT = [
      // 总干 ↑
      { name: '城关支渠', cx: 185, tip: 188, place: 'up', fs: 10 },
      { name: '幸福支渠', cx: 235, tip: 188, place: 'up', fs: 10 },
      { name: '沙改支渠', cx: 285, tip: 188, place: 'up', fs: 10 },
      // 腊皖 ↓↑
      { name: '朝阳支渠', cx: 560, tip: 147, place: 'down', fs: 10 },
      { name: '陈良支渠', cx: 650, tip: 147, place: 'down', fs: 10 },
      { name: '王居山支渠', cx: 780, tip: 147, place: 'down', fs: 10 },
      { name: '邵山支渠', cx: 960, tip: 147, place: 'down', fs: 10 },
      { name: '易潭支渠', cx: 1060, tip: 147, place: 'down', fs: 10 },
      { name: '新仓支渠', cx: 600, tip: 47, place: 'upSide', fs: 10 },
      { name: '龙王山支渠', cx: 740, tip: 47, place: 'upSide', fs: 10 },
      { name: '一安支渠', cx: 860, tip: 47, place: 'upSide', fs: 10 },
      { name: '高旗支渠', cx: 940, tip: 47, place: 'upSide', fs: 10 },
      // 太宿水平
      { name: '杨岭支渠', cx: 660, tip: 278, place: 'hRight', fs: 10 },
      { name: '龙寨支渠', cx: 515, tip: 296, place: 'hLeft', fs: 10 },
      { name: '小宫支渠', cx: 500, tip: 361, place: 'hLeft', fs: 10 },
      { name: '红旗支渠', cx: 668, tip: 428, place: 'hRight', fs: 10, key: 'hq_ts' },
      { name: '胜利支渠', cx: 500, tip: 446, place: 'hLeft', fs: 10 },
      { name: '姚圩支渠', cx: 668, tip: 464, place: 'hRight', fs: 10 },
      { name: '鲁元支渠', cx: 500, tip: 482, place: 'hLeft', fs: 10 },
      { name: '荆安支渠', cx: 678, tip: 586, place: 'hRight', fs: 10 },
      // 长马 ←
      { name: '五里支渠', cx: 290, tip: 651, place: 'hLeft', fs: 10 },
      { name: '牌楼支渠', cx: 290, tip: 681, place: 'hLeft', fs: 10 },
      { name: '新民支渠', cx: 290, tip: 711, place: 'hLeft', fs: 10 },
      // 乔木 ↓
      { name: '南岭支渠', cx: 575, tip: 796, place: 'down', fs: 9 },
      { name: '东方支渠', cx: 535, tip: 796, place: 'down', fs: 9 },
      { name: '新安支渠', cx: 495, tip: 796, place: 'down', fs: 9 },
      { name: '白马支渠', cx: 450, tip: 796, place: 'down', fs: 9 },
      { name: '万元支渠', cx: 330, tip: 796, place: 'down', fs: 9 },
      { name: '毛坝支渠', cx: 290, tip: 796, place: 'down', fs: 9 },
      { name: '明光支渠', cx: 250, tip: 796, place: 'down', fs: 9 },
      { name: '红雨支渠', cx: 210, tip: 796, place: 'down', fs: 9 },
      { name: '新前支渠', cx: 170, tip: 796, place: 'down', fs: 9 },
      { name: '木梓支渠', cx: 130, tip: 796, place: 'down', fs: 9 },
      { name: '程岭支渠', cx: 678, tip: 765, place: 'hRight', fs: 10 },
      { name: '杨肖支渠', cx: 678, tip: 800, place: 'hRight', fs: 10 },
      // 右折 / 下仓
      { name: '彭桥支渠', cx: 660, tip: 900, place: 'down', fs: 9 },
      { name: '矮脚支渠', cx: 710, tip: 900, place: 'down', fs: 9 },
      { name: '胜天支渠', cx: 770, tip: 774, place: 'up', fs: 9 },
      { name: '碎石支渠', cx: 800, tip: 906, place: 'down', fs: 9 },
      { name: '云集支渠', cx: 1020, tip: 774, place: 'up', fs: 9 },
      { name: '立新支渠', cx: 1130, tip: 906, place: 'down', fs: 9 },
      { name: '朝阳支渠', cx: 1180, tip: 906, place: 'down', fs: 9, key: 'zy_xc' },
      { name: '杨树支渠', cx: 1230, tip: 906, place: 'down', fs: 9, key: 'ys_xc' },
      { name: '署光支渠', cx: 1280, tip: 900, place: 'down', fs: 9 },
      { name: '金塘支渠', cx: 1330, tip: 774, place: 'up', fs: 9 },
      // 总干下行密集段
      { name: '毕岭支渠', cx: 700, tip: 312, place: 'down', fs: 10 },
      { name: '甘庄支渠', cx: 780, tip: 312, place: 'down', fs: 10 },
      { name: '五星支渠', cx: 814, tip: 312, place: 'down', fs: 10 },
      { name: '红旗支渠', cx: 848, tip: 312, place: 'down', fs: 10, key: 'hq_zg' },
      { name: '叶湾支渠', cx: 882, tip: 312, place: 'down', fs: 10 },
      { name: '华丰支渠', cx: 916, tip: 312, place: 'down', fs: 10 },
      { name: '桥东支渠', cx: 1010, tip: 312, place: 'down', fs: 10 },
      { name: '袁林支渠', cx: 1044, tip: 312, place: 'down', fs: 10 },
      { name: '黄屋支渠', cx: 1078, tip: 312, place: 'down', fs: 10 },
      { name: '金桥支渠', cx: 1200, tip: 312, place: 'down', fs: 10 },
      { name: '联合(1)支渠', cx: 1234, tip: 312, place: 'down', fs: 10 },
      { name: '杨树支渠', cx: 1268, tip: 312, place: 'down', fs: 10, key: 'ys_bg' },
      { name: '中沟支渠', cx: 1302, tip: 312, place: 'down', fs: 10 },
      { name: '郭塘支渠', cx: 1350, tip: 312, place: 'down', fs: 10 },
      { name: '雷埠支渠', cx: 1390, tip: 188, place: 'up', fs: 11 },
      { name: '红星支渠', cx: 1518, tip: 188, place: 'up', fs: 10 },
      { name: '三联支渠', cx: 1550, tip: 312, place: 'down', fs: 10 },
      { name: '新坝支渠', cx: 1622, tip: 188, place: 'up', fs: 10 },
      { name: '南川支渠', cx: 1654, tip: 312, place: 'down', fs: 10 },
      { name: '红岭支渠', cx: 1686, tip: 188, place: 'up', fs: 10 },
      { name: '武昌支渠', cx: 1718, tip: 312, place: 'down', fs: 10 },
      { name: '赛口支渠', cx: 1750, tip: 312, place: 'down', fs: 10 },
      { name: '南坂支渠', cx: 1782, tip: 312, place: 'down', fs: 10 },
      // 南干
      { name: '姑塘支渠', cx: 1004, tip: 508, place: 'hAbove', fs: 12 },
      { name: '联合(2)支渠', cx: 1210, tip: 456, place: 'up', fs: 10 },
      { name: '永兴支渠', cx: 1255, tip: 456, place: 'up', fs: 10 },
      { name: '李桥支渠', cx: 1300, tip: 584, place: 'down', fs: 10 },
      { name: '大桥支渠', cx: 1345, tip: 584, place: 'down', fs: 10 },
      { name: '团山支渠', cx: 1445, tip: 584, place: 'down', fs: 10 },
      { name: '乔方支渠', cx: 1490, tip: 456, place: 'up', fs: 10 },
      { name: '横山支渠', cx: 1535, tip: 456, place: 'up', fs: 10 },
      { name: '十里支渠', cx: 1580, tip: 584, place: 'down', fs: 10 },
      { name: '白林支渠', cx: 1625, tip: 456, place: 'up', fs: 10 },
      { name: '太寺支渠', cx: 1670, tip: 456, place: 'up', fs: 10 }
    ];

  var api = global.CanalTopo || {};
  api.MAIN_CANAL_LAYOUT = MAIN_CANAL_LAYOUT;
  api.BRANCH_LAYOUT = BRANCH_LAYOUT;
  global.CanalTopo = api;
})(typeof window !== 'undefined' ? window : globalThis);
