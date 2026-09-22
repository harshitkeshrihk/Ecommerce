package com.example.vishnu.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vishnu.model.Rfq
import com.example.vishnu.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminQuotePipelineViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    private val auth: Auth
) : ViewModel() {

    private val _rfqs = MutableStateFlow<List<Rfq>>(emptyList())
    val rfqs = _rfqs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _rfqs.value = adminRepository.getOpenRfqs()
            _isLoading.value = false
        }
    }

    fun sendQuote(rfq: Rfq, pricesByRfqItemId: Map<String, Double>, terms: String?) {
        val adminId = auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            val success = adminRepository.respondToQuote(
                rfqId = rfq.id,
                pricesByRfqItemId = pricesByRfqItemId,
                terms = terms,
                adminUserId = adminId,
                nextVersion = rfq.quotes.size + 1
            )
            _toastMessage.emit(if (success) "Quote sent for request #${rfq.id.take(8)}" else "Failed to send quote")
            if (success) refresh()
        }
    }
}
