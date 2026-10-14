package io.paritytech.polkadotapp.detekt

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardcodedComposeTextTest {

    @Test
    fun `reports literal when text gets a string literal`() {
        assertReportsParameter("text")
    }

    @Test
    fun `reports literal once when the call is nested in other calls`() {
        val findings = lintComposable(
            """
            Row {
                Column {
                    Foo(text = "Send")
                }
            }
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports literal at its own line when the call spans several lines`() {
        val findings = lintComposable(
            """
            Foo(
                modifier = m,
                text = "Send",
            )
            """
        )

        assertEquals(COMPOSABLE_BODY_LINE + 2, findings.single().line)
    }

    @Test
    fun `reports literal when title gets a string literal`() {
        assertReportsParameter("title")
    }

    @Test
    fun `reports literal when subtitle gets a string literal`() {
        assertReportsParameter("subtitle")
    }

    @Test
    fun `reports literal when label gets a string literal`() {
        assertReportsParameter("label")
    }

    @Test
    fun `reports literal when placeholder gets a string literal`() {
        assertReportsParameter("placeholder")
    }

    @Test
    fun `reports literal when headerText gets a string literal`() {
        assertReportsParameter("headerText")
    }

    @Test
    fun `ignores parameter when it gets stringResource`() {
        listOf("text", "title", "subtitle", "label", "placeholder", "headerText").forEach { parameter ->
            assertNoFindings("Foo($parameter = stringResource(RCommon.string.x))")
        }
    }

    @Test
    fun `exposes HardcodedComposeText when asked for its rule id`() {
        assertEquals("HardcodedComposeText", HardcodedComposeText(Config.empty).ruleName.value)
    }

    @Test
    fun `ignores literal when the function is a Preview`() {
        val bare = lint(
            """
            @Preview
            @Composable
            private fun FooPreview() {
                Text(text = "Fixture")
            }
            """
        )
        val withArguments = lint(
            """
            @Preview(showBackground = true)
            @Composable
            private fun FooPreview() {
                PolkadotTheme {
                    Text(text = "Fixture")
                }
            }
            """
        )

        assertEquals(0, bare.size)
        assertEquals(0, withArguments.size)
    }

    @Test
    fun `reports literal when the same body has no Preview annotation`() {
        val findings = lint(
            """
            @Composable
            private fun FooPreview() {
                PolkadotTheme {
                    Text(text = "Fixture")
                }
            }
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `ignores label when passed to updateTransition`() {
        assertNoFindings("""val transition = updateTransition(targetState = s, label = "x")""")
    }

    @Test
    fun `ignores label when passed to animateFloat`() {
        assertNoFindings("""val alpha by transition.animateFloat(label = "x") { 0f }""")
    }

    @Test
    fun `ignores label when passed to rememberInfiniteTransition`() {
        assertNoFindings("""val transition = rememberInfiniteTransition(label = "x")""")
    }

    @Test
    fun `ignores label when passed to other Compose animation APIs`() {
        listOf(
            """val a by animateFloatAsState(1f, label = "x")""",
            """val c by animateColorAsState(c, label = "x")""",
            """val i by animateIntAsState(1, label = "x")""",
            """AnimatedContent(targetState = s, label = "x") {}""",
            """AnimatedVisibility(visible = v, label = "x") {}""",
            """Crossfade(targetState = s, label = "x") {}""",
            """val t = rememberTransition(transitionState, label = "x")""",
            """val child = transition.createChildTransition(label = "x") { it }""",
            """val fade = remember { Animatable(0f, Float.VectorConverter, label = "x") }""",
            """val deferred = transition.createDeferredAnimation(Offset.VectorConverter, label = "x")""",
        ).forEach(::assertNoFindings)
    }

    @Test
    fun `reports label when passed to a non-animation composable`() {
        val findings = lintComposable("""PolkadotInput(label = "Amount")""")

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports title when passed to an animation-named composable`() {
        val findings = lintComposable("""AnimatedBanner(title = "Send")""")

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports label when it sits in an animation content lambda`() {
        val findings = lint(
            """
            @Composable
            fun Screen() {
                AnimatedContent(targetState = s, label = "anim") {
                    PolkadotInput(label = "Amount")
                }
            }
            """
        )

        assertEquals(1, findings.size)
        assertEquals(4, findings.single().line)
    }

    @Test
    fun `reports template when it mixes words and interpolation`() {
        listOf(
            """Text(text = "${'$'}count total")""",
            """Text(text = "${'$'}{reactionDetails.totalReactionsCount} total")""",
            """Text(text = "Failed to load message: ${'$'}{error.message}")""",
        ).forEach { body ->
            assertEquals(body, 1, lintComposable(body).size)
        }
    }

    @Test
    fun `ignores literal when passed to contentDescription`() {
        assertNoFindings("""Icon(painter = p, contentDescription = "token_icon")""")
    }

    @Test
    fun `ignores literal when it has no letters`() {
        listOf(
            """Text(text = "")""",
            """Text(text = "•")""",
            """Text(text = ".")""",
            """Text(text = ".${'$'}postfix")""",
            """Text(text = "\"${'$'}{echo.originalText}\"")""",
            """Text(text = "100%")""",
        ).forEach(::assertNoFindings)
    }

    @Test
    fun `ignores literal when it holds only escape sequences`() {
        listOf(
            """Text(text = "\n")""",
            """Text(text = "\t")""",
            """Text(text = "\u2022")""",
        ).forEach(::assertNoFindings)
    }

    @Test
    fun `ignores label when the call is outside a composable`() {
        val findings = lint(
            """
            suspend fun begin(id: ProductId) {
                workerRefCounter.acquire(id, label = "operation:x")
            }
            """
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `reports literal when the call sits in a lambda of a composable`() {
        val findings = lintComposable(
            """
            LazyColumn {
                item {
                    Text(text = "Hello")
                }
            }
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `ignores literal when the call suppresses HardcodedComposeText`() {
        val findings = lint(
            """
            @Composable
            private fun DebugPlanInfo() {
                @Suppress("HardcodedComposeText")
                NovaText(text = "[DEBUG] x")
                NovaText(text = "Later")
            }
            """
        )

        assertEquals(1, findings.size)
        assertEquals(5, findings.single().line)
    }

    @Test
    fun `ignores call when it has only a trailing lambda`() {
        assertNoFindings("Box { }")
        assertNoFindings("PolkadotTheme { }")
    }

    private fun assertReportsParameter(parameter: String) {
        val finding = lintComposable("""Foo($parameter = "Send")""").single()

        assertEquals(COMPOSABLE_BODY_LINE, finding.line)
        assertTrue(finding.message, finding.message.contains("'$parameter'"))
    }

    private fun assertNoFindings(body: String) {
        assertEquals(body, 0, lintComposable(body).size)
    }

    private fun lintComposable(body: String): List<Finding> = lint(
        """
        @Composable
        fun Screen() {
        ${body.trimIndent()}
        }
        """
    )

    private fun lint(code: String): List<Finding> = HardcodedComposeText(Config.empty).lint(code.trimIndent())

    private val Finding.line: Int
        get() = entity.location.source.line

    private companion object {
        const val COMPOSABLE_BODY_LINE = 3
    }
}
