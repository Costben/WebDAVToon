// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Rin Shibuya
package erl.webdavtoon

class InMemorySecretStore : WebDavSecretStore {
    private val values = mutableMapOf<Int, String>()

    override fun get(slot: Int): String? = values[slot]

    override fun put(slot: Int, password: String) {
        values[slot] = password
    }

    override fun remove(slot: Int) {
        values.remove(slot)
    }
}
