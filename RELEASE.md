# Release 构建（kokoneverout fork）

当前版本：`0.1.3-mimo.1`，ARM64 版本号：`112`。

所有编译均在 GitHub Actions 的 **Android Release APK and tests** 中执行。本机只保留源码和发布签名备份，无需配置 Android 构建环境。

1. 仓库的 Actions Secrets 中配置 `SIGN_KEY_BASE64`（PKCS12 密钥文件的 Base64）、`SIGN_KEY_PWD` 和 `SIGN_KEY_ALIAS`。
2. 推送到 `master` 或 `fix/**` 分支，自动运行全套单元测试、Release 构建、签名验证和 SHA-256 文件校验。
3. 在成功运行的 Artifacts 中下载 `voicehub-arm64-v8a-release`，解压后安装 APK。`SHA256SUMS.txt` 为对应文件的校验和。
4. 后续发版沿用同一密钥，并递增 `Versions.kt` 中的 `baseVersionCode`，同时更新工作流中的 `BUILD_VERSION_NAME`。

发布证书 SHA-256：

```text
C3:38:84:CD:E5:97:C0:AF:4D:33:02:B5:2C:D2:20:D2:0B:39:34:D3:1F:66:EE:55:30:2F:F4:36:94:7F:F4:77
```

本 fork 使用独立签名，可与之前的 Debug 测试版并存；无法直接覆盖使用其他签名的上游 Release。密钥和密码须在仓库之外保存并备份，不能提交到 Git 或作为公开构建产物上传。

---

## 上游作者的历史发布记录（以下不适用于本 fork）

本仓库个人线(`heimoshuiyu/fcitx5-android`,master → remote `fork`)的发版流程。
上游 CI(`publish.yml`)只发布 Gradle 构件到 Maven,**APK Release 全程手动**。

## 架构:下载链路

```
用户(国内) → 腾讯云 CDN 边缘节点(dnsv1.com,就近调度)
                 │ 缓存命中 → 直接返回
                 │ 缓存未命中 → 回源 COS 桶(广州, voicehub-apk-1301796004)
```

- 自己的服务器(hmsy-sg / voicehub)**完全不参与下载**。
- 终端用户不直接访问 COS 桶——腾讯禁止用 `*.myqcloud.com` 默认域名公开分发
  APK/IPA(返回 `DownloadForbidden`),必须走绑定的自定义域名(这里即 CDN 域名)。
- 域名 `dl.voicehub.yongyuancv.cn`:DNSPod CNAME → `dl.voicehub.yongyuancv.cn.cdn.dnsv1.com`
  (腾讯云 CDN,大陆区域,下载业务,Range 回源)。**链接目前是 HTTP**(未配证书)。
- CDN 域名归属验证依赖 DNSPod 里的 `_cdnauth` TXT 记录,**不要删**。

## 前置条件

- `local.properties` 里有 `signKeyFile` / `signKeyPwd` / `signKeyAlias`
  (签名块用 `findProperty`,**不读 local.properties**,必须走环境变量)。
- `tccli` 已 `tccli configure`(腾讯云,有 COS/CDN/DNSPod 权限)。
- `gh` 已登录。

## 步骤

### 1. 构建(签名 + 干净版本名)

```bash
cd /home/hmsy/voice/fcitx5-android
export SIGN_KEY_PWD="$(grep '^signKeyPwd=' local.properties | cut -d= -f2-)"
export SIGN_KEY_ALIAS="$(grep '^signKeyAlias=' local.properties | cut -d= -f2-)"
export SIGN_KEY_FILE="$(grep '^signKeyFile=' local.properties | cut -d= -f2-)"
export BUILD_VERSION_NAME="0.1.2-hmsy-N-g<commit-hash>"   # 必须显式指定,否则版本名带 -0-g 后缀
./gradlew :app:assembleRelease
```

产物在 `app/build/outputs/apk/release/`(4 个 ABI 分包)。
不带 `SIGN_KEY_PWD` 构建会得到 **unsigned** APK,注意看文件名。

### 2. 打 tag 并推送

```bash
git tag "0.1.2-hmsy-N-g<commit-hash>" <commit>
git push fork "0.1.2-hmsy-N-g<commit-hash>"
```

tag 名与 `BUILD_VERSION_NAME` 保持一致。

### 3. GitHub Release

```bash
gh release create "0.1.2-hmsy-N-g<commit-hash>" -R heimoshuiyu/fcitx5-android \
  --title "0.1.2-hmsy-N (<short-hash>)" \
  --notes-file <notes.md> \
  app/build/outputs/apk/release/*.apk
```

注意:批量上传可能被网络中断且错误码被吞,**传完用
`gh api repos/heimoshuiyu/fcitx5-android/releases/tags/<tag> --jq '.assets | length'`
确认是 4**,缺了逐个补 `gh release upload <tag> <file> --clobber`。
notes 模板见上一版(Changes since / Download 段)。

### 4. 同步腾讯 COS(国内直链)

```bash
APK=<本次 arm64 包>
tccli cos upload --bucket voicehub-apk-1301796004 \
  --local_path "$APK" --cos_key "apk/voicehub-arm64-release.apk" \
  --content_type "application/vnd.android.package-archive"

# 可选:存版本化副本(所有 ABI)
for f in app/build/outputs/apk/release/*.apk; do
  tccli cos upload --bucket voicehub-apk-1301796004 \
    --local_path "$f" --cos_key "apk/$(basename "$f")" \
    --content_type "application/vnd.android.package-archive"
done
```

`apk/voicehub-arm64-release.apk` 是滚动文件,Release notes 里"国内推荐"指向它。

### 5. 刷 CDN 缓存(必做,否则边缘节点继续发旧包)

```bash
tccli cdn PurgeUrlsCache --Urls '["http://dl.voicehub.yongyuancv.cn/apk/voicehub-arm64-release.apk"]' --FlushType flush
```

### 6. 验证

```bash
curl -sI "http://dl.voicehub.yongyuancv.cn/apk/voicehub-arm64-release.apk" | head -3
# 200 + Content-Type: application/vnd.android.package-archive + Content-Length 正确
```

## 回滚

下载链路回七牛(如果腾讯侧出问题):把 DNSPod 记录
`dl.voicehub`(RecordId 2299728558)的 CNAME 改回
`dl-voicehub-yongyuancv-cn-idvrjzv.qiniudns.com`,TTL 600,约 10 分钟内生效。
七牛侧配置未动过,随时可回。
