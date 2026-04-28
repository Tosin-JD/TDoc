package com.tosin.docprocessor.data.parser.util

import com.tosin.docprocessor.data.parser.util.TDocLogger

object SafeConversions {
    
    fun toInt(value: Any?, default: Int = 0, context: String = "unknown"): Int {
        if (value == null) return default
        return try {
            when (value) {
                is Number -> value.toInt()
                is String -> value.trim().toInt()
                else -> {
                    TDocLogger.warn("Cannot convert $value to Int in context: $context. Using default: $default")
                    default
                }
            }
        } catch (e: Exception) {
            TDocLogger.warn("Failed to convert $value to Int in context: $context. Error: ${e.message}. Using default: $default")
            default
        }
    }

    fun toDouble(value: Any?, default: Double = 0.0, context: String = "unknown"): Double {
        if (value == null) return default
        return try {
            when (value) {
                is Number -> value.toDouble()
                is String -> value.trim().toDouble()
                else -> {
                    TDocLogger.warn("Cannot convert $value to Double in context: $context. Using default: $default")
                    default
                }
            }
        } catch (e: Exception) {
            TDocLogger.warn("Failed to convert $value to Double in context: $context. Error: ${e.message}. Using default: $default")
            default
        }
    }

    fun toBoolean(value: Any?, default: Boolean = false, context: String = "unknown"): Boolean {
        if (value == null) return default
        return try {
            when (value) {
                is Boolean -> value
                is String -> value.trim().lowercase().let { 
                    it == "true" || it == "1" || it == "on" || it == "yes" 
                }
                is Number -> value.toInt() != 0
                else -> {
                    TDocLogger.warn("Cannot convert $value to Boolean in context: $context. Using default: $default")
                    default
                }
            }
        } catch (e: Exception) {
            TDocLogger.warn("Failed to convert $value to Boolean in context: $context. Error: ${e.message}. Using default: $default")
            default
        }
    }
}
