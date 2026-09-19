package test
import androidx.room.Entity

@Entity
data class ProductEntity(
    val id: Long,
    val title: String
)
