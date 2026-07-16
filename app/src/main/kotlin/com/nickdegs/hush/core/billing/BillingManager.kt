package com.nickdegs.hush.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Kademe: alan limiti + görünen ad (iOS StoreManager.Tier ile birebir). */
enum class HushTier(val rank: Int, val spaceLimit: Int, val display: String) {
    NONE(0, 0, "—"), PRO(1, 1, "Hush Pro"), PROPLUS(2, 3, "Hush Pro+"), ULTRA(3, 5, "Hush Ultra")
}

/**
 * Google Play Billing (kademeli abonelik). Ürünler: hush_pro / hush_proplus / hush_ultra,
 * her biri aylık + yıllık base plan. Aktif aboneliğin purchaseToken + productId'si backend
 * doğrulaması (App Store Server API'nin Android karşılığı) için tutulur.
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {

    companion object {
        const val PRO = "hush_pro"
        const val PROPLUS = "hush_proplus"
        const val ULTRA = "hush_ultra"
        val PRODUCTS = listOf(PRO, PROPLUS, ULTRA)
        fun tierOf(productId: String) = when (productId) {
            PRO -> HushTier.PRO; PROPLUS -> HushTier.PROPLUS; ULTRA -> HushTier.ULTRA; else -> HushTier.NONE
        }
        // Kademe + dönem → base plan id
        fun basePlan(tier: HushTier, yearly: Boolean): String = when (tier) {
            HushTier.PRO -> if (yearly) "pro-yearly" else "pro-monthly"
            HushTier.PROPLUS -> if (yearly) "proplus-yearly" else "proplus-monthly"
            HushTier.ULTRA -> if (yearly) "ultra-yearly" else "ultra-monthly"
            HushTier.NONE -> ""
        }
        fun productFor(tier: HushTier) = when (tier) {
            HushTier.PRO -> PRO; HushTier.PROPLUS -> PROPLUS; HushTier.ULTRA -> ULTRA; HushTier.NONE -> ""
        }
    }

    private val _tier = MutableStateFlow(HushTier.NONE)
    val tier: StateFlow<HushTier> = _tier.asStateFlow()
    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    /** Aktif aboneliğin backend doğrulaması için gerekenler. */
    @Volatile var purchaseToken: String? = null; private set
    @Volatile var productId: String? = null; private set

    val isPro: Boolean get() = _tier.value != HushTier.NONE
    val spaceLimit: Int get() = _tier.value.spaceLimit

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start() {
        if (client.isReady) { queryProducts(); refresh(); return }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode == BillingClient.BillingResponseCode.OK) { queryProducts(); refresh() }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            PRODUCTS.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
            }
        ).build()
        client.queryProductDetailsAsync(params) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) _products.value = list
        }
    }

    fun refresh() {
        if (!client.isReady) return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        client.queryPurchasesAsync(params) { _, purchases ->
            var best = HushTier.NONE; var tok: String? = null; var pid: String? = null
            for (p in purchases) {
                if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
                if (!p.isAcknowledged) acknowledge(p)
                for (prod in p.products) {
                    val t = tierOf(prod)
                    if (t.rank > best.rank) { best = t; tok = p.purchaseToken; pid = prod }
                }
            }
            purchaseToken = tok; productId = pid; _tier.value = best
        }
    }

    private fun acknowledge(p: Purchase) {
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
        ) {}
    }

    /** Kademe + dönem satın al. */
    fun purchase(activity: Activity, tier: HushTier, yearly: Boolean) {
        val prod = productFor(tier)
        val pd = _products.value.firstOrNull { it.productId == prod } ?: return
        val bp = basePlan(tier, yearly)
        val offer = pd.subscriptionOfferDetails?.firstOrNull { it.basePlanId == bp } ?: return
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(pd).setOfferToken(offer.offerToken).build()
            )
        ).build()
        client.launchBillingFlow(activity, params)
    }

    /** Paywall fiyatı: kademe + dönem → formatlanmış yerel fiyat (ör. "₺349,99"). */
    fun priceOf(tier: HushTier, yearly: Boolean): String? {
        val pd = _products.value.firstOrNull { it.productId == productFor(tier) } ?: return null
        val offer = pd.subscriptionOfferDetails?.firstOrNull { it.basePlanId == basePlan(tier, yearly) } ?: return null
        return offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice
    }

    override fun onPurchasesUpdated(r: BillingResult, purchases: MutableList<Purchase>?) {
        if (r.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (p in purchases) if (!p.isAcknowledged && p.purchaseState == Purchase.PurchaseState.PURCHASED) acknowledge(p)
            refresh()
        }
    }
}
