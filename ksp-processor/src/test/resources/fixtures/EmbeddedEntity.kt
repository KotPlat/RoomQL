package test
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity

data class Geo(val lat: Double, val lng: Double)

data class Address(
    val street: String,
    @ColumnInfo(name = "city_name") val city: String,
    @Embedded(prefix = "geo_") val geo: Geo,
)

data class Contact(val phone: String)

@Entity(tableName = "customers")
data class CustomerEntity(
    val id: Long,
    @Embedded(prefix = "addr_") val address: Address,
    @Embedded val contact: Contact?,
) {
    val label: String get() = "#$id"
    @Transient val cache: String = ""
}
