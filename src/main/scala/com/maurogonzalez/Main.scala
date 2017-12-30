package com.maurogonzalez

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.separateOnSlashes
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object Main extends App with HttpService {

  private val configKey = ""
  private val env = sys.env.getOrElse("ENVIRONMENT", default = "local")

  override def executor = system.dispatcher
  override protected def log = Logging(system, service)
  override implicit val system: ActorSystem = ActorSystem()
  override implicit val materializer: Materializer = ActorMaterializer()

  override def akkaConfig: Config = ConfigFactory.load()
  private val swaggerAddress = "localhost"


  def bind(route: Route, interface: String, basePath: String): Int ⇒ Boolean = {
    (port: Int) ⇒
      {
        val mainRouter = pathPrefix(separateOnSlashes(basePath)) {
          route ~ new SwaggerDocService(swaggerAddress, port, system, basePath, akkaConfig.getString("swagger.host")).routes
        }

        val eventualBinding = Http().bindAndHandle(mainRouter, interface, port)
        Try(Await.result(eventualBinding, Duration(60, "seconds"))) match {
          case Failure(t) ⇒
            log.error("Error binding server", t.asInstanceOf[Exception])
            true
          case Success(_) ⇒
            log.info("Server bound correctly")
            log.info("Listening on port {} with binding interface {}", port, interface)
            false
        }
      }
  }

  val portFrom: Int = akkaConfig.getInt("akka.http.ports.from")
  val portTo: Int = akkaConfig.getInt("akka.http.ports.to")
  val ports = scala.util.Random.shuffle(scala.Range.inclusive(portFrom, portTo))

  ports.takeWhile(bind(configureRoutes(system), akkaConfig.getString("akka.http.interface"), akkaConfig.getString("akka.http.path-prefix")))

}