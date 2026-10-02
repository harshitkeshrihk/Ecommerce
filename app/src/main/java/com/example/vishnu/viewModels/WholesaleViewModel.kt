package com.example.vishnu.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.MoqSlab
import com.example.vishnu.model.Product
import com.example.vishnu.model.isBusinessBuyer
import com.example.vishnu.repository.PricingRepository
import com.example.vishnu.repository.ProductRepository
import com.example.vishnu.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A product shown in the Wholesale section, with its slabs lowest quantity first. */
data class WholesaleProduct(val product: Product, val slabs: List<MoqSlab>)

/** What the Wholesale section tells this viewer about ordering at slab prices. */
enum class WholesaleAccess { BUSINESS, KYC_PENDING, NONE }

@HiltViewModel
class WholesaleViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val pricingRepository: PricingRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _products = MutableStateFlow<List<WholesaleProduct>>(emptyList())
    val products = _products.asStateFlow()

    // Null until the profile loads, so the access banner doesn't flash.
    private val _access = MutableStateFlow<WholesaleAccess?>(null)
    val access = _access.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    /**
     * Called each time the screen is shown, so a KYC approval made while
     * the app was open shows up without restarting.
     */
    fun load() {
        viewModelScope.launch {
            val products = async { productRepository.getAllProducts() }
            val slabs = async { pricingRepository.getAllSlabs() }
            val profile = async { profileRepository.getUserProfile() }

            val slabMap = slabs.await()
            _products.value = products.await()
                .mapNotNull { product ->
                    slabMap[product.id]?.takeIf { it.isNotEmpty() }
                        ?.let { WholesaleProduct(product, it.sortedBy { slab -> slab.minQty }) }
                }
            _access.value = profile.await().let {
                when {
                    it.isBusinessBuyer() -> WholesaleAccess.BUSINESS
                    it?.kycStatus == "pending" -> WholesaleAccess.KYC_PENDING
                    else -> WholesaleAccess.NONE
                }
            }
            _isLoading.value = false
        }
    }
}
