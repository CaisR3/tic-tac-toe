package com.template2.flow

import co.paralleluniverse.fibers.Suspendable
import com.template.TicTacToeState
import com.template.flow.PlayGameFlow
import com.template.flow.SelfIssueCashFlow
import com.template.isWinningPattern
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
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

                }
            }

            val stx = subFlow(signTransactionFlow)
            waitForLedgerCommit(stx.id)

            val ticTacToeState = stx.coreTransaction.outputStates.single() as TicTacToeState

            // check if player has won
            if(isWinningPattern(ticTacToeState.board)) {
                val builder = TransactionBuilder(stx.notary)
                // Issue cash to self before sending
                subFlow(SelfIssueCashFlow(1000.DOLLARS))
                // Send that cash to counterarty
                Cash.generateSpend(serviceHub, builder, 1000.DOLLARS, serviceHub.myInfo.legalIdentitiesAndCerts[0] ,otherPartyFlow.counterparty)
                builder.verify(serviceHub)

                val partSignedTx = serviceHub.signInitialTransaction(builder)

                val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), PlayGameFlow.Initiator.Companion.GATHERING_SIGS.childProgressTracker()))

                // Notarise and record the transaction in both parties' vaults.
                return subFlow(FinalityFlow(fullySignedTx))
            }

            val move = makeStupidMove(ticTacToeState.board)
            return subFlow(PlayGameFlow.Initiator(ticTacToeState.linearId, move))
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
