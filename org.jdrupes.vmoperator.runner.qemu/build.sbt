enablePlugins(JavaAppPackaging)

libraryDependencies ++= Seq(
    "org.jgrapes" % "org.jgrapes.core" % "[1.22.1,2)",
    "org.jgrapes" % "org.jgrapes.util" % "[1.38.1,2)",
    "org.jgrapes" % "org.jgrapes.io" % "[2.12.1,3)",
    "org.jgrapes" % "org.jgrapes.http" % "[3.5.0,4)",
    "commons-cli" % "commons-cli" % "1.5.0",
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "[2.16.1]",
    "org.slf4j" % "slf4j-jdk14" % "[2.0.7,3)" % Runtime
)
