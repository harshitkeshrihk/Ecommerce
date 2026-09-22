package com.example.vishnu.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.BudgetTier
import com.example.vishnu.model.GiftPack
import com.example.vishnu.model.GiftPackDraft
import com.example.vishnu.model.GiftPackLine
import com.example.vishnu.model.GiftingOrder
import com.example.vishnu.model.GiftingRules
import com.example.vishnu.model.OccasionType
import com.example.vishnu.model.Product
import com.example.vishnu.repository.GiftPackRepository
import com.example.vishnu.repository.GiftingOrderRepository
import com.example.vishnu.repository.GiftingOrderResult
import com.example.vishnu.repository.ProfileRepository
import com.example.vishnu.utils.PaymentPurpose
import com.example.vishnu.utils.PaymentResult
import com.example.vishnu.utils.PaymentRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Pack-contents editing shared by the customer builder and the admin pack editor. */
internal fun List<GiftPackLine>.withProductAdded(product: Product): List<GiftPackLine> =
    if (any { it.product.id == product.id }) {
        map { if (it.product.id == product.id) it.copy(qtyPerPack = it.qtyPerPack + 1) else it }
    } else {
        this + GiftPackLine(product, 1)
    }

internal fun List<GiftPackLine>.withQty(productId: String, qty: Int): List<GiftPackLine> =
    if (qty <= 0) filterNot { it.product.id == productId }
    else map { if (it.product.id == productId) it.copy(qtyPerPack = qty) else it }

// ---------------------------------------------------------------------
// Customer: gifting home (browse packs by budget)
// ---------------------------------------------------------------------

@HiltViewModel
class GiftingHomeViewModel @Inject constructor(
    private val giftPackRepository: GiftPackRepository
) : ViewModel() {

    private val _packs = MutableStateFlow<List<GiftPack>>(emptyList())

    private val _selectedTier = MutableStateFlow<BudgetTier?>(null) // null = all budgets
    val selectedTier = _selectedTier.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    val visiblePacks: StateFlow<List<GiftPack>> = combine(_packs, _selectedTier) { packs, tier ->
        // A pack whose items are all unavailable/deleted has nothing to sell.
        val sellable = packs.filter { it.items.isNotEmpty() }
        if (tier == null) sellable else sellable.filter { tier.matches(it.toDraft().pricePerPack) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _packs.value = giftPackRepository.getPacks(activeOnly = true)
            _isLoading.value = false
        }
    }

    fun selectTier(tier: BudgetTier?) {
        _selectedTier.value = tier
    }
}

// ---------------------------------------------------------------------
// Customer: Bulk Pack Builder + checkout
// ---------------------------------------------------------------------

@HiltViewModel
class GiftPackBuilderViewModel @Inject constructor(
    private val giftPackRepository: GiftPackRepository,
    private val giftingOrderRepository: GiftingOrderRepository,
    private val profileRepository: ProfileRepository,
    private val auth: Auth,
    private val paymentRouter: PaymentRouter
) : ViewModel() {

    // Slice 1 ships event gifting only; wedding (installments) and corporate
    // (branding) plug in here in later slices.
    val occasion = OccasionType.EVENT

    private val _draft = MutableStateFlow(GiftPackDraft(name = "Custom Gift Pack", sourcePackId = null, lines = emptyList()))
    val draft = _draft.asStateFlow()

    val packCountText = MutableStateFlow(GiftingRules.MIN_PACK_COUNT.toString())
    val personalization = MutableStateFlow("")
    val shipByDate = MutableStateFlow<LocalDate?>(null)
    val address = MutableStateFlow("")

    private val _giftingProducts = MutableStateFlow<List<Product>>(emptyList())
    val giftingProducts = _giftingProducts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    val total: StateFlow<Double> = combine(_draft, packCountText) { draft, countText ->
        draft.totalFor(countText.toIntOrNull() ?: 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    sealed class BuilderEvent {
        data class LaunchPayment(val amount: Double, val email: String, val phone: String) : BuilderEvent()
        data class Message(val text: String) : BuilderEvent()
        data class OrderPlaced(val orderId: Long) : BuilderEvent()
    }

    private val _events = MutableSharedFlow<BuilderEvent>()
    val events = _events.asSharedFlow()

    /** Exactly what the customer confirmed when they tapped Pay — edits made while
     *  the Razorpay sheet is open must not change the order that gets recorded. */
    private data class PendingCheckout(
        val draft: GiftPackDraft,
        val packCount: Int,
        val personalization: String,
        val shipByDate: LocalDate,
        val address: String
    )

    private var pendingCheckout: PendingCheckout? = null
    private var loaded = false

    init {
        viewModelScope.launch {
            paymentRouter.giftingResults.collect { result -> onPaymentResult(result) }
        }
    }

    fun load(packId: String?) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            _isLoading.value = true
            if (packId != null) {
                giftPackRepository.getPack(packId)?.let { _draft.value = it.toDraft() }
            }
            _giftingProducts.value = giftPackRepository.getGiftingProducts()
            address.value = profileRepository.getUserProfile()?.address.orEmpty()
            _isLoading.value = false
        }
    }

    fun addProduct(product: Product) {
        _draft.value = _draft.value.copy(lines = _draft.value.lines.withProductAdded(product))
    }

    fun setQtyPerPack(productId: String, qty: Int) {
        _draft.value = _draft.value.copy(lines = _draft.value.lines.withQty(productId, qty))
    }

    fun rename(name: String) {
        _draft.value = _draft.value.copy(name = name)
    }

    fun checkout() {
        if (_isProcessing.value) return
        val draft = _draft.value
        val packCount = packCountText.value.toIntOrNull()
        val error = GiftingRules.validate(
            draft = draft,
            packCount = packCount,
            personalization = personalization.value,
            shipByDate = shipByDate.value,
            address = address.value,
            today = LocalDate.now()
        )
        viewModelScope.launch {
            if (error != null) {
                _events.emit(BuilderEvent.Message(error))
                return@launch
            }
            _isProcessing.value = true
            pendingCheckout = PendingCheckout(
                draft = draft.copy(name = draft.name.trim().ifBlank { "Custom Gift Pack" }),
                packCount = packCount!!,
                personalization = personalization.value,
                shipByDate = shipByDate.value!!,
                address = address.value.trim()
            )

            val user = auth.currentUserOrNull()
            val phone = user?.phone?.takeIf { it.isNotBlank() }
                ?: profileRepository.getUserProfile()?.phoneNumber.orEmpty()

            paymentRouter.expect(PaymentPurpose.GIFTING)
            _events.emit(BuilderEvent.LaunchPayment(draft.totalFor(packCount), user?.email.orEmpty(), phone))
        }
    }

    private suspend fun onPaymentResult(result: PaymentResult) {
        val checkout = pendingCheckout
        pendingCheckout = null
        when {
            checkout == null -> Unit // not ours (e.g. VM recreated) — nothing to record
            result is PaymentResult.Failure -> _events.emit(BuilderEvent.Message("Payment failed: ${result.message}"))
            result is PaymentResult.Success -> {
                when (val placed = giftingOrderRepository.placeGiftingOrder(
                    draft = checkout.draft,
                    occasion = occasion,
                    packCount = checkout.packCount,
                    personalization = checkout.personalization,
                    shipByDate = checkout.shipByDate,
                    address = checkout.address,
                    paymentId = result.paymentId
                )) {
                    is GiftingOrderResult.Placed -> _events.emit(BuilderEvent.OrderPlaced(placed.orderId))
                    is GiftingOrderResult.Failed -> _events.emit(BuilderEvent.Message(placed.message))
                }
            }
        }
        _isProcessing.value = false
    }
}

