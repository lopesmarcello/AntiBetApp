package com.antibet.domain.model


data class BettingDomain(
    val domain: String,
    val displayName: String,
    val category: String? = null // "betting", "cassino", etc
)

object BettingDomainList {
    val domains = listOf(
        // Casas de aposta .bet.br (legalizadas no Brasil - Fevereiro 2026)
        BettingDomain("betano.bet.br", "Betano", "betting"),
        BettingDomain("superbet.bet.br", "Superbet", "betting"),
        BettingDomain("magicjackpot.bet.br", "Magic Jackpot", "betting"),
        BettingDomain("super.bet.br", "Super.bet", "betting"),
        BettingDomain("reidopitaco.bet.br", "Rei do Pitaco", "betting"),
        BettingDomain("pitaco.bet.br", "Pitaco", "betting"),
        BettingDomain("rdp.bet.br", "RDP", "betting"),
        BettingDomain("sportingbet.bet.br", "Sportingbet", "betting"),
        BettingDomain("betboo.bet.br", "Betboo", "betting"),
        BettingDomain("big.bet.br", "Big.bet", "betting"),
        BettingDomain("apostar.bet.br", "Apostar.bet", "betting"),
        BettingDomain("caesars.bet.br", "Caesars", "betting"),
        BettingDomain("kto.bet.br", "KTO", "betting"),
        BettingDomain("betsson.bet.br", "Betsson", "betting"),
        BettingDomain("galera.bet.br", "Galera Bet", "betting"),
        BettingDomain("f12.bet.br", "F12.bet", "betting"),
        BettingDomain("luva.bet.br", "Luva.bet", "betting"),
        BettingDomain("brasilbet.bet.br", "Brasil Bet", "betting"),
        BettingDomain("sporty.bet.br", "SportyBet", "betting"),
        BettingDomain("lance.bet.br", "Lance.bet", "betting"),
        BettingDomain("estrelabet.bet.br", "Estrela Bet", "betting"),
        BettingDomain("reals.bet.br", "Reals Bet", "betting"),
        BettingDomain("ux.bet.br", "UX Bet", "betting"),
        BettingDomain("betz.nacional.bet.br", "Betz Nacional", "betting"),
        BettingDomain("mrjack.bet.br", "Mr. Jack Bet", "betting"),
        BettingDomain("tropino.bet.br", "Tropino", "betting"),
        BettingDomain("bz.bet.br", "BZ Bet", "betting"),
        BettingDomain("55w.bet.br", "55W", "betting"),
        BettingDomain("ice.bet.br", "ICE Bet", "betting"),
        BettingDomain("kbet.bet.br", "KBet", "betting"),
        BettingDomain("nossa.bet.br", "Nossa Bet", "betting"),
        BettingDomain("1xbet.bet.br", "1xBet", "betting"),
        BettingDomain("betcaixa.bet.br", "BetCaixa", "betting"),
        BettingDomain("megabet.bet.br", "MegaBet", "betting"),
        BettingDomain("xbetcaixa.bet.br", "XBet Caixeta", "betting"),
        BettingDomain("bau.bet.br", "Baú Bingo", "betting"),
        BettingDomain("milhao.bet.br", "Bet do Milhão", "betting"),
        BettingDomain("jogalimpo.bet.br", "Joga Limpo", "betting"),
        BettingDomain("energia.bet.br", "Energia Bet", "betting"),
        BettingDomain("mmabet.bet.br", "MMA Bet", "betting"),
        BettingDomain("betvip.bet.br", "Bet VIP", "betting"),
        BettingDomain("papigames.bet.br", "Papi Games", "betting"),
        BettingDomain("esportivavip.bet.br", "Esportiva VIP", "betting"),
        BettingDomain("cbesportes.bet.br", "CB Esportes", "betting"),
        BettingDomain("donosdabola.bet.br", "Donos da Bola", "betting"),
        BettingDomain("vert.bet.br", "Vert Bet", "betting"),
        BettingDomain("cgg.bet.br", "CGG Bet", "betting"),
        BettingDomain("fanbit.bet.br", "Fanbit", "betting"),
        
        // Domínios internacionais populares no Brasil
        BettingDomain("bet365.com", "Bet365", "betting"),
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
        BettingDomain("stake.com", "Stake", "casino"),
        BettingDomain("mrjack.bet", "Mr. Jack Bet", "betting"),
    )

    fun matchesDomain(queryDomain: String): BettingDomain? {
        val normalized = queryDomain.lowercase().removePrefix("www.")
        return domains.firstOrNull { domain ->
            normalized.endsWith(domain.domain) || normalized == domain.domain
        }
    }
}
