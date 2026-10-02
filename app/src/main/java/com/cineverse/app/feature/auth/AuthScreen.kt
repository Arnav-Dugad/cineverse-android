package com.cineverse.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cineverse.app.AppContainer
import com.cineverse.app.core.design.CvShape
import com.cineverse.app.core.design.CvTheme
import com.cineverse.app.core.design.Haptic
import com.cineverse.app.core.design.LocalHaptics
import com.cineverse.app.core.design.Palette
import com.cineverse.app.core.ui.ScreenPadding
import com.cineverse.app.core.ui.clickableNoRipple
import com.cineverse.app.data.firebase.AuthResult
import kotlinx.coroutines.launch

/**
 * Sign in, to the same account as the website.
 *
 * Email and password always work, because they need nothing but the Firebase
 * project the website already has. Google sign-in appears only when the project
 * has an Android app registered with this APK's certificate — a button that
 * cannot succeed is worse than no button at all.
 */
@Composable
fun AuthScreen(
    app: AppContainer,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CvTheme.colors
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()

    var registering by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val result = if (registering) app.auth.register(email, password, name)
            else app.auth.signIn(email, password)
            busy = false
            when (result) {
                AuthResult.Ok -> {
                    haptics?.play(Haptic.Success)
                    app.settings.syncFromCloud()
                    onDone()
                }
                is AuthResult.Failed -> {
                    haptics?.play(Haptic.Warning)
                    error = result.message
                }
            }
        }
    }

    Box(modifier.fillMaxSize().background(colors.ink)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Palette.Red.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
        )
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = ScreenPadding),
        ) {
            Box(
                Modifier.size(42.dp).clip(CvShape.Circle).clickableNoRipple(onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.text)
            }

            Spacer(Modifier.height(40.dp))
            Text(
                "CineVerse",
                style = MaterialTheme.typography.displaySmall,
                color = colors.text,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (registering) "Make an account — it works on the web too."
                else "The same account as CineVerse on the web.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text3,
            )
            Spacer(Modifier.height(30.dp))

            if (registering) {
                Field(name, { name = it }, "Name", KeyboardType.Text)
                Spacer(Modifier.height(12.dp))
            }
            Field(email, { email = it }, "Email", KeyboardType.Email)
            Spacer(Modifier.height(12.dp))
            Field(password, { password = it }, "Password", KeyboardType.Password, secret = true)

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Red2,
                )
            }

            Spacer(Modifier.height(22.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(CvShape.Pill)
                    .background(if (busy) colors.glassStrong else Palette.Red)
                    .clickableNoRipple(::submit),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        if (registering) "Create account" else "Sign in",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (registering) "Already have an account? " else "No account yet? ",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text3,
                )
                Text(
                    if (registering) "Sign in" else "Create one",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text,
                    modifier = Modifier.clickableNoRipple {
                        registering = !registering
                        error = null
                    },
                )
            }

            Spacer(Modifier.height(30.dp))
            Text(
                "Guest browsing works without an account — you just cannot keep a list or track episodes.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.text3,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    type: KeyboardType,
    secret: Boolean = false,
) {
    val colors = CvTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = CvShape.Medium,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Red2,
            unfocusedBorderColor = colors.hairline,
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
            focusedLabelColor = colors.text2,
            unfocusedLabelColor = colors.text3,
            cursorColor = Palette.Red2,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
