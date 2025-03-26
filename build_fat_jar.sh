#!/bin/bash
set -e
wget https://repo1.maven.org/maven2/org/smali/dexlib2/2.5.2/dexlib2-2.5.2.jar
wget https://repo1.maven.org/maven2/com/google/guava/guava/33.0.0-jre/guava-33.0.0-jre.jar
cat > Manifest.txt <<EOF
Manifest-Version: 1.0
Main-Class: DEXPatch

EOF
mkdir -p tmp
(
  cd tmp
  jar xf ../dexlib2-2.5.2.jar
  jar xf ../guava-33.0.0-jre.jar
)
javac -cp dexlib2-2.5.2.jar:guava-33.0.0-jre.jar DEXPatch.java
jar cfm dexpatch-0.1.jar Manifest.txt *.class -C tmp .

# Cleanup
rm -rf tmp Manifest.txt *.class
rm -rf dexlib2-2.5.2.jar
rm -rf guava-33.0.0-jre.jar

echo "Build successful! Run with:"
echo "java -jar dexpatch.jar input.dex output.dex com/ax/example/MainActivity"
