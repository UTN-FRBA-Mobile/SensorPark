package com.sensorpark.domain.repository

import com.sensorpark.domain.model.Category
import kotlinx.coroutines.flow.Flow

/** Perfil del usuario con sesión iniciada. */
interface UserRepository {
    /** Categoría de prioridad actual; [Category.STANDARD] si no declaró ninguna. */
    val category: Flow<Category>

    suspend fun setCategory(category: Category)
}
