package com.patchouli.deepseekwidget.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class AnthropicCostsParserTest {

    private fun body(buckets: String, nextPage: String? = null): String {
        val next = if (nextPage == null) "null" else "\"$nextPage\""
        return """{"data":[$buckets],"has_more":${nextPage != null},"next_page":$next}"""
    }

    @Test
    fun `converte i centesimi in dollari non arrotondati a 6 decimali`() {
        val json = body(
            """{"starting_at":"2025-08-01T00:00:00Z","ending_at":"2025-08-02T00:00:00Z","results":[
                 {"amount":"123.78912","currency":"USD","cost_type":"tokens"}]}"""
        )

        val page = AnthropicCostsParser.parsePage(json)

        assertEquals(1, page.buckets.size)
        assertEquals(BigDecimal("1.237891"), page.buckets[0].amount)
        assertEquals("USD", page.buckets[0].currency)
        assertEquals(0, page.skipped)
    }

    @Test
    fun `somma i results dello stesso bucket`() {
        val json = body(
            """{"starting_at":"2025-08-01T00:00:00Z","ending_at":"2025-08-02T00:00:00Z","results":[
                 {"amount":"123.78912","currency":"USD","cost_type":"tokens"},
                 {"amount":"1.00000","currency":"USD","cost_type":"tokens"}]}"""
        )

        val page = AnthropicCostsParser.parsePage(json)

        assertEquals(BigDecimal("1.247891"), page.buckets[0].amount)
    }

    @Test
    fun `bucket senza results vale zero`() {
        val json = body(
            """{"starting_at":"2025-08-01T00:00:00Z","ending_at":"2025-08-02T00:00:00Z","results":[]}"""
        )

        val page = AnthropicCostsParser.parsePage(json)

        assertEquals(1, page.buckets.size)
        assertEquals(0, BigDecimal.ZERO.compareTo(page.buckets[0].amount))
    }

    @Test
    fun `amount mancante viene scartato e conteggiato`() {
        val json = body(
            """{"starting_at":"2025-08-01T00:00:00Z","ending_at":"2025-08-02T00:00:00Z","results":[
                 {"currency":"USD"},
                 {"amount":"2.00000","currency":"USD"}]}"""
        )

        val page = AnthropicCostsParser.parsePage(json)

        assertEquals(BigDecimal("0.020000"), page.buckets[0].amount)
        assertEquals(1, page.skipped)
    }

    @Test
    fun `valuta diversa da USD viene scartata`() {
        val json = body(
            """{"starting_at":"2025-08-01T00:00:00Z","ending_at":"2025-08-02T00:00:00Z","results":[
                 {"amount":"9.00000","currency":"EUR"}]}"""
        )

        val page = AnthropicCostsParser.parsePage(json)

        assertEquals(0, BigDecimal.ZERO.compareTo(page.buckets[0].amount))
        assertEquals(1, page.skipped)
    }

    @Test
    fun `legge il cursore della pagina successiva`() {
        val json = body(
            """{"starting_at":"2025-08-01T00:00:00Z","ending_at":"2025-08-02T00:00:00Z","results":[]}""",
            nextPage = "page_xxx"
        )

        val page = AnthropicCostsParser.parsePage(json)

        assertEquals("page_xxx", page.nextPage)
    }

    @Test
    fun `pagina senza next_page restituisce null`() {
        val json = body(
            """{"starting_at":"2025-08-01T00:00:00Z","ending_at":"2025-08-02T00:00:00Z","results":[]}"""
        )

        assertNull(AnthropicCostsParser.parsePage(json).nextPage)
    }
}
