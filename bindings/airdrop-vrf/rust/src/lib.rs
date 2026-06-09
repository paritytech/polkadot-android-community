#![allow(non_snake_case)]

extern crate jni;
extern crate merlin;
extern crate schnorrkel;

use jni::errors::Result as JniResult;
use jni::objects::JClass;
use jni::sys::jbyteArray;
use jni::JNIEnv;
use merlin::Transcript;
use schnorrkel::Keypair;
use std::ptr;

/// Merlin domain-separation label. MUST match the runtime's
/// `indiv_pallet_airdrop::vrf::VRF_TRANSCRIPT_LABEL`.
const VRF_TRANSCRIPT_LABEL: &[u8] = b"pop:airdrop";

macro_rules! try_or_throw {
    ($jni_env:ident, $expr:expr, $ret:expr) => {
        match $expr {
            JniResult::Ok(val) => val,
            JniResult::Err(err) => {
                $jni_env
                    .throw_new("java/lang/Exception", err.description())
                    .unwrap();
                return $ret;
            }
        }
    };
}

macro_rules! try_or_throw_null {
    ($jni_env:ident, $expr:expr) => {
        try_or_throw!($jni_env, $expr, ptr::null_mut())
    };
}

/// Builds the VRF transcript exactly as the runtime does in
/// `indiv_pallet_airdrop::vrf::transcript_for_event`:
/// label `"pop:airdrop"`, then `domain = label ++ event_id` and `signer = sr25519 public key`.
/// A byte-order or ordering slip here verifies fine locally but fails on-chain — covered by the
/// `transcript_matches_sp_core` test.
fn transcript_for_event(event_id: &[u8], public_key: &[u8]) -> Transcript {
    let mut domain = Vec::with_capacity(VRF_TRANSCRIPT_LABEL.len() + event_id.len());
    domain.extend_from_slice(VRF_TRANSCRIPT_LABEL);
    domain.extend_from_slice(event_id);

    let mut transcript = Transcript::new(b"pop:airdrop");
    transcript.append_message(b"domain", &domain);
    transcript.append_message(b"signer", public_key);
    transcript
}

/// Signs the airdrop VRF for an event id with a 96-byte schnorrkel keypair
/// (`rawSecretKey(64) ++ rawPublicKey(32)`). Returns the 96-byte output
/// `preOutput(32) ++ proof(64)`.
#[no_mangle]
fn Java_io_paritytech_polkadotapp_airdrop_1vrf_AirdropVrfCrypto_sign(
    jni_env: JNIEnv,
    _: JClass,
    keypair: jbyteArray,
    event_id: jbyteArray,
) -> jbyteArray {
    let keypair_bytes = try_or_throw_null!(jni_env, jni_env.convert_byte_array(keypair));
    let event_id_bytes = try_or_throw_null!(jni_env, jni_env.convert_byte_array(event_id));

    let keypair = match Keypair::from_bytes(&keypair_bytes) {
        Ok(keypair) => keypair,
        Err(_) => {
            jni_env
                .throw_new("java/lang/Exception", "invalid sr25519 keypair")
                .unwrap();
            return ptr::null_mut();
        }
    };

    let public_bytes = keypair.public.to_bytes();
    let transcript = transcript_for_event(&event_id_bytes, &public_bytes);

    let (in_out, proof, _) = keypair.vrf_sign(transcript);

    let mut out = Vec::with_capacity(96);
    out.extend_from_slice(&in_out.to_preout().to_bytes());
    out.extend_from_slice(&proof.to_bytes());

    try_or_throw_null!(jni_env, jni_env.byte_array_from_slice(&out))
}

#[cfg(test)]
mod tests {
    use super::*;
    use schnorrkel::{ExpansionMode, Keypair, MiniSecretKey};

    /// `event_id(game_index)` = `"pop:game:airdrop:" ++ 11 spaces ++ game_index.to_be_bytes()`
    /// = 28 + 4 = 32 bytes (matches the runtime's event id).
    fn event_id(game_index: u32) -> Vec<u8> {
        let mut id = b"pop:game:airdrop:           ".to_vec();
        id.extend_from_slice(&game_index.to_be_bytes());
        assert_eq!(id.len(), 32);
        id
    }

