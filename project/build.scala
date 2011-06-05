import sbt._
import Keys._

object ScalatraBuild extends Build {
  override lazy val settings = super.settings ++ Seq(
    organization := "org.scalatra",
    scalaVersion := "2.9.0-1",
    crossScalaVersions := Seq("2.9.0-1", "2.9.0"),
    version := "2.0.0-SNAPSHOT"
  )

  val sonatypeSnapshots = "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

  // Define deps in their own object, or else they become global.
  object Deps {
    val base64 = "net.iharder" % "base64" % "2.3.8"

    val commonsFileupload = "commons-fileupload" % "commons-fileupload" % "1.2.1" % "compile"
    val commonsIo = "commons-io" % "commons-io" % "1.4" % "compile"

    def jettyModule(artifactId: String) = "org.eclipse.jetty" % artifactId % "7.4.1.v20110513"
    val jettyWebapp = jettyModule("jetty-webapp") % "jetty"
    val jettyWebsocket = jettyModule("jetty-websocket")
    val testJettyServlet = jettyModule("test-jetty-servlet")

    val junit = "junit" % "junit" % "4.8.1"

    val mockito = "org.mockito" % "mockito-all" % "1.8.5" % "test"

    def scalateVersion(scalaVersion: String) = scalaVersion match {
      case "2.8.0" => "1.3.2"
      case "2.8.1" => "1.4.1" 
      case _ => "1.5.0"
    }
    def scalateCore(scalaVersion: String) = 
      "org.fusesource.scalate" % "scalate-core" % scalateVersion(scalaVersion)

    def scalatest(scalaVersion: String) = {
      val artifactId = scalaVersion match {
        case x if (x startsWith "2.8.") => "scalatest"
        case "2.9.0-1" => "scalatest_2.9.0"
        case x => "scalatest_"+x
      }
      val version = scalaVersion match {
        case x if (x startsWith "2.8.") => "1.3"
        case _ => "1.4.1"
      }
      "org.scalatest" % artifactId % version
    }

    def servletApi = "javax.servlet" % "servlet-api" % "2.5" % "provided"

    def slf4jModule(artifactId: String) = "org.slf4j" % artifactId % "1.6.0"
    val slf4jApi = slf4jModule("slf4j-api")
    val slf4jNop = slf4jModule("slf4j-nop") % "runtime"

    def socketioJava(version: String) = "org.scalatra.socketio-java" % "socketio-core" % version

    def specs(scalaVersion: String) = {
      val artifactId = scalaVersion match {
        case "2.9.0-1" => "specs_2.9.0"
        case x => "specs_"+x
      }
      val version = scalaVersion match {
        case "2.8.0" => "1.6.5"
        case _ => "1.6.8"
      }
      "org.scala-tools.testing" % artifactId % version
    }

    def specs2(scalaVersion: String) = {
      val artifactId = scalaVersion match { 
        case "2.8.0" => "specs2_2.8.1" // not released for 2.8.0, but works
        case x => "specs2_"+x
      }
      "org.specs2" % artifactId % "1.3"
    }
  }
  import Deps._

  lazy val scalatraProject = Project("scalatra-project", file("."))
    .aggregate(scalatraTest, scalatraScalatest, scalatraSpecs, scalatraSpecs2,
      scalatra, scalatraAuth, scalatraFileupload, scalatraScalate, scalatraSocketio,
      scalatraExample)

  lazy val scalatraTest = Project("scalatra-test", file("test"))
    .settings(libraryDependencies ++= Seq(testJettyServlet))

  lazy val scalatraScalatest = Project("scalatra-scalatest", file("scalatest"))
    .settings(libraryDependencies <<= (scalaVersion, libraryDependencies) {(sv, deps) =>
      deps ++ Seq(scalatest(sv), junit)})
    .dependsOn(scalatraTest)

  lazy val scalatraSpecs = Project("scalatra-specs", file("specs"))
    .settings(libraryDependencies <<= (scalaVersion, libraryDependencies) {(sv, deps) =>
      deps :+ specs(sv)})
    .dependsOn(scalatraTest)

  lazy val scalatraSpecs2 = Project("scalatra-specs2", file("specs2"))
    .settings(libraryDependencies <<= (scalaVersion, libraryDependencies) {(sv, deps) =>
      deps :+ specs2(sv)})
    .dependsOn(scalatraTest)

  implicit def richProject(project: Project) = new {
    def testWithScalatraTest: Project = {
      val testProjects = Seq(scalatraScalatest, scalatraSpecs, scalatraSpecs2)
      val testConfigured = testProjects map { _ % "test" }
      project dependsOn (testConfigured : _*)
    }
  }

  lazy val scalatra = Project("scalatra", file("core"))
    .settings(libraryDependencies ++= Seq(servletApi, mockito))
    .testWithScalatraTest

  lazy val scalatraAuth = Project("scalatra-auth", file("auth"))
    .settings(libraryDependencies ++= Seq(servletApi, base64, mockito))
    .dependsOn(scalatra)
    .testWithScalatraTest

  lazy val scalatraFileupload = Project("scalatra-fileupload", file("fileupload"))
    .settings(libraryDependencies ++= Seq(servletApi, commonsIo, commonsFileupload))
    .dependsOn(scalatra)
    .testWithScalatraTest

  lazy val scalatraScalate = Project("scalatra-scalate", file("scalate"))
    .settings(libraryDependencies <<= (scalaVersion, libraryDependencies) {(sv, deps) =>
      deps ++ Seq(servletApi, scalateCore(sv))})
    .dependsOn(scalatra)
    .testWithScalatraTest

  lazy val scalatraSocketio = Project("scalatra-socketio", file("socketio"))
    .settings(
      libraryDependencies <<= (version, libraryDependencies) {
        (v, deps) => deps ++ Seq(socketioJava(v), jettyWebsocket)},
      resolvers += sonatypeSnapshots)
    .dependsOn(scalatra)
    .testWithScalatraTest

  lazy val scalatraExample = Project("scalatra-example", file("example"))
    .settings(WebPlugin.webSettings :_ *)
    .settings(resolvers += sonatypeSnapshots,
              libraryDependencies ++= Seq(jettyWebapp, slf4jApi, slf4jNop),
              publishTo := None)
    .dependsOn(scalatra, scalatraFileupload, scalatraScalate, scalatraAuth, scalatraSocketio)
    .testWithScalatraTest
}
