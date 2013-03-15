if [ $JAVA_HOME ]
then
echo "Starting building..."
javac -classpath ../lib/log4j-1.2.17.jar org/umn/distributed/client/*.java org/umn/distributed/common/*.java org/umn/distributed/server/*.java *.java
echo "Build Done"
else
echo "JAVA_HOME not found"
fi
