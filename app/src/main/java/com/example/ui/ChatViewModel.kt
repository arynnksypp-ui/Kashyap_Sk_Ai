package com.example.ui

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ChatMessageEntity
import com.example.data.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class ChatViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val repository: ChatRepository
    val messages: StateFlow<List<ChatMessageEntity>>

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<Long?>(null)
    val speakingMessageId: StateFlow<Long?> = _speakingMessageId.asStateFlow()

    private val _isVoiceCallActive = MutableStateFlow(false)
    val isVoiceCallActive: StateFlow<Boolean> = _isVoiceCallActive.asStateFlow()

    private val _userName = MutableStateFlow("CruelPlatypus8961")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ChatRepository(database.chatDao())
        
        messages = repository.allMessages.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Initialize default welcome message if DB is empty
        viewModelScope.launch {
            repository.allMessages.collect { list ->
                if (list.isEmpty()) {
                    repository.insertMessage(
                        sender = "ai",
                        text = "Namaste! Main Kashyap_Sk_Ai hu. Maths, Science, Coding, Essay, GK, Translation kuch bhi pucho, main step by step samjhaunga."
                    )
                }
            }
        }

        // Initialize Android TextToSpeech
        try {
            tts = TextToSpeech(application, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("hi", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            isTtsReady = true
        }
    }

    fun sendMessage(promptText: String) {
        val query = promptText.trim()
        if (query.isBlank() || _isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true

            // Insert user message
            repository.insertMessage(sender = "user", text = query)

            // Current message history for context
            val currentList = messages.value

            // Call Gemini API / Repository
            val aiReply = repository.getAiResponse(query, currentList)

            // Insert AI message
            repository.insertMessage(sender = "ai", text = aiReply)

            _isGenerating.value = false
        }
    }

    fun toggleSpeakMessage(messageId: Long, text: String) {
        if (_speakingMessageId.value == messageId) {
            stopSpeaking()
        } else {
            stopSpeaking()
            if (isTtsReady && tts != null) {
                _speakingMessageId.value = messageId
                // Clean markdown/emojis for clean speech
                val cleanText = text
                    .replace(Regex("[#*`📦]"), "")
                    .replace("Part 1: ANALYSIS", "Analysis.")
                    .replace("Part 2: SOLUTION", "Solution.")
                    .replace("Part 3: RESULT", "Result.")
                    .replace("Pro Tip:", "Pro Tip.")

                tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, messageId.toString())
            }
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        _speakingMessageId.value = null
    }

    fun toggleVoiceCall(active: Boolean) {
        _isVoiceCallActive.value = active
    }

    fun clearChat() {
        viewModelScope.launch {
            stopSpeaking()
            repository.clearHistory()
            repository.insertMessage(
                sender = "ai",
                text = "Namaste! Main Kashyap_Sk_Ai hu. Maths, Science, Coding, Essay, GK, Translation kuch bhi pucho, main step by step samjhaunga."
            )
        }
    }

    fun setUserName(name: String) {
        if (name.isNotBlank()) {
            _userName.value = name.trim()
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
