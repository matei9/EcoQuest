package com.greenguardians.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.greenguardians.app.model.L

val LocalIsEn = compositionLocalOf { false }

@Composable
fun L.t(): String = if (LocalIsEn.current) en else ro

/** 1000 -> "1.000" (Romanian grouping). */
fun fmt(n: Int): String = n.toString().reversed().chunked(3).joinToString(".").reversed()
