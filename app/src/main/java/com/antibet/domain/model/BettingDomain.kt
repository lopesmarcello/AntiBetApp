package com.antibet.domain.model


data class BettingDomain(
    val domain: String,
    val displayName: String,
    val category: String? = null // "betting", "cassino", etc
)

object BettingDomainList {
    // needs update
    val domains = listOf(
        BettingDomain("bet365.com", "Bet365", "betting"),
        BettingDomain("betano.com", "Betano", "betting"),
        BettingDomain("pixbet.com", "Pixbet", "betting"),
        BettingDomain("blaze.com", "Blaze", "casino"),
        BettingDomain("sportingbet.com", "Sportingbet", "betting"),
        BettingDomain("betfair.com", "Betfair", "betting"),
        BettingDomain("1xbet.com", "1xBet", "betting"),
        BettingDomain("22bet.com", "22Bet", "betting"),
        BettingDomain("rivalo.com", "Rivalo", "betting"),
        BettingDomain("betway.com", "Betway", "betting"),
        BettingDomain("betnacional.com", "Betnacional", "betting"),
        BettingDomain("esportedasorte.com", "Esporte da Sorte", "betting"),
        BettingDomain("parimatch.com", "Parimatch", "betting"),
        BettingDomain("novibet.com", "Novibet", "betting"),
        BettingDomain("betmotion.com", "Betmotion", "betting"),
        BettingDomain("f12.bet", "F12.bet", "betting"),
        BettingDomain("stake.com", "Stake", "casino"),
        BettingDomain("galera.bet", "Galera.bet", "betting"),
        BettingDomain("estrela.bet", "Estrela Bet", "betting"),
        BettingDomain("mrjack.bet", "Mr. Jack Bet", "betting"),
        )

    fun matchesDomain(queryDomain: String): BettingDomain? {
        val normalized = queryDomain.lowercase().removePrefix("www.")
        return domains.firstOrNull { domain ->
            normalized.endsWith(domain.domain) || normalized == domain.domain
        }
    }
}