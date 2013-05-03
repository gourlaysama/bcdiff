Change history
==============

0.2
----

 - Now displays differencies of method access flags.

 - Diffing the content of methods can be disabled with a flag '--nomethods' (enabled by default).

 - Fix: the display of access flags were mixed up between class and method flags.

 - Better pretty printing for 'ldc', 'newarray', 'lookupswitch' and 'tableswitch'.

 - Fix: in some cases, label targets in jump instructions could referenced the wrong label.
   Now, when one label from fileA is considered equal to several from fileB, the first match wins
   (previously was the last)

0.1
----

 - Initial release
