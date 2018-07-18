package com.template

import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.utilities.days
import java.time.Instant

fun createGame(player1: Party, player2: Party) = TicTacToeState(
        player1 = player1,
        player2 = player2
)