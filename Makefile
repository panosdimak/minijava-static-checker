JTB_JAR      := lib/jtb133di.jar
JAVACC_JAR   := lib/javacc5.jar
GRAMMAR      := minijava.jj
JTB_OUT      := minijava-jtb.jj
JTB_OUT_DIRS := syntaxtree visitor
JAVACC_OUT   := MiniJavaParser.java MiniJavaParserTokenManager.java \
                MiniJavaParserConstants.java Token.java TokenMgrError.java \
                ParseException.java JavaCharStream.java

.PHONY: all compile clean

all: compile

compile:
	java -jar $(JTB_JAR) -te $(GRAMMAR)
	java -jar $(JAVACC_JAR) $(JTB_OUT)
	javac *.java syntaxtree/*.java visitor/*.java symboltable/*.java util/*.java

clean:
	find . -name '*.class' -delete
	rm -rf $(JTB_OUT) $(JTB_OUT_DIRS) $(JAVACC_OUT)
