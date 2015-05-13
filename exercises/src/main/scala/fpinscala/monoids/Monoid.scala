package fpinscala.monoids

import fpinscala.parallelism.Nonblocking._
import fpinscala.parallelism.Nonblocking.Par.toParOps // infix syntax for `Par.map`, `Par.flatMap`, etc

import scala.language.higherKinds
import scala.language.implicitConversions

trait Monoid[A] {
  def op(a1: A, a2: A): A
  def zero: A
}

object Monoid {

  val stringMonoid = new Monoid[String] {
    def op(a1: String, a2: String) = a1 + a2
    val zero = ""
  }

  def listMonoid[B] = new Monoid[List[B]] {
    def op(a1: List[B], a2: List[B]) = a1 ++ a2
    val zero = Nil
  }

  def indexedSeqMonoid[B] = new Monoid[IndexedSeq[B]] {
    def op(a1: IndexedSeq[B], a2: IndexedSeq[B]) = a1 ++ a2
    val zero = IndexedSeq[B]()
  }


  val intAddition: Monoid[Int] = new Monoid[Int] {
    def op(a1: Int, a2: Int): Int = a1 + a2
    val zero: Int = 0
  }

  val intMultiplication: Monoid[Int] = new Monoid[Int] {
    def op(a1: Int, a2: Int): Int = a1 * a2
    val zero: Int = 1
  }

  val booleanOr: Monoid[Boolean] = new Monoid[Boolean] {
    def op(a1: Boolean, a2: Boolean): Boolean = a1 | a2
    val zero: Boolean = false
  }

  val booleanAnd: Monoid[Boolean] = new Monoid[Boolean] {
    def op(a1: Boolean, a2: Boolean): Boolean = a1 & a2
    val zero: Boolean = true
  }

  /*
   scala.Option, not fpinscala...option
   
   Combine two options of unknown type with Flatmap, or Map...
   Each Option must define these methods
   */

  def optionMonoid[B]: Monoid[Option[B]] = new Monoid[Option[B]] {
    def op(a1: Option[B], a2: Option[B]): Option[B] = {
      //a1.flatMap(a2)
      a1.orElse(a2)
    }
    val zero: Option[B] = None
  }

  def endoMonoid[B]: Monoid[B => B] = new Monoid[B => B] {
    // are a1 and a2 necessarily associative?

    /* note that monoids have no commutative property.
     Order of applications of b1 and b0 matters.
     */
    def op(b0: B => B, b1: B => B): B => B = (in: B) => b1(b0(in))

    // need type signature () => B
    //def zero: B =
    // not true ... A = (B => B)

    def zero: B => B = (in: B) => in
  }

  // def functionMonoid[A,B]: Monoid[A=>B] = new Monoid[A=>B] {
  //   def op(f0: A=>B, f1: A=>B): A=>B = (a: A) => {
  //   def zero: A => B = (a: A) => ????
  //   }



  import fpinscala.testing._
  import Prop._
  import Gen._
  def monoidLaws[A](m: Monoid[A], gen: Gen[A]): Prop = {
    val la0: Gen[List[A]] = Gen.listOfN(3, gen)
    val associative: Prop = Prop.forAll(la0){
      (la: List[A]) => {
        val a0 = la(0)
        val a1 = la(1)
        val a2 = la(2)
        val left = m.op(a0, m.op(a1, a2))
        val right = m.op(m.op(a0, a1), a2)
        left == right
      }
    }

    val rightIdentity: Prop = Prop.forAll(gen){
      (a: A) => {
        // shouldn't be necessary to test left because
        // associativity is tested above
        val right = m.op(a, m.zero)
        right == a
      }
    }

    val bothProperties: Prop = associative.&&(rightIdentity)

    bothProperties
  }


  //def trimMonoid(s: String): Monoid[String] = sys.error("todo")

