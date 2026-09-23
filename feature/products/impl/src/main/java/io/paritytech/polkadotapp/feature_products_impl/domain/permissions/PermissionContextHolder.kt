package io.paritytech.polkadotapp.feature_products_impl.domain.permissions

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionContextHolder @Inject constructor() {
    @Volatile
    private var context: ProductPermissionContext? = null

    fun set(context: ProductPermissionContext) {
        this.context = context
    }

    fun get(): ProductPermissionContext? = context

    // Owner-guarded: a sheet is cleared after its dismiss animation, when the holder may carry the next prompt
    fun clear(owner: ProductPermissionContext) {
        if (context === owner) {
            context = null
        }
    }
}
