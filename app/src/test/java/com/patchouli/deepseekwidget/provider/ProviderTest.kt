package com.patchouli.deepseekwidget.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderTest {

    @Test
    fun `storage key resta stabile per i provider esistenti`() {
        assertEquals("deepseek", Provider.DEEPSEEK.storageKey)
        assertEquals("openai", Provider.OPENAI.storageKey)
        assertEquals("claude", Provider.CLAUDE.storageKey)
    }

    @Test
    fun `valore sconosciuto ricade su DeepSeek senza crash`() {
        assertEquals(Provider.DEEPSEEK, Provider.fromStorage("sconosciuto"))
        assertEquals(Provider.DEEPSEEK, Provider.fromStorage(null))
        assertEquals(Provider.CLAUDE, Provider.fromStorage("CLAUDE"))
    }
}
