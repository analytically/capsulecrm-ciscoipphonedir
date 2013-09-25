package uk.co.coen.ciscoipphonedir

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import spray.http._
import scala.concurrent.Future
import spray.client.pipelining._
import spray.routing.SimpleRoutingApp
import spray.json.{ JsValue, JsonParser, DefaultJsonProtocol }
import spray.http.StatusCodes._
import spray.http.HttpRequest
import scala.Some
import spray.http.HttpResponse
import MediaTypes._
import spray.routing.directives.CachingDirectives._
import scala.concurrent.duration.Duration
import spray.http.Uri._

object Main extends App with SimpleRoutingApp {
  implicit val system = ActorSystem("capsule-cisco")
  implicit val executionContext = system.dispatcher

  val interface = system.settings.config.getString("http.interface")
  val port = system.settings.config.getInt("http.port")

  val config = ConfigFactory.load
  val title = config.getString("title")
  val hostname = config.getString("hostname")

  val capsuleUri = Uri(s"${config.getString("capsulecrm.url")}/api/party")
  val capsuleToken = config.getString("capsulecrm.token")

  val capsulePipeline: HttpRequest => Future[HttpResponse] = (
    addCredentials(BasicHttpCredentials(capsuleToken, ""))
    ~> addHeader("Accept", "application/json")
    ~> sendReceive)

  val simpleCache = routeCache(maxCapacity = 5000, timeToIdle = Duration("30 min"))

  startServer(interface, port) {
    (get & respondWithMediaType(`text/xml`)) {
      path("") {
        complete {
          """<?xml version="1.0" encoding="utf-8" ?>""" +
            <CiscoIPPhoneMenu>
              <Title>{ title }</Title>
              <Prompt>Search Capsule CRM</Prompt>
              <MenuItem>
                <Name>Search by name</Name>
                <URL>http://{ hostname }/inputname.xml</URL>
              </MenuItem>
              <MenuItem>
                <Name>Search by tag</Name>
                <URL>http://{ hostname }/inputtag.xml</URL>
              </MenuItem>
            </CiscoIPPhoneMenu>
        }
      } ~
        path("inputname.xml") {
          complete {
            """<?xml version="1.0" encoding="utf-8" ?>""" +
              <CiscoIPPhoneInput>
                <Title>{ title }</Title>
                <Prompt>Search by name</Prompt>
                <URL>http://{ hostname }/search.xml</URL>
                <InputItem>
                  <DisplayName>Enter the name</DisplayName>
                  <QueryStringParam>q</QueryStringParam>
                  <InputFlags>A</InputFlags>
                </InputItem>
              </CiscoIPPhoneInput>
          }
        } ~
        path("inputtag.xml") {
          complete {
            """<?xml version="1.0" encoding="utf-8" ?>""" +
              <CiscoIPPhoneInput>
                <Title>{ title }</Title>
                <Prompt>Search by tag</Prompt>
                <URL>http://{ hostname }/search.xml</URL>
                <InputItem>
                  <DisplayName>Enter the tag</DisplayName>
                  <QueryStringParam>tag</QueryStringParam>
                  <InputFlags>A</InputFlags>
                </InputItem>
              </CiscoIPPhoneInput>
          }
        } ~
        path("search.xml") {
          alwaysCache(simpleCache) {
            parameters('q ?, 'tag ?) { (q, tag) =>
              ctx =>
                val uri = q match {
                  case Some(query) => capsuleUri.copy(query = Query("q" -> query))
                  case None => capsuleUri.copy(query = Query("tag" -> tag.getOrElse("")))
                }

                capsulePipeline(Get(uri)).onSuccess {
                  case response: HttpResponse =>
                    import spray.json.lenses.JsonLenses._
                    import DefaultJsonProtocol._

                    val json = JsonParser(response.entity.asString)

                    val organisations = 'parties / optionalField("organisation") / arrayOrSingletonAsArray
                    val persons = 'parties / optionalField("person") / arrayOrSingletonAsArray
                    val firstPhoneNumber = 'contacts / 'phone / arrayOrSingletonAsArray / element(0) / 'phoneNumber

                    val organisationArrayIds = organisations / filter(('contacts / 'phone).is[JsValue](_ => true)) / 'id
                    val personArrayIds = persons / filter(('contacts / 'phone).is[JsValue](_ => true)) / 'id

                    ctx.complete(OK,
                      """<?xml version="1.0" encoding="utf-8" ?>""" +
                        <CiscoIPPhoneDirectory>
                          <Title>{ title }</Title>
                          <Prompt>{ title }</Prompt>
                          {
                            for (id <- json.extract[String](organisationArrayIds)) yield {
                              <DirectoryEntry>
                                <Name>
                                  { json.extract[String](organisations / filter('id.is[String](_ == id)) / 'name) }
                                </Name>
                                <Telephone>
                                  { json.extract[String](organisations / filter('id.is[String](_ == id)) / firstPhoneNumber) }
                                </Telephone>
                              </DirectoryEntry>
                            }
                          }
                          {
                            for (id <- json.extract[String](personArrayIds)) yield {
                              <DirectoryEntry>
                                <Name>
                                  { json.extract[String](persons / filter('id.is[String](_ == id)) / 'firstName) }
                                  { json.extract[String](persons / filter('id.is[String](_ == id)) / 'lastName) }{
                                    json.extract[String](persons / filter('id.is[String](_ == id)) / optionalField("organisationName")).headOption match {
                                      case None => ""
                                      case Some(on) => " at " + on
                                    }
                                  }
                                </Name>
                                <Telephone>
                                  { json.extract[String](persons / filter('id.is[String](_ == id)) / firstPhoneNumber) }
                                </Telephone>
                              </DirectoryEntry>
                            }
                          }
                        </CiscoIPPhoneDirectory>.toString())
                }
            }
          }
        }
    }
  }
}