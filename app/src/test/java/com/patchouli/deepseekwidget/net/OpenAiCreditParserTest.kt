package com.patchouli.deepseekwidget.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class OpenAiCreditParserTest {

    private fun assertAmount(expected: String, actual: BigDecimal?) {
        assertNotNull(actual)
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    private fun success(body: String): CreditResult.Success {
        val result = OpenAiCreditParser.parse(body)
        assertTrue("atteso Success, trovato $result", result is CreditResult.Success)
        return result as CreditResult.Success
    }

    @Test
    fun `legge total_available numerico`() {
        val result = success(
            """{"object":"credit_summary","total_granted":18.0,"total_used":5.66,"total_available":12.34}"""
        )

        assertAmount("12.34", result.availableUsd)
        assertAmount("18.0", result.grantedUsd)
        assertAmount("5.66", result.usedUsd)
    }

    @Test
    fun `legge gli importi come stringhe`() {
        val result = success(
            """{"total_granted":"18.00","total_used":"5.66","total_available":"12.34"}"""
        )

        assertAmount("12.34", result.availableUsd)
        assertAmount("18.00", result.grantedUsd)
        assertAmount("5.66", result.usedUsd)
    }

    @Test
    fun `accetta il prefisso dollaro nelle stringhe`() {
        val result = success("""{"total_available":"$ 12.34"}""")

        assertAmount("12.34", result.availableUsd)
    }

    @Test
    fun `somma piu grant in data`() {
        val result = success(
            """{"object":"list","data":[
                 {"object":"credit_grant","grant_amount":10.0,"used_amount":2.5},
                 {"object":"credit_grant","grant_amount":"5.00","used_amount":"0.25"}]}"""
        )

        assertAmount("12.25", result.availableUsd)
        assertAmount("15", result.grantedUsd)
        assertAmount("2.75", result.usedUsd)
    }

    @Test
    fun `accetta un array nudo di grant`() {
        val result = success("""[{"grant_amount":"2.00","used_amount":"0.50"}]""")

        assertAmount("1.50", result.availableUsd)
    }

    @Test
    fun `ricava il disponibile da granted meno used se manca total_available`() {
        val result = success("""{"total_granted":10.0,"total_used":2.5}""")

        assertAmount("7.5", result.availableUsd)
        assertAmount("10.0", result.grantedUsd)
        assertAmount("2.5", result.usedUsd)
    }

    @Test
    fun `body html produce formato inatteso`() {
        val result = OpenAiCreditParser.parse(
            "<!doctype html><html><body>404 Not Found</body></html>"
        )

        assertTrue(result is CreditResult.Unavailable)
        assertEquals("formato inatteso", (result as CreditResult.Unavailable).detail)
    }

    @Test
    fun `body vuoto produce formato inatteso`() {
        assertTrue(OpenAiCreditParser.parse("") is CreditResult.Unavailable)
        assertTrue(OpenAiCreditParser.parse("   ") is CreditResult.Unavailable)
    }

    @Test
    fun `json senza campi utili produce formato inatteso`() {
        val result = OpenAiCreditParser.parse("""{"object":"credit_summary"}""")

        assertTrue(result is CreditResult.Unavailable)
        assertEquals("formato inatteso", (result as CreditResult.Unavailable).detail)
    }

    @Test
    fun `data vuoto produce formato inatteso`() {
        assertTrue(OpenAiCreditParser.parse("""{"data":[]}""") is CreditResult.Unavailable)
    }

    @Test
    fun `importo non numerico produce formato inatteso`() {
        assertTrue(
            OpenAiCreditParser.parse("""{"total_available":"non-un-numero"}""")
                    is CreditResult.Unavailable
        )
    }

    @Test
    fun `importo negativo produce formato inatteso`() {
        val result = OpenAiCreditParser.parse("""{"total_available":-0.01}""")

        assertTrue(result is CreditResult.Unavailable)
        assertEquals("formato inatteso", (result as CreditResult.Unavailable).detail)
    }

    @Test
    fun `importo oltre dieci milioni produce formato inatteso`() {
        val result = OpenAiCreditParser.parse("""{"total_available":10000000.01}""")

        assertTrue(result is CreditResult.Unavailable)
        assertEquals("formato inatteso", (result as CreditResult.Unavailable).detail)
    }

    @Test
    fun `accetta i limiti zero e dieci milioni`() {
        assertAmount("0", success("""{"total_available":0}""").availableUsd)
        assertAmount(
            "10000000",
            success("""{"total_available":10000000}""").availableUsd
        )
    }

    @Test
    fun `currency diversa da usd scarta la risposta`() {
        val result = OpenAiCreditParser.parse(
            """{"total_available":12.34,"currency":"eur"}"""
        )

        assertTrue(result is CreditResult.Unavailable)
        assertEquals("formato inatteso", (result as CreditResult.Unavailable).detail)
    }

    @Test
    fun `currency usd esplicita viene accettata`() {
        val result = success("""{"total_available":12.34,"currency":"USD"}""")

        assertAmount("12.34", result.availableUsd)
    }

    @Test
    fun `grant con currency diversa da usd scarta la risposta`() {
        val result = OpenAiCreditParser.parse(
            """{"data":[{"grant_amount":10.0,"used_amount":2.5,"currency":"eur"}]}"""
        )

        assertTrue(result is CreditResult.Unavailable)
        assertEquals("formato inatteso", (result as CreditResult.Unavailable).detail)
    }

    @Test
    fun `testo non json non lancia eccezioni`() {
        assertTrue(OpenAiCreditParser.parse("not json at all") is CreditResult.Unavailable)
        assertTrue(OpenAiCreditParser.parse("\u0000\u0001") is CreditResult.Unavailable)
    }
}
