package com.example.vishnu.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PaymentPurpose { CART, GIFTING }

sealed class PaymentResult {
    data class Success(val paymentId: String) : PaymentResult()
    data class Failure(val message: String) : PaymentResult()
}

/**
 * Razorpay reports results to MainActivity, not to the screen that started
 * the payment. A flow that isn't the retail cart marks itself as the pending
 * purpose before launching checkout; MainActivity then hands the result back
 * here instead of to CartViewModel.
 */
@Singleton
class PaymentRouter @Inject constructor() {

    @Volatile
    private var pendingPurpose: PaymentPurpose = PaymentPurpose.CART

    private val _giftingResults = MutableSharedFlow<PaymentResult>(extraBufferCapacity = 1)
    val giftingResults = _giftingResults.asSharedFlow()

    fun expect(purpose: PaymentPurpose) {
        pendingPurpose = purpose
    }

    /** Returns who the result belongs to and resets to the retail-cart default. */
    fun consumePurpose(): PaymentPurpose =
        pendingPurpose.also { pendingPurpose = PaymentPurpose.CART }

    fun deliverGifting(result: PaymentResult) {
        _giftingResults.tryEmit(result)
    }
}
