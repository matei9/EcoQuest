package com.greenguardians.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenguardians.app.ui.AppNav
import com.greenguardians.app.ui.LocalIsEn
import com.greenguardians.app.ui.theme.GreenGuardiansTheme
import com.greenguardians.app.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Romanian is the default even on English-language phones.
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ro"))
        }
        val isEn = AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith("en")
        setContent {
            val dark = vm.darkMode.collectAsStateWithLifecycle().value
            val monet = vm.monetColors.collectAsStateWithLifecycle().value
            GreenGuardiansTheme(darkMode = dark, monetColors = monet) {
                CompositionLocalProvider(LocalIsEn provides isEn) {
                    Surface(Modifier.fillMaxSize()) { AppNav(vm) }
                }
            }
        }
    }
}
