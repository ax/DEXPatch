#!/bin/bash
wget https://repo1.maven.org/maven2/org/smali/dexlib2/2.5.2/dexlib2-2.5.2.jar
wget https://repo1.maven.org/maven2/com/google/guava/guava/33.0.0-jre/guava-33.0.0-jre.jar
javac -cp dexlib2-2.5.2.jar:guava-33.0.0-jre.jar DEXPatch.java
echo "Run with:"
echo "java -cp .:dexlib2-2.5.2.jar:guava-33.0.0-jre.jar DEXPatch target.dex classes.dex com/ax/example/MainActivity"
