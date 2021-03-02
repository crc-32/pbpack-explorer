package ui

interface SubWindow {
    val id: String
    fun render(): Boolean
}