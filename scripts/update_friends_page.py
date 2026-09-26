from pathlib import Path

p = Path("app/src/main/java/org/camillian/community/MainActivity.kt")
s = p.read_text()

# Remove the Message action from the Friends page. Chat remains available from Messages.
s = s.replace('''                            "friends" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(localized("Friends", language), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Button(
                                    onClick = { openFriendChat(member.id) },
                                    enabled = openingChatMemberId == null
                                ) {
                                    Text(
                                        if (openingChatMemberId == member.id) localized("Opening...", language)
                                        else localized("Message", language)
                                    )
                                }
                            }''', '''                            "friends" -> Text(
                                localized("Friends", language),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )''')

# Show Accept for an incoming request directly on that member's profile card.
s = s.replace('''                            "incoming" -> Text(localized("This member sent you a request", language), color = MaterialTheme.colorScheme.secondary)''', '''                            "incoming" -> {
                                val request = incoming.firstOrNull { it.senderId == member.id }
                                if (request != null) {
                                    Button(onClick = {
                                        scope.launch {
                                            try {
                                                Supabase.client.postgrest.rpc("respond_friend_request", buildJsonObject {
                                                    put("request_id", request.id)
                                                    put("accept_request", true)
                                                })
                                                statuses = statuses + (member.id to "friends")
                                                incoming = incoming.filterNot { it.id == request.id }
                                                message = ""
                                            } catch (e: Exception) {
                                                message = e.message ?: "Could not accept friend request."
                                            }
                                        }
                                    }) { Text(localized("Accept", language)) }
                                } else {
                                    Text(localized("This member sent you a request", language), color = MaterialTheme.colorScheme.secondary)
                                }
                            }''')

p.write_text(s)
