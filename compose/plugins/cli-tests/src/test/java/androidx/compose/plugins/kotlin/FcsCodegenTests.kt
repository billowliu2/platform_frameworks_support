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

@file:Suppress("MemberVisibilityCanBePrivate")

package androidx.compose.plugins.kotlin

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import junit.framework.TestCase
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.URLClassLoader

@RunWith(ComposeRobolectricTestRunner::class)
@Config(
    manifest = Config.NONE,
    minSdk = 23,
    maxSdk = 23
)
class FcsCodegenTests : AbstractCodegenTest() {

    @Test
    fun testSimpleFunctionResolution(): Unit = ensureSetup {
        compose(
            """
            import androidx.compose.*

            @Composable
            fun noise(text: String) {}

            @Composable
            fun bar() {
                noise(text="Hello World")
            }
            """, { emptyMap<String, String>() },
            """
            """
        )
    }

    @Test
    fun testSimpleClassResolution(): Unit = ensureSetup {
        compose(
            """
            import android.widget.TextView
            import androidx.compose.*

            @Composable
            fun bar() {
                TextView(text="Hello World")
            }
            """, { emptyMap<String, String>() },
            """
            """
        )
    }

    @Test
    fun testModelOne(): Unit = ensureSetup {
        codegen(
            """
                @Model
                class ModelClass() {
                    var x = 0
                }
            """, false
        )
    }

    @Test
    fun testSetContent(): Unit = ensureSetup {
        codegen(
            """
                fun fakeCompose(block: @Composable() ()->Unit) { }

                class Test {
                    fun test() {
                        fakeCompose {
                            LinearLayout(orientation = LinearLayout.VERTICAL) {}
                        }
                    }
                }
            """
        )
    }

    @Test
    fun testComposeWithResult(): Unit = ensureSetup {
        compose(
            """
                fun <T> identity(block: @Composable() ()->T): T = block()

                @Composable
                fun TestCall() {
                  val value: Any = identity { 12 }
                  TextView(text = value.toString(), id = 100)
                }
            """,
            { emptyMap<String, String>() },
            "TestCall()", dumpClasses = true
        ).then { activity ->
            val textView = activity.findViewById<TextView>(100)
            assertEquals("12", textView.text)
        }
    }

    @Test
    fun testObservable(): Unit = ensureSetup {
        compose(
            """
                import android.widget.Button
                import androidx.compose.*
                import androidx.ui.androidview.adapters.setOnClick

                @Model
                class FancyButtonData() {
                    var x = 0
                }

                @Composable
                fun SimpleComposable() {
                    FancyButton(state=FancyButtonData())
                }

                @Composable
                fun FancyButton(state: FancyButtonData) {
                    Button(text=("Clicked "+state.x+" times"), onClick={state.x++}, id=42)
                }
            """,
            { mapOf<String, String>() },
            "SimpleComposable()"
        ).then { activity ->
            val button = activity.findViewById(42) as Button
            button.performClick()
            button.performClick()
            button.performClick()
        }.then { activity ->
            val button = activity.findViewById(42) as Button
            assertEquals("Clicked 3 times", button.text)
        }
    }

    @Test
    fun testObservableLambda(): Unit = ensureSetup {
        compose(
            """
                import android.widget.*
                import androidx.compose.*
                import androidx.ui.androidview.adapters.setOnClick

                @Model
                class FancyButtonCount() {
                    var count = 0
                }

                @Composable
                fun SimpleComposable(state: FancyButtonCount) {
                    FancyBox2 {
                        Button(
                          text=("Button clicked "+state.count+" times"),
                          onClick={state.count++},
                          id=42
                        )
                    }
                }

                @Composable
                fun FancyBox2(@Children children: ()->Unit) {
                    children()
                }
            """,
            { mapOf<String, String>() },
            "SimpleComposable(state=FancyButtonCount())"
        ).then { activity ->
            val button = activity.findViewById(42) as Button
            button.performClick()
            button.performClick()
            button.performClick()
        }.then { activity ->
            val button = activity.findViewById(42) as Button
            assertEquals("Button clicked 3 times", button.text)
        }
    }

