package test
import androidx.room.Entity

@Entity(tableName = "users")
data class UserEntity(
    val id: Int,
    val name: String,
    val age: Int
)
