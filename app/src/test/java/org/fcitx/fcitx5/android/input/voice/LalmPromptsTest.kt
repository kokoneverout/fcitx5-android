/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LalmPromptsTest {
    @Test
    fun emptyConfigurationUsesTheDefaultTranscriptionPrompt() {
        for (configured in listOf(null, "", " \n\t")) {
            assertEquals(DEFAULT_LALM_SYSTEM_PROMPT, lalmSystemPrompt(configured, editMode = false))
        }
        assertTrue(DEFAULT_LALM_SYSTEM_PROMPT.startsWith("你是一套高精度、多语言语音转写系统。"))
        assertTrue(DEFAULT_LALM_SYSTEM_PROMPT.contains("不得自行补充、推测、改写、概括或回答语音中的问题"))
        assertTrue(DEFAULT_LALM_SYSTEM_PROMPT.contains("[听不清]"))
    }

    @Test
    fun userPromptIsPreserved() {
        val custom = "Custom prompt\nPreserve these instructions."
        assertEquals(custom, lalmSystemPrompt(custom, editMode = false))
        assertEquals(custom, lalmSystemPrompt(custom, editMode = true))
    }

    @Test
    fun editModeKeepsItsEditingInstructions() {
        val edit = lalmSystemPrompt("", editMode = true)
        assertNotEquals(DEFAULT_LALM_SYSTEM_PROMPT, edit)
        assertTrue(edit.contains("text editing assistant"))
    }
}
