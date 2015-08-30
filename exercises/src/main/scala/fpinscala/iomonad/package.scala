package fpinscala

import scala.language.higherKinds
import scala.language.implicitConversions


package object iomonad {
  import fpinscala.parallelism.Nonblocking._

  type IO[A] = IO3.IO[A]
  def IO[A](a: => A): IO[A] = IO3.IO[A](a)

  type TailRec[A] = IO3.TailRec[A]
  object TailRec {
    def retrn[A](a: A): TailRec[A] = IO3.Return(a)
    //def suspend[A](func0A: Function0[A]): TailRec[A] = IO3.Suspend(func0A)
    //def flatMap[A,B](b: A => TailRec[B]): TailRec[B] =
    def suspend[A](tailRecA: => TailRec[A]): TailRec[A] =
      IO3.suspend(tailRecA)
    def flatMap[A,B](tailRecA: TailRec[A])(
      f: Function1[A,TailRec[B]]): TailRec[B] =
      IO3.FlatMap[Function0, A, B](tailRecA,f)


  }

  implicit val ioMonad = IO3.freeMonad[Par]

  def now[A](a: A): IO[A] = IO3.Return(a)

  def fork[A](a: => IO[A]): IO[A] = par(Par.lazyUnit(())) flatMap (_ => a)

  def forkUnit[A](a: => A): IO[A] = fork(now(a))

  def delay[A](a: => A): IO[A] = now(()) flatMap (_ => now(a))

  def par[A](a: Par[A]): IO[A] = IO3.Suspend(a)

  def async[A](cb: ((A => Unit) => Unit)): IO[A] =
    fork(par(Par.async(cb)))

  type Free[F[_], A] = IO3.Free[F, A]

  def Return[A](a: A): IO[A] = IO3.Return[Par,A](a)

  // To run an `IO`, we need an executor service.
  // The name we have chosen for this method, `unsafePerformIO`,
  // reflects that is is unsafe, i.e. that it has side effects,
  // and that it _performs_ the actual I/O.
  import java.util.concurrent.ExecutorService
  def unsafePerformIO[A](io: IO[A])(implicit E: ExecutorService): A =
    Par.run(E) { IO3.run(io)(IO3.parMonad) }
}