    @Test
    fun testCGSimpleTextView(): Unit = ensureSetup {
        compose(
            """
                TextView(text="Hello, world!", id=42)
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testCGLocallyScopedFunction(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo() {
                    @Composable fun Bar() {
                        TextView(text="Hello, world!", id=42)
                    }
                    Bar()
                }
            """,
            { mapOf<String, String>() },
            """
                Foo()
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testCGLocallyScopedExtensionFunction(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(x: String) {
                    @Composable fun String.Bar() {
                        TextView(text=this, id=42)
                    }
                    x.Bar()
                }
            """,
            { mapOf<String, String>() },
            """
                Foo(x="Hello, world!")
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testImplicitReceiverScopeCall(): Unit = ensureSetup {
        compose(
            """
                import androidx.compose.*

                class Bar(val text: String)

                @Composable fun Bar.Foo() {
                    TextView(text=text,id=42)
                }

                @Composable
                fun Bam(bar: Bar) {
                    with(bar) {
                        Foo()
                    }
                }
            """,
            { mapOf<String, String>() },
            """
                Bam(bar=Bar("Hello, world!"))
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testCGLocallyScopedInvokeOperator(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(x: String) {
                    @Composable
                    operator fun String.invoke() {
                        TextView(text=this, id=42)
                    }
                    x()
                }
            """,
            { mapOf<String, String>() },
            """
                Foo(x="Hello, world!")
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testTrivialExtensionFunction(): Unit = ensureSetup {
        compose(
            """ """,
            { mapOf<String, String>() },
            """
                val x = "Hello"
                @Composable fun String.foo() {}
                x.foo()
            """
        )
    }

    @Test
    fun testTrivialInvokeExtensionFunction(): Unit = ensureSetup {
        compose(
            """ """,
            { mapOf<String, String>() },
            """
                val x = "Hello"
                @Composable operator fun String.invoke() {}
                x()
            """
        )
    }

    @Test
    fun testCGNSimpleTextView(): Unit = ensureSetup {
        compose(
            """
                TextView(text="Hello, world!", id=42)
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testInliningTemp(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(x: Double, @Children children: Double.() -> Unit) {
                  x.children()
                }
            """,
            { mapOf("foo" to "bar") },
            """
                Foo(x=1.0) {
                    TextView(text=this.toString(), id=123)
                }
            """
        ).then { activity ->
            val textView = activity.findViewById(123) as TextView
            assertEquals("1.0", textView.text)
        }
    }

    @Test
    fun testInliningTemp2(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(onClick: Double.() -> Unit) {

                }
            """,
            { mapOf("foo" to "bar") },
            """
                Foo(onClick={})
            """
        ).then { }
    }

    @Test
    fun testInliningTemp3(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(onClick: (Double) -> Unit) {

                }
            """,
            { mapOf("foo" to "bar") },
            """
                Foo(onClick={})
            """
        ).then { }
    }

    @Test
    fun testInliningTemp4(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(onClick: (Double) -> Unit) {

                }
            """,
            { mapOf("foo" to "bar") },
            """
                Foo(onClick={})
            """
        ).then {}
    }

    @Test
    fun testCGNInlining(): Unit = ensureSetup {
        compose(
            """
                LinearLayout(orientation=LinearLayout.VERTICAL) {
                    TextView(text="Hello, world!", id=42)
                }
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testCGUpdatedComposition(): Unit = ensureSetup {
        var value = "Hello, world!"

        compose(
            { mapOf("value" to value) }, """
           TextView(text=value, id=42)
        """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)

            value = "Other value"
        }.then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Other value", textView.text)
        }
    }

    @Test
    fun testCGNUpdatedComposition(): Unit = ensureSetup {
        var value = "Hello, world!"

        compose(
            { mapOf("value" to value) }, """
           TextView(text=value, id=42)
        """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)

            value = "Other value"
        }.then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Other value", textView.text)
        }
    }

    @Test
    fun testCGViewGroup(): Unit = ensureSetup {
        val tvId = 258
        val llId = 260
        var text = "Hello, world!"
        var orientation = LinearLayout.HORIZONTAL

        compose(
            { mapOf("text" to text, "orientation" to orientation) }, """
            LinearLayout(orientation=orientation, id=$llId) {
              TextView(text=text, id=$tvId)
            }
        """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val linearLayout = activity.findViewById(llId) as LinearLayout

            assertEquals(text, textView.text)
            assertEquals(orientation, linearLayout.orientation)

            text = "Other value"
            orientation = LinearLayout.VERTICAL
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val linearLayout = activity.findViewById(llId) as LinearLayout

            assertEquals(text, textView.text)
            assertEquals(orientation, linearLayout.orientation)
        }
    }

    // @Test
    fun testCGNAmbient(): Unit = ensureSetup {
        val tvId = 258
        var text = "Hello, world!"

        compose(
            """

            val StringAmbient = Ambient.of<String> { "default" }

            @Composable fun Foo() {
                StringAmbient.Consumer { value ->
                    TextView(id=$tvId, text=value)
                }
            }

        """,
            { mapOf("text" to text) },
            """
            StringAmbient.Provider(value=text) {
                Foo()
            }
        """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
            text = "wat"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
        }
    }

    // @Test
    fun testAmbientPortal1(): Unit = ensureSetup {
        val llId = 123
        val tvId = 345
        var text = "Hello, world!"

        // NOTE(lmr): The fact that "bust" is needed here is actually an issue with the fact that
        // changes to providers don't invalidate consumers from other composers via Ambient.Portal.
        // When that gets fixed, we should update this test to show that.
        compose(
            """
            val StringAmbient = Ambient.of<String> { "default" }

            fun buildPortal() = effectOf<Ambient.Reference> {
                context.buildReference()
            }

            @Composable fun App(value: String) {
                StringAmbient.Provider(value) {
                    Parent(bust=Math.random())
                }
            }

            @Composable fun Parent(bust: Double) {
                val ambientRef = +buildPortal()
                val viewRef = +memo { Ref<LinearLayout>() }

                LinearLayout(id=$llId, ref=viewRef)

                +onCommit {
                    Compose.composeInto(
                        container = viewRef.value ?: error("No View Ref!"),
                        parent = ambientRef
                    ) {
                        Child(bust=Math.random())
                    }
                }
            }

            @Composable fun Child(bust: Double) {
                StringAmbient.Consumer { value ->
                    TextView(id=$tvId, text=value)
                }
            }

            """,
            { mapOf("text" to text) },
            """
            App(value=text)
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val layout = activity.findViewById(llId) as LinearLayout

            assertEquals(1, layout.childCount)
            assertEquals(text, textView.text)
            text = "wat"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val layout = activity.findViewById(llId) as LinearLayout

            assertEquals(1, layout.childCount)
            assertEquals(text, textView.text)
        }
    }

    // @Test
    fun testAmbientPortal2(): Unit = ensureSetup {
        val llId = 123
        val tvId = 345
        var text = "Hello, world!"

        // NOTE(lmr): The fact that "bust" is needed here is actually an issue with the fact that
        // changes to providers don't invalidate consumers from other composers via Ambient.Portal.
        // When that gets fixed, we should update this test to show that.
        compose(
            """
            val StringAmbient = Ambient.of<String> { "default" }

            @Composable fun App(value: String) {
                StringAmbient.Provider(value) {
                    Parent(bust=Math.random())
                }
            }

            @Composable fun Parent(bust: Double) {
                Ambient.Portal { ambientRef ->
                    val viewRef = +memo { Ref<LinearLayout>() }

                    LinearLayout(id=$llId, ref=viewRef)

                    +onCommit {
                        Compose.composeInto(
                            container = viewRef.value ?: error("No View Ref!"),
                            parent = ambientRef
                        ) {
                            Child(bust=Math.random())
                        }
                    }
                }
            }

            @Composable fun Child(bust: Double) {
                StringAmbient.Consumer { value ->
                    TextView(id=$tvId, text=value)
                }
            }

            """,
            { mapOf("text" to text) },
            """
            App(value=text)
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val layout = activity.findViewById(llId) as LinearLayout

            assertEquals(1, layout.childCount)
            assertEquals(text, textView.text)
            text = "wat"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val layout = activity.findViewById(llId) as LinearLayout

            assertEquals(1, layout.childCount)
            assertEquals(text, textView.text)
        }
    }

    // @Test
    fun testCGNClassComponent(): Unit = ensureSetup {
        var text = "Hello, world!"
        val tvId = 123

        compose(
            """
            class Foo {
                var text = ""
                @Composable
                operator fun invoke(bar: Int) {
                    TextView(id=$tvId, text=text)
                }
            }

        """,
            { mapOf("text" to text) },
            """
             Foo(text=text, bar=123)
        """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
            text = "wat"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
        }
    }

    @Test
    fun testCGNFunctionComponent(): Unit = ensureSetup {
        var text = "Hello, world!"
        val tvId = 123

        compose(
            """
            @Composable
            fun Foo(text: String) {
                TextView(id=$tvId, text=text)
            }

        """,
            { mapOf("text" to text) },
            """
             Foo(text=text)
        """,
            true
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
            text = "wat"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
        }
    }

    // @Test
    fun testAmbientReference(): Unit = ensureSetup {
        val outerId = 123
        val innerId = 345
        val buttonId = 456

        compose(
            """
                fun buildPortal() = effectOf<Ambient.Reference> {
                    context.buildReference()
                }

                fun <T> refFor() = memo { Ref<T>() }

                val textAmbient = Ambient.of { "default" }

                @Composable fun DisplayTest(id: Int) {
                    val text = +ambient(textAmbient)
                    TextView(id=id, text=text)
                }

                @Composable fun PortalTest() {
                    val portal = +buildPortal()
                    val ref = +refFor<LinearLayout>()
                    DisplayTest(id=$outerId)

                    LinearLayout(ref=ref)

                    val root = ref.value ?: error("Expected a linear")

                    Compose.composeInto(root, portal) {
                        DisplayTest(id=$innerId)
                    }
                }

                @Composable
                fun TestApp() {
                    val inc = +state { 1 }

                    Button(id=$buttonId, text="Click Me", onClick={ inc.value += 1 })

                    textAmbient.Provider(value="value: ${"$"}{inc.value}") {
                        PortalTest()
                    }
                }
            """,
            { mapOf("text" to "") },
            """
                TestApp()
            """
        ).then { activity ->
            val inner = activity.findViewById(innerId) as TextView
            val outer = activity.findViewById(outerId) as TextView
            val button = activity.findViewById(buttonId) as Button

            assertEquals("inner", "value: 1", inner.text)
            assertEquals("outer", "value: 1", outer.text)

            button.performClick()
        }.then { activity ->
            val inner = activity.findViewById(innerId) as TextView
            val outer = activity.findViewById(outerId) as TextView
            val button = activity.findViewById(buttonId) as Button

            assertEquals("inner", "value: 2", inner.text)
            assertEquals("outer", "value: 2", outer.text)

            button.performClick()
        }.then { activity ->
            val inner = activity.findViewById(innerId) as TextView
            val outer = activity.findViewById(outerId) as TextView

            assertEquals("inner", "value: 3", inner.text)
            assertEquals("outer", "value: 3", outer.text)
        }
    }

    @Test
    fun testCGNViewGroup(): Unit = ensureSetup {
        val tvId = 258
        val llId = 260
        var text = "Hello, world!"
        var orientation = LinearLayout.HORIZONTAL

        compose(
            { mapOf("text" to text, "orientation" to orientation) }, """
             LinearLayout(orientation=orientation, id=$llId) {
               TextView(text=text, id=$tvId)
             }
        """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val linearLayout = activity.findViewById(llId) as LinearLayout

            assertEquals(text, textView.text)
            assertEquals(orientation, linearLayout.orientation)

            text = "Other value"
            orientation = LinearLayout.VERTICAL
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val linearLayout = activity.findViewById(llId) as LinearLayout

            assertEquals(text, textView.text)
            assertEquals(orientation, linearLayout.orientation)
        }
    }

    @Test
    fun testMemoization(): Unit = ensureSetup {
        val tvId = 258
        val tagId = (3 shl 24) or "composed_set".hashCode()

        @Suppress("UNCHECKED_CAST")
        fun View.getComposedSet(): Set<String>? = getTag(tagId) as? Set<String>

        compose(
            """
                import android.view.View

                var composedSet = mutableSetOf<String>()
                var inc = 1

                fun View.setComposed(composed: Set<String>) = setTag($tagId, composed)

                @Composable fun ComposePrimitive(value: Int) {
                    composedSet.add("ComposePrimitive(" + value + ")")
                }

                class MutableThing(var value: String)

                val constantMutableThing = MutableThing("const")

                @Composable fun ComposeMutable(value: MutableThing) {
                    composedSet.add("ComposeMutable(" + value.value + ")")
                }
            """,
            { mapOf("text" to "") },
            """
                composedSet.clear()

                ComposePrimitive(value=123)
                ComposePrimitive(value=inc)
                ComposeMutable(value=constantMutableThing)
                ComposeMutable(value=MutableThing("new"))

                TextView(id=$tvId, composed=composedSet)

                inc++
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val composedSet = textView.getComposedSet() ?: error("expected a compose set to exist")

            fun assertContains(contains: Boolean, key: String) {
                assertEquals("composedSet contains key '$key'", contains, composedSet.contains(key))
            }

            assertContains(true, "ComposePrimitive(123)")
            assertContains(true, "ComposePrimitive(1)")
            assertContains(true, "ComposeMutable(const)")
            assertContains(true, "ComposeMutable(new)")
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            val composedSet = textView.getComposedSet() ?: error("expected a compose set to exist")

            fun assertContains(contains: Boolean, key: String) {
                assertEquals("composedSet contains key '$key'", contains, composedSet.contains(key))
            }

            // the primitive component skips based on equality
            assertContains(false, "ComposePrimitive(123)")

            // since the primitive changed, this one recomposes again
            assertContains(true, "ComposePrimitive(2)")

            // since this is a potentially mutable object, we don't skip based on it
            assertContains(true, "ComposeMutable(const)")

            // since its a new one every time, we definitely don't skip
            assertContains(true, "ComposeMutable(new)")
        }
    }

    @Test
    fun testCGNSimpleCall(): Unit = ensureSetup {
        val tvId = 258
        var text = "Hello, world!"

        compose(
            """
                @Composable fun SomeFun(x: String) {
                    TextView(text=x, id=$tvId)
                }
            """,
            { mapOf("text" to text) },
            """
                SomeFun(x=text)
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)

            text = "Other value"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
        }
    }

    // @Test
    fun testCGNSimpleCall2(): Unit = ensureSetup {
        val tvId = 258
        var text = "Hello, world!"
        var someInt = 456

        compose(
            """
                class SomeClass(var x: String) {
                    @Composable
                    operator fun invoke(y: Int) {
                        TextView(text="${"$"}x ${"$"}y", id=$tvId)
                    }
                }
            """,
            { mapOf("text" to text, "someInt" to someInt) },
            """
                SomeClass(x=text, y=someInt)
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals("Hello, world! 456", textView.text)

            text = "Other value"
            someInt = 123
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals("Other value 123", textView.text)
        }
    }

    // @Test
    fun testCGNSimpleCall3(): Unit = ensureSetup {
        val tvId = 258
        var text = "Hello, world!"
        var someInt = 456

        compose(
            """
                @Stateful
                class SomeClassoawid(var x: String) {
                    @Composable
                    operator fun invoke(y: Int) {
                        TextView(text="${"$"}x ${"$"}y", id=$tvId)
                    }
                }
            """,
            { mapOf("text" to text, "someInt" to someInt) },
            """
                SomeClassoawid(x=text, y=someInt)
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals("Hello, world! 456", textView.text)

            text = "Other value"
            someInt = 123
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals("Other value 123", textView.text)
        }
    }

    @Test
    fun testCGNCallWithChildren(): Unit = ensureSetup {
        val tvId = 258
        var text = "Hello, world!"

        compose(
            """
                @Composable
                fun Block(@Children children: () -> Unit) {
                    children()
                }
            """,
            { mapOf("text" to text) },
            """
                Block {
                    Block {
                        TextView(text=text, id=$tvId)
                    }
                }
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)

            text = "Other value"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView

            assertEquals(text, textView.text)
        }
    }

    // @Test
    fun testCGNStuff(): Unit = ensureSetup {
        val tvId = 258
        var num = 123

        compose(
            """
                class OneArg {
                    var foo = 0
                    @Composable
                    operator fun invoke() {
                        TextView(text="${"$"}foo", id=$tvId)
                    }
                }
                fun OneArg.setBar(bar: Int) { foo = bar }
            """,
            { mapOf("num" to num) },
            """
            OneArg(bar=num)
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            assertEquals("$num", textView.text)

            num = 456
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            assertEquals("$num", textView.text)
        }
    }

    // @Test
    fun testTagBasedMemoization(): Unit = ensureSetup {
        val tvId = 258
        var text = "Hello World"

        compose(
            """
                class A {
                    var foo = ""
                    inner class B {
                        @Composable
                        operator fun invoke() {
                            TextView(text=foo, id=$tvId)
                        }
                    }
                }
            """,
            { mapOf("text" to text) },
            """
                val a = A()
                a.foo = text
                a.B()
            """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            assertEquals(text, textView.text)

            text = "new value"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            assertEquals(text, textView.text)
        }
    }

    @Test
    fun testCGComposableFunctionInvocationOneParameter(): Unit = ensureSetup {
        val tvId = 91
        var phone = "(123) 456-7890"
        compose(
            """
           @Composable
           fun Phone(value: String) {
             TextView(text=value, id=$tvId)
           }
        """, { mapOf("phone" to phone) }, """
           Phone(value=phone)
        """
        ).then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            assertEquals(phone, textView.text)

            phone = "(123) 456-7899"
        }.then { activity ->
            val textView = activity.findViewById(tvId) as TextView
            assertEquals(phone, textView.text)
        }
    }

    @Test
    fun testCGComposableFunctionInvocationTwoParameters(): Unit = ensureSetup {
        val tvId = 111
        val rsId = 112
        var left = 0
        var right = 0
        compose(
            """
           var addCalled = 0

           @Composable
           fun AddView(left: Int, right: Int) {
             addCalled++
             TextView(text="${'$'}left + ${'$'}right = ${'$'}{left + right}", id=$tvId)
             TextView(text="${'$'}addCalled", id=$rsId)
           }
        """, { mapOf("left" to left, "right" to right) }, """
           AddView(left=left, right=right)
        """
        ).then { activity ->
            // Should be called on the first compose
            assertEquals("1", (activity.findViewById(rsId) as TextView).text)
            assertEquals(
                "$left + $right = ${left + right}",
                (activity.findViewById(tvId) as TextView).text
            )
        }.then { activity ->
            // Should be skipped on the second compose
            assertEquals("1", (activity.findViewById(rsId) as TextView).text)
            assertEquals(
                "$left + $right = ${left + right}",
                (activity.findViewById(tvId) as TextView).text
            )

            left = 1
        }.then { activity ->
            // Should be called again because left changed.
            assertEquals("2", (activity.findViewById(rsId) as TextView).text)
            assertEquals(
                "$left + $right = ${left + right}",
                (activity.findViewById(tvId) as TextView).text
            )

            right = 41
        }.then { activity ->
            // Should be called again because right changed
            assertEquals("3", (activity.findViewById(rsId) as TextView).text)
            assertEquals(
                "$left + $right = ${left + right}",
                (activity.findViewById(tvId) as TextView).text
            )
        }.then { activity ->
            // Should be skipped because nothing changed
            assertEquals("3", (activity.findViewById(rsId) as TextView).text)
        }
    }

    @Test
    fun testImplicitReceiverPassing1(): Unit = ensureSetup {
        compose(
            """
                fun Int.Foo(x: @Composable() Int.() -> Unit) {
                    x()
                }
            """,
            { mapOf<String, String>() },
            """
                val id = 42

                id.Foo(x={
                    TextView(text="Hello, world!", id=this)
                })
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testImplicitReceiverPassing2(): Unit = ensureSetup {
        compose(
            """
                fun Int.Foo(x: @Composable() Int.(text: String) -> Unit, text: String) {
                    x(text=text)
                }
            """,
            { mapOf<String, String>() },
            """
                val id = 42

                id.Foo(text="Hello, world!", x={ text ->
                    TextView(text=text, id=this)
                })
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testEffects1(): Unit = ensureSetup {
        compose(
            """
                import androidx.ui.androidview.adapters.*

                @Composable
                fun Counter() {
                    Observe {
                        var count = +state { 0 }
                        TextView(
                            text=("Count: " + count.value),
                            onClick={
                                count.value += 1
                            },
                            id=42
                        )
                    }
                }
            """,
            { mapOf<String, String>() },
            """
                Counter()
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Count: 0", textView.text)
            textView.performClick()
        }.then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Count: 1", textView.text)
        }
    }

    @Test
    fun testEffects2(): Unit = ensureSetup {
        compose(
            """
                import androidx.ui.androidview.adapters.*

                @Model class MyState<T>(var value: T)

                @Composable
                fun Counter() {
                    Observe {
                        var count = +memo { MyState(0) }
                        TextView(
                            text=("Count: " + count.value),
                            onClick={
                                count.value += 1
                            },
                            id=42
                        )
                    }
                }
            """,
            { mapOf<String, String>() },
            """
                Counter()
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Count: 0", textView.text)
            textView.performClick()
        }.then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Count: 1", textView.text)
        }
    }

    @Test
    fun testEffects3(): Unit = ensureSetup {
        val log = StringBuilder()
        compose(
            """
                import androidx.ui.androidview.adapters.*

                @Composable
                fun Counter(log: StringBuilder) {
                    Observe {
                        var count = +state { 0 }
                        +onCommit {
                            log.append("a")
                        }
                        +onActive {
                            log.append("b")
                        }
                        TextView(
                            text=("Count: " + count.value),
                            onClick={
                                count.value += 1
                            },
                            id=42
                        )
                    }
                }
            """,
            { mapOf("log" to log) },
            """
                Counter(log=log)
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Count: 0", textView.text)
            assertEquals("ab", log.toString())

            execute {
                textView.performClick()
            }

            assertEquals("Count: 1", textView.text)
            assertEquals("aba", log.toString())
        }
    }

    @Test
    fun testEffects4(): Unit = ensureSetup {
        val log = StringBuilder()
        compose(
            """
                import androidx.ui.androidview.adapters.*

                fun printer(log: StringBuilder, str: String) = effectOf<Unit> {
                    +onCommit {
                        log.append(str)
                    }
                }

                @Composable
                fun Counter(log: StringBuilder) {
                    Observe {
                        var count = +state { 0 }
                        +printer(log, "" + count.value)
                        TextView(
                            text=("Count: " + count.value),
                            onClick={
                                count.value += 1
                            },
                            id=42
                        )
                    }
                }
            """,
            { mapOf("log" to log) },
            """
                Counter(log=log)
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Count: 0", textView.text)
            assertEquals("0", log.toString())

            execute {
                textView.performClick()
            }

            assertEquals("Count: 1", textView.text)
            assertEquals("01", log.toString())
        }
    }

    // b/118610495
    // @Test
    fun testCGChildCompose(): Unit = ensureSetup {
        val tvId = 153

        var text = "Test 1"

        compose(
            """
            var called = 0

            class TestContainer(@Children var children: @Composable() ()->Unit): Component() {
              override fun compose() {
                LinearLayout {
                  children()
                }
              }
            }

            class TestClass(var text: String): Component() {
              override fun compose() {
                TestContainer {
                  TextView(text=text, id=$tvId)
                }
              }
            }
        """, { mapOf("text" to text) }, """
            TestClass(text=text)
        """
        ).then { activity ->
            val tv = activity.findViewById(tvId) as TextView
            assertEquals(text, tv.text)

            text = "Test 2"
        }.then { activity ->
            val tv = activity.findViewById(tvId) as TextView
            assertEquals(text, tv.text)
        }
    }

    // @Test
    fun testPrivatePivotalProperties(): Unit = ensureSetup {
        val tvId = 153

        compose(
            """
            class ClassComponent(@Children private val callback: () -> Unit) : Component() {
                override fun compose() {
                    callback()
                }
            }
        """, { mapOf("text" to "") }, """
            ClassComponent {
                TextView(id=$tvId, text="Hello world!")
            }
        """
        ).then { activity ->
            val tv = activity.findViewById(tvId) as TextView
            assertEquals("Hello world!", tv.text)
        }.then { activity ->
            val tv = activity.findViewById(tvId) as TextView
            assertEquals("Hello world!", tv.text)
        }
    }

    @Test
    fun testVariableCalls1(): Unit = ensureSetup {
        compose(
            """
                val component = @Composable {
                    TextView(text="Hello, world!", id=42)
                }
            """,
            { mapOf<String, String>() },
            """
                component()
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testVariableCalls2(): Unit = ensureSetup {
        compose(
            """
                val component = @Composable {
                    TextView(text="Hello, world!", id=42)
                }
                class HolderA(val composable: @Composable() () -> Unit)

                val holder = HolderA(component)

            """,
            { mapOf<String, String>() },
            """
                holder.composable()
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testVariableCalls3(): Unit = ensureSetup {
        compose(
            """
                val component = @Composable {
                    TextView(text="Hello, world!", id=42)
                }
                class HolderB(val composable: @Composable() () -> Unit) {
                    @Composable
                    fun Foo() {
                        composable()
                    }
                }

                val holder = HolderB(component)

            """,
            { mapOf<String, String>() },
            """
                holder.Foo()
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    // @Test
    fun testVariableCalls4(): Unit = ensureSetup {
        compose(
            """
                val component = @Composable {
                    TextView(text="Hello, world!", id=42)
                }
                class HolderC(val composable: @Composable() () -> Unit) {
                    inner class Foo(): Component() {
                        override fun compose() {
                            composable()
                        }
                    }
                }

                val holder = HolderC(component)

            """,
            { mapOf<String, String>() },
            """
                holder.Foo()
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    // b/123721921
    @Test
    fun testDefaultParameters1(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(a: Int = 42, b: String) {
                    TextView(text=b, id=a)
                }
            """,
            { mapOf<String, String>() },
            """
                Foo(b="Hello, world!")
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testDefaultParameters2(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun Foo(a: Int = 42, b: String, @Children c: () -> Unit) {
                    c()
                    TextView(text=b, id=a)
                }
            """,
            { mapOf<String, String>() },
            """
                Foo(b="Hello, world!") {}
            """
        ).then { activity ->
            val textView = activity.findViewById(42) as TextView
            assertEquals("Hello, world!", textView.text)
        }
    }

    @Test
    fun testPropertiesAndCtorParamsOnEmittables(): Unit = codegen(
        """
            class SimpleEmittable(label: String? = null) : Emittable {
                var label: String? = null
                override fun emitInsertAt(index: Int, instance: Emittable) {}
                override fun emitMove(from: Int, to: Int, count: Int) {}
                override fun emitRemoveAt(index: Int, count: Int) {}
            }

            @Composable
            fun foo() {
                SimpleEmittable(label="Foo")
            }
        """
    )

    @Test
    fun testSimpleClassConstructor(): Unit = codegen(
        """
            import androidx.compose.Emittable

            import androidx.compose.composer

            class Path2() : Emittable {

                private val path99 = Path3()

                fun draw(canvas: Canvas2) {
                    canvas.drawPath(path99)
                }

                override fun emitInsertAt(index: Int, instance: Emittable) { }
                override fun emitMove(from: Int, to: Int, count: Int) { }
                override fun emitRemoveAt(index: Int, count: Int) { }
                override fun toString(): String = ""
            }


            class Canvas2() {
                fun drawPath(path: Path3) {
                    System.out.println(""+path)
                }
            }


            class Path3(private val internalPath: android.graphics.Path = android.graphics.Path()) {
            }
        """
    )

    @Test
    fun testMovement(): Unit = ensureSetup {
        val tvId = 50
        val btnIdAdd = 100
        val btnIdUp = 200
        val btnIdDown = 300

        // Duplicate the steps to reproduce an issue discovered in the Reorder example
        compose(
            """
            fun <T> List<T>.move(from: Int, to: Int): List<T> {
                if (to < from) return move(to, from)
                val item = get(from)
                val currentItem = get(to)
                val left = if (from > 0) subList(0, from) else emptyList()
                val right = if (to < size) subList(to + 1, size) else emptyList()
                val middle = if (to - from > 1) subList(from + 1, to) else emptyList()
                return left + listOf(currentItem) + middle + listOf(item) + right
            }

            @Composable
            fun Reordering() {
                Observe {
                    val items = +state { listOf(1, 2, 3, 4, 5) }

                    LinearLayout(orientation=LinearLayout.VERTICAL) {
                        items.value.forEachIndexed { index, id ->
                            Item(
                                id=id,
                                onMove={ amount ->
                                    val next = index + amount
                                    if (next >= 0 && next < items.value.size) {
                                        items.value = items.value.move(index, index + amount)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            @Composable
            private fun Item(@Pivotal id: Int, onMove: (Int) -> Unit) {
                Observe {
                    val count = +state { 0 }
                    LinearLayout(orientation=LinearLayout.HORIZONTAL) {
                        TextView(id=(id+$tvId), text="id: ${'$'}id amt: ${'$'}{count.value}")
                        Button(id=(id+$btnIdAdd), text="+", onClick={ count.value++ })
                        Button(id=(id+$btnIdUp), text="Up", onClick={ onMove(1) })
                        Button(id=(id+$btnIdDown), text="Down", onClick={ onMove(-1) })
                    }
                }
            }
            """, { emptyMap<String, String>() },
            """
               Reordering()
            """
        ).then { activity ->
            // Click 5 add
            val button = activity.findViewById(btnIdAdd + 5) as Button
            button.performClick()
        }.then { activity ->
            // Click 5 down
            val button = activity.findViewById(btnIdDown + 5) as Button
            button.performClick()
        }.then { activity ->
            // Click 5 down
            val button = activity.findViewById(btnIdDown + 5) as Button
            button.performClick()
        }.then { activity ->
            // Click 5 up
            val button = activity.findViewById(btnIdUp + 5) as Button
            button.performClick()
        }.then { activity ->
            // Click 5 up
            val button = activity.findViewById(btnIdUp + 5) as Button
            button.performClick()
        }.then { activity ->
            // Click 5 add
            val button = activity.findViewById(btnIdAdd + 5) as Button
            button.performClick()
        }.then { activity ->
            val textView = activity.findViewById(tvId + 5) as TextView
            assertEquals("id: 5 amt: 2", textView.text)
        }
    }

    @Test
    fun testObserveKtxWithInline(): Unit = ensureSetup {
        compose(
            """
                @Composable
                fun SimpleComposable() {
                    val count = +state { 1 }
                    Box {
                        repeat(count.value) {
                            Button(text="Increment", onClick={ count.value += 1 }, id=(41+it))
                        }
                    }
                }

                @Composable
                fun Box(@Children children: ()->Unit) {
                    LinearLayout(orientation=LinearLayout.VERTICAL) {
                        children()
                    }
                }
            """, { emptyMap<String, String>() },
            """
               SimpleComposable()
            """
        ).then { activity ->
            val button = activity.findViewById(41) as Button
            button.performClick()
            button.performClick()
            button.performClick()
            button.performClick()
            button.performClick()
        }.then { activity ->
            TestCase.assertNotNull(activity.findViewById(46))
        }
    }

    @Test
    fun testKeyTag(): Unit = ensureSetup {
        compose(
            """
            val list = mutableListOf(0,1,2,3)

            @Composable
            fun Reordering() {
                LinearLayout {
                    Recompose { recompose ->
                        Button(
                          id=50,
                          text="Recompose!",
                          onClick={ list.add(list.removeAt(0)); recompose(); }
                        )
                        LinearLayout(id=100) {
                            for(id in list) {
                                Key(key=id) {
                                    StatefulButton()
                                }
                            }
                        }
                    }
                }
            }

            @Composable
            private fun StatefulButton() {
                val count = +state { 0 }
                Button(text="Clicked ${'$'}{count.value} times!", onClick={ count.value++ })
            }
            """, { emptyMap<String, String>() },
            """
               Reordering()
            """
        ).then { activity ->
            val layout = activity.findViewById(100) as LinearLayout
            layout.getChildAt(0).performClick()
        }.then { activity ->
            val recomposeButton = activity.findViewById(50) as Button
            recomposeButton.performClick()
        }.then { activity ->
            val layout = activity.findViewById(100) as LinearLayout
            assertEquals("Clicked 0 times!", (layout.getChildAt(0) as Button).text)
            assertEquals("Clicked 0 times!", (layout.getChildAt(1) as Button).text)
            assertEquals("Clicked 0 times!", (layout.getChildAt(2) as Button).text)
            assertEquals("Clicked 1 times!", (layout.getChildAt(3) as Button).text)
        }
    }

    @Test
    fun testNonFcs(): Unit = ensureSetup {
        compose(
            """

            class MyTextView(context: Context) : TextView(context) {}

            fun foo(context: Context): TextView = MyTextView(context=context)

            """, { emptyMap<String, String>() },
            """
            """
        )
    }

    @Test
    fun testNonComposeParameters(): Unit = ensureSetup {
        compose(
            """
                class Action(
                   val s: String = "",
                   val param: Int,
                   type: Set<Int> = setOf(),
                   val action: () -> Unit
                )

                @Composable
                fun DefineAction(
                    onAction: Action = Action(param = 1) {},
                    @Children children: ()->Unit
                 ) { }
            """
        )
    }

    override fun setUp() {
        isSetup = true
        super.setUp()
    }

    override fun setupEnvironment(environment: KotlinCoreEnvironment) {
        environment.configuration.put(
            ComposeConfigurationKeys.COMPOSABLE_CHECKER_MODE_KEY,
            ComposableAnnotationChecker.Mode.FCS
        )
        super.setupEnvironment(environment)
    }

    private var isSetup = false
    private inline fun <T> ensureSetup(block: () -> T): T {
        if (!isSetup) setUp()
        return block()
    }

    fun codegen(text: String, dumpClasses: Boolean = false): Unit = ensureSetup {
        val className = "Test_${uniqueNumber++}"
        val fileName = "$className.kt"

        classLoader(
            """
           import android.content.Context
           import android.widget.*
           import androidx.compose.*

           $text

        """, fileName, dumpClasses
        )
    }

    fun compose(text: String, dumpClasses: Boolean = false): CompositionTest = compose(
        { mapOf<String, Any>() },
        text,
        dumpClasses
    )

    fun <T : Any> compose(
        valuesFactory: () -> Map<String, T>,
        text: String,
        dumpClasses: Boolean = false
    ) = compose("", valuesFactory, text, dumpClasses)

    private fun execute(block: () -> Unit) {
        val scheduler = RuntimeEnvironment.getMasterScheduler()
        scheduler.pause()
        block()
        scheduler.advanceToLastPostedRunnable()
    }

    fun <T : Any> compose(
        prefix: String,
        valuesFactory: () -> Map<String, T>,
        text: String,
        dumpClasses: Boolean = false
    ): CompositionTest {
        val className = "Test_${uniqueNumber++}"
        val fileName = "$className.kt"

        val candidateValues = valuesFactory()

        @Suppress("NO_REFLECTION_IN_CLASS_PATH")
        val parameterList = candidateValues.map {
            "${it.key}: ${it.value::class.qualifiedName}"
        }.joinToString()
        val parameterTypes = candidateValues.map {
            it.value::class.javaPrimitiveType ?: it.value::class.javaObjectType
        }.toTypedArray()

        val compiledClasses = classLoader(
            """
           import android.content.Context
           import android.widget.*
           import androidx.compose.*
           import androidx.ui.androidview.adapters.*

           $prefix

           class $className {

             @Composable
             fun test($parameterList) {
               $text
             }
           }
        """, fileName, dumpClasses
        )

        val allClassFiles = compiledClasses.allGeneratedFiles.filter {
            it.relativePath.endsWith(".class")
        }

        val loader = URLClassLoader(emptyArray(), this.javaClass.classLoader)

        val instanceClass = run {
            var instanceClass: Class<*>? = null
            var loadedOne = false
            for (outFile in allClassFiles) {
                val bytes = outFile.asByteArray()
                val loadedClass = loadClass(loader, null, bytes)
                if (loadedClass.name == className) instanceClass = loadedClass
                loadedOne = true
            }
            if (!loadedOne) error("No classes loaded")
            instanceClass ?: error("Could not find class $className in loaded classes")
        }

        val instanceOfClass = instanceClass.newInstance()
        val testMethod = instanceClass.getMethod("test", *parameterTypes)

        return compose {
            val values = valuesFactory()
            val arguments = values.map { it.value as Any }.toTypedArray()
            testMethod.invoke(instanceOfClass, *arguments)
        }
    }
}
