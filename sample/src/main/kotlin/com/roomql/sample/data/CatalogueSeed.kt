package com.roomql.sample.data

import kotlin.random.Random

/**
 * Deterministic catalogue data.
 *
 * Seeded from a fixed value so every run — app launch, unit test, screenshot render —
 * produces byte-identical data. Reproducibility is what lets tests assert on exact row
 * counts and lets generated screenshots stay stable across builds.
 */
object CatalogueSeed {

    const val PRODUCT_COUNT: Int = 500

    val categories: List<String> = listOf(
        "Headphones", "Keyboards", "Monitors", "Laptops", "Cameras", "Speakers",
    )

    private val brandNames = listOf(
        "Aurora" to "Sweden",
        "Basalt" to "Germany",
        "Cobalt" to "Japan",
        "Dunlin" to "United Kingdom",
        "Everest" to "United States",
        "Fathom" to "Canada",
    )

    private val adjectives = listOf(
        "Compact", "Studio", "Field", "Pro", "Lite", "Ultra", "Classic", "Nomad",
    )

    private val nouns = listOf(
        "Edge", "Pulse", "Vertex", "Drift", "Ridge", "Atlas", "Quartz", "Ember",
    )

    fun brands(): List<BrandEntity> =
        brandNames.mapIndexed { index, (name, country) ->
            BrandEntity(id = index + 1, name = name, country = country)
        }

    fun products(count: Int = PRODUCT_COUNT): List<ProductEntity> {
        val random = Random(seed = 20260913L)
        val dayMillis = 86_400_000L
        return (1..count).map { id ->
            val category = categories[random.nextInt(categories.size)]
            ProductEntity(
                id = id,
                name = "${adjectives[random.nextInt(adjectives.size)]} " +
                    "${nouns[random.nextInt(nouns.size)]} ${100 + random.nextInt(900)}",
                category = category,
                brandId = 1 + random.nextInt(brandNames.size),
                price = (999 + random.nextInt(99_001)) / 100.0,
                rating = (10 + random.nextInt(41)) / 10.0,
                inStock = random.nextInt(4) != 0,
                createdAt = 1_767_225_600_000L - random.nextInt(365) * dayMillis,
            )
        }
    }

    /** Inserts brands and products into an empty database. */
    fun populate(db: AppDatabase) {
        db.brandDao().insertAll(brands())
        db.productDao().insertAll(products())
    }
}
