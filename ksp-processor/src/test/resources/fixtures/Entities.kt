package test
import androidx.room.Entity

@Entity(tableName = "users")
data class UserEntity(val id: Int)

@Entity(tableName = "posts")
data class PostEntity(val id: Int, val title: String)
