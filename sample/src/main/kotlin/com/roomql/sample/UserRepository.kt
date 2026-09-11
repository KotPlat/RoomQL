package com.roomql.sample

import com.roomql.android.toQuery
import com.roomql.runtime.JoinType
import com.roomql.runtime.SortDirection
import com.roomql.runtime.query
import kotlinx.coroutines.flow.Flow

/**
 * Demonstrates Mode B end to end: build a dynamic query with `query { }` and hand the
 * result to a manual `@RawQuery` DAO method. Nullable filter arguments are simply
 * omitted from the SQL when null.
 */
class UserRepository(
    private val userDao: UserDao,
    private val orderDao: OrderDao,
) {
    fun searchUsers(minAge: Int?, status: String?): List<UserEntity> {
        val q = query {
            from(UserEntityColumns)
            where {
                UserEntityColumns.age gte minAge
                UserEntityColumns.status eq status
            }
            orderBy(UserEntityColumns.age, SortDirection.DESC)
        }
        return userDao.search(q.toQuery())
    }

    suspend fun searchUsersSuspend(status: String?): List<UserEntity> {
        val q = query {
            from(UserEntityColumns)
            where { UserEntityColumns.status eq status }
        }
        return userDao.searchSuspend(q.toQuery())
    }

    fun observeUsers(minAge: Int?): Flow<List<UserEntity>> {
        val q = query {
            from(UserEntityColumns)
            where { UserEntityColumns.age gte minAge }
        }
        return userDao.observe(q.toQuery())
    }

    fun usersWithOrders(): List<UserOrder> {
        val q = query {
            from(UserEntityColumns)
            join(OrderEntityColumns, JoinType.INNER) {
                on { UserEntityColumns.id eq OrderEntityColumns.userId }
            }
        }
        return orderDao.usersWithOrders(q.toQuery())
    }
}
