package com.template.flow

import co.paralleluniverse.fibers.Suspendable
import com.template.TicTacToeState
import com.template.isWinningPattern
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import shared.com.template.flow.PlayGameFlow

object PlayGameEngineFlow {

    @InitiatedBy(PlayGameFlow.PlayGameInitiator::class)
    class Acceptor(val otherPartyFlow: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call(): Unit {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {

                }
            }

            val stx = subFlow(signTransactionFlow)
            waitForLedgerCommit(stx.id)

            val ticTacToeState = stx.coreTransaction.outputStates.single() as TicTacToeState

            // check if player has won - can we just rely on contract verification?
            if(isWinningPattern(ticTacToeState.board)) {
                val builder = TransactionBuilder(stx.notary)
                // Issue cash to self before sending
                subFlow(SelfIssueCashFlow(1000.DOLLARS))
                // Send that cash to counterparty
                Cash.generateSpend(serviceHub, builder, 1000.DOLLARS, serviceHub.myInfo.legalIdentitiesAndCerts[0] ,otherPartyFlow.counterparty)
                builder.verify(serviceHub)

                val partSignedTx = serviceHub.signInitialTransaction(builder)

                val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), PlayGameFlow.PlayGameInitiator.Companion.GATHERING_SIGS.childProgressTracker()))

                // Notarise and record the transaction in both parties' vaults.
                subFlow(FinalityFlow(fullySignedTx))
            }

            if(!ticTacToeState.complete) {
                val move = makeStupidMove(ticTacToeState.board)
                subFlow(PlayGameFlow.PlayGameInitiator(ticTacToeState.linearId, move))
            }
        }

        fun makeStupidMove(board: Array<Array<Int>>): IntArray {
            // find row and column with first available play
            var rowCount = 0
            var columnCount = 0
            for (row in board) {
                columnCount = row.indexOf(-1)
                if(columnCount != -1) break
                rowCount = rowCount + 1
            }
            return intArrayOf(rowCount, columnCount)
        }
    }
}
