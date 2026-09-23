package com.example.vishnu.utils

import android.content.Context
import android.net.Uri
import com.example.vishnu.model.GiftingRules

/** A picked logo/proof image, ready to upload to the brand-assets bucket. */
class BrandImage(val bytes: ByteArray, val extension: String)

/** Maps an image MIME type to the file extension the bucket accepts, or null if unsupported. */
fun brandImageExtension(mimeType: String?): String? = when (mimeType) {
    "image/png" -> "png"
    "image/jpeg", "image/jpg" -> "jpg"
    "image/webp" -> "webp"
    else -> null
}

/**
 * Reads [uri] and checks it's a PNG/JPG/WEBP within the size limit.
 * Returns the image, or an error message to show.
 */
fun readBrandImage(context: Context, uri: Uri): Result<BrandImage> = runCatching {
    val extension = brandImageExtension(context.contentResolver.getType(uri))
        ?: error("Use a PNG, JPG or WEBP image")
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Couldn't read the image")
    if (bytes.size > GiftingRules.MAX_BRAND_IMAGE_BYTES) error("Image must be 5 MB or smaller")
    BrandImage(bytes, extension)
}
