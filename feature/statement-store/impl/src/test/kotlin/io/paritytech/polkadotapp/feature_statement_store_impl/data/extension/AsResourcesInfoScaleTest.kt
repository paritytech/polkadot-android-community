package io.paritytech.polkadotapp.feature_statement_store_impl.data.extension

import io.novasama.substrate_sdk_android.runtime.definitions.types.composite.DictEnum
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_members_api.data.model.RingIndex
import io.paritytech.polkadotapp.feature_members_api.data.model.RingRevision
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

class AsResourcesInfoScaleTest {
    // The runtime's AsResourcesInfo is matched by variant name, so the name must equal the Rust variant exactly.
    @Test
    fun `notification variant encodes under the runtime variant name`() {
        val info = AsResourcesInfoScale.RegisterNotificationForCollection(
            proof = ByteArray(16) { 0x0B }.toDataByteArray(),
            ringIndex = RingIndex(BigInteger.valueOf(3)),
            revision = RingRevision(5),
            collection = MembershipCollectionScale.LitePeople,
        )

        val encoded = info.toEncodableInstance() as DictEnum.Entry<*>

        assertEquals("RegisterNotificationForCollection", encoded.name)
    }
}