  def concatenate[A](as: List[A], m: Monoid[A]): A = {
    as.foldLeft(m.zero)(m.op)
  }
  /*
   But what if our list has an element type that doesn’t have a Monoid instance? Well, we can always map over the list to turn it into a type that does:
   */
  def foldMap[A, B](as: List[A], m: Monoid[B])(f: A => B): B = {
    /*
     Monoid[B] {
     def op(b1: B, b2: B): B
     def zero: B
     }
     */
    val lb = as.map(f)
    
    lb.foldLeft(m.zero)(m.op)
  }

  // foldMap with no dependency on other fold implementations
  def _foldMap[A, B](as: List[A], m: Monoid[B])(f: A => B): B = {
    this._foldMapZ(as, m)(f)(m.zero)
  }
  // overloading not allowed in Scala because of potential
  // for currying.  Ambiguous whether currying or overloading.
  def _foldMapZ[A, B](as: List[A], m: Monoid[B])(f: A => B)(z: B): B = {
    // I think this is essentially an implementation of fold right...
    // as match {
    //   case Nil => m.zero
    //   case (a :: Nil) => {
    //     val b = f(a)
    //     b
    //   }
    //   case (a :: tail) => {
    //     val b = f(a)
    //     val b2 = m.op(b, _foldMap(tail, m)(f))
    //     b2
    //   }
    // }
    
    /*
     No issue with using List's built-in methods, as in the correct
     answer for par fold map.
     The pattern-matching solution above used List's built-in
     unapply method, so even that solution was dependent.
     */

    val bs: List[B] = as.map(f)
    // foldMapV assumes efficient indexing of sequence/vector
    // A List does not have efficient indexing.
    // So just implement this as a fold right
    //val b: B = foldMapV(bs, m)((b: B)=>b)

    @annotation.tailrec
    def aggregator(la: List[B], z: B): B = {
      /*
       Review fold left and fold right implementations in 
       fpinscala.datastructures.List, and
       why one is tail recursive and the other isn't
       */
      la match {
        case ((h: B):: Nil) => m.op(h, z)
        case ((h: B)::(t: List[B])) => aggregator(t, m.op(z, h))
      }
    }

    aggregator(bs, z)

  }


  /*
   Implemented by _foldMap; no circular dependency on other
   fold implementations.

   Assume that the below implementations of foldRight and 
   foldLeft do not have access to the list monoid --
   only _foldMap does.

   Where did this assumption come from?...
   */


  //lb.foldLeft(m.zero)(m.op)
  // def fold(list: List[B])(combiner: (B, List[B]) => B): B =
  //   list match {
  //     case Nil => m.zero
  //     case (head::Nil) =>
  //     case (head::tail) =>
  // }


  /*

   _foldMap[A, B](as: List[A], m: Monoid[B])(f: A => B): B

   foldRight[C, D](as: List[C])(z: D)(f: (C, D) => D): D

   B == D
   A == C

   need monoid for D
   */
  def foldRight[A, B](as: List[A])(z: B)(f: (A, B) => B): B = {
    // {(A, B) => B} => {A => B => B}
    //val g: A =>(B => B) = (a: A) => ((b: B) => f(a,b))
    // trait
    // scala.Function1[A, Function1[B, B]]
    val g: A => (B => B) = f.curried
    // Thought that it was incorrect to hide type A => B => B in
    // type A => B...
    _foldMapZ(as, Monoid.endoMonoid)(g)(z)
  }


  def foldLeft[A, B](as: List[A])(z: B)(f: (B, A) => B): B = {
    // val bMonoid = new Monoid[List[B]] {
    //   def op(b0: B, b1: B)= b0 ++ b1
    //   def zero = Nil
    // }
    // _foldMap(as, bMonoid){
    //   // need A => B
    //   (a: A) => f(bMonoid.zero, a)
    // }
    val g: B => (A => B) = f.curried
    _foldMapZ(as, Monoid.endoMonoid)(g)(z)
  }

