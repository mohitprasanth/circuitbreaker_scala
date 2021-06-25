package hystrixcb

import akka.http.scaladsl.model.HttpResponse

object UrlTriggerTest extends App{

  for {i <- (1 to 1000)}{
    println(s"$i th request")
    val res: Option[HttpResponse] = UrlTrigger(i).execute()
    println(res.getOrElse(None))
    Thread.sleep(3000)
  }

}
