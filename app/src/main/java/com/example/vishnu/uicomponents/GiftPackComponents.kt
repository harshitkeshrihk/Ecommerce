package com.example.vishnu.uicomponents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.vishnu.model.GiftPackLine
import com.example.vishnu.model.Product
import com.example.vishnu.utils.formatRupees

/** Editable list of what goes into ONE pack. Setting qty to 0 removes the line. */
@Composable
fun PackContentsEditor(
    lines: List<GiftPackLine>,
    onQtyChange: (productId: String, qty: Int) -> Unit,
    onAddItemClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        if (lines.isEmpty()) {
            Text(
                "No items yet — add products to build the pack.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        lines.forEach { line ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = line.product.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(line.product.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    Text(
                        "${formatRupees(line.product.priceRetail)} each",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                IconButton(onClick = { onQtyChange(line.product.id, line.qtyPerPack - 1) }) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }
                Text("${line.qtyPerPack}", fontWeight = FontWeight.Bold)
                IconButton(onClick = { onQtyChange(line.product.id, line.qtyPerPack + 1) }) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }
            }
        }
        TextButton(onClick = onAddItemClick) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add item")
        }
    }
}

/** Picks a product from the gifting collection. */
@Composable
fun GiftProductPickerDialog(
    products: List<Product>,
    onPick: (Product) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Gifting collection") },
        text = {
            if (products.isEmpty()) {
                Text("No products are marked as suitable for gifting yet.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(products, key = { it.id }) { product ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(product) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = product.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(product.name, Modifier.weight(1f), maxLines = 2)
                            Text(formatRupees(product.priceRetail), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    )
}
