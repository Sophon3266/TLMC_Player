# TLMC Player

一个用于浏览和播放 WebDAV 服务器媒体文件的 Android 应用，支持音频、视频、图片和文本内容。

## 功能特性

- **文件浏览**: 连接 WebDAV 服务器，浏览目录结构
- **文件搜索**: 从当前目录递归搜索，最多返回 200 条结果
- **音频播放**: 支持播放列表、后台播放和通知栏控制，重开应用自动恢复上次播放列表，并记忆音频上次播放进度
- **音频并行播放策略**: 其他应用开始播放音频时，本应用不会自动停止当前音频播放
- **CUE 分轨播放**: 自动检测同名 CUE 文件，按曲目分割播放
- **歌词支持**: 支持 LRC 歌词解析与同步高亮
- **视频播放**: 支持播放/暂停、进度拖动、倍速、画中画(PiP)、断点续播
- **横竖屏体验**: 视频页支持沉浸式全屏控制
- **图片查看**: 支持缩放和平移
- **文本查看**: 支持多种编码 (UTF-8、GBK、Shift-JIS 等)

## 格式支持

- **音频**: flac, mp3, wav, ogg, m4a, aac
- **视频**: mp4, mkv, webm, mov, avi, m4v, mpg, mpeg, vob, ts, m2ts, mts
- **图片**: png, jpg, jpeg, tif, tiff, bmp, webp
- **文本**: txt, log, md, nfo, ini, cfg, lrc
- **CUE**: cue

说明:

- 对于 vob、m2ts、mts 等格式，实际可播放性受设备硬件解码能力影响。
- DVD 相关能力为导出后的视频文件级播放，不包含 VIDEO_TS/IFO 菜单导航。

## 使用说明

- 点击目录: 进入下一层目录
- 点击音频文件: 可选择单曲播放或整目录加入播放列表
- 点击 CUE 文件: 进入音频播放器并按曲目播放
- 点击视频文件: 进入视频播放器，可使用倍速和画中画
- 点击图片文件: 进入图片查看器
- 点击文本文件: 进入文本查看器

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

```text
app/src/main/java/com/tlmc/player/
├── TLMCApplication.kt      # Application 类
├── di/                     # 依赖注入模块
├── data/
│   ├── model/              # 数据模型
│   ├── repository/         # 数据仓库
│   └── webdav/             # WebDAV 客户端
├── ui/
│   ├── browser/            # 文件浏览器
│   ├── player/             # 音频播放器
│   ├── video/              # 视频播放器
│   ├── image/              # 图片查看器
│   └── text/               # 文本查看器
└── util/                   # 工具类
```

## 许可证

MIT License
