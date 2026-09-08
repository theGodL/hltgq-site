const fs = require('fs');
const lines = fs.readFileSync('src/main/resources/三维系统对接接口.md', 'utf8').split('\n');
lines.forEach((l, i) => {
  if (/^#{1,3} /.test(l)) console.log(i + 1, l);
});
