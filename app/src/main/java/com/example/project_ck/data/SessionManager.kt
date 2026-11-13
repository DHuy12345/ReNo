package com.example.project_ck.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("reno_session_prefs", Context.MODE_PRIVATE)

    var currentUserId: Long
        get() = prefs.getLong(KEY_USER_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_USER_ID, value).apply()

    fun isLoggedIn(): Boolean = currentUserId != -1L

    fun logout() {
        prefs.edit().remove(KEY_USER_ID).apply()
    }

    var ideaSortAscending: Boolean
        get() = prefs.getBoolean(KEY_IDEA_SORT_ASC, false)
        set(value) = prefs.edit().putBoolean(KEY_IDEA_SORT_ASC, value).apply()

    companion object {
        private const val KEY_USER_ID = "current_user_id"
        private const val KEY_IDEA_SORT_ASC = "idea_sort_asc"
    }
}