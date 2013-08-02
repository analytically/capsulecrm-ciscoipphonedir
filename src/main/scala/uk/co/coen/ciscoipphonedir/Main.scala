package uk.co.coen.ciscoipphonedir

import akka.actor.{Props, ActorSystem}
import akka.io.IO
import spray.can.Http

object Main extends App {
  implicit val system = ActorSystem()

  // the handler actor replies to incoming HttpRequests
  val handler = system.actorOf(Props[CapsuleCRMCiscoIPPhoneDirectoryService], name = "handler")

  val interface = system.settings.config.getString("http.interface")
  val port = system.settings.config.getInt("http.port")

  IO(Http) ! Http.Bind(handler, interface, port)
}