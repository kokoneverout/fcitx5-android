# MiMo LALM 语音转写

在语音输入设置中选择 **LALM** 后端，填写：

- API URL：`https://api.xiaomimimo.com/v1`
- API Key：你自己的 API Key
- 模型：`mimo-v2.6-flash`
- 音频格式：原有 `input_audio` 会根据 MiMo 模型名称自动使用 Data URL；也可显式选择 `input_audio（Data URL / MiMo）`，适用于模型别名代理。
- 深度思考模式：默认“自动”，对 MiMo V2.6 发送 `thinking.type=disabled`，其他模型不额外发送参数。可手动选择开启或关闭。

`input_audio` 的 MiMo 请求形式为：

```json
{
  "type": "input_audio",
  "input_audio": {
    "data": "data:audio/wav;base64,..."
  }
}
```

其他模型仍使用原有的纯 Base64 + `format` 形式；显式选择 `audio_url` 时保持该协议。

## 排查空文本或接口错误

1. 开启“保存转录历史”。
2. 开启 LALM 分组中的“保存 LALM API 调试响应”（默认关闭）。
3. 重新进行转写，在转录历史详情的“原始响应”中查看完整 JSON。
4. 检查 `choices[0].message.content`、`reasoning_content`、`finish_reason` 和 `usage`。HTTP 错误或无法解析的响应也会保留原始响应体，错误状态中包含 HTTP 状态码。
5. 排查后关闭调试开关。关闭后不再保存新响应，已有响应会在下次历史清理或应用重启时清除；也可以在转录历史中删除记录。

调试响应仅保存在本机，遵循现有最多 100 条、30 天的历史保留规则。响应可能包含私人文本和思考内容。请求的 API Key、音频 Base64、截图和提示词不会由此功能额外记录；不要把含有私人信息的响应直接公开。

`content` 为 `null`、缺失或空字符串时保留空结果以供重试，不会用模型的思考内容替代转写。支持字符串和文本内容数组。

## 验证

推荐在 GitHub Actions 的 **Android APK and tests** 工作流中验证。推送到 `master`、`fix/**` 分支会自动运行；工作流进入默认分支后也可手动启动。工作流先执行单元测试，再生成可安装的 ARM64 Debug APK，下载构建产物 `voicehub-arm64-v8a-debug` 即可，无需在本机配置 Android 构建环境。

```text
./gradlew :app:testDebugUnitTest --tests '*LalmApiTest' --tests '*VoiceBackendConfigurationTest'
```

测试覆盖音频格式、MiMo 自动识别、思考模式、正常/空/无效响应、调试开关和 HTTP 错误响应体。真实识别质量和延迟需要使用你自己的 API Key 和录音在设备上验证。

## 官方文档

- [音频理解：Data URL 与 MIME 类型](https://mimo.mi.com/docs/en-US/quick-start/usage-guide/multimodal-understanding/audio-understanding)
- [深度思考：thinking.type](https://mimo.mi.com/docs/en-US/quick-start/usage-guide/other/deep-thinking)
