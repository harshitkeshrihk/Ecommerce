package com.example.vishnu.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.PriceSource
import com.example.vishnu.model.Product
import com.example.vishnu.model.ResolvedPrice
import com.example.vishnu.model.RfqDraftLine
import com.example.vishnu.repository.PricingRepository
import com.example.vishnu.repository.ProductRepository
import com.example.vishnu.repository.ProfileRepository
import com.example.vishnu.repository.QuickOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickOrderViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val pricingRepository: PricingRepository,
    private val quickOrderRepository: QuickOrderRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products = _products.asStateFlow()

    // productId -> requested qty. Only entries with qty > 0 are "in the order."
    private val _quantities = MutableStateFlow<Map<String, Int>>(emptyMap())
    val quantities = _quantities.asStateFlow()

    // productId -> resolved unit price for the current qty at that product.
    private val _resolvedPrices = MutableStateFlow<Map<String, ResolvedPrice>>(emptyMap())
    val resolvedPrices = _resolvedPrices.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isPlacingOrder = MutableStateFlow(false)
    val isPlacingOrder = _isPlacingOrder.asStateFlow()

    sealed class QuickOrderEvent {
        data class Placed(val summaryText: String) : QuickOrderEvent()
        data class Failed(val message: String) : QuickOrderEvent()
    }

    private val _events = MutableSharedFlow<QuickOrderEvent>()
    val events = _events.asSharedFlow()

    // Derived from the same three sources draftLines()/the rows read, as a
    // proper Flow — a plain property re-read via viewModel.totalAmount inside
    // a @Composable is NOT tracked by Compose's snapshot system the way
    // collectAsState() is, so it could visibly lag one recomposition behind
    // what the rows were already showing.
    val totalAmount: StateFlow<Double> = combine(_quantities, _resolvedPrices, _products) { qtyMap, prices, products ->
        products.filter { qtyMap.containsKey(it.id) }.sumOf { product ->
            val qty = qtyMap[product.id] ?: 0
            (prices[product.id]?.unitPrice ?: product.priceWholesale) * qty
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    init {
        viewModelScope.launch {
            _isLoading.value = true
            _products.value = productRepository.getAllProducts()
            _isLoading.value = false
        }
    }

    fun setQuantity(product: Product, qty: Int) {
        val safeQty = qty.coerceAtLeast(0)
        _quantities.value = _quantities.value.toMutableMap().apply {
            if (safeQty == 0) remove(product.id) else put(product.id, safeQty)
        }
        if (safeQty > 0) {
            viewModelScope.launch {
                val resolved = pricingRepository.resolvePricing(product, safeQty, isWholesaleBuyer = true)
                // Rapid qty changes fire overlapping resolvePricing calls with
                // no guaranteed completion order. Only apply this result if
                // the qty is still what it was when this call was launched —
                // otherwise a late response for an older qty can overwrite
                // the price for the current one.
                if (_quantities.value[product.id] == safeQty) {
                    _resolvedPrices.value = _resolvedPrices.value + (product.id to resolved)
                }
            }
        }
    }

    fun draftLines(): List<RfqDraftLine> {
        val qtyMap = _quantities.value
        return _products.value
            .filter { qtyMap.containsKey(it.id) }
            .map { RfqDraftLine(it, qtyMap[it.id]!!) }
    }

    fun placeOrder() {
        val lines = draftLines()
        if (lines.isEmpty()) return
        viewModelScope.launch {
            _isPlacingOrder.value = true
            val profile = profileRepository.getUserProfile()
            val address = profile?.address ?: "Address not provided"
            val priceMap = _resolvedPrices.value.mapValues { it.value.unitPrice }

            val success = quickOrderRepository.placeOrder(lines, priceMap, address)
            if (success) {
                val summary = com.example.vishnu.utils.buildOrderSummaryText(
                    orderLabel = "Quick Order",
                    lines = lines.map {
                        com.example.vishnu.utils.OrderSummaryLine(
                            it.product.name, it.qty, priceMap[it.product.id] ?: it.product.priceWholesale
                        )
                    },
                    total = totalAmount.value
                )
                _events.emit(QuickOrderEvent.Placed(summary))
                _quantities.value = emptyMap()
                _resolvedPrices.value = emptyMap()
            } else {
                _events.emit(QuickOrderEvent.Failed("Could not place order. Please try again."))
            }
            _isPlacingOrder.value = false
        }
    }
}
