package test
import androidx.room.Entity

@Entity(tableName = "items")
data class NullableEntity(
    val id: Int,
    val email: String?
)
