package org.camillian.community

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object Supabase {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    ) {
        install(Auth) {
            flowType = io.github.jan.supabase.auth.FlowType.PKCE
            scheme = "camillian"
            host = "auth"
        }
        install(Postgrest)
        install(Storage)
    }
}
