package com.example.vishnu.repository

import com.example.vishnu.model.Product
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
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

}