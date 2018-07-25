package com.template

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction

// *****************
// * Contract Code *
// *****************
// This is used to identify our contract when building a transaction
val TICTACTOE_CONTRACT_ID = "com.template.TicTacToeContract"

class TicTacToeContract : Contract {
    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Create -> {
                val output = tx.outputsOfType<TicTacToeState>().single()
                requireThat {
                    "No moves have yet been made" using output.board.all { it.all { it == -1 } }
                    "No input states in transaction" using (tx.inputStates.isEmpty())
                    "Only one output state in transaction" using (tx.outputStates.count() == 1)
                }
            }

            is Commands.Play -> {
                val output = tx.outputsOfType<TicTacToeState>().single()
                val input = tx.inputsOfType<TicTacToeState>().single()
                val flatInput = input.board.flatten()
                val flatOutput = output.board.flatten()
                val previousPlayer1Plays = flatInput.count { it == 0 }
                val previousPlayer2Plays = flatInput.count { it == 1 }
                val newPlayer1Plays = flatOutput.count { it == 0 }
                val newPlayer2Plays = flatOutput.count { it == 1 }
                requireThat {
                    "Game is not already complete" using (!input.complete)
                    "Move has been changed to next player" using (input.activePlayer != output.activePlayer)
                    "A move has to be made" using ((newPlayer1Plays + newPlayer2Plays) > (previousPlayer1Plays + previousPlayer2Plays))
                    "Move has been made by the right player" using (if(input.activePlayer == input.player2) newPlayer1Plays == newPlayer2Plays else newPlayer1Plays > newPlayer2Plays)
                    "Game appropriately marked as complete" using (isWinningPattern(output.board) == output.complete)
                    //TODO check correct winner has been set
                }
            }
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands
        : CommandData {
        class Create : Commands
        class Play : Commands
    }
}

// *********
// * State *
// *********
@CordaSerializable
data class TicTacToeState(
        val player1: Party,
        val player2: Party,
        val activePlayer: Party = player1,
        val board: Array<Array<Int>> = arrayOf(arrayOf(-1, -1, -1), arrayOf(-1, -1, -1), arrayOf(-1, -1, -1)),
        val complete: Boolean = false,
        val winner: Party? = null,
        override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {
    override val participants: List<AbstractParty> get() = listOf(player1, player2)
}

fun isWinningPattern(board: Array<Array<Int>>): Boolean {

    // rows
    if(board[0].all { it == 0 } || board[0].all { it == 1 }) return true
    if(board[1].all { it == 0 } || board[1].all { it == 1 }) return true
    if(board[2].all { it == 0 } || board[2].all { it == 1 }) return true

    // columns
    if(board.all { it[0] == 0 } || board.all { it[0] == 1 }) return true
    if(board.all { it[1] == 0 } || board.all { it[1] == 1 }) return true
    if(board.all { it[2] == 0 } || board.all { it[2] == 1 }) return true

    // diagonals
    if ((0..2).all { board[it][it] == 0 || board[it][it] == 1 }) return true
    if ((0..2).all { board[2 - it][it] == 0 || board[2 - it][it] == 1 }) return true

    return false
}