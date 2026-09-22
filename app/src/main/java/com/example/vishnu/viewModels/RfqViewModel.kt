package com.example.vishnu.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.Product
import com.example.vishnu.model.Quote
import com.example.vishnu.model.Rfq
import com.example.vishnu.model.RfqDraftLine
import com.example.vishnu.repository.ProductRepository
import com.example.vishnu.repository.ProfileRepository
import com.example.vishnu.repository.RfqRepository
import com.example.vishnu.repository.RfqSubmitResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RfqViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val rfqRepository: RfqRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products = _products.asStateFlow()

    private val _quantities = MutableStateFlow<Map<String, Int>>(emptyMap())
    val quantities = _quantities.asStateFlow()

    var neededByDate = MutableStateFlow("") // yyyy-MM-dd, free text for now
    var notes = MutableStateFlow("")

    private val _myRfqs = MutableStateFlow<List<Rfq>>(emptyList())
    val myRfqs = _myRfqs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()

    sealed class RfqEvent {
        object Submitted : RfqEvent()
        data class Error(val message: String) : RfqEvent()
        data class OrderConfirmed(val summaryText: String) : RfqEvent()
    }

    private val _events = MutableSharedFlow<RfqEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            _isLoading.value = true
            _products.value = productRepository.getAllProducts()
            _isLoading.value = false
        }
        loadMyRfqs()
    }

    fun setQuantity(product: Product, qty: Int) {
        val safeQty = qty.coerceAtLeast(0)
        _quantities.value = _quantities.value.toMutableMap().apply {
            if (safeQty == 0) remove(product.id) else put(product.id, safeQty)
        }
    }

    private fun draftLines(): List<RfqDraftLine> {
        val qtyMap = _quantities.value
        return _products.value.filter { qtyMap.containsKey(it.id) }.map { RfqDraftLine(it, qtyMap[it.id]!!) }
    }

    fun submitRfq() {
        val lines = draftLines()
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = rfqRepository.submitRfq(lines, neededByDate.value.ifBlank { null }, notes.value.ifBlank { null })
            when (result) {
                is RfqSubmitResult.Success -> {
                    _events.emit(RfqEvent.Submitted)
                    _quantities.value = emptyMap()
                    notes.value = ""
                    neededByDate.value = ""
                    loadMyRfqs()
                }
                is RfqSubmitResult.Error -> _events.emit(RfqEvent.Error(result.message))
            }
            _isSubmitting.value = false
        }
    }

    fun loadMyRfqs() {
        viewModelScope.launch {
            _myRfqs.value = rfqRepository.getMyRfqs()
        }
    }

    fun acceptQuote(rfq: Rfq, quote: Quote) {
        viewModelScope.launch {
            val profile = profileRepository.getUserProfile()
            val address = profile?.address ?: "Address not provided"
            val success = rfqRepository.acceptQuote(rfq, quote, address)
            if (success) {
                val summary = com.example.vishnu.utils.buildOrderSummaryText(
                    orderLabel = "RFQ ${rfq.id.take(8)}",
                    lines = rfq.items.map {
                        com.example.vishnu.utils.OrderSummaryLine(it.product.name, it.qty, quote.priceFor(it.id) ?: 0.0)
                    },
                    total = rfq.items.sumOf { it.qty * (quote.priceFor(it.id) ?: 0.0) }
                )
                _events.emit(RfqEvent.OrderConfirmed(summary))
                loadMyRfqs()
            } else {
                _events.emit(RfqEvent.Error("Could not confirm the order. Please try again."))
            }
        }
    }
}
