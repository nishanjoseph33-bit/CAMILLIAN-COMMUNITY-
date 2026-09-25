package org.camillian.community

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.providers.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.eq
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
                            filter { eq("id", user.id) }
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
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Camillian Community", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("Welcome, ${profile.fullName ?: profile.email ?: "Member"}")
        Spacer(Modifier.height(24.dp))
        Text("Home feed will be connected here next.")
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
                    filter { eq("member_status", status) }
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
                Supabase.client.postgrest.rpc("admin_set_member_status") {
                    parameter("target_profile_id", member.id)
                    parameter("new_status", newStatus)
                }
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
