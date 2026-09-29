package com.example.vishnu.viewModels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.BudgetTier
import com.example.vishnu.model.CapacityCalendar
import com.example.vishnu.model.CapacityCheck
import com.example.vishnu.model.CapacityOverride
import com.example.vishnu.model.CorporateProposalRequest
import com.example.vishnu.model.CapacitySettings
import com.example.vishnu.model.OPEN_PRODUCTION_STATUSES
import com.example.vishnu.model.ProductionJob
import com.example.vishnu.model.ProductionScheduler
import com.example.vishnu.model.GiftPack
import com.example.vishnu.model.GiftPackDraft
import com.example.vishnu.model.GiftPackLine
import com.example.vishnu.model.GiftingOrder
import com.example.vishnu.model.GiftingRules
import com.example.vishnu.model.OccasionType
import com.example.vishnu.model.Product
import com.example.vishnu.repository.BrandingRepository
import com.example.vishnu.repository.CorporateProposalRepository
import com.example.vishnu.repository.GiftPackRepository
import com.example.vishnu.repository.GiftingOrderRepository
import com.example.vishnu.repository.GiftingOrderResult
import com.example.vishnu.repository.ProductionRepository
import com.example.vishnu.repository.ProfileRepository
import com.example.vishnu.utils.PaymentPurpose
import com.example.vishnu.utils.PaymentResult
import com.example.vishnu.utils.PaymentRouter
import com.example.vishnu.utils.readBrandImage
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/** What the builder shows under the ship-by date. */
sealed class ShipByCapacity {
    object NotChecked : ShipByCapacity()       // no date / pack count yet
    object Unknown : ShipByCapacity()          // capacity data failed to load
    object Available : ShipByCapacity()
    data class Conflict(val earliestAvailable: LocalDate?) : ShipByCapacity()
}

/** Workshop capacity + everything already booked, fetched together. */
private data class CapacitySnapshot(val calendar: CapacityCalendar, val existingJobs: List<ProductionJob>)

private fun capacityFor(snapshot: CapacitySnapshot?, shipBy: LocalDate?, packCount: Int?): ShipByCapacity {
    if (shipBy == null || packCount == null || packCount <= 0) return ShipByCapacity.NotChecked
    if (snapshot == null) return ShipByCapacity.Unknown
    val result = ProductionScheduler.check(
        existing = snapshot.existingJobs,
        newJob = ProductionJob("new-order", shipBy, packCount),
        calendar = snapshot.calendar,
        today = LocalDate.now()
    )
    return when (result) {
        CapacityCheck.Available -> ShipByCapacity.Available
        is CapacityCheck.Conflict -> ShipByCapacity.Conflict(result.earliestAvailable)
    }
}

/** How much the customer pays at checkout. */
enum class PayChoice { MIN_ADVANCE, FULL, CUSTOM }

data class PaymentPlan(
    val total: Double,
    val minAdvance: Double,
    /** Ship-by is too close for a later balance payment. */
    val fullRequired: Boolean,
    /** What will be charged now; null if the custom amount isn't a number yet. */
    val payNow: Double?,
    /** Null until a ship-by date is picked. */
    val balanceDueDate: LocalDate?
)

