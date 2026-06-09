package io.paritytech.polkadotapp.feature_dotns_impl.data.ipfs

interface CarFetcher {
    suspend fun fetchCar(contentHash: ByteArray): Result<ByteArray>
}
