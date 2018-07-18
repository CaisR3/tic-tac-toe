package com.template.flow

import co.paralleluniverse.fibers.Suspendable
import com.template.TicTacToeState
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash

object PlayGameEngineFlow {

    //@InitiatedBy(PlayGameFlow.Initiator::class)
    class Acceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    stx.verify(serviceHub);

                    val output = stx.tx.outputs.single().data
                    "This must be a TicTacToe transaction." using (output is TicTacToeState)
                    val ticTacToeState = output as TicTacToeState

                    // check if player has won
                    if(isWinningPattern(ticTacToeState.board)) {
                        val builder = TransactionBuilder(stx.notary)
                        Cash.generateSpend(serviceHub, builder, 1000.DOLLARS, otherPartyFlow.counterparty)
                    }
                }
            }

            return subFlow(signTransactionFlow)
        }

        fun makeStupidMove(board: Array<Array<Int>>): Array<Array<Int>> {
            // find row and column with first available play
            var rowCount = 0
            var columnCount = 0
            for (row in board) {
                columnCount = row.indexOf(-1)
                if(columnCount != -1) break
                rowCount = rowCount + 1
            }
            board[rowCount][columnCount] = 1
            return board
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

        fun noMoreMoves(board: Array<Array<Int>>): Boolean {
            return !board.flatten().any { it == -1 }
        }
    }
}
