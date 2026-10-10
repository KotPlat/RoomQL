package test;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;

@Entity(tableName = "accounts")
public class AccountEntity {
    public static final String TABLE = "accounts";
    public long id;
    @ColumnInfo(name = "display_name") public String displayName;
    public transient String cache;
    @ColumnInfo(name = "kept") public transient String keptTransient;
    @Ignore public String ignored;
}
