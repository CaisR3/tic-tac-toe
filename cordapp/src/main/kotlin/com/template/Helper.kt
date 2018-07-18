package com.template

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import java.lang.IllegalArgumentException

fun ServiceHub.firstNotary() = networkMapCache.notaryIdentities.first()

inline fun <reified T : ContractState> ServiceHub.getStateAndRefByLinearId(linearId: UniqueIdentifier): StateAndRef<T> {
    val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
    return vaultService.queryBy<T>(queryCriteria).states.single()
}