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
public class Test3 implements java.io.Serializable {
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
  private static double test(int i) {
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

```diff
bcdiff Test3.class Test4.class
--- Test3.class
+++ Test4.class

- Name: Test3
+ Name: Test4
- Flags: PUBLIC, SUPER
+ Flags: SUPER
- Implemented interfaces: java/io/Serializable


@@ Method <init> // Signature: ()V
- Flags: PUBLIC
    0: aload 0
    1: invokespecial  // Method java/lang/Object.<init>:()V
    2: return

@@ Method test // Signature: (I)D
- Flags: PUBLIC
+ Flags: PRIVATE, STATIC
+   0: getstatic  // Field java/lang/System.out:Ljava/io/PrintStream;
+   1: iload 0
+   2: invokevirtual  // Method java/io/PrintStream.println:(I)V
-   0: iload 1
+   3: iload 0
    4: bipush 42
    5: if_icmpge 11:
+   6: iload 0
+   7: iconst_2
+   8: isub
-   3: iinc 1, 1
+   9: istore 0
   10: goto 3:
+  11: iload 0
-   5: iload 1
   12: iconst_2
   13: isub
   14: i2d
   15: dreturn
```

## Example with `--stat`

Running `bcdiff --stat` on the `scala.Enumeration` class file between Scala 2.9.2 and 2.10.1 gives:
![Result of bcdiff with stats](http://static.antoine.gourlay.fr/bcdiff/images/readme2.png)

# Installation

## Last release

The last (and only) release is [bcdiff 0.1 (tar.gz)](http://static.antoine.gourlay.fr/bcdiff/releases/bcdiff-0.1.tgz) ([md5](http://static.antoine.gourlay.fr/bcdiff/releases/bcdiff-0.1.tgz.md5)).

Download, extract and run `bin/bcdiff`.

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

bcdiff is quite experimental, and many things are expected to change (and improve, hopefully). It is also very manually tested; do not depend on it for anything serious.

Future versions may include, in no particular order:
 * proper testing. I do not feel like commiting loads of class files as test resources, so this will require compiling java/scala classes on the fly and matching the output to an expected (textual?) output,
 * a better diff algorithm. The current one is somewhat similar to Myers' greedy algorithm, implementing the linear refinement would be nice,
 * better printing for some specialized instructions (e.g. `tableswitch` and `lookupswitch`),
 * being able to select a particular method to diff, or only methods that match a pattern, etc. instead of the whole thing,
 * show added/removed fields,
 * maybe allow diffing directories (diff class files with the same names on both sides)?
 * maybe look into nailgun to get rid of that pesky JVM start overhead :-)

An idea, a suggestion, an issue? Please open an issue on GitHub or ring me on twitter ([@gourlaysama](https://twitter.com/gourlaysama)).

# License

bcdiff is open-source software licensed under the Simplified BSD License (see LICENSE file).
