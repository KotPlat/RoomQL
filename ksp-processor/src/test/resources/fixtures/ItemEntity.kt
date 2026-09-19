package test
import androidx.room.Entity
import androidx.room.ColumnInfo

@Entity(tableName = "items")
data class ItemEntity(
    val id: Int,
    @ColumnInfo(name = "item_name") val name: String
)
