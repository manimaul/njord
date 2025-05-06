
fun main() {
    val s57 = S57(
        "/home/willard/source/njord/chart_server/src/jvmTest/data/US5WA22M/US5WA22M.000"
    )
    s57.layerNames.forEach {
        println("layer name $it")
    }
    val fc = s57.featureCount()
    println("feature count = $fc")
}
