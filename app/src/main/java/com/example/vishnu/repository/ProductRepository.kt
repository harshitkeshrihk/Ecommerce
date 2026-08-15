package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.Product
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import io.github.jan.supabase.storage.Storage

class ProductRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val storage: Storage
) {
    suspend fun getAllProducts(): List<Product> {
        return try {
            supabase.postgrest["products"]
                .select {
                    filter { eq("is_available", true) }
                }
                .decodeList<Product>()
        } catch (e: Exception) {
            Log.e("Repo", "Error fetching products", e)
            emptyList()
        }
    }

    suspend fun getProductById(id: String): Product? {
        return try {
            supabase.postgrest["products"]
                .select {
                    filter {
                        eq("id", id)
                    }
                }
                .decodeSingleOrNull<Product>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun upsertProduct(product: Product): Boolean = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["products"].upsert(product) {
                // If ID matches, it updates. Otherwise, it inserts.
                onConflict = "id"
            }
            true
        } catch (e: Exception) {
            Log.e("Repo", "Error saving product", e)
            false
        }
    }

    suspend fun uploadProductImage(imageBytes: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "${UUID.randomUUID()}.jpg" // Unique name
            val bucket = storage.from("product-images")

            // 1. Upload
            bucket.upload(fileName, imageBytes) {
                upsert = false
            }

            // 2. Get Public URL
            val url = bucket.publicUrl(fileName)

            Log.d("Repo", "Image uploaded: $url")
            return@withContext url
        } catch (e: Exception) {
            Log.e("Repo", "Upload failed", e)
            return@withContext null
        }
    }

}