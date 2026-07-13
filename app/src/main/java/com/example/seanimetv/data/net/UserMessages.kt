package com.example.seanimetv.data.net

import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps low-level networking failures to messages a TV viewer can act on.
 * Raw exception text ("Unable to resolve host graphql.anilist.co…") must
 * never reach the screen.
 */
fun Throwable.toUserMessage(): String = when (this) {
    is UnknownHostException -> "No internet connection — check your network and try again"
    is SocketTimeoutException -> "The server is taking too long to respond — try again"
    else -> message ?: "Something went wrong — try again"
}
