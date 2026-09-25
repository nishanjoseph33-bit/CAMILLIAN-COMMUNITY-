package org.camillian.community

import android.os.Bundle
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.functions.functions
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import org.json.JSONArray
import kotlinx.serialization.json.put

private val translationClient = HttpClient(Android)

private val translationLanguageCodes = mapOf(
    "English" to "en",
    "Italiano" to "it",
    "Español" to "es",
    "Português" to "pt",
    "Français" to "fr",
    "Deutsch" to "de",
    "Tiếng Việt" to "vi",
    "Filipino" to "tl"
)

private suspend fun translateText(text: String, targetLanguage: String): String {
    if (text.isBlank()) return text
    val target = translationLanguageCodes[targetLanguage] ?: "en"
    val response = translationClient.get("https://translate.googleapis.com/translate_a/single") {
        parameter("client", "gtx")
        parameter("sl", "auto")
        parameter("tl", target)
        parameter("dt", "t")
        parameter("q", text)
    }.bodyAsText()
    val rows = JSONArray(response).optJSONArray(0) ?: return text
    return buildString {
        for (index in 0 until rows.length()) {
            val segment = rows.optJSONArray(index)
            if (segment != null && segment.length() > 0) append(segment.optString(0))
        }
    }.ifBlank { text }
}

@Serializable
private data class MemberProfile(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("religious_name") val religiousName: String? = null,
    val place: String? = null,
    val province: String? = null,
    val delegation: String? = null,
    val community: String? = null,
    val ministry: String? = null,
    @SerialName("role_title") val roleTitle: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("member_status") val memberStatus: String,
    @SerialName("member_role") val memberRole: String
)

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Supabase.client.handleDeeplinks(intent)
        if (intent.dataString?.contains("type=recovery") == true) {
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Supabase.client.handleDeeplinks(intent)
        setContent {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFFC8102E),
            onPrimary = Color.White,
            secondary = Color(0xFF0057B8),
            onSecondary = Color.White,
            tertiary = Color(0xFF0057B8),
            background = Color(0xFFFFF8F8),
            surface = Color(0xFFFFF8F8)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
            App(recoveryMode = intent?.dataString?.contains("type=recovery") == true)
        }
    }
}
    }
}

@Composable
private fun App(recoveryMode: Boolean = false) {
    var language by remember { mutableStateOf("English") }
    var profile by remember { mutableStateOf<MemberProfile?>(null) }
    var message by remember { mutableStateOf("") }
    val appScope = rememberCoroutineScope()

    val destination = when {
        recoveryMode -> "recovery"
        profile == null -> "login"
        profile!!.memberRole == "admin" || profile!!.memberRole == "super_admin" -> "admin"
        else -> "home"
    }

    AppBackground {
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                val entering = slideInHorizontally(initialOffsetX = { it / 7 }, animationSpec = tween(420)) + fadeIn(tween(260))
                val exiting = slideOutHorizontally(targetOffsetX = { -it / 10 }, animationSpec = tween(360)) + fadeOut(tween(180))
                entering.togetherWith(exiting)
            },
            label = "app-page-transition"
        ) { screen ->
            Box(Modifier.fillMaxSize()) {
                when (screen) {
                    "recovery" -> RecoveryPasswordScreen(language = language, onDone = { profile = null })
                    "login" -> LoginScreen(language = language, onLanguageChange = { language = it }, onApproved = { profile = it }, onMessage = { message = it }, initialMessage = message)
                    "admin" -> AdminDashboard(profile!!, language = language, onLanguageChange = { language = it }, onLogout = {
                        appScope.launch {
                            Supabase.client.auth.signOut()
                            profile = null
                        }
                    })
                    else -> CommunityShell(profile!!, language = language, onLanguageChange = { language = it }, onProfileUpdated = { updated -> profile = updated }, onLogout = {
                        appScope.launch {
                            Supabase.client.auth.signOut()
                            profile = null
                        }
                    })
                }
            }
        }
    }
}


@Composable
private fun AppBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.camillian_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.22f)
        ) {}
        content()
    }
}

@Composable
private fun RecoveryPasswordScreen(language: String = "English", onDone: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Set a new password", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("New password") }, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(confirm, { confirm = it }, Modifier.fillMaxWidth(), label = { Text("Confirm password") }, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !saving && password.length >= 6 && password == confirm,
            onClick = {
                scope.launch {
                    saving = true
                    try {
                        Supabase.client.auth.updateUser { this.password = password }
                        message = "Password updated. You can sign in with your new password."
                        Supabase.client.auth.signOut()
                        onDone()
                    } catch (e: Exception) {
                        message = e.message ?: "Could not update password."
                    } finally { saving = false }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (saving) "Saving..." else "Update password") }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(message)
        }
    }
}

