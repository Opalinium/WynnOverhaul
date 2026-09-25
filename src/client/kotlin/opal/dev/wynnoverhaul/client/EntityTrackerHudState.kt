package opal.dev.wynnoverhaul.client

object EntityTrackerHudState {
    data class Row(val label: String, val distance: Double, val colorArgb: Int)

    @Volatile
    var rows: List<Row> = emptyList()
}
