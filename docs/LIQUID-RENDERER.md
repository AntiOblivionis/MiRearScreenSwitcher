# 原外观液体与重力物理（2026-10-28）

按用户反馈撤回 2026-10-26 的 3D 体积渲染，充电页面重新直接使用 `LightningShapeView`。保留原有的 `#34C759` 纯绿色、两周期小波纹、六个半透明气泡、40 px 底部阴影、黑色背景和中央电量文字。电量填充动画及之前的可靠性修复继续保留。

当前源代码已移除 `PhysicalLiquidView`、`LiquidRenderer`、`LiquidSimulation`、`LiquidVolume` 及 GLSL 资源，不保留隐藏的 3D 渲染路径。历史 APK/证据仍在忽略目录 `artifacts/liquid-20261026/`，本次构建单独保存到 `artifacts/liquid-canvas-20261028/`。

## 新增的运动

- 加速度计的表观加速度映射至背屏和显示旋转坐标；液面可转向四边，支持侧放和倒置。保留平移加速度的影响，所以晃动也会激发运动。
- `LiquidSurface` 使用带阻尼的液面角度响应与 65 个耦合波动节点，产生惯性、回弹和逐渐衰减的波浪。原来的小幅循环波纹仍在。
- 每帧求解裁剪后液体多边形的面积，使旋转和波动不改变电量对应的填充比例。0% 完全为空，100% 填满，不在满电时留下波浪缺口。
- 气泡沿反重力方向上浮，并裁剪在液体内部；运动按时间推进，不因绘制频率而改变速度。
- 平放时屏幕平面内的重力趋近零，保持最后的稳定方向并阻尼残余运动。此版本是二维自由液面近似，不模拟前后玻璃之间的三维流动、飞溅断裂、折射或焦散。

## 工作量与生命周期

物理步长为 1/120 秒，一次最多追赶 8 步；Canvas 绘制目标上限 60 fps。模拟复用固定数组和 Path，不构造粒子体积纹理、GL 帧缓冲或执行光线遍历。传感器请求 50 Hz。页面暂停、隐藏、解绑或释放后注销传感器并停止帧回调；恢复时重置计时，不补算隐藏期间的运动。传感器注册失败保留默认竖直效果，每次可见会话最多尝试一次；旧会话的延迟事件被拒绝。

这些是代码预算，不是 SM8850 实测帧率或耗电结论。

## 本地验证

- `scripts/Test-Core.ps1`：32 项核心断言、15 项连接生命周期断言、24 项液面断言通过。液面覆盖惯性响应、侧放、倒置、平放、连续旋转、近空/近满、面积/边界、非法输入、暂停与 30/60 fps 一致性。
- arm64 Release 和 Android Lint 通过；当前告警数量见随 APK 保存的完整报告。
- 预览使用生产 Java 模型导出的轮廓，浏览器 Canvas 复现原颜色、气泡和阴影。已检查四个方向的静态画面并生成完整翻转视频。浏览器字体和像素栅格不等同于 Android 实机。
- 本次未改动 Dart。先前 Flutter 验证不冒充本次重新执行。
- 尚未在目标 Xiaomi/HyperOS 背屏上验证实际传感器方向、页面生命周期、帧率、温升或功耗。

复现：

```powershell
.\scripts\Test-Core.ps1 -JavaHome '<JDK 17>'
& '<JDK 17>\bin\java.exe' -cp build/core-tests com.tgwgroup.MiRearScreenSwitcher.LiquidRegressionTest snapshots.json
node scripts/Render-Liquid-Preview.cjs snapshots.json preview-output
.\scripts\Build-Local.ps1 -AndroidSdk '<Android SDK>' -JavaHome '<JDK 17>' -Lint
```

预览脚本需要 Node.js、Playwright 和 Chrome；可通过 `MRSS_CHROME` 指定浏览器路径。第四个参数可传 `left,top,right,bottom` 摄像头安全边距。默认 `296,0,0,0` 仅来自仓库里的 dumpsys 示例，非实机测量；文字在安全区域内居中，遮罩和虚线用于说明避让区，不是 App 的新增绘制。App 本身仍通过 `applySafeAreaToText` 使用系统报告的背屏 Cutout 设置电量文字四边 margin；这次修正仅纠正之前预览漏掉安全区域的问题，没有改 APK。

APK 沿用 `3.2.0+9`，没有发行密钥时使用本地 Android 调试签名。
