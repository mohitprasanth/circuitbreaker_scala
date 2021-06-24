package hystrixcb

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.netflix.hystrix.HystrixCommand.Setter
import com.netflix.hystrix.{HystrixCommand, HystrixCommandGroupKey, HystrixCommandProperties}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object UrlTrigger {
  val hystrixConfig: Setter = Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("ScrapeUrlGroup"))
    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
      .withCircuitBreakerSleepWindowInMilliseconds(30000) //how much time CB need to sleep before triggering actual endpoint
      .withCircuitBreakerRequestVolumeThreshold(5) //minimum how many requests must present in RollingStatisticalWindow
      .withCircuitBreakerErrorThresholdPercentage(50) //minimum how much % of requests must fail (say error or timeout) in RollingStatisticalWindow
      .withMetricsRollingStatisticalWindowInMilliseconds(30000) ///very very very important ()
      .withExecutionTimeoutInMilliseconds(2000) //timeout of each execution in RollingStatisticalWindow
      .withRequestCacheEnabled(true)
    )

  implicit val actorRef = ActorSystem("UrlTriggerSystem")

  def apply(reqId: Int): UrlTrigger = new UrlTrigger(reqId)
}

class UrlTrigger(private val reqId : Int) extends HystrixCommand[Option[HttpResponse]](UrlTrigger.hystrixConfig) {
  import UrlTrigger._
  override def run(): Option[HttpResponse] = {
    val res = Option(Await.result(Http().singleRequest(HttpRequest(uri = s"http://localhost:5000/$reqId")),60.seconds))

    //EVEN THESE ERROR ARE COUNTED AS FAILURES
    //    if(reqId>5)
    //      throw new RuntimeException("break it intentionally")

    res
  }

  //override def getCacheKey: String = s"http://localhost:5000/$reqId"
  //How to flush cache ?
  //shutdown and reinitialize HystrixRequestContext with some life cycle method such as filters or such to reInit cache.
  //https://stackoverflow.com/questions/27117065/hystrix-request-caching-by-example

  override def getFallback: Option[HttpResponse] = None
}
