package io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.recovery

import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.LedgerEntry
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.PinnedChainView
import io.paritytech.polkadotapp.feature_transactions_impl.domain.durable.RuleOutcome
import io.paritytech.polkadotapp.feature_transactions_impl.domain.durable.evaluateLadder

/**
 * The ladder as it looked when it lived in this module, for the twenty-two cases that pin its behaviour.
 *
 * Production no longer calls this: the engine drives the ladder and coinage supplies only the oracle. It is
 * kept in the test source set so `CoinageRulesTest` asserts against the same function, with the same
 * arguments and the same return type, as before the split — which is what makes it evidence that the split
 * changed nothing rather than a test rewritten to match new behaviour.
 *
 * [recordedStillCanonical] was a field of [ChainEvidence] and is now resolved by the pass, batched across
 * every transaction carrying a record; the test passes it in where it used to set the field.
 */
suspend fun evaluateRules(
    entry: LedgerEntry,
    dag: CoinageEntryDag,
    evidence: ChainEvidence,
    view: PinnedChainView,
    recordedStillCanonical: Boolean? = null,
): RuleOutcome = evaluateLadder(
    tx = entry.facts,
    scope = CoinagePassScope(dag, mapOf(entry.id to evidence)),
    view = view,
    recordedStillCanonical = recordedStillCanonical,
)
