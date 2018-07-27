package com.template.flow

import co.paralleluniverse.fibers.Suspendable
import shared.com.template.flow.PlayGameFlow
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction

object PlayGameFlowResponder {

    @InitiatedBy(PlayGameFlow.PlayGameInitiator::class)
    class Acceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    //stx.verify(serviceHub);
                }
            }

            return subFlow(signTransactionFlow)
        }
    }
}