@HiltViewModel
class GiftPackBuilderViewModel @Inject constructor(
    private val giftPackRepository: GiftPackRepository,
    private val giftingOrderRepository: GiftingOrderRepository,
    private val productionRepository: ProductionRepository,
    private val profileRepository: ProfileRepository,
    private val brandingRepository: BrandingRepository,
    private val auth: Auth,
    private val paymentRouter: PaymentRouter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val selectableOccasions = listOf(OccasionType.EVENT, OccasionType.WEDDING, OccasionType.CORPORATE)
    val occasion = MutableStateFlow(OccasionType.EVENT)

    /** Corporate only: the logo to brand the gifts with, and where it should go. */
    val logoUri = MutableStateFlow<Uri?>(null)
    val logoNotes = MutableStateFlow("")

    /** Corporate only: budget per person when the order started from a budget proposal. */
    private val _budgetPerPerson = MutableStateFlow<Double?>(null)
    val budgetPerPerson = _budgetPerPerson.asStateFlow()

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

    val payChoice = MutableStateFlow(PayChoice.MIN_ADVANCE)
    val customPayText = MutableStateFlow("")

    val paymentPlan: StateFlow<PaymentPlan> =
        combine(total, shipByDate, payChoice, customPayText) { total, shipBy, choice, customText ->
            val fullRequired = shipBy != null && GiftingRules.requiresFullPayment(shipBy, LocalDate.now())
            val minAdvance = GiftingRules.minAdvance(total)
            PaymentPlan(
                total = total,
                minAdvance = minAdvance,
                fullRequired = fullRequired,
                payNow = when {
                    fullRequired -> total
                    choice == PayChoice.MIN_ADVANCE -> minAdvance
                    choice == PayChoice.FULL -> total
                    else -> customText.toDoubleOrNull()
                },
                balanceDueDate = shipBy?.let(GiftingRules::balanceDueDate)
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PaymentPlan(0.0, 0.0, false, 0.0, null))

    private val capacitySnapshot = MutableStateFlow<CapacitySnapshot?>(null)

    /** Live checkProductionCapacity result for the chosen ship-by date and pack count. */
    val shipByCapacity: StateFlow<ShipByCapacity> =
        combine(capacitySnapshot, shipByDate, packCountText) { snapshot, shipBy, countText ->
            capacityFor(snapshot, shipBy, countText.toIntOrNull())
        }.flowOn(Dispatchers.Default) // up to a year of re-planning when searching for the earliest date
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShipByCapacity.NotChecked)

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
        val address: String,
        val amountNow: Double,
        val occasion: OccasionType,
        val logoPath: String?,
        val logoNotes: String?,
        val budgetPerPerson: Double?
    )

    private var pendingCheckout: PendingCheckout? = null
    private var loaded = false

    init {
        viewModelScope.launch {
            paymentRouter.giftingResults.collect { result -> onPaymentResult(result) }
        }
    }

    /**
     * [occasion], [packCount] and [budgetPerPerson] prefill the builder when it's
     * opened from the corporate budget flow (headcount becomes the pack count).
     */
    fun load(
        packId: String?,
        occasion: OccasionType? = null,
        packCount: Int? = null,
        budgetPerPerson: Double? = null
    ) {
        if (loaded) return
        loaded = true
        occasion?.let { this.occasion.value = it }
        packCount?.let { packCountText.value = it.toString() }
        _budgetPerPerson.value = budgetPerPerson
        viewModelScope.launch {
            _isLoading.value = true
            if (packId != null) {
                giftPackRepository.getPack(packId)?.let { _draft.value = it.toDraft() }
            }
            _giftingProducts.value = giftPackRepository.getGiftingProducts()
            address.value = profileRepository.getUserProfile()?.address.orEmpty()
            capacitySnapshot.value = loadCapacitySnapshot()
            _isLoading.value = false
        }
    }

    private suspend fun loadCapacitySnapshot(): CapacitySnapshot? {
        val calendar = productionRepository.getCalendar(LocalDate.now()) ?: return null
        val load = productionRepository.getOpenLoad() ?: return null
        return CapacitySnapshot(calendar, ProductionScheduler.jobsFromLoad(load))
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
            val isCorporate = occasion.value == OccasionType.CORPORATE
            if (isCorporate && logoUri.value == null) {
                _events.emit(BuilderEvent.Message("Upload your company logo for a corporate order"))
                return@launch
            }
            val logoImage = if (isCorporate) {
                readBrandImage(context, logoUri.value!!).getOrElse {
                    _events.emit(BuilderEvent.Message(it.message ?: "Couldn't read the logo"))
                    return@launch
                }
            } else null
            val orderTotal = draft.totalFor(packCount!!)
            val payNow = paymentPlan.value.payNow?.let { Math.round(it * 100) / 100.0 } // whole paise
            GiftingRules.validatePayNow(payNow, orderTotal, shipByDate.value!!, LocalDate.now())?.let {
                _events.emit(BuilderEvent.Message(it))
                return@launch
            }
            _isProcessing.value = true

            // Re-check against fresh data: other customers may have booked the
            // workshop since this screen loaded. Fail closed if we can't tell.
            val fresh = loadCapacitySnapshot()
            capacitySnapshot.value = fresh
            val shipBy = shipByDate.value
            val capacity = withContext(Dispatchers.Default) { capacityFor(fresh, shipBy, packCount) }
            when (capacity) {
                ShipByCapacity.Available -> Unit
                is ShipByCapacity.Conflict -> {
                    _events.emit(BuilderEvent.Message(capacityConflictMessage(capacity)))
                    _isProcessing.value = false
                    return@launch
                }
                else -> {
                    _events.emit(BuilderEvent.Message("Couldn't check workshop capacity. Please try again."))
                    _isProcessing.value = false
                    return@launch
                }
            }

            // Upload the logo before charging, so a paid order always has its logo.
            val logoPath = logoImage?.let { image ->
                val userId = auth.currentUserOrNull()?.id
                val path = userId?.let { brandingRepository.uploadLogo(it, image.bytes, image.extension) }
                if (path == null) {
                    _events.emit(BuilderEvent.Message("Couldn't upload the logo. Please try again."))
                    _isProcessing.value = false
                    return@launch
                }
                path
            }

            pendingCheckout = PendingCheckout(
                draft = draft.copy(name = draft.name.trim().ifBlank { "Custom Gift Pack" }),
                packCount = packCount!!,
                personalization = personalization.value,
                shipByDate = shipByDate.value!!,
                address = address.value.trim(),
                amountNow = payNow!!,
                occasion = occasion.value,
                logoPath = logoPath,
                logoNotes = if (isCorporate) logoNotes.value else null,
                budgetPerPerson = if (isCorporate) _budgetPerPerson.value else null
            )

            val user = auth.currentUserOrNull()
            val phone = user?.phone?.takeIf { it.isNotBlank() }
                ?: profileRepository.getUserProfile()?.phoneNumber.orEmpty()

            paymentRouter.expect(PaymentPurpose.GIFTING)
            _events.emit(BuilderEvent.LaunchPayment(payNow, user?.email.orEmpty(), phone))
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
                    occasion = checkout.occasion,
                    packCount = checkout.packCount,
                    personalization = checkout.personalization,
                    shipByDate = checkout.shipByDate,
                    address = checkout.address,
                    paymentId = result.paymentId,
                    amountPaid = checkout.amountNow,
                    logoPath = checkout.logoPath,
                    logoNotes = checkout.logoNotes,
                    budgetPerPerson = checkout.budgetPerPerson
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
// Customer: corporate gifting — budget proposal + custom requests
// ---------------------------------------------------------------------

@HiltViewModel
class CorporateGiftingViewModel @Inject constructor(
    private val giftPackRepository: GiftPackRepository,
    private val proposalRepository: CorporateProposalRepository
) : ViewModel() {

    val budgetText = MutableStateFlow("")
    val headcountText = MutableStateFlow("")

    private val _packs = MutableStateFlow<List<GiftPack>>(emptyList())

    /** The brief the current proposal was made for; null until "Show gift options". */
    private val _brief = MutableStateFlow<Pair<Double, Int>?>(null)
    val brief = _brief.asStateFlow()

    val proposals: StateFlow<List<GiftPack>> = combine(_packs, _brief) { packs, brief ->
        brief?.let { GiftingRules.proposePacks(packs, it.first) }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _requests = MutableStateFlow<List<CorporateProposalRequest>>(emptyList())
    val requests = _requests.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            _packs.value = giftPackRepository.getPacks(activeOnly = true)
            _requests.value = proposalRepository.getRequests()
            _isLoading.value = false
        }
    }

    fun refreshRequests() {
        viewModelScope.launch { _requests.value = proposalRepository.getRequests() }
    }

    fun showOptions() {
        val budget = budgetText.value.toDoubleOrNull()
        val headcount = headcountText.value.toIntOrNull()
        viewModelScope.launch {
            GiftingRules.validateCorporateBrief(budget, headcount)?.let {
                _messages.emit(it)
                return@launch
            }
            _brief.value = budget!! to headcount!!
        }
    }

    fun requestCustomProposal(notes: String) {
        val brief = _brief.value ?: return
        viewModelScope.launch {
            if (notes.isBlank()) {
                _messages.emit("Tell us what you're looking for")
                return@launch
            }
            val ok = proposalRepository.submitRequest(brief.first, brief.second, notes)
            _messages.emit(
                if (ok) "Request sent — we'll prepare a proposal for you" else "Could not send the request. Please try again."
            )
            if (ok) refreshRequests()
        }
    }
}

