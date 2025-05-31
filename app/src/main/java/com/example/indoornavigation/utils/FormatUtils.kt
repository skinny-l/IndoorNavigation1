package com.example.indoornavigation.utils

/**
 * Format a float value to a string with the specified number of decimal digits
 */
fun Float.format(digits: Int): String = "%.${digits}f".format(this)

/**
 * Format a double value to a string with the specified number of decimal digits
 */
fun Double.format(digits: Int): String = "%.${digits}f".format(this)