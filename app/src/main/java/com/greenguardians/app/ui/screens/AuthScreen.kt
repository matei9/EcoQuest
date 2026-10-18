package com.greenguardians.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenguardians.app.model.L
import com.greenguardians.app.ui.ErrorLine
import com.greenguardians.app.ui.components.LangDarkToggles
import com.greenguardians.app.ui.t
import com.greenguardians.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(vm: MainViewModel) {
    val cfg by vm.config.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var signup by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Iași") }
    var refCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🌱 Green Guardians", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            LangDarkToggles(vm)
        }
        Text(
            if (signup) L("Creează cont", "Create account").t() else L("Autentificare", "Sign in").t(),
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            password, { password = it }, Modifier.fillMaxWidth(), label = { Text(L("Parolă (min. 8)", "Password (min. 8)").t()) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        if (signup) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(L("Nume afișat", "Display name").t()) }, singleLine = true)
            Text(L("Oraș", "City").t())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cfg.cities.forEach { c -> FilterChip(city == c, { city = c }, { Text(c) }) }
            }
            OutlinedTextField(refCode, { refCode = it }, Modifier.fillMaxWidth(), label = { Text(L("Cod invitație (opțional)", "Invite code (optional)").t()) }, singleLine = true)
        }
        error?.let { ErrorLine(it) }
        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                error = null
                scope.launch {
                    runCatching {
                        if (signup) vm.signup(email.trim(), password, name.trim(), city, refCode)
                        else vm.login(email.trim(), password)
                    }.onFailure { error = it }
                    busy = false
                }
            },
        ) {
            if (busy) CircularProgressIndicator(Modifier.padding(2.dp)) else Text(if (signup) L("Creează cont", "Sign up").t() else L("Intră în cont", "Sign in").t())
        }
        TextButton(onClick = { signup = !signup; error = null }) {
            Text(if (signup) L("Am deja cont", "I already have an account").t() else L("Nu ai cont? Înregistrează-te", "No account? Sign up").t())
        }
    }
}
