# 沉浸陪伴聊天 APK

这是一个本地 Android 项目，用于连接 OpenAI 兼容接口，例如 API Mart。

## 已实现功能

- 沉浸式陪伴聊天界面
- 设置页填写 API Base URL，默认 `https://apimart.ai/v1`
- 本地填写并保存 API Key
- 聊天模型手动填写
- 图片模型手动填写
- 支持调用 `/v1/models` 获取模型列表，并选择聊天模型或图片模型
- 支持 OpenAI 兼容 `/v1/chat/completions`
- 支持 OpenAI 兼容 `/v1/images/generations`
- 角色卡可编辑：角色名称、角色设定、图片风格
- 聊天记录本地保存
- 长期记忆本地保存，可手动编辑或清空
- 自动根据聊天内容生成图片
- 手动点击“生成图”从最近聊天提取图片提示词

## 重要说明

API Key 目前保存在手机本地 `SharedPreferences` 中，适合个人使用。不要把这个版本直接发给别人使用同一个 Key。

“文本 + 图片”采用两步调用：

1. 聊天模型生成角色扮演回复。
2. 后台调用聊天模型提取图片提示词和长期记忆，再调用图片模型。

这样不会让角色回复被 JSON 格式污染。

## 打包 APK

需要本机安装：

- JDK 17
- Android Studio 或 Android SDK
- Gradle，或使用 Android Studio 自带构建

用 Android Studio 打开本目录：

```text
C:\Users\Admin（无密码）\Documents\口播\ImmersiveCompanionChat
```

然后执行：

```text
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

或在命令行中执行：

```powershell
gradle assembleDebug
```

生成路径通常是：

```text
app\build\outputs\apk\debug\app-debug.apk
```

## 首次使用

打开 APK 后进入“设置”：

- Base URL：`https://apimart.ai/v1`
- API Key：填写你的 API Mart Key
- 聊天模型：填写或从 `/models` 拉取后选择
- 图片模型：填写或从 `/models` 拉取后选择
- 角色设定：按你的角色扮演需求修改

