package com.template

import com.template.flow.CreateGameFlow
import com.template.flow.PlayGameFlow
import com.template2.flow.PlayGameEngineFlow
import net.corda.core.contracts.ContractState
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineFlowTests {

    private val network = MockNetwork(listOf("com.template", "com.template2", "net.corda.finance.contracts.asset", "net.corda.finance.schemas"))

    private val a = network.createPartyNode()
    private val b = network.createPartyNode()

    @Before
    fun setup() {
        network.runNetwork()

        a.registerInitiatedFlow(PlayGameFlow.Acceptor::class.java)
        b.registerInitiatedFlow(PlayGameEngineFlow.Acceptor::class.java)

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
    fun `winning game results in cash being received`() {

        // create game first
        val createFlow = CreateGameFlow.Initiator(b.info.legalIdentities.first())
        val createFuture = a.startFlow(createFlow)
        network.runNetwork()
        val gameStx = createFuture.getOrThrow()

        // play winning game
        val game = gameStx.coreTransaction.outputStates.first() as TicTacToeState
        val playFlow1 = PlayGameFlow.Initiator(game.linearId, intArrayOf(2,0))
        val playFuture1 = a.startFlow(playFlow1)
        network.runNetwork()

        playFuture1.getOrThrow()

        val playFlow2 = PlayGameFlow.Initiator(game.linearId, intArrayOf(2,1))
        val playFuture2 = a.startFlow(playFlow2)
        network.runNetwork()

        playFuture2.getOrThrow()

        val playFlow3 = PlayGameFlow.Initiator(game.linearId, intArrayOf(2,2))
        val playFuture3 = a.startFlow(playFlow3)
        network.runNetwork()

        val stx = playFuture3.getOrThrow()

        network.runNetwork()

        // We check the recorded transaction in both vaults.
        val cashStateA = a.services.vaultService.queryBy<ContractState>().states
        val cashStateB = b.services.vaultService.queryBy<ContractState>().states
    }
}