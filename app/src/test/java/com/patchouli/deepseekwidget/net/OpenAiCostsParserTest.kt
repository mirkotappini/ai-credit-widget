package com.patchouli.deepseekwidget.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class OpenAiCostsParserTest {

    private fun body(buckets: String, nextPage: String? = null): String {
        val next = if (nextPage == null) "null" else "\"$nextPage\""
        return """{"object":"page","data":[$buckets],"has_more":${nextPage != null},"next_page":$next}"""
    }

    @Test
    fun `somma i results dello stesso bucket`() {
        val json = body(
            """{"object":"bucket","start_time":100,"end_time":200,"results":[
                 {"object":"organization.costs.result","amount":{"value":0.06,"currency":"usd"}},
                 {"object":"organization.costs.result","amount":{"value":0.04,"currency":"usd"}}]}"""
        )
        val page = OpenAiCostsParser.parsePage(json)

        assertEquals(1, page.buckets.size)
        assertEquals(BigDecimal("0.10"), page.buckets[0].amount)
        assertEquals(100L, page.buckets[0].startTsUtc)
        assertEquals(0, page.skipped)
        assertNull(page.nextPage)
    }

    @Test
    fun `bucket senza results resta presente a zero`() {
        val json = body("""{"object":"bucket","start_time":100,"end_time":200,"results":[]}""")

        val page = OpenAiCostsParser.parsePage(json)

        assertEquals(1, page.buckets.size)
        assertEquals(BigDecimal.ZERO, page.buckets[0].amount)
    }

    @Test
    fun `amount nullo viene scartato e conteggiato`() {
        val json = body(
            """{"object":"bucket","start_time":100,"end_time":200,"results":[
                 {"object":"organization.costs.result","amount":null},
                 {"object":"organization.costs.result","amount":{"value":1.5,"currency":"usd"}}]}"""
        )
        val page = OpenAiCostsParser.parsePage(json)

        assertEquals(BigDecimal("1.5"), page.buckets[0].amount)
        assertEquals(1, page.skipped)
    }

    @Test
    fun `valuta diversa da usd viene scartata`() {
        val json = body(
            """{"object":"bucket","start_time":100,"end_time":200,"results":[
                 {"object":"organization.costs.result","amount":{"value":9.99,"currency":"eur"}}]}"""
        )
        val page = OpenAiCostsParser.parsePage(json)

        assertEquals(BigDecimal.ZERO, page.buckets[0].amount)
        assertEquals("usd", page.buckets[0].currency)
        assertEquals(1, page.skipped)
    }

    @Test
    fun `bucket malformato senza end_time viene ignorato`() {
        val json = body(
            """{"object":"bucket","start_time":100,"results":[{"amount":{"value":1,"currency":"usd"}}]}"""
        )

        val page = OpenAiCostsParser.parsePage(json)

        assertTrue(page.buckets.isEmpty())
    }

    @Test
    fun `legge il cursore next_page`() {
        val json = body(
            """{"object":"bucket","start_time":100,"end_time":200,"results":[]}""",
            nextPage = "cursor_abc"
        )

        val page = OpenAiCostsParser.parsePage(json)

        assertEquals("cursor_abc", page.nextPage)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `data assente lancia eccezione`() {
        OpenAiCostsParser.parsePage("""{"object":"page","next_page":null}""")
    }

    @Test
    fun `errore http riporta il messaggio dell api`() {
        val body = """{"error":{"message":"Invalid value for 'bucket_width'.","type":"invalid_request_error","param":"bucket_width"}}"""

        assertEquals(
            "HTTP 400: Invalid value for 'bucket_width'.",
            OpenAiCostsParser.errorDetail(400, body)
        )
    }

    @Test
    fun `errore http senza body resta solo il codice`() {
        assertEquals("HTTP 500", OpenAiCostsParser.errorDetail(500, null))
        assertEquals("HTTP 502", OpenAiCostsParser.errorDetail(502, "  "))
        assertEquals("HTTP 400", OpenAiCostsParser.errorDetail(400, "<html>bad</html>"))
    }
}
