package edu.luc.cs.metrics.defect.density.service

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Properties

object Boot extends App with MySSLConfig/*with CommitDensityService*/{

  implicit val system = ActorSystem("gitCommitDensity")
  val service = system.actorOf(Props[MyServiceActor],"git-file-commit-density")
  implicit val timeout = Timeout(1 hour)
  val port = Properties.envOrElse("PORT","8080").toInt
  IO(Http) ? Http.Bind(service,interface = "0.0.0.0", port = port)
  /*println("\nEnter username/reponame/branchname")
  val lines = scala.io.Source.stdin.getLines
  val input = lines.next().split("/")
  import scala.concurrent.ExecutionContext.Implicits._
  println(s"You entered: \nUsername: ${input(0)} \nReponame: ${input(1)} \nBranchname: ${input(2)}\n")
  val f = dataForDensityMetrics(input(0), input(1), input(2),"month")
  f.onComplete{
    case scala.util.Success(v) => println("And Done"+v)
    case scala.util.Failure(v)=> println("FAIL")
      v.printStackTrace()
  }
  Await.result(f,1 hour)*/
}