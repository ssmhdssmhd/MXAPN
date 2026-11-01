# MXAPN - APN 接入点管理工具

> 自动获取最新接入点 (APN)，支持中国移动、中国联通、中国电信、中国广电四大运营商，推荐最合适的接入点，用户可手动选择导入使用。

[![Build MXAPN APK](https://github.com/ssmhdssmhd/MXAPN/actions/workflows/build.yml/badge.svg)](https://github.com/ssmhdssmhd/MXAPN/actions/workflows/build.yml)

## 功能特性

- 🌐 **网络获取最新 APN** - 自动从 GitHub 仓库拉取最新的接入点数据
- 📱 **四大运营商支持** - 移动、联通、电信、广电全覆盖
- 🎯 **智能推荐** - 根据当前 SIM 卡自动识别运营商，推荐最佳接入点
- 🔄 **手动选择** - 用户可自由切换运营商和接入点
- 📥 **多种导入方式** - 支持直接导入、Root 导入、复制信息、打开系统设置
- 📋 **本地备用数据** - 网络不可用时自动使用本地内置数据

## 支持的运营商及 APN

| 运营商 | MCC | MNC | APN 类型 |
|--------|-----|-----|----------|
| 中国移动 | 460 | 00/02/04/07/08/13 | CMNET(互联网), CMWAP(彩信), IMS(VoLTE), CMIOT(物联网), CMLTE, CMTDS |
| 中国联通 | 460 | 01/06/09 | 3GNET(互联网), 3GWAP(彩信), IMS(VoLTE), WONET, UNINET |
| 中国电信 | 460 | 03/05/11 | CTNET(互联网), CTWAP(彩信), IMS(VoLTE), SOS(紧急), CTLTE, CTM2M(物联网) |
| 中国广电 | 460 | 15/60 | CBNET(互联网), CMWAP(彩信), IMS(VoLTE), CBNET-IOT(物联网) |

## 导入方式说明

Android 系统对 APN 设置有严格权限限制，本应用提供以下导入方式：

1. **直接导入** - 通过 ContentResolver 写入（需系统签名权限，普通应用可能失败）
2. **Root 导入** - 通过 Root 权限执行命令写入（设备需已 Root）
3. **复制信息** - 将 APN 信息复制到剪贴板，用户手动在系统设置中添加
4. **打开设置** - 直接打开系统 APN 设置页面，引导用户手动配置

> 💡 对于大多数未 Root 的设备，推荐使用「复制信息」+「打开设置」的方式手动配置。

## 下载安装

在 GitHub Actions 中自动编译 APK，可在 [Actions 页面](https://github.com/ssmhdssmhd/MXAPN/actions) 下载最新构建产物。

## 版本更新日志

### v0.0.3 (2026-11-02)

**补全所有运营商接入点**

- APN 总数从 13 个扩展到 49 个
- 中国移动：21 个（含所有 MNC 00/02/04/07/08/13 的互联网、彩信、IMS、物联网、LTE、TD 接入点）
- 中国联通：11 个（含 MNC 01/06/09 的互联网、彩信、IMS、WONET、UNINET 接入点）
- 中国电信：10 个（含 MNC 03/11 的互联网、彩信、IMS、SOS、LTE、物联网接入点）
- 中国广电：7 个（含 MNC 15/60 的互联网、彩信、IMS、物联网接入点）
- 新增 IMS (VoLTE)、SOS (紧急)、物联网等专用接入点类型
- 更新协议为 IPV4V6 以支持 5G
- 更新 CarrierDetector MNC 映射表

### v0.0.2 (2026-11-02)

**修复 APK 签名问题**

- 添加 Release APK 签名配置
- 通过 GitHub Secrets 安全存储签名密钥库
- 修复安装时"软件包似乎无效/缺乏开发者证书"的问题
- 优化构建流程，自动验证签名

### v0.0.1 (2026-11-02)

**初始版本发布**

- 实现从网络获取最新 APN 数据功能
- 支持移动、联通、电信、广电四大运营商
- 根据 SIM 卡自动检测当前运营商
- 智能推荐最合适的接入点
- 提供手动选择运营商和 APN 功能
- 支持多种 APN 导入方式（直接导入/Root/复制/打开设置）
- 内置本地备用 APN 数据
- Material Design 3 界面
- GitHub Actions 自动编译 APK

## APN 数据更新

APN 数据存储在仓库根目录的 `apn_data.json` 文件中，应用启动时会从以下地址获取最新数据：

```
https://raw.githubusercontent.com/ssmhdssmhd/MXAPN/main/apn_data.json
```

如需更新 APN 数据，只需修改 `apn_data.json` 并提交到 main 分支即可，应用会自动获取最新数据，无需更新 APK。

## 项目结构

```
MXAPN/
├── app/
│   ├── src/main/
│   │   ├── java/com/mx/apn/
│   │   │   ├── MainActivity.kt          # 主界面
│   │   │   ├── data/
│   │   │   │   ├── ApnData.kt           # APN 数据模型
│   │   │   │   └── ApnRepository.kt     # 数据仓库（网络/本地）
│   │   │   ├── ui/
│   │   │   │   ├── MainViewModel.kt     # 视图模型
│   │   │   │   └── ApnAdapter.kt        # 列表适配器
│   │   │   └── util/
│   │   │       ├── CarrierDetector.kt   # 运营商检测
│   │   │       └── ApnImporter.kt       # APN 导入工具
│   │   ├── res/                         # 资源文件
│   │   └── assets/apn_data.json         # 本地备用数据
│   └── build.gradle
├── apn_data.json                        # APN 数据（远程数据源）
├── .github/workflows/build.yml          # GitHub Actions 编译
└── README.md
```

## 技术栈

- Kotlin
- AndroidX / Material Design 3
- OkHttp (网络请求)
- Gson (JSON 解析)
- MVVM 架构
- RecyclerView
- GitHub Actions (CI/CD)

## 最低系统要求

- Android 5.0 (API 21) 及以上

## License

MIT License
