// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon.ui.screen.settings

/**
 * One row of the in-app open-source license list.
 *
 * Mirrors a section of the repo-root `THIRD-PARTY-NOTICES.md`, which stays the single source of
 * truth: [assetPath] names a file the `syncOssAssets` Gradle task copies from the repo root into
 * the APK assets, so no license text is duplicated into this repository.
 */
data class LicenseEntry(
    val id: String,
    val title: String,
    val spdxId: String,
    val components: List<String>,
    val assetPath: String,
    val sourceUrl: String? = null,
)

/** The curated table behind [LicensesScreen]; one entry per license group. */
object LicensesData {

    /** Asset directory the `syncOssAssets` task fills from the repo root. */
    const val ASSETS_DIR = "licenses/"

    /** Repo-root `LICENSE`, copied under its SPDX name. */
    const val APP_LICENSE_ASSET = "licenses/GPL-3.0.txt"

    /** Repo-root `THIRD-PARTY-NOTICES.md`: the full component inventory. */
    const val NOTICES_ASSET = "licenses/THIRD-PARTY-NOTICES.md"

    /**
     * The app's own license. GPL-3.0-only is forced by the bundled GPL-2.0-or-later pinyin4j and
     * chosen over GPLv2 because Apache-2.0 and MPL-2.0 components are GPLv2-incompatible.
     */
    val appEntries = listOf(
        LicenseEntry(
            id = "app-gpl-3.0",
            title = "WebDAVToon",
            spdxId = "GPL-3.0-only",
            components = listOf(
                "erl.webdavtoon (Android app)",
                "rust-core / librust_core.so",
            ),
            assetPath = APP_LICENSE_ASSET,
            sourceUrl = "https://github.com/Costben/WebDAVToon",
        ),
    )

