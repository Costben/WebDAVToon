# 第三方组件与许可声明 / Third-Party Notices

本文件列出 WebDAVToon 分发产物（APK 与 Rust 动态库）中所包含的第三方组件、各自的许可证，以及这些许可证对分发者提出的要求。

## 一、本应用自身的许可

WebDAVToon 以 **GNU General Public License v3.0 or later** 授权：

```
Copyright (C) 2026 Rin Shibuya

This program is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.
```

许可证全文见仓库根目录的 [LICENSE](LICENSE)。

> **为什么不是 MIT**：本应用链接了 `com.belerweb:pinyin4j:2.5.1`（见下节），其源码实际以 GPL-2.0-or-later 授权，属于强 copyleft，因此整个分发产物必须按 GPL 授权。选用 GPLv3 而非 GPLv2 的原因：本应用同时链接了 Apache-2.0 的 AndroidX/okhttp/Glide 等组件，以及 MPL-2.0 的 uniffi，二者都与 GPLv2 不兼容，但与 GPLv3 兼容。

## 二、GPL-2.0-or-later（强 copyleft，决定本应用的授权）

| 组件 | 版本 | 用途 | 证据 |
| :--- | :--- | :--- | :--- |
| `com.belerweb:pinyin4j` | 2.5.1 | 文件夹拼音搜索（`app/src/main/java/erl/webdavtoon/FolderSearchMatcher.kt`） | 源码头部：*"distributed under GNU GENERAL PUBLIC LICENSE (GPL) ... either version 2 of the License, or (at your option) any later version"* |

