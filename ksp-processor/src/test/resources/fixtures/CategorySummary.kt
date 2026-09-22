package test
import androidx.room.ColumnInfo
import com.roomql.runtime.Projection

@Projection
data class CategorySummary(
    @ColumnInfo(name = "category_id") val categoryId: Int,
    @ColumnInfo(name = "category_name") val categoryName: String,
    val productCount: Long,
)