    /** Third-party groups, in the order of `THIRD-PARTY-NOTICES.md` sections two to eight. */
    val thirdPartyEntries = listOf(
        LicenseEntry(
            id = "gpl-2.0-or-later",
            title = "GNU General Public License v2.0 or later",
            spdxId = "GPL-2.0-or-later",
            components = listOf("com.belerweb:pinyin4j 2.5.1"),
            assetPath = NOTICES_ASSET,
            sourceUrl = "https://github.com/belerweb/pinyin4j",
        ),
        LicenseEntry(
            id = "lgpl-2.1-or-later",
            title = "GNU Lesser General Public License v2.1 or later",
            spdxId = "LGPL-2.1-or-later",
            components = listOf(
                "FFmpeg 3.0.1 (libavcodec, libavformat, libavutil, libswscale; arm64-v8a)",
                "com.github.wseemann:FFmpegMediaMetadataRetriever 1.0.14",
                "com.hp.hpl.sparta (bundled in pinyin4j)",
            ),
            assetPath = "licenses/LGPL-2.1.txt",
            sourceUrl = "https://github.com/wseemann/FFmpegMediaMetadataRetriever",
        ),
        LicenseEntry(
            id = "mpl-2.0",
            title = "Mozilla Public License 2.0",
            spdxId = "MPL-2.0",
            components = listOf(
                "uniffi 0.28.3 (uniffi, uniffi_bindgen, uniffi_build, uniffi_checksum_derive, uniffi_core, uniffi_macros, uniffi_meta, uniffi_testing, uniffi_udl)",
                "webpki-roots 0.22.6",
            ),
            assetPath = "licenses/MPL-2.0.txt",
            sourceUrl = "https://github.com/mozilla/uniffi-rs",
        ),
        LicenseEntry(
            id = "apache-2.0",
            title = "Apache License 2.0",
            spdxId = "Apache-2.0",
            components = listOf(
                "AndroidX / Jetpack Compose (BOM 2026.05.01)",
                "io.github.suqi8.coui.kmp:coui-* 1.1.0 (COUI)",
                "com.github.bumptech.glide:glide 4.16.0 and friends (dual-licensed)",
                "com.squareup.okhttp3:okhttp 4.12.0, com.squareup.okio:okio-jvm 3.6.0",
                "com.google.code.gson:gson 2.10.1, com.google.guava:listenablefuture 1.0",
                "com.github.chrisbanes:PhotoView 2.3.0",
                "com.github.wseemann:FFmpegMediaMetadataRetriever 1.0.14",
                "org.jetbrains.kotlin:kotlin-stdlib 2.4.10, org.jetbrains:annotations 23.0.0, dev.drewhamilton.poko:poko-annotations-jvm 0.23.1",
                "Rust: opendal 0.50.2, backon, flagset, sync_wrapper",
                "net.java.dev.jna:jna 5.14.0 (this project selects the Apache-2.0 branch)",
            ),
            assetPath = "licenses/Apache-2.0.txt",
        ),
        LicenseEntry(
            id = "mit",
            title = "MIT License",
            spdxId = "MIT",
            components = listOf(
                "277 Rust crates: 59 MIT plus 218 MIT OR Apache-2.0 (tokio, rusqlite, libsqlite3-sys, smb-*, reqwest, hyper, tracing, nom, quick-xml, lz4_flex)",
                "com.materialkolor:material-color-utilities-android 5.0.0",
            ),
            assetPath = NOTICES_ASSET,
            sourceUrl = "https://crates.io",
        ),
        LicenseEntry(
            id = "bsd-3-clause",
            title = "BSD 3-Clause License",
            spdxId = "BSD-3-Clause",
            components = listOf(
                "androidx.datastore:datastore-preferences-external-protobuf 1.1.3",
                "Rust: curve25519-dalek, ed25519-dalek, subtle, x25519-dalek",
                "com.github.bumptech.glide:* 4.16.0 (simplified BSD)",
            ),
            assetPath = NOTICES_ASSET,
        ),
        LicenseEntry(
            id = "isc",
            title = "ISC License",
            spdxId = "ISC",
            components = listOf(
                "Rust: rustls-webpki 0.101.7 / 0.103.9, untrusted, ring (Apache-2.0 AND ISC), aws-lc-rs / aws-lc-sys",
                "webpki 0.22.4 (custom ISC-style license file)",
            ),
            assetPath = NOTICES_ASSET,
        ),
        LicenseEntry(
            id = "unicode-3.0",
            title = "Unicode License v3",
            spdxId = "Unicode-3.0",
            components = listOf(
                "19 Rust crates including icu_*, zerovec, zerotrie, yoke, zerofrom, litemap, tinystr, writeable, potential_utf, unicode-ident",
            ),
            assetPath = "licenses/Unicode-3.0.txt",
        ),
        LicenseEntry(
            id = "cdla-permissive-2.0",
            title = "Community Data License Agreement Permissive 2.0",
            spdxId = "CDLA-Permissive-2.0",
            components = listOf("Rust: webpki-roots 1.0.6 (certificate data)"),
            assetPath = NOTICES_ASSET,
        ),
        LicenseEntry(
            id = "permissive-combinations",
            title = "Other permissive license combinations",
            spdxId = "Zlib OR Apache-2.0 OR MIT; Unlicense OR MIT; Apache-2.0 WITH LLVM-exception; Apache-2.0 OR BSL-1.0; CC0-1.0 OR MIT-0 OR Apache-2.0",
            components = listOf(
                "Rust: bytemuck, tinyvec, tinyvec_macros, lru-slab",
                "Rust: aho-corasick, byteorder, memchr",
                "Rust: linux-raw-sys, rustix, wasi, wasip2, wit-bindgen",
                "Rust: ryu, dunce, r-efi",
            ),
            assetPath = NOTICES_ASSET,
        ),
        LicenseEntry(
            id = "public-domain",
            title = "Public domain",
            spdxId = "Public domain",
            components = listOf("SQLite 3.45.0 (sqlite3.c, built via libsqlite3-sys 0.28.0)"),
            assetPath = NOTICES_ASSET,
        ),
    )
}
