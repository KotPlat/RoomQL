package test
import androidx.room.Entity
import androidx.room.ColumnInfo

@Entity(tableName = "orders")
data class OrderEntity(
    val id: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
