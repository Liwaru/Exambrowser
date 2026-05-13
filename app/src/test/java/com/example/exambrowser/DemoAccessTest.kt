package com.example.exambrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAccessTest {
    @Test
    fun studentLoginReturnsStudentLevel() {
        val session = DemoAccess.login("siswa", "123456")

        assertEquals(UserLevel.STUDENT, session?.level)
        assertEquals(1, session?.idUser)
    }

    @Test
    fun teacherLoginReturnsTeacherLevel() {
        val session = DemoAccess.login("guru", "123456")

        assertEquals(UserLevel.TEACHER, session?.level)
        assertEquals(2, session?.idUser)
    }

    @Test
    fun adminLoginReturnsAdminLevel() {
        val session = DemoAccess.login("admin", "123456")

        assertEquals(UserLevel.ADMIN, session?.level)
        assertEquals(3, session?.idUser)
    }

    @Test
    fun invalidPasswordIsRejected() {
        val session = DemoAccess.login("guru", "password-salah")

        assertNull(session)
    }

    @Test
    fun examPinValidationUsesActivePinOnly() {
        assertTrue(DemoAccess.isValidExamPin("123456"))
        assertFalse(DemoAccess.isValidExamPin("483921"))
    }
}
