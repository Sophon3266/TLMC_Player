# TLMC Player

一个用于浏览和播放 WebDAV 服务器上音乐的 Android 应用。

## 功能特性

- **文件浏览**: 连接 WebDAV 服务器，浏览目录结构
- **文件搜索**: 从当前目录递归搜索，最多返回 200 条结果
- **音乐播放**: 支持 FLAC、MP3、WAV 格式
- **CUE 分割**: 自动检测同名 CUE 文件，按曲目分割播放
- **图片查看**: 支持 PNG、JPG、TIF 格式，支持缩放和平移
- **文本查看**: 支持多种编码 (UTF-8、GBK、Shift-JIS 等)
- **后台播放**: 支持后台播放和通知栏控制

## 构建说明

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.5

### 构建步骤

1. 克隆项目
2. 使用 Android Studio 打开项目
3. 等待 Gradle 同步完成
4. 点击 Run 或使用 `./gradlew assembleDebug`

## 项目结构

```
app/src/main/java/com/tlmc/player/
├── TLMCApplication.kt      # Application 类
├── di/                     # 依赖注入模块
├── data/
│   ├── model/              # 数据模型
│   ├── repository/         # 数据仓库
│   └── webdav/             # WebDAV 客户端
├── ui/
│   ├── browser/            # 文件浏览器
│   ├── player/             # 音乐播放器
│   ├── image/              # 图片查看器
│   └── text/               # 文本查看器
└── util/                   # 工具类
```

## 许可证

MIT License
