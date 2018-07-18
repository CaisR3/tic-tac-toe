package com.template

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
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
                val previousPlayer1Plays = input.board.flatten().count { it == 0 }
                val previousPlayer2Plays = input.board.flatten().count { it == 1 }
                val newPlayer1Plays = output.board.flatten().count { it == 0 }
                val newPlayer2Plays = output.board.flatten().count { it == 1 }
                requireThat {
                    "Game is not already complete" using (!input.complete)
                    "Move has been changed to next player" using (input.activePlayer != output.activePlayer)
                    "A move has to be made" using ( newPlayer1Plays > previousPlayer1Plays || newPlayer2Plays > previousPlayer2Plays)
                    "Move has been made by the right player" using (if(input.activePlayer == input.player1) newPlayer1Plays == newPlayer2Plays else newPlayer1Plays > newPlayer2Plays)
                    //"Game appropriately marked as complete"
                }
            }

            is Commands.Complete -> {
                requireThat {
                    "Player has won"
                }
            }
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands
        : CommandData {
        class Create : Commands
        class Play : Commands
        class Complete : Commands
    }
}

// *********
// * State *
// *********
data class TicTacToeState(
        val player1: Party,
        val player2: Party,
        val activePlayer: Party = player1,
        val board: Array<Array<Int>> = arrayOf(arrayOf(-1, -1, -1), arrayOf(-1, -1, -1), arrayOf(-1, -1, -1)),
        /*val row1Column1: Int = -1,
        val row1Column2: Int = -1,
        val row1Column3: Int = -1,
        val row2Column1: Int = -1,
        val row2Column2: Int = -1,
        val row2Column3: Int = -1,
        val row3Column1: Int = -1,
        val row3Column2: Int = -1,
        val row3Column3: Int = -1,*/
        val complete: Boolean = false,
        override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {
    override val participants: List<AbstractParty> get() = listOf(player1, player2)
}