  /*
   Reduces the number of strings allocated and deallocated.
   Clearly not parallelized, though.

   In some cases, this function will run out of memory
   where foldLeft would not.  foldLeft is tail recursive.

   Old question answered: even a tail-recursive function
   can run out of memory.  For example,
   the output of the fold could be enormous.
   */
  def foldMapV[A, B](as: IndexedSeq[A], m: Monoid[B])(f: A => B): B = {
    val length = as.length
    val b = if(length==1) f(as.head)
    else {
      val approxHalf = length/2
      // right bound of slice is exclusive
      val leftRecursion: B = foldMapV(
        as.slice(0, approxHalf), m)(f)
      val rightRecursion: B = foldMapV(
        as.slice(approxHalf, length), m)(f)

      m.op(leftRecursion, rightRecursion)
    }
    b
  }: B

  // hint hint...
  import fpinscala.parallelism.Nonblocking._



  // Implement only for checking ordering of IndexedSeq[Int].
  // Could get more complicated to leave comparison abstract and check
  // ordering for IndexedSeq of any type

  /*
   While order checking will be associative, can be done in any order,
   that is not to say that the input IndexedSeq will be "scrambled".
   First and Following
   */

  def orderedIntMonoid =
    new Monoid[(Int, Int, Boolean)] {
      def op(
        first: (Int, Int, Boolean),
        following: (Int, Int, Boolean)
      ): (Int, Int, Boolean) = {
        val (min0, max0, ordered0) = first
        val (min1, max1, ordered1) = following

        //if ((ordered0 || ordered1) == false):
        val min2 = min0
        val max2 = max1

        val ordered2: Boolean =
          //if ((ordered0 || ordered1)==false) false
          if (ordered0 && ordered1 && (max0 < min1)) true
          else if (max0 > min1) false
          else true

        (min2, max2, ordered2)
      }

      /*
       May not be an identity for (Int, Int, Boolean)

       The only requirement is that 
       op(X, zero)=X, op(zero, X)=X, and op(zero, zero) = zero
       
       So there is nothing wrong with using "short circuits"
       to enforce this Identity.
       */
      def zero: (Int, Int, Boolean) = (0,0,true)
    }


  /*
   Two Par implementations
   fpinscala.parallelism.Par
   fpinscala.parallelism.Nonblocking.Par

   Not an implementation of Par
   fpinscala.parallelism.Actor
   */

  def ordered(ints: IndexedSeq[Int]): Boolean = {
    val aggregation = foldMapV(ints, orderedIntMonoid){
      // Int => (Int, Int, Boolean)
      (i: Int) => (i, i, true)
    }
    aggregation._3
  }


  sealed trait WC
  case class Stub(chars: String) extends WC
  case class Part(lStub: String, words: Int, rStub: String) extends WC

  def par[A](m: Monoid[A]): Monoid[Par[A]] = new Monoid[Par[A]] {
    //  type Par[A] = ExecutorService => Future[A]
    def op(par1: Par[A], par2: Par[A]): Par[A] = {
      // This does too much... runs the two Pars
      // (es: java.util.concurrent.ExecutorService) => {
      //   // remember you have an op to combine two A values
      //   val par3 = Par.map2(par1, par2)(m.op): Par[A]
      //   par3.run(es)
      // }
      Par.map2(par1, par2)(m.op): Par[A]
    }
    def zero: Par[A] = Par.unit(m.zero)
  }

  // split down the middle and merge
  def _parFoldMap[A,B](v: IndexedSeq[A], m: Monoid[B])(f: A => B):
      Par[B] =
    if(v.length==1){
      val head: A = v.head   // I don't know how this is typesafe
                             // for an empty Seq
      val b: B = f(head)
      val parB: Par[B] = Par.delay(b)

      parB
    } else {
      val middle = v.length / 2
      val (leftSeq, rightSeq):
          Tuple2[IndexedSeq[A], IndexedSeq[A]] =
        v.splitAt(middle)

      val parLeft: Par[B] = parFoldMap(leftSeq, m)(f)
      val parRight: Par[B] = parFoldMap(rightSeq, m)(f)

      val parMerged: Par[B] = Par.map2(parLeft, parRight)(m.op)

      parMerged
    }

