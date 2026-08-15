package com.example.seanimetv.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Builds a [ViewModelProvider.Factory] from a lambda, so call sites can write
 * `by viewModels { viewModelFactory { HomeViewModel(...) } }` instead of declaring
 * an anonymous `ViewModelProvider.Factory` object each time.
 */
fun <VM : ViewModel> viewModelFactory(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
