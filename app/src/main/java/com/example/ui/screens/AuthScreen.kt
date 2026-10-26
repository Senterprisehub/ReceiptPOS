package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AccountProvider
import com.example.ui.POSViewModel
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TealSecondary

enum class AuthMode {
    SIGN_IN,
    SIGN_UP
}

@Composable
fun AuthScreen(
    viewModel: POSViewModel,
    modifier: Modifier = Modifier
) {
    var authMode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var selectedProvider by remember { mutableStateOf(AccountProvider.GMAIL) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // Helper validation for email domain
    fun validateEmailDomain(input: String): Boolean {
        val trimmed = input.trim().lowercase()
        return trimmed.endsWith("@gmail.com") ||
                trimmed.endsWith("@googlemail.com") ||
                trimmed.endsWith("@outlook.com") ||
                trimmed.endsWith("@hotmail.com") ||
                trimmed.endsWith("@live.com") ||
                trimmed.endsWith("@msn.com")
    }

    // Auto-detect provider from email input
    LaunchedEffect(email) {
        val trimmed = email.trim().lowercase()
        if (trimmed.endsWith("@outlook.com") || trimmed.endsWith("@hotmail.com") || trimmed.endsWith("@live.com")) {
            selectedProvider = AccountProvider.OUTLOOK
        } else if (trimmed.endsWith("@gmail.com") || trimmed.endsWith("@googlemail.com")) {
            selectedProvider = AccountProvider.GMAIL
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Branding & Cloud Sync Icon Badge
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                TealSecondary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = "Cloud POS Sync",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Apex POS Cloud",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Cloud-Connected POS & Cloud Storage Sync",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Auth Mode Selector Tabs (Sign In / Sign Up)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    Surface(
                        onClick = {
                            authMode = AuthMode.SIGN_IN
                            errorMessage = null
                        },
                        shape = RoundedCornerShape(9.dp),
                        color = if (authMode == AuthMode.SIGN_IN) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_sign_in")
                    ) {
                        Text(
                            text = "Sign In",
                            modifier = Modifier.padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (authMode == AuthMode.SIGN_IN) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        onClick = {
                            authMode = AuthMode.SIGN_UP
                            errorMessage = null
                        },
                        shape = RoundedCornerShape(9.dp),
                        color = if (authMode == AuthMode.SIGN_UP) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_sign_up")
                    ) {
                        Text(
                            text = "Sign Up",
                            modifier = Modifier.padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (authMode == AuthMode.SIGN_UP) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Card Form
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Account Provider Info Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (authMode == AuthMode.SIGN_IN) "Sign in to your account" else "Create cloud account",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (selectedProvider == AccountProvider.GMAIL) Color(0xFFEA4335).copy(alpha = 0.12f) else Color(0xFF0078D4).copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (selectedProvider == AccountProvider.GMAIL) Icons.Default.Mail else Icons.Default.MarkunreadMailbox,
                                    contentDescription = null,
                                    tint = if (selectedProvider == AccountProvider.GMAIL) Color(0xFFEA4335) else Color(0xFF0078D4),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (selectedProvider == AccountProvider.GMAIL) "Gmail" else "Outlook",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedProvider == AccountProvider.GMAIL) Color(0xFFEA4335) else Color(0xFF0078D4)
                                )
                            }
                        }
                    }

                    // Account Provider Switcher Chips (Gmail / Outlook)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = {
                                selectedProvider = AccountProvider.GMAIL
                                if (email.isNotBlank() && (email.endsWith("@outlook.com") || email.endsWith("@hotmail.com"))) {
                                    email = email.substringBefore("@") + "@gmail.com"
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selectedProvider == AccountProvider.GMAIL) Color(0xFFEA4335) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            color = if (selectedProvider == AccountProvider.GMAIL) Color(0xFFEA4335).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("provider_gmail")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mail,
                                    contentDescription = "Gmail",
                                    tint = Color(0xFFEA4335),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Google / Gmail",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedProvider == AccountProvider.GMAIL) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                selectedProvider = AccountProvider.OUTLOOK
                                if (email.isNotBlank() && email.endsWith("@gmail.com")) {
                                    email = email.substringBefore("@") + "@outlook.com"
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selectedProvider == AccountProvider.OUTLOOK) Color(0xFF0078D4) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            color = if (selectedProvider == AccountProvider.OUTLOOK) Color(0xFF0078D4).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("provider_outlook")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Window,
                                    contentDescription = "Outlook",
                                    tint = Color(0xFF0078D4),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Outlook / MS",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedProvider == AccountProvider.OUTLOOK) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Quick domain completion suggestions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("@gmail.com", "@outlook.com", "@hotmail.com").forEach { domain ->
                            SuggestionChip(
                                onClick = {
                                    val prefix = if (email.contains("@")) email.substringBefore("@") else email
                                    email = "$prefix$domain"
                                },
                                label = { Text(domain, fontSize = 10.sp) }
                            )
                        }
                    }

                    // Display Name (Only in Sign Up Mode)
                    if (authMode == AuthMode.SIGN_UP) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = {
                                displayName = it
                                errorMessage = null
                            },
                            label = { Text("Your Name or Business Name") },
                            placeholder = { Text("e.g. Alex Johnson") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_display_name")
                        )
                    }

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        label = { Text(if (selectedProvider == AccountProvider.GMAIL) "Gmail Address" else "Outlook Address") },
                        placeholder = {
                            Text(if (selectedProvider == AccountProvider.GMAIL) "example@gmail.com" else "example@outlook.com")
                        },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        trailingIcon = {
                            if (email.isNotBlank()) {
                                IconButton(onClick = { email = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear email")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        isError = email.isNotBlank() && !validateEmailDomain(email),
                        supportingText = {
                            if (email.isNotBlank() && !validateEmailDomain(email)) {
                                Text("Only @gmail.com or @outlook.com accounts allowed", color = StatusError)
                            } else {
                                Text("Cloud storage will be tied to this account", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_email")
                    )

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text(if (authMode == AuthMode.SIGN_IN) "Password" else "Create Password") },
                        placeholder = { Text("••••••••") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (authMode == AuthMode.SIGN_UP) ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            onDone = {
                                focusManager.clearFocus()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_password")
                    )

                    // Confirm Password (Only in Sign Up Mode)
                    if (authMode == AuthMode.SIGN_UP) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                errorMessage = null
                            },
                            label = { Text("Confirm Password") },
                            placeholder = { Text("••••••••") },
                            leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_confirm_password")
                        )
                    }

                    // Error Banner
                    AnimatedVisibility(visible = errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusError.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = StatusError,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage ?: "",
                                    color = StatusError,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Submit Action Button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            errorMessage = null

                            val trimmedEmail = email.trim()
                            if (trimmedEmail.isEmpty()) {
                                errorMessage = "Please enter your email"
                                return@Button
                            }
                            if (!validateEmailDomain(trimmedEmail)) {
                                errorMessage = "Only Gmail (@gmail.com) or Outlook (@outlook.com) accounts are supported."
                                return@Button
                            }
                            if (password.length < 6) {
                                errorMessage = "Password must be at least 6 characters."
                                return@Button
                            }

                            if (authMode == AuthMode.SIGN_UP && password != confirmPassword) {
                                errorMessage = "Passwords do not match."
                                return@Button
                            }

                            isSubmitting = true
                            if (authMode == AuthMode.SIGN_IN) {
                                viewModel.login(trimmedEmail, password) { success, err ->
                                    isSubmitting = false
                                    if (!success) {
                                        errorMessage = err ?: "Login failed. Please check credentials."
                                    }
                                }
                            } else {
                                viewModel.register(trimmedEmail, displayName, password, selectedProvider) { success, err ->
                                    isSubmitting = false
                                    if (!success) {
                                        errorMessage = err ?: "Sign up failed."
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("submit_auth_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connecting Cloud Storage...")
                        } else {
                            Icon(
                                imageVector = if (authMode == AuthMode.SIGN_IN) Icons.Default.Login else Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (authMode == AuthMode.SIGN_IN) "Sign In & Connect Cloud" else "Create Account & Link Storage",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    // Demo / Quick Login Preset Button for Testing
                    Surface(
                        onClick = {
                            email = "demo.pos@gmail.com"
                            password = "password123"
                            displayName = "Apex Store Owner"
                            selectedProvider = AccountProvider.GMAIL
                            errorMessage = null
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("demo_fill_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Fill with Demo Gmail Credentials",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Cloud Storage Assurance Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = EmeraldTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Automatic Cloud Synchronization",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "All sales receipts, product catalog, customer debts, and configurations are continuously synced and encrypted to your personal cloud storage.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
