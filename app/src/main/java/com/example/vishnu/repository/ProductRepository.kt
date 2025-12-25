package com.example.vishnu.repository

import android.util.Log
import com.example.vishnu.model.Product
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ProductRepository @Inject constructor(
    private val supabase: SupabaseClient
) {
    // The Function to get Real Data
    suspend fun getProducts(): List<Product> {
        return try {
            // "products" matches your table name in Supabase
            supabase.postgrest["products"]
                .select()
                .decodeList<Product>()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList() // Return empty list on error (or handle gracefully)
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

}