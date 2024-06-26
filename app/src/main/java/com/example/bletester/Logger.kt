package com.example.bletester

import androidx.compose.ui.graphics.Color

// Энумерация для уровней логирования с указанием цвета
enum class LogLevel(val color: Color) {
    Error(Color.Red),
    Debug(Color.Blue),
    Info(Color.Green)
}

// Класс для представления одного лога
data class LogItem(val tag: String, val message: String, val level: LogLevel)

// Объект-логгер для примера
object Logger {
    private val logs = mutableListOf<LogItem>()
    private val listeners = mutableListOf<(List<LogItem>) -> Unit>()

    fun e(tag: String, message: String) {
        addLog(LogItem(tag, message, LogLevel.Error))
    }

    fun d(tag: String, message: String) {
        addLog(LogItem(tag, message, LogLevel.Debug))
    }

    fun i(tag: String, message: String) {
        addLog(LogItem(tag, message, LogLevel.Info))
    }

    fun getLogs(): List<LogItem> {
        return logs.toList()
    }

    // Добавление лога в список и уведомление слушателей
    private fun addLog(log: LogItem) {
        logs.add(log)
        notifyListeners()
    }

    // Добавление слушателя для обновлений
    fun addLogListener(listener: (List<LogItem>) -> Unit) {
        listeners.add(listener)
        // При добавлении слушателя сразу передаем текущие логи
        listener(logs)
    }

    // Уведомление всех слушателей о изменении логов
    private fun notifyListeners() {
        listeners.forEach { it(logs) }
    }
}