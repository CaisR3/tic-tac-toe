package com.template

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity

val DUMMY_LINEAR_ID = UniqueIdentifier.fromString("3a3be8e0-996f-4a9a-a654-e9560df52f14")
val PLAYER_1 = TestIdentity(CordaX500Name("Player1", "", "GB"))
val PLAYER_2 = TestIdentity(CordaX500Name("Player2", "", "GB"))