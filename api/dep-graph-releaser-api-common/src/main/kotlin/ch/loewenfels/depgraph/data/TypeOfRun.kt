package ch.loewenfels.depgraph.data

enum class TypeOfRun {
    EXPLORE,
    DRY_RUN,
    RELEASE
}

fun TypeOfRun.toProcessName() = when(this){
    TypeOfRun.EXPLORE ->  "Explore Release Order"
    TypeOfRun.DRY_RUN -> "Dry Run"
    TypeOfRun.RELEASE -> "Release"
}
