package ch.loewenfels.depgraph.maven

import java.io.File

fun getTestDirectory(name: String) = File(IntegrationSpec.javaClass.getResource("/$name/").path)