// ---------------------------------------------------------------------
// Customer: my gifting orders + pay balance
// ---------------------------------------------------------------------

@HiltViewModel
class MyGiftingOrdersViewModel @Inject constructor(
    private val giftPackRepository: GiftPackRepository,
    private val giftingOrderRepository: GiftingOrderRepository,
    private val profileRepository: ProfileRepository,
    private val brandingRepository: BrandingRepository,
    private val auth: Auth,
    private val paymentRouter: PaymentRouter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** Order whose proof decision / logo replacement is being saved. */
    private val _brandingBusyOrderId = MutableStateFlow<Long?>(null)
    val brandingBusyOrderId = _brandingBusyOrderId.asStateFlow()

    suspend fun imageUrl(path: String): String? = brandingRepository.signedUrl(path)

    fun approveProof(order: GiftingOrder) = decide(order, approve = true, comment = null)

    fun requestProofChanges(order: GiftingOrder, comment: String) = decide(order, approve = false, comment = comment)

    private fun decide(order: GiftingOrder, approve: Boolean, comment: String?) {
        val proof = order.latestProof ?: return
        viewModelScope.launch {
            _brandingBusyOrderId.value = order.orderId
            val error = brandingRepository.decideProof(proof.id, approve, comment)
            _events.emit(
                Event.Message(
                    error ?: if (approve) "Logo approved — your order can now go into production"
                    else "Thanks — our team will send an updated proof"
                )
            )
            _brandingBusyOrderId.value = null
            refresh()
        }
    }

    fun replaceLogo(order: GiftingOrder, uri: Uri, notes: String) {
        viewModelScope.launch {
            val image = readBrandImage(context, uri).getOrElse {
                _events.emit(Event.Message(it.message ?: "Couldn't read the logo"))
                return@launch
            }
            val userId = auth.currentUserOrNull()?.id ?: return@launch
            _brandingBusyOrderId.value = order.orderId
            val error = brandingRepository.replaceLogo(userId, order.orderId, image.bytes, image.extension, notes)
            _events.emit(Event.Message(error ?: "Logo updated"))
            _brandingBusyOrderId.value = null
            refresh()
        }
    }

    private val _orders = MutableStateFlow<List<GiftingOrder>>(emptyList())
    val orders = _orders.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    /** Order whose balance payment is in flight (disables its button). */
    private val _payingOrderId = MutableStateFlow<Long?>(null)
    val payingOrderId = _payingOrderId.asStateFlow()

    sealed class Event {
        data class LaunchPayment(val amount: Double, val email: String, val phone: String) : Event()
        data class Message(val text: String) : Event()
    }

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    private data class PendingBalance(val orderId: Long, val amount: Double)
    private var pendingBalance: PendingBalance? = null

    init {
        viewModelScope.launch {
            paymentRouter.giftingResults.collect { onPaymentResult(it) }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val userId = auth.currentUserOrNull()?.id
            _orders.value = if (userId == null) emptyList() else giftPackRepository.getMyGiftingOrders(userId)
            _isLoading.value = false
        }
    }

    fun payBalance(order: GiftingOrder) {
        if (_payingOrderId.value != null || order.isFullyPaid) return
        viewModelScope.launch {
            _payingOrderId.value = order.orderId
            val amount = Math.round(order.balance * 100) / 100.0
            pendingBalance = PendingBalance(order.orderId, amount)

            val user = auth.currentUserOrNull()
            val phone = user?.phone?.takeIf { it.isNotBlank() }
                ?: profileRepository.getUserProfile()?.phoneNumber.orEmpty()

            paymentRouter.expect(PaymentPurpose.GIFTING)
            _events.emit(Event.LaunchPayment(amount, user?.email.orEmpty(), phone))
        }
    }

    private suspend fun onPaymentResult(result: PaymentResult) {
        val pending = pendingBalance ?: return // not ours (e.g. the pack builder's checkout)
        pendingBalance = null
        when (result) {
            is PaymentResult.Failure -> _events.emit(Event.Message("Payment failed: ${result.message}"))
            is PaymentResult.Success -> {
                when (val r = giftingOrderRepository.payBalance(pending.orderId, result.paymentId, pending.amount)) {
                    is GiftingOrderResult.Placed -> _events.emit(Event.Message("Balance paid — order #${pending.orderId} is fully paid"))
                    is GiftingOrderResult.Failed -> _events.emit(Event.Message(r.message))
                }
                refresh()
            }
        }
        _payingOrderId.value = null
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
    private val giftPackRepository: GiftPackRepository,
    private val brandingRepository: BrandingRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _orders = MutableStateFlow<List<GiftingOrder>>(emptyList())
    val orders = _orders.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    /** Order whose proof is uploading. */
    private val _uploadingOrderId = MutableStateFlow<Long?>(null)
    val uploadingOrderId = _uploadingOrderId.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _orders.value = giftPackRepository.getGiftingOrders()
            _isLoading.value = false
        }
    }

    suspend fun imageUrl(path: String): String? = brandingRepository.signedUrl(path)

    /** Uploads the next proof version for a corporate order. */
    fun uploadProof(order: GiftingOrder, uri: Uri, note: String) {
        val customerId = order.order.userId ?: return
        viewModelScope.launch {
            val image = readBrandImage(context, uri).getOrElse {
                _messages.emit(it.message ?: "Couldn't read the image")
                return@launch
            }
            _uploadingOrderId.value = order.orderId
            val version = (order.latestProof?.version ?: 0) + 1
            val ok = brandingRepository.uploadProof(customerId, order.orderId, version, image.bytes, image.extension, note)
            _messages.emit(if (ok) "Proof v$version sent to the customer" else "Could not upload the proof")
            _uploadingOrderId.value = null
            refresh()
        }
    }
}

