package com.example.exambrowser

enum class UserLevel {
    STUDENT,
    TEACHER,
    ADMIN
}

data class UserSession(
    val idUser: Int,
    val username: String,
    val displayName: String,
    val level: UserLevel
)

object DemoAccess {
    private const val DEMO_PIN = "123456"

    private val users = mapOf(
        "siswa" to DemoUser(1, "siswa", "Siswa Demo", UserLevel.STUDENT),
        "guru" to DemoUser(2, "guru", "Guru Demo", UserLevel.TEACHER),
        "admin" to DemoUser(3, "admin", "Admin Sekolah", UserLevel.ADMIN)
    )

    fun login(username: String, password: String): UserSession? {
        val key = username.trim().lowercase()
        val user = users[key] ?: return null
        if (password != "123456") return null

        return UserSession(
            idUser = user.idUser,
            username = user.username,
            displayName = user.displayName,
            level = user.level
        )
    }

    fun isValidExamPin(pin: String): Boolean = pin == DEMO_PIN

    private data class DemoUser(
        val idUser: Int,
        val username: String,
        val displayName: String,
        val level: UserLevel
    )
}
