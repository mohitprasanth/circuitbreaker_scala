name := """hxfn"""

version := "1.0"

scalaVersion := "2.12.14"

val akkaVersion = "2.5.13"
val akkaHttpVersion = "10.1.3"

libraryDependencies ++= Seq(
  "com.netflix.hystrix" % "hystrix-core" % "1.4.20",
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.5"
)