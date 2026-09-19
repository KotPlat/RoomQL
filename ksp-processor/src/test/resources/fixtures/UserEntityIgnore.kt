package test
import androidx.room.Entity
import androidx.room.Ignore

@Entity(tableName = "users")
data class UserEntity(
    val id: Int,
    val name: String,
    @Ignore val fullNameCache: String = ""
)