private val shortDate = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")

fun capacityConflictMessage(conflict: ShipByCapacity.Conflict): String =
    conflict.earliestAvailable?.let {
        "The workshop is fully booked before this date. Earliest available ship-by date: ${it.format(shortDate)}"
    } ?: "The workshop has no free capacity for this many packs. Please contact us."

// ---------------------------------------------------------------------
// Admin: corporate proposal requests
// ---------------------------------------------------------------------

@HiltViewModel
class AdminProposalRequestsViewModel @Inject constructor(
    private val proposalRepository: CorporateProposalRepository,
    private val giftPackRepository: GiftPackRepository
) : ViewModel() {

    private val _requests = MutableStateFlow<List<CorporateProposalRequest>>(emptyList())
    val requests = _requests.asStateFlow()

    /** All packs, including hidden ones made for a single customer. */
    private val _packs = MutableStateFlow<List<GiftPack>>(emptyList())
    val packs = _packs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            // Open requests first, then newest.
            _requests.value = proposalRepository.getRequests()
                .sortedBy { if (it.status == CorporateProposalRequest.OPEN) 0 else 1 }
            _packs.value = giftPackRepository.getPacks(activeOnly = false)
            _isLoading.value = false
        }
    }

    fun respond(request: CorporateProposalRequest, pack: GiftPack, note: String) {
        viewModelScope.launch {
            val ok = proposalRepository.respond(request.id, pack.id, note.trim().ifBlank { null })
            _messages.emit(if (ok) "Proposal sent to the customer" else "Could not send the proposal")
            refresh()
        }
    }

    fun close(request: CorporateProposalRequest, note: String) {
        viewModelScope.launch {
            val ok = proposalRepository.close(request.id, note.trim().ifBlank { null })
            _messages.emit(if (ok) "Request closed" else "Could not close the request")
            refresh()
        }
    }
}

