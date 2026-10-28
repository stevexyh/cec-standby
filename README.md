# CEC Standby

修补部分 Android 电视收到 HDMI-CEC 待机指令却不关屏的问题，并在唤醒后回到选定 HDMI。

独立包名：`io.github.stevexyh.cecstandby`。源码源自 Aerial 仓库中单独开发的 CEC 补丁 0.6.3，保留 MIT 许可；无需编译 Aerial。版本 0.1.0 是迁移基线，仍有机型硬编码及 ADB 保持依赖，尚未验证新包的真机行为。

- AI 开发入口：[AGENTS.md](AGENTS.md)
- 构建：[Development](docs/DEVELOPMENT.md)
- AI 安装：[Install](docs/INSTALL.md)
- 调试与验收：[Debugging](docs/DEBUGGING.md)、[Acceptance](docs/ACCEPTANCE.md)
- 状态与限制：[Handoff](docs/HANDOFF.md)

构建需要 JDK 17、Android SDK 36 / build-tools 36.0.0：`./build.sh`。构建和默认测试不连接电视。当前仅有旧补丁在单一小米固件上的实测依据，不能据此承诺通用支持或持久稳定。
