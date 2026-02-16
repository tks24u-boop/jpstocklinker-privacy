package jp.stocklinker.app

data class Group(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String,
    var color: String = "#4FC3F7",
    var icon: String = "📁",
    var order: Int = 0
)
