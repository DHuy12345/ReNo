package com.example.project_ck.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.project_ck.ReNoApplication
import com.example.project_ck.data.SessionManager
import com.example.project_ck.data.entity.User
import com.example.project_ck.data.repository.AppRepository
import com.example.project_ck.util.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AppRepository((application as ReNoApplication).database)
    private val sessionManager = SessionManager(application)

    val currentUserIdState: MutableStateFlow<Long> = MutableStateFlow(sessionManager.currentUserId)
    val currentUserFlow: StateFlow<User?> = MutableStateFlow<User?>(null)

    private val _userState = MutableStateFlow<User?>(null)
    val userState: StateFlow<User?> = _userState.asStateFlow()

    init {
        if (sessionManager.currentUserId != -1L) {
            loadUser(sessionManager.currentUserId)
        }
    }

    fun loadUser(userId: Long) {
        viewModelScope.launch {
            repo.getUserFlow(userId).collect { user ->
                _userState.value = user
            }
        }
    }

    fun login(usernameInput: String, passwordInput: String, onResult: (Boolean, String?) -> Unit) {
        if (usernameInput.isBlank() || passwordInput.isBlank()) {
            onResult(false, "Vui lòng nhập đầy đủ username và mật khẩu")
            return
        }

        viewModelScope.launch {
            val res = repo.loginUser(usernameInput.trim(), passwordInput)
            res.onSuccess { user ->
                sessionManager.currentUserId = user.id
                currentUserIdState.value = user.id
                _userState.value = user
                ReminderScheduler.rescheduleAll(getApplication(), user.id)
                onResult(true, null)
            }.onFailure { err ->
                onResult(false, err.message ?: "Đăng nhập thất bại")
            }
        }
    }

    fun register(
        phoneOrEmail: String,
        username: String,
        pass: String,
        confirmPass: String,
        onResult: (Boolean, Map<String, String>) -> Unit
    ) {
        val errors = mutableMapOf<String, String>()

        // Validate phoneOrEmail
        val contact = phoneOrEmail.trim()
        if (contact.contains("@")) {
            if (!Patterns.EMAIL_ADDRESS.matcher(contact).matches()) {
                errors["contact"] = "Email hoặc số điện thoại không hợp lệ"
            }
        } else {
            var phone = contact
            if (phone.startsWith("+84")) phone = "0" + phone.substring(3)
            if (!phone.matches(Regex("^0\\d{9}$"))) {
                errors["contact"] = "Email hoặc số điện thoại không hợp lệ"
            }
        }

        // Validate username
        val uName = username.trim()
        if (uName.length !in 3..30 || uName.contains("@")) {
            errors["username"] = "Username phải từ 3 đến 30 ký tự"
        }

        // Validate password
        if (pass.length !in 6..64) {
            errors["password"] = "Mật khẩu phải có ít nhất 6 ký tự"
        }

        // Validate confirm password
        if (confirmPass != pass) {
            errors["confirmPass"] = "Mật khẩu nhập lại không khớp"
        }

        if (errors.isNotEmpty()) {
            onResult(false, errors)
            return
        }

        viewModelScope.launch {
            val res = repo.registerUser(uName, contact, pass)
            res.onSuccess {
                onResult(true, emptyMap())
            }.onFailure { err ->
                val msg = err.message ?: "Đăng ký thất bại"
                if (msg.contains("Username")) {
                    errors["username"] = msg
                } else {
                    errors["contact"] = msg
                }
                onResult(false, errors)
            }
        }
    }

    fun updateUserProfile(
        newUsername: String,
        newEmailOrPhone: String,
        newReviewTime: String,
        newAvatarPath: String?,
        onResult: (Boolean, String?) -> Unit
    ) {
        val user = _userState.value ?: return
        if (newUsername.length !in 3..30 || newUsername.contains("@")) {
            onResult(false, "Username phải từ 3 đến 30 ký tự")
            return
        }

        var email: String? = user.email
        var phone: String? = user.phone

        val contact = newEmailOrPhone.trim()
        if (contact.isNotEmpty()) {
            if (contact.contains("@")) {
                if (!Patterns.EMAIL_ADDRESS.matcher(contact).matches()) {
                    onResult(false, "Email không hợp lệ")
                    return
                }
                email = contact.lowercase()
            } else {
                var p = contact
                if (p.startsWith("+84")) p = "0" + p.substring(3)
                if (!p.matches(Regex("^0\\d{9}$"))) {
                    onResult(false, "Số điện thoại không hợp lệ")
                    return
                }
                phone = p
            }
        }

        val updatedUser = user.copy(
            username = newUsername.trim(),
            email = email,
            phone = phone,
            review_time = newReviewTime,
            avatar_path = newAvatarPath ?: user.avatar_path
        )

        viewModelScope.launch {
            val res = repo.updateUser(updatedUser)
            res.onSuccess {
                _userState.value = updatedUser
                ReminderScheduler.rescheduleAll(getApplication(), updatedUser.id)
                onResult(true, null)
            }.onFailure { err ->
                onResult(false, err.message ?: "Cập nhật thất bại")
            }
        }
    }

    fun logout() {
        val userId = sessionManager.currentUserId
        sessionManager.logout()
        currentUserIdState.value = -1L
        _userState.value = null
        if (userId != -1L) {
            val alarmManager = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // Clear reminders
        }
    }
}