package fpinscala.answers.streamingio
// import scala.language.implicitConversions
// import scala.language.postfixOps
import scala.language.higherKinds

import language.higherKinds

/* 
 * A context in which exceptions can be caught and
 * thrown. 
 */
trait Partial[F[_]] { 
  def attempt[A](a: F[A]): F[Either[Throwable,A]]
  def fail[A](t: Throwable): F[A]
}

