/*
 * Copyright 2019 The Android Open Source Project
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

import androidx.ui.engine.text.FontStyle
import androidx.ui.engine.text.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FontFamilyListTest {

    @Test(expected = AssertionError::class)
    fun `cannot be instantiated with empty font family list`() {
        FontFamilyList(listOf())
    }

    @Test
    fun `two equal family list declarations are equal`() {
        val font = Font(
            name = "fontName",
            weight = FontWeight.w900,
            style = FontStyle.Italic,
            ttcIndex = 1,
            fontVariationSettings = "'wdth' 150"
        )
        val fontFamilyList =
            FontFamilyList(FontFamily(font))

        val otherFontFamilyList =
            FontFamilyList(FontFamily(font.copy()))

        assertThat(fontFamilyList).isEqualTo(otherFontFamilyList)
    }

    @Test
    fun `two non equal family list declarations are not equal`() {
        val font = Font(
            name = "fontName",
            weight = FontWeight.w900,
            style = FontStyle.Italic,
            ttcIndex = 1,
            fontVariationSettings = "'wdth' 150"
        )
        val fontFamilyList =
            FontFamilyList(FontFamily(font))

        val otherFontFamilyList = FontFamilyList(
            FontFamily(font.copy(fontVariationSettings = "'wdth' 151"))
        )

        assertThat(fontFamilyList).isNotEqualTo(otherFontFamilyList)
    }
}