**注意：该构件的 Maven POM 把许可证标注为 `BSD`，这是错误的。** 其打包的 `net.sourceforge.pinyin4j` 源码头部全部为 GPLv2-or-later 声明（[上游文件](https://raw.githubusercontent.com/belerweb/pinyin4j/master/src/main/java/net/sourceforge/pinyin4j/PinyinHelper.java)），构件内不含任何 LICENSE 文件。上游项目 pinyin4j（SourceForge，作者 Li Min）在其项目页声明为 GPLv2。

同一构件内还打包了 `com.hp.hpl.sparta`（HP SPARTA XML 解析器），其源码头部为 **LGPL-2.1-or-later**（*"Copyright (C) 2002 Hewlett-Packard Company ... GNU Lesser General Public License ... version 2.1 of the License, or (at your option) any later version"*）；该构件的 JAR 内同样不含任何许可文本。

## 三、LGPL-2.1-or-later（弱 copyleft，随 APK 以独立动态库分发）

| 组件 | 版本 | 形态 |
| :--- | :--- | :--- |
| FFmpeg | 3.0.1 | `libavcodec.so` / `libavformat.so` / `libavutil.so` / `libswscale.so` / `libffmpeg_mediametadataretriever_jni.so`，随 `com.github.wseemann:FFmpegMediaMetadataRetriever:1.0.14` 提供，当前仅打包 `arm64-v8a` |
| HP SPARTA | 2003 快照 | 随 `pinyin4j` 打包的 `com.hp.hpl.sparta` 类 |

该 FFmpeg 构件的许可证为 LGPL-2.1-or-later，依据是二进制内嵌的版本串：

```
libavcodec  license: LGPL version 2.1 or later
libavformat license: LGPL version 2.1 or later
libavutil   license: LGPL version 2.1 or later
FFmpeg version 3.0.1
```

其构建配置中显式包含 `--disable-gpl`，未启用任何 GPL 组件（同时 `--disable-encoders`，仅保留 png 编码器）。

**对应源码（Corresponding Source）**：这些原生库以独立 `.so` 文件动态加载，未经修改，可被用户自行替换。对应的完整源码可按以下路径获取：

- FFmpeg 3.0.1 原始源码：<http://ffmpeg.org/releases/ffmpeg-3.0.1.tar.bz2>
- 构建所用的补丁与配置脚本（tag `v1.0.14`）：<https://github.com/wseemann/FFmpegMediaMetadataRetriever/tree/v1.0.14/gradle/fmmr-library/library/src/main/ffmpeg>

LGPL-2.1 许可证全文见 [licenses/LGPL-2.1.txt](licenses/LGPL-2.1.txt)。

## 四、MPL-2.0（文件级 copyleft，不改变本应用的授权）

| 组件 | 版本 | 说明 |
| :--- | :--- | :--- |
| `uniffi` 及其子 crate：`uniffi`、`uniffi_bindgen`、`uniffi_build`、`uniffi_checksum_derive`、`uniffi_core`、`uniffi_macros`、`uniffi_meta`、`uniffi_testing`、`uniffi_udl` | 0.28.3 | 生成 Kotlin 绑定与 JNA 加载器 |
| `webpki-roots` | 0.22.6 | Mozilla CA 根证书数据，经 `async-tls` 引入 |

`uniffi_bindgen` 生成的 Kotlin 绑定已检入仓库：`app/src/main/java/uniffi/rust_core/rust_core.kt`。该文件为未修改的生成产物。

MPL-2.0 的义务：保留版权与许可声明；以二进制形式分发时须告知接收者如何获取其源码（见下方「五、源码获取」）；若修改了 MPL 覆盖的文件，修改后的文件须以 MPL-2.0 公开。本项目未修改上述任何 MPL 组件。

MPL-2.0 许可证全文见 [licenses/MPL-2.0.txt](licenses/MPL-2.0.txt)。

## 五、Apache-2.0

Android 侧主要组件：

- AndroidX / Jetpack Compose（Compose BOM 2026.05.01，Compose 1.11.2、Material3 1.4.0）、`activity-compose` 1.9.3、`lifecycle-*` 2.10.0、`room-*` 2.8.5、`datastore-preferences` 1.1.3、`biometric` 1.1.0、`fragment` 1.8.9、`navigationevent-compose` 1.1.2、`core-ktx`、`savedstate`、`window` 等
- `io.github.suqi8.coui.kmp:coui-ui / coui-icons / coui-preference / coui-core / coui-shader / coui-squircle` 1.1.0（COUI 设计体系，Miuix 的派生项目）
- `com.github.bumptech.glide:glide / okhttp3-integration / gifdecoder / disklrucache / annotations` 4.16.0（BSD 与 Apache-2.0 双许可）
- `com.squareup.okhttp3:okhttp / logging-interceptor` 4.12.0、`com.squareup.okio:okio-jvm` 3.6.0
- `com.google.code.gson:gson` 2.10.1、`com.google.guava:listenablefuture` 1.0
- `com.github.chrisbanes:PhotoView` 2.3.0（JitPack 生成的 POM 未填写许可证字段，上游仓库声明为 Apache-2.0）
- `com.github.wseemann:FFmpegMediaMetadataRetriever` 1.0.14（封装层本身为 Apache-2.0，其内置 FFmpeg 见第三节）
- `org.jetbrains.kotlin:kotlin-stdlib` 2.4.10、`org.jetbrains.kotlinx:kotlinx-coroutines-*`、`org.jetbrains:annotations` 23.0.0、`dev.drewhamilton.poko:poko-annotations-jvm` 0.23.1

Rust 侧以纯 Apache-2.0 授权的直接依赖：`opendal` 0.50.2（WebDAV/FTP 服务）、`backon`、`flagset`、`sync_wrapper`。（另有 218 个 `MIT OR Apache-2.0` 双许可 crate，本项目按 MIT 使用，见下节。）

**`net.java.dev.jna:jna:5.14.0`** 采用 LGPL-2.1-or-later 与 Apache-2.0 双许可，本项目**选择 Apache-2.0** 这一分支。

Apache-2.0 许可证全文见 [licenses/Apache-2.0.txt](licenses/Apache-2.0.txt)。

## 六、MIT

Rust 侧共 277 个 crate 以 MIT 授权：其中 59 个为纯 `MIT`，218 个为 `MIT OR Apache-2.0` 双许可（本项目按 MIT 使用）。主要直接依赖包括：`tokio`、`rusqlite`、`libsqlite3-sys`、`smb` 与 `smb-*` 系列（`smb`、`smb-dtyp`、`smb-fscc`、`smb-msg`、`smb-rpc`、`smb-transport`）、`reqwest`、`hyper`、`bytes`、`tracing`、`nom`、`quick-xml`、`lz4_flex` 等。

Android 侧：`com.materialkolor:material-color-utilities-android` 5.0.0。

```
MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

各 crate 的具体版权人见其各自源码包内的声明。

## 七、BSD 系列

| 组件 | 许可证 |
| :--- | :--- |
| `androidx.datastore:datastore-preferences-external-protobuf` 1.1.3 | BSD-3-Clause |
| Rust：`curve25519-dalek`、`ed25519-dalek`、`subtle`、`x25519-dalek` | BSD-3-Clause |
| `com.github.bumptech.glide:*` 4.16.0 | 简化 BSD（与 Apache-2.0 双许可） |

```
BSD 3-Clause License

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.
3. Neither the name of the copyright holder nor the names of its contributors
   may be used to endorse or promote products derived from this software
   without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## 八、其他宽松许可证

| 许可证 | 组件 |
| :--- | :--- |
| ISC | `rustls-webpki`（0.101.7、0.103.9）、`untrusted`、`ring`（Apache-2.0 AND ISC）、`aws-lc-rs` / `aws-lc-sys`（ISC 及其组合表达式） |
| ISC 风格自定义文本（`license-file`） | `webpki` 0.22.4 |
| Unicode-3.0 | `icu_*` 系列、`zerovec`、`zerotrie`、`yoke`、`zerofrom`、`litemap`、`tinystr`、`writeable`、`potential_utf`、`unicode-ident` 等 19 个 crate，全文见 [licenses/Unicode-3.0.txt](licenses/Unicode-3.0.txt) |
| CDLA-Permissive-2.0 | `webpki-roots` 1.0.6（证书数据） |
| Zlib OR Apache-2.0 OR MIT | `bytemuck`、`tinyvec`、`tinyvec_macros`、`lru-slab` |
| Unlicense OR MIT | `aho-corasick`、`byteorder`、`memchr` |
| Apache-2.0 WITH LLVM-exception OR Apache-2.0 OR MIT | `linux-raw-sys`、`rustix`、`wasi`、`wasip2`、`wit-bindgen` |
| Apache-2.0 OR BSL-1.0 | `ryu` |
| CC0-1.0 OR MIT-0 OR Apache-2.0 | `dunce` |
| MIT OR Apache-2.0 OR LGPL-2.1-or-later | `r-efi`（本项目选择 MIT） |
| 公共领域 | 随 `libsqlite3-sys` 0.28.0 一同编译的 SQLite 3.45.0（`sqlite3.c` 明示放弃版权） |

## 九、源码获取（Source Availability）

- 本应用自身的完整源码：<https://github.com/Costben/WebDAVToon>
- Rust 依赖的精确版本清单：`rust-core/Cargo.lock`（共 428 个 crate），各 crate 源码可从 <https://crates.io> 按名称与版本获取
- Android 依赖的声明清单：`app/build.gradle.kts`，各构件源码可从 Google Maven、Maven Central、JitPack 获取
- 内置 FFmpeg 的对应源码：见第三节
- MPL-2.0 组件的源码：<https://github.com/mozilla/uniffi-rs>（uniffi 0.28.3）、`webpki-roots` 0.22.6

## 十、清单完整性说明

- Rust 侧的清单取自 `rust-core/Cargo.lock` 的完整解析结果（428 个 crate），逐个核对了各 crate 自带的 `license` 字段；其中无 GPL、AGPL、EPL、CDDL、SSPL 组件。
- Android 侧的清单取自当前构建实际解析出的运行时 classpath（127 个模块），逐个核对了构件 POM 的许可证字段；少数构件（`androidx.core`、`androidx.collection`、`jspecify` 等 15 个）的 POM 未被 Gradle 下载，其许可证依据同一 `group:artifact` 的其他版本判定为 Apache-2.0。
- 本文件不逐条列出全部传递依赖的版权行，精确清单以上述两个来源为准。
