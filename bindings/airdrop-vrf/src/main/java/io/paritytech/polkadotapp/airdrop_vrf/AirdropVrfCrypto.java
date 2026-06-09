package io.paritytech.polkadotapp.airdrop_vrf;

public class AirdropVrfCrypto {

    static {
        System.loadLibrary("airdrop_vrf_java");
    }

    // keypair: 96 bytes (rawSecretKey 64 ++ rawPublicKey 32); eventId: 32 bytes.
    // Returns 96 bytes: preOutput 32 ++ proof 64.
    public static native byte[] sign(byte[] keypair, byte[] eventId);
}
