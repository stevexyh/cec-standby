# CEC Standby

给特定 Android 电视固件修补 HDMI-CEC 待机问题，并在唤醒后回到选定 HDMI。工具运行在电视上，不需要电脑常驻，也不需要安装 Aerial。

- 包名：`io.github.stevexyh.cecstandby`
- 当前源码版本：**0.2.0 / versionCode 2**
- 0.1.0 是从 [Aerial](https://github.com/pengchujin/aerial-tv) 仓库中独立 CEC 补丁 0.6.3 脱敏迁移的基线；0.2.0 加入机型配置与独立 ADB 恢复。保留上游 MIT 许可。
- **新包尚未真机验收。** 已通过本地构建与离线测试；旧补丁的真机结果不能替代新包的验证，也不能证明长期稳定。

## 能做什么

JSON 档案配置 CEC vendor、HDMI 端口、输入标识、原生播放器和允许跳转的桌面。安装前及运行时严格匹配厂商、型号、Android API 和固件版本；不匹配就不执行电源动作。未知设备只支持诊断，不会自动猜测设置。

目前只实现 Android 9/API 28 的系统接口和小米式日志解析。内置小米 ASTP0 档案供后续实测；配置化不等于通用兼容。电视需支持网络 ADB 的 5555 端口，且允许给本应用单独授权；Android 11+ 无线调试不在支持范围内。

## 本地构建与测试

需要 Python 3.9+、JDK 17、Android SDK platform 36 / build-tools 36.0.0；`JAVA_HOME` 和 `ANDROID_HOME` 可配置。先把 JDK 的 bin 加入 PATH。

```sh
./build.sh
./tools/test.sh
```

构建产物在 `build/cec-standby.apk`，开发签名在被忽略的 `.keys/`；两者不提交。构建和测试不会连接或发现电视。

## AI 安装入口

将 [docs/INSTALL.md](docs/INSTALL.md) 提供给能执行本地命令的 AI，说明目标电视和授权范围。具体入口如下；仅在需要操作电视时执行：

```sh
python3 tools/device.py diagnose --serial '<ADB-SERIAL>'
python3 tools/device.py install --serial '<ADB-SERIAL>' --profile profiles/xiaomi-astp0.json --keep-adb
```

使用已有的 ADB 连接，默认 server 端口 5037，可用 `--server-port` 指定。`--keep-adb` 是显式保持 ADB 的选择，模板默认关闭。需要在电视上接受本应用自己的调试授权，无法复用旧包的私钥。本工具不充当默认桌面、不安装系统服务、不修改广告开关。

## 开发与恢复

- [AGENTS.md](AGENTS.md)：AI 开发必读顺序和操作边界
- [Architecture](docs/ARCHITECTURE.md)、[Development](docs/DEVELOPMENT.md)：实现、配置格式、构建与签名
- [Debugging](docs/DEBUGGING.md)、[Acceptance](docs/ACCEPTANCE.md)：排障与本地/真机验收
- [Handoff](docs/HANDOFF.md)：当前交付状态和未验证项
- [Install](docs/INSTALL.md)：安装、迁移、卸载与设置恢复

真实 IP、序列号、原始日志、安装前设置备份只放被忽略的 `local/`。卸载只清理自身内容；恢复原 ADB 开关是单独的显式步骤，可能中断连接。
