package com.template.flow

import co.paralleluniverse.fibers.Suspendable
import com.template.TICTACTOE_CONTRACT_ID
import com.template.TicTacToeContract
import com.template.TicTacToeState
import com.template.getStateAndRefByLinearId
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step

object PlayGameEngineFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val linearId: UniqueIdentifier, val move: IntArray) : FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new IOU.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val ticTacToeStateAndRef = serviceHub.getStateAndRefByLinearId<TicTacToeState>(linearId)
            val ticTacToeState = ticTacToeStateAndRef.state.data
            val txCommand = Command(TicTacToeContract.Commands.Play(), ticTacToeState.participants.map { it.owningKey })

            // are we player1 or player2
            val otherPlayer = if (ticTacToeState.player1 == ticTacToeState.activePlayer) ticTacToeState.player2 else ticTacToeState.player1
            val ourMarker = if (otherPlayer == ticTacToeState.player2) 0 else 1

            val currentStateOfPlay = ticTacToeState.board
            // move is expressed as row, column
            currentStateOfPlay[move[0]][move[1]] = ourMarker;

            // Let's flip who's go it is next and apply play
            val ticTacToeStateWithPlay = ticTacToeState.copy(activePlayer = otherPlayer, board = currentStateOfPlay)

            val txBuilder = TransactionBuilder(notary)
                    .addInputState(ticTacToeStateAndRef)
                    .addOutputState(ticTacToeStateWithPlay, TICTACTOE_CONTRACT_ID)
                    .addCommand(txCommand)

            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS
            // Send the state to the counterparty, and receive it back with their signature.
            val otherParty = ticTacToeStateWithPlay.activePlayer
            val otherPartyFlow = initiateFlow(otherParty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    stx.verify(serviceHub);

                    val output = stx.tx.outputs.single().data
                    "This must be a TicTacToe transaction." using (output is TicTacToeState)
                    val ticTacToe = output as TicTacToeState
                    //"I won't accept IOUs with a value over 100." using (iou.value <= 100)
                }
            }

            return subFlow(signTransactionFlow)
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
            return (0..2).all { board[it][it] == 0 || board[it][it] == 1 }
            return (0..2).all { board[2 - it][it] == 0 || board[2 - it][it] == 1 }
        }
    }
}
