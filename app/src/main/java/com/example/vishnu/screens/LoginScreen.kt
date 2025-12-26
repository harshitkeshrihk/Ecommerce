package com.example.vishnu.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vishnu.viewModels.AuthState
import com.example.vishnu.viewModels.AuthViewModel
import io.github.jan.supabase.auth.status.SessionStatus

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()
    val sessionStatus by viewModel.sessionStatus.collectAsState(initial = SessionStatus.NotAuthenticated(false))

    // Switch between Login and Sign Up
    var isLoginMode by remember { mutableStateOf(true) }

    // Inputs
    val email by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()
    val name by viewModel.fullName.collectAsState()

    // Redirect if authenticated
    LaunchedEffect(sessionStatus) {
        if (sessionStatus is SessionStatus.Authenticated) {
            onAuthSuccess()
        }
    }

    // Handle Toast messages
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Error -> Toast.makeText(context, state.error, Toast.LENGTH_SHORT).show()
            is AuthState.Success -> Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // A nice gradient background using your Primary color
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                )
            )
    ) {
        // --- 1. Top Section (Logo & Welcome) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Logo Placeholder
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag, // Using a generic bag icon
                        contentDescription = "Logo",
                        tint = Color.White,
                        modifier = Modifier.size(50.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Vishnu Crockery",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Quality for your kitchen",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
        }

        // --- 2. Bottom Sheet Section (The Form) ---
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.65f) // Takes up bottom 65% of screen
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Toggle Title
                Text(
                    text = if (isLoginMode) "Welcome Back" else "Get Started",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isLoginMode) "Login to continue" else "Create a new account",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- Form Fields ---

                // Full Name (Animated Visibility)
                AnimatedVisibility(
                    visible = !isLoginMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        AuthTextField(
                            value = name,
                            onValueChange = { viewModel.fullName.value = it },
                            label = "Full Name",
                            icon = Icons.Default.Person
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                AuthTextField(
                    value = email,
                    onValueChange = { viewModel.email.value = it },
                    label = "Email Address",
                    icon = Icons.Default.Email,
                    keyboardType = KeyboardType.Email
                )

                Spacer(modifier = Modifier.height(16.dp))

                AuthPasswordField(
                    value = password,
                    onValueChange = { viewModel.password.value = it },
                    label = "Password"
                )

                Spacer(modifier = Modifier.height(32.dp))

                // --- Action Button ---
                Button(
                    onClick = { if (isLoginMode) viewModel.onSignIn() else viewModel.onSignUp() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = authState !is AuthState.Loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) {
                    if (authState is AuthState.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = if (isLoginMode) "LOGIN" else "SIGN UP",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- Toggle Text ---
                val annotatedString = buildAnnotatedString {
                    append(if (isLoginMode) "New here? " else "Already have an account? ")
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                        append(if (isLoginMode) "Create Account" else "Login Here")
                    }
                }

                Text(
                    text = annotatedString,
                    modifier = Modifier.clickable { isLoginMode = !isLoginMode },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// --- Helper Composables for Consistency ---

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.LightGray
        )
    )
}

@Composable
fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(image, contentDescription = null, tint = Color.Gray)
            }
        },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.LightGray
        )
    )
}