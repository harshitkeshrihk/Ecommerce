package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.BrandAssetRequest
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

/**
 * Corporate logos and proofs in the private `brand-assets` bucket
 * (layout: <customer user id>/...), plus proof decisions.
 */
@Singleton
class BrandingRepository @Inject constructor(
    private val storage: Storage,
    private val postgrest: Postgrest
) {
    private val bucket get() = storage.from(BUCKET)

    /** Uploads the customer's logo into their own folder; returns its path. */
    suspend fun uploadLogo(userId: String, bytes: ByteArray, extension: String): String? =
        upload("$userId/logos/${UUID.randomUUID()}.$extension", bytes)

    /** Signed, short-lived link to a private logo/proof image. */
    suspend fun signedUrl(path: String): String? = withContext(Dispatchers.IO) {
        try {
            bucket.createSignedUrl(path, expiresIn = 30.minutes)
        } catch (e: Exception) {
            Log.e("BrandingRepo", "Error creating signed URL for $path", e)
            null
        }
    }

    // --- Customer ---

    /** Approve, or request changes (comment required) on the latest proof. Returns an error message or null. */
    suspend fun decideProof(assetId: String, approve: Boolean, comment: String?): String? =
        withContext(Dispatchers.IO) {
            try {
                postgrest.rpc(
                    "decide_brand_proof",
                    buildJsonObject {
                        put("p_asset_id", assetId)
                        put("p_approve", approve)
                        put("p_comment", comment.orEmpty())
                    }
                )
                null
            } catch (e: Exception) {
                Log.e("BrandingRepo", "Error deciding proof $assetId", e)
                "Could not save your decision. Please try again."
            }
        }

    /** Swap the logo on an order (allowed until a proof is approved). Returns an error message or null. */
    suspend fun replaceLogo(userId: String, orderId: Long, bytes: ByteArray, extension: String, notes: String?): String? {
        val path = uploadLogo(userId, bytes, extension) ?: return "Could not upload the logo"
        return withContext(Dispatchers.IO) {
            try {
                postgrest.rpc(
                    "replace_gifting_logo",
                    buildJsonObject {
                        put("p_order_id", orderId)
                        put("p_logo_path", path)
                        put("p_logo_notes", notes.orEmpty())
                    }
                )
                null
            } catch (e: Exception) {
                Log.e("BrandingRepo", "Error replacing logo on order $orderId", e)
                "Could not replace the logo (it may already be approved)."
            }
        }
    }

    // --- Admin ---

    /** Uploads proof [version] into the customer's folder and records it for review. */
    suspend fun uploadProof(
        customerUserId: String,
        orderId: Long,
        version: Int,
        bytes: ByteArray,
        extension: String,
        adminNote: String?
    ): Boolean {
        val path = upload("$customerUserId/proofs/$orderId/v$version-${UUID.randomUUID()}.$extension", bytes)
            ?: return false
        return withContext(Dispatchers.IO) {
            try {
                postgrest["brand_assets"].insert(
                    BrandAssetRequest(orderId, version, path, adminNote?.trim()?.ifBlank { null })
                )
                true
            } catch (e: Exception) {
                Log.e("BrandingRepo", "Error recording proof v$version for order $orderId", e)
                false
            }
        }
    }

    private suspend fun upload(path: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            bucket.upload(path, bytes) { upsert = false }
            path
        } catch (e: Exception) {
            Log.e("BrandingRepo", "Upload failed for $path", e)
            null
        }
    }

    companion object {
        const val BUCKET = "brand-assets"
    }
}