@Composable
private fun LoginScreen(
    language: String = "English",
    onLanguageChange: (String) -> Unit = {},
    onApproved: (MemberProfile) -> Unit,
    onMessage: (String) -> Unit,
    initialMessage: String
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showRegister by remember { mutableStateOf(false) }
    var showRecovery by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(initialMessage) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(Color(0xFF202124))
    ) {
        val compact = maxHeight < 760.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 18.dp else 34.dp, vertical = if (compact) 18.dp else 28.dp)
                .border(2.dp, Color.White, RoundedCornerShape(38.dp))
                .padding(if (compact) 10.dp else 16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2E32)),
                elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = if (compact) 24.dp else 54.dp, vertical = if (compact) 22.dp else 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF333333),
                            shadowElevation = 8.dp
                        ) {
                            Text(
                                "Camillian",
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        LanguageSelector(selected = language, onSelected = onLanguageChange)
                    }

                    Spacer(Modifier.height(if (compact) 22.dp else 34.dp))

                    Surface(
                        modifier = Modifier.size(if (compact) 112.dp else 138.dp),
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF383838),
                        shadowElevation = 18.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.camillian_logo),
                                contentDescription = "Camillian logo",
                                modifier = Modifier
                                    .size(if (compact) 94.dp else 116.dp)
                                    .border(2.dp, Color.White, RoundedCornerShape(50)),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Spacer(Modifier.height(if (compact) 22.dp else 34.dp))

                    Text(
                        localized("Welcome", language),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        localized("Sign in to Camillian Community", language),
                        color = Color(0xFFD0D0D0),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.width(54.dp).height(3.dp),
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFC8102E)
                    ) { }

                    Spacer(Modifier.height(if (compact) 24.dp else 32.dp))

                    Text(
                        localized("Email", language),
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFE8E8E8),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(7.dp))
                    LoginField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = localized("Email", language),
                        password = false,
                        showPassword = false,
                        onTogglePassword = {}
                    )

                    Spacer(Modifier.height(18.dp))

                    Text(
                        localized("Password", language),
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFE8E8E8),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(7.dp))
                    LoginField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = localized("Password", language),
                        password = true,
                        showPassword = showPassword,
                        onTogglePassword = { showPassword = !showPassword }
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color.White,
                                    checkmarkColor = Color(0xFF333333),
                                    uncheckedColor = Color(0xFFBDBDBD)
                                )
                            )
                            Text(localized("Remember me", language), color = Color(0xFFE0E0E0))
                        }

                        TextButton(
                            onClick = { showRecovery = true },
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(
                                localized("Forgot password?", language),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = {
                            loading = true
                            message = ""
                            scope.launch {
                                try {
                                    Supabase.client.auth.signInWith(Email) {
                                        this.email = email.trim()
                                        this.password = password
                                    }
                                    val user = Supabase.client.auth.currentUserOrNull()
                                        ?: error("No active session.")
                                    val member = Supabase.client.from("profiles").select {
                                        filter { filter("id", FilterOperator.EQ, user.id) }
                                    }.decodeSingle<MemberProfile>()

                                    when (member.memberStatus) {
                                        "approved" -> onApproved(member)
                                        "pending" -> {
                                            Supabase.client.auth.signOut()
                                            message = "Membership is awaiting administrator approval."
                                        }
                                        "rejected" -> {
                                            Supabase.client.auth.signOut()
                                            message = "Membership application was not approved."
                                        }
                                        "suspended" -> {
                                            Supabase.client.auth.signOut()
                                            message = "Account is suspended. Please contact an administrator."
                                        }
                                        else -> {
                                            Supabase.client.auth.signOut()
                                            message = "Membership status could not be verified."
                                        }
                                    }
                                } catch (e: Exception) {
                                    message = e.message ?: "Sign-in failed."
                                } finally {
                                    loading = false
                                }
                                onMessage(message)
                            }
                        },
                        enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(30.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF303030),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF303030),
                            disabledContentColor = Color(0xFF909090)
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Text(
                            if (loading) localized("Checking membership...", language) else "Log in",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    if (message.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            message,
                            color = Color(0xFFFF9D9D),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    TextButton(
                        onClick = { showRegister = true },
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text(
                            "Don't have an account?  Sign up",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (showRegister) {
            RegisterDialog(
                language = language,
                onDismiss = { showRegister = false },
                onMessage = { message = it }
            )
        }

        if (showRecovery) {
            RecoveryDialog(
                language = language,
                onDismiss = { showRecovery = false },
                onMessage = { message = it }
            )
        }
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean,
    showPassword: Boolean,
    onTogglePassword: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        singleLine = true,
        placeholder = {
            Text(placeholder, color = Color(0xFFBDBDBD))
        },
        trailingIcon = if (password) {
            {
                TextButton(
                    onClick = onTogglePassword,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        if (showPassword) "Hide" else "Show",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        } else null,
        visualTransformation = if (password && !showPassword) {
            PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        shape = RoundedCornerShape(30.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF292929),
            unfocusedContainerColor = Color(0xFF292929),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.White,
            focusedBorderColor = Color(0xFF666666),
            unfocusedBorderColor = Color(0xFF292929)
        )
    )
}

@Serializable
private data class InviteCheck(val valid: Boolean)

@Composable
private fun RegisterDialog(language: String = "English", onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Member registration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Full name") })
                OutlinedTextField(email, { email = it }, label = { Text("Email") })
                OutlinedTextField(password, { password = it }, visualTransformation = PasswordVisualTransformation(), label = { Text("Password") })
                OutlinedTextField(invite, { invite = it }, label = { Text("Invitation code") })
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try {
                        val valid = Supabase.client.postgrest.rpc("check_invite_code", buildJsonObject { put("invite_code", invite) }).decodeSingle<InviteCheck>().valid
                        if (!valid) error("Invalid or expired invitation code.")
                        Supabase.client.auth.signUpWith(Email) {
                            this.email = email.trim()
                            this.password = password
                            data = buildJsonObject { put("full_name", name.trim()) }
                        }
                        Supabase.client.postgrest.rpc("consume_invite_code", buildJsonObject { put("invite_code", invite) })
                        onMessage("Registration submitted. An administrator must approve your membership.")
                        onDismiss()
                    } catch (e: Exception) {
                        onMessage(e.message ?: "Registration failed.")
                    } finally { busy = false }
                }
            }) { Text(if (busy) "Registering..." else "Register") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localized("Cancel", language)) } }
    )
}

@Composable
private fun RecoveryDialog(language: String = "English", onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset password") },
        text = { OutlinedTextField(email, { email = it }, label = { Text("Email") }) },
        confirmButton = {
            Button(enabled = !busy && email.isNotBlank(), onClick = {
                scope.launch {
                    busy = true
                    try {
                        Supabase.client.auth.resetPasswordForEmail(email.trim(), redirectUrl = "camillian://auth")
                        onMessage("Password reset email sent. Check your email.")
                        onDismiss()
                    } catch (e: Exception) {
                        onMessage(e.message ?: "Could not send recovery email.")
                    } finally { busy = false }
                }
            }) { Text(if (busy) "Sending..." else "Send reset email") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localized("Cancel", language)) } }
    )
}

@Serializable
private data class FeedPost(
    val id: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("kind") val kind: String = "text",
    @SerialName("text_content") val textContent: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val province: String? = null
)

@Serializable
private data class PostReaction(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String,
    val reaction: String = "like"
)

