# P3-1：URL 编码改造

> 涉及文件：FileUtils.kt

---

## 现状问题

- `encodeWebDavUrl` 逐段做 `decode → encode`，遇到 `%20` 和 `+` 混用时会出错
- 没有处理 Unicode 路径（中文文件名）
- 编码后手动 `.replace("%2E", ".")` 等回退，不可靠

---

## 改造步骤

### 第 1 步：RFC 3986 标准编码

```kotlin
fun encodeWebDavUrl(url: String): String {
    return try {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd == -1) return Uri.encode(url, ":/@#?&=")

        val pathStart = url.indexOf('/', schemeEnd + 3)
        if (pathStart == -1) return url

        val baseUrl = url.substring(0, pathStart)
        val rawPath = url.substring(pathStart)

        val encodedPath = rawPath.split('/').joinToString("/") { segment ->
            if (segment.isEmpty()) return@joinToString ""
            // 直接对原始段做 RFC 3986 编码，不做先 decode 的操作
            segment.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
                when (byte.toInt().toChar()) {
                    'A'..'Z', 'a'..'z', '0'..'9', '-', '.', '_', '~' -> byte.toInt().toChar().toString()
                    else -> "%${byte.toInt().and(0xFF).toString(16).uppercase().padStart(2, '0')}"
                }
            }
        }
        "$baseUrl$encodedPath"
    } catch (e: Exception) {
        Log.e(TAG, "URL encoding failed", e)
        url
    }
}
```

- 不再做 `URLDecoder.decode` → `URLEncoder.encode` 的双重转换
- 直接将段转为 UTF-8 字节，逐字节判断是否需要编码
- 中文字符直接编码为 `%XX%XX%XX`，不做任何解码前处理

### 第 2 步：保留安全字符集

RFC 3986 不编码的字符：`A-Z a-z 0-9 - . _ ~`

当前代码手动 replace 回退的字符：
- `%2E` → `.`（句号）
- `%2D` → `-`（连字符）
- `%5F` → `_`（下划线）
- `%7E` → `~`（波浪号）

改造后这些字符直接不编码，不需要 replace 回退。

### 第 3 步：删除旧的编码逻辑

```
删除：
  - rawPath.replace("+", "%2B") 前处理
  - URLDecoder.decode 调用
  - URLEncoder.encode 调用
  - 4 个 .replace 回退
```

新逻辑只有：`segment.toByteArray → 逐字节判断 → 拼接`

### 第 4 步：增加单元测试

```kotlin
@Test
fun testEncodeWebDavUrl() {
    // 普通路径
    assertEquals("https://example.com/path/to/file.jpg",
        encodeWebDavUrl("https://example.com/path/to/file.jpg"))

    // 空格
    assertEquals("https://example.com/my%20photo.jpg",
        encodeWebDavUrl("https://example.com/my photo.jpg"))

    // 中文
    assertEquals("https://example.com/%E5%9B%BE%E7%89%87.jpg",
        encodeWebDavUrl("https://example.com/图片.jpg"))

    // 特殊字符
    assertEquals("https://example.com/a%23b.jpg",
        encodeWebDavUrl("https://example.com/a#b.jpg"))

    // 已编码的路径不应二次编码
    assertEquals("https://example.com/my%20photo.jpg",
        encodeWebDavUrl("https://example.com/my%20photo.jpg"))
}
```

---

## 验证要点

- 中文文件名路径正确编码
- 空格、#、% 等特殊字符正确编码
- 已编码的 URL 不会被二次编码
- 普通英文路径不变