// ---------------------------------------------------------------------
// Admin: production calendar + capacity settings
// ---------------------------------------------------------------------

data class CalendarDay(
    val date: LocalDate,
    val capacity: Int,
    val planned: Int,
    val isOverridden: Boolean,
    /** (order, packs worked on it that day) */
    val work: List<Pair<GiftingOrder, Int>>,
    /** Orders whose ship-by date is this day */
    val due: List<GiftingOrder>
)

data class AtRiskOrder(val order: GiftingOrder, val projectedFinish: LocalDate?)

@HiltViewModel
class AdminProductionCalendarViewModel @Inject constructor(
    private val giftPackRepository: GiftPackRepository,
    private val productionRepository: ProductionRepository
) : ViewModel() {

    private val _days = MutableStateFlow<List<CalendarDay>>(emptyList())
    val days = _days.asStateFlow()

    private val _atRisk = MutableStateFlow<List<AtRiskOrder>>(emptyList())
    val atRisk = _atRisk.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val today = LocalDate.now()
            val calendar = productionRepository.getCalendar(today)
            if (calendar == null) {
                _error.value = "Couldn't load capacity settings. Has 005_phase2_production_capacity.sql been run?"
                _isLoading.value = false
                return@launch
            }
            _error.value = null

            val orders = giftPackRepository.getGiftingOrders()
                .filter { it.order.status in OPEN_PRODUCTION_STATUSES }
            val byId = orders.associateBy { it.orderId.toString() }
            val plan = ProductionScheduler.plan(ProductionScheduler.jobsFrom(orders), calendar, today)

            _atRisk.value = plan.late.mapNotNull { id ->
                byId[id]?.let { AtRiskOrder(it, plan.finishDay[id]) }
            }
            _days.value = (0 until CALENDAR_DAYS).map { offset ->
                val date = today.plusDays(offset.toLong())
                CalendarDay(
                    date = date,
                    capacity = calendar.capacityOn(date),
                    planned = plan.plannedOn(date),
                    isOverridden = calendar.isOverridden(date),
                    work = plan.allocations[date].orEmpty().mapNotNull { (id, packs) -> byId[id]?.let { it to packs } },
                    due = orders.filter { it.shipByDate == date.toString() }
                )
            }
            _isLoading.value = false
        }
    }

    companion object {
        const val CALENDAR_DAYS = 60
    }
}

