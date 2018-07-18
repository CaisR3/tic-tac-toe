package com.template

import net.corda.testing.node.MockServices
import org.junit.Test
import java.time.Duration
import java.time.Instant
import net.corda.testing.node.*

class ContractTests {
    private val ledgerServices = MockServices()

    @Test
    fun `create game`() {
        val game = createGame(PLAYER_1.party, PLAYER_2.party)
        ledgerServices.ledger {
            transaction("Player1 creates game") {
                output(TICTACTOE_CONTRACT_ID, game)
                command(listOf(PLAYER_1.publicKey, PLAYER_2.publicKey), TicTacToeContract.Commands.Create())
                timeWindow(Instant.now(), Duration.ofSeconds(60))
                verifies()
            }
        }
    }

    @Test
    fun `create and play game`() {
        val game = createGame(PLAYER_1.party, PLAYER_2.party)
        ledgerServices.ledger {
            transaction("Player1 creates game") {
                output(TICTACTOE_CONTRACT_ID, "new game", game)
                command(listOf(PLAYER_1.publicKey, PLAYER_2.publicKey), TicTacToeContract.Commands.Create())
                timeWindow(Instant.now(), Duration.ofSeconds(60))
                verifies()
            }

            transaction("Player doesn't switch active player") {
                input("new game")
                output(TICTACTOE_CONTRACT_ID, "game with play", "new game".output<TicTacToeState>().copy(activePlayer = PLAYER_1.party))
                command(listOf(PLAYER_1.publicKey, PLAYER_2.publicKey), TicTacToeContract.Commands.Play())
                timeWindow(Instant.now(), Duration.ofSeconds(60))
                fails()
            }

            transaction("Player makes tries to skip by switching active player") {
                input("new game")
                output(TICTACTOE_CONTRACT_ID, "game with play 2", "new game".output<TicTacToeState>().copy(activePlayer = PLAYER_2.party))
                command(listOf(PLAYER_1.publicKey, PLAYER_2.publicKey), TicTacToeContract.Commands.Play())
                timeWindow(Instant.now(), Duration.ofSeconds(60))
                fails()
            }

            transaction("Player makes move and switches") {
                input("new game")
                val board = "new game".output<TicTacToeState>().board
                // play naught in first row, second column
                board[0][1] = 0
                output(TICTACTOE_CONTRACT_ID, "game with play 3", "new game".output<TicTacToeState>().copy(activePlayer = PLAYER_2.party, board = board))
                command(listOf(PLAYER_1.publicKey, PLAYER_2.publicKey), TicTacToeContract.Commands.Play())
                timeWindow(Instant.now(), Duration.ofSeconds(60))
                verifies()
            }
        }
    }
}