/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.ui.engine.text.font

data class FontFamily(val fonts: List<Font>) : List<Font> by fonts {
    constructor(font: Font) : this(listOf(font))
    constructor(vararg fonts: Font) : this(fonts.asList())

    init {
        assert(fonts.size > 0) { "At least one font is required in FontFamily" }
        assert(fonts.distinctBy { Pair(it.weight, it.style) }.size == fonts.size) {
            "There cannot be two fonts with the same FontWeight and FontStyle in the same " +
                    "FontFamily"
        }
    }
}