package org.camillian.community

import android.os.Bundle
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
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
    @SerialName("member_status") val memberStatus: String,
    @SerialName("member_role") val memberRole: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

@Composable
private fun App() {
    var profile by remember { mutableStateOf<MemberProfile?>(null) }
    var message by remember { mutableStateOf("") }

    if (profile == null) {
        LoginScreen(
            onApproved = { profile = it },
            onMessage = { message = it },
            initialMessage = message
        )
    } else {
        if (profile!!.memberRole == "admin" || profile!!.memberRole == "super_admin") {
            AdminDashboard(profile!!)
        } else {
            CommunityShell(profile!!, onLogout = {
                Supabase.client.auth.signOut()
                profile = null
            })
        }
    }
}

@Composable
private fun LoginScreen(
    onApproved: (MemberProfile) -> Unit,
    onMessage: (String) -> Unit,
    initialMessage: String
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showRegister by remember { mutableStateOf(false) }
    var showRecovery by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(initialMessage) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Camillian Community", style = MaterialTheme.typography.headlineMedium)
        Text("Members only")
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Spacer(Modifier.height(20.dp))
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
                        val user = Supabase.client.auth.currentUserOrNull() ?: error("No active session.")
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Checking membership..." else "Sign in")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { showRegister = true }) { Text("Register") }
            TextButton(onClick = { showRecovery = true }) { Text("Forgot password?") }
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(message)
        }
        if (showRegister) RegisterDialog(onDismiss = { showRegister = false }, onMessage = { message = it })
        if (showRecovery) RecoveryDialog(onDismiss = { showRecovery = false }, onMessage = { message = it })
    }
}


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
                        val valid = Supabase.client.postgrest.rpc("check_invite_code", buildJsonObject { put("invite_code", invite) }).decodeAs<Boolean>()
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
                        Supabase.client.auth.sendRecoveryEmail(email.trim())
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
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class PostReaction(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String,
    val reaction: String = "like"
)

@Composable
private fun HomeScreen(profile: MemberProfile) {
    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var composer by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var posting by remember { mutableStateOf(false) }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaKind by remember { mutableStateOf<String?>(null) }
    var reactionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reactionCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var reactingPostId by remember { mutableStateOf<String?>(null) }
    var commentPostId by remember { mutableStateOf<String?>(null) }
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
                    val extension = if (mediaKind == "video") "mp4" else "jpg"
                    val path = "posts/" + profile.id + "/" + System.currentTimeMillis() + "." + extension
                    Supabase.client.storage["camillian-media"].upload(path, bytes, upsert = false)
                    mediaUrl = Supabase.client.storage["camillian-media"].publicUrl(path)
                }
                Supabase.client.from("posts").insert(buildJsonObject {
                    put("author_id", profile.id)
                    put("kind", mediaKind ?: "text")
                    put("text_content", text.ifBlank { null })
                    put("media_url", mediaUrl)
                    put("media_type", mediaKind)
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
            TextButton(onClick = { loadFeed() }) { Text("Refresh") }
        }

        Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Share with the community", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (mediaUri != null) {
                    Text("Selected " + (mediaKind ?: "media"))
                    TextButton(onClick = { mediaUri = null; mediaKind = null }) { Text("Remove") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { mediaPicker.launch("image/*") }) { Text("Photo") }
                    OutlinedButton(onClick = { mediaPicker.launch("video/*") }) { Text("Video") }
                }
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
                            Text("Member " + post.authorId.take(8), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
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
                                TextButton(onClick = { commentPostId = post.id }) { Text("Comments") }
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


    if (commentPostId != null) {
        CommentsDialog(postId = commentPostId!!, profile = profile, onDismiss = { commentPostId = null })
    }


@Composable
private fun CommunityShell(profile: MemberProfile, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf("Home") }
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Home", "Events", "Communities", "Messages", "Profile").forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.take(1)) },
                        label = { Text(item) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                "Home" -> HomeScreen(profile)
                "Events" -> EventsScreen()
                "Communities" -> CommunitiesScreen()
                "Messages" -> MessagesScreen(profile)
                "Profile" -> ProfileScreen(profile, onLogout)
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
private fun EventsScreen() {
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
private fun CommunitiesScreen() {
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
private fun MessagesScreen(profile: MemberProfile) {
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
private fun ProfileScreen(profile: MemberProfile, onLogout: () -> Unit) {
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
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Text("My Profile", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(20.dp))
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                                }) {
                                    filter { filter("id", FilterOperator.EQ, profile.id) }
                                }
                                message = "Profile saved."
                            } catch (e: Exception) {
                                message = e.message ?: "Could not save profile."
                            } finally {
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
private fun AdminDashboard(profile: MemberProfile) {
    var status by remember { mutableStateOf("pending") }
    var members by remember { mutableStateOf<List<MemberProfile>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var actionMemberId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
            } finally {
                loading = false
            }
        }
    }

    fun changeStatus(member: MemberProfile, newStatus: String) {
        scope.launch {
            actionMemberId = member.id
            message = ""
            try {
                val parameters = buildJsonObject {
                    put("target_profile_id", member.id)
                    put("new_status", newStatus)
                }
                Supabase.client.postgrest.rpc("admin_set_member_status", parameters)
                message = "Member status changed to $newStatus."
                loadMembers()
            } catch (e: Exception) {
                message = e.message ?: "Could not change member status."
            } finally {
                actionMemberId = null
            }
        }
    }

    LaunchedEffect(status) { loadMembers() }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Admin Dashboard", style = MaterialTheme.typography.headlineMedium)
                Text("Administrator: ${profile.fullName ?: profile.email ?: "Admin"}")
            }
            TextButton(
                onClick = { loadMembers() },
                enabled = !loading
            ) {
                Text(if (loading) "Loading..." else "Refresh")
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("pending", "approved", "rejected", "suspended").forEach { value ->
                FilterChip(
                    selected = status == value,
                    onClick = { status = value },
                    label = { Text(value.replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (message.isNotBlank()) {
            Text(message, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }

        if (loading && members.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(members, key = { it.id }) { member ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            member.fullName?.takeIf { it.isNotBlank() } ?: "Unnamed member",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(member.email ?: "No email")
                        Spacer(Modifier.height(4.dp))
                        Text("Role: ${member.memberRole}")
                        Text("Status: ${member.memberStatus.replaceFirstChar { it.uppercase() }}")
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (member.memberStatus == "pending") {
                                Button(
                                    onClick = { changeStatus(member, "approved") },
                                    enabled = actionMemberId == null
                                ) {
                                    Text(
                                        if (actionMemberId == member.id) "Approving..."
                                        else "Approve"
                                    )
                                }
                            }

                            if (member.memberStatus != "rejected") {
                                OutlinedButton(
                                    onClick = { changeStatus(member, "rejected") },
                                    enabled = actionMemberId == null
                                ) {
                                    Text("Reject")
                                }
                            }

                            if (member.memberStatus != "suspended") {
                                OutlinedButton(
                                    onClick = { changeStatus(member, "suspended") },
                                    enabled = actionMemberId == null
                                ) {
                                    Text("Suspend")
                                }
                            }
                        }
                    }
                }
            }

            if (!loading && members.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                "No members in ${status.replaceFirstChar { it.uppercase() }}.",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (status == "pending") {
                                Spacer(Modifier.height(6.dp))
                                Text("If you just added a member in Supabase, tap Refresh.")
                            }
                        }
                    }
                }
            }
        }
    }
}
