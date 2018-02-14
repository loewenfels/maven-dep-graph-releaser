package ch.loewenfels.depgraph.maven

import java.io.File

fun getTestDirectory(name: String) = File(MavenFacadeSpec.javaClass.getResource("/$name/").path)