  // improved
  def parFoldMap[A,B](v: IndexedSeq[A], m: Monoid[B])(f: A => B):
      Par[B] = {
    /*
     Think of this as
     Par[IndexedSeq[A]] => Par[B]
     */
    //val parSeqA: Par[IndexedSeq[A]] = Par.delay(v)
    // Par.flatMap(parSeqA){
    //   // don't use IndexedSeq's map or flatMap emethods
    //   (seqA: IndexedSeq[A]) => {
    implicit def indexedSeqToList(is: IndexedSeq[A]): List[A] = is.toList

    val parListB: Par[List[B]] = Par.parMap(v)(f)
    // Par[List[B]] => Par[B]
    // reduce in parallel
    val parB: Par[B] = Par.map(parListB){
      (listB: List[B]) => listB.foldLeft(m.zero)(m.op)
    }

    parB


  }
  /*
   we perform the mapping and the reducing both in parallel

   def parFoldMap[A,B](v: IndexedSeq[A], m: Monoid[B])(f: A => B): Par[B] =
   Par.parMap(v)(f).flatMap { bs =>
   note that foldMapV is not a parallelized function, but
   is parallelized by flatMap above.

   A single reduction cannot be parallelized, but multiple reductions
   can happen at the same time.

   val parB: Par[B] = foldMapV(bs, par(m))(b => Par.lazyUnit(b))

   parB
   }

   */




  val wcMonoid: Monoid[WC] = new Monoid[WC] {
    def op(wc0: WC, wc1: WC): WC = {

      //def (stub: Stub

      (wc0, wc1) match {
        case (Stub(chars0), Stub(chars1)) => {
          //Part(chars0, 0, chars1)
          Stub(chars0 + chars1)
        }
        case (Stub(chars0),
          Part(lStub1, words1, rStub1)) =>
          Part(chars0+lStub1, words1, rStub1)
        case (Part(lStub0, words0, rStub0),
          Stub(chars1)) =>
          Part(lStub0, words0, rStub0+chars1)
        case (Part(lStub0, words0, rStub0),
          Part(lStub1, words1, rStub1)) => {
          // increment count and discard middle
          Part(lStub0, words0+words1+1, rStub1)
        }
      }
    }
    def zero: WC = Stub("")
  }

  def count(s: String): Int = {
    val as = s.toCharArray()
    val ss = as.toIndexedSeq

    // map and reduce:
    // map each character to a WC

    val sWc: IndexedSeq[WC] = ss.map((c: Char) => Stub(c.toString))
    // then reduce the WC
    // IndexedSeq[WC] => Int

  }

  def productMonoid[A,B](A: Monoid[A], B: Monoid[B]): Monoid[(A, B)] =
    new Monoid[(A, B)] {
      def op(ab0: (A, B), ab1: (A, B)): (A, B) = {
        val a0 = ab0._1
        val a1 = ab1._1
        val a2 = A.op(a0, a1)
        val b0 = ab0._2
        val b1 = ab1._2
        val b2 = B.op(b0, b1)
        (a2, b2)
      }
      def zero: (A, B) = (A.zero, B.zero)
    }
    
  

  def functionMonoid[A,B](B: Monoid[B]): Monoid[A => B] =
    new Monoid[A => B] {
      def op(f0: A=>B, f1: A=>B): A=>B = (a: A) => {
        val b0 = f0(a)
        val b1 = f1(a)
        val b2 = B.op(b0, b1)
        b2
      }
      def zero: A => B = (a: A) => B.zero    // ???
    }


  // def mapMergeMonoid[K,V](V: Monoid[V]): Monoid[Map[K, V]] =
  //   sys.error("todo")

  // def bag[A](as: IndexedSeq[A]): Map[A, Int] =
  //   sys.error("todo")
}

object MonoidTest {
  import fpinscala.monoids.Monoid._


  def main(args: Array[String]): Unit = {
    /*
     Use int addition monoid and par monoid
     */
    val nums = (1 to 100).toList
    val sq = nums.toIndexedSeq
    //val sq = IndexedSeq(1 to 100)
    println("nums 1 to 100")
    // val sum1: Int = foldMap(nums, intAddition)((i: Int) => i)
    // println("sum with fold map: "+sum1)

    val ordered = Monoid.ordered(sq)


  }
}
trait Foldable[F[_]] {
  import Monoid._

