# bcdiff

bcdiff is a command-line JVM bytecode diff tool written in Scala.

bcdiff supports:

 * diffing the content of methods with the same name and signature between two class files,
 * statistics on the difference between two class files (`--stat` and `--shortstat`),
 * colored output (`--color` is on by default, `--nocolor` to disable),
 * and... that's all for now, but there is more to come.

# Quick example

Compiling these two files:

```java
class Test3 {
  public double test(int i) {
    while (i < 42) {
      i++;
    }
    return i - 2;
  }
}
```

```java
class Test4 {
  public double test(int i) {
    System.out.println(i);
    while (i < 42) {
      i = i - 2;
    }
    return i - 2;
  }
}
```

And diffing the resulting class files:

```sh
bcdiff Test3.class Test4.class
```

![Result of bcdiff](http://static.antoine.gourlay.fr/bcdiff/images/readme1.png)

## Example with `--stat`

Running `bcdiff --stat` on the `scala.Enumeration` class file between Scala 2.9.2 and 2.10.1 gives:
![Result of bcdiff with stats](http://static.antoine.gourlay.fr/bcdiff/images/readme2.png)

# Installation

## Latest version from source

Just compile and generate a launch script:

```sh
sbt compile
sbt start-script
```

The tool can then be run without sbt by running `target/start`.
For example:

```sh
chmod +x target/start
sudo ln -s `pwd`/target/start /usr/bin/bcdiff
```
And then use `bcdiff` anywhere.

# Notes

bcdiff is new and quite experimental, and many things are expected to change (to improve, hopefully). It is also very manually tested, do not depend on it for anything serious.

Future versions may include, in no particular order:
 * proper testing. I do not feel like commiting loads of class files as test resources, so this will require compiling java/scala classes on the fly and matching the output to an expected (textual?) output,
 * a better diff algorithm. The current one is somewhat similar to Myers' greedy algorithm, implementing the linear refinement would be nice,
 * better printing for some specialized instructions (e.g. `tableswitch` and `lookupswitch`),
 * being able to select a particular method to diff, or only methods that match a pattern, etc. instead of the whole thing,
 * show added/removed fields,
 * maybe allow diffing directories (diff class files with the same names on both sides)?
 * maybe look into nailgun to get rid of that pesky JVM start overhead :-)

An idea, a suggestion, an issue? Please open an issue on GitHub or ring me on twitter ([@gourlaysama](https://twitter.com/gourlaysama).

# License

bcdiff is open-source software licensed under the Simplified BSD License (see LICENSE file).
