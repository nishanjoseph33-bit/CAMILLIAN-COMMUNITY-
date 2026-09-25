package org.camillian.community

import android.os.Bundle
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
            HomeScreen(profile!!)
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
        if (message.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(message)
        }
    }
}

@Composable
private fun HomeScreen(profile: MemberProfile) {
    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var composer by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var posting by remember { mutableStateOf(false) }
    var reactionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reactionCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val scope = rememberCoroutineScope()

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
        scope.launch {
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
                    Supabase.client.from("post_reactions").insert(buildJsonObject {
                        put("post_id", postId)
                        put("user_id", profile.id)
                        put("reaction", "like")
                    })
                }
                loadReactions()
            } catch (e: Exception) {
                message = e.message ?: "Could not update the reaction."
            }
        }
    }

    fun createPost() {
        val text = composer.trim()
        if (text.isEmpty()) return
        scope.launch {
            posting = true
            try {
                Supabase.client.from("posts").insert(buildJsonObject {
                    put("author_id", profile.id)
                    put("kind", "text")
                    put("text_content", text)
                })
                composer = ""
                loadFeed()
            } catch (e: Exception) {
                message = e.message ?: "Could not publish your post."
            } finally {
                posting = false
            }
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
                Text("Welcome, ${profile.fullName ?: profile.email ?: "Member"}")
            }
            TextButton(onClick = { loadFeed() }) { Text("Refresh") }
        }
        Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Share with the community", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = composer,
                    onValueChange = { composer = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Write a reflection, news update, or message...") },
                    minLines = 3
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { createPost() }, enabled = !posting && composer.isNotBlank(), modifier = Modifier.align(Alignment.End)) {
                    Text(if (posting) "Publishing..." else "Publish")
                }
            }
        }
        if (message.isNotBlank()) Text(message, modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
                            Text("Member ${post.authorId.take(8)}", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            if (!post.textContent.isNullOrBlank()) Text(post.textContent)
                            Spacer(Modifier.height(8.dp))
                            Text(post.createdAt ?: "", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { toggleLike(post.id) }) {
                                    Text(if (reactionIds.contains(post.id)) "♥ Liked" else "♡ Like")
                                }
                                Text(reactionCounts[post.id]?.toString() ?: "0")
                            }
                        }
                    }
                }
            }
        }
    }
}


@Serializable
private data class PostReaction(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String,
    val reaction: String = "like"
)

@Serializable
private data class FeedPost(
    val id: String,
    @SerialName("author_id") val authorId: String,
    val content: String? = null,
    @SerialName("kind") val kind: String = "text",
    @SerialName("text_content") val textContent: String? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
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

                Supabase.client.postgrest.rpc(
                    "admin_set_member_status",
                    parameters
                )
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
        Text("Admin Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text("Administrator: ${profile.fullName ?: profile.email ?: "Admin"}")
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("pending", "approved", "rejected", "suspended").forEach { value ->
                FilterChip(
                    selected = status == value,
                    onClick = { status = value },
                    label = { Text(value.replaceFirstChar { it.uppercase() }) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (loading) CircularProgressIndicator()
        if (message.isNotBlank()) Text(message)
        members.forEach { member ->
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(member.fullName ?: "Unnamed member", style = MaterialTheme.typography.titleMedium)
                    Text(member.email ?: "")
                    Text("Role: ${member.memberRole}")
                    Text("Status: ${member.memberStatus}")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (member.memberStatus != "approved") {
                            Button(
                                onClick = { changeStatus(member, "approved") },
                                enabled = actionMemberId == null
                            ) { Text("Approve") }
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
        if (!loading && members.isEmpty() && message.isBlank()) {
            Text("No members in this status.")
        }
    }
}