  def foldRight[A, B](as: F[A])(z: B)(f: (A, B) => B): B

  def foldLeft[A, B](as: F[A])(z: B)(f: (B, A) => B): B

  def foldMap[A, B](as: F[A])(f: A => B)(mb: Monoid[B]): B =
    foldLeft(as)(mb.zero){
      (b: B, a: A) => {
        val b2: B = f(a)
        mb.op(b, b2): B
      }: B
    }: B

  def concatenate[A](as: F[A])(m: Monoid[A]): A =
    foldMap(as)((a: A) => a)(m)

  def toList[A](as: F[A]): List[A] = {
    //type mismatch;
    // found   : fpinscala.monoids.Monoid[List[A]]
    // {val zero: scala.collection.immutable.Nil.type}
    // required: fpinscala.monoids.Monoid[A]

    //foldMap(as)((a: A) => a)(Monoid.listMonoid[A])
    foldLeft(as)(List[A]()){
      // (A, B) => B
      (la: List[A], a: A) => a :: la
    }
  }
}

object ListFoldable extends Foldable[List] {
  override def foldRight[A, B](as: List[A])(z: B)(f: (A, B) => B): B =
    as match {
      case (h: A) :: Nil => f(h, z)
      case (h: A) :: (t: List[A]) => f(h, foldRight(t)(z)(f))
    }

  @annotation.tailrec
  def foldLeft[A, B](as: List[A])(z: B)(f: (B, A) => B) =
    as match {
      case (h: A) :: Nil => f(z, h)
      case (h: A) :: (t: List[A]) => foldLeft(t)(f(z, h))(f)
    }

  def foldMap[A, B](as: List[A])(f: A => B)(mb: Monoid[B]): B =
    foldLeft(as)(mb.zero){
      (b: B, a: A) => mb.op(b, f(a)): B
    }: B
}

object IndexedSeqFoldable extends Foldable[IndexedSeq] {
  // override def foldRight[A, B](as: IndexedSeq[A])(z: B)(f: (A, B) => B): B =
    
  // override def foldLeft[A, B](as: IndexedSeq[A])(z: B)(f: (B, A) => B) = {
  //   val bMonoid = new Monoid[B] {
  //     def op(b1: B, b2: B): B = 
  //   foldMap(as)


  // override def foldMap[A, B](as: IndexedSeq[A])(f: A => B)(mb: Monoid[B]): B =
  //   Monoid.foldMapV(as, mb)(f)


}

object StreamFoldable extends Foldable[Stream] {
  // override def foldRight[A, B](as: Stream[A])(z: B)(f: (A, B) => B) =
  //   sys.error("todo")
  // override def foldLeft[A, B](as: Stream[A])(z: B)(f: (B, A) => B) =
  //   sys.error("todo")
}

sealed trait Tree[+A]
case class Leaf[A](value: A) extends Tree[A]
case class Branch[A](left: Tree[A], right: Tree[A]) extends Tree[A]

object TreeFoldable extends Foldable[Tree] {
  // override def foldMap[A, B](as: Tree[A])(f: A => B)(mb: Monoid[B]): B =
  //   sys.error("todo")


  override def foldLeft[A, B](as: Tree[A])(z: B)(f: (B, A) => B): B =
    as match {
      case Leaf(value: A) => f(z, value)
      case Branch(left: Tree[A], right: Tree[A]) => {
        val leftB: B = foldLeft(left)(z)(f)
        val rightB: B = foldLeft(right)(leftB)(f)
        rightB
      }
    }
  // override def foldRight[A, B](as: Tree[A])(z: B)(f: (A, B) => B) =
  //   as match {
  //     case Leaf(value: A) => f
}

object OptionFoldable extends Foldable[Option] {
  // override def foldMap[A, B](as: Option[A])(f: A => B)(mb: Monoid[B]): B =
  //   sys.error("todo")
  // override def foldLeft[A, B](as: Option[A])(z: B)(f: (B, A) => B) =
  //   sys.error("todo")
  // override def foldRight[A, B](as: Option[A])(z: B)(f: (A, B) => B) =
  //   sys.error("todo")
}

