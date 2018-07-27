package shared.com.template.flow

import co.paralleluniverse.fibers.Suspendable
import com.template.*
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step

object PlayGameFlow {
    @InitiatingFlow
    @StartableByRPC
    class PlayGameInitiator(val linearId: UniqueIdentifier, val move: IntArray) : FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new play.")
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
            val ticTacToeStateAndRef = serviceHub.getStateAndRefByLinearId<TicTacToeState>(linearId) ?: throw FlowException("No game found with provided id");

            val ticTacToeState = ticTacToeStateAndRef.state.data
            val txCommand = Command(TicTacToeContract.Commands.Play(), ticTacToeState.participants.map { it.owningKey })

            // are we player1 or player2
            val otherPlayer = if (ticTacToeState.player1 == ticTacToeState.activePlayer) ticTacToeState.player2 else ticTacToeState.player1
            val ourMarker = if (otherPlayer == ticTacToeState.player2) 0 else 1

            val currentStateOfPlay = ticTacToeState.board
            // move is expressed as row, column
            currentStateOfPlay[move[0]][move[1]] = ourMarker;

            //If our next play is a winning move, we mark the game as complete and set ourselves as the winner
            val completeWithWinner = isWinningPattern(currentStateOfPlay)
            val complete = noMoreMoves(currentStateOfPlay) || completeWithWinner
            val winner = if(completeWithWinner) serviceHub.myInfo.legalIdentities[0] else null

            // Let's flip who's go it is next and apply play
            val ticTacToeStateWithPlay = ticTacToeState.copy(activePlayer = otherPlayer, board = currentStateOfPlay, complete = complete, winner = winner)

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
}
