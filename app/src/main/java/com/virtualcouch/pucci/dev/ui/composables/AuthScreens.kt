package com.virtualcouch.pucci.dev.ui.composables

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.virtualcouch.pucci.dev.ui.effect.*
import com.virtualcouch.pucci.dev.viewmodel.TikTokViewModel

private val VirtualCouchBlue = Color(0xFF1D4EEE)

@Composable
fun AppNavigation(viewModel: TikTokViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    
    val startDestination = remember {
        if (viewModel.hasValidSession()) "main_hub" else "login"
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is LoginSuccessEffect -> {
                    navController.navigate("main_hub") {
                        popUpTo("login") { inclusive = true }
                    }
                }
                is NeedOtpEffect -> {
                    navController.navigate("otp/${effect.phoneNumber}")
                }
                is LoadingEffect -> {
                    isLoading = effect.isLoading
                    loadingMessage = effect.message
                }
                is MessageEffect -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    Box {
        NavHost(navController = navController, startDestination = startDestination) {
            composable("login") {
                LoginScreen(navController, viewModel)
            }
            composable("register") {
                RegisterScreen(navController)
            }
            composable("forgot_password") {
                ForgotPasswordScreen(navController)
            }
            composable(
                "otp/{phoneNumber}",
                arguments = listOf(navArgument("phoneNumber") { type = NavType.StringType })
            ) { backStackEntry ->
                val phoneNumber = backStackEntry.arguments?.getString("phoneNumber") ?: ""
                OtpScreen(phoneNumber, viewModel)
            }
            composable("main_hub") {
                var currentTab by remember { mutableStateOf("main") }
                VirtualCouchScreen(
                    viewModel = viewModel,
                    currentRoute = currentTab,
                    onNavigate = { currentTab = it },
                    onLogout = {
                        viewModel.logout()
                        navController.navigate("login") {
                            popUpTo("main_hub") { inclusive = true }
                        }
                    }
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    if (loadingMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = loadingMessage, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(navController: NavController, viewModel: TikTokViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Entrar no Virtual Couch",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = VirtualCouchBlue,
                unfocusedBorderColor = Color.DarkGray,
                textColor = Color.White,
                cursorColor = VirtualCouchBlue
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = null, tint = Color.Gray)
                }
            },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = VirtualCouchBlue,
                unfocusedBorderColor = Color.DarkGray,
                textColor = Color.White,
                cursorColor = VirtualCouchBlue
            ),
            singleLine = true
        )

        Text(
            text = "Esqueceu a senha?",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(top = 12.dp)
                .clickable { navController.navigate("forgot_password") }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { 
                if (email.isNotBlank() && password.isNotBlank()) {
                    viewModel.login(email, password)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = VirtualCouchBlue),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(text = "Entrar", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row {
            Text(text = "Não tem uma conta? ", color = Color.Gray)
            Text(
                text = "Criar conta",
                color = VirtualCouchBlue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { navController.navigate("register") }
            )
        }
    }
}

@Composable
fun OtpScreen(phoneNumber: String, viewModel: TikTokViewModel) {
    var otpValue by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Verifique seu SMS",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Enviamos um código de 6 dígitos para o número $phoneNumber",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        BasicTextField(
            value = otpValue,
            onValueChange = {
                if (it.length <= 6) {
                    otpValue = it
                    if (it.length == 6) {
                        viewModel.verifyOtp(phoneNumber, it)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .focusRequester(focusRequester)
                .size(1.dp), 
            textStyle = TextStyle(color = Color.Transparent)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { focusRequester.requestFocus() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 6) {
                val char = if (i < otpValue.length) otpValue[i].toString() else ""
                val isFocused = otpValue.length == i
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = if (isFocused) VirtualCouchBlue else Color.DarkGray,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                if (otpValue.length == 6) {
                    viewModel.verifyOtp(phoneNumber, otpValue)
                }
            },
            enabled = otpValue.length == 6,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = VirtualCouchBlue),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(text = "Verificar", color = Color.White, fontWeight = FontWeight.Bold)
        }
        
        Text(
            text = "Não recebeu o código? Reenviar",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = 24.dp)
                .clickable {  }
        )
    }
}

@Composable
fun RegisterScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Criar conta",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = VirtualCouchBlue,
                unfocusedBorderColor = Color.DarkGray,
                textColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = VirtualCouchBlue,
                unfocusedBorderColor = Color.DarkGray,
                textColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirmar Senha", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = VirtualCouchBlue,
                unfocusedBorderColor = Color.DarkGray,
                textColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { navController.navigate("main_hub") },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = VirtualCouchBlue),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(text = "Registrar", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ForgotPasswordScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Redefinir senha",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "Insira seu e-mail para receber um link de redefinição.",
            color = Color.Gray,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.DarkGray,
                textColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = VirtualCouchBlue),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(text = "Enviar link", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
