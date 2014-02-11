package uk.co.coen.ciscoipphonedir

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import spray.http._
import scala.concurrent.Future
import spray.client.pipelining._
import spray.routing.{Directive0, SimpleRoutingApp}
import spray.json.{DefaultJsonProtocol, JsValue, JsonParser}
import spray.http.StatusCodes._
import spray.http.HttpRequest
import scala.Some
import spray.http.HttpResponse
import MediaTypes._
import spray.routing.directives.CachingDirectives._
import scala.concurrent.duration.Duration
import spray.http.Uri._
import com.google.common.base.CharMatcher._
import scala.collection._
import mutable.ListBuffer
import spray.http.HttpHeaders.RawHeader
import com.google.common.util.concurrent.RateLimiter
import spray.routing.directives.BasicDirectives
import com.google.common.cache.{CacheLoader, CacheBuilder}
import java.util.concurrent.TimeUnit
import com.typesafe.scalalogging.slf4j.Logging

class DistinctEvictingList[A](maxSize: Int) extends Traversable[A] {
  private[this] val list: ListBuffer[A] = ListBuffer()

  def +=(elem: A): this.type = {
    if (!list.contains(elem)) {
      if (list.size == maxSize) {
        list.trimEnd(1)
      }
      list.prepend(elem)
    }
    this
  }

  def foreach[U](f: A => U) = list.foreach(f)
}

trait RateLimitDirectives extends BasicDirectives with Logging {
  val rateLimit: Int

  private[this] val rateLimiters = CacheBuilder.newBuilder().maximumSize(5000).expireAfterAccess(10, TimeUnit.MINUTES).build(
    new CacheLoader[RemoteAddress, RateLimiter] {
      def load(key: RemoteAddress) = {
        RateLimiter.create(rateLimit)
      }
    })

  def rateLimit(ip: RemoteAddress): Directive0 = {
    mapInnerRoute {
      inner =>
        ctx =>
          if (rateLimiters.get(ip).tryAcquire())
            inner(ctx.withHttpResponseHeadersMapped(headers => RawHeader("X-RateLimit-Limit", rateLimit.toString) :: headers))
          else {
            logger.warn(s"Rate limit of $rateLimit requests/minute exceeded by $ip, responding with status '429 Too Many Requests'")
            ctx.complete(TooManyRequests, s"You have exceeded your rate limit of $rateLimit requests/second.")
          }
    }
  }
}

object Main extends App with CapsuleCiscoService {
}

trait CapsuleCiscoService extends SimpleRoutingApp with RateLimitDirectives {
  val config = ConfigFactory.load

  val interface = config.getString("http.interface")
  val port = config.getInt("http.port")

  val title = config.getString("title")
  val hostname = config.getString("hostname")
  val rateLimit = config.getInt("ratelimit")

  val capsuleUri = Uri(s"${config.getString("capsulecrm.url")}/api/party")
  val capsuleToken = config.getString("capsulecrm.token")

  implicit val system = ActorSystem("capsule-cisco", config)
  sys.addShutdownHook(system.shutdown())
  implicit val executionContext = system.dispatcher

  val capsulePipeline: HttpRequest => Future[HttpResponse] = (
    addCredentials(BasicHttpCredentials(capsuleToken, ""))
    ~> addHeader("Accept", "application/json")
    ~> sendReceive)

  val lastSearches = mutable.Map[RemoteAddress, DistinctEvictingList[String]]()
  val cache = routeCache(maxCapacity = 5000, timeToIdle = Duration("10 min"))

  startServer(interface, port) {
    (get & compressResponseIfRequested() & respondWithMediaType(`text/xml`) & clientIP) { ip =>
      path("ping") {
        complete("<pong/>")
      } ~
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
          (rateLimit(ip) & alwaysCache(cache)) {
            parameters('q ?, 'tag ?, 'start.as[Int] ? 0) { (q, tag, start) =>
              validate(q.isDefined || tag.isDefined, errorMsg = "need a query or a tag to search (q/tag params)")
              validate(start >= 0, errorMsg = s"start parameter must be positive: $start")

              respondWithHeader(RawHeader("Refresh", s"0; url=http://$hostname/search.xml?q=${q.orElse(tag).get}&start=${start + 25}")) {
                ctx =>
                  val uri = q match {
                    case Some(query) =>
                      lastSearches += (ip -> (lastSearches.getOrElse(ip, new DistinctEvictingList[String](10)) += query))
                      capsuleUri.copy(query = Query("q" -> query, "start" -> start.toString, "limit" -> "25"))
                    case None => capsuleUri.copy(query = Query("tag" -> tag.getOrElse("")))
                  }

                  capsulePipeline(Get(uri)).onSuccess {
                    case response: HttpResponse =>
                      import spray.json.lenses.JsonLenses._
                      import DefaultJsonProtocol._

                      val json = JsonParser(response.entity.asString(HttpCharsets.`UTF-8`))

                      val organisations = 'parties / optionalField("organisation") / arrayOrSingletonAsArray
                      val persons = 'parties / optionalField("person") / arrayOrSingletonAsArray
                      val firstPhoneNumber = 'contacts / 'phone / arrayOrSingletonAsArray / element(0) / 'phoneNumber

                      val organisationArrayIds = organisations / filter(('contacts / 'phone).is[JsValue](_ => true)) / 'id

                      val personArrayIds = persons / filter(('contacts / 'phone).is[JsValue](_ => true)) / 'id

                      ctx.complete(OK,
                        """<?xml version="1.0" encoding="utf-8" ?>""" + is('\n').removeFrom(
                          <CiscoIPPhoneDirectory>
                            <Title>{ title }</Title>
                            <Prompt>{ title }</Prompt>
                            {
                              for (id <- json.extract[String](organisationArrayIds)) yield {
                                <DirectoryEntry>
                                  <Name>{ json.extract[String](organisations / filter('id.is[String](_ == id)) / 'name) }</Name>
                                  <Telephone>{ WHITESPACE.removeFrom(json.extract[String](organisations / filter('id.is[String](_ == id)) / firstPhoneNumber).head) }</Telephone>
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
                                        case Some(on) => s" at $on"
                                      }
                                    }
                                  </Name>
                                  <Telephone>{ WHITESPACE.removeFrom(json.extract[String](persons / filter('id.is[String](_ == id)) / firstPhoneNumber).head) }</Telephone>
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