    #[test]
    fn signs_and_self_verifies() {
        let keypair = Keypair::generate();
        let eid = event_id(7);

        let (in_out, proof, _) =
            keypair.vrf_sign(transcript_for_event(&eid, &keypair.public.to_bytes()));

        let verify = keypair.public.vrf_verify(
            transcript_for_event(&eid, &keypair.public.to_bytes()),
            &in_out.to_preout(),
            &proof,
        );
        assert!(verify.is_ok());
    }

    /// Proves our hand-built merlin transcript is byte-identical to sp-core's `VrfTranscript`
    /// (the runtime's construction). If the two transcripts agree, the challenge bytes drawn from
    /// each are equal; any difference in label/order/bytes diverges here.
    #[test]
    fn transcript_matches_sp_core() {
        use sp_core::sr25519::vrf::VrfTranscript;

        let keypair = Keypair::generate();
        let eid = event_id(42);
        let public = keypair.public.to_bytes();

        let mut domain = VRF_TRANSCRIPT_LABEL.to_vec();
        domain.extend_from_slice(&eid);

        // sp-core's VrfTranscript wraps a merlin Transcript built the same way; `.0` is it.
        let sp_transcript: merlin::Transcript = VrfTranscript::new(
            b"pop:airdrop",
            &[
                (b"domain", domain.as_slice()),
                (b"signer", public.as_slice()),
            ],
        )
        .0;

        let mut ours = transcript_for_event(&eid, &public);

        let mut ours_challenge = [0u8; 32];
        let mut sp_challenge = [0u8; 32];
        ours.challenge_bytes(b"eq-check", &mut ours_challenge);
        let mut sp = sp_transcript;
        sp.challenge_bytes(b"eq-check", &mut sp_challenge);

        assert_eq!(ours_challenge, sp_challenge);
    }

    /// Full end-to-end parity with iOS (`airdrop_vrf_matches_sp_core_and_verifies`): the 96-byte
    /// output we emit must (a) have a pre-output byte-equal to sp-core's `Pair::vrf_sign`, and
    /// (b) decode as sp-core's `VrfSignature` and pass `vrf_verify` — i.e. the runtime would accept
    /// it. Catches signature/encoding drift a future schnorrkel bump could introduce, which the
    /// transcript-only test would miss.
    #[test]
    fn airdrop_vrf_matches_sp_core_and_verifies() {
        use parity_scale_codec::Decode;
        use sp_core::crypto::{VrfPublic, VrfSecret};
        use sp_core::sr25519::vrf::{VrfSignature, VrfTranscript};
        use sp_core::sr25519::Pair;
        use sp_core::Pair as _;

        let seed: [u8; 32] = *b"12345678901234567890123456789012";
        let eid = event_id(7);

        // Our signing path — identical to the JNI fn.
        let keypair = MiniSecretKey::from_bytes(&seed)
            .expect("mini secret key")
            .expand_to_keypair(ExpansionMode::Ed25519);
        let public = keypair.public.to_bytes();
        let (in_out, proof, _) = keypair.vrf_sign(transcript_for_event(&eid, &public));
        let mut out = Vec::with_capacity(96);
        out.extend_from_slice(&in_out.to_preout().to_bytes());
        out.extend_from_slice(&proof.to_bytes());

        // sp-core oracle.
        let pair = Pair::from_seed(&seed);
        let sp_public = pair.public();
        assert_eq!(public, sp_public.0, "public keys must match");

        let domain = [VRF_TRANSCRIPT_LABEL, eid.as_slice()].concat();
        let sign_data = VrfTranscript::new(
            b"pop:airdrop",
            &[(b"domain", domain.as_slice()), (b"signer", &sp_public.0)],
        )
        .into_sign_data();

        let reference = pair.vrf_sign(&sign_data);
        assert_eq!(
            &out[..32],
            &reference.pre_output.0.to_bytes()[..],
            "pre-output must match sp-core"
        );

        let signature = VrfSignature::decode(&mut &out[..]).expect("decode VrfSignature");
        assert!(
            sp_public.vrf_verify(&sign_data, &signature),
            "runtime must verify our signature"
        );
    }
}
