## 🌍 Select your language | 选择语言 

New feature: 🎼 Smart Music Controller rear widget (check more details bellow)

- <details>
    <summary>English</summary>

      # MiRearScreenSwitcher (MRSS)
      
      It is a "one-click switch for the back screen of dual-screen devices such as Xiaomi 17Pro/17Pro Max"
      
      ## 📄 Open source protocol
      
      ** ⁇ ️ Notification of important agreement changes**
      
      - **V3.0.0 and later**: Adopts **GPL-3.0** open source protocol
      - **V3.0.0 previous versions**: Still protected by **MIT** protocol
      
      This change is designed to protect the core innovations of the project while maintaining the open source spirit. All versions before V3.0.0 will continue to be freely used in accordance with the MIT protocol.
      
      **💬 Communication & Support**
      - QQ communication group: **932738927** - [Join group chat](https://tgwgroup.ltd/2025/10/21/%e5%85%b3%e4%ba%8emrss%e4%ba%a4%e6%b5%81%e7%be%a4/)
      - Reward support: [Invite the author to a cup of coffee](https://tgwgroup.ltd/2025/10/19/%e5%85%b3%e4%ba%8e%e6%89%93%e8%b5%8f/) ☕
      
      ---
      
      ## ✨ Functional Features (V3.2.0)
      
      - 🎯 **Quick Switch**: Switch the application to the back screen with one click through the control center shortcut switch
      - 📸 **Back screenshot**: Take the back screen with one click and save it to the photo album (automatically close the control center, keycode wake-up)
      - 📹 **Back screen recording**: Floating window control, record the back screen and save it to the Movies folder (continue to wake up in keycode)
      - ⚡ **Charging animation**: Exquisite charging animation is displayed on the back screen when plugging in (3D lightning + flowing liquid effect)
      - 📢 **Notification push**: When receiving the notification, the notification content will be automatically displayed on the back screen (supports application selection + privacy mode + dynamic overloading)
      - 🔓 **Backend available**: Trigger switching normally even if applied in the background
      - 🚀 **No ROOT** required: Based on Shizuku implementation, no ROOT permissions required
      - 🎨 **Exquisite UI**: Material 3 design, four-color gradient UI, accurate 2.84 super elliptical fillets
      -  ⁇ ️ **Intelligent protection**: Prevents the system Launcher from overwriting the projected application
      - 💡 **The back screen always lights up**: The optional back screen always lights up function prevents the screen from automatically closing
      - 🔄 **Intelligent monitoring**: Automatically clear notifications when the backscreen application exits or switches
      - 📱 **DPI adjustment**: Supports dynamic adjustment of back screen DPI and optimizes display effect (recommended 260-350)
      - 🤚 **Back screen cover detection**: Optional proximity sensor detection function
      - 🔄 **Back screen rotation**: Support back screen 0°/90°/180°/270° rotation, application automatically resurrected
      - 🚫 **Task Hide**: The main application is not displayed in the latest task list to prevent false cleaning
      - 🌐 **URI call**: Supports controlling MRSS (Tasker/MacroDroid, etc.) from external applications through the mrss://protocol
      - 🔔 **Intelligent notification**: Supports follow-up system Do Not Disturb mode and only notifies when the phone is upside down (based on home screen proximity sensor detection)
      - 🎬 **Media Library Integration**: Screenshots and recordings are automatically refreshed to the album for easy viewing
      - 🎼 **Smart Music Controller (Dynamic Rear Widget)**

      Transform your rear display into a fully automated, battery-efficient media hub. The Smart Music Controller listens for media sessions and seamlessly brings your music to the back of your device without any manual setup. 

      **How to Enable:**
      Navigate to the app's main menu and turn on the **"Smart Media Widget"** toggle.

      **Key Features:**
      * **Universal Auto-Detection:** The widget dynamically appears on the rear screen the moment *any* media app starts playing music. 
      * **Auto-Close:** The widget intelligently dismisses itself from the rear screen the moment you close the active media application.
      * **Persistent Lockscreen Support:** Your music controls stay alive and accessible on the rear screen even when your phone is completely locked.
      * **Smart AOD & Battery Optimization:** Built to protect your battery life. After 5-6 seconds of inactivity, the widget automatically enters Doze/AOD mode. The brightness dims and the live progress bar pauses to prevent unnecessary screen refresh drain. 
      * **Wake on Tap:** Simply **double-tap** the rear screen to instantly wake the display and refresh the UI.
      * **Intuitive Gesture Control:** Want it out of the way? **Slide down** on the rear screen to dismiss the widget. It will intelligently re-display itself the next time you interact with your media (like skipping a track or pressing play/pause via your headphones or main screen).
      * **Uninterrupted Notifications:** The widget doesn't block your alerts. You will still receive and see all your regular notifications on the rear screen even while the music controller is active.

      *Feature contributed by [@Kandaraa](https://github.com/Kandaraa)*
      
      ## 📋 Prerequisites for use
      
      1.  **Device requirements**: Support Xiaomi mobile phones with back screens (dual-screen devices such as Xiaomi 17Pro/17Pro Max)
      2.  **Shizuku**: Shizuku needs to be installed and started
         - Download address: [Shizuku official website](https://shizuku.rikka.app/)
         - Startup mode: ADB or wireless debugging
      
      ## 🚀 How to use
      
      ### 1.  Initial Settings
      
      1.  Install MRSS applications
      2.  Make sure Shizuku is started
      3.  Open the MRSS application, grant Shizuku permission
      4.  The waiting status shows "Everything is ready"
      
      ### 2.  Add shortcut switch
      
      1.  Pull down from the top of the screen to open **Control Center**
      2.  Tap the **Edit button**
      3.  Find the following shortcut switch and add:
         - **Switch to back screen**: Place the current application on the back screen
         - **Get the back screenshot**: Take the back screenshot and save it
         - **Back screen recording**: Record back screen (optional)
      4.  Done!
      
      ### 3.  Daily use
      
      **Toggle application to back screen: **
      1.  Open any application you want to switch to the back screen
      2.  Pull Down Control Center
      3.  Click on the "**Switch to Back Screen**" shortcut switch
      4.  The application immediately switches to the back screen and the control center automatically stalls
      5.  Flip the phone to view it on the back screen
      
      **Capture the back screen: **
      1.  Pull Down Control Center
      2.  Click on the "**Get Back Screenshot**" shortcut switch
      3.  The control center automatically folds and the screenshots are saved to the album
      
      **Recording backscreen: **
      1.  Pull Down Control Center
      2.  Click on the "**backscreen recording**" shortcut switch
      3.  A floating window appears, and the recording starts by clicking the red round button
      4.  The button becomes square during recording, click again to stop recording
      5.  Video saved to Movies/MRSS_*.mp4
      
      **Return to home screen: **
      - Method 1: Tap the notification "Tap to switch the application back to the home screen"
      - Method 2: Exit the application on the back screen and the notification will automatically disappear
      
      **Charging animations and notifications: **
      - Charging animation: Charging animation with lightning icon automatically displayed when plugged in, can be turned off in-app
      - Notification push: Enable in the app, select the app you need to push, support privacy mode, do not disturb mode follow and only notify when the phone is upside down, automatic destruction time can be customized (no upper limit)
      
      **Adjust the back screen display: **
      - DPI Adjustment: Set up in the application, recommended 260-350
      - Rotation control: Supports 0°/90°/180°/270° rotation
      - The back screen is always on: Can be switched in the application
      - Back screen cover detection: Can be switched in-app (based on proximity sensor)
      
      **💡 Tips**: 
      -The shortcut switch can still be used normally even if the MRSS application is in the background or is turned off!
      - MRSS does not appear on the most recent task list to avoid false cleanup
      - The back screen is always on function to switch in-app
      - Notifications are cleared automatically when the application exits or switches
      - Charging animations and notification push are both switchable independently
      - Supports calling via URI (mrss://switch? Current=1 etc.
      
      ## 🔧 technology implementation
      
      - **Flutter**: Cross-platform UI framework, Material 3 design, four-color gradient + precise superelliptic fillet
      - **Shizuku**: Provides shell permissions to perform privileged operations
      - **Quick Settings Tile**: Android system-level shortcut switch service (switch/screenshot/screen recording)
      - **ActivityTaskManager**: Display switching via system service calls
      - **Foreground Service + WakeLock**: The front desk service holds a wake-up lock and the optional back screen is always on
      - **NotificationListenerService**: System notification monitoring, push to back screen in real time
      - **Keycode Wakeup**: Precisely wake up the back screen with input keyevent KEYCODE_WAKEUP
      - **Media Scanner**: Automatically refresh media libraries, screenshots and recordings automatically appear in albums
      - **Dynamic Animation Reload**: Notify animation dynamic overload mechanism, support continuous notifications
      - **Rear Animation Manager**: Unified management of charging animations and notification animations to achieve animation interruption
      - **Intelligent monitoring**: Detect back-screen front-end applications every 2 seconds and automatically clear invalid notifications
      - **Charging Monitor**: BroadcastReceiver monitors charging events, triggering backscreen animations
      - **3D animation**: Custom Canvas drawing, nonlinear animation, gravity sensing liquid effect
      - **Proximity sensor detection**: The main screen proximity sensor detects in real time and supports notification function when the phone is upside down
      - **Screenshot & Record**: screenshot of screencap + screenrecord screen recording
      - **URI Protocol**: supports mrss://protocol external calls
      
      ## 📝 permission description
      
      - `moe.shizuku.manager.permission.API_V23`: Shizuku API permissions for performing privileged operations
      - `android.permission.WAKE_LOCK`: Keep the back screen always on
      - `android.permission.FOREGROUND_SERVICE`: Front desk service permissions
      - `android.permission.POST_NOTIFICATIONS`: Notification permissions (Android 13+)
      - `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`: Notification monitoring service (optional)
      - `android.permission.SYSTEM_ALERT_WINDOW`: Floating window permissions (screen recording function required)
      - `android.permission.QUERY_ALL_PACKAGES`: Get the application list (notify function needs)
      - System broadcast reception: Listen `ACTION_POWER_CONNECTED/DISCONNECTED`Charging event
      
      ##  ⁇ ️ Development and construction
      
      ```bash
      # Installation dependency
      flutter pub get
      
      # Build Debug APK
      flutter build apk - debug
      
      # Build Release APK (arm64-v8 a, code obfuscation + resource compression)
      flutter build apk - release - split-per-abi - target-platform android-arm64
      ```
      
      The generated APK is located at: `build/app/outputs/flutter-apk/app-arm64-v8a-release.apk`
      
      ## 🔍 Technical details
      
      ### V3.0 Core Features
      
      1.  **intelligent application switching** 🎯
         - Triggered via Quick Settings Tile shortcut switch
         - Use the `am stack` command to get the front office application
         - Call `service call activity_Task 50`Toggle display
         - Automatic kill system Launcher prevents squeezing
         - Toast prompts to display specific application names
      
      2.  **charging animation** ⚡
         - Charging animation is automatically displayed on the back screen when plugged in
         - 3D glass lightning container + flowing green liquid
         - Gravity-induced real liquid effects
         - Non-linear animation, from 0% fill to current charge
         - The UI icon uses the lightning ⚡ logo, which is more in line with the charging theme
         - Can be switched in-app, supports charging animation to always light mode
      
      3.  **notification push** 📢
         - NotificationListenerService listens for system notifications
         - Select the application that needs to be pushed (supports search, batch selection)
         - Notifications are displayed on the back screen (application icon + title + content)
         - Privacy Mode: Hide the specific content of the notification
         - Fine animation: icon zoom + content fade in
         - Auto-destruction time is customizable (no upper limit, supports any duration)
         - Notify that the switch status is persistent and restored correctly after restarting
      
      4.  **back screen recording** 📹
         - Suspended window control, red round/square button
         - Record backscreen with screenrecord
         - Continuously wake up the back screen to prevent recording interruptions
         - Video saved to Movies/MRSS_*.mp4
         - Dragable floating window position
      
      5.  **Front desk service keeps alive**  ⁇ ️
         - Unified "MRSS Kernel Service" front desk notification
         - Optional back-screen constant light function (FLAG_KEEP_SCREEN_ON)
         - Tap Notifications to quickly return to the home screen
         -The main application is hidden from recent tasks to prevent false cleanup
      
      6.  **Intelligent monitoring and cleaning** 🔄
         - Detect backscreen front desk application status every 2 seconds
         - Automatically stop service and clear notifications when application exits or switches
         - Prevent multiple applications from being put on at the same time
      
      7.  **DPI dynamic adjustment** 📱
         - Support real-time adjustment of backscreen display density
         - Modify with the `wm density` command
         - One-click restore default settings
         - Recommended range: 260-350
      
      8.  **backscreen rotation control** 🔄
         - Support 4 directions: 0°/90°/180°/270°
         - Use `wm user-rotation -d 1` to independently control the back screen
         - Automated resurrection applied after rotation
         - Displays the current rotational state in real time
      
      9.  **Back screen cover detection** 🤚
         - Optional proximity sensor detection function
         - In-app switchable
         
      10.  **URI Protocol Support** 🌐
         - mrss://switch? Current=1 - Switch current application
         - mrss://switch? PackageName=xxx - Toggle Specified Applications
         - mrss://return? Current=1 - Return to home screen
         - mrss://screenshot - Screenshot
         - mrss://config? dpi=xxx&rotation=x - Configuration, rotations incoming from rotation should be 0-3, the same as the adb shell
      
      ### New features for V3.0
      
      - ✅ **Charging animation**: 3D lightning container + gravity-sensitive liquid
      - ✅ **Notification push**: System notification is displayed to the back screen in real time
      - ✅ **Back screen recording**: The floating window controls the recording function
      - ✅ **URI call**: Supports external application control (Tasker, etc.)
      - ✅ **Exquisite UI**: Four-color gradient + super elliptical fillet design
      - ✅ **Code Optimization**: Remove unused code and fix garbled code issues
      
      ### V3.2.0 New Features
      
      - ✅ **Notification only when the phone is reversed**: Detected by the home screen proximity sensor, the back screen notification is only displayed when the phone is reversed
      - ✅ **Sensor detection optimization**: Monitor the home screen proximity sensor in real time to accurately determine the status of the mobile phone
      
      ### V3.1.3 New Features
      
      - ✅ **Multilingual support**: Completely supports Simplified Chinese, Traditional Chinese and English, and automatically follows the system language
      - ✅ **UI detail optimization**: Fix UI display problems in multi-language environments and optimize text line breaks and alignments
      - ✅ **Notification localization**: Notification titles and content support multilingual display
      
      ### V3.1.2 New Features
      
      - ✅ **Application selection optimization**: The selected application is automatically displayed at the top of the list for easy management
      - ✅ **Kill logic optimization**: Optimize the launcher kill strategy and change it to only kill once before the animation starts to avoid frequent operations
      
      ### V3.1.1 New Features
      
      - ✅ **Service survival optimization**: ChargingService adds front desk service survival to prevent being killed by the system
      - ✅ **Unified management of notifications**: All services use unified kernel service notifications to avoid the accumulation of multiple notifications
      - ✅ **Settings Status Fix**: Fix NotificationService settings conflict issue to ensure status consistency
      
      ### V3.1.0 New Features
      
      - ✅ **Charging animation icon optimization**: The charging animation UI icon was changed from a light bulb to a lightning icon, which is more in line with the charging theme
      - ✅ **Notify switch status repair**: Fixed back screen notification switch state persistence problem, state correct recovery after restart
      - ✅ **Notify service status synchronization**: Fixed the problem of notification service switch status synchronization. It can work normally after being turned off and then turned on
      - ✅ **Unlimited automatic destruction time**: Unlock the upper limit of 60 seconds for automatic destruction time of notification, and support customization of any time
      
      ### Performance Optimization
      
      - ✅ code confusion (ProGuard/R8)
      - ✅ Resource compression
      - ✅ contains only the arm64-v8a architecture
      - ✅ APK volume optimization
      
      ## 📄 license
      
      **V3.0.0 and later**: GPL-3.0 License - see [LICENSE] (LICENSE) file for details
      **V3.0.0 previous versions**: MIT License
      
      ---
      
      ## 📝 Update log
      
      ### V3.2.0 (2025)
      
      #### New Features
      - **Notify when only the phone is upside down**: The notification function adds the "Notify when only the phone is upside down" option. By detecting the home screen proximity sensor to determine whether the phone is upside down, the back screen notification is only displayed when the phone is upside down
      
      #### Feature Optimization
      - Replace the "Notify only when the screen is locked" feature with "Notify only when the phone is upside down", using proximity sensors to detect more accurately
      - Optimize notification detection logic and monitor the home screen proximity sensor status in real time
      
      #### Technology Optimization
      - NotificationService Adds Home Screen Proximity Sensor Monitor
      - Optimize the sensor detection algorithm, a distance less than 20% of the maximum distance is considered a cover
      
      ### V3.1.3 (2025)
      
      #### New Features
      - Full multilingual support (Simplified/Traditional/English)
      - Full localization of notification content and Toast messages
      
      #### Optimization
      - Fix UI text overflow in English mode
      - Optimize text display in a multilingual environment
      
      ### V3.1.2 (2025)
      
      #### Feature Optimization
      - In the application selection list, selected applications are automatically displayed at the top, and unselected applications are below
      - Optimize the launcher kill logic of charging animations and notifying animations, instead killing only once before the animation starts
      
      #### Technology Optimization
      - Remove the continuous kill launcher logic from the charging animation's always-on loop
      - Remove the continuous kill launcher logic from the notification animation loop
      - Application of selection list sorting logic optimization, automatic reordering when switching selections
      
      ### V3.1.1 (2025)
      
      #### Problem fix
      - Fixes a feature failure problem caused by ChargingService's lack of front desk service to keep alive
      - Fixes state inconsistencies caused by NotificationService settings conflicts
      - Unify all services using kernel service notifications to avoid notification bar accumulation
      
      #### Technology Optimization
      - ChargingService now uses a unified kernel service notification to keep alive
      - NotificationService Set Load Logic Optimization to Avoid State Conflicts
      - Increased service stability, reducing the probability of being killed by the system
      
      ### V3.1.0 (2025)
      
      #### New Features
      - Charging animation UI icon changed from light bulb to lightning icon
      - Unlock the 60-second limit on automatic destruction time for notifications
      
      #### Problem fix
      - Fixed the problem of persistence of the back screen notification switch state, and the state was restored correctly after restarting
      - Fixed the problem of synchronizing the status of the notification service switch. It can work normally after turning it off and then turning it on
      - Fixes the notifications service to reload the switch status every time it receives a notification
      
      ### V3.0.0 (2025)
      
      #### Major Update
      - Adopts GPL-3.0 open source protocol
      - New charging animation: 3D lightning container + gravity-sensing liquid effect
      - Notification push function: system notifications are displayed to the back screen in real time
      - Back screen recording function: suspended window controls recording
      - URI protocol support: support for external application control (Tasker, etc)
      - Exquisite UI: four-color gradient + super elliptical fillet design
      
      ## 👥 team
      
      ### Author
      **AntiOblivionis**
      - 🎮 QQ: 319641317
      - 📱 Coolan: [@AntiOblivionis](http://www.coolapk.com/u/8158212)
      - 🐙 Github: [GoldenglowSusie](https://github.com/GoldenglowSusie/)
      - 📺 Bilibili: [Rhode Island T0 Mechanic Cheng Shan](https://space.bilibili.com/407059627)
      
      ### Chief Test Officer
      ** Shikizawa**
      - 📱 Coolan: [@Shiokizawa](http://www.coolapk.com/u/4279097)
      - Provides critical test feedback and functional advice
      
      ## 🤖 AI collaborative development
      
      This project was developed by the author together with the following AI assistants:
      - Cursor
      - Claude-4.5-Sonnet
      - GPT-5
      - Gemini-2.5-Pro
      
      ## 🙏 Acknowledgement
      
      - [Shizuku](https://github.com/RikkaApps/Shizuku) - Privileged API support available
      - Flutter Team - Excellent cross-platform framework
      - Xiaomi HyperOS Xiaomi The Paper OS Team - Backscreen function for Xiaomi phones
      
      ---
      
      ## 📜 Copyright statement
      
      ### Icon Attribution
      
      The icons and brand identifications used in this application are attributed as follows
      
      1.  **Application icon**: This application icon directly uses the icon resources in Xiaomi HyperOS system. According to [Xiaomi Operating System User Agreement] (the copyright of trademarks such as https://terms.miui.com/doc/eula/cn.html)，小米、MIUI、Xiaomi HyperOS and related icons belongs to Xiaomi Technology Co., Ltd. This application is only an auxiliary tool developed by a third party and has nothing to do with Xiaomi official. If there is any infringement, please contact us to delete it.
      
      2.  **Kuan icon**: The Kuan icon used in the application belongs to Kuan (Beijing Kuan Network Technology Co., Ltd.). According to [Kuan User Agreement](https://m.coolapk.com/mp/user/agreement)，酷安的商标、图标等知识产权归其所有。本应用使用酷安图标仅用于跳转链接标识，不代表与酷安有任何官方合作关系。
      
      ### Disclaimer
      
      This application is an open source project, based on Shizuku to implement back screen function expansion, for learning and communication only. By using this application you understand and agree that
      - This application is not an official Xiaomi application and has no connection with Xiaomi
      - The risk of using this application is at the user's own risk
      - The developer is not responsible for any loss caused by the use of this application
      - In case of infringement, please contact to delete
      
      ---
</details>

- <details>
    <summary>中文</summary>
         # MiRearScreenSwitcher (MRSS)

         为小米17Pro/17Pro Max等双屏设备的背屏一键切换器

         ## 📄 开源协议

         **⚠️ 重要协议变更通知**

         - **V3.0.0及以后版本**: 采用 **GPL-3.0** 开源协议
         - **V3.0.0以前版本**: 仍受 **MIT** 协议保护

         此变更旨在保护项目的核心创新，同时保持开源精神。V3.0.0以前的所有版本将继续按照MIT协议自由使用。

         **💬 交流与支持**
         - QQ交流群：**932738927** - [加入群聊](https://tgwgroup.ltd/2025/10/21/%e5%85%b3%e4%ba%8emrss%e4%ba%a4%e6%b5%81%e7%be%a4/)
         - 打赏支持：[请作者喝杯咖啡](https://tgwgroup.ltd/2025/10/19/%e5%85%b3%e4%ba%8e%e6%89%93%e8%b5%8f/) ☕

         ---

         ## ✨ 功能特性 (V3.2.0)

         - 🎯 **快捷切换**: 通过控制中心快捷开关一键将应用切换到背屏
         - 📸 **背屏截图**: 一键截取背屏画面并保存到相册（自动收起控制中心，keycode唤醒）
         - 📹 **背屏录屏**: 悬浮窗控制，录制背屏画面并保存到Movies文件夹（持续keycode唤醒）
         - ⚡ **充电动画**: 插电时在背屏显示精美的充电动画（3D闪电+流动液体效果）
         - 📢 **通知推送**: 收到通知时自动在背屏显示通知内容（支持应用选择+隐私模式+动态重载）
         - 🔓 **后台可用**: 即使应用在后台也能正常触发切换
         - 🚀 **无需ROOT**: 基于Shizuku实现，无需ROOT权限
         - 🎨 **精致UI**: Material 3设计，四色渐变UI，精确2.84超椭圆圆角
         - 🛡️ **智能保护**: 防止系统Launcher覆盖投射的应用
         - 💡 **背屏常亮**: 可选的背屏常亮功能，防止自动息屏
         - 🔄 **智能监控**: 背屏应用退出或切换时自动清除通知
         - 📱 **DPI调整**: 支持动态调整背屏DPI，优化显示效果（推荐260-350）
         - 🤚 **背屏遮盖检测**: 可选的接近传感器检测功能
         - 🔄 **背屏旋转**: 支持背屏0°/90°/180°/270°旋转，应用自动复活
         - 🚫 **任务隐藏**: 主应用不在最近任务列表显示，防止误清理
         - 🌐 **URI调用**: 支持通过mrss://协议从外部应用控制MRSS（Tasker/MacroDroid等）
         - 🔔 **智能通知**: 支持跟随系统勿扰模式和仅倒扣手机时通知（基于主屏接近传感器检测）
         - 🎬 **媒体库集成**: 截图和录制自动刷新到相册，方便查看
         - 🎼 **智能音乐控制器** (动态副屏小部件)

            将您的副屏转变为全自动、省电的媒体中心。智能音乐控制器会监听媒体会话，无需任何手动设置即可将音乐控制无缝显示在设备背面。

            **如何启用：**
            进入应用主菜单，打开 **"智能媒体小部件" (Smart Media Widget)** 开关。

            **主要功能：**
            * **全局自动检测：** 当*任何*媒体应用开始播放音乐时，小部件会动态显示在副屏上。
            * **自动关闭：** 当您关闭当前播放的媒体应用时，小部件会自动从副屏退出。
            * **息屏常驻支持：** 即使手机完全锁定，您的音乐控制也会在副屏上保持活动状态并可随时访问。
            * **智能 AOD 与电池优化：** 专为保护电池续航而设计。在无操作 5-6 秒后，小部件会自动进入 Doze/AOD（息屏显示）模式。亮度会降低，实时进度条也会暂停，以防止不必要的屏幕刷新耗电。
            * **点击唤醒：** 只需 **双击** 副屏即可瞬间唤醒显示屏并刷新 UI。
            * **直观的手势控制：** 想要隐藏它？在副屏上 **向下滑动** 即可关闭小部件。当您下次与媒体交互（例如通过耳机或主屏幕切歌、按播放/暂停）时，它会智能地重新显示。
            * **通知无干扰：** 小部件不会屏蔽您的提醒。即使音乐控制器处于活动状态，您依然能在副屏上正常接收并查看所有常规通知。

            *此功能由 [@Kandaraa](https://github.com/Kandaraa) 贡献*

         ## 📋 使用前提

         1. **设备要求**: 支持背屏的小米手机（小米17Pro/17Pro Max等双屏设备）
         2. **Shizuku**: 需要安装并启动Shizuku
            - 下载地址: [Shizuku官网](https://shizuku.rikka.app/)
            - 启动方式: ADB或无线调试

         ## 🚀 使用方法

         ### 1. 初次设置

         1. 安装MRSS应用
         2. 确保Shizuku已启动
         3. 打开MRSS应用，授予Shizuku权限
         4. 等待状态显示"一切就绪"

         ### 2. 添加快捷开关

         1. 从屏幕顶部下拉打开**控制中心**
         2. 点击**编辑按钮**
         3. 找到以下快捷开关并添加：
            - **切换至背屏**: 将当前应用投放到背屏
            - **获取背屏截图**: 截取背屏画面并保存
            - **背屏录制**: 录制背屏画面（可选）
         4. 完成！

         ### 3. 日常使用

         **切换应用到背屏：**
         1. 打开任意想要切换到背屏的应用
         2. 下拉控制中心
         3. 点击"**切换至背屏**"快捷开关
         4. 应用立即切换到背屏，控制中心自动收起
         5. 翻转手机即可在背屏查看

         **截取背屏画面：**
         1. 下拉控制中心
         2. 点击"**获取背屏截图**"快捷开关
         3. 控制中心自动收起，截图保存到相册

         **录制背屏画面：**
         1. 下拉控制中心
         2. 点击"**背屏录制**"快捷开关
         3. 悬浮窗出现，点击红色圆形按钮开始录制
         4. 录制中按钮变为方形，再次点击停止录制
         5. 视频保存到Movies/MRSS_*.mp4

         **返回主屏：**
         - 方法1: 点击通知"点击将应用切换回主屏"
         - 方法2: 在背屏退出应用，通知自动消失

         **充电动画和通知：**
         - 充电动画: 插电时自动显示闪电图标充电动画，可在应用内关闭
         - 通知推送: 在应用内启用，选择需要推送的应用，支持隐私模式、勿扰模式跟随和仅倒扣手机时通知，自动销毁时间可自定义（无上限）

         **调整背屏显示：**
         - DPI调整: 在应用内设置，推荐260-350
         - 旋转控制: 支持0°/90°/180°/270°旋转
         - 背屏常亮: 可在应用内开关
         - 背屏遮盖检测: 可在应用内开关（基于接近传感器）

         **💡 提示**: 
         - 即使MRSS应用在后台或已关闭，快捷开关依然可以正常使用！
         - MRSS不会出现在最近任务列表，避免误清理
         - 背屏常亮功能可在应用内开关
         - 应用退出或切换时，通知会自动清除
         - 充电动画和通知推送都可独立开关
         - 支持通过URI调用（mrss://switch?current=1等）

         ## 🔧 技术实现

         - **Flutter**: 跨平台UI框架，Material 3设计，四色渐变+精确超椭圆圆角
         - **Shizuku**: 提供shell权限执行特权操作
         - **Quick Settings Tile**: Android系统级快捷开关服务（切换/截图/录屏）
         - **ActivityTaskManager**: 通过system service调用实现显示切换
         - **Foreground Service + WakeLock**: 前台服务持有唤醒锁，可选的背屏常亮
         - **NotificationListenerService**: 系统通知监听，实时推送到背屏
         - **Keycode Wakeup**: 使用input keyevent KEYCODE_WAKEUP精确唤醒背屏
         - **Media Scanner**: 自动刷新媒体库，截图和录制自动出现在相册
         - **Dynamic Animation Reload**: 通知动画动态重载机制，支持连续通知
         - **Rear Animation Manager**: 统一管理充电动画和通知动画，实现动画打断
         - **智能监控**: 每2秒检测背屏前台应用，自动清除无效通知
         - **充电监听**: BroadcastReceiver监听充电事件，触发背屏动画
         - **3D动画**: 自定义Canvas绘制，非线性动画，重力感应液体效果
         - **接近传感器检测**: 主屏接近传感器实时检测，支持倒扣手机时通知功能
         - **Screenshot & Record**: screencap截图 + screenrecord录屏
         - **URI Protocol**: 支持mrss://协议外部调用

         ## 📝 权限说明

         - `moe.shizuku.manager.permission.API_V23`: Shizuku API权限，用于执行特权操作
         - `android.permission.WAKE_LOCK`: 保持背屏常亮
         - `android.permission.FOREGROUND_SERVICE`: 前台服务权限
         - `android.permission.POST_NOTIFICATIONS`: 通知权限（Android 13+）
         - `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`: 通知监听服务（可选）
         - `android.permission.SYSTEM_ALERT_WINDOW`: 悬浮窗权限（录屏功能需要）
         - `android.permission.QUERY_ALL_PACKAGES`: 获取应用列表（通知功能需要）
         - 系统广播接收: 监听`ACTION_POWER_CONNECTED/DISCONNECTED`充电事件

         ## 🛠️ 开发构建

         ```bash
         # 安装依赖
         flutter pub get

         # 构建Debug APK
         flutter build apk --debug

         # 构建Release APK (arm64-v8a, 代码混淆+资源压缩)
         flutter build apk --release --split-per-abi --target-platform android-arm64
         ```

         生成的APK位于: `build/app/outputs/flutter-apk/app-arm64-v8a-release.apk`

         ## 🔍 技术细节

         ### V3.0 核心功能

         1. **智能应用切换** 🎯
            - 通过Quick Settings Tile快捷开关触发
            - 使用`am stack`命令获取前台应用
            - 调用`service call activity_task 50`切换显示屏
            - 自动杀死系统Launcher防止挤占
            - Toast提示显示具体应用名

         2. **充电动画** ⚡
            - 插电时自动在背屏显示充电动画
            - 3D玻璃闪电容器 + 流动绿色液体
            - 重力感应真实液体效果
            - 非线性动画，从0%填充到当前电量
            - UI图标使用闪电⚡标识，更符合充电主题
            - 可在应用内开关，支持充电动画常亮模式

         3. **通知推送** 📢
            - NotificationListenerService监听系统通知
            - 选择需要推送的应用（支持搜索、批量选择）
            - 通知内容显示在背屏（应用图标+标题+内容）
            - 隐私模式：隐藏通知具体内容
            - 精美动画：图标缩放+内容淡入
            - 自动销毁时间可自定义（无上限，支持任意时长）
            - 通知开关状态持久化，重启后正确恢复

         4. **背屏录屏** 📹
            - 悬浮窗控制，红色圆形/方形按钮
            - 使用screenrecord录制背屏画面
            - 持续唤醒背屏防止录制中断
            - 视频保存到Movies/MRSS_*.mp4
            - 可拖动悬浮窗位置

         5. **前台Service保活** 🛡️
            - 统一的"MRSS内核服务"前台通知
            - 可选的背屏常亮功能（FLAG_KEEP_SCREEN_ON）
            - 点击通知可快速返回主屏
            - 主应用隐藏于最近任务，防止误清理

         6. **智能监控与清理** 🔄
            - 每2秒检测背屏前台应用状态
            - 应用退出或切换时自动停止服务并清除通知
            - 防止多应用同时投放

         7. **DPI动态调整** 📱
            - 支持实时调整背屏显示密度
            - 使用`wm density`命令修改
            - 一键还原默认设置
            - 推荐范围: 260-350

         8. **背屏旋转控制** 🔄
            - 支持4个方向: 0°/90°/180°/270°
            - 使用`wm user-rotation -d 1`独立控制背屏
            - 旋转后应用自动复活
            - 实时显示当前旋转状态

         9. **背屏遮盖检测** 🤚
            - 可选的接近传感器检测功能
            - 应用内可开关
            
         10. **URI协议支持** 🌐
            - mrss://switch?current=1 - 切换当前应用
            - mrss://switch?packageName=xxx - 切换指定应用
            - mrss://return?current=1 - 返回主屏
            - mrss://screenshot - 截图
            - mrss://config?dpi=xxx&rotation=x - 配置，rotation传入的旋转应为0-3，与adb shell相同

         ### V3.0 新增特性

         - ✅ **充电动画**: 3D闪电容器 + 重力感应液体
         - ✅ **通知推送**: 系统通知实时显示到背屏
         - ✅ **背屏录屏**: 悬浮窗控制录制功能
         - ✅ **URI调用**: 支持外部应用控制（Tasker等）
         - ✅ **精美UI**: 四色渐变 + 超椭圆圆角设计
         - ✅ **代码优化**: 移除未使用代码，修复乱码问题

         ### V3.2.0 新增特性

         - ✅ **仅倒扣手机时通知**: 通过主屏接近传感器检测，仅在手机倒扣时显示背屏通知
         - ✅ **传感器检测优化**: 实时监听主屏接近传感器，准确判断手机状态

         ### V3.1.3 新增特性

         - ✅ **多语言支持**: 完整支持简体中文、繁体中文和英文，自动跟随系统语言
         - ✅ **UI细节优化**: 修复多语言环境下的UI显示问题，优化文本换行和对齐
         - ✅ **通知本地化**: 通知标题和内容支持多语言显示

         ### V3.1.2 新增特性

         - ✅ **应用选择优化**: 选中的应用在列表中自动置顶显示，方便管理
         - ✅ **击杀逻辑优化**: 优化launcher击杀策略，改为仅在动画启动前击杀一次，避免频繁操作

         ### V3.1.1 新增特性

         - ✅ **服务保活优化**: ChargingService 添加前台服务保活，防止被系统杀死
         - ✅ **通知统一管理**: 所有服务使用统一的内核服务通知，避免多个通知堆积
         - ✅ **设置状态修复**: 修复 NotificationService 设置冲突问题，确保状态一致性

         ### V3.1.0 新增特性

         - ✅ **充电动画图标优化**: 充电动画UI图标从灯泡改为闪电图标，更符合充电主题
         - ✅ **通知开关状态修复**: 修复背屏通知开关状态持久化问题，重启后状态正确恢复
         - ✅ **通知服务状态同步**: 修复通知服务开关状态同步问题，关闭后再开启可正常工作
         - ✅ **自动销毁时间无上限**: 解除通知自动销毁时间60秒上限限制，支持自定义任意时长

         ### 性能优化

         - ✅ 代码混淆（ProGuard/R8）
         - ✅ 资源压缩
         - ✅ 只包含arm64-v8a架构
         - ✅ APK体积优化

         ## 📄 许可证

         **V3.0.0及以后版本**: GPL-3.0 License - 详见 [LICENSE](LICENSE) 文件
         **V3.0.0以前版本**: MIT License

         ---

         ## 📝 更新日志

         ### V3.2.0 (2025)

         #### 新功能
         - **仅倒扣手机时通知**: 通知功能新增"仅倒扣手机时通知"选项，通过检测主屏接近传感器判断手机是否倒扣，仅在倒扣时显示背屏通知

         #### 功能优化
         - 将"仅在锁屏时通知"功能改为"仅倒扣手机时通知"，使用接近传感器检测更准确
         - 优化通知检测逻辑，实时监听主屏接近传感器状态

         #### 技术优化
         - NotificationService 新增主屏接近传感器监听器
         - 优化传感器检测算法，距离小于最大距离20%视为遮盖

         ### V3.1.3 (2025)

         #### 新功能
         - 完整的多语言支持（简/繁/英）
         - 通知内容和Toast消息全面本地化

         #### 优化
         - 修复英文模式下UI文本溢出问题
         - 优化多语言环境下的文本显示效果

         ### V3.1.2 (2025)

         #### 功能优化
         - 应用选择列表中，选中的应用自动置顶显示，未选中的应用在下方
         - 优化充电动画和通知动画的launcher击杀逻辑，改为仅在动画启动前击杀一次

         #### 技术优化
         - 移除充电动画常亮循环中的持续击杀launcher逻辑
         - 移除通知动画循环中的持续击杀launcher逻辑
         - 应用选择列表排序逻辑优化，切换选择时自动重新排序

         ### V3.1.1 (2025)

         #### 问题修复
         - 修复 ChargingService 缺少前台服务保活导致的功能失效问题
         - 修复 NotificationService 设置冲突导致的状态不一致问题
         - 统一所有服务使用内核服务通知，避免通知栏堆积

         #### 技术优化
         - ChargingService 现在使用统一的内核服务通知保活
         - NotificationService 设置加载逻辑优化，避免状态冲突
         - 服务稳定性提升，减少被系统杀死的概率

         ### V3.1.0 (2025)

         #### 新功能
         - 充电动画UI图标从灯泡改为闪电图标
         - 解除通知自动销毁时间60秒上限限制

         #### 问题修复
         - 修复背屏通知开关状态持久化问题，重启后状态正确恢复
         - 修复通知服务开关状态同步问题，关闭后再开启可正常工作
         - 修复通知服务每次收到通知时重新加载开关状态

         ### V3.0.0 (2025)

         #### 重大更新
         - 采用GPL-3.0开源协议
         - 全新充电动画：3D闪电容器 + 重力感应液体效果
         - 通知推送功能：系统通知实时显示到背屏
         - 背屏录屏功能：悬浮窗控制录制
         - URI协议支持：支持外部应用控制（Tasker等）
         - 精美UI：四色渐变 + 超椭圆圆角设计

         ## 👥 团队

         ### 作者
         **AntiOblivionis**
         - 🎮 QQ: 319641317
         - 📱 酷安: [@AntiOblivionis](http://www.coolapk.com/u/8158212)
         - 🐙 Github: [GoldenglowSusie](https://github.com/GoldenglowSusie/)
         - 📺 Bilibili: [罗德岛T0驭械术师澄闪](https://space.bilibili.com/407059627)

         ### 首席测试官
         **汐木泽**
         - 📱 酷安: [@汐木泽](http://www.coolapk.com/u/4279097)
         - 提供关键测试反馈和功能建议

         ## 🤖 AI协作开发

         本项目由作者与以下AI助手共同开发：
         - Cursor
         - Claude-4.5-Sonnet
         - GPT-5
         - Gemini-2.5-Pro

         ## 🙏 致谢

         - [Shizuku](https://github.com/RikkaApps/Shizuku) - 提供特权API支持
         - Flutter团队 - 优秀的跨平台框架
         - Xiaomi HyperOS 小米澎湃OS团队 - 小米手机背屏功能

         ---

         ## 📜 版权声明

         ### 图标归属

         本应用使用的图标及品牌标识归属如下：

         1. **应用图标**：本应用图标直接使用了小米HyperOS系统中的图标资源。根据[小米操作系统用户协议](https://terms.miui.com/doc/eula/cn.html)，小米、MIUI、Xiaomi HyperOS等商标及相关图标的版权归小米科技有限责任公司所有。本应用仅为第三方开发的辅助工具，与小米官方无关，如有侵权请联系删除。

         2. **酷安图标**：应用内使用的酷安图标归酷安（北京酷安网络科技有限公司）所有。根据[酷安用户协议](https://m.coolapk.com/mp/user/agreement)，酷安的商标、图标等知识产权归其所有。本应用使用酷安图标仅用于跳转链接标识，不代表与酷安有任何官方合作关系。

         ### 免责声明

         本应用为开源项目，基于Shizuku实现背屏功能扩展，仅供学习交流使用。使用本应用即表示您理解并同意：
         - 本应用非小米官方应用，与小米公司无任何关联
         - 使用本应用的风险由用户自行承担
         - 开发者不对使用本应用造成的任何损失负责
         - 如有侵权，请联系删除

         ---
</details>