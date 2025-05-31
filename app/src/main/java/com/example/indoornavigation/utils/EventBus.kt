package com.example.indoornavigation.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Simple event bus implementation using observer pattern
 */
object EventBus {
    private val listeners = mutableMapOf<String, MutableList<(Any) -> Unit>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    /**
     * Post an event to the event bus
     */
    fun post(event: Any) {
        val eventClass = event.javaClass.name
        scope.launch {
            listeners[eventClass]?.forEach { listener ->
                listener(event)
            }
        }
    }
    
    /**
     * Register to receive events of a specific type
     */
    fun <T : Any> register(type: Class<T>, onEvent: (T) -> Unit) {
        val eventClass = type.name
        if (!listeners.containsKey(eventClass)) {
            listeners[eventClass] = mutableListOf()
        }
        
        listeners[eventClass]?.add { event ->
            if (type.isInstance(event)) {
                @Suppress("UNCHECKED_CAST")
                onEvent(event as T)
            }
        }
    }
    
    /**
     * Unregister all listeners for a specific event type
     */
    fun <T : Any> unregister(type: Class<T>) {
        val eventClass = type.name
        listeners.remove(eventClass)
    }
}