package com.roomql.sample

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: UserRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = UserRepository(db.userDao(), db.orderDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedUsers() = db.userDao().insertAll(
        UserEntity(id = 1, name = "Alice", age = 30, status = "active"),
        UserEntity(id = 2, name = "Bob", age = 17, status = "active"),
        UserEntity(id = 3, name = "Carol", age = 40, status = "inactive"),
    )

    @Test
    fun `both filters applied when non-null`() {
        seedUsers()

        val result = repo.searchUsers(minAge = 18, status = "active")

        // Only Alice: age >= 18 AND status = active. Bob is 17, Carol is inactive.
        assertEquals(listOf("Alice"), result.map { it.name })
    }

    @Test
    fun `null filter is omitted, not treated as a null match`() {
        seedUsers()

        // status = null must drop the status predicate entirely, not filter status IS NULL.
        val result = repo.searchUsers(minAge = 18, status = null)

        // age >= 18: Alice (30) and Carol (40), ordered by age DESC.
        assertEquals(listOf("Carol", "Alice"), result.map { it.name })
    }

    @Test
    fun `all filters null returns everything`() {
        seedUsers()

        val result = repo.searchUsers(minAge = null, status = null)

        assertEquals(3, result.size)
    }

    @Test
    fun `suspend variant runs`() = runBlocking {
        seedUsers()

        val result = repo.searchUsersSuspend(status = "inactive")

        assertEquals(listOf("Carol"), result.map { it.name })
    }

    @Test
    fun `join maps colliding columns into the result POJO`() {
        seedUsers()
        db.orderDao().insertAll(
            OrderEntity(id = 100, userId = 1, total = 42.0, status = "paid"),
            OrderEntity(id = 101, userId = 3, total = 9.5, status = "paid"),
        )

        val result = repo.usersWithOrders().sortedBy { it.orderTotal }

        assertEquals(listOf("Carol", "Alice"), result.map { it.userName })
        assertEquals(listOf(9.5, 42.0), result.map { it.orderTotal })
    }

    @Test
    fun `flow re-emits when an observed table changes`() = runBlocking {
        db.userDao().insertAll(UserEntity(id = 1, name = "Alice", age = 30, status = "active"))

        val emissions = mutableListOf<List<UserEntity>>()
        val job = launch(Dispatchers.IO) {
            repo.observeUsers(minAge = 18).collect { emissions.add(it) }
        }

        withTimeout(3000) { while (emissions.isEmpty()) delay(10) }
        val initialCount = emissions.last().size
        assertEquals(1, initialCount)

        db.userDao().insertAll(UserEntity(id = 2, name = "Dave", age = 25, status = "active"))

        withTimeout(3000) { while (emissions.last().size == initialCount) delay(10) }
        assertEquals(2, emissions.last().size)

        job.cancel()
    }
}
