# 大肥鱼笔记

一个极简的本地笔记 App：TXT 文件管理 + 配图 + 词频统计。

## 功能

- **新建 / 编辑 / 重命名 / 删除** TXT 笔记
- **给每篇笔记设置配图**（存为 `笔记名_cover.png`，与 TXT 同目录）
- **自动保存**（停止输入 1.5 秒后）
- **元数据展示**：创建时间、文件大小、字数、修改次数
- **词频统计**：
  - 中文按 2-gram / 3-gram 滑动窗口
  - 英文按单词
  - 结果按出现次数从多到少排序
  - 可切换「含停用词」、设置最低出现次数（2/3/5）

## 文件存放位置

```
/sdcard/Documents/大肥鱼笔记/
    我的笔记.txt
    我的笔记_cover.png      ← 配图
```

元数据（创建时间、编辑次数）存在应用私有目录的 `notes_meta.json`。

## 构建

### 方式一：GitHub Actions（推荐）

1. 把整个 `NotepadApp` 目录推到 GitHub 仓库
2. 进入 **Actions** 标签页，等待 `Build APK` 跑完
3. 在运行结果的 **Artifacts** 里下载 `dafeiyu-notepad-debug`
4. 解压得到 `app-debug.apk`，传到手机安装

推第一次就会自动触发构建，之后每次 `push` 都会重新编译。

### 方式二：本地构建（需要有 Android SDK 的机器）

```bash
gradle assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

## 技术栈

- Kotlin + AndroidX
- Material 3
- 最低支持 Android 7.0（API 24）
- 目标 SDK 34

## 已知限制

- 中文分词用 n-gram 近似，会有「的了」「我们」这类噪声（已内置停用词表过滤）
- 配图仅支持一张封面图，不是富文本内嵌图片
- 元数据存在应用私有目录，卸载 App 会丢失统计（TXT 本身不会丢）
