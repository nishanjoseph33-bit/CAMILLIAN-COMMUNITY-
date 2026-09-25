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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    "recovery" -> RecoveryPasswordScreen(onDone = { profile = null })
                    "login" -> LoginScreen(language = language, onLanguageChange = { language = it }, onApproved = { profile = it }, onMessage = { message = it }, initialMessage = message)
                    "admin" -> AdminDashboard(profile!!, onLogout = {
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
private fun RecoveryPasswordScreen(onDone: () -> Unit) {
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
    var showRegister by remember { mutableStateOf(false) }
    var showRecovery by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(initialMessage) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        val compact = maxHeight < 760.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = if (compact) 16.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                LanguageSelector(selected = language, onSelected = onLanguageChange)
            }

            Spacer(Modifier.height(if (compact) 8.dp else 20.dp))

            Image(
                painter = painterResource(id = R.drawable.camillian_logo),
                contentDescription = "Camillian logo",
                modifier = Modifier.size(if (compact) 78.dp else 94.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                shape = RoundedCornerShape(2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFD9D9D9).copy(alpha = 0.96f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = if (compact) 22.dp else 34.dp,
                        vertical = if (compact) 24.dp else 34.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 84.dp else 100.dp)
                            .offset(y = if (compact) (-58).dp else (-66).dp)
                            .background(Color(0xFFD9D9D9), RoundedCornerShape(50))
                            .border(4.dp, Color.White, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "♙",
                            color = Color(0xFF222222),
                            fontSize = if (compact) 48.sp else 56.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(if (compact) (-42).dp else (-48).dp))

                    Text(
                        "LOGIN",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp
                        ),
                        color = Color(0xFF171516)
                    )
                    Text(
                        "Welcome to Camillian Community",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF555154),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(if (compact) 22.dp else 30.dp))

                    LoginField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = localized("Email", language),
                        password = false
                    )

                    Spacer(Modifier.height(14.dp))

                    LoginField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = localized("Password", language),
                        password = true
                    )

                    Spacer(Modifier.height(10.dp))

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
                                    checkedColor = Color(0xFF252122),
                                    uncheckedColor = Color(0xFF444044)
                                )
                            )
                            Text(
                                "Remember",
                                color = Color(0xFF333033),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        TextButton(
                            onClick = { showRecovery = true },
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(
                                localized("Forgot password?", language),
                                color = Color(0xFF333033),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

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
                            .height(54.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF252122),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            if (loading) localized("Checking membership...", language) else "LOGIN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(onClick = { showRegister = true }) {
                        Text(
                            localized("Register", language),
                            color = Color(0xFF0057B8),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (message.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            message,
                            color = Color(0xFF9B1C1C),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Camillian Community",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
        }

        if (showRegister) {
            RegisterDialog(
                onDismiss = { showRegister = false },
                onMessage = { message = it }
            )
        }

        if (showRecovery) {
            RecoveryDialog(
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
    password: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        singleLine = true,
        placeholder = {
            Text(
                placeholder,
                color = Color(0xFFEDEDED)
            )
        },
        leadingIcon = {
            Text(
                if (password) "▣" else "@",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(30.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF252122),
            unfocusedContainerColor = Color(0xFF252122),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.White,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedLeadingIconColor = Color.White,
            unfocusedLeadingIconColor = Color.White
        )
    )
}

@Serializable
private data class InviteCheck(val valid: Boolean)

@Composable
private fun RegisterDialog(onDismiss: () -> Unit, onMessage: (String) -> Unit) {
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RecoveryDialog(onDismiss: () -> Unit, onMessage: (String) -> Unit) {
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
    var provinceMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Camillian Community", style = MaterialTheme.typography.headlineSmall)
                Text("Welcome, " + (profile.fullName ?: profile.email ?: "Member"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showComposer = !showComposer }) {
                    Text(if (showComposer) "×" else "+")
                }
                TextButton(onClick = { loadFeed() }) { Text("Refresh") }
            }
        }

        if (showComposer) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Share with the community", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = {
                            showComposer = false
                            mediaUri = null
                            mediaKind = null
                        }) { Text("Close") }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (mediaUri != null) {
                        Text("Selected " + (mediaKind ?: "media"))
                        TextButton(onClick = { mediaUri = null; mediaKind = null }) { Text("Remove") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { mediaPicker.launch("image/*") }) { Text("Photo") }
                        OutlinedButton(onClick = { mediaPicker.launch("video/*") }) { Text("Video") }
                    }
                    Box {
                        OutlinedButton(onClick = { provinceMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (postProvince.isBlank()) "Add province" else "Province: " + postProvince)
                        }
                        DropdownMenu(expanded = provinceMenuExpanded, onDismissRequest = { provinceMenuExpanded = false }) {
                            if (profile.province.isNullOrBlank()) {
                                DropdownMenuItem(text = { Text("No province set in profile") }, onClick = { provinceMenuExpanded = false })
                            } else {
                                DropdownMenuItem(text = { Text("Province: " + profile.province) }, onClick = { postProvince = profile.province.orEmpty(); provinceMenuExpanded = false })
                            }
                            DropdownMenuItem(text = { Text("Clear province") }, onClick = { postProvince = ""; provinceMenuExpanded = false })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = composer,
                        onValueChange = { composer = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Write a reflection, news update, or message...") },
                        minLines = 3
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { createPost() },
                        enabled = !posting && (composer.isNotBlank() || mediaUri != null),
                        modifier = Modifier.align(Alignment.End)
                    ) { Text(if (posting) "Publishing..." else "Publish") }
                }
            }
        }

        if (message.isNotBlank()) {
            Text(message, modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
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
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(posts, key = { it.id }) { post ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                when (post.kind) {
                                    "news" -> "Camillian News"
                                    "announcement" -> "Announcement"
                                    "scripture" -> "Scripture"
                                    "reflection" -> "Reflection"
                                    else -> "Community Post"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            val author = authorProfiles[post.authorId]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!author?.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(model = author?.avatarUrl, contentDescription = "Member photo", modifier = Modifier.size(44.dp), contentScale = ContentScale.Crop)
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(author?.fullName?.takeIf { it.isNotBlank() } ?: author?.email ?: "Member", style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(8.dp))
                            if (!post.mediaUrl.isNullOrBlank()) {
                                AsyncImage(model = post.mediaUrl, contentDescription = "Post media", modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp), contentScale = ContentScale.Crop)
                                Spacer(Modifier.height(8.dp))
                            }
                            if (!post.textContent.isNullOrBlank()) Text(post.textContent)
                            Spacer(Modifier.height(8.dp))
                            Text(post.createdAt ?: "", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { toggleLike(post.id) },
                                    enabled = reactingPostId == null
                                ) {
                                    Text(
                                        if (reactingPostId == post.id) "Saving..." else if (reactionIds.contains(post.id)) "Liked" else "Like"
                                    )
                                }
                                Text(reactionCounts[post.id]?.toString() ?: "0")
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { commentPostId = post.id }) { Text(localized("Comments", language)) }
                                if (post.authorId == profile.id) {
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
        CommentsDialog(postId = commentPostId!!, profile = profile, onDismiss = { commentPostId = null })
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
private fun CommentsDialog(postId: String, profile: MemberProfile, onDismiss: () -> Unit) {
    var comments by remember { mutableStateOf<List<PostComment>>(emptyList()) }
    var composer by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}


private fun localized(key: String, language: String): String {
    val data = mapOf(
        "Home" to mapOf("Italiano" to "Home", "Español" to "Inicio", "Português" to "Início", "Français" to "Accueil", "Deutsch" to "Start", "Tiếng Việt" to "Trang chủ", "Filipino" to "Home"),
        "Events" to mapOf("Italiano" to "Eventi", "Español" to "Eventos", "Português" to "Eventos", "Français" to "Événements", "Deutsch" to "Termine", "Tiếng Việt" to "Sự kiện", "Filipino" to "Mga Kaganapan"),
        "Communities" to mapOf("Italiano" to "Comunità", "Español" to "Comunidades", "Português" to "Comunidades", "Français" to "Communautés", "Deutsch" to "Gemeinschaften", "Tiếng Việt" to "Cộng đoàn", "Filipino" to "Mga Komunidad"),
        "Messages" to mapOf("Italiano" to "Messaggi", "Español" to "Mensajes", "Português" to "Mensagens", "Français" to "Messages", "Deutsch" to "Nachrichten", "Tiếng Việt" to "Tin nhắn", "Filipino" to "Mga Mensahe"),
        "Profile" to mapOf("Italiano" to "Profilo", "Español" to "Perfil", "Português" to "Perfil", "Français" to "Profil", "Deutsch" to "Profil", "Tiếng Việt" to "Hồ sơ", "Filipino" to "Profile")
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
                        icon = { Text(item.take(1)) },
                        label = { Text(localized(item, language)) }
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
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            members = Supabase.client.from("profiles").select {
                filter { filter("member_status", FilterOperator.EQ, "approved") }
                order("full_name", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }.decodeList<MemberProfile>().filter { it.id != profile.id }
        } catch (_: Exception) {
        } finally { loading = false }
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
        Text("Find Friends", style = MaterialTheme.typography.headlineMedium)
        Text("Find approved members of the Camillian community.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search by name, province, community or ministry") })
        Spacer(Modifier.height(12.dp))
        if (loading) CircularProgressIndicator()
        else if (filtered.isEmpty()) Text(if (members.isEmpty()) "No other approved members found." else "No members match your search.")
        else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(filtered, key = { it.id }) { member ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!member.avatarUrl.isNullOrBlank()) AsyncImage(model = member.avatarUrl, contentDescription = "Member photo", modifier = Modifier.size(56.dp), contentScale = ContentScale.Crop)
                        else Image(painter = painterResource(id = R.drawable.camillian_logo), contentDescription = "Camillian logo", modifier = Modifier.size(56.dp), contentScale = ContentScale.Fit)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(member.fullName?.takeIf { it.isNotBlank() } ?: "Unnamed member", style = MaterialTheme.typography.titleMedium)
                            member.religiousName?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            member.province?.takeIf { it.isNotBlank() }?.let { Text("Province: " + it, style = MaterialTheme.typography.bodySmall) }
                            member.community?.takeIf { it.isNotBlank() }?.let { Text("Community: " + it, style = MaterialTheme.typography.bodySmall) }
                            member.ministry?.takeIf { it.isNotBlank() }?.let { Text("Ministry: " + it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

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
private fun AdminTools(profile: MemberProfile) {
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
            Text("Administration tools", style = MaterialTheme.typography.titleLarge)
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
private fun AdminDashboard(profile: MemberProfile, onLogout: () -> Unit) {
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
                Text("Admin Dashboard", style = MaterialTheme.typography.headlineMedium)
                Text("Administrator: ${profile.fullName ?: profile.email ?: "Admin"}")
            }
            TextButton(onClick = { loadMembers() }, enabled = !loading) {
                Text(if (loading) "Loading..." else "Refresh")
            }
        }

        AdminTools(profile)

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

