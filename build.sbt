ThisBuild / scalaVersion := "2.13.12"
ThisBuild / organization := "com.example"
ThisBuild / version      := "0.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "version-eviction-highest-wins",
    // Two declarations of the same artifact at different versions.
    // Coursier applies highest-wins eviction: 2.10.0 beats 2.9.0.
    // After resolution, only cats-core_2.13:2.10.0 appears in the tree.
    // cats-core_2.13:2.9.0 is evicted and MUST NOT appear in Mend output.
    // No dependencyOverrides -- pure Coursier eviction only (contrast: probe #3).
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.9.0",
      "org.typelevel" %% "cats-core" % "2.10.0",
    ),
  )