package fpinscala.state


trait RNG {
  def nextInt: (Int, RNG) // Should generate a random `Int`. We'll later define other functions in terms of `nextInt`.
}

object RNG {
  // NB - this was called SimpleRNG in the book text

  case class Simple(seed: Long) extends RNG {
    def nextInt: (Int, RNG) = {
      val newSeed = (seed * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL // `&` is bitwise AND. We use the current seed to generate a new seed.
      val nextRNG = Simple(newSeed) // The next state, which is an `RNG` instance created from the new seed.
      val n = (newSeed >>> 16).toInt // `>>>` is right binary shift with zero fill. The value `n` is our new pseudo-random integer.
      (n, nextRNG) // The return value is a tuple containing both a pseudo-random integer and the next `RNG` state.
    }
  }

  type Rand[+A] = RNG => (A, RNG)

  // uses type inference to guess argument to nextInt -- an RNG
  val int: Rand[Int] = _.nextInt

  // explicit
  def randInt: Rand[Int] = (rng: RNG) => rng.nextInt

  def unit[A](a: A): Rand[A] =
    rng => (a, rng)

  def map[A,B](s: Rand[A])(f: A => B): Rand[B] =
    rng => {
      val (a, rng2) = s(rng)
      (f(a), rng2)
    }

  def nonNegativeInt(rng: RNG): (Int, RNG) = {
    val (nextInt, nextRNG) = rng.nextInt
    (nextInt.abs, nextRNG)
  }
                                             //RNG.map(rng)(_.abs)

  def double(rng: RNG): (Double, RNG) = {
    val (nextInt, nextRNG) = rng.nextInt
    val nextDouble = nextInt.doubleValue/Int.MaxValue
    (nextDouble, nextRNG)
  }
  def randDouble: Rand[Double] = (rng: RNG) => double(rng)

  def intDouble(rng: RNG): ((Int,Double), RNG) = {
    // val (nextInt, secondRNG) = rng.nextInt
    // val (nextDouble, thirdRNG) = RNG.double(secondRNG)
    // ((nextInt, nextDouble), thirdRNG)
    val intDoubleRand: Rand[(Int, Double)] = 
      RNG.map2(
        (rngA: RNG) => rngA.nextInt, 
        (rngB: RNG) => RNG.double(rngB)
      )((int: Int, dbl: Double) => (int, dbl))

    intDoubleRand(rng)
  }

  def doubleInt(rng: RNG): ((Double,Int), RNG) = {
    val (nextDouble, secondRNG) = RNG.double(rng)
    val (nextInt, thirdRNG) = secondRNG.nextInt
    ((nextDouble, nextInt), thirdRNG)
  }

  def double3(rng: RNG): ((Double,Double,Double), RNG) = {
    /*
     It would be nice to figure out this solution with flatMap,
     fold, etc.
     */
    // for(i<-1:3;
    //   val (nextDouble, nextRNG){

    // }
    val ll = List.fill(3)(randDouble)

    ???
    
  }

  def ints(count: Int)(rng: RNG): (List[Int], RNG) = {
    val ll = List.fill(count)(randInt)

    // ll.foldRight(z: B)(op: Function2[Function1[Int], B, B])

    ll.foldRight((List[Int](), rng))(
      (nextRand: Rand[Int], l2: (List[Int], RNG)) => {
        val (nextInt, nextRNG) = nextRand(l2._2)
        (nextInt :: l2._1, nextRNG)
      }
    )
  }
  def list[A](count: Int)(rng: RNG)(rand: Rand[A]): (List[A], RNG) = {
    val ll = List.fill(count)(rand)
    ll.foldRight((List[A](), rng))(

// [error] Note: Tuples cannot be directly destructured in method or function parameters.
// [error]       Either create a single parameter accepting the Tuple1,
// [error]       or consider a pattern matching anonymous function: `{ case (param1, param1) => ... }
// [error]       (nextRand: Rand[A], (prevAList, prevRNG): (List[A], RNG)) => {
// [error]                                               ^


//       (nextRand: Rand[A], (prevAList, prevRNG): (List[A], RNG)) => {
//         val (nextA, nextRNG) = nextRand(prevRNG)
//         (nextA :: prevAList, nextRNG)
//       }

      (nextRand: Rand[A], l2: (List[A], RNG)) => {
        val (nextA, nextRNG) = nextRand(l2._2)
        (nextA :: l2._1, nextRNG)
      }

    )
  }

  def map2[A,B,C](ra: Rand[A], rb: Rand[B])(f: (A, B) => C): Rand[C] = 
  {
    // Return function RNG => (C, RNG)
    (rng: RNG) => {
      val (raValue, raRNG) = ra(rng)
      val (rbValue, rbRNG) = rb(raRNG)
      (f(raValue, rbValue), rbRNG)
    }

  }

  def both[A,B](ra: Rand[A], rb: Rand[B]): Rand[(A,B)] = 
    RNG.map2(ra,rb){
      (a: A, b: B) => (a,b)
    }

  def randIntDouble: Rand[(Int, Double)] = 
    RNG.both(RNG.randInt, RNG.randDouble)

  def randIntDoubleEquivalent: Rand[(Int, Double)] = 
    RNG.both(RNG.randInt, RNG.double)

  def randIntDoubleEquivalent2: Rand[(Int, Double)] = 
    RNG.both(RNG.randInt, RNG.double(_))


  def randDoubleInt: Rand[(Double, Int)] = 
    RNG.both(RNG.randDouble, RNG.randInt)


  /*
   List[Rand[A]]
   Cons(Rand[A], Cons(Rand[A], Cons(Rand[A], Nil)))
   Cons((rng1)=>(a1,rng2), Cons(rng2=>(a2,rng3), Cons(rng3=>(a3,rng4), Nil)))

   
   Rand[List[A]]
   (rng1) => (Cons(a1,Cons(a2,Cons(a3,Nil))), rng4)

   sequence
   map2(


   Remember List has all the built-in methods that Rand lacks:
   fold
   flatMap
   etc.
   */

  def sequence[A](fs: List[Rand[A]]): Rand[List[A]] = {
    // fs.foldRight(z: B)(op: Function2[Function1[A], B, B])
    // fs.fold(z: A1)(op: Function2[A1, A1, A1])
    // fs.foldLeft(z: B)(f: Function2[B, Function1[A], B])
    
    fs.foldRight{
      RNG.unit(scala.collection.immutable.Nil): Rand[List[A]]
    }{
      (ra: Rand[A], rr: Rand[List[A]]) => {
        RNG.map2(ra, rr){(a: A, la: List[A]) =>
          a :: la
        }
      }
    }
  }

  def flatMap[A,B](f: Rand[A])(g: A => Rand[B]): Rand[B] = {

    // difference between
    // (g: A) => Rand[B]
    // and
    // g: A => Rand[B]

    // Rand[A] = RNG => (A, RNG)
    // Rand[B] = RNG => (B, RNG)
    // g = A => (RNG => (A, RNG))
    // g = (a: A) => ((rng: RNG) => (a, rng))

    (rng: RNG) => {
      val (valueA, rngA) = f(rng)
      val randB = g(valueA)
      randB(rngA)
    }
  }

  def mapWithFlatMap[A,B](s: Rand[A])(f: A => B): Rand[B] = {
    //def flatMap[A,B](f: Rand[A])(g: A => Rand[B]): Rand[B]

    // this is 'unit'
    //val g: (A => Rand[B]) = (a: A) => ((rng: RNG) => (f(a), rng))
    val g: (A => Rand[B]) = (a: A) => RNG.unit(f(a))
    flatMap(s)(g): Rand[B]

  }
}

case class State[S,+A](run: S => (A, S)) {
  def map[B](f: A => B): State[S, B] = {
    State {
      (s0: S) => {
        val (a, s1) = run(s0)
        (f(a), s1)
      }
    }

  }
    
  def map2[B,C](sb: State[S, B])(f: (A, B) => C): State[S, C] = {
    State {
      (s0: S) => {
        val (a, s1) = this.run(s0)
        val (b, s2) = sb.run(s1)
        (f(a,b), s2)
      }
    }
  }

  def flatMap[B](f: A => State[S, B]): State[S, B] = {
// [error]  found   : S => fpinscala.state.State[S,B]
// [error]  required: fpinscala.state.State[S,B]

    State {
      (s0: S) => {
        val (a, s1) = run(s0)
        f(a).run(s1)
      }
    }
  }

}

sealed trait Input
case object Coin extends Input
case object Turn extends Input

case class Machine(locked: Boolean, candies: Int, coins: Int)

object State {
  def unit[S,A](a: A): State[S,A] = 
    State {
      (s: S) => (a, s)
    }

  type Rand[A] = State[RNG, A]
  def simulateMachine(inputs: List[Input]): State[Machine, (Int, Int)] = ???
}
