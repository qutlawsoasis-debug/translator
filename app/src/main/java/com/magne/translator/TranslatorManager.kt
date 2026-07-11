package com.magne.translator

class TranslatorManager {
    private val dictionary = hashMapOf(
        "привет" to "Hello",
        "как дела" to "How are you",
        "до свидания" to "Goodbye",
        "пока" to "Goodbye",
        "спасибо" to "Thank you",
        "где туалет" to "Where is the restroom",
        "сколько стоит" to "How much does it cost",
        "я не понимаю" to "I do not understand",
        "помогите" to "Help me",
        "помоги мне" to "Help me",
        "который час" to "What time is it",
        "что это" to "What is this"
    )

    fun translate(russianText: String): String? {
        val lowerText = russianText.lowercase().trim()
        if (lowerText.isEmpty()) return null
        
        // Точное совпадение
        if (dictionary.containsKey(lowerText)) {
            return dictionary[lowerText]
        }
        
        // Частичное совпадение (если человек сказал чуть больше)
        for ((key, value) in dictionary) {
            if (lowerText.contains(key)) {
                return value
            }
        }
        
        return null
    }
}
