package com.otto.launcher.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroqResponseParserTest {
    @Test fun `valid body yields content`() {
        assertEquals("{\"ok\":true}", parseChatContent("""{"choices":[{"message":{"content":"{\"ok\":true}"}}]}"""))
    }

    @Test fun `prose around json is accepted`() {
        assertEquals("value", extractJsonObject("Here: {\"key\":\"value\"}. Done")?.optString("key"))
    }

    @Test fun `malformed json is rejected`() = assertNull(extractJsonObject("{broken}"))

    @Test fun `empty choices yields no content`() = assertNull(parseChatContent("""{"choices":[]}"""))

    @Test fun `blank content yields no content`() {
        assertNull(parseChatContent("""{"choices":[{"message":{"content":""}}]}"""))
    }
}
