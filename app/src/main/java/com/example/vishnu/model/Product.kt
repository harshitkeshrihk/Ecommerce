package com.example.vishnu.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Place this at the bottom of the file or in a separate Data file
@Serializable
data class Product(
    val id: String = "",
    val name: String,         // e.g., "Laser Etched Steel Thali Set"
    val material: String,     // e.g., "Stainless Steel 304 Grade"
    val gauge: String,        // e.g., "22 Gauge"
    val weight: String,       // e.g., "450g" - Added to UI!
    @SerialName("price_retail") // Maps DB 'price_retail' -> Kotlin 'priceRetail'
    val priceRetail: Double,

    @SerialName("price_wholesale")
    val priceWholesale: Double,

    @SerialName("video_url")
    val videoUrl: String? = null,

    @SerialName("image_url")
    val imageUrl: String,

    @SerialName("stock_count")
    val stockCount: Int,

    @SerialName("is_available")
    val isAvailable: Boolean,

    @SerialName("is_bestseller")
    val isBestseller: Boolean = false,

    @SerialName("created_at")
    val createdAt: String? = null

)

//fun getMockProducts(): List<Product> {
//    return listOf(
//        Product("1", "Royal Dinner Set (36 Pcs)", "SS 304", "22 G", "4.2 kg", 2499.0, 1850.0,
//            "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
//            50, true,
//            "https://plus.unsplash.com/premium_photo-1661777196224-bfda51e61172?q=80&w=600&auto=format&fit=crop",
//            true
//        ),
//
//        Product("2", "Heavy Bottom Kadhai", "Induction Base", "18 G", "1.1 kg", 850.0, 620.0,
//            "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
//            120, true,
//            "https://images.unsplash.com/photo-1590794056226-79ef3a8147e1?q=80&w=600&auto=format&fit=crop"
//        ),
//
//        Product("3", "Laser Design Water Jug", "SS 202", "24 G", "350g", 450.0, 310.0,
//            "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
//            200, true,
//            "https://images.unsplash.com/photo-1580913428706-c311ab527eb6?q=80&w=600&auto=format&fit=crop"
//        ),
//
//        Product("4", "Masala Dabba (See-Through)", "SS 304", "26 G", "600g", 699.0, 480.0,
//            null, 80, true,
//            "https://images.unsplash.com/photo-1606787366850-de6330128bfc?q=80&w=600&auto=format&fit=crop"
//        ),
//
//        Product("5", "Copper Bottom Handi Set", "Copper/SS", "20 G", "1.5 kg", 1200.0, 950.0,
//            null, 0, false,
//            "https://images.unsplash.com/photo-1610701596007-11502861dcfa?q=80&w=600&auto=format&fit=crop"
//        ),
//
//        Product("6", "Stainless Steel Tea Cups (6 Pcs)", "SS 304", "24 G", "400g", 399.0, 260.0,
//            null, 150, true,
//            "https://images.unsplash.com/photo-1523906834658-6e24ef2386f9?q=80&w=600&auto=format&fit=crop"
//        ),
//
//        Product("7", "Induction Friendly Fry Pan", "SS + Aluminium", "18 G", "900g", 999.0, 720.0,
//            null, 90, true,
//            "https://images.unsplash.com/photo-1603046891744-1f76b6e8c85b?q=80&w=600&auto=format&fit=crop",
//            true
//        ),
//
//        Product("8", "Deep Steel Patila", "SS 202", "20 G", "1.8 kg", 1100.0, 800.0,
//            null, 60, true,
//            "https://images.unsplash.com/photo-1611486212355-d276af4581a1?q=80&w=600&auto=format&fit=crop"
//        ),
//
//        Product("9", "Non-Stick Tawa", "Aluminium", "3 mm", "850g", 750.0, 520.0,
//            null, 130, true,
//            "https://images.unsplash.com/photo-1604908177070-ec8b08d8c3b6?q=80&w=600&auto=format&fit=crop"
//        ),
//
//        Product("10", "Steel Storage Container Set (12 Pcs)", "SS 304", "26 G", "2.5 kg", 1800.0, 1350.0,
//            null, 40, true,
//            "https://images.unsplash.com/photo-1601050690597-3b2d40c850e9?q=80&w=600&auto=format&fit=crop",
//            true
//        ),
//
//        // ---------- BULK GENERATED PRODUCTS ----------
//        *(11..55).map { index ->
//            Product(
//                id = index.toString(),
//                name = "Kitchen Utility Item #$index",
//                material = listOf("SS 202", "SS 304", "Aluminium", "Copper Base").random(),
//                gauge = listOf("18 G", "20 G", "22 G", "24 G", "26 G").random(),
//                weight = "${(300..2500).random()}g",
//                priceRetail = (300..2500).random().toDouble(),
//                priceWholesale = (200..1800).random().toDouble(),
//                videoUrl = if (index % 3 == 0)
//                    "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
//                else null,
//                stockCount = (0..200).random(),
//                isAvailable = index % 7 != 0,
//                imageUrl = "https://images.unsplash.com/photo-1604908554268-0f7f1f3dbf7d?q=80&w=600&auto=format&fit=crop",
//                isBestseller = index % 10 == 0
//            )
//        }.toTypedArray()
//    )
//}
