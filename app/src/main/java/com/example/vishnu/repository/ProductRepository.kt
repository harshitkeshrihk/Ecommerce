package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.Product
import com.example.vishnu.model.Store
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
    // The Function to get Real Data
//    suspend fun getProducts(): List<Product> {
//        return try {
//            // "products" matches your table name in Supabase
//            supabase.postgrest["products"]
//                .select()
//                .decodeList<Product>()
//        } catch (e: Exception) {
//            e.printStackTrace()
//            emptyList() // Return empty list on error (or handle gracefully)
//        }
//    }

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

    suspend fun getProductsByStore(storeId: String): List<Product> {
        return try {
            supabase.postgrest["products"]
                .select {
                    filter { eq("store_id", storeId) } // <--- KEY FILTER
                    filter { eq("is_available", true) }
                }
                .decodeList<Product>()
        } catch (e: Exception) {
            Log.e("Repo", "Error fetching products for store $storeId", e)
            emptyList()
        }
    }

    suspend fun getStores(): List<Store> {
        return try {
            supabase.postgrest["stores"]
                .select {
                    filter { eq("is_active", true) } // Only active stores
                }
                .decodeList<Store>()
        } catch (e: Exception) {
            Log.e("Repo", "Error fetching stores", e)
            emptyList()
        }
    }

}