@Composable
private fun HomeScreen(profile: MemberProfile, language: String = "English") {
    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var composer by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var posting by remember { mutableStateOf(false) }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaKind by remember { mutableStateOf<String?>(null) }
    var reactionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var authorProfiles by remember { mutableStateOf<Map<String, MemberProfile>>(emptyMap()) }
    var reactionCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var reactingPostId by remember { mutableStateOf<String?>(null) }
    var commentPostId by remember { mutableStateOf<String?>(null) }
    var showComposer by remember { mutableStateOf(false) }
    var postProvince by remember { mutableStateOf(profile.province.orEmpty()) }
    var translatedPosts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var translatingPostId by remember { mutableStateOf<String?>(null) }
    var editingPostId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(language) { translatedPosts = emptyMap(); translatingPostId = null }
    val context = LocalContext.current
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        mediaUri = uri
        mediaKind = if (uri?.toString()?.contains("video", ignoreCase = true) == true) "video" else "photo"
    }

    fun loadFeed() {
        scope.launch {
            loading = true
            try {
                posts = Supabase.client.from("posts").select {
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }.decodeList<FeedPost>()
                message = ""
            } catch (e: Exception) {
                message = e.message ?: "Could not load the community feed."
            } finally {
                loading = false
            }
        }
    }

    fun loadAuthors() {
        scope.launch {
            try {
                val authors = Supabase.client.from("profiles").select().decodeList<MemberProfile>()
                authorProfiles = authors.associateBy { it.id }
            } catch (_: Exception) { }
        }
    }

    fun loadReactions() {
        scope.launch {
            try {
                val reactions = Supabase.client.from("post_reactions").select {
                    filter { filter("reaction", FilterOperator.EQ, "like") }
                }.decodeList<PostReaction>()
                reactionIds = reactions.filter { it.userId == profile.id }.map { it.postId }.toSet()
                reactionCounts = reactions.groupingBy { it.postId }.eachCount()
            } catch (e: Exception) {
                message = e.message ?: "Could not load reactions."
            }
        }
    }

    fun editPost(post: FeedPost) {
        editingPostId = post.id
        composer = post.textContent.orEmpty()
        postProvince = post.province.orEmpty()
        showComposer = true
    }

    fun saveEditedPost() {
        val postId = editingPostId ?: return
        scope.launch {
            posting = true
            try {
                Supabase.client.from("posts").update(buildJsonObject {
                    put("text_content", composer.trim().ifBlank { null })
                    put("province", postProvince.trim().ifBlank { null })
                }) { filter { filter("id", FilterOperator.EQ, postId) } }
                editingPostId = null
                composer = ""
                showComposer = false
                loadFeed()
            } catch (e: Exception) { message = e.message ?: "Could not edit post." }
            finally { posting = false }
        }
    }

    fun deletePost(postId: String) {
        scope.launch {
            try {
                Supabase.client.postgrest.rpc("delete_own_post", buildJsonObject { put("target_post_id", postId) })
                posts = posts.filterNot { it.id == postId }
                message = ""
            } catch (e: Exception) {
                message = e.message ?: "Could not delete post."
            }
        }
    }

    fun toggleLike(postId: String) {
        if (reactingPostId != null) return
        scope.launch {
            reactingPostId = postId
            try {
                val existing = Supabase.client.from("post_reactions").select {
                    filter { filter("post_id", FilterOperator.EQ, postId) }
                    filter { filter("user_id", FilterOperator.EQ, profile.id) }
                    filter { filter("reaction", FilterOperator.EQ, "like") }
                }.decodeList<PostReaction>()

                if (existing.isNotEmpty()) {
                    existing.forEach { reaction ->
                        Supabase.client.from("post_reactions").delete {
                            filter { filter("id", FilterOperator.EQ, reaction.id) }
                        }
                    }
                } else {
                    try {
                        Supabase.client.from("post_reactions").insert(buildJsonObject {
                            put("post_id", postId)
                            put("user_id", profile.id)
                            put("reaction", "like")
                        })
                    } catch (e: Exception) {
                        // A rapid double-tap can race the existence check.
                        // If the database already has the unique reaction, simply refresh.
                        if (!e.message.orEmpty().contains("23505") &&
                            !e.message.orEmpty().contains("post_reactions_post_id_user_id_key")) {
                            throw e
                        }
                    }
                }
                message = ""
                loadReactions()
            } catch (e: Exception) {
                message = e.message ?: "Could not update the reaction."
            } finally {
                reactingPostId = null
            }
        }
    }

    fun createPost() {
        val text = composer.trim()
        if (text.isEmpty() && mediaUri == null) return
        scope.launch {
            posting = true
            try {
                var mediaUrl: String? = null
                if (mediaUri != null) {
                    val uri = mediaUri!!
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not read selected media.")
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    val extension = if (mediaKind == "video") {
                        if (mime.contains("quicktime")) "mov" else "mp4"
                    } else {
                        when {
                            mime.contains("png") -> "png"
                            mime.contains("webp") -> "webp"
                            else -> "jpg"
                        }
                    }
                    val path = profile.id + "/posts/" + System.currentTimeMillis() + "." + extension
                    Supabase.client.storage.from("camillian-media").upload(path, bytes) { upsert = false }
                    mediaUrl = Supabase.client.storage.from("camillian-media").publicUrl(path)
                }
                Supabase.client.from("posts").insert(buildJsonObject {
                    put("author_id", profile.id)
                    put("kind", mediaKind ?: "text")
                    put("text_content", text.ifBlank { null })
                    put("media_url", mediaUrl)
                    put("media_type", mediaKind)
                    put("province", postProvince.trim().ifBlank { null })
                })
                composer = ""
                mediaUri = null
                mediaKind = null
                loadFeed()
            } catch (e: Exception) {
                message = e.message ?: "Could not publish your post."
            } finally { posting = false }
        }
    }

    LaunchedEffect(Unit) {
        loadFeed()
        loadAuthors()
        loadReactions()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFFFFBFB))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(50),
                color = Color.White,
                shadowElevation = 5.dp
            ) {
                Image(
                    painter = painterResource(id = R.drawable.camillian_logo),
                    contentDescription = "Camillian",
                    modifier = Modifier.padding(6.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("Camillian", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            TextButton(onClick = { showComposer = true }) {
                Text("+", fontSize = 28.sp, fontWeight = FontWeight.Light)
            }
            TextButton(onClick = { loadFeed() }) { Text("↻", fontSize = 22.sp) }
        }

        if (showComposer) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(localized(if (editingPostId == null) "Share with the community" else "Edit post", language), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showComposer = false; editingPostId = null; composer = "" }) { Text(localized("Close", language)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { mediaPicker.launch("image/*") }) { Text(localized("Photo", language)) }
                        OutlinedButton(onClick = { mediaPicker.launch("video/*") }) { Text(localized("Video", language)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = postProvince,
                        onValueChange = { postProvince = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(localized("Province", language)) },
                        placeholder = { Text(localized("Type your province", language)) },
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = composer,
                        onValueChange = { composer = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Write something for the community...") },
                        minLines = 3,
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { if (editingPostId == null) createPost() else saveEditedPost() },
                        enabled = !posting && (composer.isNotBlank() || mediaUri != null),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text(if (posting) localized(if (editingPostId == null) "Publishing..." else "Saving...", language) else localized(if (editingPostId == null) "Publish" else "Save changes", language)) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.width(68.dp),
                shape = RoundedCornerShape(34.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 3.dp,
                onClick = { showComposer = true }
            ) {
                Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("+", color = Color.White, fontSize = 28.sp)
                    Text("Post", color = Color.White, fontSize = 11.sp)
                }
            }
            authorProfiles.values.take(12).forEach { author ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp)) {
                    Surface(
                        modifier = Modifier.size(58.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shadowElevation = 2.dp
                    ) {
                        if (!author.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = author.avatarUrl,
                                contentDescription = author.fullName ?: "Member",
                                modifier = Modifier.padding(3.dp).clip(RoundedCornerShape(50)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.camillian_logo),
                                contentDescription = "Camillian member",
                                modifier = Modifier.padding(8.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        author.fullName?.substringBefore(" ")?.take(10) ?: "Member",
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        HorizontalDivider()

        if (message.isNotBlank()) {
            Text(message, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error)
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (posts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No posts yet. Be the first to share with the community.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(posts, key = { it.id }) { post ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column {
                            val author = authorProfiles[post.authorId]
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(46.dp),
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    if (!author?.avatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = author?.avatarUrl,
                                            contentDescription = "Member photo",
                                            modifier = Modifier.clip(RoundedCornerShape(50)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Image(
                                            painter = painterResource(id = R.drawable.camillian_logo),
                                            contentDescription = "Camillian logo",
                                            modifier = Modifier.padding(7.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        author?.fullName?.takeIf { it.isNotBlank() } ?: author?.email ?: "Member",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    if (!post.province.isNullOrBlank()) {
                                        Text("Province: " + post.province, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                                Text(post.createdAt ?: "", style = MaterialTheme.typography.labelSmall)
                            }

                            if (!post.mediaUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = post.mediaUrl,
                                    contentDescription = "Post media",
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 520.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            if (!post.textContent.isNullOrBlank()) {
                                Text(post.textContent, modifier = Modifier.padding(14.dp, 12.dp, 14.dp, 4.dp))
                                translatedPosts[post.id]?.let { translated ->
                                    Spacer(Modifier.height(6.dp))
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                                    ) {
                                        Column(Modifier.padding(10.dp)) {
                                            Text(
                                                localized("Translation", language),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            Text(translated)
                                        }
                                    }
                                }
                            }

                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { toggleLike(post.id) }, enabled = reactingPostId == null) {
                                    Text(if (reactionIds.contains(post.id)) "♥  " + (reactionCounts[post.id] ?: 0) else "♡  " + (reactionCounts[post.id] ?: 0), fontSize = 16.sp)
                                }
                                TextButton(
                                    onClick = {
                                        if (translatedPosts.containsKey(post.id)) {
                                            translatedPosts = translatedPosts - post.id
                                        } else {
                                            scope.launch {
                                                translatingPostId = post.id
                                                try {
                                                    val translated = translateText(post.textContent.orEmpty(), language)
                                                    translatedPosts = translatedPosts + (post.id to translated)
                                                } catch (e: Exception) {
                                                    message = e.message ?: localized("Translation failed", language)
                                                } finally {
                                                    translatingPostId = null
                                                }
                                            }
                                        }
                                    },
                                    enabled = translatingPostId == null
                                ) {
                                    Text(
                                        if (translatingPostId == post.id) localized("Translating...", language)
                                        else if (translatedPosts.containsKey(post.id)) localized("Hide translation", language)
                                        else localized("Translate", language)
                                    )
                                }
                                TextButton(onClick = { commentPostId = post.id }) {
                                    Text(localized("Comment", language))
                                }
                                Spacer(Modifier.weight(1f))
                                if (post.authorId == profile.id) {
                                    TextButton(onClick = { editPost(post) }) { Text(localized("Edit", language)) }
                                    TextButton(onClick = { deletePost(post.id) }) { Text(localized("Delete", language)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (commentPostId != null) {
        CommentsDialog(postId = commentPostId!!, profile = profile, language = language, onDismiss = { commentPostId = null })
    }
}


@Serializable
private data class PostComment(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("text_content") val textContent: String
)

@Composable
private fun CommentsDialog(postId: String, profile: MemberProfile, language: String = "English", onDismiss: () -> Unit) {
    var comments by remember { mutableStateOf<List<PostComment>>(emptyList()) }
    var composer by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var translatedComments by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var translatingCommentId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            try {
                comments = Supabase.client.from("comments").select {
                    filter { filter("post_id", FilterOperator.EQ, postId) }
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }.decodeList<PostComment>()
            } catch (_: Exception) {}
        }
    }

    fun send() {
        val text = composer.trim()
        if (text.isEmpty()) return
        scope.launch {
            sending = true
            try {
                Supabase.client.from("comments").insert(buildJsonObject {
                    put("post_id", postId)
                    put("author_id", profile.id)
                    put("text_content", text)
                })
                composer = ""
                load()
            } catch (_: Exception) {} finally {
                sending = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comments") },
        text = {
            Column {
                LazyColumn(
                    Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Member " + comment.authorId.take(8))
                                Text(comment.textContent)
                                translatedComments[comment.id]?.let { translated ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        localized("Translation", language),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(translated)
                                }
                                TextButton(
                                    onClick = {
                                        if (translatedComments.containsKey(comment.id)) {
                                            translatedComments = translatedComments - comment.id
                                        } else {
                                            scope.launch {
                                                translatingCommentId = comment.id
                                                try {
                                                    val translated = translateText(comment.textContent, language)
                                                    translatedComments = translatedComments + (comment.id to translated)
                                                } catch (_: Exception) {
                                                } finally {
                                                    translatingCommentId = null
                                                }
                                            }
                                        }
                                    },
                                    enabled = translatingCommentId == null
                                ) {
                                    Text(
                                        if (translatingCommentId == comment.id) localized("Translating...", language)
                                        else if (translatedComments.containsKey(comment.id)) localized("Hide translation", language)
                                        else localized("Translate", language)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = composer,
                    onValueChange = { composer = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Write a comment...") }
                )
                Spacer(Modifier.height(6.dp))
                Button(onClick = { send() }, enabled = !sending && composer.isNotBlank()) {
                    Text(if (sending) "Posting..." else "Comment")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(localized("Close", language)) } }
    )
}


private fun localized(key: String, language: String): String {
    val data = mapOf(
        "Home" to mapOf("Italiano" to "Home", "Español" to "Inicio", "Português" to "Início", "Français" to "Accueil", "Deutsch" to "Start", "Tiếng Việt" to "Trang chủ", "Filipino" to "Home"),
        "Events" to mapOf("Italiano" to "Eventi", "Español" to "Eventos", "Português" to "Eventos", "Français" to "Événements", "Deutsch" to "Termine", "Tiếng Việt" to "Sự kiện", "Filipino" to "Mga Kaganapan"),
        "Communities" to mapOf("Italiano" to "Comunità", "Español" to "Comunidades", "Português" to "Comunidades", "Français" to "Communautés", "Deutsch" to "Gemeinschaften", "Tiếng Việt" to "Cộng đoàn", "Filipino" to "Mga Komunidad"),
        "Messages" to mapOf("Italiano" to "Messaggi", "Español" to "Mensajes", "Português" to "Mensagens", "Français" to "Messages", "Deutsch" to "Nachrichten", "Tiếng Việt" to "Tin nhắn", "Filipino" to "Mga Mensahe"),
        "Profile" to mapOf("Italiano" to "Profilo", "Español" to "Perfil", "Português" to "Perfil", "Français" to "Profil", "Deutsch" to "Profil", "Tiếng Việt" to "Hồ sơ", "Filipino" to "Profile"),
        "Friends" to mapOf("Italiano" to "Amici", "Español" to "Amigos", "Português" to "Amigos", "Français" to "Amis", "Deutsch" to "Freunde", "Tiếng Việt" to "Bạn bè", "Filipino" to "Mga Kaibigan"),
        "Province" to mapOf("Italiano" to "Provincia", "Español" to "Provincia", "Português" to "Província", "Français" to "Province", "Deutsch" to "Provinz", "Tiếng Việt" to "Tỉnh dòng", "Filipino" to "Probinsya"),
        "Type your province" to mapOf("Italiano" to "Scrivi la tua provincia", "Español" to "Escribe tu provincia", "Português" to "Digite sua província", "Français" to "Saisissez votre province", "Deutsch" to "Provinz eingeben", "Tiếng Việt" to "Nhập tỉnh dòng của bạn", "Filipino" to "Ilagay ang iyong probinsya"),
        "Share with the community" to mapOf("Italiano" to "Condividi con la comunità", "Español" to "Comparte con la comunidad", "Português" to "Partilhe com a comunidade", "Français" to "Partager avec la communauté", "Deutsch" to "Mit der Gemeinschaft teilen", "Tiếng Việt" to "Chia sẻ với cộng đoàn", "Filipino" to "Ibahagi sa komunidad"),
        "Write something for the community..." to mapOf("Italiano" to "Scrivi qualcosa per la comunità...", "Español" to "Escribe algo para la comunidad...", "Português" to "Escreva algo para a comunidade...", "Français" to "Écrivez quelque chose pour la communauté...", "Deutsch" to "Schreibe etwas für die Gemeinschaft...", "Tiếng Việt" to "Viết điều gì đó cho cộng đoàn...", "Filipino" to "Sumulat para sa komunidad..."),
        "Photo" to mapOf("Italiano" to "Foto", "Español" to "Foto", "Português" to "Foto", "Français" to "Photo", "Deutsch" to "Foto", "Tiếng Việt" to "Ảnh", "Filipino" to "Larawan"),
        "Video" to mapOf("Italiano" to "Video", "Español" to "Vídeo", "Português" to "Vídeo", "Français" to "Vidéo", "Deutsch" to "Video", "Tiếng Việt" to "Video", "Filipino" to "Video"),
        "Publish" to mapOf("Italiano" to "Pubblica", "Español" to "Publicar", "Português" to "Publicar", "Français" to "Publier", "Deutsch" to "Veröffentlichen", "Tiếng Việt" to "Đăng", "Filipino" to "I-publish"),
        "Comment" to mapOf("Italiano" to "Commenta", "Español" to "Comentar", "Português" to "Comentar", "Français" to "Commenter", "Deutsch" to "Kommentieren", "Tiếng Việt" to "Bình luận", "Filipino" to "Magkomento"),
        "Delete" to mapOf("Italiano" to "Elimina", "Español" to "Eliminar", "Português" to "Excluir", "Français" to "Supprimer", "Deutsch" to "Löschen", "Tiếng Việt" to "Xóa", "Filipino" to "Tanggalin"),
        "Welcome" to mapOf("Italiano" to "Benvenuto", "Español" to "Bienvenido", "Português" to "Bem-vindo", "Français" to "Bienvenue", "Deutsch" to "Willkommen", "Tiếng Việt" to "Chào mừng", "Filipino" to "Maligayang pagdating"),
        "Sign in to Camillian Community" to mapOf("Italiano" to "Accedi alla Comunità Camilliana", "Español" to "Inicia sesión en la Comunidad Camilliana", "Português" to "Entre na Comunidade Camilliana", "Français" to "Connectez-vous à la Communauté Camillienne", "Deutsch" to "Bei der Camillianischen Gemeinschaft anmelden", "Tiếng Việt" to "Đăng nhập Cộng đoàn Camillian", "Filipino" to "Mag-login sa Camillian Community"),
        "Remember me" to mapOf("Italiano" to "Ricordami", "Español" to "Recuérdame", "Português" to "Lembrar-me", "Français" to "Se souvenir de moi", "Deutsch" to "Angemeldet bleiben", "Tiếng Việt" to "Ghi nhớ tôi", "Filipino" to "Tandaan ako"),
        "Reset password" to mapOf("Italiano" to "Reimposta password", "Español" to "Restablecer contraseña", "Português" to "Redefinir palavra-passe", "Français" to "Réinitialiser le mot de passe", "Deutsch" to "Passwort zurücksetzen", "Tiếng Việt" to "Đặt lại mật khẩu", "Filipino" to "I-reset ang password"),
        "New password" to mapOf("Italiano" to "Nuova password", "Español" to "Nueva contraseña", "Português" to "Nova palavra-passe", "Français" to "Nouveau mot de passe", "Deutsch" to "Neues Passwort", "Tiếng Việt" to "Mật khẩu mới", "Filipino" to "Bagong password"),
        "Confirm password" to mapOf("Italiano" to "Conferma password", "Español" to "Confirmar contraseña", "Português" to "Confirmar palavra-passe", "Français" to "Confirmer le mot de passe", "Deutsch" to "Passwort bestätigen", "Tiếng Việt" to "Xác nhận mật khẩu", "Filipino" to "Kumpirmahin ang password"),
        "Send reset email" to mapOf("Italiano" to "Invia email di reimpostazione", "Español" to "Enviar correo de restablecimiento", "Português" to "Enviar email de redefinição", "Français" to "Envoyer l’e-mail de réinitialisation", "Deutsch" to "E-Mail zum Zurücksetzen senden", "Tiếng Việt" to "Gửi email đặt lại", "Filipino" to "Ipadala ang reset email"),
        "Cancel" to mapOf("Italiano" to "Annulla", "Español" to "Cancelar", "Português" to "Cancelar", "Français" to "Annuler", "Deutsch" to "Abbrechen", "Tiếng Việt" to "Hủy", "Filipino" to "Kanselahin"),
        "Translate" to mapOf("Italiano" to "Traduci", "Español" to "Traducir", "Português" to "Traduzir", "Français" to "Traduire", "Deutsch" to "Übersetzen", "Tiếng Việt" to "Dịch", "Filipino" to "Isalin"),
        "Hide translation" to mapOf("Italiano" to "Nascondi traduzione", "Español" to "Ocultar traducción", "Português" to "Ocultar tradução", "Français" to "Masquer la traduction", "Deutsch" to "Übersetzung ausblenden", "Tiếng Việt" to "Ẩn bản dịch", "Filipino" to "Itago ang salin"),
        "Translating..." to mapOf("Italiano" to "Traduzione...", "Español" to "Traduciendo...", "Português" to "Traduzindo...", "Français" to "Traduction...", "Deutsch" to "Übersetzen...", "Tiếng Việt" to "Đang dịch...", "Filipino" to "Isinasalin..."),
        "Translation" to mapOf("Italiano" to "Traduzione", "Español" to "Traducción", "Português" to "Tradução", "Français" to "Traduction", "Deutsch" to "Übersetzung", "Tiếng Việt" to "Bản dịch", "Filipino" to "Salin"),
        "Translation failed" to mapOf("Italiano" to "Traduzione non riuscita", "Español" to "La traducción falló", "Português" to "A tradução falhou", "Français" to "La traduction a échoué", "Deutsch" to "Übersetzung fehlgeschlagen", "Tiếng Việt" to "Dịch không thành công", "Filipino" to "Hindi nagtagumpay ang pagsasalin"),
        "Close" to mapOf("Italiano" to "Chiudi", "Español" to "Cerrar", "Português" to "Fechar", "Français" to "Fermer", "Deutsch" to "Schließen", "Tiếng Việt" to "Đóng", "Filipino" to "Isara"),
        "Edit" to mapOf("Italiano" to "Modifica", "Español" to "Editar", "Português" to "Editar", "Français" to "Modifier", "Deutsch" to "Bearbeiten", "Tiếng Việt" to "Chỉnh sửa", "Filipino" to "I-edit"),
        "Edit post" to mapOf("Italiano" to "Modifica post", "Español" to "Editar publicación", "Português" to "Editar publicação", "Français" to "Modifier la publication", "Deutsch" to "Beitrag bearbeiten", "Tiếng Việt" to "Chỉnh sửa bài đăng", "Filipino" to "I-edit ang post"),
        "Save changes" to mapOf("Italiano" to "Salva modifiche", "Español" to "Guardar cambios", "Português" to "Guardar alterações", "Français" to "Enregistrer les modifications", "Deutsch" to "Änderungen speichern", "Tiếng Việt" to "Lưu thay đổi", "Filipino" to "I-save ang mga pagbabago"),
        "Publishing..." to mapOf("Italiano" to "Pubblicazione...", "Español" to "Publicando...", "Português" to "Publicando...", "Français" to "Publication...", "Deutsch" to "Wird veröffentlicht...", "Tiếng Việt" to "Đang đăng...", "Filipino" to "Ipinapadala..."),
        "Saving..." to mapOf("Italiano" to "Salvataggio...", "Español" to "Guardando...", "Português" to "Salvando...", "Français" to "Enregistrement...", "Deutsch" to "Speichern...", "Tiếng Việt" to "Đang lưu...", "Filipino" to "Nagse-save..."),
        "Admin Dashboard" to mapOf("Italiano" to "Pannello amministratore", "Español" to "Panel de administración", "Português" to "Painel de administração", "Français" to "Tableau de bord administrateur", "Deutsch" to "Administrationsbereich", "Tiếng Việt" to "Bảng quản trị", "Filipino" to "Dashboard ng administrador"),
        "Administrator" to mapOf("Italiano" to "Amministratore", "Español" to "Administrador", "Português" to "Administrador", "Français" to "Administrateur", "Deutsch" to "Administrator", "Tiếng Việt" to "Quản trị viên", "Filipino" to "Administrator")
    )
    return if (language == "English") key else data[key]?.get(language) ?: key
}

@Composable
private fun CommunityShell(profile: MemberProfile, language: String, onLanguageChange: (String) -> Unit, onProfileUpdated: (MemberProfile) -> Unit, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf("Home") }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Home", "Friends", "Events", "Communities", "Messages", "Profile").forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            when (item) {
                                "Home" -> Icon(Icons.Filled.Home, contentDescription = null)
                                "Friends" -> Icon(Icons.Filled.People, contentDescription = null)
                                "Events" -> Icon(Icons.Filled.Event, contentDescription = null)
                                "Communities" -> Icon(Icons.Filled.Group, contentDescription = null)
                                "Messages" -> Icon(Icons.Filled.Chat, contentDescription = null)
                                else -> Icon(Icons.Filled.Person, contentDescription = null)
                            }
                        },
                        label = { Text("") }
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val direction = if (targetState == "Home") -1 else 1
                val entering = slideInHorizontally(initialOffsetX = { direction * it / 8 }, animationSpec = tween(380)) + fadeIn(tween(240))
                val exiting = slideOutHorizontally(targetOffsetX = { -direction * it / 12 }, animationSpec = tween(320)) + fadeOut(tween(170))
                entering.togetherWith(exiting)
            },
            label = "community-page-transition"
        ) { currentTab ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (currentTab) {
                    "Home" -> HomeScreen(profile, language)
                    "Friends" -> FriendsScreen(profile, language)
                    "Events" -> EventsScreen(language)
                    "Communities" -> CommunitiesScreen(language)
                    "Messages" -> MessagesScreen(profile, language)
                    "Profile" -> ProfileScreen(profile, onLogout, language = language, onLanguageChange = onLanguageChange, onProfileUpdated = onProfileUpdated)
                }
            }
        }
    }
}

@Serializable
private data class CommunityEvent(
    val id: String,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null
)

@Composable
private fun FriendsScreen(profile: MemberProfile, language: String = "English") {
    var members by remember { mutableStateOf<List<MemberProfile>>(emptyList()) }
    var statuses by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var incoming by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var showNotifications by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            members = Supabase.client.from("profiles").select {
                filter { filter("member_status", FilterOperator.EQ, "approved") }
                order("full_name", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }.decodeList<MemberProfile>().filter { it.id != profile.id }

            val reqs = Supabase.client.from("friend_requests").select {
                filter { filter("receiver_id", FilterOperator.EQ, profile.id) }
                filter { filter("status", FilterOperator.EQ, "pending") }
            }.decodeList<FriendRequest>()
            incoming = reqs

            notifications = Supabase.client.from("notifications").select {
                filter { filter("user_id", FilterOperator.EQ, profile.id) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }.decodeList<NotificationItem>()

            val sent = Supabase.client.from("friend_requests").select {
                filter { filter("sender_id", FilterOperator.EQ, profile.id) }
            }.decodeList<FriendRequest>()
            val received = Supabase.client.from("friend_requests").select {
                filter { filter("receiver_id", FilterOperator.EQ, profile.id) }
            }.decodeList<FriendRequest>()
            val loaded = mutableMapOf<String, String>()
            sent.forEach { request ->
                loaded[request.receiverId] = when (request.status) {
                    "accepted" -> "friends"
                    "pending" -> "outgoing"
                    else -> "none"
                }
            }
            received.forEach { request ->
                loaded[request.senderId] = when (request.status) {
                    "accepted" -> "friends"
                    "pending" -> "incoming"
                    else -> loaded[request.senderId] ?: "none"
                }
            }
            statuses = loaded
        } catch (e: Exception) {
            message = e.message ?: "Could not load friends."
        } finally {
            loading = false
        }
    }

    val filtered = members.filter { member ->
        val q = search.trim()
        q.isBlank() || member.fullName.orEmpty().contains(q, true) ||
            member.religiousName.orEmpty().contains(q, true) ||
            member.province.orEmpty().contains(q, true) ||
            member.community.orEmpty().contains(q, true) ||
            member.ministry.orEmpty().contains(q, true)
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Find Friends", style = MaterialTheme.typography.headlineMedium)
                Text("Find and connect with Camillian members.")
            }
            TextButton(onClick = { showNotifications = !showNotifications }) {
                Text(
                    if (notifications.any { !it.isRead }) "Notifications •" else "Notifications",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (showNotifications) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Notifications", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    if (notifications.isEmpty()) {
                        Text("No notifications yet.")
                    } else {
                        notifications.take(10).forEach { notification ->
                            Text(notification.title, fontWeight = FontWeight.SemiBold)
                            if (!notification.body.isNullOrBlank()) Text(notification.body!!)
                            Spacer(Modifier.height(8.dp))
                        }
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    Supabase.client.from("notifications").update({
                                        set("is_read", true)
                                    }) {
                                        filter { filter("user_id", FilterOperator.EQ, profile.id) }
                                    }
                                    notifications = notifications.map { it.copy(isRead = true) }
                                } catch (e: Exception) {
                                    message = e.message ?: "Could not mark notifications as read."
                                }
                            }
                        }) { Text("Mark all as read") }
                    }
                }
            }
        }

        if (incoming.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Friend Requests", style = MaterialTheme.typography.titleLarge)
                    incoming.forEach { request ->
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("New friend request", modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        Supabase.client.postgrest.rpc("respond_friend_request", buildJsonObject {
                                            put("request_id", request.id)
                                            put("accept_request", true)
                                        })
                                        message = "Friend request accepted."
                                    } catch (e: Exception) { message = e.message ?: "Could not accept request." }
                                }
                            }) { Text("Accept") }
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        Supabase.client.postgrest.rpc("respond_friend_request", buildJsonObject {
                                            put("request_id", request.id)
                                            put("accept_request", false)
                                        })
                                        message = "Friend request declined."
                                    } catch (e: Exception) { message = e.message ?: "Could not decline request." }
                                }
                            }) { Text("Decline") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            search,
            { search = it },
            Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search by name, province, community or ministry") }
        )
        Spacer(Modifier.height(12.dp))

        if (loading) CircularProgressIndicator()
        else if (filtered.isEmpty()) Text(if (members.isEmpty()) "No other approved members found." else "No members match your search.")
        else LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filtered, key = { it.id }) { member ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!member.avatarUrl.isNullOrBlank()) {
                                AsyncImage(model = member.avatarUrl, contentDescription = "Member photo", modifier = Modifier.size(56.dp), contentScale = ContentScale.Crop)
                            } else {
                                Image(painter = painterResource(id = R.drawable.camillian_logo), contentDescription = "Camillian logo", modifier = Modifier.size(56.dp), contentScale = ContentScale.Fit)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(member.fullName?.takeIf { it.isNotBlank() } ?: "Unnamed member", style = MaterialTheme.typography.titleMedium)
                                member.province?.takeIf { it.isNotBlank() }?.let { Text("Province: " + it, style = MaterialTheme.typography.bodySmall) }
                                member.community?.takeIf { it.isNotBlank() }?.let { Text("Community: " + it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        when (statuses[member.id]) {
                            "friends" -> Text("Friends", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            "outgoing" -> Text("Request sent", color = MaterialTheme.colorScheme.secondary)
                            "incoming" -> Text("This member sent you a request", color = MaterialTheme.colorScheme.secondary)
                            else -> Button(onClick = {
                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        Supabase.client.postgrest.rpc("send_friend_request", buildJsonObject { put("target_user_id", member.id) })
                                        statuses = statuses + (member.id to "outgoing")
                                        message = "Friend request sent."
                                    } catch (e: Exception) { message = e.message ?: "Could not send request." }
                                }
                            }) { Text("Add Friend") }
                        }
                    }
                }
            }
        }

        if (message.isNotBlank()) {
            Text(message, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Serializable
private data class FriendRequest(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    val status: String
)

@Serializable
private data class NotificationItem(
    val id: String,
    @SerialName("actor_id") val actorId: String? = null,
    @SerialName("notification_type") val notificationType: String,
    val title: String,
    val body: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Composable
private fun EventsScreen(language: String = "English") {
    var events by remember { mutableStateOf<List<CommunityEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            events = Supabase.client.from("events").select {
                order("start_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }.decodeList<CommunityEvent>()
        } catch (e: Exception) {
            message = e.message ?: "Could not load events."
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Events", style = MaterialTheme.typography.headlineMedium)
        Text("Retreats, conferences, chapters, feast days and community events.")
        Spacer(Modifier.height(16.dp))
        if (loading) CircularProgressIndicator()
        else if (events.isEmpty()) Text("No events have been published yet.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(events, key = { it.id }) { event ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(event.title, style = MaterialTheme.typography.titleLarge)
                        if (!event.startAt.isNullOrBlank()) Text(event.startAt!!)
                        if (!event.location.isNullOrBlank()) Text("Location: " + event.location)
                        if (!event.description.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(event.description!!)
                        }
                    }
                }
            }
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
    }
}

@Serializable
private data class Organization(
    val id: String,
    val name: String,
    val type: String,
    val description: String? = null
)

@Composable
private fun CommunitiesScreen(language: String = "English") {
    var organizations by remember { mutableStateOf<List<Organization>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            organizations = Supabase.client.from("organizations").select {
                order("name", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }.decodeList<Organization>()
        } catch (_: Exception) {
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Communities", style = MaterialTheme.typography.headlineMedium)
        Text("Provinces, delegations, communities and formation houses.")
        Spacer(Modifier.height(16.dp))
        if (loading) CircularProgressIndicator()
        else if (organizations.isEmpty()) Text("No communities have been added yet.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(organizations, key = { it.id }) { org ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(org.name, style = MaterialTheme.typography.titleLarge)
                        Text(org.type)
                        if (!org.description.isNullOrBlank()) Text(org.description!!)
                    }
                }
            }
        }
    }
}


@Serializable
private data class Conversation(
    val id: String,
    val title: String? = null,
    @SerialName("is_group") val isGroup: Boolean = false
)

@Serializable
private data class ConversationMember(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String
)

@Serializable
private data class ChatMessage(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("message_text") val messageText: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Composable
private fun MessagesScreen(profile: MemberProfile, language: String = "English") {
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var selected by remember { mutableStateOf<Conversation?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val memberships = Supabase.client.from("conversation_members").select {
                filter { filter("user_id", FilterOperator.EQ, profile.id) }
            }.decodeList<ConversationMember>()
            val ids = memberships.map { it.conversationId }.toSet()
            if (ids.isNotEmpty()) {
                conversations = Supabase.client.from("conversations").select().decodeList<Conversation>()
                    .filter { it.id in ids }
            }
        } catch (_: Exception) {
        } finally {
            loading = false
        }
    }

    if (selected != null) {
        ChatScreen(profile, selected!!, onBack = { selected = null })
        return
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Messages", style = MaterialTheme.typography.headlineMedium)
        Text("Private and community conversations.")
        Spacer(Modifier.height(16.dp))
        if (loading) CircularProgressIndicator()
        else if (conversations.isEmpty()) Text("No conversations yet.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(conversations, key = { it.id }) { conversation ->
                Card(Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { selected = conversation },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth().padding(8.dp)) {
                            Text(conversation.title ?: "Community conversation")
                            Text(if (conversation.isGroup) "Group" else "Private conversation")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(profile: MemberProfile, conversation: Conversation, onBack: () -> Unit) {
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var composer by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            try {
                messages = Supabase.client.from("messages").select {
                    filter { filter("conversation_id", FilterOperator.EQ, conversation.id) }
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }.decodeList<ChatMessage>()
            } catch (_: Exception) {
            }
        }
    }

    fun send() {
        val text = composer.trim()
        if (text.isEmpty()) return
        scope.launch {
            sending = true
            try {
                Supabase.client.from("messages").insert(buildJsonObject {
                    put("conversation_id", conversation.id)
                    put("sender_id", profile.id)
                    put("message_text", text)
                })
                composer = ""
                load()
            } catch (_: Exception) {
            } finally {
                sending = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(conversation.title ?: "Conversation", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (item.senderId == profile.id) "You" else "Member")
                        Text(item.messageText)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = composer,
                onValueChange = { composer = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                maxLines = 3
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { send() }, enabled = !sending && composer.isNotBlank()) {
                Text(if (sending) "..." else "Send")
            }
        }
    }
}

@Composable
private fun ProfileScreen(profile: MemberProfile, onLogout: () -> Unit, language: String = "English", onLanguageChange: (String) -> Unit = {}, onProfileUpdated: (MemberProfile) -> Unit = {}) {
    var fullName by remember { mutableStateOf(profile.fullName.orEmpty()) }
    var religiousName by remember { mutableStateOf(profile.religiousName.orEmpty()) }
    var phone by remember { mutableStateOf(profile.phone.orEmpty()) }
    var place by remember { mutableStateOf(profile.place.orEmpty()) }
    var province by remember { mutableStateOf(profile.province.orEmpty()) }
    var delegation by remember { mutableStateOf(profile.delegation.orEmpty()) }
    var community by remember { mutableStateOf(profile.community.orEmpty()) }
    var ministry by remember { mutableStateOf(profile.ministry.orEmpty()) }
    var roleTitle by remember { mutableStateOf(profile.roleTitle.orEmpty()) }
    var bio by remember { mutableStateOf(profile.bio.orEmpty()) }
    var avatarUrl by remember { mutableStateOf(profile.avatarUrl) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var uploadingAvatar by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> avatarUri = uri }

    Column(Modifier.fillMaxSize()) {
        Text("My Profile", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(20.dp))
        LanguageSelector(selected = language, onSelected = onLanguageChange)
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(model = avatarUrl, contentDescription = "Profile photo", modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Crop)
                } else if (avatarUri != null) {
                    AsyncImage(model = avatarUri, contentDescription = "Selected profile photo", modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Crop)
                } else {
                    Image(painter = painterResource(id = R.drawable.camillian_logo), contentDescription = "Camillian logo", modifier = Modifier.size(120.dp), contentScale = ContentScale.Fit)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { avatarPicker.launch("image/*") }, enabled = !uploadingAvatar) {
                    Text(if (avatarUri == null) "Choose profile photo" else "Change profile photo")
                }
            }
            item { Text(profile.email ?: "") }
            item { OutlinedTextField(fullName, { fullName = it }, Modifier.fillMaxWidth(), label = { Text("Name") }) }
            item { OutlinedTextField(religiousName, { religiousName = it }, Modifier.fillMaxWidth(), label = { Text("Religious name") }) }
            item { OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Phone") }) }
            item { OutlinedTextField(place, { place = it }, Modifier.fillMaxWidth(), label = { Text("Place") }) }
            item { OutlinedTextField(province, { province = it }, Modifier.fillMaxWidth(), label = { Text("Province") }) }
            item { OutlinedTextField(delegation, { delegation = it }, Modifier.fillMaxWidth(), label = { Text("Delegation") }) }
            item { OutlinedTextField(community, { community = it }, Modifier.fillMaxWidth(), label = { Text("Community") }) }
            item { OutlinedTextField(ministry, { ministry = it }, Modifier.fillMaxWidth(), label = { Text("Ministry") }) }
            item { OutlinedTextField(roleTitle, { roleTitle = it }, Modifier.fillMaxWidth(), label = { Text("Role / title") }) }
            item { OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), minLines = 4, label = { Text("Bio") }) }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            try {
                                if (avatarUri != null) {
                                    uploadingAvatar = true
                                    val uri = avatarUri!!
                                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                        ?: error("Could not read the selected profile photo.")
                                    val mime = context.contentResolver.getType(uri).orEmpty()
                                    val extension = when {
                                        mime.contains("png") -> "png"
                                        mime.contains("webp") -> "webp"
                                        else -> "jpg"
                                    }
                                    val path = profile.id + "/avatar-" + System.currentTimeMillis() + "." + extension
                                    Supabase.client.storage.from("camillian-media").upload(path, bytes) { upsert = false }
                                    avatarUrl = Supabase.client.storage.from("camillian-media").publicUrl(path)
                                }

                                Supabase.client.from("profiles").update(buildJsonObject {
                                    put("full_name", fullName.trim())
                                    put("religious_name", religiousName.trim())
                                    put("phone", phone.trim())
                                    put("place", place.trim())
                                    put("province", province.trim())
                                    put("delegation", delegation.trim())
                                    put("community", community.trim())
                                    put("ministry", ministry.trim())
                                    put("role_title", roleTitle.trim())
                                    put("bio", bio.trim())
                                    put("avatar_url", avatarUrl)
                                }) {
                                    filter { filter("id", FilterOperator.EQ, profile.id) }
                                }

                                val updatedProfile = Supabase.client.from("profiles").select {
                                    filter { filter("id", FilterOperator.EQ, profile.id) }
                                }.decodeSingle<MemberProfile>()

                                onProfileUpdated(updatedProfile)
                                message = "Profile saved successfully."
                            } catch (e: Exception) {
                                message = e.message ?: "Could not save profile."
                            } finally {
                                uploadingAvatar = false
                                saving = false
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (saving) "Saving..." else "Save profile") }
            }
            if (message.isNotBlank()) item {
                Text(message, color = MaterialTheme.colorScheme.primary)
            }
            item {
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign out")
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}


@Composable
private fun AdminTools(profile: MemberProfile, language: String = "English") {
    var inviteEmail by remember { mutableStateOf("") }
    var inviteMessage by remember { mutableStateOf("") }
    var inviteSending by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf("") }
    var announcement by remember { mutableStateOf("") }
    var eventTitle by remember { mutableStateOf("") }
    var eventLocation by remember { mutableStateOf("") }
    var eventDescription by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(localized("Administration tools", language), style = MaterialTheme.typography.titleLarge)
            Text("Invite a member by email", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(inviteEmail, { inviteEmail = it }, Modifier.fillMaxWidth(), label = { Text("Member email") }, singleLine = true)
            Button(enabled = !inviteSending && inviteEmail.contains("@"), onClick = {
                scope.launch {
                    inviteSending = true
                    inviteMessage = ""
                    try {
                        Supabase.client.functions.invoke("admin-invite-member", buildJsonObject { put("email", inviteEmail.trim()) })
                        inviteMessage = "Invitation email sent."
                        inviteEmail = ""
                    } catch (e: Exception) {
                        inviteMessage = e.message ?: "Could not send invitation."
                    } finally { inviteSending = false }
                }
            }) { Text(if (inviteSending) "Sending..." else "Send invitation") }
            if (inviteMessage.isNotBlank()) Text(inviteMessage, color = MaterialTheme.colorScheme.primary)

            HorizontalDivider()
            Text("Legacy invitation code", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(inviteCode, { inviteCode = it }, Modifier.fillMaxWidth(), label = { Text("New invitation code") })
            Button(enabled = !busy && inviteCode.length >= 6, onClick = {
                scope.launch {
                    busy = true
                    try {
                        Supabase.client.postgrest.rpc("admin_create_invite", buildJsonObject {
                            put("plain_code", inviteCode.trim())
                            put("invite_description", "Camillian member invitation")
                            put("invite_max_uses", 1)
                        })
                        message = "Invitation code created."
                        inviteCode = ""
                    } catch (e: Exception) { message = e.message ?: "Could not create invitation code." }
                    finally { busy = false }
                }
            }) { Text("Create invitation code") }

            HorizontalDivider()
            OutlinedTextField(announcement, { announcement = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("Announcement / news") })
            Button(enabled = !busy && announcement.isNotBlank(), onClick = {
                scope.launch {
                    busy = true
                    try {
                        Supabase.client.postgrest.rpc("admin_create_post", buildJsonObject {
                            put("post_kind", "announcement")
                            put("post_text", announcement.trim())
                        })
                        message = "Announcement published."
                        announcement = ""
                    } catch (e: Exception) { message = e.message ?: "Could not publish announcement." }
                    finally { busy = false }
                }
            }) { Text("Publish announcement") }

            HorizontalDivider()
            OutlinedTextField(eventTitle, { eventTitle = it }, Modifier.fillMaxWidth(), label = { Text("Event title") })
            OutlinedTextField(eventLocation, { eventLocation = it }, Modifier.fillMaxWidth(), label = { Text("Event location") })
            OutlinedTextField(eventDescription, { eventDescription = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Event description") })
            Button(enabled = !busy && eventTitle.isNotBlank(), onClick = {
                scope.launch {
                    busy = true
                    try {
                        Supabase.client.postgrest.rpc("admin_create_event", buildJsonObject {
                            put("event_title", eventTitle.trim())
                            put("event_description", eventDescription.trim())
                            put("event_location", eventLocation.trim())
                        })
                        message = "Event published."
                        eventTitle = ""; eventLocation = ""; eventDescription = ""
                    } catch (e: Exception) { message = e.message ?: "Could not create event." }
                    finally { busy = false }
                }
            }) { Text("Create event") }

            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun LanguageSelector(selected: String = "English", onSelected: (String) -> Unit = {}) {
    val languages = listOf(
        "English", "Italiano", "Español", "Português",
        "Français", "Deutsch", "Tiếng Việt", "Filipino"
    )
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("Language: " + selected) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language) },
                    onClick = { onSelected(language); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun AdminDashboard(profile: MemberProfile, language: String = "English", onLanguageChange: (String) -> Unit = {}, onLogout: () -> Unit) {
    var status by remember { mutableStateOf("pending") }
    var members by remember { mutableStateOf<List<MemberProfile>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var actionMemberId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    fun loadMembers() {
        scope.launch {
            loading = true
            try {
                members = Supabase.client.from("profiles").select {
                    filter { filter("member_status", FilterOperator.EQ, status) }
                }.decodeList<MemberProfile>()
                message = ""
            } catch (e: Exception) {
                message = e.message ?: "Could not load members."
            } finally { loading = false }
        }
    }

    fun changeStatus(member: MemberProfile, newStatus: String) {
        scope.launch {
            actionMemberId = member.id
            try {
                Supabase.client.postgrest.rpc("admin_set_member_status", buildJsonObject {
                    put("target_profile_id", member.id)
                    put("new_status", newStatus)
                })
                message = "Member status changed to $newStatus."
                loadMembers()
            } catch (e: Exception) {
                message = e.message ?: "Could not change member status."
            } finally { actionMemberId = null }
        }
    }

    LaunchedEffect(status) { loadMembers() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLogout) {
                Text("Logout")
            }

            Column(Modifier.weight(1f)) {
                Text(localized("Admin Dashboard", language), style = MaterialTheme.typography.headlineMedium)
                Text("${localized("Administrator", language)}: ${profile.fullName ?: profile.email ?: "Admin"}")
            }
            TextButton(onClick = { loadMembers() }, enabled = !loading) {
                Text(if (loading) "Loading..." else "Refresh")
            }
        }

        AdminTools(profile, language)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("pending", "approved", "rejected", "suspended").forEach { value ->
                FilterChip(
                    selected = status == value,
                    onClick = { status = value },
                    label = { Text(value.replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)

        if (loading && members.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        members.forEach { member ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        member.fullName?.takeIf { it.isNotBlank() } ?: "Unnamed member",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(member.email ?: "No email")
                    Text("Role: ${member.memberRole}")
                    Text("Status: ${member.memberStatus.replaceFirstChar { it.uppercase() }}")
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (member.memberStatus == "pending") {
                            Button(
                                onClick = { changeStatus(member, "approved") },
                                enabled = actionMemberId == null
                            ) { Text(if (actionMemberId == member.id) "Approving..." else "Approve") }
                        }
                        if (member.memberStatus != "rejected") {
                            OutlinedButton(
                                onClick = { changeStatus(member, "rejected") },
                                enabled = actionMemberId == null
                            ) { Text("Reject") }
                        }
                        if (member.memberStatus != "suspended") {
                            OutlinedButton(
                                onClick = { changeStatus(member, "suspended") },
                                enabled = actionMemberId == null
                            ) { Text("Suspend") }
                        }
                    }
                }
            }
        }

        if (!loading && members.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("No members in ${status.replaceFirstChar { it.uppercase() }}.", style = MaterialTheme.typography.titleMedium)
                    if (status == "pending") {
                        Spacer(Modifier.height(6.dp))
                        Text("If you just added a member in Supabase, tap Refresh.")
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

