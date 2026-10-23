package com.patchouli.deepseekwidget.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderTest {

    @Test
    fun `storage key resta stabile per i provider esistenti`() {
        assertEquals("deepseek", Provider.DEEPSEEK.storageKey)
        assertEquals("openai", Provider.OPENAI.storageKey)
    }

    @Test
    fun `valore sconosciuto ricade su DeepSeek senza crash`() {
        assertEquals(Provider.DEEPSEEK, Provider.fromStorage("sconosciuto"))
        assertEquals(Provider.DEEPSEEK, Provider.fromStorage(null))
    }

    @Test
    fun `fromStorage riconosce i due provider`() {
        assertEquals(Provider.DEEPSEEK, Provider.fromStorage("DeepSeek"))
        assertEquals(Provider.OPENAI, Provider.fromStorage("OPENAI"))
    }
}
