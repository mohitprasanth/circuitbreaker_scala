package hystrixcb

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import com.netflix.hystrix.{HystrixCommand, HystrixCommandGroupKey}
import rx.Observable

import scala.concurrent.Future


object SimpleHyCB extends App {
  var cHys = new CustomHystrix[String](HystrixCommandGroupKey.Factory.asKey("ExampleGroup"),"Joveo!!")
  println(cHys.run()) //sync

  cHys = new CustomHystrix[String](HystrixCommandGroupKey.Factory.asKey("ExampleGroup"),"Joveo!!")
  println(cHys.queue()) //Async

  cHys = new CustomHystrix[String](HystrixCommandGroupKey.Factory.asKey("ExampleGroup"),"Joveo!!")
  println(cHys.execute()) //sync

  cHys = new CustomHystrix[String](HystrixCommandGroupKey.Factory.asKey("ExampleGroup"),"Joveo!!")
  val ob: Observable[String] = cHys.observe()
  ob.subscribe( x => println(x)) //async
}

class CustomHystrix[A](s: HystrixCommandGroupKey, name : A) extends HystrixCommand[A](s) {
  override def run():A = {
    name
  }
}