# MQTT Publisher Android App

## 功能说明

- **主界面**：显示 MQTT 连接状态、服务器/Topic/间隔信息、累计发送条数，以及最近 20 条发布记录
- **开始/停止**：一键控制发布服务
- **设置界面**：修改所有 MQTT 参数（服务器、端口、用户名、密码、Topic、QoS、Retained）
- **发送间隔**：滑动条（1~3600 秒）+ 直接输入框，双向联动
- **后台服务**：使用前台服务保持运行，状态栏有通知显示

## 默认配置

| 参数 | 默认值 |
|------|--------|
| 服务器 | （首次使用请在设置中配置） |
| 端口 | 1883 |
| Topic | （首次使用请在设置中配置） |
| Payload 格式 | `YYYYMMDD-HHmmss_N`（N=发送次数）|
| ClientID 格式 | `设备型号_YYYYMMDD-HHmmss` |
| 间隔 | 5 秒 |
| QoS | 1 |

## 导入步骤

1. 打开 Android Studio
2. File → Open → 选择此目录 `MqttPublisher/`
3. 等待 Gradle 同步完成
4. 连接安卓设备（开启 USB 调试）
5. 点击 Run ▶ 即可安装

## 依赖库

- `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5`
- Material Components, AndroidX

## 注意事项

- Android 13+ 需要允许通知权限（首次运行时系统会弹窗询问）
- 首次使用请在设置中配置 MQTT 服务器地址、端口和 Topic
