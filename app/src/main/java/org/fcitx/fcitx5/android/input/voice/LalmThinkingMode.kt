/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.voice

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class LalmThinkingMode(override val stringRes: Int) : ManagedPreferenceEnum {
    Auto(R.string.voice_input_lalm_thinking_auto),
    Disabled(R.string.voice_input_lalm_thinking_disabled),
    Enabled(R.string.voice_input_lalm_thinking_enabled),
}