// ---------------------------------------------------------------------
// Admin: gift pack list, pack editor, gifting orders
// ---------------------------------------------------------------------

@HiltViewModel
class AdminGiftPacksViewModel @Inject constructor(
    private val giftPackRepository: GiftPackRepository
) : ViewModel() {

    private val _packs = MutableStateFlow<List<GiftPack>>(emptyList())
    val packs = _packs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _packs.value = giftPackRepository.getPacks(activeOnly = false)
            _isLoading.value = false
        }
    }

    fun setActive(pack: GiftPack, isActive: Boolean) {
        viewModelScope.launch {
            if (giftPackRepository.setPackActive(pack.id, isActive)) {
                _packs.value = _packs.value.map { if (it.id == pack.id) it.copy(isActive = isActive) else it }
            }
        }
    }
}

@HiltViewModel
class AdminGiftPackEditViewModel @Inject constructor(
    private val giftPackRepository: GiftPackRepository
) : ViewModel() {

    val name = MutableStateFlow("")
    val description = MutableStateFlow("")
    val isActive = MutableStateFlow(true)

    private val _lines = MutableStateFlow<List<GiftPackLine>>(emptyList())
    val lines = _lines.asStateFlow()

    private val _giftingProducts = MutableStateFlow<List<Product>>(emptyList())
    val giftingProducts = _giftingProducts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _saveResult = MutableSharedFlow<String?>() // null = saved, else error message
    val saveResult = _saveResult.asSharedFlow()

    private var packId: String? = null
    private var loaded = false

    fun load(id: String?) {
        if (loaded) return
        loaded = true
        packId = id
        viewModelScope.launch {
            _isLoading.value = true
            if (id != null) {
                giftPackRepository.getPack(id)?.let { pack ->
                    name.value = pack.name
                    description.value = pack.description.orEmpty()
                    isActive.value = pack.isActive
                    _lines.value = pack.items.map { GiftPackLine(it.product, it.qty) }
                }
            }
            _giftingProducts.value = giftPackRepository.getGiftingProducts()
            _isLoading.value = false
        }
    }

    fun addProduct(product: Product) {
        _lines.value = _lines.value.withProductAdded(product)
    }

    fun setQtyPerPack(productId: String, qty: Int) {
        _lines.value = _lines.value.withQty(productId, qty)
    }

    fun save() {
        viewModelScope.launch {
            when {
                name.value.isBlank() -> _saveResult.emit("Enter a pack name")
                _lines.value.isEmpty() -> _saveResult.emit("Add at least one item")
                else -> {
                    _isLoading.value = true
                    val id = giftPackRepository.savePack(
                        packId = packId,
                        name = name.value.trim(),
                        description = description.value.trim().ifBlank { null },
                        isActive = isActive.value,
                        lines = _lines.value
                    )
                    _isLoading.value = false
                    _saveResult.emit(if (id != null) null else "Could not save the pack")
                }
            }
        }
    }
}

@HiltViewModel
class AdminGiftingOrdersViewModel @Inject constructor(
    private val giftPackRepository: GiftPackRepository
) : ViewModel() {

    private val _orders = MutableStateFlow<List<GiftingOrder>>(emptyList())
    val orders = _orders.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            _orders.value = giftPackRepository.getGiftingOrders()
            _isLoading.value = false
        }
    }
}
