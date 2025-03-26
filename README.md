# :mushroom: DEXPatch
Surgically inject a System.loadLibrary() into a dex.

The following program can be used to inject a `System.loadLibrary()` call into the `<clinit>` of the specified class in a COMPILED dex.
Thanks to [dexlib2](https://github.com/JesusFreke/smali/tree/master/dexlib2), that performs direct bytecode manipulation, this avoids decompilation/recompilation errors and preserves original obfuscation and optimizations.

Here is used to inject a `System.loadLibrary("frida-gadget")` call in a suitable place that typically is the static initializer of the main application Activity.
## Usage
```
java -jar dexpatch.jar input.dex output.dex com/ax/example/MainActivity
```
or
```
javac -cp dexlib2-2.5.2.jar:guava-33.0.0-jre.jar DEXPatch.java
java -cp .:dexlib2-2.5.2.jar:guava-33.0.0-jre.jar DEXPatch input.dex output.dex com/ax/example/MainActivity
```
## How to
You can run `build_fat_jar.sh` to build the `dexpatch.jar`.
## Requirements
- dexlib2-2.5.2.jar
- guava-33.0.0-jre.jar

## References
https://github.com/JesusFreke/smali/tree/master
