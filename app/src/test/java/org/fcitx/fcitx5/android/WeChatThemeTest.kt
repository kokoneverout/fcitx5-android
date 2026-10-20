/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.fcitx.fcitx5.android.data.theme.CustomThemeSerializer
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.junit.Assert.assertEquals
import org.junit.Test

class WeChatThemeTest {
    @Test
    fun lightThemePreservesEveryImportedColor() {
        assertMatchesArchive("wechat-light.json", ThemePreset.WeChatLight)
    }

    @Test
    fun darkThemePreservesEveryImportedColor() {
        assertMatchesArchive("wechat-dark.json", ThemePreset.WeChatDark)
    }

    private fun assertMatchesArchive(resource: String, builtin: Theme.Builtin) {
        val original = requireNotNull(javaClass.getResource("/themes/$resource")).readText()
        val expected = Json.parseToJsonElement(original).jsonObject - "name"
        val actual = Json.parseToJsonElement(
            Json.encodeToString(CustomThemeSerializer, builtin.deriveCustomNoBackground("test"))
        ).jsonObject - "name"
        assertEquals(expected, actual)
    }
}
