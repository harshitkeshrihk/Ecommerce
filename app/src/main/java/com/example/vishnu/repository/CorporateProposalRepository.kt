package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.CorporateProposalRequest
import com.example.vishnu.model.CorporateProposalRequestInsert
import com.example.vishnu.model.CorporateProposalResponse
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order as SupabaseOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Custom corporate proposal requests: customers ask, admin answers with a pack. */
@Singleton
class CorporateProposalRepository @Inject constructor(
    private val postgrest: Postgrest
) {
    suspend fun submitRequest(budgetPerPerson: Double, headcount: Int, notes: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                postgrest["corporate_proposal_requests"].insert(
                    CorporateProposalRequestInsert(budgetPerPerson, headcount, notes.trim())
                )
                true
            } catch (e: Exception) {
                Log.e("ProposalRepo", "Error submitting proposal request", e)
                false
            }
        }

    /** RLS returns the caller's own requests (or all of them for an admin). */
    suspend fun getRequests(): List<CorporateProposalRequest> = withContext(Dispatchers.IO) {
        try {
            postgrest["corporate_proposal_requests"]
                .select { order("created_at", order = SupabaseOrder.DESCENDING) }
                .decodeList<CorporateProposalRequest>()
        } catch (e: Exception) {
            Log.e("ProposalRepo", "Error fetching proposal requests", e)
            emptyList()
        }
    }

    /** Admin: answer a request with a pack (status -> proposed). */
    suspend fun respond(requestId: String, packId: String, adminNote: String?): Boolean =
        update(requestId, CorporateProposalResponse(CorporateProposalRequest.PROPOSED, packId, adminNote, Instant.now().toString()))

    /** Admin: close a request without a proposal (e.g. can't be fulfilled). */
    suspend fun close(requestId: String, adminNote: String?): Boolean =
        update(requestId, CorporateProposalResponse(CorporateProposalRequest.CLOSED, null, adminNote, Instant.now().toString()))

    private suspend fun update(requestId: String, body: CorporateProposalResponse): Boolean =
        withContext(Dispatchers.IO) {
            try {
                postgrest["corporate_proposal_requests"].update(body) { filter { eq("id", requestId) } }
                true
            } catch (e: Exception) {
                Log.e("ProposalRepo", "Error updating proposal request $requestId", e)
                false
            }
        }
}
