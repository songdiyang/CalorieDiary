package com.calorie.diary

import com.calorie.diary.ui.navigation.Routes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAuthTest {

    @Test
    fun onlyLoginAndRegistrationAreAuthenticationRoutes() {
        assertTrue(Routes.isAuthenticationRoute(Routes.ACCOUNT))
        assertTrue(Routes.isAuthenticationRoute(Routes.REGISTER))
        assertFalse(Routes.isAuthenticationRoute(Routes.HOME))
        assertFalse(Routes.isAuthenticationRoute(Routes.CALENDAR))
        assertFalse(Routes.isAuthenticationRoute(Routes.FRIENDS))
        assertFalse(Routes.isAuthenticationRoute(Routes.PROFILE))
        assertFalse(Routes.isAuthenticationRoute(Routes.RECOGNITION))
        assertFalse(Routes.isAuthenticationRoute(null))
    }
}
