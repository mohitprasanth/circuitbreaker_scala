package resilience4j

import akka.http.scaladsl.model.HttpResponse

object ResilienceCBTest extends App {
  for {i <- (1 to 1000)}{
    println(s"$i th request")
    val res: Option[HttpResponse] = new ResilienceCB(i).getResponse()
    println(res.getOrElse(None))
    Thread.sleep(3000)
  }
}