@HiltViewModel
class AdminCapacitySettingsViewModel @Inject constructor(
    private val productionRepository: ProductionRepository
) : ViewModel() {

    val defaultPacksText = MutableStateFlow("")
    val closedWeekdays = MutableStateFlow<Set<Int>>(emptySet())

    private val _overrides = MutableStateFlow<List<CapacityOverride>>(emptyList())
    val overrides = _overrides.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            productionRepository.getSettings()?.let {
                defaultPacksText.value = it.defaultPacksPerDay.toString()
                closedWeekdays.value = it.closedWeekdays.toSet()
            }
            _overrides.value = productionRepository.getOverrides(LocalDate.now())
            _isLoading.value = false
        }
    }

    fun toggleWeekday(isoDay: Int) {
        closedWeekdays.value = closedWeekdays.value.let { if (isoDay in it) it - isoDay else it + isoDay }
    }

    fun saveSettings() {
        val packs = defaultPacksText.value.toIntOrNull()
        viewModelScope.launch {
            if (packs == null || packs < 0) {
                _messages.emit("Enter a valid number of packs per day")
                return@launch
            }
            val ok = productionRepository.saveSettings(CapacitySettings(packs, closedWeekdays.value.sorted()))
            _messages.emit(if (ok) "Capacity saved" else "Could not save capacity")
        }
    }

    fun addOverride(date: LocalDate, packs: Int, note: String) {
        viewModelScope.launch {
            val ok = productionRepository.upsertOverride(
                CapacityOverride(date.toString(), packs.coerceAtLeast(0), note.trim().ifBlank { null })
            )
            if (ok) _overrides.value = productionRepository.getOverrides(LocalDate.now())
            else _messages.emit("Could not save the date override")
        }
    }

    fun deleteOverride(day: String) {
        viewModelScope.launch {
            if (productionRepository.deleteOverride(day)) {
                _overrides.value = _overrides.value.filterNot { it.day == day }
            } else {
                _messages.emit("Could not delete the date override")
            }
        }
    }
}
