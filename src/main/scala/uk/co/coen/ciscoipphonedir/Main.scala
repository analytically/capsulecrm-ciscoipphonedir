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
import com.google.common.base.CharMatcher
import scala.collection._
import mutable.ListBuffer
import spray.http.HttpHeaders.RawHeader

class UniqueFixedList[A](max: Int) extends Traversable[A] {
  val list: ListBuffer[A] = ListBuffer()

  def +=(elem: A): this.type = {
    if (!list.contains(elem)) {
      if (list.size == max) {
        list.trimStart(1)
      }
      list.append(elem)
    }
    this
  }

  def foreach[U](f: A => U) = list.foreach(f)
}

object Main extends App with SimpleRoutingApp {
  implicit val system = ActorSystem("capsule-cisco")
  implicit val executionContext = system.dispatcher

  val newlines = CharMatcher.is('\n')

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

  val lastSearches = mutable.Map[RemoteAddress, UniqueFixedList[String]]()
  val simpleCache = routeCache(maxCapacity = 5000, timeToIdle = Duration("10 min"))

  startServer(interface, port) {
    (get & respondWithMediaType(`text/xml`) & clientIP) { ip =>
      path("capsule.xml") {
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
              {
                for (search <- lastSearches.get(ip).getOrElse(Nil)) yield {
                  <MenuItem>
                    <Name>'{ search }'</Name>
                    <URL>http://{ hostname }/search.xml?q={ search }</URL>
                  </MenuItem>
                }
              }
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
            parameters('q ?, 'tag ?, 'start.as[Int] ?) { (q, tag, start) =>
              respondWithHeader(RawHeader("Refresh", s"100;url=http://$hostname/search.xml?q=${q.orElse(tag).get}&start=${start.getOrElse(0) + 25}")) {
                ctx =>
                  val uri = q match {
                    case Some(query) =>
                      lastSearches += (ip -> (lastSearches.getOrElse(ip, new UniqueFixedList[String](5)) += query))
                      capsuleUri.copy(query = Query("q" -> query, "start" -> start.getOrElse(0).toString, "limit" -> "25"))
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
                        """<?xml version="1.0" encoding="utf-8" ?>""" + newlines.removeFrom(
                          <CiscoIPPhoneDirectory>
                            <Title>{ title }</Title>
                            <Prompt>{ title }</Prompt>
                            {
                              for (id <- json.extract[String](organisationArrayIds)) yield {
                                <DirectoryEntry>
                                  <Name>{ json.extract[String](organisations / filter('id.is[String](_ == id)) / 'name) }</Name>
                                  <Telephone>{ CharMatcher.WHITESPACE.removeFrom(json.extract[String](organisations / filter('id.is[String](_ == id)) / firstPhoneNumber).head) }</Telephone>
                                </DirectoryEntry>
                              }
                            }
                            {
                              for (id <- json.extract[String](personArrayIds)) yield {
                                <DirectoryEntry>
                                  <Name>
                                    { json.extract[String](persons / filter('id.is[String](_ == id)) / 'firstName) } { json.extract[String](persons / filter('id.is[String](_ == id)) / 'lastName) } {
                                      json.extract[String](persons / filter('id.is[String](_ == id)) / optionalField("organisationName")).headOption match {
                                        case None => ""
                                        case Some(on) => " at " + on
                                      }
                                    }
                                  </Name>
                                  <Telephone>{ CharMatcher.WHITESPACE.removeFrom(json.extract[String](persons / filter('id.is[String](_ == id)) / firstPhoneNumber).head) }</Telephone>
                                </DirectoryEntry>
                              }
                            }
                          </CiscoIPPhoneDirectory>.toString()))
                  }
              }
            }
          }
        }
    }
  }
}
