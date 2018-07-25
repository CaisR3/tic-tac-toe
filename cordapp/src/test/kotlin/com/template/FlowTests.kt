package com.template

import com.template.flow.CreateGameFlow
import com.template.flow.PlayGameFlow
import net.corda.core.flows.FlowException
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class FlowTests {

    private val network = MockNetwork(listOf("com.template", "net.corda.finance.contracts.asset"))
    private val a = network.createPartyNode()
    private val b = network.createPartyNode()

    @Before
    fun setup() {
        network.runNetwork()

        listOf(a, b).forEach {
            it.registerInitiatedFlow(PlayGameFlow.Acceptor::class.java)
        }

        network.runNetwork()
    }

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `game create flow records a correctly-formed transaction in both parties' transaction storages`() {
        val flow = CreateGameFlow.Initiator(b.info.legalIdentities.first())
        val future = a.startFlow(flow)
        network.runNetwork()

        // We check the recorded transaction in both vaults.
        val stx = future.getOrThrow()
        listOf(a, b).forEach { node ->
            assertEquals(stx, node.services.validatedTransactions.getTransaction(stx.id))

            val ltx = node.transaction {
                stx.toLedgerTransaction(node.services)
            }

            // A single game state output
            assertEquals(1, ltx.outputsOfType<TicTacToeState>().size)

            // One command.
            assertEquals(1, ltx.commands.size)

            // An OptionContract.Commands.Issue command with the correct attributes.
            val createCmd = ltx.commandsOfType<TicTacToeContract.Commands.Create>().single()
            assert(createCmd.signers.containsAll(listOf(a.info.legalIdentities.first().owningKey, b.info.legalIdentities.first().owningKey)))

        }
    }

    @Test
    fun `game play flow records a correctly-formed transaction in both parties' transaction storages`() {

        // create game first
        val createFlow = CreateGameFlow.Initiator(b.info.legalIdentities.first())
        val createFuture = a.startFlow(createFlow)
        network.runNetwork()
        val gameStx = createFuture.getOrThrow()

        // play game
        val game = gameStx.coreTransaction.outputStates.first() as TicTacToeState
        val move = intArrayOf(0,0)
        val playFlow = PlayGameFlow.Initiator(game.linearId, move)
        val playFuture = a.startFlow(playFlow)
        network.runNetwork()

        // We check the recorded transaction in both vaults.
        val stx = playFuture.getOrThrow()
        listOf(a, b).forEach { node ->
            assertEquals(stx, node.services.validatedTransactions.getTransaction(stx.id))

            val ltx = node.transaction {
                stx.toLedgerTransaction(node.services)
            }

            // A single game state output
            assertEquals(1, ltx.outputsOfType<TicTacToeState>().size)

            // One command.
            assertEquals(1, ltx.commands.size)

            // An OptionContract.Commands.Issue command with the correct attributes.
            val playCmd = ltx.commandsOfType<TicTacToeContract.Commands.Play>().single()
            assert(playCmd.signers.containsAll(listOf(a.info.legalIdentities.first().owningKey, b.info.legalIdentities.first().owningKey)))

        }
    }

    @Test
    fun `valid back and forth game play flow doesn't throw an exception and no winner`() {

        // create game first
        val createFlow = CreateGameFlow.Initiator(b.info.legalIdentities.first())
        val createFuture = a.startFlow(createFlow)
        network.runNetwork()
        val gameStx = createFuture.getOrThrow()

        // player1 plays game
        val game = gameStx.coreTransaction.outputStates.first() as TicTacToeState
        val move = intArrayOf(0,0)
        val playFlow = PlayGameFlow.Initiator(game.linearId, move)
        val playFuture = a.startFlow(playFlow)
        network.runNetwork()
        playFuture.getOrThrow()

        // player2 plays game
        val move2 = intArrayOf(1,1)
        val playFlow2 = PlayGameFlow.Initiator(game.linearId, move2)
        val playFuture2 = b.startFlow(playFlow2)
        network.runNetwork()
        playFuture2.getOrThrow()

        // player1 plays game
        val move3 = intArrayOf(2,2)
        val playFlow3 = PlayGameFlow.Initiator(game.linearId, move3)
        val playFuture3 = a.startFlow(playFlow3)
        network.runNetwork()
        playFuture3.getOrThrow()

        val state = a.services.vaultService.queryBy<TicTacToeState>().states.single().state.data
        assert(state.board[0][0] == 0)
        assert(state.board[1][1] == 1)
        assert(state.board[2][2] == 0)
        assert(state.complete == false)
        assert(state.winner == null)
    }

    @Test(expected = FlowException::class)
    fun `invalid back and forth game play flow throws an exception`() {

        // create game first
        val createFlow = CreateGameFlow.Initiator(b.info.legalIdentities.first())
        val createFuture = a.startFlow(createFlow)
        network.runNetwork()
        val gameStx = createFuture.getOrThrow()

        // player1 plays game
        val game = gameStx.coreTransaction.outputStates.first() as TicTacToeState
        val move = intArrayOf(0,0)
        val playFlow = PlayGameFlow.Initiator(game.linearId, move)
        val playFuture = a.startFlow(playFlow)
        network.runNetwork()
        playFuture.getOrThrow()

        // player2 plays game
        // Try and make same move as player 1
        val playFlow2 = PlayGameFlow.Initiator(game.linearId, move)
        val playFuture2 = b.startFlow(playFlow2)
        network.runNetwork()
        playFuture2.getOrThrow()
    }
}