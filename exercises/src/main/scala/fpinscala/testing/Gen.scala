package fpinscala.testing

import fpinscala.laziness.Stream
import fpinscala.state._
import fpinscala.parallelism._
import fpinscala.parallelism.Par.Par
import Gen._
import Prop._
import java.util.concurrent.{Executors,ExecutorService}

/*
The library developed in this chapter goes through several iterations. This file is just the
shell, which you can fill in and modify while working through the chapter.
*/

trait Prop {
  // def check: Boolean
  // def &&(p: Prop): Prop = new Prop {
  //   def check: Boolean = this.check && p.check
  // }
  // cannot define 'check' method like this
  //Prop(this.check && p.check)
  def check: Either[(FailedCase, SuccessCount), SuccessCount]
  // def &&(p: Prop): Prop = new Prop {
  //   def check: Either[(FailedCase, SuccessCount), SuccessCount] =
  //     (this.check, p.check) match {
  //       case Left((failedCaseString, successCountInt)) => 
  //       case Right((
  //       //case (scala.util.Either$RightProjection(sc)


}

object Prop {
  // makes Either to be constructed interpretable by its type signature
  // self-commenting code
  type SuccessCount = Int
  type FailedCase = String

  def forAll[A](gen: Gen[A])(f: A => Boolean): Prop = ???
}

/*
 case class State[S,A](run: S => (AS))
 */
case class Gen[A](sample: State[RNG, A])

object Gen {
  //import fpinscala.testing.Gen
  def unit[A](a: => A): Gen[A] = new Gen(State.unit(a))
  // def boolean: Gen[Boolean] 

  // do it with flatMap
  def listOfN[A](n: Int, g: Gen[A]): Gen[List[A]] = {
    if(n>0){

  }
  def _listOfN[A](n: Int, g: Gen[A]): Gen[List[A]] = {
    val listState = List.fill(n)(g.sample)
    Gen(State.sequence(listState))
  }
  def stringOfLength(n: Int): Gen[String] = {
    val genAsciiInt = choose(0x21, 0x7a)
    val genListInt: Gen[List[Int]] = listOfN(n, genAsciiInt)
    genListInt.map((li: List[Int]) => li.toString())
  }

  // generates one integer
  def choose(start: Int, stopExclusive: Int): Gen[Int] = {
    // use Simple RNG
    val gen = Gen(State.Rand[Int])


  }
}

trait Gen[A] {
  def map[B](f: A => B): Gen[B] = {
    val newState: State[RNG, B] = this.sample.map(f)
    Gen[B](newState)
  }
  def flatMap[B](f: A => Gen[B]): Gen[B] = {
    val newGen = this.sample.flatMap(f)

    newGen
  }


}

trait SGen[+A] {

}

