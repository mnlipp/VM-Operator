lazy val commonSettings = Seq(
    // Sensible layout
    Compile / javaSource := baseDirectory.value / "src",
    Compile / resourceDirectory := baseDirectory.value / "resources",
    Test / javaSource := baseDirectory.value / "test",
    Test / resourceDirectory := baseDirectory.value / "test-resources",
    
    // Do not append Scala versions to the generated artifacts
    crossPaths := false,
    // This forbids including Scala related libraries into the dependency
    autoScalaLibrary := false,
    
    Compile / packageDoc / publishArtifact := false
)

lazy val util = (project in file("org.jdrupes.vmoperator.util"))
    .settings(commonSettings: _*)

lazy val common = (project in file("org.jdrupes.vmoperator.common"))
    .settings(commonSettings: _*)
    .dependsOn(util)

lazy val runnerQemu = (project in file("org.jdrupes.vmoperator.runner.qemu"))
    .settings(commonSettings: _*)
    .dependsOn(common)
