package com.roomql.demo.data

import androidx.room.Dao
import androidx.room.Query

/**
 * Baseline 2: one DAO method per filter combination.
 *
 * This is what you write when you refuse raw SQL strings and want Room to verify every
 * query at compile time. Four optional filters means 2^4 = 16 methods; a fifth filter
 * would mean 32. Every method below is correct, compile-time verified, and completely
 * unmaintainable in aggregate -- and that is the argument.
 *
 * The tedium is the point. Do not "simplify" this file to a representative few.
 */
@Dao
interface OverloadedProductDao {
    @Query("SELECT * FROM products ORDER BY rating DESC, id ASC")
    fun searchNoFilters(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE category = :category ORDER BY rating DESC, id ASC")
    fun searchByCategory(category: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE price >= :minPrice ORDER BY rating DESC, id ASC")
    fun searchByMinPrice(minPrice: Double): List<ProductEntity>

    @Query("SELECT * FROM products WHERE rating >= :minRating ORDER BY rating DESC, id ASC")
    fun searchByMinRating(minRating: Double): List<ProductEntity>

    @Query("SELECT * FROM products WHERE in_stock = :inStockOnly ORDER BY rating DESC, id ASC")
    fun searchByInStock(inStockOnly: Boolean): List<ProductEntity>

    @Query("SELECT * FROM products WHERE category = :category AND price >= :minPrice ORDER BY rating DESC, id ASC")
    fun searchByCategoryAndMinPrice(category: String, minPrice: Double): List<ProductEntity>

    @Query("SELECT * FROM products WHERE category = :category AND rating >= :minRating ORDER BY rating DESC, id ASC")
    fun searchByCategoryAndMinRating(category: String, minRating: Double): List<ProductEntity>

    @Query("SELECT * FROM products WHERE category = :category AND in_stock = :inStockOnly ORDER BY rating DESC, id ASC")
    fun searchByCategoryAndInStock(category: String, inStockOnly: Boolean): List<ProductEntity>

    @Query("SELECT * FROM products WHERE price >= :minPrice AND rating >= :minRating ORDER BY rating DESC, id ASC")
    fun searchByMinPriceAndMinRating(minPrice: Double, minRating: Double): List<ProductEntity>

    @Query("SELECT * FROM products WHERE price >= :minPrice AND in_stock = :inStockOnly ORDER BY rating DESC, id ASC")
    fun searchByMinPriceAndInStock(minPrice: Double, inStockOnly: Boolean): List<ProductEntity>

    @Query("SELECT * FROM products WHERE rating >= :minRating AND in_stock = :inStockOnly ORDER BY rating DESC, id ASC")
    fun searchByMinRatingAndInStock(minRating: Double, inStockOnly: Boolean): List<ProductEntity>

    @Query("SELECT * FROM products WHERE category = :category AND price >= :minPrice AND rating >= :minRating ORDER BY rating DESC, id ASC")
    fun searchByCategoryAndMinPriceAndMinRating(category: String, minPrice: Double, minRating: Double): List<ProductEntity>

    @Query("SELECT * FROM products WHERE category = :category AND price >= :minPrice AND in_stock = :inStockOnly ORDER BY rating DESC, id ASC")
    fun searchByCategoryAndMinPriceAndInStock(category: String, minPrice: Double, inStockOnly: Boolean): List<ProductEntity>

    @Query("SELECT * FROM products WHERE category = :category AND rating >= :minRating AND in_stock = :inStockOnly ORDER BY rating DESC, id ASC")
    fun searchByCategoryAndMinRatingAndInStock(category: String, minRating: Double, inStockOnly: Boolean): List<ProductEntity>

    @Query("SELECT * FROM products WHERE price >= :minPrice AND rating >= :minRating AND in_stock = :inStockOnly ORDER BY rating DESC, id ASC")
    fun searchByMinPriceAndMinRatingAndInStock(minPrice: Double, minRating: Double, inStockOnly: Boolean): List<ProductEntity>

    @Query("SELECT * FROM products WHERE category = :category AND price >= :minPrice AND rating >= :minRating AND in_stock = :inStockOnly ORDER BY rating DESC, id ASC")
    fun searchByCategoryAndMinPriceAndMinRatingAndInStock(category: String, minPrice: Double, minRating: Double, inStockOnly: Boolean): List<ProductEntity>
}
