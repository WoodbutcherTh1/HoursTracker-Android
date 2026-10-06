package com.hourstracker.app.data

/**
 * The worker and workplace details that do not influence pay (so they live outside core-model's
 * `WorkplaceSettings`). The ID number is never part of this object: it is kept encrypted by [SecureIdStore].
 */
data class UserProfile(
    val fullName: String = "",
    val employeeNumber: String = "",
    val workplaceName: String = "",
    val contractorName: String = "",
)
