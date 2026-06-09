package io.paritytech.polkadotapp.common.domain.validation

interface Validation<P> {
    context(ValidationProcess)
    suspend fun validate(payload: P): ValidationResult<P>
}
