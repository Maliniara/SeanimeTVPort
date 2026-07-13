package com.example.seanimetv.navigation

import com.example.seanimetv.ROUTE_HOME
import com.example.seanimetv.ROUTE_LOGIN
import com.example.seanimetv.ROUTE_PLAYER
import com.example.seanimetv.ROUTE_SERIES
import com.example.seanimetv.ROUTE_SETTINGS
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit test that verifies the navigation route constants declared in MainActivity.kt
 * have the expected string values. Serves as a compile-time guard confirming the constants
 * are accessible from outside the file and have not been accidentally changed.
 */
class NavigationRoutesTest {

    @Test
    fun routeLogin_equalsExpected() = assertEquals("login", ROUTE_LOGIN)

    @Test
    fun routeHome_equalsExpected() = assertEquals("home", ROUTE_HOME)

    @Test
    fun routeSeries_equalsExpected() = assertEquals("series/{anilistId}", ROUTE_SERIES)

    @Test
    fun routePlayer_equalsExpected() = assertEquals("player", ROUTE_PLAYER)

    @Test
    fun routeSettings_equalsExpected() = assertEquals("settings", ROUTE_SETTINGS)
}
