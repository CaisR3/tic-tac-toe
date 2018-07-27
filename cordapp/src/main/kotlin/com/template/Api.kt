package com.template

import shared.com.template.flow.CreateGameFlow
import shared.com.template.flow.PlayGameFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.getCashBalances
import java.util.*
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("tictactoe")
class TemplateApi(val rpcOps: CordaRPCOps) {
    private val me = rpcOps.nodeInfo().legalIdentities.first()
    private val myLegalName = me.name


    // Accessible at /api/tictactoe/templateGetEndpoint.
    @GET
    @Path("templateGetEndpoint")
    @Produces(MediaType.APPLICATION_JSON)
    fun templateGetEndpoint(): Response {
        return Response.ok("Template GET endpoint.").build()
    }

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName.organisation)

    /**
     * Returns all parties registered with the network map.
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = rpcOps.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                .filter { it !in listOf(myLegalName) })
    }

    /**
     * Get the node's current cash balances.
     */
    @GET
    @Path("cash-balances")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCashBalances(): Map<Currency, Amount<Currency>> {
        val balance = rpcOps.getCashBalances()
        return if (balance.isEmpty()) {
            mapOf(Pair(Currency.getInstance("USD"), 0.DOLLARS))
        } else {
            balance
        }
    }

    /**
     * Displays single game state.
     */
    @GET
    @Path("game")
    @Produces(MediaType.APPLICATION_JSON)
    fun getGame(@QueryParam(value = "id") id: String) = rpcOps.vaultQueryBy<TicTacToeState>().states.filter { it.state.data.linearId == UniqueIdentifier.fromString(id)}.single().state.data


    /**
     * Displays all game states that exist in the node's vault.
     */
    @GET
    @Path("all-games")
    @Produces(MediaType.APPLICATION_JSON)
    fun getGames(): Map<String, List<TicTacToeState>> {
        val games = rpcOps.vaultQueryBy<TicTacToeState>().states
        println(games.count())
        return mapOf("games" to games.map { it.state.data })
    }

    /**
     * Displays all complete game states that exist in the node's vault.
     */
    @GET
    @Path("all-complete-games")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCompleteGames(): Map<String, List<TicTacToeState>> {
        val games = rpcOps.vaultQueryBy<TicTacToeState>().states.filter { it.state.data.complete == true }
        return mapOf("games" to games.map { it.state.data })
    }

    /**
     * Displays all incomplete game states that exist in the node's vault.
     */
    @GET
    @Path("all-incomplete-games")
    @Produces(MediaType.APPLICATION_JSON)
    fun getIncompleteGames(): Map<String, List<TicTacToeState>> {
        val games = rpcOps.vaultQueryBy<TicTacToeState>().states.filter { it.state.data.complete == false }
        return mapOf("games" to games.map { it.state.data })
    }

    /**
     * Creates a new game.
     */
    @GET
    @Path("create-game")
    @Produces(MediaType.APPLICATION_JSON)
    fun createGame(@QueryParam(value = "opponent") opponentName: CordaX500Name): Response {
        val opponent = rpcOps.wellKnownPartyFromX500Name(opponentName) ?: throw IllegalArgumentException("Unknown opponent.")
        // We try and create the game
        return try {
            val flowHandle = rpcOps.startFlow(CreateGameFlow::Initiator, opponent)
            val flowResult = flowHandle.returnValue.getOrThrow()
            // Return the response.
            Response.status(Response.Status.CREATED).entity(flowResult.tx.outputsOfType<TicTacToeState>().single().linearId).build()
        } catch (e: Exception) {
            // For the purposes of this demo app, we do not differentiate by exception type.
            Response.status(Response.Status.BAD_REQUEST).entity(e.message).build()
        }
    }

    /**
     * Makes a move.
     */
    @GET
    @Path("play-game")
    fun playGame(@QueryParam(value = "id") id: String,
                    @QueryParam(value = "row") row: Int,
                    @QueryParam(value = "column") column: Int): Response {

        return try {
            val move = intArrayOf(row, column)
            val linearId = UniqueIdentifier.fromString(id)
            val flowHandle = rpcOps.startFlow(PlayGameFlow::PlayGameInitiator, linearId, move)
            val flowResult = flowHandle.returnValue.getOrThrow()
            // Return the response.
            Response.status(Response.Status.CREATED).entity("Move played: ${flowResult.tx.outputsOfType<TicTacToeState>().single()}.").build()
        } catch (e: Exception) {
            // For the purposes of this demo app, we do not differentiate by exception type.
            Response.status(Response.Status.BAD_REQUEST).entity(e.message).build()
        }
    }

}