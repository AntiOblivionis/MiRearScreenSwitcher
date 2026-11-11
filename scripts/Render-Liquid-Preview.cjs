// Canvas preview of production Java contour snapshots; Android rasterization still needs device QA.
// node scripts/Render-Liquid-Preview.cjs <snapshots.json> <output-directory> [left,top,right,bottom]
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const data = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
// Repository dumpsys example, not a measurement of the connected phone. The app uses live insets.
data.safeInsets = (process.argv[4] || '296,0,0,0').split(',').map(Number);
if (data.safeInsets.length !== 4 || data.safeInsets.some(v => !Number.isFinite(v) || v < 0)
    || data.safeInsets[0] + data.safeInsets[2] >= data.width
    || data.safeInsets[1] + data.safeInsets[3] >= data.height) throw new Error('Invalid safe insets');
const output = path.resolve(process.argv[3]);
fs.mkdirSync(output, { recursive: true });
function preview(data) {
  const radii = [6, 4, 3, 3.5, 4.5, 2.5];
  const [left, top, right, bottom] = data.safeInsets;
  const textX = (left + data.width - right) / 2;
  const textY = (top + data.height - bottom) / 2;
  function draw(canvas, state) {
    const c = canvas.getContext('2d'); c.fillStyle = '#000'; c.fillRect(0, 0, data.width, data.height);
    c.beginPath(); state.points.forEach(([x, y], i) => i ? c.lineTo(x, y) : c.moveTo(x, y)); c.closePath();
    c.fillStyle = '#34c759'; c.fill();
    const shadow = c.createLinearGradient(0, data.height - 40, 0, data.height);
    shadow.addColorStop(0, '#00000000'); shadow.addColorStop(.7, '#00000020'); shadow.addColorStop(1, '#00000040');
    c.fillStyle = shadow; c.fill(); c.save(); c.clip(); c.fillStyle = '#ffffff80';
    state.bubbles.forEach(([x, y], i) => { c.beginPath(); c.arc(x, y, radii[i], 0, Math.PI * 2); c.fill(); });
    c.restore(); c.fillStyle = '#fff'; c.font = 'bold 82px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle';
    c.shadowColor = '#00000080'; c.shadowOffsetY = 6; c.shadowBlur = 12;
    c.fillText('42%', textX, textY); c.shadowOffsetY = 0; c.shadowBlur = 0;
    // A diagram of the excluded inset band, not an invented camera shape or APK overlay.
    c.fillStyle = '#000000b0';
    c.fillRect(0, 0, left, data.height); c.fillRect(data.width - right, 0, right, data.height);
    c.fillRect(left, 0, data.width - left - right, top);
    c.fillRect(left, data.height - bottom, data.width - left - right, bottom);
    c.save(); c.strokeStyle = '#aaaaaa90'; c.lineWidth = 2; c.setLineDash([8, 8]);
    c.strokeRect(left, top, data.width - left - right, data.height - top - bottom); c.restore();
    if (left > 150) {
      c.fillStyle = '#bbb'; c.font = '22px sans-serif';
      c.fillText('摄像头避让区', left / 2, data.height / 2 - 15);
      c.fillText('示意，非镜头形状', left / 2, data.height / 2 + 20);
    }
  }
  const labels = {upright: '原外观 · 竖直', tilted: '倾斜 45°', sideways: '侧放 90°', inverted: '倒置 180°'};
  for (const [name, state] of Object.entries(data.states)) {
    const section = document.createElement('section'); const title = document.createElement('h2');
    title.textContent = labels[name]; section.append(title);
    const canvas = document.createElement('canvas'); canvas.id = name; canvas.width = data.width; canvas.height = data.height;
    section.append(canvas); document.querySelector('main').append(section); draw(canvas, state);
  }
  let running = false;
  window.play = async function(record = false) {
    if (running) return; running = true;
    const canvas = document.getElementById('upright'); let recorder; const chunks = [];
    const stream = record ? canvas.captureStream(30) : null;
    const complete = record ? new Promise(resolve => {
      recorder = new MediaRecorder(stream, {mimeType:'video/webm;codecs=vp9',videoBitsPerSecond:2400000});
      recorder.ondataavailable = event => chunks.push(event.data);
      recorder.onstop = async () => {
        stream.getTracks().forEach(track => track.stop());
        const bytes = new Uint8Array(await new Blob(chunks).arrayBuffer());
        let binary = ''; for (const byte of bytes) binary += String.fromCharCode(byte);
        resolve(btoa(binary));
      }; recorder.start();
    }) : null;
    const start = performance.now();
    await new Promise(resolve => {
      function frame(now) {
        const index = Math.max(0, Math.floor((now - start) * .03));
        if (index >= data.frames.length) { resolve(); return; }
        draw(canvas, data.frames[index]); requestAnimationFrame(frame);
      }
      requestAnimationFrame(frame);
    });
    if (recorder) recorder.stop(); running = false;
    return complete ? await complete : null;
  };
  document.querySelector('button').onclick = () => window.play();
  window.previewLayout = {safeInsets:data.safeInsets, textX, textY};
}
const html = `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>MRSS 原外观 + 重力物理</title>
<style>body{background:#151515;color:#ddd;font:16px system-ui;margin:24px}h1{font-size:24px}h2{font-size:17px;font-weight:500}main{display:grid;grid-template-columns:1fr 1fr;gap:20px;max-width:1220px}canvas{width:100%;display:block;border-radius:20px}button{padding:10px 18px;margin-bottom:10px;cursor:pointer}p{color:#aaa}</style>
<h1>恢复原液体效果 · 加入重力与液面物理</h1><p>实际 Java 模型输出轮廓，浏览器 Canvas 预览；非手机实拍。保留原绿色、气泡、阴影与电量文字。</p>
<p>文字按安全区域居中。避让边距（左/上/右/下）：${data.safeInsets.join(' / ')} px。默认取自仓库中的 296 px 示例，非实机测量；遮罩与虚线仅用于说明，App 不绘制它们。</p>
<button>播放完整翻转与回稳</button><main></main><script>(${preview.toString()})(${JSON.stringify(data)})</script></html>`;
fs.writeFileSync(path.join(output, 'preview.html'), html);
(async () => {
  const browser = await chromium.launch({executablePath:process.env.MRSS_CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
  try {
    const page = await browser.newPage({viewport:{width:1280,height:1030},deviceScaleFactor:1});
    page.on('pageerror', error => { throw error; });
    await page.goto('file:///' + path.join(output, 'preview.html').replaceAll('\\','/'));
    const layout = await page.evaluate(() => window.previewLayout);
    if (layout.textX !== (data.safeInsets[0] + data.width - data.safeInsets[2]) / 2
        || layout.textY !== (data.safeInsets[1] + data.height - data.safeInsets[3]) / 2)
      throw new Error('Preview text not centered in safe area');
    fs.writeFileSync(path.join(output, 'layout-check.json'), JSON.stringify(layout, null, 2));
    await page.screenshot({path:path.join(output, 'comparison.png'),fullPage:true});
    const video = await page.evaluate(() => window.play(true));
    fs.writeFileSync(path.join(output, 'motion.webm'), Buffer.from(video, 'base64'));
    console.log('Saved original-style contour preview and gravity motion video.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
