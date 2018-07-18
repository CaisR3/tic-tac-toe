package com.template

import com.template.flow.CreateGameFlow
import com.template.flow.PlayGameFlow
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class FlowTests {

    private val network = MockNetwork(listOf("com.template"))
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
        val game = createGame(a.info.legalIdentities.first(), b.info.legalIdentities.first())
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
}